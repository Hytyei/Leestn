# Leestn

A podcast tracker and player. Trakt-style dark UI, real podcasts, offline listening, on-demand transcripts, ratings in the style of Series Graph, friends, badges, widgets.

**Version 1.0.0-pre.1, a pre-release for testing the app and the backend.**

    app/       Android app (Kotlin, Jetpack Compose, Media3)
    backend/   Cloudflare Worker (D1 database, Queues, Workers AI Whisper)

There is no central Leestn server. Whoever wants friends and transcripts hosts a small Worker on their own free Cloudflare account and shares a code with friends. Without a server the app still works: you track and play podcasts on your own device.

## What's in it

- **Real podcasts:** search and charts through Apple's directory, any RSS link, Apple Podcasts links, Spotify show links (matched to the open RSS feed) and OPML import.
- **Favourite genres** at first launch drive "Picked for you" and "Because you like X".
- **Player:** background playback, lock-screen controls, speed, ±15/30 s, sleep timer. The mini player is on every screen.
- **Offline:** download episodes. Optional: auto-download the next N episodes, Wi-Fi only, delete after listening.
- **Seasons and extras:** full episodes are the main story, bonus and trailer episodes are side content. The end of each episode suggests what to hear next.
- **Ratings like Series Graph:** rate every episode 1-10, rate a whole show, and see a coloured grid of episodes by season with season averages. Each show shows your average and, on a server, everyone's average.
- **Transcripts:** shown automatically when the publisher provides one. Otherwise press **Generate transcript** on the episode. Nothing is transcribed unless someone presses it. Generated transcripts are shared on the server, so an episode is only processed once.
- **Profile:** photo, name, bio, stats, year recap, 19 tiered badges.
- **Friends:** follow people on your server, see what they listen to and rate, open their profiles.
- **Widgets:** Now playing, Mini player, Continue, New episodes, Stats.

## Install the app

Download the APK from the latest [GitHub release](../../releases) (or build it: open this folder in Android Studio and run, or `./gradlew assembleDebug`). Needs Android 8.0+.

## Run your own server (with Whisper transcripts)

You need a free Cloudflare account and Node 20+. About ten minutes.

    cd backend
    npm install
    npx wrangler login

1. Create the database, paste the id it prints into `wrangler.toml` as `database_id`:

       npx wrangler d1 create leestn

2. Create the queue that feeds Whisper:

       npx wrangler queues create leestn-transcribe

3. Create the tables:

       npm run db:migrate:remote

4. Choose an invite key. Anyone who has it can join your server, so make it long and random:

       npx wrangler secret put INVITE_KEY

5. Deploy. Workers AI needs no extra setup, the `[ai]` binding in `wrangler.toml` is enough:

       npm run deploy

   Wrangler prints your address, like `https://leestn-api.yourname.workers.dev`. Check it by opening it in a browser: you should see `{"ok":true,...}`.

6. In the app: **Settings > Friends and transcripts > Host my own**. Enter the address, the invite key, and a username. You are now connected.

### Share it with friends

On your phone open **Friends > People** (or Settings). There is an **Invite friends** card with a code starting with `LSTN-`. Tap **Share** and send it. Your friend installs the app, opens **Settings > Friends and transcripts > Join with a code**, pastes it, and picks a username. They are now on your server: they can follow you, and they can generate transcripts there.

The code contains your server address and invite key, so only send it to people you want on the server. To lock the door again, change the key with `npx wrangler secret put INVITE_KEY` (existing members keep working; the old code stops admitting new people). Everyone can do the same on their own account with their own friends.

### What it costs

Whisper large-v3-turbo on Workers AI costs about $0.0005 per audio minute. The free allowance is 10,000 neurons a day, roughly 200 minutes of audio, resetting at 00:00 UTC. Past that you pay per use, or Cloudflare refuses. Two caps in `wrangler.toml` keep it in check: `MAX_TRANSCRIPTS_PER_DAY` (new jobs across the server, default 40) and `MAX_PER_USER_PER_DAY` (per person, default 8). Queues' free plan allows 10,000 operations a day, which is not the limit you will hit first.

### Limits

- **MP3** is cut into 1 MB slices by byte position. Timestamps come from the slice position and the episode length, so they can drift a few seconds.
- **M4A/MP4 with AAC audio** is read properly: the Worker finds the file's index, cuts the audio between frames, and sends each piece as plain AAC. Timestamps are exact. HE-AAC is best effort.
- **Raw .aac** files work like MP3. **Ogg/Opus, WAV, FLAC, video files and non-AAC audio** are refused with a message that names the format.
- The file's real format is detected from its first bytes, so a wrong file extension or content type doesn't matter.
- The host must support HTTP range requests (almost all do).

### API

| Route | Auth | Purpose |
| --- | --- | --- |
| `GET /v1/transcripts/:epId` | invite key or token | status, progress, lines |
| `POST /v1/transcripts` | invite key or token | start a transcript: `{epId, audioUrl, durationSec}` |
| `POST /v1/users` | invite key | create an account, returns a one-time token |
| `GET/PATCH/DELETE /v1/me` | token | own account |
| `PUT/DELETE /v1/me/avatar` | token | profile photo (JPEG, up to 120 KB) |
| `PUT/GET /v1/sync` | token | push or pull log, show ratings, badges |
| `GET /v1/users?q=` | token | find people |
| `POST/DELETE /v1/follow/:handle` | token | follow or unfollow |
| `GET /v1/feed` | token | what people you follow listened to |
| `GET /v1/users/:handle` | token | profile, stats, badges, recent listens |
| `GET /v1/users/:handle/wrapped/:year` | token | year recap numbers |
| `GET /v1/shows/:showId/ratings` | token | everyone's ratings for a show |

Local development: `cp .dev.vars.example .dev.vars`, `npm run db:migrate:local`, `npm run dev`. `npm test` runs an end-to-end test of the Worker against an in-memory SQLite database with a fake Whisper (needs [Bun](https://bun.sh)).

## GitHub

- `android.yml` builds a debug APK on every push and pull request.
- `backend.yml` typechecks and tests the Worker. To also deploy on every push to `main`: add the repository secrets `CLOUDFLARE_API_TOKEN` (template "Edit Cloudflare Workers", plus D1 edit) and `CLOUDFLARE_ACCOUNT_ID`, do the one-time setup above by hand, then set the repository variable `DEPLOY_BACKEND` to `true`.
- `release.yml`: push a tag such as `v1.0.0-pre.1` and it publishes a GitHub pre-release with the APK attached.

      git tag v1.0.0-pre.1 && git push origin v1.0.0-pre.1

## Status of this pre-release

The Worker is tested end to end (including M4A files built for the test) with a fake Whisper and an in-memory database (accounts, sync, follow, feed, ratings, avatars, chunked transcripts, limits). **The Android app has not been compiled or run by its author's tooling**, and the live Whisper model, Apple's directory and Spotify's lookup have not been called from it. Expect build errors and rough edges, and please report them.

Known gaps: the APK is a debug build (not signed for the Play Store); the system font is used instead of Bricolage Grotesque, DM Sans and IBM Plex Mono; friends are found by username search only.
