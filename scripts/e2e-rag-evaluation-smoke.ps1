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
$docId = $null

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

function Assert-ApiOk {
    param(
        [object]$Response,
        [string]$Name
    )
    if ($null -eq $Response -or $Response.code -ne "0") {
        throw "$Name API error: $($Response | ConvertTo-Json -Depth 10 -Compress)"
    }
}

function Wait-ForDocumentChunks {
    param(
        [string]$DocumentId,
        [int]$Attempts = 20,
        [int]$DelaySeconds = 3
    )
    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        $chunksResp = Invoke-RestMethod -Uri "$BaseUrl/knowledge-base/docs/$DocumentId/chunks?current=1&size=10" -Headers $h
        Assert-ApiOk $chunksResp "Document chunks"
        $records = @($chunksResp.data.records)
        if ($records.Count -gt 0) {
            return ,$records
        }
        if ($attempt -lt $Attempts) {
            Start-Sleep -Seconds $DelaySeconds
        }
    }
    throw "Document $DocumentId has no chunks after $Attempts attempts"
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
    $upload = $uploadResp | ConvertFrom-Json
    Assert-ApiOk $upload "Upload document"
    $script:docId = [string]$upload.data.id
    if ([string]::IsNullOrWhiteSpace($script:docId)) { throw "Upload did not return document id" }
    return $script:docId
}
Remove-Item $tmpFile -Force -ErrorAction SilentlyContinue

# Manual chunking makes the smoke deterministic in both queue-backed and local modes.
Test-Step "Start document chunking" {
    $chunkResp = Invoke-RestMethod -Uri "$BaseUrl/knowledge-base/docs/$docId/chunk" -Method POST -Headers $h
    Assert-ApiOk $chunkResp "Start document chunking"
    return $true
}

# ---- Step 3: Verify document is indexed ----
$indexedChunks = @(Test-Step "Verify document chunks exist" {
    $docs = Invoke-RestMethod -Uri "$BaseUrl/knowledge-base/$kbId/docs?page=1&size=5" -Headers $h
    Assert-ApiOk $docs "Document page"
    $chunks = @(Wait-ForDocumentChunks -DocumentId $docId)
    $firstDoc = @($docs.data.records | Where-Object { [string]$_.id -eq $docId })[0]
    Write-Host "  Documents: $($docs.data.total), chunk records: $($chunks.Count), listed chunkCount: $($firstDoc.chunkCount)"
    return ,$chunks
})

# ---- Step 4: Create evaluation dataset ----
$dsResp = Test-Step "Create evaluation dataset" {
    $expectedChunkIds = @($indexedChunks | Select-Object -First 3 | ForEach-Object { [string]$_.id })
    if ($expectedChunkIds.Count -eq 0) { throw "No expected chunk ids available for evaluation dataset" }
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
                expectedDocIds = @($docId)
                expectedChunkIds = $expectedChunkIds
                negativeChunkIds = @()
                tags = @("smoke", "embedding")
                minRecall = 0.5
            },
            @{
                caseId = "smoke-2"
                question = "What are the core RAG pipeline components?"
                expectedKbIds = @($kbId)
                expectedDocIds = @($docId)
                expectedChunkIds = $expectedChunkIds
                negativeChunkIds = @()
                tags = @("smoke", "rag")
                minRecall = 0.5
            }
        )
    } | ConvertTo-Json -Depth 5
    $r = Invoke-RestMethod -Uri "$BaseUrl/knowledge-base/$kbId/retrieval-evaluation-datasets" `
        -Method POST -Headers $h -ContentType "application/json" -Body $dsBody
    Assert-ApiOk $r "Create evaluation dataset"
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
    Assert-ApiOk $r "Run evaluation"
    $d = $r.data
    Write-Host "  recall@k=$($d.recallAtK), precision@k=$($d.precisionAtK), MRR=$($d.mrr), NDCG=$($d.ndcgAtK)"
    Write-Host "  emptyRecallRate=$($d.emptyRecallRate), avgLatency=$($d.averageLatencyMs)ms"
    Write-Host "  Cases evaluated: $($d.evaluableCaseCount)/$($d.caseCount)"
    if ($d.evaluableCaseCount -lt 2) { throw "Expected both smoke cases to be evaluable" }
    if ([double]$d.recallAtK -le 0) { throw "Expected recall@k > 0 after indexing smoke chunks" }
    if ([double]$d.emptyRecallRate -ge 1.0) { throw "Expected non-empty recall results" }
    return $true
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
    if ($docId) {
        Invoke-RestMethod -Uri "$BaseUrl/knowledge-base/docs/$docId" -Method DELETE -Headers $h | Out-Null
        Write-Host "  Document deleted" -ForegroundColor Green
    }
    Invoke-RestMethod -Uri "$BaseUrl/knowledge-base/$kbId" -Method DELETE -Headers $h | Out-Null
    Write-Host "  KB deleted" -ForegroundColor Green
} catch {
    Write-Host "  Cleanup warning: $($_.Exception.Message)" -ForegroundColor Yellow
}

if ($failed -gt 0) { exit 1 }
Write-Host "`nAll smoke tests passed!" -ForegroundColor Green
exit 0
