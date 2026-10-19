# Release 1.5.3: fix transcript timing by hand, hide timings, paste your own text

**What changes for people** (on an episode's transcript, a row of buttons appears under the title)

1. **Sync** (manual timing fix, saved on your phone). Play the episode, tap **Set** on the line you are hearing now. One Set moves the whole transcript; two Sets far apart (say near the start and near the end) also fix drift, and the lines in between are spread evenly. **−5 s, −1 s, +1 s, +5 s** nudge everything, **Reset** undoes it. Every change is saved immediately, per episode.
2. **Timings toggle.** Off shows the transcript as plain text with no times and no follow-along. On brings the timings back.
3. **Paste text.** Paste a transcript from anywhere. Plain text works (blank lines or new lines make paragraphs). Lines that start with a time (`0:12 text`, `[01:02:03] text`) or WebVTT/SRT files keep their timings. Pasted text without timings can be given timings with Sync (Set at least two lines). When an episode has both an original and a pasted transcript, **Original / Pasted** buttons switch between them. Pasted text and timing fixes are stored only on your device.
4. **Better automatic timing for MP3 (server).** The server used the duration from the podcast's feed to place each part, and feeds are often wrong (ads, re-encodes), so timestamps drifted. It now reads the MP3's own header: the exact frame count from a Xing/Info header (and the seek table for variable bitrate), or the constant bitrate. The feed's duration is only a fallback.

Already-generated transcripts keep their old (wrong) timings. For those, either press **Redo** (uses free Whisper time) or fix them with Sync.

| Question | Answer |
| --- | --- |
| Database migration needed? | **No.** No tables or columns change. Do not run a migration. (If you never deployed 1.5.0, the script will still show `0003_usage_meter.sql` and apply it after a backup and your `YES`.) |
| Worker deployment needed? | **Yes**, for the better MP3 timing only. Sync, Timings and Paste work without it. |
| Android release needed? | **Yes.** |
| New secrets, bindings, dependencies? | None. `wrangler.toml` untouched. |

## Steps

Patches in the zip: `leestn-v1.5.3.patch` (if you applied 1.5.2) or `leestn-v1.5.3-from-1.0.patch` (everything). Check, then apply (files on your PC only):

    git apply --check <patch name>
    git apply <patch name>

`git status` must not list `backend/wrangler.toml`. Then:

    powershell -ExecutionPolicy Bypass -File .\release\release-backend.ps1

It runs the tests, lists waiting migrations (expect none), asks `YES` for a backup, asks `YES` to deploy the existing `leestn-api`, and checks `/health` shows `1.5.3`.

App: `git add -A`, commit, push, `git tag v1.5.3`, `git push origin v1.5.3`; then Settings → About → Check for updates.

## Rollback

Code only: `npx wrangler rollback` in `backend`. No data changes in this release. On the phone, your pasted text and timing fixes live in the app's private storage; they survive updates and are removed only if you erase the app's data.

## Risks

- **The Android code has never been compiled by me.** Last time a build error slipped in; this release adds a lot of new screen code, so a build error is possible. Paste it and I'll fix it right away.
- The timing logic (spreading lines between your Set points, pasted-text parsing) is tested by compiling it with the real Kotlin compiler and running it, but the buttons and layout have not been run on a device.
- The new MP3 timing is tested with synthetic files (Xing, Info and constant-bitrate headers). Variable-bitrate files without a Xing header are still estimated as constant bitrate and can drift; Sync fixes those.
- Transcripts with ads inserted dynamically can differ between downloads, so the same episode can have a different offset at different times. Sync is saved per device.
- Syncing and pasting are not shared with friends or the server; they are only on your phone.
