import { readFileSync } from "fs";
import { call, drain, files, finish, makeDb, makeEnv, ok } from "./harness";

// The owner's account is renamed hytyeileestn -> hytyei by migration 0006. Prove nothing that matters is tied to the handle.
const sql = (f: string) => readFileSync(import.meta.dir + "/../migrations/" + f, "utf8");
const sha = async (s: string) => [...new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s)))].map((b) => b.toString(16).padStart(2, "0")).join("");

// a fake Google, as in google.ts
const pair = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]) as CryptoKeyPair;
const jwk: any = { ...(await crypto.subtle.exportKey("jwk", pair.publicKey)), kid: "k1", alg: "RS256", use: "sig" };
const prev = globalThis.fetch;
globalThis.fetch = (async (u: any, init: any) => String(u).startsWith("https://www.googleapis.com/oauth2/v3/certs") ? new Response(JSON.stringify({ keys: [jwk] })) : prev(u, init)) as any;
const enc = (o: any) => btoa(JSON.stringify(o)).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
async function idToken(sub: string) {
  const now = Math.floor(Date.now() / 1000);
  const data = enc({ alg: "RS256", kid: "k1", typ: "JWT" }) + "." + enc({ iss: "https://accounts.google.com", aud: "cid", sub, email: sub + "@example.com", email_verified: true, iat: now, exp: now + 3600 });
  const sig = new Uint8Array(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", pair.privateKey, new TextEncoder().encode(data)));
  return data + "." + btoa(String.fromCharCode(...sig)).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}

files.set("ren.mp3", { bytes: new Uint8Array(200_000).fill(7), type: "audio/mpeg" });

async function world(withOtherHytyei: boolean) {
  const { db, DB } = makeDb("0005_google_and_founder.sql"); // the schema that is live today
  const now = Date.now();
  const owner = "tok_owner_secret", extra = "tok_owner_phone2";
  db.exec(`INSERT INTO users (id, handle, name, bio, token_hash, created_at, google_sub, google_email, avatar_v) VALUES ('uO','Hytyeileestn','Hadi','hi','${await sha(owner)}',${now - 9e6},'g-owner','o@example.com',5)`);
  db.exec(`INSERT INTO user_tokens (token_hash, user_id, created_at) VALUES ('${await sha(extra)}','uO',${now - 8e6})`);
  db.exec(`INSERT INTO users (id, handle, name, token_hash, created_at) VALUES ('uF','friend','Friend','${await sha("tok_friend")}',${now - 7e6})`);
  if (withOtherHytyei) db.exec(`INSERT INTO users (id, handle, name, token_hash, created_at) VALUES ('uX','hytyei','Someone else','${await sha("tok_x")}',${now - 6e6})`);
  db.exec(`INSERT INTO follows (follower_id, followee_id, created_at) VALUES ('uF','uO',1),('uO','uF',1)`);
  db.exec(`INSERT INTO log_entries (user_id, ep_id, show_id, show_name, ep_title, minutes, kind, listened_at, rating, hour, updated_at) VALUES
    ('uO','e1','s1','Show','One',30,'main','2026-10-01',9,20,1),('uO','e2','s1','Show','Two',20,'main','2026-10-02',7,20,1)`);
  db.exec(`INSERT INTO events (user_id, type, ref, payload, created_at) VALUES ('uO','listen','e1','{"epId":"e1","epTitle":"One","showId":"s1","showName":"Show","rating":9,"listenedAt":"2026-10-01"}',${now - 5e5})`);
  db.exec(`INSERT INTO lists (user_id, id, name, items, updated_at) VALUES ('uO','wishlist','Wishlist','[]',1)`);
  db.exec(`INSERT INTO transcripts (ep_id,status,progress,chunks_total,audio_url,duration_sec,lines,requested_by,created_at,updated_at,audio_sec)
           VALUES ('done1','ready',1,3,'https://cdn.example.com/ren.mp3',600,'[{"t":0,"text":"hello"}]','uO',${Math.floor(now / 86_400_000) * 86_400_000 + 1000},${Math.floor(now / 86_400_000) * 86_400_000 + 1000},600)`);
  const before = {
    log: (db.prepare("SELECT COUNT(*) n FROM log_entries WHERE user_id='uO'").get() as any).n,
    follows: (db.prepare("SELECT COUNT(*) n FROM follows").get() as any).n,
    transcripts: (db.prepare("SELECT COUNT(*) n FROM transcripts").get() as any).n,
    tokens: (db.prepare("SELECT COUNT(*) n FROM user_tokens").get() as any).n,
  };
  const env = makeEnv(DB);
  env.GOOGLE_CLIENT_ID = "cid";
  env.WHISPER_DAILY_MINUTES = "100000";
  return { db, DB, env, owner, extra, before };
}

// ---------- the normal case ----------
{
  const { db, env, owner, extra, before } = await world(false);
  db.exec(sql("0006_open_friends_transcribe_rename.sql"));
  ok("handle is now hytyei", (db.prepare("SELECT handle FROM users WHERE id='uO'").get() as any).handle === "hytyei");
  let r = await call(env, "GET", "/v1/me", owner);
  ok("the old token still authenticates and shows the new handle, founder role, transcription", r.s === 200 && r.j.handle === "hytyei" && r.j.role === "founder" && r.j.canTranscribe === true && r.j.id === "uO", r);
  r = await call(env, "GET", "/v1/me", extra);
  ok("the other phone's token still works", r.s === 200 && r.j.handle === "hytyei", r);
  r = await call(env, "POST", "/v1/auth/google", "", { idToken: await idToken("g-owner") });
  ok("Google sign-in still returns the same account under the new name", r.s === 200 && r.j.created === false && r.j.user.id === "uO" && r.j.user.handle === "hytyei" && r.j.user.role === "founder", r);
  const gtok = r.j.token;
  r = await call(env, "GET", "/v1/me", gtok);
  ok("and its fresh token works, with the Google link intact", r.s === 200 && r.j.google === true && r.j.googleEmail === "o@example.com" && r.j.canTranscribe === true, r);
  r = await call(env, "GET", "/v1/usage", owner);
  ok("usage meter still works", r.s === 200 && r.j.usedMin === 10, r);
  r = await call(env, "GET", "/v1/transcripts/done1", owner);
  ok("a finished transcript is still readable", r.s === 200 && r.j.status === "ready" && r.j.lines.length === 1, r);
  r = await call(env, "POST", "/v1/transcripts", owner, { epId: "newjob", audioUrl: "https://cdn.example.com/ren.mp3", durationSec: 60 });
  ok("a new transcription job is accepted", r.s === 200 || r.s === 202, r);
  await drain(env);
  r = await call(env, "GET", "/v1/transcripts/newjob", owner);
  ok("and finishes", r.s === 200 && r.j.status === "ready", r);
  ok("the job is recorded against the same account id", (db.prepare("SELECT requested_by FROM transcripts WHERE ep_id='newjob'").get() as any).requested_by === "uO");
  r = await call(env, "GET", "/v1/users/hytyei", "tok_friend");
  ok("friends see the profile under the new name, with log and followers", r.s === 200 && r.j.role === "founder" && r.j.stats.episodes === 2 && r.j.followers === 1 && r.j.following === 1 && r.j.isFollowing === true, r);
  r = await call(env, "GET", "/v1/users/hytyeileestn", "tok_friend");
  ok("the old handle is gone", r.s === 404, r);
  r = await call(env, "GET", "/v1/feed", "tok_friend");
  ok("the friend's feed shows the owner's listen under the new name", r.s === 200 && r.j.items.some((i: any) => i.user.handle === "hytyei" && i.epId === "e1" && i.minutes === 30), r);
  r = await call(env, "GET", "/v1/users?q=hyt", "tok_friend");
  ok("search finds the new name", r.j.users.some((u: any) => u.handle === "hytyei"), r);
  ok("nothing was deleted", (db.prepare("SELECT COUNT(*) n FROM log_entries WHERE user_id='uO'").get() as any).n === before.log && (db.prepare("SELECT COUNT(*) n FROM follows").get() as any).n === before.follows && (db.prepare("SELECT COUNT(*) n FROM user_tokens WHERE user_id='uO'").get() as any).n >= before.tokens);
  r = await call(env, "GET", "/v1/me", "tok_friend");
  ok("the friend (an old, invited user) keeps transcription", r.j.canTranscribe === true, r);
  // running the data statements again changes nothing
  db.exec("UPDATE users SET role = 'founder' WHERE lower(handle) = 'hytyeileestn'; UPDATE users SET handle = 'hytyei' WHERE lower(handle) = 'hytyeileestn' AND NOT EXISTS (SELECT 1 FROM users u2 WHERE lower(u2.handle) = 'hytyei');");
  ok("the rename statements are harmless to repeat", (db.prepare("SELECT handle FROM users WHERE id='uO'").get() as any).handle === "hytyei");
}

// ---------- somebody already has "hytyei" ----------
{
  const { db, env, owner } = await world(true);
  db.exec(sql("0006_open_friends_transcribe_rename.sql"));
  const h = (id: string) => db.prepare("SELECT handle, role FROM users WHERE id=?").get(id) as any;
  ok("collision: nobody is renamed", h("uO").handle === "Hytyeileestn" && h("uX").handle === "hytyei");
  ok("collision: the other 'hytyei' does NOT get the founder badge", h("uX").role === "");
  ok("collision: the owner's own account is still the founder", h("uO").role === "founder");
  const r = await call(env, "GET", "/v1/me", owner);
  ok("collision: the owner's token still works", r.s === 200 && r.j.canTranscribe === true, r);
}

finish();
