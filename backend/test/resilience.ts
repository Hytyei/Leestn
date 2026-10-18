// A bad slice or an unfriendly host must not silently kill a whole episode, and errors must say what happened.
import { call, files, finish, makeDb, makeEnv, ok, queued, setAi, worker } from "./harness";

const { DB } = makeDb();
const env = makeEnv(DB);
const T = (await call(env, "POST", "/v1/users", "secret", { handle: "res", name: "R" })).j.token;
const CH = 65_536;

async function run(failIdx: number[], attemptsForFailing = 4) {
  while (queued.length) {
    const j = queued.shift();
    const attempts = failIdx.includes(j.body.idx) ? attemptsForFailing : 1;
    await worker.queue({ messages: [{ body: j.body, attempts, ack() {}, retry() {} }] } as any, env);
  }
}
const mp3 = (n: number) => { const b = new Uint8Array(n).fill(7); b[0] = 0xff; b[1] = 0xfb; return b; };

// one slice the model always rejects: the transcript still finishes, with a note
files.set("one.mp3", { bytes: mp3(CH * 10), type: "audio/mpeg" });
let n = 0;
setAi(() => { n++; if (n === 3) throw new Error("AiError: bad audio"); return { segments: [{ start: 0, end: 2, text: " Hallo." }] }; });
let r = await call(env, "POST", "/v1/transcripts", T, { epId: "one", audioUrl: "https://cdn.example.com/one.mp3", durationSec: 100 });
ok("request accepted", r.s === 202);
await run([2]);
r = await call(env, "GET", "/v1/transcripts/one", T);
ok("episode with one bad slice is still ready", r.j.status === "ready" && r.j.lines.length === 9, r.j);
ok("it says a part is missing", /1 short part/.test(r.j.error ?? ""), r.j);

// every slice rejected: failed, and the message names the real error
files.set("bad.mp3", { bytes: mp3(CH * 8), type: "audio/mpeg" });
setAi(() => { throw new Error("AiError: model overloaded"); });
await call(env, "POST", "/v1/transcripts", T, { epId: "bad", audioUrl: "https://cdn.example.com/bad.mp3", durationSec: 80 });
await run([0, 1, 2, 3, 4, 5, 6, 7]);
r = await call(env, "GET", "/v1/transcripts/bad", T);
ok("all slices failing fails the episode with the reason", r.j.status === "failed" && /model overloaded/.test(r.j.error), r.j);

// retries below the limit do not skip anything
files.set("retry.mp3", { bytes: mp3(CH * 2), type: "audio/mpeg" });
setAi(() => ({ segments: [{ start: 0, end: 1, text: " Hi." }] }));
await call(env, "POST", "/v1/transcripts", T, { epId: "retry", audioUrl: "https://cdn.example.com/retry.mp3", durationSec: 20 });
await run([]);
r = await call(env, "GET", "/v1/transcripts/retry", T);
ok("normal episode unchanged", r.j.status === "ready" && !r.j.error, r.j);

// host answers 403: a clear message that names the host
r = await call(env, "POST", "/v1/transcripts", T, { epId: "gone", audioUrl: "https://cdn.example.com/missing.mp3", durationSec: 20 });
ok("missing/blocked audio gives a readable error", r.s === 422 && /cdn\.example\.com/.test(JSON.stringify(r.j)) && /HTTP 404/.test(JSON.stringify(r.j)), r.j);
finish();
