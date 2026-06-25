param(
    [string]$BaseUrl = "http://127.0.0.1",
    [string]$Username = "admin",
    [string]$Password = "admin123",
    [string]$ArtifactDir = "output/playwright/artifacts",
    [switch]$Headed
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir
$runtimeDir = Join-Path $repoRoot "output/playwright"
$runtimePackage = Join-Path $runtimeDir "package.json"
$playwrightPackage = Join-Path $runtimeDir "node_modules/playwright/package.json"

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
    (Join-Path $scriptDir "e2e-agent-rollout-smoke.mjs"),
    "--base-url", $BaseUrl,
    "--username", $Username,
    "--password", $Password,
    "--artifact-dir", $artifactPath
)

if ($Headed) {
    $nodeArgs += "--headed"
}

node @nodeArgs
exit $LASTEXITCODE
