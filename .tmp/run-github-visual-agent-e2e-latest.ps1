$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$base = 'http://127.0.0.1:9090'
$workspace = Split-Path -Parent $PSScriptRoot
$runRoot = Join-Path $workspace 'docs\e2e\redis-project-intro'
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$runDir = Join-Path $runRoot $timestamp
New-Item -ItemType Directory -Force -Path $runDir | Out-Null

$loginBody = @{ username = 'admin'; password = 'admin123' } | ConvertTo-Json -Compress
$login = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' -Body $loginBody -TimeoutSec 60
$token = $login.data.token
if (-not $token) {
    throw 'Login did not return token'
}

$conversationId = 'codex-redis-latest-e2e-' + (Get-Date -Format 'yyyyMMddHHmmss')
$question = @'
Generate a fresh E2E validation output for https://github.com/redis/redis.
The final content must be Chinese Markdown and must satisfy these hard requirements:
1. Read README, docs, and core source evidence before summarizing.
2. Call github_repository_reader, web_fetch, chart_visualization, image_generation, newsletter_generation, ppt_generation, and frontend_design.
3. Include project overview, architecture design, architecture diagram, flow diagram, core logic, key features, key file evidence table, generated image reference, generated article/layout artifact summary, and conclusion.
4. Section 2.1 must be a standard Mermaid flowchart code block. Do not use ASCII box diagrams. Do not output malformed fences such as ```mermaidflowchart.
5. Image references must be Web-safe http/https URLs or data:image/*;base64 URLs. Do not use local paths, relative paths, file:// URLs, localhost URLs, or internal MinIO URLs.
6. Chapter 9 must include concrete summaries for the actual outputs from newsletter_generation, ppt_generation, and frontend_design. It must have the three subsections for long-form article summary, presentation summary, and Web layout preview summary.
7. After the Markdown body, output exactly one HTML artifact:
<artifact language="html" title="project-intro-web-preview.html">
...
</artifact>
The HTML artifact must cover the whole document, not only Chapter 9, and its image references must also be Web-safe.
8. The final Markdown and HTML must be suitable for Web streaming, copy, and download.
9. Do not merely claim that newsletter_generation, ppt_generation, and frontend_design ran. Use the actual tool observations when writing Chapter 9. The E2E verifier will save those tool observations as newsletter.md, presentation.md, and frontend-design-tool-output.html from tool_call_finished events.
'@

$query = [ordered]@{
    question = $question
    conversationId = $conversationId
    userId = '2001523723396308993'
    chatMode = 'agent'
    agentId = 'github-visual-project-intro-agent'
    versionId = 'github-visual-project-intro-agent-v1'
    deepThinking = 'false'
}
$qs = ($query.GetEnumerator() | ForEach-Object {
    [uri]::EscapeDataString($_.Key) + '=' + [uri]::EscapeDataString([string]$_.Value)
}) -join '&'

$rawFile = Join-Path $runDir 'raw.sse.txt'
$headers = @{ Authorization = $token; 'X-User-Id' = '2001523723396308993' }

Invoke-WebRequest -UseBasicParsing -Method Get -Uri "$base/rag/v3/chat?$qs" -Headers $headers -TimeoutSec 1800 -OutFile $rawFile

$info = [ordered]@{
    conversationId = $conversationId
    runDir = $runDir
    rawFile = $rawFile
    finishedAt = (Get-Date).ToString('o')
}
$info | ConvertTo-Json -Depth 4 | Set-Content -Path (Join-Path $runDir 'run-info.json') -Encoding UTF8

Write-Output "conversationId=$conversationId"
Write-Output "runDir=$runDir"
Write-Output "rawFile=$rawFile"
