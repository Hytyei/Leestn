# Release 1.7.0: new look, widgets, rename, open friends, side episodes

**This release changes the app, the server and the database.** Nothing is deleted. Read "Before you start" first.

## What's new

- **New look.** New colours, a floating bottom bar, cards, a Home with "Continue listening", "Your week" colour blocks and a streak card, and a larger, calmer layout. Every screen was restyled; every feature is still there.
- **Transcript.** Bigger text (20 sp). The line being played now sits about a third of the way down the screen instead of at the top. The buttons above the transcript now wrap into tidy rows instead of sliding sideways.
- **Side episodes (trailers, teasers, bonus).** On every episode page you can mark it as a side episode (or not). Side episodes are left out of the show average, the show rating and the rating graph. "Count side episodes in this show" turns them back on for that show. Titles that look like trailers or teasers are suggested automatically. Your labels are stored on your phone.
- **Art for RSS-linked podcasts.** Cover images are now fetched with a normal browser-style request and relative links are resolved. This is my best diagnosis (some image hosts refuse unknown apps), not a proven fix: tell me a show that is still blank.
- **Widgets.** Restyled to the new look, plus a new **Last activity** widget (show, episode, minutes, streak).
- **Username Hytyeileestn → Hytyei.** Done by the migration. Your key, Google link, ratings, log, followers and transcripts are attached to your internal id, not the name, so Whisper and Google sign-in keep working. The founder badge is set at the same time.
- **Friends are open, Whisper is invite only.** Anyone can sign up and add friends. Transcription needs the invite key: new people enter it in Settings. Existing users stay unlocked.
- **Feed shows your own activity** (what you listened to, how long, your rating), labelled "You".

## Database changes (migration 0006, only adds and renames one account)

See `NOTES-1.7.0-backend.md` for the detail. In short: adds `users.can_transcribe`, sets it to 1 for everybody who exists, marks `hytyeileestn` as founder and renames it to `hytyei` **only if nobody already uses `hytyei`**. Nothing is deleted.

| Question | Answer |
| --- | --- |
| Database migration needed? | **Yes**, `0006_open_friends_transcribe_rename.sql` (the script backs up first and asks for `YES`). |
| Worker deployment needed? | **Yes.** |
| Android release needed? | **Yes** (app 1.7.0, versionCode 26). |
| New secret? | No. Optional settings `SIGNUPS` and `MAX_SIGNUPS_PER_DAY` (see the notes). |

## Before you start

1. Make sure nobody else has taken the username `hytyei` (the script's step 8 lists the founder account afterwards; if the rename did not happen, tell me).
2. Your **Google setup stays as it is**: same Web client id secret, same Android client. Nothing to redo.

## Release steps

1. Apply the patch that matches what you have: `leestn-v1.7.0-from-1.5.9.patch` (or `-from-1.5.8`, `-from-1.5.7`, `-from-1.5.6`, `-from-1.5.5`, `-from-1.0`).

       git apply --check <patch name>
       git apply <patch name>

   `git status` must not list `backend/wrangler.toml`.
2. Backend (backup, migration, deploy, health check, founder check, all with `YES` gates):

       powershell -ExecutionPolicy Bypass -File .\release\release-backend.ps1

   It targets the existing Worker `leestn-api` and database `leestn` only.
3. Check: https://leestn-api.thehdv7.workers.dev/health should say version 1.7.0.
4. App:

       git add -A
       git commit -m "1.7.0: new look, widgets, rename, open friends, side episodes"
       git push
       git tag v1.7.0
       git push origin v1.7.0

   When the GitHub build finishes, open Settings → About → Check for updates.
5. Phone: sign in again if asked (same key, or Google). Your profile should read **Hytyei** with the gold badge. Add the new **Last activity** widget from the widget picker.

## Backup and rollback

- The script saves a backup to `backups\` and a Time Travel restore point first.
- Worker only: `npx wrangler rollback` in `backend`. The new column is harmless to older code. To undo the rename by hand (changes one row): `npx wrangler d1 execute leestn --remote --command "UPDATE users SET handle='hytyeileestn' WHERE handle='hytyei' AND id=(SELECT id FROM users WHERE role='founder' LIMIT 1)"` (ask me first).
- "Migration done but deploy failed": run the script again; it skips the finished migration and deploys.
- App: install the previous APK over it.

## Known limits (please read)

- **The Android code has never been compiled by me.** Many files changed; a build error is possible. Paste it and I'll fix it.
- **The redesign is partial.** Done: colours, bottom bar, Home, cards on every screen, Episode page. Redone: every screen.
- Widgets and RSS art are unseen on a device.
- The TypeScript type check was not re-run in this sandbox (npm was unreachable). The 247 backend tests pass.
- Open sign-up lets strangers create (non-transcribing) accounts, capped per day; set `SIGNUPS=invite` to close it. If the invite key leaks, rotate `INVITE_KEY`.
