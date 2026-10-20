-- 0004: a shared timing fix for a transcript ("line 12 is really at 3:05"), saved by any member
-- and seen by everyone on the server. Additive only: a new table, nothing existing is changed.
CREATE TABLE IF NOT EXISTS transcript_edits (
  ep_id       TEXT PRIMARY KEY,
  offset_sec  INTEGER NOT NULL DEFAULT 0,
  anchors     TEXT NOT NULL DEFAULT '[]',
  updated_by  TEXT,
  updated_at  INTEGER NOT NULL
);
