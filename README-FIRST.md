# Release 1.5.9: sign in with Google, link existing accounts, founder badge

**This release changes the app, the server and the database.** Read the "Google setup" part once: it has steps in the Google Cloud website that only you can do.

## What's new

- **Continue with Google** (Settings → Friends and transcripts, when you are not connected). If your Google account is already linked to a Leestn account, it signs you in (no username or invite key needed beyond the server code). If not, type a username first and it creates a new account linked to Google.
- **Connect Google account** (same place, when you are connected): links Google to the account you already have. Nothing about your account changes: same username, ratings, log, followers. **Disconnect Google** removes the link again.
- **Several phones.** Signing in with Google on a new phone gives that phone its own key. Your original key keeps working, so nothing is logged out.
- **Verified founder badge** (gold check) next to the username **Hytyei**, on your profile, on other people's view of your profile, in people lists and in the friends feed. It can only be set on the server; nobody can give it to themselves from the app.

## Database changes (migration 0005, only adds things)

- `users`: new columns `google_sub`, `google_email`, `role` (empty for everybody).
- new table `user_tokens` for the extra phone keys.
- the one data change: `role = 'founder'` for the account whose username is `hytyei` (any capitalisation).

No user, rating, log or transcript is deleted or rewritten. The migration runs once and is recorded; the script backs up the database first.

| Question | Answer |
| --- | --- |
| Database migration needed? | **Yes**, `0005_google_and_founder.sql` (the script lists and applies it after a backup and your `YES`). |
| Worker deployment needed? | **Yes.** |
| Android release needed? | **Yes** (app 1.5.9, versionCode 24; adds Google sign-in libraries). |
| New secret? | `GOOGLE_CLIENT_ID` (the public client id; see below). Without it the app shows "Google sign-in isn't set up on this server yet" and everything else works as before. |

## Google setup (one time, about 15 minutes)

Google only lets an app use "Sign in with Google" if you register it. This costs nothing.

1. Open https://console.cloud.google.com and sign in with the Google account you want to own this. At the top, click the project picker → **New project** → name it `Leestn` → Create. Make sure the new project is selected.
2. Menu → **APIs & Services → OAuth consent screen** (or "Google Auth Platform"). Choose **External**, app name `Leestn`, your email as support and developer email. Save. Under **Audience / Test users**, add the Google emails of everyone who will sign in (up to 100). While the app is in "Testing", only listed people can use it, which is fine for a friends server. (You can press "Publish app" later; basic sign-in with email and name needs no Google review.)
3. **APIs & Services → Credentials → Create credentials → OAuth client ID → Application type: Web application**, name `Leestn server`. No URLs are needed. Create, then **copy the Client ID** (it ends in `.apps.googleusercontent.com`).
4. Create a second one: **Create credentials → OAuth client ID → Application type: Android**. Package name: `app.leestn`. SHA-1: the fingerprint of the key that signs your release app. To read it, in PowerShell run (the file and alias are the ones in your `keystore.properties`; it asks for the keystore password, which stays on your computer):

       keytool -list -v -keystore "PATH\TO\your-keystore.jks" -alias YOUR_ALIAS

   Copy the line that starts with `SHA1:`. If you also want to try debug builds from Android Studio, create one more Android client with the debug key's SHA-1 (`keytool -list -v -keystore "$env:USERPROFILE\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android`).
   Both clients must be in the **same project**. The Android client is not copied anywhere; Google matches it by package name and fingerprint.
5. Give the server the Web client id (after the release steps below):

       cd backend
       npx wrangler secret put GOOGLE_CLIENT_ID

   It asks for the value: paste the **Web** client id from step 3. This targets the existing Worker named in `backend\wrangler.toml` (`leestn-api`), and `wrangler.toml` itself is not changed. (The id is not really secret; a secret is just the way to set it without editing that file.)

Phones need Google Play Services (normal Android phones have it).

## Release steps

1. Pick the patch for what you have applied: `leestn-v1.5.9-from-1.5.8.patch`, `-from-1.5.7.patch`, `-from-1.5.6.patch`, `-from-1.5.5.patch` or `-from-1.0.patch`.

       git apply --check <patch name>
       git apply <patch name>

   `git status` must not list `backend/wrangler.toml`.
2. Backend (backup, migration, deploy, health check, founder check, all with `YES` gates):

       powershell -ExecutionPolicy Bypass -File .\release\release-backend.ps1

3. Google secret (step 5 above): `cd backend`, `npx wrangler secret put GOOGLE_CLIENT_ID`.
4. Check it is on: open https://leestn-api.thehdv7.workers.dev/v1/auth/config in a browser. It should show `"google":true` and your client id.
5. App:

       git add -A
       git commit -m "1.5.9: Google sign-in, account linking, founder badge"
       git push
       git tag v1.5.9
       git push origin v1.5.9

   When the GitHub build finishes, open Settings → About → Check for updates on the phone.
6. On your phone: Settings → Friends and transcripts → **Connect Google account** → pick your Google account. Your profile should show the gold "Verified founder" tag.

## If the founder badge is missing

The script's last step lists accounts that have the badge. If `hytyei` is not there (for example the account has a different username on the server), tell me the exact username. To add it yourself later (changes one row, nothing else):

    cd backend
    npx wrangler d1 execute leestn --remote --command "UPDATE users SET role = 'founder' WHERE lower(handle) = 'hytyei'"

## Backup and rollback

- The script saves a full backup to `backups\` and a Time Travel restore point first.
- Worker code only: `npx wrangler rollback` in `backend`. The new database columns and table are harmless to the older code (it ignores them), so you do not have to undo the migration.
- App: install the previous APK over it.
- "Migration done but deploy failed": nothing breaks; run the script again, it skips the finished migration and deploys.
- The new code also still works if the migration is somehow not applied yet (sign-in with the old keys keeps working; only the new Google and badge features need it).

## Risks

- **The Android code has never been compiled by me**, and this release adds new libraries (Google sign-in). A build error is possible: paste it and I'll fix it.
- **Google Sign-in can't be tested here.** The server side is tested with a simulated Google (signature, audience, expiry, linking rules, several phones). The phone side is not, and Google's setup is the usual source of trouble: if the account picker says sign-in failed, the package name or SHA-1 in step 4 is the first thing to check. Tell me the exact message.
- Anyone who holds a Google account linked to an account can sign in to it. Unlinking is under Settings. Deleting your account also removes its link.
- A server-side token or Google email is never shown to other users.
- Only people on the Google "Test users" list can sign in until you publish the app in the Google console.
- The badge is set by username when the migration runs. Nobody can claim "hytyei" for the badge later, because the badge is not given at sign-up.
