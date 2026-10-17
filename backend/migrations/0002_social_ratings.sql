-- v1.0: avatars, show ratings, who asked for a transcript, feed links.

ALTER TABLE users ADD COLUMN avatar   TEXT    NOT NULL DEFAULT '';   -- base64 JPEG, at most ~120 KB
ALTER TABLE users ADD COLUMN avatar_v INTEGER NOT NULL DEFAULT 0;     -- changes whenever the avatar does (cache buster)

ALTER TABLE log_entries ADD COLUMN feed_url TEXT NOT NULL DEFAULT '';  -- lets a friend open the show from your activity

ALTER TABLE transcripts ADD COLUMN requested_by TEXT NOT NULL DEFAULT '';  -- user id, used for the per-user daily limit
CREATE INDEX idx_tx_requested ON transcripts (requested_by, created_at);

-- Your overall rating of a whole show (separate from episode ratings).
CREATE TABLE show_ratings (
  user_id    TEXT NOT NULL,
  show_id    TEXT NOT NULL,
  show_name  TEXT NOT NULL DEFAULT '',
  show_art   TEXT NOT NULL DEFAULT '',
  rating     INTEGER NOT NULL,           -- 1..10
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (user_id, show_id)
);
CREATE INDEX idx_show_ratings_show ON show_ratings (show_id);
CREATE INDEX idx_log_show ON log_entries (show_id);
