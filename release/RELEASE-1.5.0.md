# Release 1.5.0

**What changes for people**

- A meter shows how much of today's free Whisper (speech to text) time is left, and Generate transcript warns, and the server refuses, when an episode would go over.
- New Leestn logo in the app and as the Android app icon (including the themed icon on Android 13+).
- In-app updates: Settings → About → Check for updates, and a banner on Home, from the releases of github.com/Hytyei/Leestn.

| Question | Answer |
| --- | --- |
| Database migration needed? | **Yes, one, additive:** `0003_usage_meter.sql` adds one column `audio_sec` (default 0) to `transcripts`. Nothing is removed, rewritten or reset. Old rows keep working. |
| Worker deployment needed? | **Yes.** New route `GET /v1/usage` and the limit check. |
| Android release needed? | **Yes.** Meter, logos, updater. Friends need the new APK once, by hand (see "First install"). |
| New secrets, bindings, queues, dependencies? | None on Cloudflare. GitHub needs four signing secrets (below). `WHISPER_DAILY_MINUTES` is optional; the default is built in, so `wrangler.toml` doesn't change. |
| Production resources used | Worker `leestn-api`, D1 `leestn` (`02f593c4-e232-466e-839e-c6277a592eb9`), queue `leestn-transcribe`. Nothing else. |

Order matters: **backend first, then the app.** The new app copes with an old server (the meter simply stays hidden), but do the backend first anyway.

## Part A: backend (Worker plus migration)

Open PowerShell in your Leestn folder (`D:\RandD\Android Apps\Leestn App\Leestn`) and make sure you're logged in (`cd backend`, `npx wrangler whoami`, `cd ..`; use `npx wrangler login` if it says not logged in).

**1. Put the new files in your project.** The update is a Git patch (a list of exact changes). This only *checks* that it fits and changes nothing:

    git apply --check leestn-v1.5.0.patch

Nothing printed = it fits. Then apply it (edits files on your PC only, nothing goes online):

    git apply leestn-v1.5.0.patch

If there's an error, don't force it: copy the folders from `files\` in the zip over your project (replace when asked). The zip has no `wrangler.toml` on purpose; yours holds your real database id. `git status` must **not** list `backend/wrangler.toml`.

**2. Run the release script** (it's the same one as before; it stops at the first problem and waits for you to type `YES` at each gate):

    powershell -ExecutionPolicy Bypass -File .\release\release-backend.ps1

What it does, in order: checks `wrangler.toml` points at the real Worker, database and queue; runs the tests on your PC (nothing online); **lists waiting migrations (read-only), and this time it should show `0003_usage_meter.sql`**; GATE: saves a full backup of the live database to `backups\` (and notes the Time Travel restore point); GATE: applies `0003` to the existing `leestn` database; dry run of the Worker; GATE: deploys the existing `leestn-api`; checks `/health` says `1.5.0`.

Add `-NoDeploy` to do only the checks and backup.

**3. Check (safe, creates nothing):** open `https://leestn-api.thehdv7.workers.dev/health` in a browser. It must say `"version":"1.5.0"`.

## Part B: signing key (one time, needed for updates)

Android only installs an update over an existing app if both are signed with **the same key**. So we make one key and use it forever. **If you lose it, nobody can update; keep a copy somewhere safe, and never commit it.**

1. Create the key. This makes a file `leestn-release.jks` in the current folder (keytool comes with Android Studio; this path is the usual one, adjust if yours differs). It asks you for a password and some name fields; the name fields can be anything. Remember the password.

       & "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -keystore leestn-release.jks -alias leestn -keyalg RSA -keysize 2048 -validity 10000

   Move `leestn-release.jks` somewhere safe outside the project folder, and keep a backup copy.
2. Turn it into text for GitHub (this copies it to the clipboard; nothing is uploaded):

       [Convert]::ToBase64String([IO.File]::ReadAllBytes("leestn-release.jks")) | Set-Clipboard

3. On GitHub: repository **Hytyei/Leestn** → Settings → Secrets and variables → Actions → New repository secret. Add four secrets:
   - `LEESTN_KEYSTORE_BASE64`: paste from the clipboard
   - `LEESTN_KEYSTORE_PASSWORD`: the password you chose
   - `LEESTN_KEY_ALIAS`: `leestn`
   - `LEESTN_KEY_PASSWORD`: the same password (keytool uses one password for both when you press Enter at that question)
4. The repository must be **public** (Settings → General → bottom: Change visibility), otherwise your friends' apps can't read the releases. Don't put secrets in the code; the project's own secrets live only in Cloudflare and GitHub Secrets, so this is safe.

If you also build in Android Studio and want those builds to update cleanly too, create a file `keystore.properties` in the project root (it's git-ignored) with:

    storeFile=C:\\path\\to\\leestn-release.jks
    storePassword=YOUR_PASSWORD
    keyAlias=leestn
    keyPassword=YOUR_PASSWORD

## Part C: publish the app

1. Save the change in Git:

       git add -A
       git status      # check: no .jks file, no backups\ folder, no keystore.properties, no wrangler.toml change
       git commit -m "v1.5.0: Whisper meter, new logo, in-app updates"
       git push

2. Make the release. This starts a build on GitHub that makes the signed APK and publishes it as release `v1.5.0`:

       git tag v1.5.0
       git push origin v1.5.0

   Watch it on GitHub → Actions → Release. If it fails with "Signing secrets are missing", redo Part B step 3.

### First install (one time)

Your phones currently have a build signed with a different key, and the old version has no updater. So **once**: uninstall Leestn, install `leestn-v1.5.0.apk` from the release page. (Uninstalling removes local data, but anything synced to your server comes back when you reconnect.) From then on, every new release arrives through Settings → About → Check for updates, and the Home banner.

Future releases: bump `versionCode` and `versionName` in `app/build.gradle.kts`, commit, push a new tag such as `v1.5.1`. A tag with a dash (`v1.6.0-pre.1`) becomes a pre-release; the app offers the highest version either way.

## If something goes wrong

- **Migration applied, deploy failed:** safe. The old Worker ignores the new column. Run the script again; applied migrations never run twice.
- **New Worker misbehaves:** in `backend`, `npx wrangler deployments list`, then `npx wrangler rollback` (code only; data and secrets untouched). The `audio_sec` column can stay; the old code doesn't mind it.
- **Data damaged (very unlikely):** don't improvise. Keep `backups\leestn-*.sql` and the `.timetravel.txt` beside it and ask me. D1 Time Travel can restore to a point before the migration, but it also discards everything written after that point.
- **App update says "signed with a different key":** that APK wasn't signed with your key. Install the release APK by hand once after uninstalling.
- **Bad app release:** delete the release on GitHub (or publish a fixed higher version); the app offers the highest version.

## Cost

Nothing new. The meter reads a sum from your own database (a handful of D1 reads per check). Whisper stays about $0.0005 per audio minute, free up to about 200 minutes per day on Workers AI (10,000 free neurons a day, resetting 00:00 UTC). GitHub Actions and releases are free for public repositories.

## Changed files

- `backend/migrations/0003_usage_meter.sql` (new): the one additive column.
- `backend/src/transcripts.ts`, `index.ts`, `types.ts`: `GET /v1/usage`, the free-time guard, version `1.5.0`. `backend/package.json`: version, test script. `backend/test/usage.ts` (new): tests including upgrade from the current schema.
- App: `ui/UsageMeter.kt`, `ui/UpdateUi.kt`, `ui/Logo.kt`, `data/Updater.kt`, `data/Version.kt` (new); `AppViewModel`, `Server`, Settings, Episode, Home, Onboarding, `MainActivity`; manifest (install permission, FileProvider), `res/xml/file_paths.xml`; logos and launcher icons in `res/`; `build.gradle.kts` (version 1.5.0 / code 15, release signing).
- `.github/workflows/release.yml` (signed build), `.gitignore`, `README.md`, `branding/`, `release/`.

## Risks you should know about

- **The Android app was not compiled or run here** (no Android SDK in my workspace). Expect a possible build error; paste it to me and I'll fix it.
- The meter is an **estimate**: it counts audio sent through your server, and can't see other Workers AI use in the same Cloudflare account. The real limit is Cloudflare's; when an episode's length is unknown, the size at 128 kbps is used.
- **Live Whisper with M4A is still untested** (from the previous release).
- **The PowerShell script and the GitHub workflow have not been run** by me; the script stops safely at any problem.
- The updater needs a **public** repo and Android shows its own "Install" confirmation; an app can't install silently.
- The logo mapping is my reading of your two pictures: wide wordmark = in-app, square U-mark = app icon. Tell me if that is swapped.
