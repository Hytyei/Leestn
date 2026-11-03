# Release 1.7.1: RSS covers fixed, calmer look

**App only.** No server change, no database change, no migration.

## What's fixed

- **Covers for RSS-linked podcasts.** The real cause: Android's feed reader was set to strip the `itunes:` prefix, so `itunes:image` was never recognised and shows added by RSS link got no cover address at all (the image host was never the problem). The same fault dropped the author, category, episode durations, seasons and episode numbers, trailer and bonus labels, full show notes and podcast transcripts from every feed. All of these now come through.
  - Shows you already added get their cover back on the next launch, from the feed already saved on the phone.
  - Episode codes for seasoned shows change from `E1` to `S1E1` and trailers and bonus episodes move to Side. Episode ids are unchanged, so your log, ratings, lists and transcripts stay attached.
  - If a cover from Apple's directory can't be reached on your network, the show's own feed cover is used instead.
- **New episodes on Home** showed the oldest episodes of each show instead of the newest.
- **Seasons count** on a show page counted the Bonus and Trailers groups as seasons.
- The first-run screen's heading was black on black.
- Status bar icons were dark on the dark app when the phone was in light mode.

## What changed in the look

- Home: the Leestn logo is gone; the greeting sits alone at the top. Empty sections no longer leave gaps.
- Graphite neutrals around the brand red, one type scale (no extra-bold, no all-caps mono labels), smaller corners, a 4 dp spacing rhythm.
- Colour blocks only lead Home and Profile; elsewhere numbers sit in quiet cards. Genre filters on Discover are plain chips.
- Glows, pulsing play buttons and bouncy springs are gone. Presses respond faster, lists no longer replay their entrance when you scroll back, and the dock keeps its labels so nothing jumps when you switch tabs.
- Show and episode pages paint the cover colour behind the status bar, with a fade so text never runs into the clock.

## Checks

- New device test `FeedReaderTest` (5 cases, runs on Android's real parser): failed 4 of 5 before the fix, passes 5 of 5 after.
- Upgrade tested on the emulator: v1.7.0 installed, feed added (placeholder cover), signed 1.7.1 installed over it, cover shown.
- Signed with the existing Leestn key (same SHA-256 as 1.7.0), so it installs over the current app.

| Question | Answer |
| --- | --- |
| Database migration needed? | No |
| Worker deployment needed? | No |
| Android release needed? | Yes (1.7.1, versionCode 27) |
