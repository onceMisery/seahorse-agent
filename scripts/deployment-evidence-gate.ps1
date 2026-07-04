param(
    [string]$BackendBaseUrl = "http://127.0.0.1:9090",
    [string]$FrontendBaseUrl = "http://127.0.0.1",
    [string]$Username = "admin",
    [string]$Password = "admin123",
    [string]$BackendImage = "seahorse-agent-backend:latest",
    [string]$S3BaseUrl = "http://127.0.0.1:19092",
    [int]$S3HostPort = 19092,
    [switch]$SkipS3,
    [switch]$SkipPulsar,
    [switch]$SkipRagPromotion,
    [switch]$SkipAgentRollout
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$results = @()

function Invoke-GateStep {
    param(
        [string]$Name,
        [string]$ScriptPath,
        [string[]]$Arguments
    )

    Write-Host "`nDEPLOYMENT_GATE_STEP=$Name" -ForegroundColor Cyan
    $startedAt = Get-Date
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $ScriptPath @Arguments
    $exitCode = $LASTEXITCODE
    $durationMs = [int]((Get-Date) - $startedAt).TotalMilliseconds
    $script:results += [PSCustomObject]@{
        Name = $Name
        ExitCode = $exitCode
        DurationMs = $durationMs
    }
    if ($exitCode -ne 0) {
        Write-Host "DEPLOYMENT_GATE_STEP_FAILED=$Name exit=$exitCode durationMs=$durationMs" -ForegroundColor Red
        return
    }
    Write-Host "DEPLOYMENT_GATE_STEP_PASS=$Name durationMs=$durationMs" -ForegroundColor Green
}

if (-not $SkipS3) {
    Invoke-GateStep -Name "s3-storage-switch" `
        -ScriptPath (Join-Path $scriptDir "e2e-s3-storage-smoke.ps1") `
        -Arguments @(
            "-BaseUrl", $S3BaseUrl,
            "-Username", $Username,
            "-Password", $Password,
            "-BackendImage", $BackendImage,
            "-HostPort", "$S3HostPort"
        )
}

if (-not $SkipPulsar) {
    Invoke-GateStep -Name "pulsar-consume-loop" `
        -ScriptPath (Join-Path $scriptDir "e2e-pulsar-mq-smoke.ps1") `
        -Arguments @(
            "-BaseUrl", $BackendBaseUrl,
            "-Username", $Username,
            "-Password", $Password
        )
}

if (-not $SkipRagPromotion) {
    Invoke-GateStep -Name "rag-strategy-promotion" `
        -ScriptPath (Join-Path $scriptDir "e2e-rag-strategy-promotion-smoke.ps1") `
        -Arguments @(
            "-BaseUrl", $FrontendBaseUrl,
            "-Username", $Username,
            "-Password", $Password
        )
}

if (-not $SkipAgentRollout) {
    Invoke-GateStep -Name "agent-rollout-promote" `
        -ScriptPath (Join-Path $scriptDir "e2e-agent-rollout-smoke.ps1") `
        -Arguments @(
            "-BaseUrl", $FrontendBaseUrl,
            "-Username", $Username,
            "-Password", $Password
        )
}

$failed = @($results | Where-Object { $_.ExitCode -ne 0 })
$passed = @($results | Where-Object { $_.ExitCode -eq 0 })

Write-Host "`nDeployment evidence gate summary" -ForegroundColor Cyan
$results | ConvertTo-Json -Depth 4 -Compress | Write-Host
Write-Host "DEPLOYMENT_EVIDENCE_GATE_STEPS=$($results.Count)"
Write-Host "DEPLOYMENT_EVIDENCE_GATE_PASS_COUNT=$($passed.Count)"
Write-Host "DEPLOYMENT_EVIDENCE_GATE_FAIL_COUNT=$($failed.Count)"

if ($results.Count -eq 0) {
    Write-Host "DEPLOYMENT_EVIDENCE_GATE=FAIL no steps selected" -ForegroundColor Red
    exit 1
}

if ($failed.Count -gt 0) {
    Write-Host "DEPLOYMENT_EVIDENCE_GATE=FAIL" -ForegroundColor Red
    exit 1
}

Write-Host "DEPLOYMENT_EVIDENCE_GATE=PASS" -ForegroundColor Green
