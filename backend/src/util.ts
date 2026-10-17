import type { Env } from "./types";

export class HttpError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

export function json(env: Env, data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", ...cors(env) },
  });
}

export function cors(env: Env): Record<string, string> {
  return {
    "access-control-allow-origin": env.ALLOW_ORIGIN || "*",
    "access-control-allow-headers": "authorization, content-type, if-none-match",
    "access-control-allow-methods": "GET, POST, PUT, PATCH, DELETE, OPTIONS",
    "access-control-max-age": "86400",
  };
}

export async function body<T = Record<string, unknown>>(req: Request): Promise<T> {
  try {
    const text = await req.text();
    if (text.length > 2_000_000) throw new HttpError(413, "Body too large");
    return (text ? JSON.parse(text) : {}) as T;
  } catch (e) {
    if (e instanceof HttpError) throw e;
    throw new HttpError(400, "Body must be JSON");
  }
}

export async function sha256Hex(s: string): Promise<string> {
  const buf = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s));
  return [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

export function randomToken(): string {
  const b = crypto.getRandomValues(new Uint8Array(24));
  return "lst_" + [...b].map((x) => x.toString(16).padStart(2, "0")).join("");
}

export function bearer(req: Request): string {
  const h = req.headers.get("authorization") || "";
  return h.toLowerCase().startsWith("bearer ") ? h.slice(7).trim() : "";
}

/** Constant-time-ish string compare. */
export function safeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let r = 0;
  for (let i = 0; i < a.length; i++) r |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return r === 0;
}

export function requireInvite(req: Request, env: Env): void {
  if (!env.INVITE_KEY) throw new HttpError(500, "This server has no INVITE_KEY yet. Run: npx wrangler secret put INVITE_KEY");
  if (!safeEqual(bearer(req), env.INVITE_KEY)) throw new HttpError(401, "Missing or wrong invite key");
}

export function int(v: unknown, def = 0, min = -Infinity, max = Infinity): number {
  const n = typeof v === "number" ? v : parseInt(String(v ?? ""), 10);
  if (!Number.isFinite(n)) return def;
  return Math.min(max, Math.max(min, Math.trunc(n)));
}

export function str(v: unknown, max = 500): string {
  return typeof v === "string" ? v.slice(0, max) : "";
}

export function bytesToBase64(bytes: Uint8Array): string {
  let s = "";
  const step = 0x8000;
  for (let i = 0; i < bytes.length; i += step) {
    s += String.fromCharCode(...bytes.subarray(i, i + step));
  }
  return btoa(s);
}
