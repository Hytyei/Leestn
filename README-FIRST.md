# Release 1.5.2: retry works, better transcript text, remove from Continue listening

**What changes for people**

1. **Retry after a failed transcript now really retries.** Before, "Try again" only re-read the old failure from the server. Now it starts a new transcription. If the failure was "limit reached", it will work again after the reset.
2. **Better transcript text** (the cause of garbled or wrong-language text):
   - The show's language (from its feed) is now passed to the speech model. Before, the model guessed the language for every 1 MB slice separately, and short or musical slices came out as the wrong language or nonsense.
   - The cover-art block at the start of MP3 files (ID3 tag) is no longer sent as "audio", and every slice now starts on a real MP3 frame.
   - Lines the model invents over silence, loops of the same line, and low-confidence lines are removed.
   - Timestamps are placed from the real byte position instead of a rough slice number.
   - If the model refuses the new options, the server automatically falls back to the old plain call.
3. **"Redo" button** on a generated transcript (when you're connected) to transcribe it again. It uses free Whisper time again and replaces the shared transcript for everyone on the server. Old bad transcripts are not fixed automatically; press Redo on the ones that look wrong.
4. **Remove from Continue listening:** an X on each item. It forgets the saved position.

| Question | Answer |
| --- | --- |
| Database migration needed? | **No.** No tables or columns change. Do not run a migration. (If you never deployed 1.5.0, the script will still show and apply `0003_usage_meter.sql` after a backup and your `YES`.) |
| Worker deployment needed? | **Yes.** |
| Android release needed? | **Yes** (retry fix, Redo, language, remove button). The text-quality fixes that don't need the language (ID3, filtering, timestamps) work with the old app too. |
| New secrets, bindings, dependencies? | None. `wrangler.toml` untouched. |

## Steps

Patches in the zip: `leestn-v1.5.2.patch` (if you already applied 1.5.1) or `leestn-v1.5.2-from-1.0.patch` (everything, if you never applied any of the 1.5.x patches). Check, then apply (files on your PC only):

    git apply --check <patch name>
    git apply <patch name>

`git status` must not list `backend/wrangler.toml`. Then:

    powershell -ExecutionPolicy Bypass -File .\release\release-backend.ps1

It runs the tests, lists waiting migrations (expect none), asks `YES` for a backup, asks `YES` to deploy the existing `leestn-api`, and checks `/health` shows `1.5.2`.

App: `git add -A`, commit, push, `git tag v1.5.2`, `git push origin v1.5.2`. The app then offers the update (Settings → About → Check for updates).

## Rollback

Code only: `npx wrangler rollback` in `backend`. No data changes in this release.

## Risks

- **The new speech-model options (language, voice-activity filter) are untested against the real Workers AI.** The tests use a fake model. The server falls back to the old plain call if the model rejects them, so it can't make things worse than before, but I can't promise the quality gain until you try. Test with the same episodes that came out wrong, press Redo, and tell me what you see.
- **MP3 timestamps are still estimates** (the episode length from the feed spread by bytes), so on a variable-bitrate file or an episode with inserted ads they can drift by seconds. M4A timestamps are exact.
- Transcripts are cut into slices of about a minute; a word cut at a slice edge can be lost or doubled.
- The Android code has still not been compiled by me.
