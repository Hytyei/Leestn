import { readFileSync } from "fs";
import { call, makeDb, makeEnv } from "./harness";

let fails = 0;
const ok = (n: string, c: boolean, extra?: any) => { console.log((c ? "PASS " : "FAIL ") + n + (c ? "" : " " + JSON.stringify(extra ?? ""))); if (!c) fails++; };

// ---- upgrade: everybody who exists before 0006 joined with the key, so all of them may transcribe ----
{
  const { db, DB } = makeDb("0005_google_and_founder.sql");
  db.exec("INSERT INTO users (id, handle, name, token_hash, created_at) VALUES ('u1','a1','A',lower(hex(randomblob(8))),1),('u2','b1','B',lower(hex(randomblob(8))),1)");
  db.exec(readFileSync(import.meta.dir + "/../migrations/0006_open_friends_transcribe_rename.sql", "utf8"));
  ok("upgrade: every existing user keeps transcription", (db.prepare("SELECT COUNT(*) n FROM users WHERE can_transcribe=1").get() as any).n === 2);
  db.exec("INSERT INTO users (id, handle, name, token_hash, created_at) VALUES ('u3','c1','C','h3',2)");
  ok("upgrade: a user added afterwards starts without it", (db.prepare("SELECT can_transcribe c FROM users WHERE id='u3'").get() as any).c === 0);
}

const { db, DB } = makeDb();
const env = makeEnv(DB);
env.WHISPER_DAILY_MINUTES = "100000";

// ---- sign-up ----
let r = await call(env, "POST", "/v1/users", "", { handle: "fran", name: "Fran" });
const fran = r.j.token;
ok("a friend can join without any key", r.s === 201 && /^lst_/.test(fran) && r.j.user.canTranscribe === false, r);
r = await call(env, "POST", "/v1/users", "secret", { handle: "kim", name: "Kim" });
const kim = r.j.token;
ok("joining with the invite key unlocks transcription", r.s === 201 && r.j.user.canTranscribe === true, r);
r = await call(env, "POST", "/v1/users", "wrong", { handle: "zed", name: "Z" });
ok("a wrong key is refused with a clear message", r.s === 401 && /wrong/i.test(r.j.error), r);
r = await call(env, "GET", "/v1/me", fran);
ok("me shows canTranscribe false", r.s === 200 && r.j.canTranscribe === false, r);
r = await call(env, "GET", "/v1/me", kim);
ok("me shows canTranscribe true", r.j.canTranscribe === true, r);
r = await call(env, "GET", "/v1/auth/config");
ok("config says sign-up is open", r.j.openSignup === true, r);

// ---- friends features work for a friend without transcription ----
r = await call(env, "PUT", "/v1/sync", fran, { log: [{ epId: "e1", showId: "s1", showName: "S", epTitle: "T", minutes: 33, listenedAt: "2026-10-10", rating: 7 }] });
ok("a friend can sync", r.s === 200, r);
r = await call(env, "POST", "/v1/follow/kim", fran);
ok("a friend can follow people", r.s === 200, r);
r = await call(env, "GET", "/v1/users/kim", fran);
ok("a friend can open profiles", r.s === 200, r);
r = await call(env, "GET", "/v1/feed", fran);
ok("a friend's feed has their own listen", r.s === 200 && r.j.items.length === 1 && r.j.items[0].mine === true && r.j.items[0].minutes === 33 && r.j.items[0].rating === 7, r);
r = await call(env, "GET", "/v1/users?q=ki", fran);
ok("a friend can search people", r.s === 200 && r.j.users.some((u: any) => u.handle === "kim"), r);

// ---- every transcription route is closed to a friend, open to an unlocked user and to the key ----
const MSG = "Transcription is invite only. Enter the invite key in Settings.";
const routes: [string, string, any][] = [
  ["GET", "/v1/usage", undefined],
  ["POST", "/v1/transcripts", { epId: "x1", audioUrl: "https://cdn.example.com/none.mp3", durationSec: 60 }],
  ["GET", "/v1/transcripts/x1", undefined],
  ["PUT", "/v1/transcripts/x1/timing", { offset: 1 }],
];
for (const [m, p, b] of routes) {
  r = await call(env, m, p, fran, b);
  ok(`locked friend: ${m} ${p} is 403 with the clear message`, r.s === 403 && r.j.error === MSG, r);
  r = await call(env, m, p, "secret", b);
  ok(`invite key itself: ${m} ${p} is not refused`, r.s !== 401 && r.s !== 403, r);
}
r = await call(env, "GET", "/v1/usage", kim);
ok("unlocked user sees the usage meter", r.s === 200 && typeof r.j.limitMin === "number", r);
r = await call(env, "GET", "/v1/transcripts/x1", kim);
ok("unlocked user can read transcripts", r.s === 200, r);
r = await call(env, "GET", "/v1/usage", "");
ok("no token at all is still 401", r.s === 401, r);

// ---- unlock ----
r = await call(env, "POST", "/v1/me/unlock", "", { key: "secret" });
ok("unlock needs an account token", r.s === 401, r);
r = await call(env, "POST", "/v1/me/unlock", fran, { key: "nope" });
ok("wrong key does not unlock", r.s === 401, r);
r = await call(env, "POST", "/v1/me/unlock", fran, {});
ok("empty key does not unlock", r.s === 401, r);
r = await call(env, "GET", "/v1/usage", fran);
ok("still locked after wrong tries", r.s === 403, r);
r = await call(env, "POST", "/v1/me/unlock", fran, { key: "secret" });
ok("right key unlocks", r.s === 200 && r.j.canTranscribe === true, r);
r = await call(env, "GET", "/v1/usage", fran);
ok("now the meter works", r.s === 200, r);
r = await call(env, "POST", "/v1/me/unlock", fran, { key: "secret" });
ok("unlocking twice is fine", r.s === 200, r);
r = await call(env, "GET", "/v1/me", fran);
ok("me now says canTranscribe true", r.j.canTranscribe === true, r);

// ---- the abuse brake and the invite-only switch ----
env.MAX_SIGNUPS_PER_DAY = "3"; // fran, kim already exist
r = await call(env, "POST", "/v1/users", "", { handle: "gus", name: "G" });
ok("a third keyless sign-up in the day still fits (3 accounts so far counts 2)", r.s === 201, r);
r = await call(env, "POST", "/v1/users", "", { handle: "hal", name: "H" });
ok("then the daily cap stops keyless sign-ups with 429", r.s === 429, r);
r = await call(env, "POST", "/v1/users", "secret", { handle: "ivy", name: "I" });
ok("the cap does not stop people with the key", r.s === 201, r);
delete env.MAX_SIGNUPS_PER_DAY;
env.SIGNUPS = "invite";
r = await call(env, "POST", "/v1/users", "", { handle: "jay", name: "J" });
ok("SIGNUPS=invite closes keyless sign-up", r.s === 401, r);
r = await call(env, "POST", "/v1/users", "secret", { handle: "jay", name: "J" });
ok("and the key still works", r.s === 201, r);
env.SIGNUPS = "open";
r = await call(env, "POST", "/v1/users", "", { handle: "lee", name: "L" });
ok("any other value keeps it open", r.s === 201, r);
env.INVITE_KEY = undefined;
r = await call(env, "POST", "/v1/users", "", { handle: "max", name: "M" });
ok("without an INVITE_KEY set, friends can still join", r.s === 201, r);
r = await call(env, "POST", "/v1/me/unlock", fran, { key: "" });
ok("and nobody can unlock with an empty key", r.s === 401, r);

console.log(fails ? `${fails} FAILED` : "ALL PASSED");
process.exit(fails ? 1 : 0);
