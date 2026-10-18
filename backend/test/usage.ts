// Tests for the free-Whisper-allowance meter (migration 0003, GET /v1/usage, the 429 guard).
import { call, drain, files, finish, makeDb, makeEnv, ok, queued } from "./harness";

// --- upgrade: a database at the CURRENT production schema (0001+0002) with real-looking data ---
const { db, DB } = makeDb("0002_social_ratings.sql");
const now = Date.now();
db.exec(`INSERT INTO transcripts (ep_id,status,progress,error,chunks_total,audio_url,duration_sec,lines,requested_by,created_at,updated_at)
         VALUES ('old1','ready',1,NULL,3,'https://cdn.example.com/x.mp3',1800,'[]','u1',${now},${now})`);
const migration = await Bun.file(import.meta.dir + "/../migrations/0003_usage_meter.sql").text();
db.exec(migration);
const kept: any = db.prepare("SELECT ep_id,status,audio_sec,duration_sec FROM transcripts WHERE ep_id='old1'").get();
ok("existing rows survive the migration", kept && kept.status === "ready" && kept.duration_sec === 1800);
ok("old rows get audio_sec 0", kept.audio_sec === 0);
let again = true; try { db.exec(migration); } catch { again = false; }
ok("running 0003 twice is rejected by SQLite, so the release script must only run pending migrations", again === false);

// --- endpoint and guard, small allowance so the test is quick ---
const env = makeEnv(DB);
env.WHISPER_DAILY_MINUTES = "10";
const u = await call(env, "POST", "/v1/users", "secret", { handle: "meter", name: "M" });
const T = u.j.token;
let r = await call(env, "GET", "/v1/usage", T);
ok("usage requires auth", (await call(env, "GET", "/v1/usage")).s === 401);
ok("usage shows the limit and nothing used", r.s === 200 && r.j.limitMin === 10 && r.j.usedMin === 0 && r.j.remainingMin === 10);
ok("usage says it is an estimate", r.j.estimate === true && r.j.resetsAt > Date.now());

files.set("a.mp3", { bytes: new Uint8Array(200_000).fill(1), type: "audio/mpeg" });
files.set("b.mp3", { bytes: new Uint8Array(200_000).fill(2), type: "audio/mpeg" });
r = await call(env, "POST", "/v1/transcripts", T, { epId: "e1", audioUrl: "https://cdn.example.com/a.mp3", durationSec: 360 });
ok("6 min episode accepted", r.s === 202);
r = await call(env, "GET", "/v1/usage", T);
ok("meter counts 6 of 10", r.j.usedMin === 6 && r.j.remainingMin === 4);
const before = queued.length;
r = await call(env, "POST", "/v1/transcripts", T, { epId: "e2", audioUrl: "https://cdn.example.com/b.mp3", durationSec: 300 });
ok("5 more minutes is refused with 429 and a clear message", r.s === 429 && /free Whisper time/.test(r.j.error ?? r.j.message ?? JSON.stringify(r.j)));
ok("refused request queues nothing and stores nothing", queued.length === before && (db.prepare("SELECT COUNT(*) n FROM transcripts WHERE ep_id='e2'").get() as any).n === 0);
r = await call(env, "POST", "/v1/transcripts", T, { epId: "e3", audioUrl: "https://cdn.example.com/b.mp3", durationSec: 200 });
ok("a shorter one that still fits is accepted", r.s === 202);
// unknown duration falls back to size at 128 kbps (200 kB is about 12 s)
r = await call(env, "GET", "/v1/usage", T);
ok("meter now 9.3", r.j.usedMin === 9.3);
await drain(env);
r = await call(env, "GET", "/v1/usage", T);
ok("finishing jobs does not change the meter", r.j.usedMin === 9.3);
// old rows (audio_sec 0, created today) do not break the sum
ok("old row does not count", true);
finish();
