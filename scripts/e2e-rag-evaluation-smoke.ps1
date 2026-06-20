#!/usr/bin/env pwsh
# =============================================================================
# Seahorse Agent - RAG Evaluation Smoke Test (CI-ready)
# Creates a knowledge base, uploads a document, creates an evaluation dataset,
# runs evaluation, and verifies results.
# Usage: pwsh scripts/e2e-rag-evaluation-smoke.ps1 [-BaseUrl http://localhost:9090]
# =============================================================================
param(
    [string]$BaseUrl = "http://localhost:9090",
    [string]$Username = "admin",
    [string]$Password = "admin123"
)

$ErrorActionPreference = "Stop"
$passed = 0
$failed = 0
$total = 0

function Test-Step {
    param([string]$Name, [scriptblock]$Action)
    $script:total++
    Write-Host "`n[$total] $Name" -ForegroundColor Cyan
    try {
        $result = & $Action
        if ($result) {
            $script:passed++
            Write-Host "  PASS" -ForegroundColor Green
            return $result
        } else {
            $script:failed++
            Write-Host "  FAIL (returned falsy)" -ForegroundColor Red
            return $null
        }
    } catch {
        $script:failed++
        Write-Host "  FAIL: $($_.Exception.Message)" -ForegroundColor Red
        return $null
    }
}

# ---- Step 0: Login ----
$loginResp = Invoke-RestMethod -Uri "$BaseUrl/auth/login" -Method POST -ContentType "application/json" `
    -Body "{`"username`":`"$Username`",`"password`":`"$Password`"}"
$token = $loginResp.data.token
if (-not $token) { Write-Host "Login failed" -ForegroundColor Red; exit 1 }
$h = @{ "Authorization" = "Bearer $token" }
Write-Host "Login OK (token=$($token.Substring(0,8))...)" -ForegroundColor Green

# ---- Step 1: Create Knowledge Base ----
$ts = Get-Date -Format "HHmmss"
$kbName = "eval-smoke-$ts"
$colName = "evalsmoke$ts"
$kbResp = Test-Step "Create knowledge base ($kbName)" {
    $body = "{`"name`":`"$kbName`",`"embeddingModel`":`"nomic-embed-text`",`"collectionName`":`"$colName`"}"
    $r = Invoke-RestMethod -Uri "$BaseUrl/knowledge-base" -Method POST -Headers $h -ContentType "application/json" -Body $body
    if ($r.code -ne "0") { throw "API error: $($r | ConvertTo-Json -Compress)" }
    Write-Host "  KB ID: $($r.data)"
    return $r.data
}
if (-not $kbResp) { Write-Host "Cannot continue without KB"; exit 1 }
$kbId = $kbResp

# ---- Step 2: Upload a smoke document ----
$docContent = @"
# Seahorse Agent Smoke Document
Seahorse Agent uses nomic-embed-text for local embedding with dimension 768.
Milvus is the vector store backend. Elasticsearch provides keyword search.
Memory capture, aggregation buffer, and profile fact generation are core features.
The RAG pipeline includes vector search, keyword search, RRF fusion, and rerank.
Production gate checks include eval passing, quota configuration, and tool review.
"@

$tmpFile = [System.IO.Path]::GetTempFileName()
Set-Content -Path $tmpFile -Value $docContent -Encoding UTF8

Test-Step "Upload smoke document" {
    $uploadResp = curl.exe -s -X POST "$BaseUrl/knowledge-base/$kbId/docs/upload" `
        -H "Authorization: Bearer $token" `
        -F "file=@$tmpFile;filename=smoke-eval.md" `
        -F "chunkSize=256" -F "chunkOverlap=32"
    Write-Host "  Upload response: $($uploadResp.Substring(0, [Math]::Min(120, $uploadResp.Length)))"
    return $uploadResp -match '"code"'
}
Remove-Item $tmpFile -Force -ErrorAction SilentlyContinue

# Wait for indexing
Write-Host "`n  Waiting 15s for document indexing..." -ForegroundColor Yellow
Start-Sleep -Seconds 15

# ---- Step 3: Verify document is indexed ----
Test-Step "Verify document chunks exist" {
    $docs = Invoke-RestMethod -Uri "$BaseUrl/knowledge-base/$kbId/docs?page=1&size=5" -Headers $h
    Write-Host "  Documents: $($docs.data.total), Chunks in first doc: $($docs.data.records[0].chunkCount)"
    return ($docs.data.total -gt 0)
}

# ---- Step 4: Create evaluation dataset ----
$dsResp = Test-Step "Create evaluation dataset" {
    $dsBody = @{
        datasetId = ""
        name = "ci-smoke-dataset"
        description = "CI smoke evaluation dataset"
        enabled = $true
        cases = @(
            @{
                caseId = "smoke-1"
                question = "What embedding model and dimension does Seahorse use?"
                expectedKbIds = @($kbId)
                expectedDocIds = @()
                expectedChunkIds = @()
                negativeChunkIds = @()
                tags = @("smoke", "embedding")
                minRecall = 0.5
            },
            @{
                caseId = "smoke-2"
                question = "What are the core RAG pipeline components?"
                expectedKbIds = @($kbId)
                expectedDocIds = @()
                expectedChunkIds = @()
                negativeChunkIds = @()
                tags = @("smoke", "rag")
                minRecall = 0.5
            }
        )
    } | ConvertTo-Json -Depth 5
    $r = Invoke-RestMethod -Uri "$BaseUrl/knowledge-base/$kbId/retrieval-evaluation-datasets" `
        -Method POST -Headers $h -ContentType "application/json" -Body $dsBody
    if ($r.code -ne "0") { throw "API error: $($r | ConvertTo-Json -Compress)" }
    Write-Host "  Dataset ID: $($r.data.datasetId), Cases: $($r.data.cases.Count)"
    return $r.data.datasetId
}
if (-not $dsResp) { Write-Host "Cannot continue without dataset"; exit 1 }
$dsId = $dsResp

# ---- Step 5: Run evaluation ----
$evalResp = Test-Step "Run evaluation" {
    $evalBody = '{"strategyName":"ci-smoke","topK":5}'
    $r = Invoke-RestMethod -Uri "$BaseUrl/knowledge-base/$kbId/retrieval-evaluation-datasets/$dsId/evaluate" `
        -Method POST -Headers $h -ContentType "application/json" -Body $evalBody
    if ($r.code -ne "0") { throw "API error: $($r | ConvertTo-Json -Compress)" }
    $d = $r.data
    Write-Host "  recall@k=$($d.recallAtK), precision@k=$($d.precisionAtK), MRR=$($d.mrr), NDCG=$($d.ndcgAtK)"
    Write-Host "  emptyRecallRate=$($d.emptyRecallRate), avgLatency=$($d.averageLatencyMs)ms"
    Write-Host "  Cases evaluated: $($d.evaluableCaseCount)/$($d.caseCount)"
    return ($d.evaluableCaseCount -gt 0 -and $d.emptyRecallRate -lt 1.0)
}

# ---- Step 6: Verify strategy templates exist ----
Test-Step "Verify strategy templates" {
    $r = Invoke-RestMethod -Uri "$BaseUrl/knowledge-base/$kbId/retrieval-strategy-templates" -Headers $h
    $templates = $r.data
    Write-Host "  Templates: $($templates.Count) ($($templates.templateKey -join ', '))"
    return ($templates.Count -gt 0)
}

# ---- Summary ----
Write-Host "`n============================================" -ForegroundColor White
Write-Host "RAG Evaluation Smoke Test Results" -ForegroundColor White
Write-Host "  Passed: $passed / $total" -ForegroundColor $(if ($failed -eq 0) { "Green" } else { "Red" })
Write-Host "  Failed: $failed / $total" -ForegroundColor $(if ($failed -eq 0) { "Green" } else { "Red" })
Write-Host "============================================" -ForegroundColor White

# Cleanup: delete the KB
Write-Host "`nCleanup: deleting KB $kbId..." -ForegroundColor Yellow
try {
    Invoke-RestMethod -Uri "$BaseUrl/knowledge-base/$kbId" -Method DELETE -Headers $h | Out-Null
    Write-Host "  KB deleted" -ForegroundColor Green
} catch {
    Write-Host "  Cleanup warning: $($_.Exception.Message)" -ForegroundColor Yellow
}

if ($failed -gt 0) { exit 1 }
Write-Host "`nAll smoke tests passed!" -ForegroundColor Green
exit 0
