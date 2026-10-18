-- 0003: remember how many seconds of audio each transcript job covers,
-- so the server can show a "free Whisper allowance" meter and stop before the limit.
-- Additive only: existing rows get 0 and nothing is removed or rewritten.
ALTER TABLE transcripts ADD COLUMN audio_sec INTEGER NOT NULL DEFAULT 0;
