param([string]$EnvFile = '.env.local')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not [IO.Path]::IsPathRooted($EnvFile)) { $EnvFile = Join-Path $projectRoot $EnvFile }
if (-not (Test-Path -LiteralPath $EnvFile)) { throw 'Copy .env.example to .env.local and fill in your settings first.' }
foreach ($line in Get-Content -LiteralPath $EnvFile -Encoding UTF8) {
    $entry = $line.Trim()
    if (-not $entry -or $entry.StartsWith('#')) { continue }
    if ($entry -notmatch '^([A-Z][A-Z0-9_]*)=(.*)$') { throw 'Invalid setting. Expected NAME=value.' }
    [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2].Trim(), 'Process')
}
if (-not $env:RBAC_DB_PASSWORD -or -not $env:RBAC_PHONE_KEY) { throw 'RBAC_DB_PASSWORD and RBAC_PHONE_KEY are required.' }
Push-Location $projectRoot
try {
    if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) { throw 'Install Maven 3.9+ and add mvn to PATH.' }
    & mvn spring-boot:run
    if ($LASTEXITCODE -ne 0) { throw 'Backend startup failed.' }
} finally { Pop-Location }
