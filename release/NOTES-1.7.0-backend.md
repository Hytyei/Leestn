# Leestn 1.7.0 - backend notes (rename, open friends, own feed)

## What changed
1. **Username Hytyeileestn -> Hytyei.** Only `users.handle` stores the name. Your login token, Google link, log, ratings, follows and transcripts are all tied to your internal user id, so nothing is lost and the Whisper connection keeps working.
2. **Friends are open, Whisper stays invite-only.**
   - Anyone can sign up (with or without the invite key). A new account gets `can_transcribe = 0` unless it supplied the right key.
   - Transcript routes (read, request, usage, timing) need `can_transcribe = 1`. Friends, feed, profiles and sync do not.
   - A locked user enters the key in Settings -> "Unlock transcription" (`POST /v1/me/unlock`).
3. **Feed includes your own activity** (what, how long, rating), marked `mine: true`.

## Migration 0006 (additive, forward-only) - explained
`backend/migrations/0006_open_friends_transcribe_rename.sql` does four things to the existing `users` table. It deletes nothing:
- adds a column `can_transcribe` (default 0);
- sets it to 1 for everyone who already exists, so current users keep transcription;
- marks the account `hytyeileestn` as `founder` (badge);
- renames `hytyeileestn` to `hytyei`, but ONLY if nobody already uses `hytyei`. If someone does, no rename happens and no badge moves; the owner keeps founder.

Run it only after backing up (see the release script). Check it with the usual read-only query afterwards; do not claim success without the command output.

## New optional settings (not secrets, no wrangler.toml edit needed)
| Variable | Default | Meaning |
|---|---|---|
| `SIGNUPS` | open | Set to `invite` to go back to invite-key-only sign-up. |
| `MAX_SIGNUPS_PER_DAY` | 50 | Cap on new keyless accounts in a rolling 24 hours (HTTP 429 above it). |

Set them in the Cloudflare dashboard (Worker -> Settings -> Variables) only if you want to change the defaults. Nothing touches `wrangler.toml`.

## Order of release
Migration first, then Worker deploy. The Worker also works if 0006 is not applied yet (sign-in and sign-up still work and everyone counts as unlocked), but the rename and lock need the migration.

## Risks and manual steps
- Open sign-up means strangers who find the Worker URL can create accounts. The daily cap limits this; set `SIGNUPS=invite` to close it.
- The old key still works for sign-up AND unlocks transcription. If it leaks, rotate `INVITE_KEY`; already-unlocked users stay unlocked.
- Old installs (1.5.x) still work: they send the key, so they stay unlocked. They keep the handle they typed locally until 1.7.0 refreshes it from the server.
- `ui/FriendsScreen.kt` `InviteCard` (not touched here) now offers only the friends link, and its text says friends get transcripts; the main thread should reword it.
- Release script step 8 should check for `hytyei`.

## Tests
`cd backend && bun run test` (10 files incl. new `friends.ts`, `rename.ts`) and `tsc` typecheck.
