# Release 1.7.2: widgets show covers again

**App only.** No server change, no database change, no migration.

## What's fixed

- **Widgets kept placeholder covers (and old play state) after updating the app.** Each widget read its data and downloaded its covers once, when the system first drew it. While that widget was still open on the home screen, Android answers a new update by redrawing with what it already has, so the fresh data the app published a moment later never reached the screen. After an upgrade from 1.7.0 this kept the empty covers until something like play or pause happened to come later. Widgets now reload whenever the app writes new data, including while they're open.

## Checks

- Reproduced on the emulator: 1.7.0 installed, show added, Now Playing widget placed (placeholder), upgraded to 1.7.1, app opened: widget kept the placeholder and "Playing · 0:00".
- Same steps with 1.7.2: the widget shows the cover, the paused state and the right time left. The Continue listening widget renders with its cover, and a play started in the app reaches the open widget straight away.
- Signed with the existing Leestn key (same SHA-256 as 1.7.0 and 1.7.1).

| Question | Answer |
| --- | --- |
| Database migration needed? | No |
| Worker deployment needed? | No |
| Android release needed? | Yes (1.7.2, versionCode 28) |
