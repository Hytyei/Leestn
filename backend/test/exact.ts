// MP3 slice times must come from counting frames, not from bytes: in variable-bitrate audio bytes are not proportional to time.
import { call, drain, files, finish, makeDb, makeEnv, ok, setAi, worker, queued } from "./harness";

const { DB } = makeDb();
const env = makeEnv(DB);
env.MAX_PER_USER_PER_DAY = "100"; env.MAX_TRANSCRIPTS_PER_DAY = "100"; env.WHISPER_DAILY_MINUTES = "100000";
const T = (await call(env, "POST", "/v1/users", "secret", { handle: "exact", name: "E" })).j.token;
const CH = 65_536;

// frame lengths at 44.1 kHz MPEG1 Layer III, by bitrate index (padding bit always 0)
const BR: Record<number, [number, number]> = { 1: [32, 104], 5: [64, 208], 9: [128, 417], 11: [192, 626] };
const keys = [1, 5, 9, 11];
function vbr(frames: number, seed: number, id3 = 0) {
  let x = seed;
  const rnd = () => { x = (x * 1103515245 + 12345) & 0x7fffffff; return x; };
  const starts: number[] = [];
  const parts: Uint8Array[] = [];
  let pos = id3;
  const tag = new Uint8Array(id3);
  if (id3) { const n = id3 - 10; tag.set([0x49, 0x44, 0x33, 3, 0, 0, (n >> 21) & 0x7f, (n >> 14) & 0x7f, (n >> 7) & 0x7f, n & 0x7f]); tag.fill(0x55, 10); }
  parts.push(tag);
  // long stretches of one bitrate, so the bytes-per-second rate changes a lot across the file
  let bi = 9, left = 0;
  for (let i = 0; i < frames; i++) {
    if (left-- <= 0) { bi = keys[rnd() % 4]; left = 200 + (rnd() % 600); }
    const [, len] = BR[bi];
    const f = new Uint8Array(len).fill(0x11);
    f.set([0xff, 0xfb, bi << 4, 0x00]);
    starts.push(pos); parts.push(f); pos += len;
  }
  const out = new Uint8Array(pos); let w = 0; for (const p of parts) { out.set(p, w); w += p.length; }
  return { bytes: out, starts };
}
const FRAME_SEC = 1152 / 44100;

async function check(name: string, id3: number) {
  const { bytes, starts } = vbr(5000, 42 + id3, id3);
  files.set(name, { bytes, type: "audio/mpeg" });
  setAi(() => ({ segments: [{ start: 0, end: 1, text: " S." }] }));
  // the feed says 9999 s, which is wrong, and the server must not depend on it
  await call(env, "POST", "/v1/transcripts", T, { epId: name, audioUrl: "https://cdn.example.com/" + name, durationSec: 9999 });
  // process the slices in a scrambled order, like a real queue
  const jobs: any[] = []; while (queued.length) jobs.push(queued.shift());
  jobs.sort((a, b) => ((a.body.idx * 7919) % 13) - ((b.body.idx * 7919) % 13));
  for (const j of jobs) await worker.queue({ messages: [{ body: j.body, attempts: 1, ack() {}, retry() {} }] } as any, env);
  const r = await call(env, "GET", "/v1/transcripts/" + name, T);
  const lines = r.j.lines as { t: number }[];
  let worst = 0, n = 0;
  for (let k = 0; k * CH < bytes.length; k++) {
    if (k * CH + CH <= id3) continue; // a slice that is only tag has no line
    const startOfSlice = Math.max(k * CH, id3);
    const before = starts.filter((s) => s < startOfSlice).length;
    const want = Math.round(before * FRAME_SEC);
    const got = lines[n++]?.t ?? -1000;
    worst = Math.max(worst, Math.abs(got - want));
  }
  return { status: r.j.status, worst, lines: lines.length, total: Math.round(starts.length * FRAME_SEC), n };
}

let res = await check("vbr.mp3", 0);
ok("VBR file: ready, one line per slice", res.status === "ready" && res.lines === res.n, res);
ok("VBR file: every slice starts at its true time (within 1 s), however the slices were ordered", res.worst <= 1, res);
res = await check("vbr-tag.mp3", 200_000);
ok("VBR file with a big cover-art tag: still exact", res.status === "ready" && res.worst <= 1, res);

// the old byte-fraction estimate would have been far off for this file
const { bytes, starts } = vbr(5000, 42);
const byteGuess = (CH * 5) / bytes.length * (starts.length * FRAME_SEC);
const trueT = starts.filter((s) => s < CH * 5).length * FRAME_SEC;
ok("(this file really is VBR: byte estimate is off by more than 5 s)", Math.abs(byteGuess - trueT) > 5, { byteGuess, trueT });
finish();
