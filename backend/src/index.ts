import type { ChunkJob, Env } from "./types";
import { HttpError, cors, json } from "./util";
import { consume, getTranscript, requestTranscript } from "./transcripts";
import {
  createUser, deleteAvatar, deleteMe, feed, follow, getAvatar, me, patchMe, profile, pull, push, putAvatar, searchUsers, showRatings, wrapped,
} from "./users";

async function route(req: Request, env: Env): Promise<Response> {
  const url = new URL(req.url);
  const p = url.pathname.replace(/\/+$/, "") || "/";
  const m = req.method;
  let r: RegExpMatchArray | null;

  if (p === "/" || p === "/health") return json(env, { ok: true, name: "leestn-api", version: "1.0.0-pre.2" });

  // Transcripts (invite key or account token)
  if (p === "/v1/transcripts" && m === "POST") return requestTranscript(env, req);
  if ((r = p.match(/^\/v1\/transcripts\/(.+)$/)) && m === "GET") return getTranscript(env, req, decodeURIComponent(r[1]));

  // Accounts
  if (p === "/v1/users" && m === "POST") return createUser(env, req);
  if (p === "/v1/users" && m === "GET") return searchUsers(env, req);
  if (p === "/v1/me" && m === "GET") return me(env, req);
  if (p === "/v1/me" && m === "PATCH") return patchMe(env, req);
  if (p === "/v1/me" && m === "DELETE") return deleteMe(env, req);
  if (p === "/v1/me/avatar" && m === "PUT") return putAvatar(env, req);
  if (p === "/v1/me/avatar" && m === "DELETE") return deleteAvatar(env, req);

  // Sync, friends, feed
  if (p === "/v1/sync" && m === "PUT") return push(env, req);
  if (p === "/v1/sync" && m === "GET") return pull(env, req);
  if (p === "/v1/feed" && m === "GET") return feed(env, req);
  if ((r = p.match(/^\/v1\/follow\/([a-zA-Z0-9_]+)$/)) && (m === "POST" || m === "DELETE")) return follow(env, req, r[1], m === "POST");

  // Ratings from everyone on the server
  if ((r = p.match(/^\/v1\/shows\/([^/]+)\/ratings$/)) && m === "GET") return showRatings(env, req, decodeURIComponent(r[1]));

  // Profiles
  if ((r = p.match(/^\/v1\/users\/([a-zA-Z0-9_]+)\/avatar$/)) && m === "GET") return getAvatar(env, r[1]);
  if ((r = p.match(/^\/v1\/users\/([a-zA-Z0-9_]+)\/wrapped\/(\d{4})$/)) && m === "GET") return wrapped(env, req, r[1], r[2]);
  if ((r = p.match(/^\/v1\/users\/([a-zA-Z0-9_]+)$/)) && m === "GET") return profile(env, req, r[1]);

  throw new HttpError(404, "Not found");
}

export default {
  async fetch(req: Request, env: Env): Promise<Response> {
    if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: cors(env) });
    try {
      const res = await route(req, env);
      // Add CORS to non-JSON responses (avatars) too.
      for (const [k, v] of Object.entries(cors(env))) if (!res.headers.has(k)) res.headers.set(k, v);
      return res;
    } catch (e) {
      if (e instanceof HttpError) return json(env, { error: e.message }, e.status);
      console.error(e);
      return json(env, { error: "Server error" }, 500);
    }
  },

  async queue(batch: MessageBatch<ChunkJob>, env: Env): Promise<void> {
    await consume(batch, env);
  },
} satisfies ExportedHandler<Env, ChunkJob>;
