# Release 1.5.8 (app only): richer look, seamless header, shorter series graph

**App only.** No server change: no migration, no Worker deployment, nothing to back up. Don't run `release-backend.ps1`.

## What's new

- **Seamless Home header.** The black bar is gone. Home now paints right up behind the Android status bar, and the coloured glow starts at the very top of the screen. (Cause: the status-bar space was being added by the page and again by the app frame. Each screen now adds it once, and Home lets its glow run under it.)
- **Series graph:** blocks are now at most **10 episodes** long (it was 25). Longer seasons are split into side-by-side blocks (1-10, 11-20, ...) and the graph scrolls sideways.
- **Trakt-style Home hero:** the episode you were last in is a big banner with the cover full-bleed, a fade in the cover's colour, a "CONTINUE LISTENING" tag, a glowing progress bar and a big pulsing play button.
- **Spotify-style quick picks:** a 2-column grid of your shows (cover + name, colour-tinted, equalizer on the show that is playing).
- **Last.fm-style "Your top shows":** big rank numbers, covers and a glowing bar for how many episodes you finished. The same ranked look is used in **Profile → Top shows**.
- **Floating mini player:** a rounded card with margins above the bottom bar, instead of a full-width strip.
- **Show page:** a colour wash from the cover at the top that fades as you scroll, and a glow behind the cover.

| Question | Answer |
| --- | --- |
| Database migration needed? | **No.** |
| Worker deployment needed? | **No.** |
| Android release needed? | **Yes** (app 1.5.8, versionCode 23). |

## Steps

Pick the patch matching what you have applied: `leestn-v1.5.8-from-1.5.7.patch`, `-from-1.5.6.patch`, `-from-1.5.5.patch` or `-from-1.0.patch`.

    git apply --check <patch name>
    git apply <patch name>

`git status` must not list `backend/wrangler.toml`. Then:

    git add -A
    git commit -m "1.5.8: richer UI, seamless header, 10-episode series graph"
    git push
    git tag v1.5.8
    git push origin v1.5.8

When the GitHub build finishes, open Settings → About → Check for updates on the phone.

## Rollback

Install the previous APK over it (same signing key). No data is affected.

## Risks

- **The Android code has never been compiled by me.** This release changes the app frame (status bar space) and adds new Home and Profile code, so a build error or a spacing glitch on one screen is possible. Paste the error or send a screenshot and I'll fix it.
- The status-bar handling is the riskiest change: if any screen's top touches the status bar, tell me which one.
- The ranked list counts finished episodes (those with a listened date), not hours.
