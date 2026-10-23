import { readFileSync } from "fs";
import { call, makeDb, makeEnv } from "./harness";

let fails = 0;
const ok = (n: string, c: boolean, extra?: any) => { console.log((c ? "PASS " : "FAIL ") + n + (c ? "" : " " + JSON.stringify(extra ?? ""))); if (!c) fails++; };

// A fake Google: our own RSA key, published where the worker looks for Google's keys.
const pair = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]) as CryptoKeyPair;
const jwk: any = { ...(await crypto.subtle.exportKey("jwk", pair.publicKey)), kid: "k1", alg: "RS256", use: "sig" };
let certFetches = 0;
const prev = globalThis.fetch;
globalThis.fetch = (async (u: any, init: any) => {
  if (String(u).startsWith("https://www.googleapis.com/oauth2/v3/certs")) { certFetches++; return new Response(JSON.stringify({ keys: [jwk] })); }
  return prev(u, init);
}) as any;

const enc = (o: any) => btoa(JSON.stringify(o)).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
async function idToken(claims: any, opts: { kid?: string; tamper?: boolean } = {}) {
  const now = Math.floor(Date.now() / 1000);
  const payload = { iss: "https://accounts.google.com", aud: "client-123.apps.googleusercontent.com", sub: "g-1", email: "a@example.com", email_verified: true, name: "Ann G", iat: now, exp: now + 3600, ...claims };
  const head = enc({ alg: "RS256", kid: opts.kid ?? "k1", typ: "JWT" });
  const data = head + "." + enc(payload);
  const sig = new Uint8Array(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", pair.privateKey, new TextEncoder().encode(data)));
  let s = btoa(String.fromCharCode(...sig)).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
  if (opts.tamper) s = s.slice(0, -4) + (s.endsWith("AAAA") ? "BBBB" : "AAAA");
  return data + "." + s;
}

// ---- upgrade from the schema before this release ----
{
  const { db, DB } = makeDb("0004_transcript_timing.sql");
  db.exec("INSERT INTO users (id, handle, name, token_hash, created_at) VALUES ('u1','Hytyei','Hadi','h1',1),('u2','bob','Bob','h2',1)");
  db.exec(readFileSync(import.meta.dir + "/../migrations/0005_google_and_founder.sql", "utf8"));
  const roles = Object.fromEntries((db.prepare("SELECT handle, role FROM users").all() as any[]).map((r) => [r.handle, r.role]));
  ok("upgrade: Hytyei is founder, others are not", roles["Hytyei"] === "founder" && roles["bob"] === "", roles);
  ok("upgrade: existing users and tokens untouched", (db.prepare("SELECT COUNT(*) n FROM users").get() as any).n === 2 && (db.prepare("SELECT token_hash FROM users WHERE id='u1'").get() as any).token_hash === "h1");
  db.exec("CREATE UNIQUE INDEX IF NOT EXISTS idx_users_google_sub ON users (google_sub) WHERE google_sub IS NOT NULL; UPDATE users SET role = 'founder' WHERE lower(handle) = 'hytyei';");
  ok("upgrade: the idempotent parts can be rerun", true);
}

// ---- the feature, on a fresh database ----
const { db, DB } = makeDb();
const env = makeEnv(DB);

let r = await call(env, "GET", "/v1/auth/config");
ok("config: off until a client id is set", r.s === 200 && r.j.google === false);
r = await call(env, "POST", "/v1/auth/google", "", { idToken: await idToken({}) });
ok("auth: 501 while not configured", r.s === 501, r);

env.GOOGLE_CLIENT_ID = "client-123.apps.googleusercontent.com";
r = await call(env, "GET", "/v1/auth/config");
ok("config: shows the public client id", r.j.google === true && r.j.googleClientId === env.GOOGLE_CLIENT_ID);

r = await call(env, "POST", "/v1/auth/google", "", { idToken: await idToken({}) });
ok("new Google account without a username: 404 needsHandle", r.s === 404 && r.j.needsHandle === true, r);
r = await call(env, "POST", "/v1/auth/google", "", { idToken: await idToken({}), handle: "ann" });
ok("sign up without the invite key is refused", r.s === 401, r);
r = await call(env, "POST", "/v1/auth/google", "secret", { idToken: await idToken({}), handle: "x" });
ok("bad handle is refused", r.s === 400, r);
r = await call(env, "POST", "/v1/auth/google", "secret", { idToken: await idToken({}), handle: "ann" });
const tok1 = r.j.token;
ok("sign up with Google creates the account", r.s === 201 && r.j.created === true && /^lst_/.test(tok1) && r.j.user.handle === "ann" && r.j.user.name === "Ann G", r);
r = await call(env, "GET", "/v1/me", tok1);
ok("the new token works and shows the linked email", r.s === 200 && r.j.google === true && r.j.googleEmail === "a@example.com" && r.j.role === "", r);

r = await call(env, "POST", "/v1/auth/google", "", { idToken: await idToken({}) });
const tok2 = r.j.token;
ok("signing in again needs no invite key and gives a new token", r.s === 200 && r.j.created === false && tok2 && tok2 !== tok1, r);
ok("both devices' tokens work", (await call(env, "GET", "/v1/me", tok1)).s === 200 && (await call(env, "GET", "/v1/me", tok2)).s === 200);
ok("only hashes are stored", (db.prepare("SELECT COUNT(*) n FROM user_tokens WHERE token_hash LIKE 'lst_%'").get() as any).n === 0);
r = await call(env, "POST", "/v1/auth/google", "secret", { idToken: await idToken({}), handle: "someoneelse" });
ok("a linked Google account signs in even if a handle is sent", r.s === 200 && r.j.user.handle === "ann", r);
ok("the google keys are cached", certFetches === 1, certFetches);

// bad tokens
r = await call(env, "POST", "/v1/auth/google", "", { idToken: await idToken({}, { tamper: true }) });
ok("tampered signature refused", r.s === 401, r);
r = await call(env, "POST", "/v1/auth/google", "", { idToken: await idToken({ aud: "other-client" }) });
ok("token for another app refused", r.s === 401, r);
r = await call(env, "POST", "/v1/auth/google", "", { idToken: await idToken({ iss: "https://evil.example" }) });
ok("wrong issuer refused", r.s === 401, r);
r = await call(env, "POST", "/v1/auth/google", "", { idToken: await idToken({ exp: Math.floor(Date.now() / 1000) - 3600 }) });
ok("expired token refused", r.s === 401, r);
r = await call(env, "POST", "/v1/auth/google", "", { idToken: await idToken({ email_verified: false }) });
ok("unverified email refused", r.s === 403, r);
r = await call(env, "POST", "/v1/auth/google", "", { idToken: await idToken({}, { kid: "nope" }) });
ok("unknown key id refused", r.s === 401, r);
r = await call(env, "POST", "/v1/auth/google", "", { idToken: "abc" });
ok("junk refused", r.s === 401, r);
r = await call(env, "POST", "/v1/auth/google", "", {});
ok("missing token refused", r.s === 401, r);

// linking an existing (token-only) account
r = await call(env, "POST", "/v1/users", "secret", { handle: "bob", name: "Bob" });
const bob = r.j.token;
r = await call(env, "POST", "/v1/me/google", "", { idToken: await idToken({ sub: "g-2", email: "b@example.com" }) });
ok("linking needs an account token", r.s === 401, r);
r = await call(env, "POST", "/v1/me/google", bob, { idToken: await idToken({}) });
ok("a Google account linked to someone else can't be linked again", r.s === 409, r);
r = await call(env, "POST", "/v1/me/google", bob, { idToken: await idToken({ sub: "g-2", email: "b@example.com" }) });
ok("link my existing account", r.s === 200 && r.j.googleEmail === "b@example.com", r);
r = await call(env, "POST", "/v1/me/google", bob, { idToken: await idToken({ sub: "g-2", email: "b@example.com" }) });
ok("linking the same Google account again is fine", r.s === 200, r);
r = await call(env, "POST", "/v1/me/google", bob, { idToken: await idToken({ sub: "g-3" }) });
ok("a second, different Google account is refused", r.s === 409, r);
r = await call(env, "POST", "/v1/auth/google", "", { idToken: await idToken({ sub: "g-2", email: "b@example.com" }) });
ok("signing in with the linked Google gets the old account back", r.s === 200 && r.j.user.handle === "bob", r);
r = await call(env, "DELETE", "/v1/me/google", bob);
ok("unlink", r.s === 200, r);
r = await call(env, "GET", "/v1/me", bob);
ok("unlink keeps the account and its token", r.s === 200 && r.j.google === false && r.j.googleEmail === "", r);
r = await call(env, "POST", "/v1/auth/google", "", { idToken: await idToken({ sub: "g-2" }) });
ok("after unlinking, that Google account no longer signs in", r.s === 404, r);
r = await call(env, "POST", "/v1/me/google", bob, { idToken: await idToken({ sub: "g-3", email: "c@example.com" }) });
ok("and a different Google account can be linked", r.s === 200, r);

// role / founder
db.exec("UPDATE users SET role='founder' WHERE handle='ann'");
r = await call(env, "GET", "/v1/me", tok1);
ok("me shows the founder role", r.j.role === "founder", r);
r = await call(env, "GET", "/v1/users/ann", bob);
ok("profile shows the role and never the email", r.s === 200 && r.j.role === "founder" && !JSON.stringify(r.j).includes("example.com"), r);
r = await call(env, "GET", "/v1/users?q=ann", bob);
ok("search shows the role", r.j.users[0].role === "founder", r);
r = await call(env, "GET", "/v1/users?q=", tok1);
ok("others have an empty role", r.j.users.every((x: any) => x.role === ""), r);
r = await call(env, "PATCH", "/v1/me", tok1, { role: "founder", name: "Ann" });
ok("a role can't be set from the app", (db.prepare("SELECT role FROM users WHERE handle='bob'").get() as any).role === "");
r = await call(env, "PATCH", "/v1/me", bob, { role: "founder" });
ok("nor by editing your own profile", (db.prepare("SELECT role FROM users WHERE handle='bob'").get() as any).role === "");

// old token flow still works as before
r = await call(env, "POST", "/v1/users", "secret", { handle: "carl", name: "Carl" });
ok("the invite-key sign-up still works", r.s === 201 && /^lst_/.test(r.j.token) && r.j.user.role === "", r);

// deleting an account removes its extra tokens and its Google link
const annId = (db.prepare("SELECT id FROM users WHERE handle='ann'").get() as any).id;
r = await call(env, "DELETE", "/v1/me", tok2);
ok("delete account", r.s === 200, r);
ok("extra tokens are gone", (db.prepare("SELECT COUNT(*) n FROM user_tokens WHERE user_id=?").get(annId) as any).n === 0);
ok("the other device's token no longer works", (await call(env, "GET", "/v1/me", tok1)).s === 401);
r = await call(env, "POST", "/v1/auth/google", "", { idToken: await idToken({}) });
ok("the freed Google account is a new sign-up again", r.s === 404 && r.j.needsHandle === true, r);

// keep only 20 tokens per account
r = await call(env, "POST", "/v1/auth/google", "secret", { idToken: await idToken({ sub: "g-9", email: "z@example.com" }), handle: "zed" });
for (let i = 0; i < 25; i++) await call(env, "POST", "/v1/auth/google", "", { idToken: await idToken({ sub: "g-9" }) });
ok("at most 20 extra tokens per account", (db.prepare("SELECT COUNT(*) n FROM user_tokens WHERE user_id=(SELECT id FROM users WHERE handle='zed')").get() as any).n === 20);

console.log(fails ? `${fails} FAILED` : "ALL PASSED");
process.exit(fails ? 1 : 0);
