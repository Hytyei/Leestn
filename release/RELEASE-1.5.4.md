# Release 1.5.4: fix transcript timing by hand (shared with everyone), hide timings, paste your own text

**What changes for people** (on an episode's transcript, a row of buttons appears under the title)

1. **Sync: fix the timing by hand.** Play the episode, tap **Set** on the line you are hearing now. One Set moves the whole transcript; two Sets far apart (say near the start and near the end) also fix drift, and the lines in between are spread evenly. **−5 s, −1 s, +1 s, +5 s** nudge everything, **Reset** undoes it.
   - **For a transcript the server generated, the fix is saved on the server and everyone on your server sees it** (the last person to save wins). It is also kept on your phone. If the server can't be reached, the app says "Saved on this phone, but not on the server yet".
   - For a transcript that came from the podcast publisher, or for pasted text, the fix is saved on your phone only.
   - Pressing **Redo** (new transcription) clears the shared fix, because the lines are different.
2. **Timings toggle.** Off shows the transcript as plain text with no times and no follow-along. On brings the timings back.
3. **Paste text.** Paste a transcript from anywhere. Plain text works (blank lines or new lines make paragraphs). Lines that start with a time (`0:12 text`, `[01:02:03] text`) or WebVTT/SRT files keep their timings. Pasted text without timings can be given timings with Sync (Set at least two lines). When an episode has both an original and a pasted transcript, **Original / Pasted** buttons switch between them. Pasted text is stored only on your phone.
4. **Better automatic timing for MP3 (server).** The server used the duration from the podcast's feed to place each part, and feeds are often wrong, so timestamps drifted. It now reads the MP3's own header: the exact frame count from a Xing/Info header (and the seek table for variable bitrate), or the constant bitrate. The feed's duration is only a fallback.

Already-generated transcripts keep their old timings. For those, either press **Redo** (uses free Whisper time) or fix them with Sync.

| Question | Answer |
| --- | --- |
| Database migration needed? | **Yes, one, additive:** `0004_transcript_timing.sql` creates a new table `transcript_edits` (one row per shared timing fix). Nothing existing is changed, dropped or rewritten. (If you never deployed 1.5.0, the script will also list `0003_usage_meter.sql`; it is applied first.) |
| Worker deployment needed? | **Yes.** Shared timing, better MP3 timing. |
| Android release needed? | **Yes.** |
| New secrets, bindings, dependencies? | None. `wrangler.toml` untouched. |

**Order matters: the script applies the migration first, then deploys.** (The new Worker also keeps working if the table is missing: transcripts still load and generate, but timing fixes can't be saved until the migration is applied.)

## Steps

Pick the patch that matches what you have applied so far (all three bring you to 1.5.4):

- `leestn-v1.5.4.patch`: you applied 1.5.2 (the last one that built for you).
- `leestn-v1.5.4-from-1.5.3.patch`: you already applied the 1.5.3 patch I sent earlier.
- `leestn-v1.5.4-from-1.0.patch`: you applied none of the 1.5.x patches.

Check first (changes nothing), then apply (files on your PC only):

    git apply --check <patch name>
    git apply <patch name>

`git status` must not list `backend/wrangler.toml`. Then:

    powershell -ExecutionPolicy Bypass -File .\release\release-backend.ps1

The script: checks `wrangler.toml` points at the real Worker `leestn-api`, database `leestn` and queue `leestn-transcribe`; runs the tests on your PC; **lists waiting migrations (read-only; expect `0004_transcript_timing.sql`)**; asks `YES`, then saves a full backup of the live database to `backups\` and notes the Time Travel restore point; asks `YES`, then applies the migration to the existing `leestn` database; does a dry run of the Worker; asks `YES`, then deploys the existing `leestn-api`; checks `/health` shows `1.5.4`.

App: `git add -A`, commit, push, `git tag v1.5.4`, `git push origin v1.5.4`; then Settings → About → Check for updates.

## Rollback

- **Migration applied, deploy failed:** safe. The old Worker ignores the new table. Run the script again; applied migrations never run twice.
- **New Worker misbehaves:** `npx wrangler rollback` in `backend` (code only). The new table can stay; the old code doesn't use it.
- **Data damaged (very unlikely):** don't improvise. Keep `backups\leestn-*.sql` and the `.timetravel.txt` beside it and ask me. D1 Time Travel can restore to before the migration, but it also discards everything written after that point.
- On the phone, pasted text and local timing fixes live in the app's private storage and survive updates.

## Risks

- **The Android code has never been compiled by me.** Last time a build error slipped in; this release adds a lot of new screen code, so a build error is possible. Paste it and I'll fix it right away.
- The timing logic (spreading lines between your Set points, pasted-text parsing) and the server API are tested (the logic by compiling it with the real Kotlin compiler; the API with a copy of your current schema plus existing data), but the buttons and layout have not been run on a device.
- **Anyone on your server can change the shared timing** of a transcript, and the last save wins. There is no history or undo beyond Reset.
- Variable-bitrate MP3s without a Xing header are still estimated as constant bitrate and can drift; Sync fixes those.
- Episodes with dynamically inserted ads can differ between downloads, so a fix that is right for one download may be off for another.
