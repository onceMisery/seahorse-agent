param(
    [string]$BaseUrl = "http://127.0.0.1",
    [string]$AdminUsername = "admin",
    [string]$AdminPassword = "admin123",
    [string]$UserUsername = "demo_user_001",
    [string]$UserPassword = "demo123",
    [string]$ArtifactDir = "output/playwright/artifacts",
    [string]$PostgresContainer = "seahorse-postgres",
    [string]$PostgresUsername = "seahorse",
    [string]$PostgresDatabase = "seahorse",
    [string]$TenantId = "default",
    [switch]$SkipUserSeed,
    [switch]$Headed
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir
$runtimeDir = Join-Path $repoRoot "output/playwright"
$runtimePackage = Join-Path $runtimeDir "package.json"
$playwrightPackage = Join-Path $runtimeDir "node_modules/playwright/package.json"

. (Join-Path $scriptDir "e2e-governance-user-seed.ps1")

if (-not $SkipUserSeed) {
    Ensure-SeahorseGovernanceNormalUser `
        -Username $UserUsername `
        -Password $UserPassword `
        -TenantId $TenantId `
        -PostgresContainer $PostgresContainer `
        -PostgresUsername $PostgresUsername `
        -PostgresDatabase $PostgresDatabase
}

New-Item -ItemType Directory -Force -Path $runtimeDir | Out-Null

if (-not (Test-Path -LiteralPath $runtimePackage)) {
    Set-Content -LiteralPath $runtimePackage -Encoding UTF8 -Value '{"private":true,"dependencies":{"playwright":"^1.61.0"}}'
}

if (-not (Test-Path -LiteralPath $playwrightPackage)) {
    Write-Host "Installing Playwright runtime into $runtimeDir"
    Push-Location $runtimeDir
    try {
        npm install --no-audit --no-fund
        if ($LASTEXITCODE -ne 0) {
            throw "npm install failed with exit code $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }
}

$env:PLAYWRIGHT_RUNTIME_DIR = $runtimeDir

$artifactPath = if ([System.IO.Path]::IsPathRooted($ArtifactDir)) {
    $ArtifactDir
} else {
    Join-Path $repoRoot $ArtifactDir
}

$nodeArgs = @(
    (Join-Path $scriptDir "e2e-governance-page-states-smoke.mjs"),
    "--base-url", $BaseUrl,
    "--admin-username", $AdminUsername,
    "--admin-password", $AdminPassword,
    "--user-username", $UserUsername,
    "--user-password", $UserPassword,
    "--artifact-dir", $artifactPath
)

if ($Headed) {
    $nodeArgs += "--headed"
}

node @nodeArgs
exit $LASTEXITCODE
