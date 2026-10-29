-- 0006: friends are open, transcription stays invite-only; the owner's username becomes "hytyei".
-- Additive, nothing is deleted. Each statement is written so that it is harmless if the data is not there.

-- 1. Who may use Whisper. Everybody who already has an account joined with the invite key, so they all keep access.
ALTER TABLE users ADD COLUMN can_transcribe INTEGER NOT NULL DEFAULT 0;
UPDATE users SET can_transcribe = 1;

-- 2. The owner's account (hytyeileestn) is the founder. Migration 0005 gave the badge to "hytyei" before this account had that
--    name, so it never applied. Mark the owner's account itself, and not whoever happens to hold "hytyei".
UPDATE users SET role = 'founder' WHERE lower(handle) = 'hytyeileestn';

-- 3. Rename hytyeileestn -> hytyei, but only if "hytyei" is not already taken by someone else (then nothing is renamed).
--    Only the handle text changes. The account id, token, Google link, log, follows, lists and transcripts hang off the
--    account id (not the handle), so sign-in, Google sign-in and transcription keep working unchanged.
UPDATE users SET handle = 'hytyei'
 WHERE lower(handle) = 'hytyeileestn'
   AND NOT EXISTS (SELECT 1 FROM users u2 WHERE lower(u2.handle) = 'hytyei');
