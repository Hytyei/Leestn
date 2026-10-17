-- Leestn backend schema (Cloudflare D1 / SQLite)

CREATE TABLE users (
  id          TEXT PRIMARY KEY,
  handle      TEXT NOT NULL UNIQUE COLLATE NOCASE,
  name        TEXT NOT NULL,
  bio         TEXT NOT NULL DEFAULT '',
  genres      TEXT NOT NULL DEFAULT '[]',   -- JSON array of favourite genres
  badges      TEXT NOT NULL DEFAULT '[]',   -- JSON array of {id, tier}
  token_hash  TEXT NOT NULL UNIQUE,         -- SHA-256 of the user's secret token
  created_at  INTEGER NOT NULL
);

-- One row per episode a user listened to or rated. Show and episode details are copied in,
-- so the server never needs a podcast catalogue to render profiles, stats and the feed.
CREATE TABLE log_entries (
  user_id     TEXT NOT NULL,
  ep_id       TEXT NOT NULL,
  show_id     TEXT NOT NULL,
  show_name   TEXT NOT NULL DEFAULT '',
  show_art    TEXT NOT NULL DEFAULT '',
  ep_title    TEXT NOT NULL DEFAULT '',
  genre       TEXT NOT NULL DEFAULT '',
  minutes     INTEGER NOT NULL DEFAULT 0,
  kind        TEXT NOT NULL DEFAULT 'main',  -- main | side
  listened_at TEXT,                          -- ISO date, null when only rated
  rating      INTEGER,                       -- 1..10
  hour        INTEGER NOT NULL DEFAULT 20,
  updated_at  INTEGER NOT NULL,
  PRIMARY KEY (user_id, ep_id)
);
CREATE INDEX idx_log_user_date ON log_entries (user_id, listened_at);
CREATE INDEX idx_log_ep ON log_entries (ep_id);

CREATE TABLE lists (
  user_id     TEXT NOT NULL,
  id          TEXT NOT NULL,
  name        TEXT NOT NULL,
  items       TEXT NOT NULL DEFAULT '[]',    -- JSON array of {type, id, title, show, art}
  updated_at  INTEGER NOT NULL,
  deleted     INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (user_id, id)
);

CREATE TABLE subs (
  user_id     TEXT NOT NULL,
  show_id     TEXT NOT NULL,
  name        TEXT NOT NULL DEFAULT '',
  art         TEXT NOT NULL DEFAULT '',
  feed_url    TEXT NOT NULL DEFAULT '',
  updated_at  INTEGER NOT NULL,
  deleted     INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (user_id, show_id)
);

CREATE TABLE progress (
  user_id     TEXT NOT NULL,
  ep_id       TEXT NOT NULL,
  seconds     INTEGER NOT NULL,
  updated_at  INTEGER NOT NULL,
  PRIMARY KEY (user_id, ep_id)
);

CREATE TABLE follows (
  follower_id TEXT NOT NULL,
  followee_id TEXT NOT NULL,
  created_at  INTEGER NOT NULL,
  PRIMARY KEY (follower_id, followee_id)
);
CREATE INDEX idx_follows_followee ON follows (followee_id);

-- Activity feed. One row per (user, type, ref) so re-syncing never duplicates an event.
CREATE TABLE events (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id     TEXT NOT NULL,
  type        TEXT NOT NULL,                 -- listen | list
  ref         TEXT NOT NULL,
  payload     TEXT NOT NULL,
  created_at  INTEGER NOT NULL,
  UNIQUE (user_id, type, ref)
);
CREATE INDEX idx_events_user_time ON events (user_id, created_at DESC);

-- Generated transcripts are shared: the first request processes the audio, everyone after reads the result.
CREATE TABLE transcripts (
  ep_id         TEXT PRIMARY KEY,
  status        TEXT NOT NULL,               -- queued | processing | ready | failed
  progress      REAL NOT NULL DEFAULT 0,
  error         TEXT,
  chunks_total  INTEGER NOT NULL DEFAULT 0,
  audio_url     TEXT NOT NULL,
  duration_sec  INTEGER NOT NULL DEFAULT 0,
  lines         TEXT,                        -- JSON array of {t, text} once ready
  created_at    INTEGER NOT NULL,
  updated_at    INTEGER NOT NULL
);

CREATE TABLE transcript_chunks (
  ep_id       TEXT NOT NULL,
  idx         INTEGER NOT NULL,
  segments    TEXT NOT NULL,                 -- JSON array of {start, end, text}, seconds from the start of the episode
  PRIMARY KEY (ep_id, idx)
);
