import { Database } from "bun:sqlite";
import { readFileSync, readdirSync } from "fs";
import worker from "../src/index";

/** A throwaway SQLite database built from the real migration files, standing in for D1. */
export function makeDb(upTo?: string) {
  const db = new Database(":memory:");
  for (const f of readdirSync(import.meta.dir + "/../migrations").sort()) {
    if (upTo && f > upTo) break;
    db.exec(readFileSync(import.meta.dir + "/../migrations/" + f, "utf8"));
  }
  class Stmt {
    args: any[] = [];
    constructor(public sql: string) {}
    bind(...a: any[]) { this.args = a; return this; }
    async first() { return (db.prepare(this.sql).get(...this.args) as any) ?? null; }
    async all() { return { results: db.prepare(this.sql).all(...this.args) as any[] }; }
    async run() { db.prepare(this.sql).run(...this.args); return {}; }
  }
  const DB: any = {
    prepare: (q: string) => new Stmt(q),
    batch: async (ss: Stmt[]) => { db.exec("BEGIN"); try { for (const s of ss) await s.run(); db.exec("COMMIT"); } catch (e) { db.exec("ROLLBACK"); throw e; } return []; },
  };
  return { db, DB };
}

export const queued: any[] = [];
export const aiCalls: Uint8Array[] = [];
export let aiImpl: (audio: Uint8Array) => any = () => ({ text: "Hello there. This is a test.", segments: [{ start: 0, end: 2, text: " Hello there." }, { start: 2, end: 4, text: " This is a test." }] });
export function setAi(f: (audio: Uint8Array) => any) { aiImpl = f; }

export function makeEnv(DB: any): any {
  return {
    DB,
    AI: { run: async (_m: string, i: any) => { const bin = Uint8Array.from(atob(i.audio), (c) => c.charCodeAt(0)); aiCalls.push(bin); return aiImpl(bin); } },
    TRANSCRIBE: { sendBatch: async (m: any[]) => { for (const x of m) { if (JSON.stringify(x).length > 128_000) throw new Error("queue message too large"); } if (m.length > 100) throw new Error("batch too big"); queued.push(...m); } },
    INVITE_KEY: "secret", MAX_TRANSCRIPTS_PER_DAY: "40", MAX_PER_USER_PER_DAY: "8", CHUNK_BYTES: "65536", MAX_AUDIO_MB: "150", ALLOW_ORIGIN: "*",
  };
}

/** Fake podcast host. Files are registered by path; it answers HEAD and Range requests like a real one. */
export const files = new Map<string, { bytes: Uint8Array; type?: string }>();
export const hostLog: string[] = [];
const realFetch = globalThis.fetch;
globalThis.fetch = (async (u: any, init: any = {}) => {
  const url = String(u);
  if (url.startsWith("https://cdn.example.com/")) {
    const f = files.get(url.slice("https://cdn.example.com/".length));
    if (!f) return new Response("nope", { status: 404 });
    const type = f.type ?? "application/octet-stream";
    const m = init.method || "GET";
    hostLog.push(m + " " + (init.headers?.range ?? ""));
    if (m === "HEAD") return new Response(null, { headers: { "content-length": String(f.bytes.length), "content-type": type } });
    const r = /bytes=(\d+)-(\d+)/.exec(init.headers?.range || "");
    if (r) { const a = +r[1], b = Math.min(+r[2], f.bytes.length - 1); return new Response(f.bytes.slice(a, b + 1), { status: 206, headers: { "content-range": `bytes ${a}-${b}/${f.bytes.length}`, "content-type": type } }); }
    return new Response(f.bytes, { headers: { "content-type": type } });
  }
  return realFetch(u, init);
}) as any;

export { worker };

export async function call(env: any, method: string, path: string, token = "", body?: any) {
  const req = new Request("https://w.test" + path, { method, headers: { ...(token ? { authorization: "Bearer " + token } : {}), "content-type": "application/json" }, body: body ? JSON.stringify(body) : undefined });
  const res = await worker.fetch(req, env);
  const t = await res.text();
  let j: any; try { j = JSON.parse(t); } catch { j = t; }
  return { s: res.status, j };
}

/** Runs every queued message through the consumer, like the real queue would. Returns how many were processed. */
export async function drain(env: any, opts: { failAfter?: number } = {}) {
  let n = 0;
  while (queued.length) {
    const j = queued.shift();
    let acked = false;
    await worker.queue({ messages: [{ body: j.body, attempts: 1, ack: () => { acked = true; }, retry: () => { acked = true; } }] } as any, env);
    n++;
    if (!acked) throw new Error("message neither acked nor retried");
  }
  return n;
}

let fails = 0;
export function ok(name: string, cond: boolean, extra?: any) { console.log((cond ? "PASS " : "FAIL ") + name + (cond ? "" : " " + JSON.stringify(extra))); if (!cond) fails++; }
export function finish() { console.log(fails ? "FAILURES: " + fails : "ALL PASSED"); if (fails) process.exit(1); }
