<#
Leestn backend release script.

What it does, in order. It stops at the first error, and at every "GATE" it waits for you to type YES.
  1. Checks that this folder points at the real Leestn Worker, database and queue (and nothing else).
  2. Runs the checks that don't touch production: TypeScript check and the automated tests.
  3. Looks, read-only, at which database migrations are waiting.
  4. Backs up the production database to a file on your computer.
  5. Applies waiting migrations (only if there are any, and only after you type YES).
  6. Deploys the Worker code (only after you type YES).
  7. Checks the live Worker answers and shows its version.

It never creates, resets, drops or replaces the Worker, database or queue, and it contains no passwords or keys.
You must already be logged in to Cloudflare in Wrangler (run "npx wrangler login" once, in the backend folder).

Run it from the repository folder:
    powershell -ExecutionPolicy Bypass -File .\release\release-backend.ps1
Add -NoDeploy to stop after the checks and backup (nothing in production changes except a backup file being written to your PC).
#>
param([switch]$NoDeploy)

$ErrorActionPreference = 'Stop'

# ---- The live Leestn resources. If the repository points anywhere else, the script refuses to continue. ----
$ExpectedWorker = 'leestn-api'
$ExpectedDbName = 'leestn'
$ExpectedDbId   = '02f593c4-e232-466e-839e-c6277a592eb9'
$ExpectedQueue  = 'leestn-transcribe'
$HealthUrl      = 'https://leestn-api.thehdv7.workers.dev/health'

function Say([string]$t)  { Write-Host ""; Write-Host "== $t" -ForegroundColor Cyan }
function Info([string]$t) { Write-Host "   $t" }
function Warn([string]$t) { Write-Host "   ! $t" -ForegroundColor Yellow }
function Gate([string]$question) {
    Write-Host ""
    Write-Host "GATE: $question" -ForegroundColor Yellow
    $a = Read-Host "Type YES (capital letters) to continue, anything else stops here"
    if ($a -cne 'YES') { throw 'Stopped by you. Nothing further was changed.' }
}
# Runs a program and stops if it reports a problem. Wrangler prints its normal output straight to this window.
function Run([string]$what, [scriptblock]$cmd) {
    & $cmd
    if ($LASTEXITCODE -ne 0) { throw "$what failed (exit code $LASTEXITCODE). Nothing after this step was run." }
}

# Runs a program and returns everything it printed. Windows PowerShell 5.1 treats harmless warning text as an error,
# so errors are only judged by the program's exit code (checked by the caller through $LASTEXITCODE).
function Capture([scriptblock]$cmd) {
    $old = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { return (& $cmd 2>&1 | Out-String) } finally { $ErrorActionPreference = $old }
}

$Repo    = Split-Path -Parent $PSScriptRoot
$Backend = Join-Path $Repo 'backend'
$Toml    = Join-Path $Backend 'wrangler.toml'
$Backups = Join-Path $Repo 'backups'

# ---------------------------------------------------------------------------------------------------
Say '1. Checking that this folder points at the real Leestn resources'
if (-not (Test-Path $Toml)) { throw "Can't find $Toml. Run this script from inside the Leestn repository." }
$t = Get-Content $Toml -Raw
function Pick([string]$pattern) { $m = [regex]::Match($t, $pattern, 'Multiline'); if ($m.Success) { $m.Groups[1].Value } else { '' } }
$worker = Pick '^\s*name\s*=\s*"([^"]+)"'
$dbName = Pick '^\s*database_name\s*=\s*"([^"]+)"'
$dbId   = Pick '^\s*database_id\s*=\s*"([^"]+)"'
$queues = @([regex]::Matches($t, '^\s*queue\s*=\s*"([^"]+)"', 'Multiline') | ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique)
if ($worker -ne $ExpectedWorker) { throw "wrangler.toml says the Worker is '$worker', expected '$ExpectedWorker'. Stopping." }
if ($dbName -ne $ExpectedDbName) { throw "wrangler.toml says the database is '$dbName', expected '$ExpectedDbName'. Stopping." }
if ($dbId   -ne $ExpectedDbId)   { throw "wrangler.toml has database id '$dbId', expected '$ExpectedDbId'. Stopping." }
if (@($queues).Count -ne 1 -or $queues[0] -ne $ExpectedQueue) { throw "wrangler.toml queue is '$($queues -join ',')', expected only '$ExpectedQueue'. Stopping." }
Info "Worker   : $worker"
Info "Database : $dbName ($dbId)"
Info "Queue    : $($queues[0])"
Info 'All match the live Leestn resources.'

if (Get-Command git -ErrorAction SilentlyContinue) {
    Push-Location $Repo
    $dirty = git status --short 2>$null
    Pop-Location
    if ($dirty) { Info 'Files changed since your last commit (this is expected right before a release):'; $dirty | ForEach-Object { Info "  $_" } }
}

# ---------------------------------------------------------------------------------------------------
Say '2. Running checks (nothing in production is touched)'
Push-Location $Backend
try {
    if (-not (Test-Path 'node_modules')) { Info 'First run: installing the packages listed in package.json.'; Run 'npm install' { npm install --no-audit --no-fund } }
    Run 'TypeScript check' { npm run typecheck }
    if (Get-Command bun -ErrorAction SilentlyContinue) {
        Run 'Automated tests' { npm test }
    } else {
        Warn "Bun isn't installed, so the automated tests were SKIPPED. (They use a fake database and a fake Whisper; they never contact Cloudflare.)"
        Gate 'Continue without running the automated tests?'
    }

    # -----------------------------------------------------------------------------------------------
    Say '3. Which database migrations are waiting? (read-only)'
    $pending = Capture { npx wrangler d1 migrations list $ExpectedDbName --remote }
    if ($LASTEXITCODE -ne 0) { throw "Couldn't read the migration list:`n$pending" }
    Write-Host $pending
    $hasPending = $pending -notmatch 'No migrations to apply'
    if ($hasPending) { Warn 'There ARE migrations waiting. They will be applied to the live database in step 5, after a backup.' }
    else { Info 'No migrations waiting. This release does not change the database.' }

    # -----------------------------------------------------------------------------------------------
    Say '4. Backup of the production database'
    Info "Saves a copy of all data to a file on this computer, in: $Backups"
    Info 'That folder holds your users'' data. It is excluded from Git. Keep it private.'
    Gate 'Create the backup now? (read-only for production)'
    New-Item -ItemType Directory -Force -Path $Backups | Out-Null
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $file  = Join-Path $Backups "leestn-$stamp.sql"
    Run 'Database backup' { npx wrangler d1 export $ExpectedDbName --remote --output $file }
    if (-not (Test-Path $file) -or (Get-Item $file).Length -lt 100) { throw "The backup file is missing or empty: $file. Stopping." }
    Info "Backup saved: $file ($([math]::Round((Get-Item $file).Length / 1KB)) KB)"
    # D1 also keeps 30 days of history ("Time Travel"). Note the current point so you can return to it if ever needed.
    $bookmark = Capture { npx wrangler d1 time-travel info $ExpectedDbName }
    Set-Content -Path (Join-Path $Backups "leestn-$stamp.timetravel.txt") -Value $bookmark
    Info 'Saved the current Time Travel restore point next to the backup.'

    if ($NoDeploy) { Say 'Stopped as requested (-NoDeploy). Nothing in production was changed.'; return }

    # -----------------------------------------------------------------------------------------------
    if ($hasPending) {
        Say '5. Apply waiting migrations to the live database'
        Info 'Migrations only ever add things. Each one runs once and is recorded, so it can never run twice.'
        Gate "Apply the migrations listed above to the LIVE database '$ExpectedDbName'?"
        Run 'Migration' { npx wrangler d1 migrations apply $ExpectedDbName --remote }
        $after = Capture { npx wrangler d1 migrations list $ExpectedDbName --remote }
        if ($LASTEXITCODE -ne 0) { throw "Couldn't re-read the migration list:`n$after" }
        if ($after -notmatch 'No migrations to apply') { throw "Migrations still show as waiting after applying them:`n$after" }
        Info 'Confirmed: no migrations waiting any more.'
    } else {
        Say '5. Migrations: none needed, skipped'
    }

    # -----------------------------------------------------------------------------------------------
    Say '6. Deploy the Worker code'
    Info 'Dry run first: this builds the code and lists what it would attach, without deploying.'
    $dry = Join-Path ([System.IO.Path]::GetTempPath()) "leestn-dryrun-$stamp"
    Run 'Dry run' { npx wrangler deploy --dry-run --outdir $dry }
    Info 'Deploying replaces only the code of the existing Worker. Your database, data, queue and secrets stay as they are.'
    Gate "Deploy to the LIVE Worker '$ExpectedWorker'?"
    Run 'Deploy' { npx wrangler deploy }

    # -----------------------------------------------------------------------------------------------
    Say '7. Health check (read-only, creates nothing)'
    Start-Sleep -Seconds 3
    $h = Invoke-RestMethod -Uri $HealthUrl -Method Get -TimeoutSec 30
    if (-not $h.ok) { throw "Health check did not report ok: $($h | ConvertTo-Json -Compress)" }
    $want = (Get-Content (Join-Path $Backend 'package.json') -Raw | ConvertFrom-Json).version
    Info "Live Worker says: name=$($h.name) version=$($h.version)"
    if ($h.version -ne $want) { Warn "Expected version $want. The live Worker may still be updating; run the health check again in a minute." }
    else { Info "Matches this release ($want)." }

    # -----------------------------------------------------------------------------------------------
    Say '8. Check the founder badge (read-only: only looks, changes nothing)'
    $f = Capture { npx wrangler d1 execute $ExpectedDbName --remote --command "SELECT handle, role FROM users WHERE role = 'founder'" }
    Write-Host $f
    if ($f -notmatch '(?i)hytyei') { Warn 'No account named Hytyei has the founder badge on this database. If you have not created that account yet, the badge can be added later (see RELEASE-1.5.9.md).' }
    else { Info 'Founder badge is set for Hytyei.' }
}
finally { Pop-Location }

Say 'Done'
Info 'If anything looks wrong, see release\RELEASE-1.5.9.md, section "If something goes wrong".'
