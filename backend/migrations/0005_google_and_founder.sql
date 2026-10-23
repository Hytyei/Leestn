-- Sign in with Google, several devices per account, and the founder badge. Additive only: nothing is dropped or rewritten
-- except the one `role` value set for the founder at the end.

-- A Google account can be linked to one Leestn account (and one Leestn account to one Google account).
ALTER TABLE users ADD COLUMN google_sub TEXT;
ALTER TABLE users ADD COLUMN google_email TEXT;
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_google_sub ON users (google_sub) WHERE google_sub IS NOT NULL;

-- 'founder' shows a verified badge next to the name. Empty for everybody else.
ALTER TABLE users ADD COLUMN role TEXT NOT NULL DEFAULT '';

-- Signing in with Google on a new device creates an extra token. The original token in users.token_hash keeps working.
CREATE TABLE IF NOT EXISTS user_tokens (
  token_hash TEXT PRIMARY KEY,
  user_id    TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_user_tokens_user ON user_tokens (user_id);

UPDATE users SET role = 'founder' WHERE lower(handle) = 'hytyei';
