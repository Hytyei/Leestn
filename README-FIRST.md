# Release 1.0.0-pre.2: transcripts for M4A episodes

**What changes for people:** pressing Generate transcript now also works for episodes that are M4A (AAC) files, not just MP3. Ogg/Opus, WAV, FLAC and video files are refused with a message that names the format.

| Question | Answer |
| --- | --- |
| Database migration needed? | **No.** No tables or columns change. |
| Worker deployment needed? | **Yes.** Backend code only. |
| Android release needed? | **No.** The app already shows whatever message the server sends. Don't rebuild or reinstall it for this update. |
| New secrets, bindings, queues, dependencies? | None. `wrangler.toml` and `package.json` dependencies are unchanged. |
| Production resources used | Worker `leestn-api`, D1 database `leestn` (`02f593c4-e232-466e-839e-c6277a592eb9`), queue `leestn-transcribe`. Nothing else. |

## Before you start (once)

1. Open PowerShell in your Leestn folder (`D:\RandD\Android Apps\Leestn App\Leestn`): in File Explorer click the address bar, type `powershell`, press Enter.
2. Make sure you are logged in to Cloudflare in Wrangler. This opens a browser and asks you to allow it (it stores a login on your PC, not in the project):

       cd backend
       npx wrangler login
       cd ..

   If you did this before, it's still fine. `npx wrangler whoami` shows who you are logged in as.

## Step 1: put the new files in your project

The update comes as a Git patch. A patch is a list of exact changes. This checks it fits your project **without changing anything**:

    git apply --check leestn-m4a-support.patch

- Nothing printed means it fits. Then apply it (this edits files on your PC only; nothing goes to Cloudflare or GitHub):

      git apply leestn-m4a-support.patch

- If it prints an error, don't force it. Instead copy these from the `files\` folder in the zip into your project (same places, choose "replace"): `backend\src` (all files), `backend\test` (all files), the `release` folder, and `CLAUDE.md`. Then open `backend\package.json` and change two things: `"version"` to `"1.0.0-pre.2"`, and the `"test"` line under `"scripts"` to `"bun run test/e2e.ts && bun run test/m4a.ts"`. Don't copy `README.md` or `.gitignore` over yours; add the line `backups/` to your `.gitignore`. There is no `wrangler.toml` in the zip on purpose, because yours holds your real database id.

Check the result: `git status` should list changes in `backend/src`, `backend/test`, `backend/package.json`, `README.md`, `.gitignore`, and new files `CLAUDE.md` and `release/`. It must **not** list `backend/wrangler.toml`.

## Step 2: run the release script

    powershell -ExecutionPolicy Bypass -File .\release\release-backend.ps1

(`-ExecutionPolicy Bypass` lets this one script run even though Windows normally blocks scripts. It only applies to this one run.)

The script goes through these steps and **stops at the first problem**. At each GATE it waits until you type `YES`:

1. **Checks it is the real Leestn.** Reads `backend/wrangler.toml` and stops if the Worker isn't `leestn-api`, the database isn't `leestn` with id `02f593c4-e232-466e-839e-c6277a592eb9`, or the queue isn't `leestn-transcribe`.
2. **Runs the tests** on your PC (type check + automated tests with a fake database and fake Whisper). Nothing touches Cloudflare. The automated tests need [Bun](https://bun.sh); without it the script tells you and asks if you want to continue.
3. **Lists waiting migrations** (read-only). For this release it should say "No migrations to apply". If it ever lists something, stop and tell me.
4. **GATE: backup.** Saves a full copy of the live database to `backups\leestn-<date>.sql` on your PC, and writes down D1's current "Time Travel" restore point next to it. Reading data doesn't change it. Keep the `backups` folder private; it is excluded from Git.
5. **Migrations:** skipped, because none are needed.
6. **Dry run, then GATE: deploy.** A dry run builds the code and lists what it would connect to, without deploying. After you type `YES`, it deploys the code to the existing `leestn-api` Worker. Your data, queue and secrets (`INVITE_KEY`) are not touched.
7. **Health check.** Opens `https://leestn-api.thehdv7.workers.dev/health` (read-only, creates nothing) and shows the version. It should say `1.0.0-pre.2`.

To only do the checks and backup, with nothing deployed, add `-NoDeploy` to the command.

## Step 3: save the change in Git (optional but recommended)

    git add -A
    git commit -m "Transcripts for M4A/AAC episodes (backend 1.0.0-pre.2)"
    git push

`git add -A` won't include `backups\` (it's ignored). Check with `git status` before committing that no `.dev.vars` or `.sql` backup is listed.

## After deploying: what to check

- Safe, free checks: open `https://leestn-api.thehdv7.workers.dev/health` in a browser and see `"version":"1.0.0-pre.2"`. In the app, open an MP3 episode you've already transcribed and see its transcript still loads.
- **Real M4A test (optional, uses a little of your Whisper quota):** I couldn't test the live Whisper with M4A audio. In the app pick a **short** M4A episode (under 10 minutes; costs about half a cent and uses about 470 of the 10,000 free daily "neurons") and press Generate transcript. Either it finishes, or the app shows the reason. Tell me what you see.

## If something goes wrong

Nothing here changes the database, so the data can't be damaged by this release. Problems can only be in the Worker code.

- **The new Worker misbehaves (errors on MP3, health check fails):** go back to the previous code. In `backend`:

      npx wrangler deployments list     # shows the versions, newest first
      npx wrangler rollback             # returns to the previous version; it asks you to confirm

  This switches only the code of the existing Worker. Data and secrets are untouched. MP3 transcripts keep working because old-format queue messages are understood by the new code (this is tested). One caveat: if you roll back while an M4A transcript is still being generated, that one transcript will fail. Just press Generate again afterwards.
- **Deploy failed halfway:** the old version keeps running. Run the script again.
- **A migration was applied but deploy failed (not applicable now; for future releases):** the old Worker code keeps working with the new, additive columns. Fix and run the script again; migrations never run twice.
- **Data damaged (very unlikely; only after a migration):** don't improvise. Keep the backup file and the `.timetravel.txt` next to it, and ask me. D1 can restore itself to an earlier point in time (`wrangler d1 time-travel restore`), which keeps the same database id, but it also discards everything written after that point, so decide together.

## Cost

No new services. M4A transcription costs the same as MP3: about $0.0005 per audio minute on Workers AI, within the same daily limits (`MAX_TRANSCRIPTS_PER_DAY`, `MAX_PER_USER_PER_DAY`). The Worker does a little more work to plan an M4A (about 5 requests to the podcast host, a few milliseconds of computing).

## Changed files

- `backend/src/mp4.ts` (new): reads the M4A index and cuts audio between frames.
- `backend/src/transcripts.ts`: detects the real format from the first bytes, plans M4A pieces, and builds them for Whisper. MP3 path unchanged in behaviour.
- `backend/src/types.ts`: queue message type gets two optional fields (old messages stay valid).
- `backend/src/index.ts`, `backend/package.json`: version `1.0.0-pre.2`, test script.
- `backend/test/harness.ts`, `e2e.ts`, `m4a.ts`: tests (37 new checks for M4A, MP3 flow unchanged).
- `release/release-backend.ps1`, `release/README-FIRST.md` (new): this procedure.
- `CLAUDE.md` (new): your standing rules, so they apply to every future update.
- `README.md`, `.gitignore` (`backups/`).

## Compatibility and risks

- **App ↔ Worker:** the API is identical (same routes, same responses). The only difference is that some M4A requests that used to be refused now succeed, and refusals for other formats name the format.
- **Queue messages already waiting** (old format) are still processed correctly by the new code (tested).
- **Live Whisper with ADTS audio is untested.** The M4A pieces are standard AAC streams, which Whisper tooling normally decodes, but I could not confirm it against Workers AI. If it refuses them, the transcript ends as "failed" with a message after three tries; nothing else is affected.
- **HE-AAC** (low-bitrate podcasts) is best effort. **Video podcasts** and non-AAC audio are refused.
