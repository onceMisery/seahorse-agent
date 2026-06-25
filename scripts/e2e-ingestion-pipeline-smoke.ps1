param(
    [string]$BaseUrl = "http://127.0.0.1:9090",
    [string]$Username = "admin",
    [string]$Password = "admin123",
    [string]$PostgresContainer = "seahorse-postgres",
    [string]$PostgresUser = "seahorse",
    [string]$PostgresDatabase = "seahorse"
)

$ErrorActionPreference = "Stop"
$passed = 0
$failed = 0
$total = 0

function Test-Step {
    param([string]$Name, [scriptblock]$Action)
    $script:total++
    Write-Host "`n[$script:total] $Name" -ForegroundColor Cyan
    try {
        $result = & $Action
        $script:passed++
        Write-Host "  PASS" -ForegroundColor Green
        return $result
    } catch {
        $script:failed++
        Write-Host "  FAIL: $($_.Exception.Message)" -ForegroundColor Red
        return $null
    }
}

function Assert-ApiOk {
    param([object]$Response, [string]$Name)
    if ($null -eq $Response -or "$($Response.code)" -ne "0") {
        throw "$Name API error: $($Response | ConvertTo-Json -Depth 30 -Compress)"
    }
}

function Assert-Equal {
    param([object]$Actual, [object]$Expected, [string]$Name)
    if ("$Actual" -ne "$Expected") {
        throw "$Name expected '$Expected' but got '$Actual'"
    }
}

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        throw $Message
    }
}

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        [hashtable]$Headers = @{},
        [object]$Body = $null
    )
    $uri = "$BaseUrl$Path"
    $params = @{
        Uri = $uri
        Method = $Method
        Headers = $Headers
    }
    if ($null -ne $Body) {
        $params.ContentType = "application/json"
        $params.Body = ($Body | ConvertTo-Json -Depth 40 -Compress)
    }
    return Invoke-RestMethod @params
}

function Invoke-MultipartFile {
    param(
        [string]$Path,
        [string]$FilePath,
        [string]$Token
    )
    $url = "$BaseUrl$Path"
    $raw = & curl.exe -sS -w "`n%{http_code}" -X POST `
        -H "Authorization: Bearer $Token" `
        -F "file=@$FilePath" `
        $url
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        throw "curl exited with $exitCode for multipart POST $Path"
    }
    $lines = @($raw)
    if ($lines.Count -eq 0) {
        throw "empty curl output for multipart POST $Path"
    }
    $status = [int]$lines[-1]
    $content = if ($lines.Count -gt 1) { ($lines[0..($lines.Count - 2)] -join "`n") } else { "" }
    if ($status -ne 200) {
        throw "Expected HTTP 200 but got $status for multipart POST $Path body=$content"
    }
    return $content | ConvertFrom-Json
}

function Invoke-DbScalarRow {
    param([string]$Sql)
    $raw = & docker.exe exec $PostgresContainer psql -U $PostgresUser -d $PostgresDatabase -t -A -F "|" -c $Sql
    if ($LASTEXITCODE -ne 0) {
        throw "psql failed with exit code $LASTEXITCODE"
    }
    $rows = @($raw | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($rows.Count -eq 0) {
        return $null
    }
    return $rows[0]
}

function New-SuccessPipelineBody {
    param([string]$Name)
    return @{
        name = $Name
        description = "Codex real ingestion success pipeline"
        nodes = @(
            @{
                nodeId = "1"
                nodeType = "parser"
                nextNodeId = "2"
                settings = @{}
            },
            @{
                nodeId = "2"
                nodeType = "chunker"
                settings = @{
                    chunkSize = 40
                    overlapSize = 0
                    embed = $false
                }
            }
        )
    }
}

function New-FailingPipelineBody {
    param([string]$Name)
    return @{
        name = $Name
        description = "Codex real ingestion retry pipeline"
        nodes = @(
            @{
                nodeId = "1"
                nodeType = "parser"
                nextNodeId = "2"
                settings = @{}
            },
            @{
                nodeId = "2"
                nodeType = "chunker"
                nextNodeId = "3"
                settings = @{
                    chunkSize = 64
                    overlapSize = 0
                    embed = $false
                }
            },
            @{
                nodeId = "3"
                nodeType = "indexer"
                settings = @{
                    collectionName = "codex_ingestion_retry"
                    kbId = "1"
                    docId = "1"
                }
            }
        )
    }
}

$login = Test-Step "Login" {
    $response = Invoke-Api -Method POST -Path "/auth/login" -Body @{
        username = $Username
        password = $Password
    }
    Assert-ApiOk $response "Login"
    Assert-True (-not [string]::IsNullOrWhiteSpace([string]$response.data.token)) "Login did not return token"
    $response
}
if (-not $login) { exit 1 }

$token = [string]$login.data.token
$headers = @{ Authorization = "Bearer $token" }
$suffix = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$marker = "CODX_INGESTION_PIPELINE_$suffix"

$successPipeline = Test-Step "Create success ingestion pipeline" {
    $response = Invoke-Api -Method POST -Path "/ingestion/pipelines" -Headers $headers -Body (New-SuccessPipelineBody "$marker-success")
    Assert-ApiOk $response "Create success pipeline"
    Assert-True (-not [string]::IsNullOrWhiteSpace([string]$response.data.id)) "Success pipeline did not return id"
    $response.data
}
if (-not $successPipeline) { exit 1 }

$successTask = Test-Step "Execute parser/chunker success task" {
    $response = Invoke-Api -Method POST -Path "/ingestion/tasks" -Headers $headers -Body @{
        pipelineId = [string]$successPipeline.id
        source = @{
            type = "text"
            location = "Codex ingestion pipeline real success $marker. This text should become multiple chunks."
            fileName = "codex-ingestion-success.txt"
        }
        metadata = @{
            marker = $marker
            kbId = "1"
            docId = "1"
            collectionName = "codex_ingestion_success"
        }
    }
    Assert-ApiOk $response "Execute success task"
    Assert-Equal $response.data.status "completed" "Success task status"
    Assert-True ([int]$response.data.chunkCount -gt 0) "Success task chunkCount should be > 0"
    $response.data
}
if (-not $successTask) { exit 1 }

$successNodes = Test-Step "Verify success task nodes via API" {
    $response = Invoke-Api -Method GET -Path "/ingestion/tasks/$($successTask.taskId)/nodes" -Headers $headers
    Assert-ApiOk $response "Success task nodes"
    $nodes = @($response.data)
    Assert-Equal $nodes.Count 2 "Success node count"
    Assert-Equal $nodes[0].nodeType "parser" "First success node type"
    Assert-Equal $nodes[0].status "success" "Parser status"
    Assert-Equal $nodes[1].nodeType "chunker" "Second success node type"
    Assert-Equal $nodes[1].status "success" "Chunker status"
    Assert-True (-not [string]::IsNullOrWhiteSpace([string]$nodes[0].inputSummary)) "Parser inputSummary missing"
    Assert-True (-not [string]::IsNullOrWhiteSpace([string]$nodes[1].outputSummary)) "Chunker outputSummary missing"
    $nodes
}

$successDb = Test-Step "Verify success task in PostgreSQL" {
    $taskId = [string]$successTask.taskId
    $row = Invoke-DbScalarRow @"
select status,
       chunk_count,
       pipeline_version,
       coalesce(pipeline_snapshot_json::jsonb #>> '{nodes,0,nodeType}', ''),
       coalesce(metadata_json::jsonb #>> '{marker}', '')
from t_ingestion_task
where id = $taskId and deleted = 0;
"@
    Assert-True (-not [string]::IsNullOrWhiteSpace($row)) "Success task DB row missing"
    $parts = $row -split "\|", 5
    Assert-Equal $parts[0] "completed" "DB success status"
    Assert-True ([int]$parts[1] -gt 0) "DB success chunk_count should be > 0"
    Assert-True ([int]$parts[2] -ge 1) "DB pipeline_version should be >= 1"
    Assert-Equal $parts[3] "parser" "DB pipeline snapshot first node"
    Assert-Equal $parts[4] $marker "DB task marker"

    $nodeRow = Invoke-DbScalarRow @"
select count(*),
       count(*) filter (where status = 'success'),
       count(*) filter (where input_summary is not null),
       count(*) filter (where output_summary is not null)
from t_ingestion_task_node
where task_id = $taskId and deleted = 0;
"@
    $nodeParts = $nodeRow -split "\|", 4
    Assert-Equal $nodeParts[0] "2" "DB success node count"
    Assert-Equal $nodeParts[1] "2" "DB success node status count"
    Assert-Equal $nodeParts[2] "2" "DB input summary count"
    Assert-Equal $nodeParts[3] "2" "DB output summary count"
    $row
}

$failingPipeline = Test-Step "Create failing ingestion pipeline" {
    $response = Invoke-Api -Method POST -Path "/ingestion/pipelines" -Headers $headers -Body (New-FailingPipelineBody "$marker-retry")
    Assert-ApiOk $response "Create failing pipeline"
    $response.data
}
if (-not $failingPipeline) { exit 1 }

$failedTask = Test-Step "Execute task that fails at indexer" {
    $response = Invoke-Api -Method POST -Path "/ingestion/tasks" -Headers $headers -Body @{
        pipelineId = [string]$failingPipeline.id
        source = @{
            type = "text"
            location = "Codex ingestion retry source $marker. Parser and chunker should pass before indexer fails."
            fileName = "codex-ingestion-retry.txt"
        }
        metadata = @{
            marker = "$marker-retry"
        }
    }
    Assert-ApiOk $response "Execute failing task"
    Assert-Equal $response.data.status "failed" "Failed task status"
    Assert-True (-not [string]::IsNullOrWhiteSpace([string]$response.data.message)) "Failed task message missing"
    $response.data
}
if (-not $failedTask) { exit 1 }

$failedNodes = Test-Step "Verify failed task node evidence" {
    $response = Invoke-Api -Method GET -Path "/ingestion/tasks/$($failedTask.taskId)/nodes" -Headers $headers
    Assert-ApiOk $response "Failed task nodes"
    $nodes = @($response.data)
    Assert-Equal $nodes.Count 3 "Failed node count"
    Assert-Equal $nodes[0].status "success" "Failed pipeline parser status"
    Assert-Equal $nodes[1].status "success" "Failed pipeline chunker status"
    Assert-Equal $nodes[2].nodeType "indexer" "Failed node type"
    Assert-Equal $nodes[2].status "failed" "Indexer node status"
    Assert-Equal $nodes[2].errorCode "INDEXER_FAILED" "Indexer error code"
    Assert-True (-not [string]::IsNullOrWhiteSpace([string]$nodes[2].errorMessage)) "Indexer error message missing"
    $nodes
}

$retryTask = Test-Step "Retry failed task from indexer node" {
    $response = Invoke-Api -Method POST -Path "/ingestion/tasks/$($failedTask.taskId)/retry" -Headers $headers -Body @{
        fromNodeId = "3"
    }
    Assert-ApiOk $response "Retry failed task"
    Assert-True ([string]$response.data.taskId -ne [string]$failedTask.taskId) "Retry should create a new task"
    Assert-Equal $response.data.status "failed" "Retry task status remains failed without embeddings"
    $response.data
}
if (-not $retryTask) { exit 1 }

$retryNodes = Test-Step "Verify retry node and metadata evidence" {
    $response = Invoke-Api -Method GET -Path "/ingestion/tasks/$($retryTask.taskId)/nodes" -Headers $headers
    Assert-ApiOk $response "Retry task nodes"
    $nodes = @($response.data)
    Assert-Equal $nodes.Count 1 "Retry node count"
    Assert-Equal $nodes[0].nodeId "3" "Retry node id"
    Assert-Equal $nodes[0].retryCount 1 "Retry node retryCount"
    Assert-Equal $nodes[0].errorCode "INDEXER_FAILED" "Retry error code"

    $taskId = [string]$retryTask.taskId
    $row = Invoke-DbScalarRow @"
select coalesce(metadata_json::jsonb #>> '{retryOfTaskId}', ''),
       coalesce(metadata_json::jsonb #>> '{retryFromNodeId}', ''),
       coalesce(metadata_json::jsonb #>> '{restoredNodeIds,0}', ''),
       coalesce(metadata_json::jsonb #>> '{restoredNodeIds,1}', '')
from t_ingestion_task
where id = $taskId and deleted = 0;
"@
    $parts = $row -split "\|", 4
    Assert-Equal $parts[0] $failedTask.taskId "DB retryOfTaskId"
    Assert-Equal $parts[1] "3" "DB retryFromNodeId"
    Assert-Equal $parts[2] "1" "DB restored first node"
    Assert-Equal $parts[3] "2" "DB restored second node"
    $nodes
}

$rollbackTarget = Test-Step "Create temporary knowledge document for rollback" {
    $kbResponse = Invoke-Api -Method POST -Path "/knowledge-base" -Headers $headers -Body @{
        name = "$marker-kb"
        embeddingModel = "nomic-embed-text"
        collectionName = "codexing$suffix"
    }
    Assert-ApiOk $kbResponse "Create rollback KB"
    $kbId = [string]$kbResponse.data
    $tempFile = New-TemporaryFile
    Set-Content -LiteralPath $tempFile.FullName -Encoding UTF8 -Value "Codex rollback target document $marker"
    try {
        $upload = Invoke-MultipartFile -Path "/knowledge-base/$kbId/docs/upload" -FilePath $tempFile.FullName -Token $token
        Assert-ApiOk $upload "Upload rollback document"
        $docId = [string]$upload.data.id
        Assert-True (-not [string]::IsNullOrWhiteSpace($docId)) "Rollback upload did not return doc id"
        [PSCustomObject]@{
            kbId = $kbId
            docId = $docId
            collectionName = "codexing$suffix"
        }
    } finally {
        Remove-Item -LiteralPath $tempFile.FullName -ErrorAction SilentlyContinue
    }
}
if (-not $rollbackTarget) { exit 1 }

$rollbackTask = Test-Step "Execute rollback-target ingestion task" {
    $response = Invoke-Api -Method POST -Path "/ingestion/tasks" -Headers $headers -Body @{
        pipelineId = [string]$successPipeline.id
        source = @{
            type = "text"
            location = "Codex rollback ingestion task $marker"
            fileName = "codex-ingestion-rollback.txt"
        }
        metadata = @{
            marker = "$marker-rollback"
            kbId = [string]$rollbackTarget.kbId
            docId = [string]$rollbackTarget.docId
            collectionName = [string]$rollbackTarget.collectionName
        }
    }
    Assert-ApiOk $response "Execute rollback-target task"
    Assert-Equal $response.data.status "completed" "Rollback-target task status"
    $response.data
}
if (-not $rollbackTask) { exit 1 }

$rollbackResult = Test-Step "Rollback ingestion task and verify compensation" {
    $response = Invoke-Api -Method POST -Path "/ingestion/tasks/$($rollbackTask.taskId)/rollback" -Headers $headers
    Assert-ApiOk $response "Rollback ingestion task"
    Assert-Equal $response.data.status "rolled_back" "Rollback result status"
    Assert-Equal $response.data.kbId $rollbackTarget.kbId "Rollback result kbId"
    Assert-Equal $response.data.docId $rollbackTarget.docId "Rollback result docId"

    $taskId = [string]$rollbackTask.taskId
    $row = Invoke-DbScalarRow @"
select status,
       coalesce(metadata_json::jsonb #>> '{rollbackStatus}', ''),
       coalesce(metadata_json::jsonb #>> '{rollbackDocId}', ''),
       coalesce(metadata_json::jsonb #>> '{rollbackKbId}', '')
from t_ingestion_task
where id = $taskId and deleted = 0;
"@
    $parts = $row -split "\|", 4
    Assert-Equal $parts[0] "rolled_back" "DB rollback task status"
    Assert-Equal $parts[1] "rolled_back" "DB rollback metadata status"
    Assert-Equal $parts[2] $rollbackTarget.docId "DB rollback doc id"
    Assert-Equal $parts[3] $rollbackTarget.kbId "DB rollback kb id"

    $docRow = Invoke-DbScalarRow "select deleted from t_knowledge_document where id = $($rollbackTarget.docId);"
    Assert-Equal $docRow "1" "Rollback target document deleted flag"
    $response.data
}

Write-Host "`nSummary: $passed / $total passed, $failed failed" -ForegroundColor Cyan
Write-Host "Marker: $marker"
Write-Host "Success pipeline: $($successPipeline.id)"
Write-Host "Success task: $($successTask.taskId)"
Write-Host "Failed task: $($failedTask.taskId)"
Write-Host "Retry task: $($retryTask.taskId)"
Write-Host "Rollback task: $($rollbackTask.taskId)"
Write-Host "Rollback doc: $($rollbackTarget.docId)"

if ($failed -gt 0) {
    exit 1
}
