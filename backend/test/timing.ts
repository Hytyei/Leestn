// Shared timing fixes: migration 0004 on top of the current schema, the PUT/GET API, and reset on regeneration.
import { call, drain, files, finish, makeDb, makeEnv, ok } from "./harness";

// --- upgrade from the current production schema (0001..0003) with an existing finished transcript ---
const { db, DB } = makeDb("0003_usage_meter.sql");
const now = Date.now();
db.exec(`INSERT INTO transcripts (ep_id,status,progress,error,chunks_total,audio_url,duration_sec,lines,requested_by,created_at,updated_at,audio_sec)
         VALUES ('old1','ready',1,NULL,3,'https://cdn.example.com/x.mp3',1800,'[{"t":0,"text":"a"},{"t":60,"text":"b"}]','u1',${now},${now},1800)`);
const env = makeEnv(DB);
env.WHISPER_DAILY_MINUTES = "100000"; env.MAX_PER_USER_PER_DAY = "50"; env.MAX_TRANSCRIPTS_PER_DAY = "50";
const A = (await call(env, "POST", "/v1/users", "secret", { handle: "ann", name: "A" })).j.token;
const B = (await call(env, "POST", "/v1/users", "secret", { handle: "bob", name: "B" })).j.token;

// the new Worker must keep serving transcripts even before the migration has run
let r = await call(env, "GET", "/v1/transcripts/old1", A);
ok("reading works before migration 0004 is applied", r.s === 200 && r.j.status === "ready" && r.j.lines.length === 2 && r.j.timing === undefined, r);

db.exec(await Bun.file(import.meta.dir + "/../migrations/0004_transcript_timing.sql").text());
ok("old transcript survives the migration", (db.prepare("SELECT COUNT(*) n FROM transcripts WHERE ep_id='old1'").get() as any).n === 1);
db.exec(await Bun.file(import.meta.dir + "/../migrations/0004_transcript_timing.sql").text());
ok("0004 can be run twice without error (idempotent)", true);

r = await call(env, "GET", "/v1/transcripts/old1", A);
ok("no fix saved yet: no timing field", r.s === 200 && r.j.timing === undefined, r);

// --- save a fix as Ann, see it as Bob ---
ok("saving needs an account", (await call(env, "PUT", "/v1/transcripts/old1/timing", "", { offset: 3 })).s === 401);
r = await call(env, "PUT", "/v1/transcripts/old1/timing", A, { offset: 4, anchors: [[0, 2], [1, 70]] });
ok("Ann saves a fix", r.s === 200 && r.j.timing.offset === 4, r);
r = await call(env, "GET", "/v1/transcripts/old1", B);
ok("Bob sees Ann's fix", r.j.timing && r.j.timing.offset === 4 && r.j.timing.anchors.length === 2 && r.j.timing.anchors[1][1] === 70, r.j);
r = await call(env, "PUT", "/v1/transcripts/old1/timing", B, { offset: -2, anchors: [[5, 9]] });
r = await call(env, "GET", "/v1/transcripts/old1", A);
ok("last save wins", r.j.timing.offset === -2 && r.j.timing.anchors.length === 1, r.j);
r = await call(env, "POST", "/v1/transcripts", A, { epId: "old1", audioUrl: "https://cdn.example.com/x.mp3", durationSec: 1800 });
ok("asking for a finished transcript also returns the fix", r.s === 200 && r.j.timing?.offset === -2, r);

// --- validation ---
r = await call(env, "PUT", "/v1/transcripts/old1/timing", A, { offset: 99999, anchors: [["x", 1], [1], [-1, 5], [2, 999999], [3, 30], "nope"] });
ok("junk is clamped or dropped", r.s === 200 && r.j.timing.offset === 3600 && JSON.stringify(r.j.timing.anchors) === "[[3,30]]", r);
ok("no transcript, no fix", (await call(env, "PUT", "/v1/transcripts/nothing/timing", A, { offset: 1 })).s === 404);

// --- clearing ---
r = await call(env, "PUT", "/v1/transcripts/old1/timing", A, { offset: 0, anchors: [] });
ok("saving an empty fix clears it", r.s === 200 && r.j.timing === null, r);
r = await call(env, "GET", "/v1/transcripts/old1", B);
ok("and it is gone for everyone", r.j.timing === undefined, r.j);

// --- a new transcription drops the old fix, because the lines are different ---
files.set("n.mp3", { bytes: new Uint8Array(70_000).fill(1), type: "audio/mpeg" });
await call(env, "POST", "/v1/transcripts", A, { epId: "n", audioUrl: "https://cdn.example.com/n.mp3", durationSec: 30 });
await drain(env);
await call(env, "PUT", "/v1/transcripts/n/timing", A, { offset: 7, anchors: [[0, 3]] });
r = await call(env, "GET", "/v1/transcripts/n", A);
ok("fix saved on a generated transcript", r.j.timing?.offset === 7, r.j);
await call(env, "POST", "/v1/transcripts", A, { epId: "n", audioUrl: "https://cdn.example.com/n.mp3", durationSec: 30, force: true });
ok("redo clears the old fix", (db.prepare("SELECT COUNT(*) n FROM transcript_edits WHERE ep_id='n'").get() as any).n === 0);
finish();
