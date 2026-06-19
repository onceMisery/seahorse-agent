param(
    [string]$SmokeScript = "scripts/e2e-backend-smoke.ps1"
)

$ErrorActionPreference = "Stop"

$scriptPath = Join-Path (Get-Location) $SmokeScript
if (-not (Test-Path -LiteralPath $scriptPath)) {
    throw "Smoke script not found: $SmokeScript"
}

$content = Get-Content -LiteralPath $scriptPath -Raw

$requiredSnippets = @(
    'Authorization = "Bearer $token"',
    '"/knowledge-base/$kbId/docs/upload"',
    '"/knowledge-base/docs/$docId/chunk"',
    '"/knowledge-base/docs/$docId/chunk-logs?current=1&size=10"',
    'Assert-NonEmptyPageRecords "Knowledge document chunk logs" $chunkLogs',
    '"/rag/v3/chat?',
    '&knowledgeBaseIds=$kbId',
    '"/rag/traces/runs?current=1&size=10"',
    '"/rag/traces/runs/$traceId/nodes"',
    '"/memories/readiness?userId=$userId&tenantId=default"',
    '"/memories/profile-facts?userId=$userId&tenantId=default&limit=20"',
    '"/memories/maintenance/run?reason=smoke-check&compaction=true&alias=true&gc=true"',
    'Assert-NonEmptyDataArray "Profile facts" $profileFacts',
    'Assert-RetrievalTraceNodes $traceNodes',
    '[ValidateSet("full-compose", "none")]',
    'Assert-FullComposeRuntime -ContainerName $DockerContainerName',
    '"/readiness/summary"',
    'Assert-FullAdapterReadiness $readiness',
    'docker-compose.full.yml',
    'SEAHORSE_AGENT_ADAPTERS_VECTOR_TYPE',
    'SEAHORSE_AGENT_ADAPTERS_CACHE_TYPE',
    'SEAHORSE_AGENT_ADAPTERS_MQ_TYPE',
    'SEAHORSE_AGENT_ADAPTERS_OBSERVATION_TYPE',
    'vector.store',
    'search.keyword',
    'cache',
    'mq',
    'ForbiddenPattern "noop|unknown"',
    'unavailable|noop|unknown',
    'ForbiddenPattern "\blocal\b|unknown"',
    'ForbiddenPattern "\bdirect\b|unknown"'
)

$forbiddenSnippets = @(
    '"/rag/query"',
    '"/rag/config"',
    '"/knowledge-base/$kbId/documents"',
    '"/knowledge-base/$kbId/chunks"',
    '-X POST "$BaseUrl/rag/v3/chat"',
    '"/api/readiness/summary"'
)

$missing = @($requiredSnippets | Where-Object { -not $content.Contains($_) })
$forbidden = @($forbiddenSnippets | Where-Object { $content.Contains($_) })

if ($missing.Count -gt 0 -or $forbidden.Count -gt 0) {
    if ($missing.Count -gt 0) {
        Write-Host "Missing required smoke contract snippets:" -ForegroundColor Red
        $missing | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    }
    if ($forbidden.Count -gt 0) {
        Write-Host "Forbidden stale smoke contract snippets:" -ForegroundColor Red
        $forbidden | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    }
    exit 1
}

Write-Host "Smoke contract check passed for $SmokeScript"
