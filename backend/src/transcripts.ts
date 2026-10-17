import type { ChunkJob, Env, Segment } from "./types";
import { HttpError, bytesToBase64, int, json, str, body } from "./util";
import { member } from "./users";

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

async function probe(url: string): Promise<{ size: number; type: string }> {
  const headers = { "user-agent": "LeestnTranscriber/0.4" };
  let size = 0;
  let type = "";
  try {
    const head = await fetch(url, { method: "HEAD", redirect: "follow", headers });
    size = int(head.headers.get("content-length"), 0);
    type = head.headers.get("content-type") || "";
  } catch {
    /* fall through to the range probe */
  }
  if (!size) {
    const r = await fetch(url, { headers: { ...headers, range: "bytes=0-0" }, redirect: "follow" });
    const m = /\/(\d+)$/.exec(r.headers.get("content-range") || "");
    if (m) size = parseInt(m[1], 10);
    type = type || r.headers.get("content-type") || "";
    await r.body?.cancel();
  }
  if (!size) throw new HttpError(422, "Could not read the audio size. The host must support range requests.");
  return { size, type };
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

  const { size, type } = await probe(audioUrl);
  if (/mp4|m4a|aac|x-m4a/i.test(type) || /\.(m4a|mp4|aac)(\?|$)/i.test(audioUrl)) {
    throw new HttpError(422, "This episode is an M4A/AAC file, which can't be split for transcription yet. MP3 works.");
  }
  const maxBytes = int(env.MAX_AUDIO_MB, 150, 1) * 1024 * 1024;
  if (size > maxBytes) throw new HttpError(413, `Audio is larger than ${env.MAX_AUDIO_MB} MB`);

  const chunkBytes = int(env.CHUNK_BYTES, 1_048_576, 65_536, 4 * 1024 * 1024);
  const total = Math.max(1, Math.ceil(size / chunkBytes));
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

  const jobs = Array.from({ length: total }, (_, idx) => ({ body: { epId, idx } as ChunkJob }));
  for (let i = 0; i < jobs.length; i += 100) await env.TRANSCRIBE.sendBatch(jobs.slice(i, i + 100));
  return json(env, { status: "queued", progress: 0, lines: [] }, 202);
}

async function fail(env: Env, epId: string, message: string) {
  await env.DB.prepare("UPDATE transcripts SET status='failed', error=?, updated_at=? WHERE ep_id=?")
    .bind(message.slice(0, 300), Date.now(), epId)
    .run();
}

async function transcribeChunk(env: Env, row: Row, idx: number): Promise<void> {
  const chunkBytes = int(env.CHUNK_BYTES, 1_048_576, 65_536, 4 * 1024 * 1024);
  const from = idx * chunkBytes;
  const r = await fetch(row.audio_url, {
    headers: { range: `bytes=${from}-${from + chunkBytes - 1}`, "user-agent": "LeestnTranscriber/0.4" },
    redirect: "follow",
  });
  if (r.status === 200 && idx > 0) throw new HttpError(422, "The audio host does not support range requests");
  if (r.status !== 200 && r.status !== 206) throw new Error(`Audio fetch failed: ${r.status}`);
  const bytes = new Uint8Array(await r.arrayBuffer()).subarray(0, chunkBytes);

  const out: any = await (env.AI as any).run(MODEL, { audio: bytesToBase64(bytes) });

  // Seconds covered by one slice: from the known duration, else assume a 128 kbps MP3.
  const exactPerChunk = row.duration_sec > 0 ? row.duration_sec / row.chunks_total : (chunkBytes * 8) / 128_000;
  const offset = idx * exactPerChunk;

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
      await transcribeChunk(env, row, idx);
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
