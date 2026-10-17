import type { Env } from "./types";
import { HttpError, bearer, body, bytesToBase64, int, json, randomToken, requireInvite, safeEqual, sha256Hex, str } from "./util";

export interface User {
  id: string;
  handle: string;
  name: string;
  bio: string;
  genres: string;
  badges: string;
  avatar_v: number;
  created_at: number;
}

const COLS = "id, handle, name, bio, genres, badges, avatar_v, created_at";

export async function userByToken(env: Env, token: string): Promise<User | null> {
  if (!token) return null;
  return env.DB.prepare(`SELECT ${COLS} FROM users WHERE token_hash = ?`).bind(await sha256Hex(token)).first<User>();
}

export async function authed(env: Env, req: Request): Promise<User> {
  const t = bearer(req);
  if (!t) throw new HttpError(401, "Missing token");
  const u = await userByToken(env, t);
  if (!u) throw new HttpError(401, "Unknown token");
  return u;
}

/** A signed-in user, or the invite key (used before an account exists). */
export async function member(env: Env, req: Request): Promise<{ user: User | null }> {
  const t = bearer(req);
  const u = await userByToken(env, t);
  if (u) return { user: u };
  if (env.INVITE_KEY && safeEqual(t, env.INVITE_KEY)) return { user: null };
  throw new HttpError(401, "Missing or wrong key");
}

const pub = (u: User) => ({
  id: u.id,
  handle: u.handle,
  name: u.name,
  bio: u.bio,
  genres: JSON.parse(u.genres),
  badges: JSON.parse(u.badges),
  avatarV: u.avatar_v,
  createdAt: u.created_at,
});

async function byHandle(env: Env, handle: string): Promise<User> {
  const u = await env.DB.prepare(`SELECT ${COLS} FROM users WHERE handle = ?`).bind(handle.toLowerCase()).first<User>();
  if (!u) throw new HttpError(404, "No such user");
  return u;
}

export async function createUser(env: Env, req: Request): Promise<Response> {
  requireInvite(req, env);
  const b = await body(req);
  const handle = str(b.handle, 30).trim().toLowerCase();
  const name = str(b.name, 60).trim() || handle;
  if (!/^[a-z0-9_]{3,30}$/.test(handle)) throw new HttpError(400, "Handle must be 3 to 30 letters, numbers or underscores");
  const genres = Array.isArray(b.genres) ? (b.genres as unknown[]).map((g) => str(g, 40)).filter(Boolean).slice(0, 20) : [];
  const bio = str(b.bio, 300);
  const token = randomToken();
  const id = crypto.randomUUID();
  try {
    await env.DB.prepare("INSERT INTO users (id, handle, name, bio, genres, token_hash, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)")
      .bind(id, handle, name, bio, JSON.stringify(genres), await sha256Hex(token), Date.now())
      .run();
  } catch {
    throw new HttpError(409, "That handle is taken");
  }
  // The token is shown once. Only its hash is stored.
  return json(env, { user: { id, handle, name, bio, genres, badges: [], avatarV: 0, createdAt: Date.now() }, token }, 201);
}

export async function me(env: Env, req: Request): Promise<Response> {
  return json(env, pub(await authed(env, req)));
}

export async function patchMe(env: Env, req: Request): Promise<Response> {
  const u = await authed(env, req);
  const b = await body(req);
  const name = typeof b.name === "string" ? str(b.name, 60).trim() || u.name : u.name;
  const bio = typeof b.bio === "string" ? str(b.bio, 300) : u.bio;
  const genres = Array.isArray(b.genres) ? JSON.stringify((b.genres as unknown[]).map((g) => str(g, 40)).filter(Boolean).slice(0, 20)) : u.genres;
  await env.DB.prepare("UPDATE users SET name=?, bio=?, genres=? WHERE id=?").bind(name, bio, genres, u.id).run();
  return json(env, pub({ ...u, name, bio, genres }));
}

/** Delete the account and everything stored for it. Transcripts are shared and stay. */
export async function deleteMe(env: Env, req: Request): Promise<Response> {
  const u = await authed(env, req);
  await env.DB.batch([
    env.DB.prepare("DELETE FROM log_entries WHERE user_id=?").bind(u.id),
    env.DB.prepare("DELETE FROM lists WHERE user_id=?").bind(u.id),
    env.DB.prepare("DELETE FROM subs WHERE user_id=?").bind(u.id),
    env.DB.prepare("DELETE FROM progress WHERE user_id=?").bind(u.id),
    env.DB.prepare("DELETE FROM show_ratings WHERE user_id=?").bind(u.id),
    env.DB.prepare("DELETE FROM events WHERE user_id=?").bind(u.id),
    env.DB.prepare("DELETE FROM follows WHERE follower_id=? OR followee_id=?").bind(u.id, u.id),
    env.DB.prepare("DELETE FROM users WHERE id=?").bind(u.id),
  ]);
  return json(env, { ok: true });
}

// ---------- avatars ----------

export async function putAvatar(env: Env, req: Request): Promise<Response> {
  const u = await authed(env, req);
  const buf = new Uint8Array(await req.arrayBuffer());
  if (buf.length < 100 || buf.length > 120_000) throw new HttpError(413, "Avatar must be under 120 KB");
  if (!(buf[0] === 0xff && buf[1] === 0xd8 && buf[2] === 0xff)) throw new HttpError(415, "Avatar must be a JPEG");
  const v = Date.now();
  await env.DB.prepare("UPDATE users SET avatar=?, avatar_v=? WHERE id=?").bind(bytesToBase64(buf), v, u.id).run();
  return json(env, { avatarV: v });
}

export async function deleteAvatar(env: Env, req: Request): Promise<Response> {
  const u = await authed(env, req);
  await env.DB.prepare("UPDATE users SET avatar='', avatar_v=? WHERE id=?").bind(Date.now(), u.id).run();
  return json(env, { avatarV: 0 });
}

export async function getAvatar(env: Env, handle: string): Promise<Response> {
  const row = await env.DB.prepare("SELECT avatar FROM users WHERE handle = ?").bind(handle.toLowerCase()).first<{ avatar: string }>();
  if (!row || !row.avatar) return new Response("Not found", { status: 404 });
  const bin = atob(row.avatar);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return new Response(bytes, { headers: { "content-type": "image/jpeg", "cache-control": "public, max-age=31536000, immutable" } });
}

// ---------- sync ----------

/** Push local changes. Every collection is optional, and rows are upserts, so repeating a push is safe. */
export async function push(env: Env, req: Request): Promise<Response> {
  const u = await authed(env, req);
  const b = await body<any>(req);
  const now = Date.now();
  const st: D1PreparedStatement[] = [];

  for (const e of (Array.isArray(b.log) ? b.log : []).slice(0, 3000)) {
    const epId = str(e.epId, 120);
    if (!epId) continue;
    const listened = typeof e.listenedAt === "string" && /^\d{4}-\d{2}-\d{2}/.test(e.listenedAt) ? e.listenedAt.slice(0, 10) : null;
    const rating = e.rating == null ? null : int(e.rating, 0, 1, 10) || null;
    if (!listened && rating == null) {
      st.push(env.DB.prepare("DELETE FROM log_entries WHERE user_id=? AND ep_id=?").bind(u.id, epId));
      continue;
    }
    st.push(
      env.DB.prepare(
        `INSERT INTO log_entries (user_id, ep_id, show_id, show_name, show_art, ep_title, genre, minutes, kind, listened_at, rating, hour, feed_url, updated_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
         ON CONFLICT(user_id, ep_id) DO UPDATE SET show_id=excluded.show_id, show_name=excluded.show_name, show_art=excluded.show_art,
           ep_title=excluded.ep_title, genre=excluded.genre, minutes=excluded.minutes, kind=excluded.kind,
           listened_at=excluded.listened_at, rating=excluded.rating, hour=excluded.hour, feed_url=excluded.feed_url, updated_at=excluded.updated_at`,
      ).bind(u.id, epId, str(e.showId, 120), str(e.showName, 200), str(e.showArt, 500), str(e.epTitle, 300), str(e.genre, 60), int(e.minutes, 0, 0, 3000), e.kind === "side" ? "side" : "main", listened, rating, int(e.hour, 20, 0, 23), str(e.feedUrl, 1000), now),
    );
    if (listened) {
      st.push(
        env.DB.prepare("INSERT OR IGNORE INTO events (user_id, type, ref, payload, created_at) VALUES (?, 'listen', ?, ?, ?)").bind(
          u.id,
          epId,
          JSON.stringify({ epId, epTitle: str(e.epTitle, 300), showId: str(e.showId, 120), showName: str(e.showName, 200), showArt: str(e.showArt, 500), feedUrl: str(e.feedUrl, 1000), rating, listenedAt: listened }),
          now,
        ),
      );
    }
  }
  for (const r of (Array.isArray(b.showRatings) ? b.showRatings : []).slice(0, 1000)) {
    const id = str(r.showId, 120);
    if (!id) continue;
    const rating = r.rating == null ? 0 : int(r.rating, 0, 0, 10);
    if (!rating) st.push(env.DB.prepare("DELETE FROM show_ratings WHERE user_id=? AND show_id=?").bind(u.id, id));
    else
      st.push(
        env.DB.prepare(
          `INSERT INTO show_ratings (user_id, show_id, show_name, show_art, rating, updated_at) VALUES (?, ?, ?, ?, ?, ?)
           ON CONFLICT(user_id, show_id) DO UPDATE SET show_name=excluded.show_name, show_art=excluded.show_art, rating=excluded.rating, updated_at=excluded.updated_at`,
        ).bind(u.id, id, str(r.showName, 200), str(r.showArt, 500), rating, now),
      );
  }
  for (const l of (Array.isArray(b.lists) ? b.lists : []).slice(0, 200)) {
    const id = str(l.id, 80);
    if (!id) continue;
    st.push(
      env.DB.prepare(
        `INSERT INTO lists (user_id, id, name, items, updated_at, deleted) VALUES (?, ?, ?, ?, ?, ?)
         ON CONFLICT(user_id, id) DO UPDATE SET name=excluded.name, items=excluded.items, updated_at=excluded.updated_at, deleted=excluded.deleted`,
      ).bind(u.id, id, str(l.name, 100), JSON.stringify(Array.isArray(l.items) ? l.items.slice(0, 1000) : []), now, l.deleted ? 1 : 0),
    );
  }
  for (const s of (Array.isArray(b.subs) ? b.subs : []).slice(0, 1000)) {
    const id = str(s.showId, 120);
    if (!id) continue;
    st.push(
      env.DB.prepare(
        `INSERT INTO subs (user_id, show_id, name, art, feed_url, updated_at, deleted) VALUES (?, ?, ?, ?, ?, ?, ?)
         ON CONFLICT(user_id, show_id) DO UPDATE SET name=excluded.name, art=excluded.art, feed_url=excluded.feed_url, updated_at=excluded.updated_at, deleted=excluded.deleted`,
      ).bind(u.id, id, str(s.name, 200), str(s.art, 500), str(s.feedUrl, 1000), now, s.deleted ? 1 : 0),
    );
  }
  for (const p of (Array.isArray(b.progress) ? b.progress : []).slice(0, 2000)) {
    const id = str(p.epId, 120);
    if (!id) continue;
    st.push(
      env.DB.prepare(
        `INSERT INTO progress (user_id, ep_id, seconds, updated_at) VALUES (?, ?, ?, ?)
         ON CONFLICT(user_id, ep_id) DO UPDATE SET seconds=excluded.seconds, updated_at=excluded.updated_at`,
      ).bind(u.id, id, int(p.seconds, 0, 0, 86_400), now),
    );
  }
  if (Array.isArray(b.badges)) {
    const badges = (b.badges as any[]).slice(0, 60).map((x) => ({ id: str(x.id, 40), tier: int(x.tier, 0, 0, 10) }));
    st.push(env.DB.prepare("UPDATE users SET badges=? WHERE id=?").bind(JSON.stringify(badges), u.id));
  }
  for (let i = 0; i < st.length; i += 90) await env.DB.batch(st.slice(i, i + 90));
  return json(env, { ok: true, applied: st.length, at: now });
}

export async function pull(env: Env, req: Request): Promise<Response> {
  const u = await authed(env, req);
  const since = int(new URL(req.url).searchParams.get("since"), 0, 0);
  const q = (sql: string) => env.DB.prepare(sql).bind(u.id, since).all();
  const [log, lists, subs, progress, showRatings] = await Promise.all([
    q("SELECT ep_id, show_id, show_name, show_art, ep_title, genre, minutes, kind, listened_at, rating, hour, feed_url FROM log_entries WHERE user_id=? AND updated_at>?"),
    q("SELECT id, name, items, deleted FROM lists WHERE user_id=? AND updated_at>?"),
    q("SELECT show_id, name, art, feed_url, deleted FROM subs WHERE user_id=? AND updated_at>?"),
    q("SELECT ep_id, seconds FROM progress WHERE user_id=? AND updated_at>?"),
    q("SELECT show_id, show_name, show_art, rating FROM show_ratings WHERE user_id=? AND updated_at>?"),
  ]);
  return json(env, {
    at: Date.now(),
    user: pub(u),
    log: log.results,
    lists: lists.results.map((l: any) => ({ ...l, items: JSON.parse(l.items) })),
    subs: subs.results,
    progress: progress.results,
    showRatings: showRatings.results,
  });
}

// ---------- people ----------

export async function follow(env: Env, req: Request, handle: string, on: boolean): Promise<Response> {
  const u = await authed(env, req);
  const t = await byHandle(env, handle);
  if (t.id === u.id) throw new HttpError(400, "You can't follow yourself");
  if (on) await env.DB.prepare("INSERT OR IGNORE INTO follows (follower_id, followee_id, created_at) VALUES (?, ?, ?)").bind(u.id, t.id, Date.now()).run();
  else await env.DB.prepare("DELETE FROM follows WHERE follower_id=? AND followee_id=?").bind(u.id, t.id).run();
  return json(env, { following: on });
}

/** People on this server. With no query, the newest members. Handy for a small friends-only server. */
export async function searchUsers(env: Env, req: Request): Promise<Response> {
  const u = await authed(env, req);
  const q = (new URL(req.url).searchParams.get("q") || "").trim().toLowerCase().replace(/^@/, "").replace(/[%_]/g, "");
  const like = `%${q}%`;
  const rows = await env.DB.prepare(
    `SELECT us.handle, us.name, us.avatar_v, us.bio,
            EXISTS (SELECT 1 FROM follows f WHERE f.follower_id = ? AND f.followee_id = us.id) AS following
       FROM users us WHERE us.id != ? AND (? = '' OR us.handle LIKE ? OR lower(us.name) LIKE ?)
      ORDER BY following DESC, us.created_at DESC LIMIT 40`,
  ).bind(u.id, u.id, q, like, like).all<any>();
  return json(env, { users: rows.results.map((r) => ({ handle: r.handle, name: r.name, bio: r.bio, avatarV: r.avatar_v, following: !!r.following })) });
}

export async function feed(env: Env, req: Request): Promise<Response> {
  const u = await authed(env, req);
  const before = int(new URL(req.url).searchParams.get("before"), Date.now() + 1);
  const rows = await env.DB.prepare(
    `SELECT e.id, e.type, e.payload, e.created_at, us.handle, us.name, us.avatar_v
       FROM events e JOIN follows f ON f.followee_id = e.user_id AND f.follower_id = ?
       JOIN users us ON us.id = e.user_id
      WHERE e.created_at < ? ORDER BY e.created_at DESC LIMIT 50`,
  ).bind(u.id, before).all<any>();
  return json(env, {
    items: rows.results.map((r) => ({ id: r.id, type: r.type, at: r.created_at, user: { handle: r.handle, name: r.name, avatarV: r.avatar_v }, ...JSON.parse(r.payload) })),
  });
}

async function stats(env: Env, userId: string, year?: string) {
  const like = year ? `${year}-%` : "%";
  const base = "FROM log_entries WHERE user_id=? AND listened_at IS NOT NULL AND listened_at LIKE ?";
  const [tot, shows, months, ratings, hours] = await Promise.all([
    env.DB.prepare(`SELECT COUNT(*) AS episodes, COALESCE(SUM(minutes),0) AS minutes, AVG(rating) AS avg_rating ${base}`).bind(userId, like).first<any>(),
    env.DB.prepare(`SELECT show_id, show_name, show_art, feed_url, COUNT(*) AS episodes, SUM(minutes) AS minutes, AVG(rating) AS avg_rating ${base} GROUP BY show_id ORDER BY minutes DESC LIMIT 10`).bind(userId, like).all(),
    env.DB.prepare(`SELECT substr(listened_at,1,7) AS month, SUM(minutes) AS minutes, COUNT(*) AS episodes ${base} GROUP BY month ORDER BY month`).bind(userId, like).all(),
    env.DB.prepare(`SELECT rating, COUNT(*) AS n FROM log_entries WHERE user_id=? AND rating IS NOT NULL AND (listened_at IS NULL OR listened_at LIKE ?) GROUP BY rating ORDER BY rating`).bind(userId, like).all(),
    env.DB.prepare(`SELECT hour, COUNT(*) AS n ${base} GROUP BY hour ORDER BY hour`).bind(userId, like).all(),
  ]);
  return {
    episodes: tot?.episodes ?? 0,
    hours: Math.round(((tot?.minutes ?? 0) / 60) * 10) / 10,
    avgRating: tot?.avg_rating == null ? null : Math.round(tot.avg_rating * 10) / 10,
    topShows: shows.results,
    months: months.results,
    ratings: ratings.results,
    hours24: hours.results,
  };
}

export async function profile(env: Env, req: Request, handle: string): Promise<Response> {
  const { user: viewer } = await member(env, req);
  const u = await byHandle(env, handle);
  const [s, followers, following, recent, isFollowing, rated] = await Promise.all([
    stats(env, u.id),
    env.DB.prepare("SELECT COUNT(*) AS n FROM follows WHERE followee_id=?").bind(u.id).first<{ n: number }>(),
    env.DB.prepare("SELECT COUNT(*) AS n FROM follows WHERE follower_id=?").bind(u.id).first<{ n: number }>(),
    env.DB.prepare(
      `SELECT ep_id AS epId, ep_title AS epTitle, show_id AS showId, show_name AS showName, show_art AS showArt, feed_url AS feedUrl, rating, listened_at AS listenedAt
         FROM log_entries WHERE user_id=? AND listened_at IS NOT NULL ORDER BY listened_at DESC, updated_at DESC LIMIT 20`,
    ).bind(u.id).all(),
    viewer ? env.DB.prepare("SELECT 1 AS x FROM follows WHERE follower_id=? AND followee_id=?").bind(viewer.id, u.id).first() : Promise.resolve(null),
    env.DB.prepare("SELECT show_id AS showId, show_name AS showName, show_art AS showArt, rating FROM show_ratings WHERE user_id=? ORDER BY rating DESC, updated_at DESC LIMIT 12").bind(u.id).all(),
  ]);
  return json(env, {
    ...pub(u),
    followers: followers?.n ?? 0,
    following: following?.n ?? 0,
    isFollowing: !!isFollowing,
    isMe: viewer?.id === u.id,
    stats: s,
    recent: recent.results,
    showRatings: rated.results,
  });
}

export async function wrapped(env: Env, req: Request, handle: string, year: string): Promise<Response> {
  await member(env, req);
  if (!/^\d{4}$/.test(year)) throw new HttpError(400, "year must be 4 digits");
  const u = await byHandle(env, handle);
  return json(env, { user: { handle: u.handle, name: u.name }, year: Number(year), ...(await stats(env, u.id, year)) });
}

// ---------- community ratings ----------

/** What everyone on this server thinks of a show and its episodes. */
export async function showRatings(env: Env, req: Request, showId: string): Promise<Response> {
  const { user } = await member(env, req);
  const [sr, er, eps, mine] = await Promise.all([
    env.DB.prepare("SELECT AVG(rating) AS avg, COUNT(*) AS n FROM show_ratings WHERE show_id=?").bind(showId).first<{ avg: number | null; n: number }>(),
    env.DB.prepare("SELECT AVG(rating) AS avg, COUNT(*) AS n FROM log_entries WHERE show_id=? AND rating IS NOT NULL").bind(showId).first<{ avg: number | null; n: number }>(),
    env.DB.prepare("SELECT ep_id, AVG(rating) AS avg, COUNT(*) AS n FROM log_entries WHERE show_id=? AND rating IS NOT NULL GROUP BY ep_id").bind(showId).all<{ ep_id: string; avg: number; n: number }>(),
    user ? env.DB.prepare("SELECT rating FROM show_ratings WHERE user_id=? AND show_id=?").bind(user.id, showId).first<{ rating: number }>() : Promise.resolve(null),
  ]);
  const r1 = (x: number | null | undefined) => (x == null ? null : Math.round(x * 10) / 10);
  const episodes: Record<string, { avg: number | null; n: number }> = {};
  for (const e of eps.results) episodes[e.ep_id] = { avg: r1(e.avg), n: e.n };
  return json(env, {
    showAvg: r1(sr?.avg),
    showN: sr?.n ?? 0,
    epsAvg: r1(er?.avg),
    epsN: er?.n ?? 0,
    yourShowRating: mine?.rating ?? null,
    episodes,
  });
}
