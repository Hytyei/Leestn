import type { ChunkJob, Env, Segment } from "./types";
import { HttpError, bytesToBase64, int, json, str, body } from "./util";
import { member } from "./users";
import { Unsupported, buildAdts, findMoov, isAdts, plan as planAac, type AacInfo, type Slice } from "./mp4";

const MODEL = "@cf/openai/whisper-large-v3-turbo";
const MAX_ATTEMPTS = 3;

interface Row {
  ep_id: string;
  status: string;
  progress: number;
  error: string | null;
  chunks_total: number;
  audio_url: string;
  duration_sec: number;
  lines: string | null;
}

type Timing = { offset: number; anchors: [number, number][]; shifts: [number, number, number][] };

function view(row: Row | null, timing?: Timing | null) {
  if (!row) return { status: "none", progress: 0, lines: [] };
  return {
    status: row.status,
    progress: row.progress,
    error: row.error ?? undefined,
    lines: row.status === "ready" && row.lines ? JSON.parse(row.lines) : [],
    ...(timing ? { timing } : {}),
  };
}

/** The shared timing fix for this transcript, if someone saved one. */
async function timingOf(env: Env, epId: string): Promise<Timing | null> {
  try {
    const r = await env.DB.prepare("SELECT offset_sec, anchors FROM transcript_edits WHERE ep_id = ?").bind(epId).first<{ offset_sec: number; anchors: string }>();
    if (!r) return null;
    // Stored as {"a": anchors, "s": shifts}. Older rows hold a plain array of anchors.
    const j = JSON.parse(r.anchors);
    if (Array.isArray(j)) return { offset: r.offset_sec, anchors: j, shifts: [] };
    return { offset: r.offset_sec, anchors: j.a ?? [], shifts: j.s ?? [] };
  } catch {
    return null; // e.g. migration 0004 not applied yet: reading transcripts must keep working
  }
}

/** PUT /v1/transcripts/:epId/timing — save (or clear) the shared timing fix. Any member may do it. */
export async function putTiming(env: Env, req: Request, epId: string): Promise<Response> {
  const { user } = await member(env, req);
  const row = await env.DB.prepare("SELECT status FROM transcripts WHERE ep_id = ?").bind(epId).first<{ status: string }>();
  if (!row || row.status !== "ready") throw new HttpError(404, "There is no finished transcript on the server for this episode");
  const b = await body(req);
  const offset = int(b.offset, 0, -3600, 3600);
  const list = Array.isArray(b.anchors) ? (b.anchors as unknown[]).slice(0, 300) : [];
  const anchors: [number, number][] = [];
  for (const a of list) {
    if (!Array.isArray(a) || a.length !== 2) continue;
    const i = Math.trunc(Number(a[0]));
    const sec = Math.trunc(Number(a[1]));
    if (Number.isFinite(i) && Number.isFinite(sec) && i >= 0 && i < 200_000 && sec > -7200 && sec < 86_400) anchors.push([i, sec]);
  }
  // shifts: [firstLine, lastLine (-1 = to the end), milliseconds]
  const shifts: [number, number, number][] = [];
  for (const a of Array.isArray(b.shifts) ? (b.shifts as unknown[]).slice(0, 300) : []) {
    if (!Array.isArray(a) || a.length !== 3) continue;
    const from = Math.trunc(Number(a[0]));
    const to = Math.trunc(Number(a[1]));
    const delta = Math.trunc(Number(a[2]));
    if ([from, to, delta].every(Number.isFinite) && from >= 0 && from < 200_000 && to >= -1 && to < 200_000 && delta !== 0 && Math.abs(delta) <= 3_600_000) shifts.push([from, to, delta]);
  }
  if (offset === 0 && anchors.length === 0 && shifts.length === 0) {
    await env.DB.prepare("DELETE FROM transcript_edits WHERE ep_id = ?").bind(epId).run();
    return json(env, { ok: true, timing: null });
  }
  await env.DB.prepare(
    `INSERT INTO transcript_edits (ep_id, offset_sec, anchors, updated_by, updated_at) VALUES (?, ?, ?, ?, ?)
     ON CONFLICT(ep_id) DO UPDATE SET offset_sec=excluded.offset_sec, anchors=excluded.anchors, updated_by=excluded.updated_by, updated_at=excluded.updated_at`,
  ).bind(epId, offset, JSON.stringify({ a: anchors, s: shifts }), user?.id ?? "key", Date.now()).run();
  return json(env, { ok: true, timing: { offset, anchors, shifts } });
}

/** Refuse anything that isn't a public https URL, so the Worker can't be pointed at internal addresses. */
function checkAudioUrl(raw: string): URL {
  let u: URL;
  try {
    u = new URL(raw);
  } catch {
    throw new HttpError(400, "audioUrl is not a valid URL");
  }
  const h = u.hostname.toLowerCase();
  const privateHost =
    h === "localhost" ||
    h.endsWith(".local") ||
    h.endsWith(".internal") ||
    /^\[/.test(h) ||
    /^(127|10|0)\./.test(h) ||
    /^192\.168\./.test(h) ||
    /^169\.254\./.test(h) ||
    /^172\.(1[6-9]|2\d|3[01])\./.test(h) ||
    !h.includes(".");
  if (u.protocol !== "https:" || privateHost) throw new HttpError(400, "audioUrl must be a public https URL");
  return u;
}

const UA = { "user-agent": "LeestnTranscriber/1.0" };

const hostOf = (url: string) => { try { return new URL(url).hostname; } catch { return "the audio host"; } };

/** Reads bytes [from, to) of a file. Throws a clear error if the host refuses us or ignores range requests. */
async function readRange(url: string, from: number, to: number): Promise<Uint8Array> {
  const r = await fetch(url, { headers: { ...UA, range: `bytes=${from}-${to - 1}` }, redirect: "follow" });
  if (r.status === 200 && from > 0) {
    await r.body?.cancel();
    throw new HttpError(422, `${hostOf(url)} doesn't allow reading the audio in parts (no range support), so this episode can't be transcribed.`);
  }
  if (r.status !== 200 && r.status !== 206) {
    await r.body?.cancel();
    if ([401, 403, 404, 410, 451].includes(r.status)) {
      throw new HttpError(422, `${hostOf(url)} refused the server's request for the audio (HTTP ${r.status}). Some hosts block automated downloads.`);
    }
    throw new Error(`Audio fetch failed: HTTP ${r.status} from ${hostOf(url)}`);
  }
  return new Uint8Array(await r.arrayBuffer()).subarray(0, to - from);
}

/** Size of an ID3v2 tag at the start of a file, from its 10-byte header (0 when there is none). */
function id3Size(head: Uint8Array): number {
  if (head.length < 10 || head[0] !== 0x49 || head[1] !== 0x44 || head[2] !== 0x33) return 0;
  const n = ((head[6] & 0x7f) << 21) | ((head[7] & 0x7f) << 14) | ((head[8] & 0x7f) << 7) | (head[9] & 0x7f);
  return 10 + n + (head[5] & 0x10 ? 10 : 0);
}

const MP3_BR = {
  1: [0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320],
  2: [0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160],
} as const;
const MP3_SR = { 3: [44100, 48000, 32000], 2: [22050, 24000, 16000], 0: [11025, 12000, 8000] } as const;

/** Length in bytes of the MPEG Layer III frame starting at i, or 0 if there is no valid header there. */
function mp3FrameLen(b: Uint8Array, i: number): number {
  if (i + 4 > b.length || b[i] !== 0xff || (b[i + 1] & 0xe0) !== 0xe0) return 0;
  const ver = (b[i + 1] >> 3) & 3; // 3 = MPEG1, 2 = MPEG2, 0 = MPEG2.5
  const layer = (b[i + 1] >> 1) & 3; // 1 = Layer III
  if (ver === 1 || layer !== 1) return 0;
  const bi = b[i + 2] >> 4;
  const si = (b[i + 2] >> 2) & 3;
  if (bi === 0 || bi === 15 || si === 3) return 0;
  const br = (ver === 3 ? MP3_BR[1] : MP3_BR[2])[bi] * 1000;
  const sr = (MP3_SR as any)[ver][si] as number;
  const pad = (b[i + 2] >> 1) & 1;
  return Math.floor(((ver === 3 ? 144 : 72) * br) / sr) + pad;
}

/** Index of the first real MP3 frame (confirmed by a second frame right after it). Slices are cut at arbitrary bytes. */
function mp3Start(b: Uint8Array): number {
  const limit = Math.min(b.length - 4, 8192);
  for (let i = 0; i < limit; i++) {
    const l = mp3FrameLen(b, i);
    if (l && mp3FrameLen(b, i + l)) return i;
  }
  return 0;
}

/**
 * Reads the first MP3 frame after the ID3 tag to learn the file's real length. A Xing/Info header gives the exact
 * frame count (and, for VBR, a seek table); otherwise a constant bitrate gives length = bytes / rate.
 * Feeds often state a wrong duration, which is what made timestamps drift.
 */
async function mp3Timing(url: string, id3: number, size: number): Promise<{ dur: number; toc?: number[] } | null> {
  let b: Uint8Array;
  try {
    b = await readRange(url, id3, Math.min(size, id3 + 4096));
  } catch {
    return null;
  }
  const i = mp3Start(b);
  const len = mp3FrameLen(b, i);
  if (!len) return null;
  const ver = (b[i + 1] >> 3) & 3;
  const bi = b[i + 2] >> 4;
  const si = (b[i + 2] >> 2) & 3;
  const mono = ((b[i + 3] >> 6) & 3) === 3;
  const sr = (MP3_SR as any)[ver][si] as number;
  const kbps = (ver === 3 ? MP3_BR[1] : MP3_BR[2])[bi];
  const spf = ver === 3 ? 1152 : 576;
  const at = i + 4 + (ver === 3 ? (mono ? 17 : 32) : mono ? 9 : 17);
  const tag = String.fromCharCode(...b.subarray(at, at + 4));
  if ((tag === "Xing" || tag === "Info") && at + 12 <= b.length) {
    const flags = b[at + 7];
    let p = at + 8;
    const u32 = (o: number) => ((b[o] << 24) | (b[o + 1] << 16) | (b[o + 2] << 8) | b[o + 3]) >>> 0;
    if (flags & 1) {
      const frames = u32(p);
      p += 4;
      if (flags & 2) p += 4;
      const dur = (frames * spf) / sr;
      if (dur > 1) {
        const toc = tag === "Xing" && flags & 4 && p + 100 <= b.length ? Array.from(b.subarray(p, p + 100)) : undefined;
        return { dur, toc };
      }
    }
    // Info/Xing without a frame count: fall through to the constant-bitrate estimate
    const audio = size - id3 - len;
    return audio > 0 && kbps > 0 ? { dur: (audio * 8) / (kbps * 1000) } : null;
  }
  const audio = size - id3;
  return audio > 0 && kbps > 0 ? { dur: (audio * 8) / (kbps * 1000) } : null;
}

/** Time in seconds at a byte fraction, using a Xing seek table (it maps percent of time to 1/256 of the file). */
function timeAtByteFraction(f: number, dur: number, toc?: number[]): number {
  if (!toc || toc.length !== 100) return f * dur;
  const target = Math.min(1, Math.max(0, f)) * 256;
  let k = 0;
  while (k < 99 && toc[k + 1] <= target) k++;
  const a = toc[k];
  const bnext = k < 99 ? toc[k + 1] : 256;
  const frac = bnext > a ? (target - a) / (bnext - a) : 0;
  return ((k + frac) / 100) * dur;
}

/** "de-DE" -> "de". Only plain 2 or 3 letter codes are passed on. */
export function langCode(raw: unknown): string | undefined {
  const c = String(raw ?? "").toLowerCase().split(/[-_]/)[0];
  return /^[a-z]{2,3}$/.test(c) ? c : undefined;
}

type Kind = "mp3" | "adts" | "mp4" | "unsupported";

/** Looks at the first bytes of the file, because the URL and content type are often wrong. */
function sniff(head: Uint8Array, type: string, url: string): { kind: Kind; why?: string } {
  const s = (o: number, n: number) => String.fromCharCode(...head.subarray(o, o + n));
  if (s(0, 3) === "ID3") return { kind: "mp3" };
  if (isAdts(head)) return { kind: "adts" };
  if (head[0] === 0xff && (head[1] & 0xe0) === 0xe0) return { kind: "mp3" };
  if (s(4, 4) === "ftyp") return { kind: "mp4" };
  if (s(0, 4) === "OggS") return { kind: "unsupported", why: "Ogg/Opus audio" };
  if (s(0, 4) === "RIFF") return { kind: "unsupported", why: "WAV audio" };
  if (s(0, 4) === "fLaC") return { kind: "unsupported", why: "FLAC audio" };
  if (/mp4|m4a|aac/i.test(type) || /\.(m4a|mp4)(\?|$)/i.test(url)) return { kind: "mp4" };
  return { kind: "mp3" }; // unknown: try it as MP3, like before
}

async function probe(url: string): Promise<{ size: number; type: string; head: Uint8Array }> {
  let size = 0;
  let type = "";
  let status = 0;
  let head = new Uint8Array(0);
  try {
    const r = await fetch(url, { headers: { ...UA, range: "bytes=0-15" }, redirect: "follow" });
    status = r.status;
    type = r.headers.get("content-type") || "";
    const m = /\/(\d+)$/.exec(r.headers.get("content-range") || "");
    if (r.status === 206 && m) size = parseInt(m[1], 10);
    else if (r.status === 200) size = int(r.headers.get("content-length"), 0);
    if (r.status === 200 || r.status === 206) {
      const reader = r.body?.getReader();
      const parts: number[] = [];
      while (reader && parts.length < 16) {
        const { done, value } = await reader.read();
        if (done || !value) break;
        for (const b of value) if (parts.length < 16) parts.push(b);
      }
      await reader?.cancel();
      head = new Uint8Array(parts);
    }
  } catch {
    /* fall through to HEAD */
  }
  if (!size) {
    const h = await fetch(url, { method: "HEAD", redirect: "follow", headers: UA });
    size = int(h.headers.get("content-length"), 0);
    type = type || h.headers.get("content-type") || "";
    status = status || h.status;
  }
  if (!size) {
    if ([401, 403, 404, 410, 451].includes(status)) throw new HttpError(422, `${hostOf(url)} refused the server's request for the audio (HTTP ${status}). Some hosts block automated downloads.`);
    throw new HttpError(422, `Couldn't read the audio size from ${hostOf(url)} (HTTP ${status || "no answer"}). The host must report the file size and allow range requests.`);
  }
  return { size, type, head };
}

export async function getTranscript(env: Env, req: Request, epId: string): Promise<Response> {
  await member(env, req);
  const row = await env.DB.prepare("SELECT * FROM transcripts WHERE ep_id = ?").bind(epId).first<Row>();
  return json(env, view(row, row?.status === "ready" ? await timingOf(env, epId) : null));
}

export async function usageNow(env: Env) {
  const dayStart = Math.floor(Date.now() / 86_400_000) * 86_400_000;
  const limitSec = int(env.WHISPER_DAILY_MINUTES, 200, 0, 100_000) * 60;
  const r = await env.DB.prepare("SELECT COALESCE(SUM(audio_sec),0) AS s, COUNT(*) AS n FROM transcripts WHERE created_at >= ?").bind(dayStart).first<{ s: number; n: number }>();
  return { dayStart, limitSec, usedSec: r?.s ?? 0, jobs: r?.n ?? 0 };
}

/** GET /v1/usage: how much of today's free Whisper time is used. Read-only, uses no AI. */
export async function getUsage(env: Env, req: Request): Promise<Response> {
  await member(env, req);
  const u = await usageNow(env);
  return json(env, {
    limitMin: Math.round(u.limitSec / 60),
    usedMin: Math.round((u.usedSec / 60) * 10) / 10,
    remainingMin: Math.max(0, Math.round(((u.limitSec - u.usedSec) / 60) * 10) / 10),
    jobs: u.jobs,
    resetsAt: u.dayStart + 86_400_000,
    estimate: true,
  });
}

export async function requestTranscript(env: Env, req: Request): Promise<Response> {
  const { user } = await member(env, req);
  const requester = user?.id ?? "key";
  const b = await body(req);
  const epId = str(b.epId, 120);
  const audioUrl = str(b.audioUrl, 2000);
  const durationSec = int(b.durationSec, 0, 0, 60 * 60 * 24);
  if (!epId || !audioUrl) throw new HttpError(400, "epId and audioUrl are required");
  checkAudioUrl(audioUrl);

  const existing = await env.DB.prepare("SELECT * FROM transcripts WHERE ep_id = ?").bind(epId).first<Row>();
  // A failed job can always be asked again. A finished one only when the caller explicitly asks to redo it (text came out wrong).
  const redo = b.force === true && existing?.status === "ready";
  if (existing && existing.status !== "failed" && !redo) return json(env, view(existing, existing.status === "ready" ? await timingOf(env, epId) : null));

  // Daily cap on new jobs, across all users, to keep Whisper spend predictable.
  const cap = int(env.MAX_TRANSCRIPTS_PER_DAY, 40, 0);
  const dayStart = Math.floor(Date.now() / 86_400_000) * 86_400_000;
  const today = await env.DB.prepare("SELECT COUNT(*) AS n FROM transcripts WHERE created_at >= ?").bind(dayStart).first<{ n: number }>();
  if ((today?.n ?? 0) >= cap) throw new HttpError(429, "The server's transcription limit for today has been reached. Try again tomorrow.");
  const perUser = int(env.MAX_PER_USER_PER_DAY, 8, 0);
  const mine = await env.DB.prepare("SELECT COUNT(*) AS n FROM transcripts WHERE requested_by = ? AND created_at >= ?").bind(requester, dayStart).first<{ n: number }>();
  if ((mine?.n ?? 0) >= perUser) throw new HttpError(429, `You have used your ${perUser} transcripts for today. Try again tomorrow.`);

  const { size, type, head } = await probe(audioUrl);
  const maxBytes = int(env.MAX_AUDIO_MB, 150, 1) * 1024 * 1024;
  if (size > maxBytes) throw new HttpError(413, `Audio is larger than ${env.MAX_AUDIO_MB} MB`);
  const found = sniff(head, type, audioUrl);
  if (found.kind === "unsupported") {
    throw new HttpError(422, `This episode is ${found.why}, which can't be transcribed yet. MP3 and M4A (AAC) work.`);
  }

  // Free-allowance guard: Workers AI Whisper is free up to a daily amount of audio.
  const estSec = durationSec > 0 ? durationSec : Math.round((size * 8) / 128_000);
  const u = await usageNow(env);
  if (u.usedSec + estSec > u.limitSec) {
    const left = Math.max(0, Math.floor((u.limitSec - u.usedSec) / 60));
    throw new HttpError(429, `Not enough free Whisper time left today (${left} min left, this episode needs about ${Math.ceil(estSec / 60)} min). The allowance resets at 00:00 UTC.`);
  }

  const chunkBytes = int(env.CHUNK_BYTES, 1_048_576, 65_536, 4 * 1024 * 1024);
  let messages: ChunkJob[];
  if (found.kind === "mp4") {
    try {
      const at = await findMoov(size, (from, to) => readRange(audioUrl, from, to));
      const moov = await readRange(audioUrl, at.from, at.to);
      const p = planAac(moov, chunkBytes);
      messages = p.slices.map((s, idx) => ({ epId, idx, aac: { t0: s.t0, runs: s.runs, ...p.aac, channels: p.aac.channels } }));
    } catch (e) {
      if (e instanceof Unsupported) throw new HttpError(422, e.message);
      throw e;
    }
  } else {
    const n = Math.max(1, Math.ceil(size / chunkBytes));
    const tag = found.kind === "mp3" ? id3Size(head) : 0;
    messages = Array.from({ length: n }, (_, idx) => {
      const m: ChunkJob = found.kind === "adts" ? { epId, idx, fmt: "adts" } : { epId, idx };
      if (found.kind === "mp3") { m.sz = size; if (tag > 0) m.id3 = tag; }
      return m;
    });
  }
  if (found.kind === "mp3") {
    const t = await mp3Timing(audioUrl, id3Size(head), size);
    if (t) for (const m of messages) { m.dur = Math.round(t.dur * 10) / 10; if (t.toc) m.toc = t.toc; }
  }
  const lang = langCode(b.language);
  if (lang) for (const m of messages) m.lang = lang;
  const total = messages.length;
  const now = Date.now();
  // a new transcription has different lines, so an old shared timing fix no longer fits
  try {
    await env.DB.prepare("DELETE FROM transcript_edits WHERE ep_id = ?").bind(epId).run();
  } catch {
    /* migration 0004 not applied yet: nothing to clear */
  }
  await env.DB.batch([
    env.DB.prepare("DELETE FROM transcript_chunks WHERE ep_id = ?").bind(epId),
    env.DB.prepare(
      `INSERT INTO transcripts (ep_id, status, progress, error, chunks_total, audio_url, duration_sec, lines, requested_by, created_at, updated_at, audio_sec)
       VALUES (?, 'queued', 0, NULL, ?, ?, ?, NULL, ?, ?, ?, ?)
       ON CONFLICT(ep_id) DO UPDATE SET status='queued', progress=0, error=NULL, chunks_total=excluded.chunks_total,
         audio_url=excluded.audio_url, duration_sec=excluded.duration_sec, lines=NULL, requested_by=excluded.requested_by,
         created_at=excluded.created_at, updated_at=excluded.updated_at, audio_sec=excluded.audio_sec`,
    ).bind(epId, total, audioUrl, durationSec, requester, now, now, estSec),
  ]);

  // Queue limits: 100 messages and 256 KB per batch. M4A messages carry frame sizes, so batch by size too.
  let batch: { body: ChunkJob }[] = [];
  let batchBytes = 0;
  for (const m of messages) {
    const len = JSON.stringify(m).length;
    if (batch.length >= 50 || batchBytes + len > 200_000) {
      await env.TRANSCRIBE.sendBatch(batch);
      batch = [];
      batchBytes = 0;
    }
    batch.push({ body: m });
    batchBytes += len;
  }
  if (batch.length) await env.TRANSCRIBE.sendBatch(batch);
  return json(env, { status: "queued", progress: 0, lines: [] }, 202);
}

async function fail(env: Env, epId: string, message: string) {
  await env.DB.prepare("UPDATE transcripts SET status='failed', error=?, updated_at=? WHERE ep_id=?")
    .bind(message.slice(0, 300), Date.now(), epId)
    .run();
}

/** Gets the audio of one piece as bytes Whisper can decode, plus where the piece starts and how long it is, in seconds. */
async function pieceOf(env: Env, row: Row, job: ChunkJob): Promise<{ bytes: Uint8Array; offset: number; span: number }> {
  const chunkBytes = int(env.CHUNK_BYTES, 1_048_576, 65_536, 4 * 1024 * 1024);
  const read = (from: number, to: number) => readRange(row.audio_url, from, to);
  if (job.aac) {
    const a: AacInfo = { profile: job.aac.profile, sfi: job.aac.sfi, channels: job.aac.channels };
    const slice: Slice = { t0: job.aac.t0, runs: job.aac.runs };
    const bytes = await buildAdts(slice, a, read);
    const frames = slice.runs.reduce((n, r) => n + r[1].length, 0);
    const rate = [96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350][a.sfi] ?? 44100;
    return { bytes, offset: slice.t0, span: (frames * 1024) / rate };
  }
  // MP3 and raw AAC: a byte range. Timing comes from the byte position and the episode length (or a 128 kbps guess).
  const id3 = job.id3 ?? 0;
  const from = Math.max(job.idx * chunkBytes, id3);
  const end = job.idx * chunkBytes + chunkBytes;
  if (end <= from) return { bytes: new Uint8Array(0), offset: 0, span: 0 }; // this slice is all tag (cover art)
  let bytes = await read(from, end);
  if (job.fmt === "adts") {
    let i = 0;
    while (i + 1 < bytes.length && !isAdts(bytes.subarray(i))) i++;
    bytes = bytes.subarray(i);
  } else {
    bytes = bytes.subarray(mp3Start(bytes)); // start on a whole frame so the decoder isn't confused
  }
  const audioBytes = job.sz && job.sz > id3 ? job.sz - id3 : row.chunks_total * chunkBytes;
  const total = job.dur && job.dur > 0 ? job.dur : row.duration_sec > 0 ? row.duration_sec : (audioBytes * 8) / 128_000;
  const at = (byte: number) => timeAtByteFraction(Math.min(1, (byte - id3) / audioBytes), total, job.toc);
  const offset = at(from);
  const span = Math.max(1, at(Math.min(end, id3 + audioBytes)) - offset);
  return { bytes, offset, span };
}

async function transcribeChunk(env: Env, row: Row, job: ChunkJob): Promise<void> {
  const idx = job.idx;
  const { bytes, offset, span: exactPerChunk } = await pieceOf(env, row, job);

  if (bytes.length < 2000) {
    // nothing to transcribe here (tag only)
    await env.DB.prepare("INSERT OR REPLACE INTO transcript_chunks (ep_id, idx, segments) VALUES (?, ?, '[]')").bind(row.ep_id, idx).run();
    return finishChunk(env, row);
  }
  const out: any = await runWhisper(env, bytesToBase64(bytes), job.lang);

  const segs: Segment[] = [];
  if (Array.isArray(out?.segments) && out.segments.length) {
    for (const s of cleanSegments(out.segments)) {
      const text = String(s.text ?? "").trim();
      if (text) segs.push({ start: offset + Number(s.start ?? 0), end: offset + Number(s.end ?? 0), text });
    }
  } else if (typeof out?.text === "string" && out.text.trim()) {
    // No timestamps from the model: split into sentences and spread them across the slice.
    const parts = out.text.trim().split(/(?<=[.!?])\s+/).filter(Boolean);
    const step = exactPerChunk / Math.max(1, parts.length);
    parts.forEach((text: string, i: number) => segs.push({ start: offset + i * step, end: offset + (i + 1) * step, text }));
  }

  await env.DB.prepare("INSERT OR REPLACE INTO transcript_chunks (ep_id, idx, segments) VALUES (?, ?, ?)")
    .bind(row.ep_id, idx, JSON.stringify(segs))
    .run();

  return finishChunk(env, row);
}

async function finishChunk(env: Env, row: Row): Promise<void> {
  const done = await env.DB.prepare("SELECT COUNT(*) AS n FROM transcript_chunks WHERE ep_id = ?").bind(row.ep_id).first<{ n: number }>();
  const n = done?.n ?? 0;
  if (n >= row.chunks_total) await assemble(env, row.ep_id);
  else {
    await env.DB.prepare("UPDATE transcripts SET status='processing', progress=?, updated_at=? WHERE ep_id=? AND status IN ('queued','processing')")
      .bind(Math.min(0.99, n / row.chunks_total), Date.now(), row.ep_id)
      .run();
  }
}

/**
 * Calls Whisper. A language hint stops it guessing the language for every slice (the usual cause of
 * garbled text), and the other options reduce invented text over silence. If this model rejects the options,
 * try once more with the plain call so a parameter problem can never stop transcripts working.
 */
async function runWhisper(env: Env, audio: string, lang?: string): Promise<any> {
  const opts: Record<string, unknown> = { audio, task: "transcribe", vad_filter: true, condition_on_previous_text: false };
  if (lang) opts.language = lang;
  try {
    return await (env.AI as any).run(MODEL, opts);
  } catch (e) {
    const m = e instanceof Error ? e.message : String(e);
    if (!/invalid|unsupported|unknown|not allowed|schema|input|param|property|language/i.test(m)) throw e;
    return await (env.AI as any).run(MODEL, { audio });
  }
}

/** Drops segments the model is unsure are speech, runaway repetition and the usual invented lines over silence. */
function cleanSegments(raw: any[]): any[] {
  const out: any[] = [];
  let run = 0;
  for (const s of raw) {
    const text = String(s?.text ?? "").trim();
    if (!text) continue;
    if (Number(s.no_speech_prob) > 0.8 && Number(s.avg_logprob) < -0.8) continue;
    if (Number(s.compression_ratio) > 2.6) continue;
    const prev = out.length ? String(out[out.length - 1].text).trim().toLowerCase() : "";
    run = prev === text.toLowerCase() ? run + 1 : 0;
    if (run >= 2) continue; // the same line three times in a row is a loop, not speech
    out.push(s);
  }
  return out;
}

async function assemble(env: Env, epId: string) {
  const rows = await env.DB.prepare("SELECT segments FROM transcript_chunks WHERE ep_id = ? ORDER BY idx").bind(epId).all<{ segments: string }>();
  const lines: { t: number; text: string }[] = [];
  for (const r of rows.results) {
    for (const s of JSON.parse(r.segments) as Segment[]) if (s.text) lines.push({ t: Math.max(0, Math.round(s.start)), text: s.text });
  }
  const skippedRows = rows.results.filter((r) => r.segments.includes('"skip"'));
  const skipped = skippedRows.length;
  if (skipped * 4 > rows.results.length) {
    const why = (JSON.parse(skippedRows[0].segments)[0]?.skip as string) || "unknown error";
    return fail(env, epId, `${skipped} of ${rows.results.length} parts couldn't be transcribed (${why})`);
  }
  if (!lines.length) return fail(env, epId, "No speech was recognised in this episode");
  const note = skipped ? `${skipped} short part${skipped > 1 ? "s" : ""} couldn't be transcribed and ${skipped > 1 ? "are" : "is"} missing.` : null;
  await env.DB.batch([
    env.DB.prepare("UPDATE transcripts SET status='ready', progress=1, error=?, lines=?, updated_at=? WHERE ep_id=?").bind(note, JSON.stringify(lines), Date.now(), epId),
    env.DB.prepare("DELETE FROM transcript_chunks WHERE ep_id = ?").bind(epId),
  ]);
}

/** Queue consumer: one message is one slice of one episode. */
/** A slice that keeps failing is recorded as skipped, so one bad part doesn't lose the whole episode. */
async function skipChunk(env: Env, row: Row, idx: number, message: string): Promise<void> {
  const mark = JSON.stringify([{ start: 0, end: 0, text: "", skip: message.slice(0, 160) }]);
  await env.DB.prepare("INSERT OR REPLACE INTO transcript_chunks (ep_id, idx, segments) VALUES (?, ?, ?)").bind(row.ep_id, idx, mark).run();
  const done = await env.DB.prepare("SELECT COUNT(*) AS n FROM transcript_chunks WHERE ep_id = ?").bind(row.ep_id).first<{ n: number }>();
  if ((done?.n ?? 0) >= row.chunks_total) await assemble(env, row.ep_id);
}

export async function consume(batch: MessageBatch<ChunkJob>, env: Env): Promise<void> {
  for (const msg of batch.messages) {
    const { epId, idx } = msg.body;
    try {
      const row = await env.DB.prepare("SELECT * FROM transcripts WHERE ep_id = ?").bind(epId).first<Row>();
      if (!row || row.status === "ready" || row.status === "failed") {
        msg.ack();
        continue;
      }
      await transcribeChunk(env, row, msg.body);
      msg.ack();
    } catch (e) {
      const message = e instanceof Error ? e.message : String(e);
      const permanent = e instanceof HttpError && e.status === 422;
      if (permanent) {
        await fail(env, epId, message);
        msg.ack();
      } else if (msg.attempts > MAX_ATTEMPTS) {
        try {
          const row = await env.DB.prepare("SELECT * FROM transcripts WHERE ep_id = ?").bind(epId).first<Row>();
          if (row && row.status !== "ready" && row.status !== "failed") await skipChunk(env, row, idx, message);
        } catch {
          await fail(env, epId, message);
        }
        msg.ack();
      } else {
        msg.retry({ delaySeconds: 10 * msg.attempts });
      }
    }
  }
}
