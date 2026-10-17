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

function view(row: Row | null) {
  if (!row) return { status: "none", progress: 0, lines: [] };
  return {
    status: row.status,
    progress: row.progress,
    error: row.error ?? undefined,
    lines: row.status === "ready" && row.lines ? JSON.parse(row.lines) : [],
  };
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

/** Reads bytes [from, to) of a file. Throws if the host ignores range requests for a later part of the file. */
async function readRange(url: string, from: number, to: number): Promise<Uint8Array> {
  const r = await fetch(url, { headers: { ...UA, range: `bytes=${from}-${to - 1}` }, redirect: "follow" });
  if (r.status === 200 && from > 0) {
    await r.body?.cancel();
    throw new HttpError(422, "The audio host does not support range requests");
  }
  if (r.status !== 200 && r.status !== 206) throw new Error(`Audio fetch failed: ${r.status}`);
  return new Uint8Array(await r.arrayBuffer()).subarray(0, to - from);
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
  let head = new Uint8Array(0);
  try {
    const r = await fetch(url, { headers: { ...UA, range: "bytes=0-15" }, redirect: "follow" });
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
  }
  if (!size) throw new HttpError(422, "Could not read the audio size. The host must support range requests.");
  return { size, type, head };
}

export async function getTranscript(env: Env, req: Request, epId: string): Promise<Response> {
  await member(env, req);
  const row = await env.DB.prepare("SELECT * FROM transcripts WHERE ep_id = ?").bind(epId).first<Row>();
  return json(env, view(row));
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
  if (existing && existing.status !== "failed") return json(env, view(existing));

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
    messages = Array.from({ length: n }, (_, idx) => (found.kind === "adts" ? { epId, idx, fmt: "adts" as const } : { epId, idx }));
  }
  const total = messages.length;
  const now = Date.now();
  await env.DB.batch([
    env.DB.prepare("DELETE FROM transcript_chunks WHERE ep_id = ?").bind(epId),
    env.DB.prepare(
      `INSERT INTO transcripts (ep_id, status, progress, error, chunks_total, audio_url, duration_sec, lines, requested_by, created_at, updated_at)
       VALUES (?, 'queued', 0, NULL, ?, ?, ?, NULL, ?, ?, ?)
       ON CONFLICT(ep_id) DO UPDATE SET status='queued', progress=0, error=NULL, chunks_total=excluded.chunks_total,
         audio_url=excluded.audio_url, duration_sec=excluded.duration_sec, lines=NULL, requested_by=excluded.requested_by,
         created_at=excluded.created_at, updated_at=excluded.updated_at`,
    ).bind(epId, total, audioUrl, durationSec, requester, now, now),
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
  // MP3 and raw AAC: a byte range. Timing is estimated from the episode length (or a 128 kbps guess).
  const from = job.idx * chunkBytes;
  let bytes = await read(from, from + chunkBytes);
  if (job.fmt === "adts") {
    // start at the first complete frame
    let i = 0;
    while (i + 1 < bytes.length && !isAdts(bytes.subarray(i))) i++;
    bytes = bytes.subarray(i);
  }
  const span = row.duration_sec > 0 ? row.duration_sec / row.chunks_total : (chunkBytes * 8) / 128_000;
  return { bytes, offset: job.idx * span, span };
}

async function transcribeChunk(env: Env, row: Row, job: ChunkJob): Promise<void> {
  const idx = job.idx;
  const { bytes, offset, span: exactPerChunk } = await pieceOf(env, row, job);

  const out: any = await (env.AI as any).run(MODEL, { audio: bytesToBase64(bytes) });

  const segs: Segment[] = [];
  if (Array.isArray(out?.segments) && out.segments.length) {
    for (const s of out.segments) {
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

  const done = await env.DB.prepare("SELECT COUNT(*) AS n FROM transcript_chunks WHERE ep_id = ?").bind(row.ep_id).first<{ n: number }>();
  const n = done?.n ?? 0;
  if (n >= row.chunks_total) await assemble(env, row.ep_id);
  else {
    await env.DB.prepare("UPDATE transcripts SET status='processing', progress=?, updated_at=? WHERE ep_id=? AND status IN ('queued','processing')")
      .bind(Math.min(0.99, n / row.chunks_total), Date.now(), row.ep_id)
      .run();
  }
}

async function assemble(env: Env, epId: string) {
  const rows = await env.DB.prepare("SELECT segments FROM transcript_chunks WHERE ep_id = ? ORDER BY idx").bind(epId).all<{ segments: string }>();
  const lines: { t: number; text: string }[] = [];
  for (const r of rows.results) {
    for (const s of JSON.parse(r.segments) as Segment[]) lines.push({ t: Math.max(0, Math.round(s.start)), text: s.text });
  }
  if (!lines.length) return fail(env, epId, "No speech was recognised in this episode");
  await env.DB.batch([
    env.DB.prepare("UPDATE transcripts SET status='ready', progress=1, error=NULL, lines=?, updated_at=? WHERE ep_id=?").bind(JSON.stringify(lines), Date.now(), epId),
    env.DB.prepare("DELETE FROM transcript_chunks WHERE ep_id = ?").bind(epId),
  ]);
}

/** Queue consumer: one message is one slice of one episode. */
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
      if (permanent || msg.attempts > MAX_ATTEMPTS) {
        await fail(env, epId, message);
        msg.ack();
      } else {
        msg.retry({ delaySeconds: 10 * msg.attempts });
      }
    }
  }
}
