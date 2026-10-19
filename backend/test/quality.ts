// Transcript quality: language hint, ID3 tag skipping, frame alignment, hallucination filtering, option fallback.
import { aiInputs, call, drain, files, finish, makeDb, makeEnv, ok, aiCalls, setAi } from "./harness";

const { DB } = makeDb();
const env = makeEnv(DB);
env.MAX_PER_USER_PER_DAY = "100";
env.MAX_TRANSCRIPTS_PER_DAY = "100";
env.WHISPER_DAILY_MINUTES = "100000";
const T = (await call(env, "POST", "/v1/users", "secret", { handle: "qual", name: "Q" })).j.token;
const CH = 65_536;

/** A synthetic MP3: optional ID3v2 tag filled with 0x55, then 417-byte MPEG1 Layer III frames at 128 kbps. */
function mp3(frames: number, tagBytes = 0) {
  const out = new Uint8Array(tagBytes + frames * 417);
  if (tagBytes) {
    const n = tagBytes - 10;
    out.set([0x49, 0x44, 0x33, 3, 0, 0, (n >> 21) & 0x7f, (n >> 14) & 0x7f, (n >> 7) & 0x7f, n & 0x7f], 0);
    out.fill(0x55, 10, tagBytes);
  }
  for (let f = 0; f < frames; f++) { const o = tagBytes + f * 417; out.set([0xff, 0xfb, 0x90, 0x00], o); out.fill(0x11, o + 4, o + 417); }
  return out;
}
const reset = () => { aiInputs.length = 0; aiCalls.length = 0; };

// --- language hint ---
files.set("de.mp3", { bytes: mp3(600), type: "audio/mpeg" });
setAi(() => ({ segments: [{ start: 0, end: 2, text: " Hallo." }] }));
let r = await call(env, "POST", "/v1/transcripts", T, { epId: "de", audioUrl: "https://cdn.example.com/de.mp3", durationSec: 60, language: "de-DE" });
await drain(env);
ok("language hint reaches the model as a plain code", aiInputs.length > 0 && aiInputs.every((i) => i.language === "de"), aiInputs[0]);
ok("vad filter on, previous-text conditioning off", aiInputs[0].vad_filter === true && aiInputs[0].condition_on_previous_text === false);
reset();
files.set("x.mp3", { bytes: mp3(600), type: "audio/mpeg" });
await call(env, "POST", "/v1/transcripts", T, { epId: "x", audioUrl: "https://cdn.example.com/x.mp3", durationSec: 60, language: "<script>" });
await drain(env);
ok("a junk language value is ignored", aiInputs.length > 0 && aiInputs.every((i) => i.language === undefined));

// --- the model rejects the extra options: fall back to the plain call ---
reset();
files.set("fb.mp3", { bytes: mp3(300), type: "audio/mpeg" });
setAi((_a, i) => { if (i.vad_filter !== undefined) throw new Error("AiError: invalid input property vad_filter"); return { segments: [{ start: 0, end: 1, text: " Ok." }] }; });
await call(env, "POST", "/v1/transcripts", T, { epId: "fb", audioUrl: "https://cdn.example.com/fb.mp3", durationSec: 30, language: "en" });
await drain(env);
r = await call(env, "GET", "/v1/transcripts/fb", T);
ok("still transcribes when the model refuses the options", r.j.status === "ready", r.j);

// --- ID3 tag (cover art) is never sent to the model, and slices start on a frame ---
reset();
const TAG = 150_000; // bigger than two slices
const f = mp3(500, TAG);
files.set("art.mp3", { bytes: f, type: "audio/mpeg" });
setAi(() => ({ segments: [{ start: 0, end: 1, text: " Wort." }] }));
await call(env, "POST", "/v1/transcripts", T, { epId: "art", audioUrl: "https://cdn.example.com/art.mp3", durationSec: 100 });
await drain(env);
r = await call(env, "GET", "/v1/transcripts/art", T);
ok("episode with a big cover-art tag is ready", r.j.status === "ready", r.j);
ok("no tag bytes were sent to the model", aiCalls.length > 0 && aiCalls.every((b) => !b.includes(0x55)));
ok("every slice starts on an MP3 frame", aiCalls.every((b) => b[0] === 0xff && (b[1] & 0xe0) === 0xe0));
ok("slices that are only tag are not sent", aiCalls.length === Math.ceil(f.length / CH) - Math.floor(TAG / CH));
const ts = r.j.lines.map((l: any) => l.t);
ok("timestamps rise and stay inside the episode", ts.every((t: number, i: number) => i === 0 || t >= ts[i - 1]) && Math.max(...ts) <= 100 && ts[0] >= 0, ts);

// --- invented text is filtered ---
reset();
files.set("h.mp3", { bytes: mp3(120), type: "audio/mpeg" });
setAi(() => ({ segments: [
  { start: 0, end: 1, text: " Guten Tag." },
  { start: 1, end: 2, text: " Danke." }, { start: 2, end: 3, text: " Danke." }, { start: 3, end: 4, text: " Danke." }, { start: 4, end: 5, text: " Danke." },
  { start: 5, end: 6, text: " Musik.", no_speech_prob: 0.95, avg_logprob: -1.5 },
  { start: 6, end: 7, text: " blah blah blah", compression_ratio: 3.4 },
  { start: 7, end: 8, text: " Tschüss." },
] }));
await call(env, "POST", "/v1/transcripts", T, { epId: "h", audioUrl: "https://cdn.example.com/h.mp3", durationSec: 10 });
await drain(env);
r = await call(env, "GET", "/v1/transcripts/h", T);
const texts = r.j.lines.map((l: any) => l.text).join("|");
ok("loops, no-speech and runaway lines are dropped, real lines kept", r.j.status === "ready" && /Guten Tag/.test(texts) && /Tschüss/.test(texts) && !/Musik|blah/.test(texts) && (texts.match(/Danke/g) || []).length === 2, texts);

// --- retry after a failure starts a new transcription ---
reset();
files.set("rf.mp3", { bytes: mp3(300), type: "audio/mpeg" });
setAi(() => { throw new Error("AiError: broken"); });
await call(env, "POST", "/v1/transcripts", T, { epId: "rf", audioUrl: "https://cdn.example.com/rf.mp3", durationSec: 30 });
// exhaust attempts on every slice
const { worker } = await import("./harness");
const { queued } = await import("./harness");
while (queued.length) { const j: any = queued.shift(); await worker.queue({ messages: [{ body: j.body, attempts: 4, ack() {}, retry() {} }] } as any, env); }
r = await call(env, "GET", "/v1/transcripts/rf", T);
ok("it failed", r.j.status === "failed", r.j);
setAi(() => ({ segments: [{ start: 0, end: 1, text: " Jetzt geht es." }] }));
r = await call(env, "POST", "/v1/transcripts", T, { epId: "rf", audioUrl: "https://cdn.example.com/rf.mp3", durationSec: 30 });
ok("asking again after a failure is accepted", r.s === 202, r);
await drain(env);
r = await call(env, "GET", "/v1/transcripts/rf", T);
ok("and now it works", r.j.status === "ready" && r.j.lines.length > 0, r.j);
// --- timing comes from the file itself, not from the feed's (often wrong) duration ---
function withXing(frames: number, kind = "Xing") {
  const f = mp3(frames + 1);
  const at = 4 + 32;
  f.fill(0, 4, 417);
  f.set([...kind].map((c) => c.charCodeAt(0)), at);
  f[at + 7] = 7; // frames, bytes, toc
  f.set([(frames >>> 24) & 255, (frames >>> 16) & 255, (frames >>> 8) & 255, frames & 255], at + 8);
  for (let k = 0; k < 100; k++) f[at + 16 + k] = Math.floor((k * 256) / 100);
  return f;
}
async function lastStart(name: string, bytes: Uint8Array, clientSec: number) {
  files.set(name, { bytes, type: "audio/mpeg" });
  setAi(() => ({ segments: [{ start: 0, end: 1, text: " Wort." }] }));
  await call(env, "POST", "/v1/transcripts", T, { epId: name, audioUrl: "https://cdn.example.com/" + name, durationSec: clientSec });
  await drain(env);
  const out = await call(env, "GET", "/v1/transcripts/" + name, T);
  return (out.j.lines as { t: number }[]).map((l) => l.t);
}
let t1 = await lastStart("xing.mp3", withXing(1000), 5000); // real length 26.1 s
ok("Xing frame count beats a wrong feed duration", Math.max(...t1) <= 27 && Math.max(...t1) >= 20, t1);
let t2 = await lastStart("cbr.mp3", mp3(1000), 5000); // 128 kbps constant: 26 s
ok("constant bitrate beats a wrong feed duration", Math.max(...t2) <= 27 && Math.max(...t2) >= 20, t2);
let t3 = await lastStart("nofeed.mp3", mp3(1000), 0);
ok("works with no feed duration at all", Math.max(...t3) <= 27 && Math.max(...t3) >= 20, t3);
let t4 = await lastStart("info.mp3", withXing(1000, "Info"), 5000);
ok("Info (constant bitrate) header works", Math.max(...t4) <= 27 && Math.max(...t4) >= 20, t4);

// --- redo a finished transcript on request only ---
reset();
setAi(() => ({ segments: [{ start: 0, end: 1, text: " Zweite Fassung." }] }));
r = await call(env, "POST", "/v1/transcripts", T, { epId: "rf", audioUrl: "https://cdn.example.com/rf.mp3", durationSec: 30 });
ok("asking for a finished transcript just returns it", r.s === 200 && r.j.status === "ready" && aiCalls.length === 0, r);
r = await call(env, "POST", "/v1/transcripts", T, { epId: "rf", audioUrl: "https://cdn.example.com/rf.mp3", durationSec: 30, force: true });
ok("force redoes it", r.s === 202, r);
await drain(env);
r = await call(env, "GET", "/v1/transcripts/rf", T);
ok("the new text replaces the old", r.j.status === "ready" && r.j.lines[0].text === "Zweite Fassung.", r.j);
finish();
