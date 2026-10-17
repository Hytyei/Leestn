// Tests for transcribing M4A/AAC episodes, using small synthetic files. No network, no real Whisper.
import { call, drain, files, finish, makeDb, makeEnv, ok, queued, aiCalls, setAi, hostLog } from "./harness";
import { plan, findMoov, Unsupported } from "../src/mp4";

// ---------- a tiny MP4 writer ----------
const be32 = (n: number) => [(n >>> 24) & 255, (n >>> 16) & 255, (n >>> 8) & 255, n & 255];
const be16 = (n: number) => [(n >>> 8) & 255, n & 255];
const str = (s: string) => [...s].map((c) => c.charCodeAt(0));
const box = (type: string, ...parts: number[][]) => { const body = parts.flat(); return [...be32(8 + body.length), ...str(type), ...body]; };
const full = (type: string, ...parts: number[][]) => box(type, [0, 0, 0, 0], ...parts);
const desc = (tag: number, body: number[]) => [tag, body.length, ...body];

interface Opts {
  frames: number;
  /** frames per mp4 chunk */ perChunk?: number;
  sfi?: number; channels?: number; objType?: number; esdsObject?: number;
  moovFirst?: boolean; co64?: boolean; gap?: number; constSize?: number;
  twoStts?: boolean; hdlr?: string; codec?: string; videoGap?: number;
}
const frameSize = (i: number) => 180 + ((i * 37) % 120);

const cat = (...parts: (number[] | Uint8Array)[]) => {
  const out = new Uint8Array(parts.reduce((n, p) => n + p.length, 0));
  let w = 0;
  for (const p of parts) { out.set(p, w); w += p.length; }
  return out;
};

function buildM4a(o: Opts) {
  const perChunk = o.perChunk ?? 40;
  const sfi = o.sfi ?? 4, ch = o.channels ?? 2, obj = o.objType ?? 2;
  const sizes = new Array<number>(o.frames);
  for (let i = 0; i < o.frames; i++) sizes[i] = o.constSize ?? frameSize(i);
  const nChunks = Math.ceil(o.frames / perChunk);

  const asc = [(obj << 3) | (sfi >> 1), ((sfi & 1) << 7) | (ch << 3)];
  const esds = full("esds", desc(0x03, [0, 1, 0, ...desc(0x04, [o.esdsObject ?? 0x40, 0x15, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, ...desc(0x05, asc)]), ...desc(0x06, [2])]));
  const entry = box(o.codec ?? "mp4a", [0, 0, 0, 0, 0, 0], be16(1), [0, 0, 0, 0, 0, 0, 0, 0], be16(ch), be16(16), [0, 0, 0, 0], [0xac, 0x44, 0, 0], esds);
  const stsd = full("stsd", be32(1), entry);
  const half = Math.floor(o.frames / 2);
  const stts = o.twoStts ? full("stts", be32(2), be32(half), be32(1024), be32(o.frames - half), be32(2048)) : full("stts", be32(1), be32(o.frames), be32(1024));
  const stsc = full("stsc", be32(1), be32(1), be32(perChunk), be32(1));
  const sizeBytes: number[] = [];
  if (!o.constSize) for (const z of sizes) sizeBytes.push(...be32(z));
  const stsz = o.constSize ? full("stsz", be32(o.constSize), be32(o.frames)) : full("stsz", be32(0), be32(o.frames), sizeBytes);

  const ftyp = box("ftyp", str("M4A "), be32(0), str("M4A mp42isom"));
  const mdhd = full("mdhd", be32(0), be32(0), be32(44100), be32(o.frames * 1024), be16(0), be16(0));
  const hdlr = full("hdlr", be32(0), str(o.hdlr ?? "soun"), [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]);

  // mdat holds the chunks, with optional padding (another track's data) after each one
  const pad = o.videoGap ?? o.gap ?? 0;
  const rel: number[] = [];
  let total = 0;
  for (let c = 0; c < nChunks; c++) {
    rel.push(total);
    for (let i = c * perChunk; i < Math.min(o.frames, (c + 1) * perChunk); i++) total += sizes[i];
    total += pad;
  }
  const mdatBody = new Uint8Array(total).fill(0xee);
  for (let c = 0, w = 0; c < nChunks; c++) {
    for (let i = c * perChunk; i < Math.min(o.frames, (c + 1) * perChunk); i++) {
      mdatBody.fill(0, w, w + sizes[i]);
      mdatBody[w] = i & 255; mdatBody[w + 1] = (i >> 8) & 255;
      w += sizes[i];
    }
    w += pad;
  }
  const buildMoov = (base: number) => {
    const offs = rel.map((r) => base + 8 + r);
    const co = o.co64 ? full("co64", be32(nChunks), offs.flatMap((x) => [...be32(Math.floor(x / 2 ** 32)), ...be32(x >>> 0)])) : full("stco", be32(nChunks), offs.flatMap(be32));
    const stbl = box("stbl", stsd, stts, stsc, stsz, co);
    const trak = box("trak", box("mdia", mdhd, hdlr, box("minf", stbl)));
    return box("moov", box("mvhd", [0, 0, 0, 0, ...new Array(96).fill(0)]), trak);
  };
  const mdatHead = [...be32(8 + mdatBody.length), ...str("mdat")];
  let moov = buildMoov(0);
  if (o.moovFirst) { moov = buildMoov(ftyp.length + moov.length); return { bytes: cat(ftyp, moov, mdatHead, mdatBody) }; }
  moov = buildMoov(ftyp.length);
  return { bytes: cat(ftyp, mdatHead, mdatBody, moov) };
}

/** Walks an ADTS stream, checking every header, and returns the id of each frame. */
function readAdts(b: Uint8Array, sfi: number, ch: number, prof: number) {
  const ids: number[] = [];
  let p = 0;
  while (p < b.length) {
    if (b[p] !== 0xff || (b[p + 1] & 0xf6) !== 0xf0) throw new Error("bad sync at " + p);
    const profile = b[p + 2] >> 6, f = (b[p + 2] >> 2) & 15, c = ((b[p + 2] & 1) << 2) | (b[p + 3] >> 6);
    const len = ((b[p + 3] & 3) << 11) | (b[p + 4] << 3) | (b[p + 5] >> 5);
    if (profile !== prof || f !== sfi || c !== ch) throw new Error("bad header fields");
    if (p + len > b.length) throw new Error("frame runs past the end");
    ids.push(b[p + 7] | (b[p + 8] << 8));
    p += len;
  }
  return ids;
}

const { DB } = makeDb();
const env = makeEnv(DB);
env.MAX_PER_USER_PER_DAY = "100"; env.MAX_TRANSCRIPTS_PER_DAY = "100";
let r = await call(env, "POST", "/v1/users", "secret", { handle: "tester", name: "T" });
const T = r.j.token;

async function transcribe(name: string, file: Uint8Array, duration: number, type = "audio/mp4") {
  files.set(name, { bytes: file, type });
  queued.length = 0; aiCalls.length = 0;
  const res = await call(env, "POST", "/v1/transcripts", T, { epId: "ep_" + name, audioUrl: "https://cdn.example.com/" + name, durationSec: duration });
  return res;
}

// 1. plain M4A, index at the end, 3000 frames
{
  const m = buildM4a({ frames: 3000 });
  const res = await transcribe("a.m4a", m.bytes, 70);
  ok("m4a accepted", res.s === 202, res);
  const n = queued.length;
  ok("m4a split into several pieces", n >= 5 && n < 40, n);
  let ids: number[] = [];
  setAi((audio) => { ids.push(...readAdts(audio, 4, 2, 1)); return { segments: [{ start: 0, end: 1, text: " piece" }] }; });
  await drain(env);
  ok("every frame sent exactly once, in order", ids.length === 3000 && ids.every((v, i) => v === i), { got: ids.length });
  const done = await call(env, "GET", "/v1/transcripts/ep_a.m4a", T);
  ok("m4a transcript ready", done.j.status === "ready" && done.j.lines.length === n, done.j);
  const ts = done.j.lines.map((l: any) => l.t);
  ok("timestamps rise and end near the real length", ts.every((t: number, i: number) => i === 0 || t > ts[i - 1]) && ts[0] === 0 && ts[n - 1] > 60 && ts[n - 1] < 71, ts);
}

// 2. index first (fast-start), 64-bit offsets, padding between chunks, two timing entries
for (const [label, o] of [["moov first", { moovFirst: true }], ["co64", { co64: true }], ["padding between chunks", { gap: 500 }], ["two stts entries", { twoStts: true }], ["constant frame size", { constSize: 250 }], ["mono 22 kHz", { sfi: 7, channels: 1 }], ["tiny file", { frames: 20 }]] as [string, Partial<Opts>][]) {
  const o2: Opts = { frames: 1500, ...o };
  const m = buildM4a(o2);
  const res = await transcribe(`v${label.replace(/\W/g, "")}.m4a`, m.bytes, 35);
  ok(`${label}: accepted`, res.s === 202, res);
  const ids: number[] = [];
  setAi((audio) => { ids.push(...readAdts(audio, o2.sfi ?? 4, o2.channels ?? 2, 1)); return { segments: [{ start: 0, end: 1, text: " x" }] }; });
  await drain(env);
  ok(`${label}: all frames in order`, ids.length === o2.frames && ids.every((v, i) => v === i), ids.length);
}

// 3. things we refuse, with a message a person can read
for (const [label, o, word] of [
  ["not AAC (MP3 inside MP4)", { frames: 200, esdsObject: 0x6b }, "isn't AAC"],
  ["not mp4a (ALAC)", { frames: 200, codec: "alac" }, "isn't AAC"],
  ["no audio track", { frames: 200, hdlr: "vide" }, "no audio"],
  ["video mixed with audio", { frames: 3000, videoGap: 400_000, perChunk: 20 }, "video"],
] as [string, Opts, string][]) {
  const m = buildM4a(o);
  const res = await transcribe("bad.m4a", m.bytes, 35);
  ok(`${label}: refused clearly`, res.s === 422 && String(res.j.error).includes(word), res);
  const row = await call(env, "GET", "/v1/transcripts/ep_bad.m4a", T);
  ok(`${label}: nothing queued or stored`, queued.length === 0 && row.j.status === "none", row.j);
}

// 4. other formats are named, not mislabelled
for (const [name, head, why] of [["x.ogg", "OggS", "Ogg"], ["x.wav", "RIFF", "WAV"], ["x.flac", "fLaC", "FLAC"]] as const) {
  const b = new Uint8Array(5000); b.set([...head].map((c) => c.charCodeAt(0)));
  const res = await transcribe(name, b, 30, "audio/ogg");
  ok(`${why} refused with its name`, res.s === 422 && String(res.j.error).includes(why), res);
}

// 5. the file says mp3 by name but is really M4A, and the other way round
{
  const m = buildM4a({ frames: 600 });
  const res = await transcribe("disguised.mp3", m.bytes, 14, "audio/mpeg");
  ok("M4A hiding behind an .mp3 name is still handled as M4A", res.s === 202 && queued[0].body.aac, res);
  await drain(env);
  const mp3 = new Uint8Array(100_000).fill(1); mp3.set([0x49, 0x44, 0x33]);
  const res2 = await transcribe("real.m4a", mp3, 30, "audio/mp4");
  ok("MP3 with an .m4a name is handled as MP3", res2.s === 202 && !queued[0].body.aac, res2);
  await drain(env);
}

// 6. raw AAC (.aac, ADTS) is cut at byte positions and restarted at the next frame start
{
  const parts: number[][] = [];
  for (let i = 0; i < 1200; i++) {
    const len = frameSize(i) + 7, f = new Array(len).fill(0);
    f.splice(0, 7, 0xff, 0xf1, (1 << 6) | (4 << 2) | 0, (2 << 6) | ((len >> 11) & 3), (len >> 3) & 255, ((len & 7) << 5) | 0x1f, 0xfc);
    f[7] = i & 255; f[8] = (i >> 8) & 255;
    parts.push(f);
  }
  const res = await transcribe("raw.aac", cat(...parts), 28, "audio/aac");
  ok("raw AAC accepted", res.s === 202 && queued[0].body.fmt === "adts", res);
  const starts: number[] = [];
  setAi((audio) => { starts.push(audio[0] === 0xff && (audio[1] & 0xf6) === 0xf0 ? 1 : 0); return { segments: [{ start: 0, end: 1, text: " raw" }] }; });
  await drain(env);
  ok("every raw AAC piece starts on a frame", starts.length > 3 && starts.every((x) => x === 1), starts);
}

// 7. the old queue messages (MP3, no 'aac' field) still work: what is queued right now in production looks like this
{
  const { DB: DB2 } = makeDb("0002_social_ratings.sql");
  const env2 = makeEnv(DB2);
  const now = Date.now();
  await DB2.prepare("INSERT INTO transcripts (ep_id, status, progress, chunks_total, audio_url, duration_sec, requested_by, created_at, updated_at) VALUES ('old1','processing',0,2,'https://cdn.example.com/old.mp3',60,'u',?,?)").bind(now, now).run();
  files.set("old.mp3", { bytes: new Uint8Array(100_000).fill(3), type: "audio/mpeg" });
  setAi(() => ({ segments: [{ start: 0, end: 1, text: " old" }] }));
  queued.length = 0;
  queued.push({ body: { epId: "old1", idx: 0 } }, { body: { epId: "old1", idx: 1 } });
  await drain(env2);
  const row = await DB2.prepare("SELECT status, lines FROM transcripts WHERE ep_id='old1'").first();
  ok("queue messages from before this update still finish", row.status === "ready" && JSON.parse(row.lines).length === 2, row);
}

// 8. work and size at the largest allowed size
{
  const frames = 465_000; // about three hours of 44.1 kHz AAC
  const m = buildM4a({ frames, constSize: 190, perChunk: 43 });
  files.set("long.m4a", { bytes: m.bytes, type: "audio/mp4" });
  env.CHUNK_BYTES = "1048576";
  const before = hostLog.length;
  const t0 = performance.now();
  const res = await call(env, "POST", "/v1/transcripts", T, { epId: "ep_long", audioUrl: "https://cdn.example.com/long.m4a", durationSec: 10800 });
  const ms = performance.now() - t0;
  ok("three-hour episode is accepted", res.s === 202, res);
  const biggest = Math.max(...queued.map((q) => JSON.stringify(q).length));
  ok("every queue message is under 128 KB", biggest < 128_000, biggest);
  console.log(`INFO  three-hour episode: planned in ${ms.toFixed(0)} ms (includes test host and database), ${queued.length} pieces, largest message ${biggest} bytes`);
  const stats = hostLog.length - before;
  console.log(`INFO  requests to the audio host while planning: ${stats}`);
  queued.length = 0;
}

finish();
