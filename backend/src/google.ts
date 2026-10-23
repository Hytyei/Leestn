import type { Env } from "./types";
import { HttpError, bearer, body, json, randomToken, safeEqual, sha256Hex, str } from "./util";
import { authed, issueToken, pubUser, USER_COLS, type User } from "./users";

// Sign in with Google. The app gets a Google ID token on the phone and sends it here. We check Google's signature,
// that the token was made for this server's Google client (GOOGLE_CLIENT_ID), and that it has not expired.
// No Google secret is needed: only the public client id, and the signing keys Google publishes.

const CERTS = "https://www.googleapis.com/oauth2/v3/certs";
let cache: { keys: any[]; at: number } | null = null;

function b64u(s: string): Uint8Array {
  const p = s.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - (s.length % 4)) % 4);
  const bin = atob(p);
  const out = new Uint8Array(new ArrayBuffer(bin.length));
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

async function googleKeys(force = false): Promise<any[]> {
  if (!force && cache && Date.now() - cache.at < 3_600_000) return cache.keys;
  const res = await fetch(CERTS);
  if (!res.ok) throw new HttpError(502, "Couldn't reach Google to check the sign-in");
  const j = (await res.json()) as { keys?: any[] };
  cache = { keys: j.keys || [], at: Date.now() };
  return cache.keys;
}

export interface GoogleIdentity {
  sub: string;
  email: string;
  name: string;
}

export async function verifyGoogleToken(env: Env, idToken: string): Promise<GoogleIdentity> {
  if (!env.GOOGLE_CLIENT_ID) throw new HttpError(501, "Google sign-in isn't set up on this server yet");
  const parts = idToken.split(".");
  if (parts.length !== 3) throw new HttpError(401, "That Google sign-in isn't valid");
  let header: any, payload: any;
  try {
    header = JSON.parse(new TextDecoder().decode(b64u(parts[0])));
    payload = JSON.parse(new TextDecoder().decode(b64u(parts[1])));
  } catch {
    throw new HttpError(401, "That Google sign-in isn't valid");
  }
  if (header.alg !== "RS256" || !header.kid) throw new HttpError(401, "That Google sign-in isn't valid");
  let jwk = (await googleKeys()).find((k) => k.kid === header.kid);
  if (!jwk) jwk = (await googleKeys(true)).find((k) => k.kid === header.kid);
  if (!jwk) throw new HttpError(401, "That Google sign-in isn't valid");
  const key = await crypto.subtle.importKey("jwk", jwk, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
  const ok = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, b64u(parts[2]) as unknown as BufferSource, new TextEncoder().encode(parts[0] + "." + parts[1]));
  if (!ok) throw new HttpError(401, "That Google sign-in isn't valid");
  if (payload.iss !== "accounts.google.com" && payload.iss !== "https://accounts.google.com") throw new HttpError(401, "That Google sign-in isn't valid");
  if (payload.aud !== env.GOOGLE_CLIENT_ID) throw new HttpError(401, "That Google sign-in was made for a different app");
  if (typeof payload.exp !== "number" || payload.exp * 1000 < Date.now() - 60_000) throw new HttpError(401, "That Google sign-in has expired. Try again.");
  if (typeof payload.sub !== "string" || !payload.sub) throw new HttpError(401, "That Google sign-in isn't valid");
  if (payload.email_verified !== true && payload.email_verified !== "true") throw new HttpError(403, "Your Google email isn't verified");
  return { sub: payload.sub, email: str(payload.email, 200), name: str(payload.name, 60) };
}

/** What the app needs to start Google sign-in. The client id is public. */
export async function authConfig(env: Env): Promise<Response> {
  return json(env, { google: !!env.GOOGLE_CLIENT_ID, googleClientId: env.GOOGLE_CLIENT_ID || "" });
}

const SELECT_BY_SUB = `SELECT ${USER_COLS} FROM users WHERE google_sub = ?`;

/**
 * Sign in or sign up with Google.
 * - A Google account that is already linked: signs in (a new token for this device). No invite key needed.
 * - Otherwise, with a handle and the server's invite key: creates the account.
 * - Otherwise: answers 404 with needsHandle so the app can ask for a username.
 */
export async function googleAuth(env: Env, req: Request): Promise<Response> {
  const b = await body(req);
  const id = await verifyGoogleToken(env, str(b.idToken, 6000));
  const existing = await env.DB.prepare(SELECT_BY_SUB).bind(id.sub).first<User>();
  if (existing) {
    const token = await issueToken(env, existing.id);
    return json(env, { user: pubUser(existing), token, created: false });
  }
  const handle = str(b.handle, 30).trim().toLowerCase().replace(/^@/, "");
  if (!handle) return json(env, { error: "No Leestn account is linked to this Google account yet. Choose a username to create one.", needsHandle: true }, 404);
  if (!env.INVITE_KEY) throw new HttpError(500, "This server has no INVITE_KEY yet");
  if (!safeEqual(bearer(req), env.INVITE_KEY)) throw new HttpError(401, "Missing or wrong invite key");
  if (!/^[a-z0-9_]{3,30}$/.test(handle)) throw new HttpError(400, "Handle must be 3 to 30 letters, numbers or underscores");
  const name = str(b.name, 60).trim() || id.name || handle;
  const bio = str(b.bio, 300);
  const token = randomToken();
  const uid = crypto.randomUUID();
  try {
    await env.DB.prepare(
      "INSERT INTO users (id, handle, name, bio, genres, token_hash, created_at, google_sub, google_email) VALUES (?, ?, ?, ?, '[]', ?, ?, ?, ?)",
    ).bind(uid, handle, name, bio, await sha256Hex(token), Date.now(), id.sub, id.email).run();
  } catch {
    throw new HttpError(409, "That handle is taken");
  }
  const u = await env.DB.prepare(SELECT_BY_SUB).bind(id.sub).first<User>();
  return json(env, { user: pubUser(u!), token, created: true }, 201);
}

/** Link the Google account to the signed-in Leestn account. */
export async function linkGoogle(env: Env, req: Request): Promise<Response> {
  const u = await authed(env, req);
  const b = await body(req);
  const id = await verifyGoogleToken(env, str(b.idToken, 6000));
  const other = await env.DB.prepare("SELECT id FROM users WHERE google_sub = ?").bind(id.sub).first<{ id: string }>();
  if (other && other.id !== u.id) throw new HttpError(409, "That Google account is already linked to another Leestn account");
  if (u.google_sub && u.google_sub !== id.sub) throw new HttpError(409, "This account is already linked to a different Google account. Disconnect it first.");
  await env.DB.prepare("UPDATE users SET google_sub=?, google_email=? WHERE id=?").bind(id.sub, id.email, u.id).run();
  return json(env, { ok: true, googleEmail: id.email });
}

/** Unlink Google. The account and the device's token keep working. */
export async function unlinkGoogle(env: Env, req: Request): Promise<Response> {
  const u = await authed(env, req);
  await env.DB.prepare("UPDATE users SET google_sub=NULL, google_email=NULL WHERE id=?").bind(u.id).run();
  return json(env, { ok: true });
}
