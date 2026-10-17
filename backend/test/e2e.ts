import { Database } from "bun:sqlite";
import { readFileSync, readdirSync } from "fs";
import worker from "../src/index";

const db = new Database(":memory:");
for (const f of readdirSync(import.meta.dir + "/../migrations").sort()) db.exec(readFileSync(import.meta.dir + "/../migrations/" + f, "utf8"));

class Stmt {
  args: any[] = [];
  constructor(public sql: string) {}
  bind(...a: any[]) { this.args = a; return this; }
  async first() { return (db.prepare(this.sql).get(...this.args) as any) ?? null; }
  async all() { return { results: db.prepare(this.sql).all(...this.args) as any[] }; }
  async run() { db.prepare(this.sql).run(...this.args); return {}; }
}
const DB: any = { prepare: (q: string) => new Stmt(q), batch: async (ss: Stmt[]) => { db.exec("BEGIN"); try { for (const s of ss) await s.run(); db.exec("COMMIT"); } catch (e) { db.exec("ROLLBACK"); throw e; } return []; } };

const queued: any[] = [];
const AI: any = { run: async (_m: string, i: any) => ({ text: "Hello there. This is a test.", segments: [{ start: 0, end: 2, text: " Hello there." }, { start: 2, end: 4, text: " This is a test." }], _len: i.audio.length }) };
const env: any = { DB, AI, TRANSCRIBE: { sendBatch: async (m: any[]) => { queued.push(...m); } }, INVITE_KEY: "secret", MAX_TRANSCRIPTS_PER_DAY: "40", MAX_PER_USER_PER_DAY: "8", CHUNK_BYTES: "65536", MAX_AUDIO_MB: "150", ALLOW_ORIGIN: "*" };

// fake audio host
const audio = new Uint8Array(200_000).fill(7);
const realFetch = globalThis.fetch;
globalThis.fetch = (async (u: any, init: any = {}) => {
  const url = String(u);
  if (url.startsWith("https://cdn.example.com/")) {
    const m = init.method || "GET";
    if (m === "HEAD") return new Response(null, { headers: { "content-length": String(audio.length), "content-type": "audio/mpeg" } });
    const r = /bytes=(\d+)-(\d+)/.exec(init.headers?.range || "");
    if (r) { const a = +r[1], b = Math.min(+r[2], audio.length - 1); return new Response(audio.slice(a, b + 1), { status: 206, headers: { "content-range": `bytes ${a}-${b}/${audio.length}` } }); }
    return new Response(audio);
  }
  return realFetch(u, init);
}) as any;

async function call(method: string, path: string, token = "", body?: any) {
  const req = new Request("https://w.test" + path, { method, headers: { ...(token ? { authorization: "Bearer " + token } : {}), "content-type": "application/json" }, body: body ? JSON.stringify(body) : undefined });
  const res = await worker.fetch(req, env);
  const t = await res.text();
  let j: any; try { j = JSON.parse(t); } catch { j = t; }
  return { s: res.status, j };
}
let fails = 0;
function ok(name: string, cond: boolean, extra?: any) { console.log((cond ? "PASS " : "FAIL ") + name + (cond ? "" : " " + JSON.stringify(extra))); if (!cond) fails++; }

let r = await call("GET", "/health"); ok("health", r.s === 200);
r = await call("POST", "/v1/users", "wrong", { handle: "alice", name: "Alice" }); ok("signup wrong key 401", r.s === 401, r);
r = await call("POST", "/v1/users", "secret", { handle: "alice", name: "Alice", bio: "hi" }); ok("signup alice", r.s === 201 && r.j.token, r);
const A = r.j.token;
r = await call("POST", "/v1/users", "secret", { handle: "alice" }); ok("dup handle 409", r.s === 409, r);
r = await call("POST", "/v1/users", "secret", { handle: "bob", name: "Bob" }); const B = r.j.token; ok("signup bob", !!B, r);
r = await call("GET", "/v1/me", A); ok("me", r.s === 200 && r.j.handle === "alice", r);
r = await call("PATCH", "/v1/me", A, { name: "Alice A", bio: "podcast nerd" }); ok("patch me", r.s === 200, r);

// push sync
const log = [
  { epId: "s1_a1", showId: "s1", showName: "Show One", showArt: "http://x/a.jpg", feedUrl: "https://f/1.xml", epTitle: "Ep 1", genre: "Comedy", minutes: 40, kind: "main", listenedAt: "2026-10-10", rating: 8, hour: 21 },
  { epId: "s1_a2", showId: "s1", showName: "Show One", epTitle: "Ep 2", minutes: 30, kind: "main", listenedAt: "2026-10-11", rating: 6, hour: 8 },
];
r = await call("PUT", "/v1/sync", A, { log, showRatings: [{ showId: "s1", showName: "Show One", rating: 9 }], badges: [{ id: "listener", tier: 1 }] }); ok("push alice", r.s === 200, r);
r = await call("PUT", "/v1/sync", B, { log: [{ epId: "s1_a1", showId: "s1", showName: "Show One", epTitle: "Ep 1", minutes: 40, listenedAt: "2026-10-12", rating: 10 }] }); ok("push bob", r.s === 200, r);

r = await call("POST", "/v1/follow/alice", B); ok("bob follows alice", r.s === 200, r);
r = await call("GET", "/v1/feed", B); ok("feed has alice items", r.s === 200 && r.j.items.length >= 1 && r.j.items[0].user.handle === "alice", r);
r = await call("GET", "/v1/users?q=ali", B); ok("search people", r.s === 200 && r.j.users.some((u: any) => u.handle === "alice" && u.following === true), r);
r = await call("GET", "/v1/users/alice", B); ok("profile alice", r.s === 200 && r.j.stats.episodes === 2 && r.j.isFollowing === true, r);
r = await call("GET", "/v1/shows/s1/ratings", B); ok("group ratings", r.s === 200 && r.j.episodes["s1_a1"].avg === 9 && r.j.showAvg === 9, r);
r = await call("GET", "/v1/users/alice/wrapped/2026", B); ok("wrapped", r.s === 200, r);

// avatar
const jpg = new Uint8Array(5000).fill(5); jpg.set([0xff,0xd8,0xff,0xe0]);
let res = await worker.fetch(new Request("https://w.test/v1/me/avatar", { method: "PUT", headers: { authorization: "Bearer " + A, "content-type": "image/jpeg" }, body: jpg }), env);
ok("put avatar", res.status === 200, await res.text());
res = await worker.fetch(new Request("https://w.test/v1/users/alice/avatar"), env);
ok("get avatar", res.status === 200 && res.headers.get("content-type") === "image/jpeg", res.status);

// transcripts
r = await call("GET", "/v1/transcripts/s1_a1", A); ok("tx none", r.j.status === "none", r);
r = await call("POST", "/v1/transcripts", A, { epId: "s1_a1", audioUrl: "http://insecure/x.mp3", durationSec: 100 }); ok("tx http refused", r.s === 400, r);
r = await call("POST", "/v1/transcripts", A, { epId: "s1_a1", audioUrl: "https://cdn.example.com/a.mp3", durationSec: 100 }); ok("tx queued", r.s === 202 && queued.length === 4, { r, q: queued.length });
const jobs = queued.splice(0);
for (const j of jobs) {
  let acked = false;
  await worker.queue({ messages: [{ body: j.body, attempts: 1, ack: () => { acked = true; }, retry: () => { throw new Error("retry"); } }] } as any, env);
  if (!acked) fails++;
  if (j !== jobs[jobs.length - 1]) { r = await call("GET", "/v1/transcripts/s1_a1", A); ok("tx progress", r.j.status === "processing" && r.j.progress > 0, r); }
}
r = await call("GET", "/v1/transcripts/s1_a1", B); ok("tx ready for another user", r.j.status === "ready" && r.j.lines.length === 8 && r.j.lines[2].t >= 25, r.j);
r = await call("POST", "/v1/transcripts", B, { epId: "s1_a1", audioUrl: "https://cdn.example.com/a.mp3", durationSec: 100 }); ok("tx repeat returns ready", r.j.status === "ready" && queued.length === 0, r);
// per-user cap
env.MAX_PER_USER_PER_DAY = "1";
r = await call("POST", "/v1/transcripts", A, { epId: "s1_zz", audioUrl: "https://cdn.example.com/b.mp3", durationSec: 100 }); ok("per-user cap 429", r.s === 429, r);

r = await call("DELETE", "/v1/follow/alice", B); ok("unfollow", r.s === 200, r);
r = await call("DELETE", "/v1/me", A); ok("delete me", r.s === 200, r);
r = await call("GET", "/v1/me", A); ok("deleted token dead", r.s === 401, r);
console.log(fails ? "FAILURES: " + fails : "ALL PASSED");
