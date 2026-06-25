param(
    [string]$BaseUrl = "http://127.0.0.1:9090",
    [string]$Username = "admin",
    [string]$Password = "admin123",
    [string]$PostgresContainer = "seahorse-postgres",
    [string]$PostgresUser = "seahorse",
    [string]$PostgresDatabase = "seahorse",
    [string]$PulsarContainer = "seahorse-pulsar-broker",
    [string]$BackendContainer = "seahorse-backend",
    [string]$Topic = "persistent://seahorse-agent/ai/knowledge-document-chunk",
    [string]$Subscription = "seahorse-document-chunk-consumer"
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

function Invoke-Json {
    param(
        [string]$Method,
        [string]$Path,
        [object]$Body = $null,
        [hashtable]$Headers = @{},
        [int]$ExpectedStatus = 200
    )

    $bodyText = $null
    if ($null -ne $Body) {
        $bodyText = if ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Depth 30 -Compress }
    }

    $tempBodyFile = $null
    $args = @("-sS", "-w", "`n%{http_code}", "-X", $Method, "$BaseUrl$Path")
    if ($bodyText) {
        $tempBodyFile = New-TemporaryFile
        $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
        [System.IO.File]::WriteAllText($tempBodyFile.FullName, $bodyText, $utf8NoBom)
        $args += @("-H", "Content-Type: application/json", "--data-binary", "@$($tempBodyFile.FullName)")
    }
    foreach ($key in $Headers.Keys) {
        $args += @("-H", "${key}: $($Headers[$key])")
    }

    try {
        $raw = & curl.exe @args
        $exitCode = $LASTEXITCODE
    } finally {
        if ($null -ne $tempBodyFile) {
            Remove-Item -LiteralPath $tempBodyFile.FullName -ErrorAction SilentlyContinue
        }
    }
    if ($exitCode -ne 0) {
        throw "curl exited with $exitCode for $Method $Path"
    }

    $lines = @($raw)
    if ($lines.Count -eq 0) {
        throw "empty curl output for $Method $Path"
    }
    $status = [int]$lines[-1]
    $content = if ($lines.Count -gt 1) { ($lines[0..($lines.Count - 2)] -join "`n") } else { "" }
    if ($status -ne $ExpectedStatus) {
        throw "Expected HTTP $ExpectedStatus but got $status for $Method $Path body=$content"
    }
    if ([string]::IsNullOrWhiteSpace($content)) {
        return $null
    }
    return $content | ConvertFrom-Json
}

function Invoke-MultipartFile {
    param(
        [string]$Path,
        [string]$FilePath,
        [hashtable]$Headers = @{}
    )

    $args = @("-sS", "-w", "`n%{http_code}", "-X", "POST", "$BaseUrl$Path", "-F", "file=@$FilePath;type=text/plain")
    foreach ($key in $Headers.Keys) {
        $args += @("-H", "${key}: $($Headers[$key])")
    }

    $raw = & curl.exe @args
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

function Invoke-PulsarStats {
    $raw = & docker.exe exec $PulsarContainer bin/pulsar-admin --admin-url http://localhost:8080 topics stats $Topic --get-precise-backlog
    if ($LASTEXITCODE -ne 0) {
        throw "pulsar-admin topics stats failed"
    }
    return ($raw -join "`n") | ConvertFrom-Json
}

function Get-SubscriptionStats {
    param([object]$Stats)
    $property = $Stats.subscriptions.PSObject.Properties[$Subscription]
    if ($null -eq $property) {
        throw "Subscription '$Subscription' was not found in Pulsar stats"
    }
    return $property.Value
}

function Wait-ForDocumentSuccess {
    param(
        [string]$DocId,
        [string]$Marker,
        [int]$Attempts = 60
    )

    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        $row = Invoke-DbScalarRow @"
select d.status,
       coalesce(d.chunk_count, 0),
       count(c.id) filter (where c.deleted = 0),
       count(c.id) filter (where c.deleted = 0 and c.content like '%$Marker%')
from t_knowledge_document d
left join t_knowledge_chunk c on c.doc_id = d.id
where d.id = $DocId
group by d.status, d.chunk_count;
"@
        if ($row) {
            $parts = $row -split "\|", 4
            if ($parts.Count -eq 4 -and $parts[0] -eq "success" -and [int]$parts[1] -gt 0 -and [int]$parts[2] -gt 0 -and [int]$parts[3] -gt 0) {
                return $row
            }
        }
        Start-Sleep -Seconds 2
    }
    throw "Document $DocId did not reach success with marker $Marker"
}

function Wait-ForPulsarCounters {
    param(
        [long]$BeforeIn,
        [long]$BeforeOut,
        [int]$Attempts = 30
    )

    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        $stats = Invoke-PulsarStats
        $sub = Get-SubscriptionStats -Stats $stats
        if ([long]$stats.msgInCounter -gt $BeforeIn -and [long]$sub.msgOutCounter -gt $BeforeOut -and [long]$sub.msgBacklog -eq 0 -and [long]$sub.unackedMessages -eq 0) {
            return [PSCustomObject]@{
                Stats = $stats
                Subscription = $sub
            }
        }
        Start-Sleep -Seconds 2
    }
    throw "Pulsar counters did not advance from msgIn=$BeforeIn msgOut=$BeforeOut"
}

function Wait-ForBackendLog {
    param(
        [string]$DocId,
        [int]$Attempts = 20
    )

    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        $previousErrorActionPreference = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        try {
            $logs = & docker.exe logs --tail 2000 $BackendContainer 2>&1
            $exitCode = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $previousErrorActionPreference
        }
        if ($exitCode -ne 0) {
            throw "docker logs failed for $BackendContainer"
        }
        $matched = @($logs | Select-String -SimpleMatch "Document chunk processing completed: docId=$DocId")
        if ($matched.Count -gt 0) {
            return "$($matched[-1])"
        }
        Start-Sleep -Seconds 1
    }
    throw "Backend logs did not contain document chunk completion for docId=$DocId"
}

$tempFile = $null
$kbId = ""
$docId = ""
$marker = "CODX_PULSAR_MQ_$(Get-Date -Format yyyyMMddHHmmssfff)"

try {
    $envProbe = Test-Step "Verify backend is configured for Pulsar" {
        $envRows = & docker.exe exec $BackendContainer printenv
        if ($LASTEXITCODE -ne 0) {
            throw "docker exec printenv failed"
        }
        $mqType = @($envRows | Where-Object { $_ -eq "SEAHORSE_AGENT_ADAPTERS_MQ_TYPE=pulsar" })
        $mqUrl = @($envRows | Where-Object { $_ -like "SEAHORSE_AGENT_ADAPTERS_MQ_PULSAR_SERVICE_URL=*" })
        if ($mqType.Count -eq 0) {
            throw "Backend is not configured with SEAHORSE_AGENT_ADAPTERS_MQ_TYPE=pulsar"
        }
        if ($mqUrl.Count -eq 0) {
            throw "Backend is missing Pulsar service URL"
        }
        "$($mqType[0]); $($mqUrl[0])" | Write-Host
        @{ mqType = $mqType[0]; mqUrl = $mqUrl[0] }
    }
    if (-not $envProbe) { exit 1 }

    $before = Test-Step "Capture Pulsar counters before chunk" {
        $stats = Invoke-PulsarStats
        $sub = Get-SubscriptionStats -Stats $stats
        if ($sub.consumers.Count -lt 1) {
            throw "Subscription $Subscription has no active consumers"
        }
        [PSCustomObject]@{
            msgInCounter = [long]$stats.msgInCounter
            msgOutCounter = [long]$sub.msgOutCounter
            msgBacklog = [long]$sub.msgBacklog
            unackedMessages = [long]$sub.unackedMessages
        } | ConvertTo-Json -Compress | Write-Host
        [PSCustomObject]@{
            msgInCounter = [long]$stats.msgInCounter
            msgOutCounter = [long]$sub.msgOutCounter
        }
    }
    if (-not $before) { exit 1 }

    $login = Test-Step "Login" {
        $response = Invoke-Json -Method POST -Path "/auth/login" -Body @{
            username = $Username
            password = $Password
        }
        Assert-ApiOk $response "Login"
        if (-not $response.data.token) {
            throw "Login response did not include token"
        }
        $response
    }
    if (-not $login) { exit 1 }
    $headers = @{ Authorization = "Bearer $($login.data.token)" }

    $kb = Test-Step "Create knowledge base" {
        $suffix = (Get-Date -Format yyyyMMddHHmmssfff)
        $response = Invoke-Json -Method POST -Path "/knowledge-base" -Headers $headers -Body @{
            name = "pulsar-mq-smoke-$suffix"
            embeddingModel = "nomic-embed-text"
            collectionName = "pulsarmq$suffix"
        }
        Assert-ApiOk $response "Create knowledge base"
        if (-not $response.data) {
            throw "Create knowledge base did not return an id"
        }
        "$($response.data)"
    }
    if (-not $kb) { exit 1 }
    $kbId = "$kb"

    $tempFile = New-TemporaryFile
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($tempFile.FullName, @"
Seahorse Pulsar MQ smoke marker $marker.
This document proves that a real Pulsar message is produced, consumed, acknowledged, and materialized as knowledge chunks.
The expected marker is $marker.
"@, $utf8NoBom)

    $upload = Test-Step "Upload knowledge document" {
        $response = Invoke-MultipartFile -Path "/knowledge-base/$kbId/docs/upload" -FilePath $tempFile.FullName -Headers $headers
        Assert-ApiOk $response "Upload knowledge document"
        if (-not $response.data.id) {
            throw "Upload did not return document id"
        }
        "$($response.data.id)"
    }
    if (-not $upload) { exit 1 }
    $docId = "$upload"

    Test-Step "Start document chunk through API" {
        $response = Invoke-Json -Method POST -Path "/knowledge-base/docs/$docId/chunk" -Headers $headers
        Assert-ApiOk $response "Start document chunk"
    } | Out-Null

    $dbEvidence = Test-Step "Verify PostgreSQL document success and marker chunk" {
        $row = Wait-ForDocumentSuccess -DocId $docId -Marker $marker
        $row | Write-Host
        $row
    }
    if (-not $dbEvidence) { exit 1 }

    $after = Test-Step "Verify Pulsar publish/consume/ack counters advanced" {
        $result = Wait-ForPulsarCounters -BeforeIn $before.msgInCounter -BeforeOut $before.msgOutCounter
        $sub = $result.Subscription
        [PSCustomObject]@{
            msgInCounter = [long]$result.Stats.msgInCounter
            msgOutCounter = [long]$sub.msgOutCounter
            msgBacklog = [long]$sub.msgBacklog
            unackedMessages = [long]$sub.unackedMessages
            lastAckedTimestamp = [long]$sub.lastAckedTimestamp
        } | ConvertTo-Json -Compress | Write-Host
        $result
    }
    if (-not $after) { exit 1 }

    Test-Step "Verify backend consumed the same document id" {
        $line = Wait-ForBackendLog -DocId $docId
        $line | Write-Host
    } | Out-Null

    Write-Host "`nSummary: $passed / $total passed, $failed failed" -ForegroundColor Cyan
    Write-Host "Marker: $marker"
    Write-Host "Knowledge base ID: $kbId"
    Write-Host "Document ID: $docId"
    Write-Host "Pulsar topic: $Topic"
    Write-Host "Pulsar subscription: $Subscription"
    Write-Host "Pulsar before msgIn/msgOut: $($before.msgInCounter)/$($before.msgOutCounter)"
    Write-Host "Pulsar after msgIn/msgOut: $($after.Stats.msgInCounter)/$($after.Subscription.msgOutCounter)"
} finally {
    if ($null -ne $tempFile -and (Test-Path -LiteralPath $tempFile.FullName)) {
        Remove-Item -LiteralPath $tempFile.FullName -ErrorAction SilentlyContinue
    }
}

if ($failed -gt 0) {
    exit 1
}
