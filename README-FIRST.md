# Release 1.5.7 (app only): livelier look, colour from artwork, new widgets

**App only.** The server is not touched: no migration, no Worker deployment, nothing to back up. Don't run `release-backend.ps1`.

## What's new in the app

- **Colour from the cover.** The player, mini player, episode page and Home pick the main colour of the show's artwork and use it for glows, gradients and progress bars. It fades smoothly when the episode changes.
- **Player.** Gradient backdrop in the cover's colour; the cover gently swells while playing and settles when paused, with a soft glow; live equalizer bars next to the show name; a breathing halo around the play button.
- **Mini player.** Gradient in the cover's colour, glowing progress line, dancing equalizer bars, springy cover.
- **Home.** Greeting that follows the time of day, an ambient glow at the top in the colour of what you last listened to (fades as you scroll), richer "Continue listening" cards (gradient, glow, gradient progress bar, Pause/Resume with equalizer), equalizer badge on the episode that is playing.
- **Episode page.** Colour wash at the top that fades as you scroll, cover that swells while playing, and the line being spoken now gets a soft colour sweep and a gentle pop.
- **Everywhere.** Section headers get a colour accent; the bottom bar icons bounce when selected.
- **Widgets, all five restyled** (Android widgets can't animate, so this is about design): Now Playing with a cover-coloured card, big rounded cover, pill progress bar and glass buttons; Mini with a full-bleed cover and colour fade; Continue with rounded posters, progress bars and "% left"; New episodes as rounded cards with a NEW dot; Stats with a 7-segment "days listened this week" ring, big hours number and rounded gradient week bars.

After installing, remove and re-add widgets only if one looks stretched; they redraw on the next update.

| Question | Answer |
| --- | --- |
| Database migration needed? | **No.** |
| Worker deployment needed? | **No.** |
| Android release needed? | **Yes** (app 1.5.7, versionCode 22). |

## Steps

Pick the patch matching what you have applied:

- `leestn-v1.5.7-from-1.5.6.patch`: you applied the 1.5.6 package.
- `leestn-v1.5.7-from-1.5.5.patch`: you stopped at 1.5.5 (includes 1.5.6).
- `leestn-v1.5.7-from-1.0.patch`: no 1.5.x patches applied.

Check, then apply (changes files on your PC only):

    git apply --check <patch name>
    git apply <patch name>

`git status` must not list `backend/wrangler.toml`. Then save and publish the app:

    git add -A
    git commit -m "1.5.7: livelier UI, artwork colours, new widgets"
    git push
    git tag v1.5.7
    git push origin v1.5.7

When the GitHub build finishes, open Settings → About → Check for updates on the phone.

## Rollback

Install the previous APK (a GitHub release, e.g. v1.5.6 or v1.5.5) over it, signed with the same key. No data is affected; highlights, pasted text and timing fixes live in app storage and survive.

## Risks

- **None of the Android code has been compiled by me**, and this release adds a lot of new UI and widget drawing code. A build error is possible: paste it and I'll fix it right away.
- Artwork colour is read from the cover once per image; very grey covers fall back to a red-ish tint.
- Widgets paint their backgrounds as pictures at the widget's size at that moment; after resizing, a widget may look stretched until its next refresh.
- Extra animations use a little more battery while the player is open; they stop when playback is paused (except the soft halo).
