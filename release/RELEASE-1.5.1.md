# Release 1.5.1: fewer failed transcripts, add any show by its feed

**What changes for people**

- **Transcripts that failed for one bad part now finish.** If the speech model rejects one slice of an episode (after 3 tries), that slice is skipped and the rest is transcribed, with a note "N short parts couldn't be transcribed". The episode only fails if more than a quarter of it can't be read, and then the message says why.
- **Clear reasons when a podcast host blocks us.** The error now names the host and the HTTP status (for example "cdn.example.com refused the server's request for the audio (HTTP 403)" or "doesn't allow reading the audio in parts").
- **Your own podcast:** Leestn searches Apple's podcast directory, and a Spotify link is also looked up there, so a show that isn't listed at Apple doesn't appear. You can now paste **any link**: the RSS feed, or a web page that advertises one (Leestn finds the feed on the page). "Nothing found" in Discover now offers "Add by RSS or link".

| Question | Answer |
| --- | --- |
| Database migration needed? | **No.** No tables or columns change. Do not run any migration step. |
| Worker deployment needed? | **Yes** (transcript code only). |
| Android release needed? | **Yes** (add-by-link and the Discover hint). The transcript fixes work without it. |
| New secrets, bindings, dependencies? | None. `wrangler.toml` untouched. |

## Steps

If you have **not yet applied** `leestn-v1.5.0.patch`, apply `leestn-v1.5.1-from-1.0.patch` instead (it contains everything). If you already applied 1.5.0, use `leestn-v1.5.1.patch`. Check first, then apply (edits files on your PC only):

    git apply --check <patch name>
    git apply <patch name>

`git status` must not list `backend/wrangler.toml`. Then, as before:

    powershell -ExecutionPolicy Bypass -File .\release\release-backend.ps1

(Step 3 of the script lists waiting migrations. If you did not deploy 1.5.0 before, it will show `0003_usage_meter.sql` and apply it after a backup and your `YES`; otherwise "No migrations to apply".) It backs up, asks `YES` before deploying, and checks `/health` shows `1.5.1`.

App: `git add -A`, commit, push, then `git tag v1.5.1` and `git push origin v1.5.1`. The app on your phone will offer the update (Settings → About → Check for updates) once you have done the one-time signing setup from `RELEASE-1.5.0.md`.

## Rollback

Code only: `npx wrangler rollback` in `backend`. No data changes in this release.

## Risks

- **I could not reproduce your Easy German failure.** No tool in my workspace can download from podcast hosts or call Whisper. Its feed is served through a proxy host (`proxyfeed.svmaudio.com`) and tracking redirects, so my best guesses are a host that blocks automated downloads or ignores range requests. This release makes those cases, and single bad slices, either work or explain themselves. **After updating, try Easy German again and send me the exact message it shows.**
- **Hosts that don't support range requests still can't be transcribed** (the server reads audio in parts). Supporting them needs temporary file storage (R2), a Cloudflare change I haven't made without your approval.
- Feed discovery works on pages that declare their feed in the HTML. Castbox or Spotify pages may not; the RSS link is the reliable route.
- The Android code has still not been compiled by me.
