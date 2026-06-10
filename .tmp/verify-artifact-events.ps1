#!/usr/bin/env pwsh
# Verify AGENT_ARTIFACT events from latest E2E run

$latestDir = Get-ChildItem "docs/e2e/redis-project-intro" -Directory |
    Where-Object { $_.Name -match '^\d{8}-\d{6}$' } |
    Sort-Object Name -Descending |
    Select-Object -First 1

if (-not $latestDir) {
    Write-Host "ERROR: No E2E run directory found" -ForegroundColor Red
    exit 1
}

Write-Host "`nLatest E2E run: $($latestDir.Name)" -ForegroundColor Cyan
$sseFile = Join-Path $latestDir.FullName "raw.sse.txt"

if (-not (Test-Path $sseFile)) {
    Write-Host "ERROR: raw.sse.txt not found" -ForegroundColor Red
    exit 1
}

$content = Get-Content $sseFile -Raw -Encoding UTF8
$lines = $content -split "`n"

$artifactEvents = $lines | Where-Object { $_ -match 'AGENT_ARTIFACT' }
Write-Host "`nTotal AGENT_ARTIFACT events: $($artifactEvents.Count)" -ForegroundColor Yellow

if ($artifactEvents.Count -eq 0) {
    Write-Host "FAILED: No artifact events found" -ForegroundColor Red
    exit 1
}

Write-Host "`nArtifact details:" -ForegroundColor Green
foreach ($evt in $artifactEvents) {
    try {
        $json = $evt.Substring(5).Trim() | ConvertFrom-Json
        $tp = $json.typedPayload
        Write-Host "  $($tp.artifactType.PadRight(12)) | $($tp.title.PadRight(35)) | $($tp.mimeType)"
    } catch {
        Write-Host "  [Parse error]" -ForegroundColor Red
    }
}

if ($artifactEvents.Count -ge 5) {
    Write-Host "`nSUCCESS: Task 5 fix verified (5+ artifact events)" -ForegroundColor Green
    exit 0
} else {
    Write-Host "`nPARTIAL: Only $($artifactEvents.Count)/5 expected events" -ForegroundColor Yellow
    exit 2
}
