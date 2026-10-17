# Leestn: standing rules for every update

These come from the owner (Hadi). Follow them for every future change, and finish every update with the report in section 6.

**The owner is a beginner.** Do not assume they know databases, Cloudflare, Android builds or Git. Explain what each command does, and confirm it targets the existing Leestn resources, before telling them to run it.

## Production (already live, never replace)

- Repository: https://github.com/Hytyei/Leestn
- Local project: `D:\RandD\Android Apps\Leestn App\Leestn`
- Worker: `leestn-api`, https://leestn-api.thehdv7.workers.dev
- D1 database: `leestn`, id `02f593c4-e232-466e-839e-c6277a592eb9`
- Queue: `leestn-transcribe`
- Secrets (`INVITE_KEY`, account tokens) are never written into source, commits, logs, reports or generated files.

## 1. Preserve production infrastructure and data

- Inspect the Android app, backend, Wrangler config, migrations, tests and Git history before changing anything. (If the repository can't be reached from the sandbox, say so and work from the delivered copy.)
- Keep the Worker name and URL, D1 database and id, Queue, bindings, variables and secrets unless a change is strictly necessary and justified.
- Never create a replacement production Worker or database to ship a feature.
- Never reset, drop, recreate or clear the production database. Never delete users, synced data, transcripts, ratings or history.
- Do not touch other projects, Workers or databases in the account.
- `wrangler.toml` in the owner's repo holds the real database id. Never ship a whole-project zip that would overwrite it; ship a patch or only the changed files.

## 2. Make each update migration-ready

- Identify the current schema and the exact changes needed.
- Any schema change is a new, sequential, forward-only SQL file in `backend/migrations/`. Never edit an applied migration.
- Safe for existing data, backwards compatible, idempotent where possible, expand-and-contract if anything destructive is unavoidable.
- If no schema change is needed, say "no migration needed". Do not invent one.

## 3. Test locally before release

- Run the existing tests and add tests for new behaviour. Run the TypeScript typecheck, Android checks and anything else relevant.
- Test migrations against a disposable database that holds representative existing data, and test upgrading from the current schema, not only a fresh one.
- Check app/backend API compatibility, authentication, input validation, rate limits, failure handling and resource use.
- Never send test requests to production that create jobs, use AI quota or change data.
- Fix failures first. Disclose every check that could not be run.

## 4. Prepare one controlled production release

Package: all source changes, new migrations (if any), Android source and build config, and a checklist with exact commands in order, covering:

- a backup of production D1 before any migration;
- the migration command, targeting the existing `leestn` database;
- the deploy command, targeting the existing Worker, only if backend code changed;
- Android build steps, only if the app changed;
- post-deploy health checks and smoke tests that create or delete no user data;
- rollback and recovery, including "migration succeeded but deploy failed";
- a short report of changed files, schema changes, compatibility risks and estimated running cost.

Prefer one PowerShell script with explicit confirmation gates that stops on any error, verifies the target Worker and database, never resets or recreates production, and contains no secrets.

## 5. Deployment rules

- Never deploy automatically while developing or testing. Never run a production migration without the owner's explicit approval.
- Do not claim a migration or deployment worked unless command output confirms it.
- Backend-only change: don't rebuild the app unless API compatibility requires it. App-only change: don't deploy the backend.
- Keep app and backend versions compatible during rollout.
- Don't upgrade Wrangler or dependencies for housekeeping during a feature update.

## 6. Final response, every update

1. Implementation status
2. Tests passed and failed
3. Production database changes
4. Whether a migration is required
5. Whether a Worker deployment is required
6. Whether an Android release is required
7. Exact release commands in execution order
8. Backup and rollback instructions
9. Outstanding risks or manual steps

Development and testing happen first. Production changes happen only when the owner approves the prepared release.
