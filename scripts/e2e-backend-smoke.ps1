param(
    [string]$BaseUrl = "http://localhost:9090",
    [string]$Username = "admin",
    [string]$Password = "admin123",
    [string]$DockerContainerName = "seahorse-backend",
    [ValidateSet("full-compose", "none")]
    [string]$RuntimeProfile = "full-compose"
)

$ErrorActionPreference = "Stop"
$results = New-Object System.Collections.Generic.List[object]

function Add-Result {
    param(
        [string]$Name,
        [string]$Status,
        [string]$Detail = ""
    )
    $results.Add([PSCustomObject]@{ Name = $Name; Status = $Status; Detail = $Detail })
    $color = if ($Status -eq "PASS") { "Green" } elseif ($Status -eq "SKIP") { "Yellow" } else { "Red" }
    Write-Host "[$Status] $Name $Detail" -ForegroundColor $color
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
        $bodyText = if ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Depth 20 -Compress }
    }

    $tempBodyFile = $null
    $args = @("-sS", "-w", "`n%{http_code}", "-X", $Method, "$BaseUrl$Path")
    if ($bodyText) {
        $tempBodyFile = New-TemporaryFile
        Set-Content -Path $tempBodyFile.FullName -Value $bodyText -Encoding UTF8 -NoNewline
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
            Remove-Item -Path $tempBodyFile.FullName -ErrorAction SilentlyContinue
        }
    }
    if ($exitCode -ne 0) {
        throw "curl exited with $exitCode for $Method $Path"
    }
    $lines = @($raw)
    if ($lines.Count -eq 0) {
        throw "empty curl output for $Method $Path"
    }
    $statusLine = $lines[-1]
    $status = [int]$statusLine
    $content = if ($lines.Count -gt 1) { ($lines[0..($lines.Count - 2)] -join "`n") } else { "" }
    if ($status -ne $ExpectedStatus) {
        throw "Expected HTTP $ExpectedStatus but got $status for $Method $Path body=$content"
    }
    if ([string]::IsNullOrWhiteSpace($content)) {
        return $null
    }
    return $content | ConvertFrom-Json
}

function Invoke-Text {
    param(
        [string]$Method,
        [string]$Path,
        [hashtable]$Headers = @{},
        [int]$ExpectedStatus = 200
    )

    $args = @("-sS", "-w", "`n%{http_code}", "-X", $Method, "$BaseUrl$Path")
    foreach ($key in $Headers.Keys) {
        $args += @("-H", "${key}: $($Headers[$key])")
    }

    $raw = & curl.exe @args
    $exitCode = $LASTEXITCODE
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
    return $content
}

function Invoke-MultipartFile {
    param(
        [string]$Path,
        [string]$FilePath,
        [hashtable]$Headers = @{}
    )

    $args = @("-sS", "-w", "`n%{http_code}", "-X", "POST", "$BaseUrl$Path", "-F", "file=@$FilePath")
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

function Assert-Code {
    param(
        [string]$Name,
        [object]$Response,
        [string]$ExpectedCode = "0"
    )
    if ($null -eq $Response) {
        throw "$Name returned empty response"
    }
    if ([string]$Response.code -ne $ExpectedCode) {
        throw "$Name expected code $ExpectedCode but got $($Response.code): $($Response.message)"
    }
}

function Assert-NonEmptyPageRecords {
    param(
        [string]$Name,
        [object]$Response
    )

    if ($null -eq $Response.data -or $null -eq $Response.data.records) {
        throw "$Name response missing data.records"
    }
    $records = @($Response.data.records)
    if ($records.Count -eq 0) {
        throw "$Name returned no records"
    }
}

function Assert-NonEmptyDataArray {
    param(
        [string]$Name,
        [object]$Response
    )

    if ($null -eq $Response.data) {
        throw "$Name response missing data"
    }
    $items = @($Response.data)
    if ($items.Count -eq 0) {
        throw "$Name returned no records"
    }
}

function Assert-ProfileFactsContainValue {
    param(
        [object]$Response,
        [string]$ExpectedValue
    )

    Assert-Code "Profile facts" $Response
    if ([string]::IsNullOrWhiteSpace($ExpectedValue)) {
        throw "Expected profile fact value must not be blank"
    }
    if ($null -eq $Response.data) {
        throw "Profile facts response missing data"
    }
    $match = @($Response.data | Where-Object {
            [string]$_.valueText -eq $ExpectedValue
        })[0]
    if ($null -eq $match) {
        $actualValues = @($Response.data | ForEach-Object { [string]$_.valueText }) -join ", "
        throw "Profile facts did not include valueText=$ExpectedValue; actual=[$actualValues]"
    }
    return $match
}

function Find-ProfileFactForValue {
    param(
        [string]$UserId,
        [string]$TenantId,
        [string]$ExpectedValue,
        [hashtable]$Headers = @{},
        [int]$Attempts = 75,
        [int]$DelaySeconds = 1
    )

    $encodedUserId = [System.Uri]::EscapeDataString($UserId)
    $encodedTenantId = [System.Uri]::EscapeDataString($TenantId)
    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        $response = Invoke-Json -Method GET -Path "/memories/profile-facts?userId=$encodedUserId&tenantId=$encodedTenantId&limit=20" -Headers $Headers
        try {
            [void](Assert-ProfileFactsContainValue -Response $response -ExpectedValue $ExpectedValue)
            return $response
        } catch {
            if ($attempt -ge $Attempts) {
                throw
            }
        }
        Start-Sleep -Seconds $DelaySeconds
    }

    throw "Profile facts did not include valueText=$ExpectedValue after $Attempts attempts"
}

function Wait-ForNonEmptyPageRecords {
    param(
        [string]$Name,
        [string]$Path,
        [hashtable]$Headers = @{},
        [int]$Attempts = 30,
        [int]$DelaySeconds = 1
    )

    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        $response = Invoke-Json -Method GET -Path $Path -Headers $Headers
        Assert-Code $Name $response
        if ($null -ne $response.data -and $null -ne $response.data.records) {
            $records = @($response.data.records)
            if ($records.Count -gt 0) {
                return $response
            }
        }
        if ($attempt -lt $Attempts) {
            Start-Sleep -Seconds $DelaySeconds
        }
    }

    throw "$Name returned no records after $Attempts attempts"
}

function Assert-RagTraceRunMatchesConversation {
    param(
        [object]$Run,
        [string]$ConversationId
    )

    if ($null -eq $Run) {
        throw "RAG trace run was not found"
    }
    $actualConversationId = [string]$Run.conversationId
    if ($actualConversationId -ne $ConversationId) {
        throw "RAG trace run conversationId=$actualConversationId, expected $ConversationId"
    }
    if ([string]::IsNullOrWhiteSpace([string]$Run.traceId)) {
        throw "RAG trace run did not return traceId"
    }
}

function Find-RagTraceRunForConversation {
    param(
        [string]$ConversationId,
        [hashtable]$Headers = @{},
        [int]$Attempts = 30,
        [int]$DelaySeconds = 1
    )

    $encodedConversationId = [System.Uri]::EscapeDataString($ConversationId)
    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        $response = Invoke-Json -Method GET -Path "/rag/traces/runs?current=1&size=10&conversationId=$encodedConversationId" -Headers $Headers
        Assert-Code "RAG trace run page" $response
        if ($null -ne $response.data -and $null -ne $response.data.records) {
            $run = @($response.data.records | Where-Object { [string]$_.conversationId -eq $ConversationId })[0]
            if ($null -ne $run) {
                Assert-RagTraceRunMatchesConversation -Run $run -ConversationId $ConversationId
                return $run
            }
        }
        if ($attempt -lt $Attempts) {
            Start-Sleep -Seconds $DelaySeconds
        }
    }

    throw "RAG trace run page returned no records for conversationId=$ConversationId after $Attempts attempts"
}

function Assert-RetrievalTraceNodes {
    param(
        [object]$Response
    )

    Assert-Code "RAG trace nodes" $Response
    Assert-NonEmptyDataArray "RAG trace nodes" $Response
    $retrievalNodes = @($Response.data | Where-Object {
            $_.nodeType -match "RETRIEVAL" -or $_.nodeName -match "retrieval"
        })
    if ($retrievalNodes.Count -eq 0) {
        throw "RAG trace nodes did not include retrieval evidence"
    }
}

function Assert-RagTraceNodesContainHit {
    param(
        [object]$Response,
        [string]$ExpectedText
    )

    Assert-Code "RAG trace nodes" $Response
    Assert-NonEmptyDataArray "RAG trace nodes" $Response
    $retrievalNodes = @($Response.data | Where-Object {
            $_.nodeType -match "RETRIEVAL" -or $_.nodeName -match "retrieval|search-channel"
        })
    foreach ($node in $retrievalNodes) {
        $extraText = if ($node.extraData -is [string]) {
            [string]$node.extraData
        } elseif ($null -ne $node.extraData) {
            $node.extraData | ConvertTo-Json -Depth 20 -Compress
        } else {
            ""
        }
        if ([string]::IsNullOrWhiteSpace($extraText) -or -not $extraText.Contains($ExpectedText)) {
            continue
        }
        $hitCount = 0
        try {
            $extra = $extraText | ConvertFrom-Json
            $hitCount = [int]$extra.hitCount
        } catch {
            if ($extraText -match '"hitCount"\s*:\s*([1-9][0-9]*)') {
                $hitCount = [int]$Matches[1]
            }
        }
        if ($hitCount -gt 0) {
            return
        }
    }

    throw "RAG trace nodes did not include retrieval hit evidence for '$ExpectedText'"
}

function Convert-EnvListToMap {
    param(
        [object[]]$EnvList
    )

    $map = @{}
    foreach ($entry in @($EnvList)) {
        $text = [string]$entry
        $separator = $text.IndexOf("=")
        if ($separator -le 0) {
            continue
        }
        $name = $text.Substring(0, $separator)
        $value = $text.Substring($separator + 1)
        $map[$name] = $value
    }
    return $map
}

function Assert-FullComposeRuntime {
    param(
        [string]$ContainerName
    )

    $raw = & docker.exe inspect $ContainerName 2>&1
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        throw "docker inspect failed for ${ContainerName}: $raw"
    }

    $inspect = ($raw -join "`n") | ConvertFrom-Json
    $container = @($inspect)[0]
    if ($null -eq $container) {
        throw "docker inspect returned no container for $ContainerName"
    }

    $configFiles = [string]$container.Config.Labels.'com.docker.compose.project.config_files'
    if ([string]::IsNullOrWhiteSpace($configFiles) -or $configFiles -notmatch 'docker-compose\.full\.yml') {
        throw "$ContainerName was not created from docker-compose.full.yml; config_files=$configFiles"
    }

    $envMap = Convert-EnvListToMap -EnvList $container.Config.Env
    $required = [ordered]@{
        SEAHORSE_AGENT_ADAPTERS_VECTOR_TYPE = "milvus"
        SEAHORSE_AGENT_ADAPTERS_CACHE_TYPE = "redis"
        SEAHORSE_AGENT_ADAPTERS_MQ_TYPE = "pulsar"
        SEAHORSE_AGENT_ADAPTERS_OBSERVATION_TYPE = "micrometer"
    }
    foreach ($name in $required.Keys) {
        $actual = [string]$envMap[$name]
        $expected = [string]$required[$name]
        if ($actual -ne $expected) {
            throw "$ContainerName has $name=$actual, expected $expected for full-compose smoke"
        }
    }

    return "configFiles=$configFiles vector=$($envMap['SEAHORSE_AGENT_ADAPTERS_VECTOR_TYPE']) cache=$($envMap['SEAHORSE_AGENT_ADAPTERS_CACHE_TYPE']) mq=$($envMap['SEAHORSE_AGENT_ADAPTERS_MQ_TYPE'])"
}

function Assert-ReadinessPassed {
    param(
        [object[]]$Checks,
        [string]$Id,
        [string]$ForbiddenPattern
    )

    $check = @($Checks | Where-Object { $_.id -eq $Id })[0]
    if ($null -eq $check) {
        throw "readiness missing check $Id"
    }
    if ([string]$check.status -ne "passed") {
        throw "readiness $Id status=$($check.status) message=$($check.message)"
    }
    if (-not [string]::IsNullOrWhiteSpace($ForbiddenPattern) -and [string]$check.message -match $ForbiddenPattern) {
        throw "readiness $Id still reports fallback adapter: $($check.message)"
    }
}

function Assert-FullAdapterReadiness {
    param(
        [object]$Response
    )

    $data = if ($null -ne $Response.data -and $null -ne $Response.data.checks) { $Response.data } else { $Response }
    if ($null -eq $data -or $null -eq $data.checks) {
        throw "readiness summary response missing checks"
    }

    $checks = @($data.checks)
    Assert-ReadinessPassed -Checks $checks -Id "vector.store" -ForbiddenPattern "noop|unknown"
    Assert-ReadinessPassed -Checks $checks -Id "search.keyword" -ForbiddenPattern "不可用|unavailable|noop|unknown"
    Assert-ReadinessPassed -Checks $checks -Id "cache" -ForbiddenPattern "\blocal\b|unknown"
    Assert-ReadinessPassed -Checks $checks -Id "mq" -ForbiddenPattern "\bdirect\b|unknown"
}

try {
    $health = Invoke-Json -Method GET -Path "/actuator/health"
    if ($health.status -ne "UP") { throw "health status is $($health.status)" }
    Add-Result "Actuator health" "PASS" "status=UP"

    if ($RuntimeProfile -eq "full-compose") {
        $runtime = Assert-FullComposeRuntime -ContainerName $DockerContainerName
        Add-Result "Full compose runtime" "PASS" $runtime
    }

    $login = Invoke-Json -Method POST -Path "/auth/login" -Body @{ username = $Username; password = $Password }
    Assert-Code "Login" $login
    $token = $login.data.token
    $userId = [string]$login.data.userId
    if ([string]::IsNullOrWhiteSpace($token) -or [string]::IsNullOrWhiteSpace($userId)) {
        throw "login did not return token/userId"
    }
    $authHeaders = @{ Authorization = "Bearer $token"; "X-User-Id" = $userId }
    Add-Result "Auth login" "PASS" "userId=$userId role=$($login.data.role)"

    $validation = Invoke-Json -Method POST -Path "/auth/login" -Body @{ username = $Username; password = "bad" } -ExpectedStatus 400
    if ($validation.code -ne "VALIDATION_ERROR" -and $validation.code -ne "INVALID_ARGUMENT") {
        throw "unexpected bad login code $($validation.code)"
    }
    Add-Result "Structured login error" "PASS" "code=$($validation.code)"

    $me = Invoke-Json -Method GET -Path "/user/me" -Headers $authHeaders
    Assert-Code "Current user" $me
    Add-Result "Current user" "PASS" "username=$($me.data.username)"

    $features = Invoke-Json -Method GET -Path "/api/features" -Headers $authHeaders
    if ([string]::IsNullOrWhiteSpace($features.productMode) -or $null -eq $features.features) {
        throw "Feature flags response missing productMode/features"
    }
    Add-Result "Feature flags" "PASS" "productMode=$($features.productMode)"

    if ($RuntimeProfile -eq "full-compose") {
        $readiness = Invoke-Json -Method GET -Path "/readiness/summary" -Headers $authHeaders
        Assert-FullAdapterReadiness $readiness
        Add-Result "Full adapter readiness" "PASS" "mode=$($readiness.mode)"
    }

    $users = Invoke-Json -Method GET -Path "/users?current=1&size=5" -Headers $authHeaders
    Assert-Code "User page" $users
    Add-Result "User page" "PASS"

    $quota = Invoke-Json -Method GET -Path "/api/me/quota-summary" -Headers $authHeaders
    Assert-Code "Quota summary" $quota
    Add-Result "Quota summary" "PASS"

    $notifications = Invoke-Json -Method GET -Path "/api/notifications?page=1&size=10" -Headers $authHeaders
    Assert-Code "Notification list" $notifications
    $unread = Invoke-Json -Method GET -Path "/api/notifications/unread-count" -Headers $authHeaders
    Assert-Code "Notification unread count" $unread
    $markAll = Invoke-Json -Method POST -Path "/api/notifications/mark-all-read" -Headers $authHeaders
    Assert-Code "Notification mark all read" $markAll
    Add-Result "Notification center" "PASS" "unread=$($unread.data)"

    $export = Invoke-Json -Method POST -Path "/api/export/tasks" -Headers $authHeaders -Body @{
        exportType = "AUDIT_LOG"
        fileName = "e2e-audit-export.json"
        parameters = "{}"
    }
    Assert-Code "Create export task" $export
    $taskId = $export.data.taskId
    if ($null -eq $taskId) { throw "export task did not return taskId" }
    $exportTask = Invoke-Json -Method GET -Path "/api/export/tasks/$taskId" -Headers $authHeaders
    Assert-Code "Get export task" $exportTask
    Add-Result "Data export task" "PASS" "taskId=$taskId"

    $kbList = Invoke-Json -Method GET -Path "/knowledge-base?current=1&size=10" -Headers $authHeaders
    Assert-Code "Knowledge base page" $kbList
    $strategy = Invoke-Json -Method GET -Path "/knowledge-base/chunk-strategies" -Headers $authHeaders
    Assert-Code "Knowledge chunk strategies" $strategy
    $suffix = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $memoryFactValue = "smoke-profile-$suffix"
    $kb = Invoke-Json -Method POST -Path "/knowledge-base" -Headers $authHeaders -Body @{
        name = "e2e-kb-$suffix"
        embeddingModel = "nomic-embed-text"
        collectionName = "e2ekb$suffix"
    }
    Assert-Code "Create knowledge base" $kb
    $kbId = $kb.data
    $kbDetail = Invoke-Json -Method GET -Path "/knowledge-base/$kbId" -Headers $authHeaders
    Assert-Code "Get knowledge base" $kbDetail
    Add-Result "Knowledge base CRUD smoke" "PASS" "kbId=$kbId"

    $docFile = New-TemporaryFile
    Set-Content -LiteralPath $docFile.FullName -Encoding UTF8 -Value @"
# Seahorse smoke document

Seahorse Agent full compose smoke checks use nomic-embed-text for local embedding.
The expected embedding dimension is 768.
"@
    try {
        $upload = Invoke-MultipartFile -Path "/knowledge-base/$kbId/docs/upload" -FilePath $docFile.FullName -Headers $authHeaders
        Assert-Code "Upload knowledge document" $upload
        $docId = [string]$upload.data.id
        if ([string]::IsNullOrWhiteSpace($docId)) {
            throw "upload did not return document id"
        }

        $docDetail = Invoke-Json -Method GET -Path "/knowledge-base/docs/$docId" -Headers $authHeaders
        Assert-Code "Get knowledge document" $docDetail
        $chunkStart = Invoke-Json -Method POST -Path "/knowledge-base/docs/$docId/chunk" -Headers $authHeaders
        Assert-Code "Start knowledge document chunk" $chunkStart
        $chunkLogs = Wait-ForNonEmptyPageRecords `
            -Name "Knowledge document chunk logs" `
            -Path "/knowledge-base/docs/$docId/chunk-logs?current=1&size=10" `
            -Headers $authHeaders
        Assert-NonEmptyPageRecords "Knowledge document chunk logs" $chunkLogs
        Add-Result "Knowledge document upload/chunk smoke" "PASS" "docId=$docId"
    } finally {
        Remove-Item -LiteralPath $docFile.FullName -ErrorAction SilentlyContinue
    }

    $conversationId = "smoke-$suffix"
    $chatQuestion = [System.Uri]::EscapeDataString("What embedding model and dimension are used in the smoke document?")
    $chat = Invoke-Text -Method GET -Path "/rag/v3/chat?question=$chatQuestion&conversationId=$conversationId&userId=$userId&knowledgeBaseIds=$kbId" -Headers $authHeaders
    if ([string]::IsNullOrWhiteSpace($chat)) {
        throw "RAG SSE chat returned empty stream"
    }
    Add-Result "RAG SSE chat smoke" "PASS" "conversationId=$conversationId"

    $traceRun = Find-RagTraceRunForConversation -ConversationId $conversationId -Headers $authHeaders
    $traceId = [string]$traceRun.traceId
    $traceNodes = Invoke-Json -Method GET -Path "/rag/traces/runs/$traceId/nodes" -Headers $authHeaders
    Assert-RetrievalTraceNodes $traceNodes
    Assert-RagTraceNodesContainHit -Response $traceNodes -ExpectedText "nomic-embed-text"
    Add-Result "RAG trace API smoke" "PASS" "traceId=$traceId conversationId=$conversationId"

    $memoryConversationId = "smoke-memory-$suffix"
    $memoryQuestion = [System.Uri]::EscapeDataString("I prefer $memoryFactValue")
    $memoryChat = Invoke-Text -Method GET -Path "/rag/v3/chat?question=$memoryQuestion&conversationId=$memoryConversationId&userId=$userId" -Headers $authHeaders
    if ([string]::IsNullOrWhiteSpace($memoryChat)) {
        throw "Memory SSE chat returned empty stream"
    }

    $maintenance = Invoke-Json -Method POST -Path "/memories/maintenance/run?reason=smoke-check&compaction=true&alias=true&gc=true" -Headers $authHeaders
    Assert-Code "Memory maintenance run" $maintenance
    $readiness = Invoke-Json -Method GET -Path "/memories/readiness?userId=$userId&tenantId=default" -Headers $authHeaders
    Assert-Code "Memory readiness" $readiness
    $profileFacts = Find-ProfileFactForValue -UserId $userId -TenantId "default" -ExpectedValue $memoryFactValue -Headers $authHeaders
    [void](Assert-ProfileFactsContainValue -Response $profileFacts -ExpectedValue $memoryFactValue)
    Add-Result "Memory/profile smoke" "PASS" "valueText=$memoryFactValue facts=$(@($profileFacts.data).Count)"

    $agentList = Invoke-Json -Method GET -Path "/api/agents?current=1&size=10" -Headers $authHeaders
    Assert-Code "Agent list" $agentList
    $tools = Invoke-Json -Method GET -Path "/api/tools?current=1&size=10" -Headers $authHeaders
    Assert-Code "Tool catalog" $tools
    $skills = Invoke-Json -Method GET -Path "/api/skills?current=1&size=10" -Headers $authHeaders
    Assert-Code "Skill catalog" $skills
    Add-Result "Agent/tool/skill catalog" "PASS"

    $audit = Invoke-Json -Method GET -Path "/api/tool-invocations?current=1&size=10" -Headers $authHeaders
    Assert-Code "Tool invocation audit" $audit
    Add-Result "Audit API" "PASS"

    $metadata = Invoke-Json -Method GET -Path "/metadata-extraction/results?tenantId=default&current=1&size=10" -Headers $authHeaders
    Assert-Code "Metadata extraction results" $metadata
    Add-Result "Metadata governance API" "PASS"

    $sre = Invoke-Json -Method GET -Path "/api/sre/health" -Headers $authHeaders
    Assert-Code "SRE health" $sre
    Add-Result "SRE health" "PASS"
} catch {
    $detail = $_.Exception.Message
    if ($_.InvocationInfo) {
        $detail = "$detail at line $($_.InvocationInfo.ScriptLineNumber): $($_.InvocationInfo.Line.Trim())"
    }
    if ($_.ScriptStackTrace) {
        $detail = "$detail stack=$($_.ScriptStackTrace)"
    }
    Add-Result "E2E backend smoke" "FAIL" $detail
}

$failed = @($results | Where-Object { $_.Status -eq "FAIL" })
Write-Host ""
Write-Host "Backend E2E summary: $($results.Count) checks, $($failed.Count) failed"
$results | Format-Table -AutoSize
if ($failed.Count -gt 0) {
    exit 1
}
