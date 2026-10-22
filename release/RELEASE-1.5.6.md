# Release 1.5.6: transcript timing that doesn't drift, playback speed, line highlighter

## New in the app (Android release)

- **Playback speed.** Tap the speed button in the full player, or the new **Speed** button on an episode page, to open a speed window: a slider from 0.5x to 3x in 0.05 steps, -0.05 / +0.05 buttons, and presets (0.75, 1, 1.25, 1.5, 1.75, 2, 2.5, 3). The speed is remembered.
- **Manual line highlighter (plain-text mode).** When Timings is off, or the transcript has no timestamps, tap a line to highlight it (same look as the playing line in timing mode: darker background and a red bar). Tap again to remove it. A **Clear highlights** chip appears when any are set. Highlights are kept on your phone per episode, separately for the original and pasted text. Pasting new text clears the pasted text's highlights, because the line numbers change. With Timings on and the transcript timed, tapping a line still jumps to it as before.

## Backend: timing that doesn't drift

**What was wrong.** The server cuts an episode into slices of about a minute and sends each to the speech model. It placed each slice on the timeline by guessing: "this slice starts X bytes into the file, so it starts at X/size of the episode's length". That is only right for constant-bitrate audio. Many podcasts are variable bitrate (quiet parts use fewer bytes), so a slice's real start time can be many seconds off, and the error grows differently for every slice. That is why a fix for the whole transcript (a constant shift) stops working after the first minute or two: the error isn't constant.

**What changes.** For MP3 (and raw AAC) episodes, each slice now measures its own length by counting its audio frames, and the true start of every slice is the sum of the lengths of the slices before it (added up when the transcript is assembled, whatever order the slices finish in). Tested with a synthetic variable-bitrate file: every slice lands within 1 second of its true time, also when the file starts with a big cover-art tag, and when the slices finish out of order. M4A episodes were already exact. If a slice doesn't parse as MP3 the server falls back to the old estimate for that slice.

**What this does not fix**
- Transcripts that already exist keep their old timings. Press **Redo** on an episode (uses free Whisper time) to get the new timing, or fix it with Sync.
- Episodes where the server's download differs from what your phone plays (dynamically inserted ads) can still be offset, and no timing method fixes that. Sync can fix a constant offset.
- Invented lines (the model producing text over music or silence) are a different problem and are unchanged by this release.

| Question | Answer |
| --- | --- |
| Database migration needed? | **No.** No tables or columns change. Do not run any migration step. The format of the temporary per-slice data changes, but it is deleted when each transcript finishes and the new code reads both old and new formats. (If you never deployed 1.5.4, the script will still list and apply `0004_transcript_timing.sql` first, after a backup and your `YES`; likewise 0003 if you never deployed 1.5.0.) |
| Worker deployment needed? | **Yes.** |
| Android release needed? | **Yes**, for speed and highlighter (app 1.5.6, versionCode 21). The timing fix itself is server-side and works with any app version. |
| New secrets, bindings, dependencies? | None. `wrangler.toml` untouched. |

## Steps

Pick the patch that matches what you have applied (all bring you to 1.5.6):

- `leestn-v1.5.6-from-1.5.5.patch`: you applied 1.5.5.
- `leestn-v1.5.6-from-1.5.4.patch`: you stopped at 1.5.4.
- `leestn-v1.5.6-from-1.0.patch`: you applied none of the 1.5.x patches (this one also includes the 1.5.x app changes).

Check first (changes nothing), then apply (files on your PC only):

    git apply --check <patch name>
    git apply <patch name>

`git status` must not list `backend/wrangler.toml`. Then:

    powershell -ExecutionPolicy Bypass -File .\release\release-backend.ps1

The script checks `wrangler.toml` points at the real Worker `leestn-api`, database `leestn` and queue `leestn-transcribe`; runs the tests on your PC; lists waiting migrations (read-only; expect "No migrations to apply"); asks `YES`, then saves a full backup of the live database to `backups\`; does a dry run of the Worker; asks `YES`, then deploys the existing `leestn-api`; checks `/health` shows `1.5.6`.

Save it in Git: `git add -A`, `git commit -m "1.5.6: exact MP3 timing, playback speed, line highlighter"`, `git push`. Then the app: `git tag v1.5.6`, `git push origin v1.5.6`; when the GitHub build finishes, open Settings → About → Check for updates on the phone.

**Safe moment to deploy:** avoid deploying while a transcript is being generated. A slice already stored in the old format while others use the new one only affects that one transcript's timing, and Redo fixes it.

## Rollback

Code only: `npx wrangler rollback` in `backend`. No data changes in this release.

## Risks

- **The Android code has never been compiled by me.** The new speed window and highlighter are untested on a device; if the build fails, paste the error and I will fix it.

- **Tested with synthetic MP3 files, not with a real podcast through the real Workers AI.** Please Redo one episode that had drifting timing and tell me whether it is now right, and where it is still off.
- Counting frames assumes standard MP3 (MPEG audio Layer III). Anything else falls back to the old estimate.
- The time between slices is exact only to about a frame (26 ms) per slice; over 100 slices a fraction of a second at most.
