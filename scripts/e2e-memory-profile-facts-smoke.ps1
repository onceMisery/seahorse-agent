param(
    [string]$BaseUrl = "http://127.0.0.1",
    [string]$Username = "admin",
    [string]$Password = "admin123",
    [switch]$Headed
)

$ErrorActionPreference = "Stop"

$script = Join-Path $PSScriptRoot "e2e-memory-profile-facts-smoke.mjs"
$args = @(
    $script,
    "--base-url", $BaseUrl,
    "--username", $Username,
    "--password", $Password
)

if ($Headed) {
    $args += "--headed"
}

node @args
exit $LASTEXITCODE
