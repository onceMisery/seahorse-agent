param(
    [string]$BaseUrl = "http://127.0.0.1:9090",
    [string]$Username = "admin",
    [string]$Password = "admin123",
    [long]$AgentScopeRunProfileId = -9104,
    [long]$KernelRunProfileId = -9101,
    [string]$BackendContainer = "seahorse-backend",
    [string]$PostgresContainer = "seahorse-postgres",
    [string]$PostgresUser = "seahorse",
    [string]$PostgresDatabase = "seahorse",
    [switch]$VerifyOtelTrace,
    [switch]$VerifyStudio,
    [string]$JaegerUrl = "http://127.0.0.1:16686",
    [string]$OtelTraceQueryUrl = "http://localhost:16686/trace/{traceId}",
    [string]$StudioUrl = "http://127.0.0.1:3000",
    [string]$StudioContainer = "seahorse-agentscope-studio"
)

$ErrorActionPreference = "Stop"
$passed = 0
$failed = 0
$total = 0
$a2aEnabled = $false
$agentScopeStudioMarker = "agentscope-studio-e2e-$([guid]::NewGuid().ToString('N'))"

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
        if (-not [string]::IsNullOrWhiteSpace($_.ScriptStackTrace)) {
            Write-Host "  $($_.ScriptStackTrace)" -ForegroundColor DarkRed
        }
        return $null
    }
}

function Assert-ApiOk {
    param([object]$Response, [string]$Name)
    if ($null -eq $Response -or "$($Response.code)" -ne "0") {
        throw "$Name API error: $($Response | ConvertTo-Json -Depth 20 -Compress)"
    }
}

function Assert-Equal {
    param([object]$Actual, [object]$Expected, [string]$Name)
    if ("$Actual" -ne "$Expected") {
        throw "$Name expected '$Expected' but got '$Actual'"
    }
}

function Assert-NotBlank {
    param([string]$Value, [string]$Name)
    if ([string]::IsNullOrWhiteSpace($Value)) {
        throw "$Name was blank"
    }
}

function Get-JsonValue {
    param([object]$Value, [string]$Name)
    if ($null -eq $Value -or $Value -is [string]) {
        return $null
    }
    $property = $Value.PSObject.Properties[$Name]
    if ($null -eq $property) {
        return $null
    }
    return $property.Value
}

function New-SseEvent {
    param([string]$Name, [string[]]$DataLines)
    $raw = [string]::Join("`n", @($DataLines))
    $payload = $raw
    $trimmed = $raw.TrimStart()
    if ($trimmed.StartsWith("{") -or ($trimmed.StartsWith("[") -and $trimmed -ne "[DONE]")) {
        try {
            $payload = $raw | ConvertFrom-Json
        } catch {
            $payload = $raw
        }
    }
    return [PSCustomObject]@{
        Name = $Name
        RawData = $raw
        Payload = $payload
    }
}

function ConvertFrom-SseContent {
    param([string]$Content)
    $events = @()
    $eventName = "message"
    $dataLines = @()
    foreach ($line in ($Content -split "\r?\n")) {
        if ([string]::IsNullOrEmpty($line)) {
            if ($dataLines.Count -gt 0) {
                $events += New-SseEvent -Name $eventName -DataLines $dataLines
            }
            $eventName = "message"
            $dataLines = @()
            continue
        }
        if ($line.StartsWith(":")) {
            continue
        }
        if ($line.StartsWith("event:")) {
            $eventName = $line.Substring(6).Trim()
            continue
        }
        if ($line.StartsWith("data:")) {
            $dataLines += $line.Substring(5).Trim()
        }
    }
    if ($dataLines.Count -gt 0) {
        $events += New-SseEvent -Name $eventName -DataLines $dataLines
    }
    return @($events)
}

function Get-SseRunId {
    param([object[]]$Events)
    foreach ($event in $Events) {
        $runId = Get-JsonValue $event.Payload "runId"
        if (-not [string]::IsNullOrWhiteSpace("$runId")) {
            return "$runId"
        }
        $typedPayload = Get-JsonValue $event.Payload "typedPayload"
        $runId = Get-JsonValue $typedPayload "runId"
        if (-not [string]::IsNullOrWhiteSpace("$runId")) {
            return "$runId"
        }
    }
    return ""
}

function Get-MessageDelta {
    param([object]$Payload)
    $type = Get-JsonValue $Payload "type"
    $delta = Get-JsonValue $Payload "delta"
    if ("$type" -eq "response" -and -not [string]::IsNullOrWhiteSpace("$delta")) {
        return "$delta"
    }
    return ""
}

function Get-SseResponseText {
    param([object[]]$Events)
    $chunks = @()
    foreach ($event in @($Events | Where-Object { $_.Name -eq "message" })) {
        $delta = Get-MessageDelta $event.Payload
        if (-not [string]::IsNullOrWhiteSpace($delta)) {
            $chunks += $delta
        }
    }
    if ($chunks.Count -eq 0) {
        foreach ($event in @($Events | Where-Object { $_.Name -eq "stream_event" })) {
            $eventType = Get-JsonValue $event.Payload "eventType"
            if ("$eventType" -ne "message") {
                continue
            }
            $delta = Get-MessageDelta (Get-JsonValue $event.Payload "typedPayload")
            if (-not [string]::IsNullOrWhiteSpace($delta)) {
                $chunks += $delta
            }
        }
    }
    return ($chunks -join "")
}

function Assert-ChatSseContract {
    param([string]$Name, [object]$Chat)
    $events = @($Chat.Events)
    if ($events.Count -eq 0) {
        throw "$Name SSE had no parseable events"
    }
    $eventNames = @($events | ForEach-Object { $_.Name } | Sort-Object -Unique)
    foreach ($required in @("meta", "message", "finish", "done")) {
        if ($eventNames -notcontains $required) {
            throw "$Name SSE missing '$required' event. Events: $($eventNames -join ',')"
        }
    }
    $errorEvents = @($events | Where-Object { $_.Name -eq "error" -or $_.Name -eq "recoverable_error" })
    if ($errorEvents.Count -gt 0) {
        throw "$Name SSE included error events: $($errorEvents | ConvertTo-Json -Depth 20 -Compress)"
    }
    if (-not ($events | Where-Object { $_.RawData -eq "[DONE]" })) {
        throw "$Name SSE did not include [DONE] payload"
    }
    Assert-NotBlank $Chat.RunId "$Name SSE runId"
    Assert-NotBlank $Chat.ResponseText "$Name SSE response text"
}

function Assert-SseEquivalentContract {
    param([object]$AgentScopeChat, [object]$KernelChat)
    foreach ($required in @("meta", "message", "finish", "done")) {
        if (@($AgentScopeChat.EventNames) -notcontains $required) {
            throw "AgentScope SSE missing required event '$required'"
        }
        if (@($KernelChat.EventNames) -notcontains $required) {
            throw "Kernel SSE missing required event '$required'"
        }
    }
    if ($AgentScopeChat.ResponseChars -le 0 -or $KernelChat.ResponseChars -le 0) {
        throw "AgentScope/kernel SSE response text must both be non-empty"
    }
    if ($AgentScopeChat.StreamEventCount -le 0 -or $KernelChat.StreamEventCount -le 0) {
        throw "AgentScope/kernel SSE must both include stream_event envelopes"
    }
}

function Assert-SnapshotMatchesChat {
    param([string]$Name, [object]$Snapshot, [object]$Chat)
    Assert-Equal $Snapshot.runId $Chat.RunId "$Name SSE/snapshot run_id"
    Assert-NotBlank $Snapshot.traceId "$Name snapshot traceId"
}

function Assert-AgentScopeSnapshotTraceContext {
    param([object]$Snapshot)
    if ("$($Snapshot.agentScopeTraceEnabled)" -eq "true") {
        Assert-NotBlank $Snapshot.agentScopeStudioUrl "AgentScope snapshot agentScope.studioUrl"
        Assert-NotBlank $Snapshot.studioRunId "AgentScope trace_context studioRunId"
        Assert-NotBlank $Snapshot.studioProject "AgentScope trace_context studioProject"
        Assert-NotBlank $Snapshot.studioTraceUrl "AgentScope trace_context studioTraceUrl"
        if ("$($Snapshot.studioRunId)" -eq "$($Snapshot.traceId)") {
            throw "Studio runtime run id must remain distinct from the Seahorse logical trace id"
        }
        $expectedStudioUrl = "$($Snapshot.studioUrl.TrimEnd('/'))/projects/" `
            + [System.Uri]::EscapeDataString("$($Snapshot.studioProject)") `
            + "/runs/" + [System.Uri]::EscapeDataString("$($Snapshot.studioRunId)")
        Assert-Equal $Snapshot.studioTraceUrl $expectedStudioUrl "AgentScope Studio run URL"
    }
}

function Assert-OtelSnapshotTraceContext {
    param([string]$Name, [object]$Snapshot)
    Assert-NotBlank $Snapshot.otelTraceId "$Name snapshot otelTraceId"
    Assert-NotBlank $Snapshot.otelTraceUrl "$Name snapshot otelTraceUrl"
    if ("$($Snapshot.otelTraceId)" -notmatch '^[0-9a-f]{32}$') {
        throw "$Name snapshot otelTraceId was not a 32-character lowercase hex id"
    }
    $expectedUrl = $OtelTraceQueryUrl.Replace("{traceId}", "$($Snapshot.otelTraceId)")
    Assert-Equal $Snapshot.otelTraceUrl $expectedUrl "$Name snapshot OTEL trace URL"

    $response = $null
    $trace = $null
    $deadline = (Get-Date).AddSeconds(45)
    do {
        try {
            $response = Invoke-RestMethod -Uri "$($JaegerUrl.TrimEnd('/'))/api/traces/$($Snapshot.otelTraceId)" `
                -TimeoutSec 10
        } catch {
            $response = $null
        }
        if ($null -ne $response -and $null -ne $response.data) {
            $trace = @($response.data | Where-Object { $null -ne $_ }) | Select-Object -First 1
        }
        if ($null -ne $trace) {
            break
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)

    if ($null -eq $trace) {
        throw "$Name OTEL trace was not queryable from Jaeger"
    }
    $spans = @($trace.spans)
    if (-not ($spans | Where-Object { "$($_.operationName)" -eq "agent.run" })) {
        throw "$Name OTEL trace did not contain agent.run"
    }
    $runTags = @($spans | ForEach-Object { @($_.tags) } |
        Where-Object { "$($_.key)" -eq "seahorse.run.id" -and "$($_.value)" -eq "$($Snapshot.runId)" })
    if ($runTags.Count -eq 0) {
        throw "$Name OTEL trace did not contain seahorse.run.id=$($Snapshot.runId)"
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

function Invoke-StudioScalar {
    param([string]$Sql)
    $raw = & docker.exe exec $StudioContainer sqlite3 /app/data/AgentScope-Studio/database.sqlite $Sql
    if ($LASTEXITCODE -ne 0) {
        throw "Studio sqlite query failed with exit code $LASTEXITCODE"
    }
    return "$($raw | Select-Object -First 1)".Trim()
}

function Assert-StudioRuntimeEvidence {
    param([object]$Snapshot, [string]$Marker)
    $safeRunId = "$($Snapshot.studioRunId)".Replace("'", "''")
    $safeProject = "$($Snapshot.studioProject)".Replace("'", "''")
    $safeMarker = $Marker.Replace("'", "''")
    $deadline = (Get-Date).AddSeconds(45)
    $runRow = ""
    $messageCount = 0
    $spanCount = 0
    do {
        $runRow = Invoke-StudioScalar `
            "select project || '|' || name || '|' || status from run_table where id='$safeRunId';"
        $messageCount = [int](Invoke-StudioScalar `
            "select count(*) from message_table where run_id='$safeRunId';")
        $spanCount = [int](Invoke-StudioScalar `
            "select count(*) from span_table where conversationId='$safeRunId' and instr(attributes, '$safeMarker') > 0;")
        if (-not [string]::IsNullOrWhiteSpace($runRow) `
                -and $messageCount -gt 0 `
                -and $spanCount -gt 0) {
            break
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)

    if ([string]::IsNullOrWhiteSpace($runRow)) {
        throw "Studio run $($Snapshot.studioRunId) was not persisted"
    }
    $runParts = $runRow -split "\|", 3
    Assert-Equal $runParts[0] $Snapshot.studioProject "Studio persisted project"
    if ($messageCount -le 0) {
        throw "Studio run $($Snapshot.studioRunId) did not receive an AgentScope output message"
    }
    if ($spanCount -le 0) {
        throw "Studio did not ingest an AgentScope OTLP span for run $($Snapshot.studioRunId) and marker $Marker"
    }
    $status = & curl.exe -sS -o NUL -w "%{http_code}" "$($Snapshot.studioTraceUrl)"
    if ($LASTEXITCODE -ne 0) {
        throw "curl failed for Studio run URL"
    }
    Assert-Equal ([int]$status) 200 "Studio run page status"
    Write-Host "Studio run: $runRow, messages=$messageCount, matching spans=$spanCount"
}

function Invoke-HttpStatus {
    param([string]$Path)
    $status = & curl.exe -sS -o NUL -w "%{http_code}" "$BaseUrl$Path"
    if ($LASTEXITCODE -ne 0) {
        throw "curl failed for $Path"
    }
    return [int]$status
}

function Invoke-Chat {
    param(
        [string]$ConversationId,
        [hashtable]$Headers,
        [string]$Question,
        [long]$RunProfileId = 0,
        [string]$ChatMode = ""
    )
    $encodedQuestion = [System.Uri]::EscapeDataString($Question)
    $query = "conversationId=$ConversationId&question=$encodedQuestion"
    if ($RunProfileId -ne 0) {
        $query = "$query&runProfileId=$RunProfileId"
    }
    if (-not [string]::IsNullOrWhiteSpace($ChatMode)) {
        $query = "$query&chatMode=$([System.Uri]::EscapeDataString($ChatMode))"
    }
    $response = Invoke-WebRequest -Uri "$BaseUrl/rag/v3/chat?$query" `
        -Headers $Headers -UseBasicParsing -TimeoutSec 180
    if ([int]$response.StatusCode -ne 200) {
        throw "Chat returned HTTP $($response.StatusCode)"
    }
    $contentType = "$($response.Headers['Content-Type'])"
    if ($contentType -notlike "*text/event-stream*") {
        throw "Chat content type was '$contentType'"
    }
    if ($response.Content -notlike "*[DONE]*") {
        throw "Chat SSE did not include [DONE]"
    }
    $events = ConvertFrom-SseContent $response.Content
    $responseText = Get-SseResponseText $events
    $eventNames = @($events | ForEach-Object { $_.Name } | Sort-Object -Unique)
    $chat = [PSCustomObject]@{
        ContentType = $contentType
        Length = $response.Content.Length
        Events = @($events)
        EventNames = $eventNames
        DataEventCount = @($events).Count
        StreamEventCount = @($events | Where-Object { $_.Name -eq "stream_event" }).Count
        MessageEventCount = @($events | Where-Object { $_.Name -eq "message" }).Count
        ResponseText = $responseText
        ResponseChars = $responseText.Length
        RunId = Get-SseRunId $events
    }
    Assert-ChatSseContract "Chat" $chat
    [pscustomobject]@{
        contentType = $chat.ContentType
        bytes = $chat.Length
        events = $chat.EventNames -join ","
        streamEvents = $chat.StreamEventCount
        responseChars = $chat.ResponseChars
        runId = $chat.RunId
    } | ConvertTo-Json -Compress | Write-Host
    return $chat
}

function Get-LatestSnapshot {
    param([string]$ConversationId)
    Start-Sleep -Seconds 2
    $sql = @"
select run_id,
       coalesce(role_card_id::text, ''),
       coalesce(run_profile_id::text, ''),
       executor_engine,
       coalesce(snapshot_json::jsonb #>> '{runProfile,name}', '') as run_profile_name,
       coalesce(coalesce(nullif(trace_context_json, ''), '{}')::jsonb ->> 'traceId', '') as trace_id,
       coalesce(coalesce(nullif(trace_context_json, ''), '{}')::jsonb ->> 'studioUrl', '') as studio_url,
       coalesce(coalesce(nullif(trace_context_json, ''), '{}')::jsonb ->> 'studioRunId', '') as studio_run_id,
       coalesce(coalesce(nullif(trace_context_json, ''), '{}')::jsonb ->> 'studioProject', '') as studio_project,
       coalesce(coalesce(nullif(trace_context_json, ''), '{}')::jsonb ->> 'studioTraceUrl', '') as studio_trace_url,
       coalesce(coalesce(nullif(trace_context_json, ''), '{}')::jsonb ->> 'otelTraceId', '') as otel_trace_id,
       coalesce(coalesce(nullif(trace_context_json, ''), '{}')::jsonb ->> 'otelTraceUrl', '') as otel_trace_url,
       coalesce(snapshot_json::jsonb #>> '{agentScope,studioTraceEnabled}', '') as agent_scope_trace_enabled,
       coalesce(snapshot_json::jsonb #>> '{agentScope,studioUrl}', '') as agent_scope_studio_url,
       coalesce(snapshot_json::jsonb #>> '{agentScope,studioRunId}', '') as agent_scope_studio_run_id
from t_run_context_snapshot
where conversation_id = $ConversationId
  and deleted = 0
order by create_time desc
limit 1;
"@
    $row = Invoke-DbScalarRow $sql
    if (-not $row) {
        throw "No t_run_context_snapshot row found for conversation $ConversationId"
    }
    $parts = $row -split "\|", 15
    if ($parts.Count -lt 15) {
        throw "Unexpected snapshot row format: $row"
    }
    return [PSCustomObject]@{
        runId = $parts[0]
        roleCardId = $parts[1]
        runProfileId = $parts[2]
        executorEngine = $parts[3]
        runProfileName = $parts[4]
        traceId = $parts[5]
        studioUrl = $parts[6]
        studioRunId = $parts[7]
        studioProject = $parts[8]
        studioTraceUrl = $parts[9]
        otelTraceId = $parts[10]
        otelTraceUrl = $parts[11]
        agentScopeTraceEnabled = $parts[12]
        agentScopeStudioUrl = $parts[13]
        agentScopeStudioRunId = $parts[14]
    }
}

Test-Step "Verify AgentScope runtime flags" {
    $envLines = @(& docker.exe inspect $BackendContainer --format '{{range .Config.Env}}{{println .}}{{end}}')
    if (-not ($envLines | Where-Object { $_ -eq "SEAHORSE_AGENTSCOPE_EXECUTOR_ENABLED=true" })) {
        throw "SEAHORSE_AGENTSCOPE_EXECUTOR_ENABLED=true was not found on $BackendContainer"
    }
    if ($envLines | Where-Object { $_ -eq "SEAHORSE_AGENTSCOPE_A2A_ENABLED=true" }) {
        $script:a2aEnabled = $true
    } elseif ($envLines | Where-Object { $_ -eq "SEAHORSE_AGENTSCOPE_A2A_ENABLED=false" }) {
        $script:a2aEnabled = $false
    } else {
        throw "SEAHORSE_AGENTSCOPE_A2A_ENABLED was not found on $BackendContainer"
    }
    if ($VerifyOtelTrace) {
        if (-not ($envLines | Where-Object { $_ -eq "SEAHORSE_OBSERVABILITY_TRACING_ENABLED=true" })) {
            throw "SEAHORSE_OBSERVABILITY_TRACING_ENABLED=true was not found on $BackendContainer"
        }
        $expectedQueryUrl = "SEAHORSE_OBSERVABILITY_TRACING_QUERY_URL=$OtelTraceQueryUrl"
        if (-not ($envLines | Where-Object { $_ -eq $expectedQueryUrl })) {
            throw "Expected OTEL query URL was not found on $BackendContainer"
        }
    }
    if ($VerifyStudio) {
        foreach ($expected in @(
                "SEAHORSE_AGENTSCOPE_STUDIO_ENABLED=true",
                "SEAHORSE_AGENTSCOPE_STUDIO_PUBLIC_URL=http://localhost:3000")) {
            if (-not ($envLines | Where-Object { $_ -eq $expected })) {
                throw "$expected was not found on $BackendContainer"
            }
        }
        $health = & docker.exe inspect $StudioContainer --format '{{.State.Health.Status}}'
        Assert-Equal $health "healthy" "AgentScope Studio container health"
        $rootStatus = & curl.exe -sS -o NUL -w "%{http_code}" "$($StudioUrl.TrimEnd('/'))/"
        Assert-Equal ([int]$rootStatus) 200 "AgentScope Studio root status"
    }
}

$login = Test-Step "Login" {
    $body = @{ username = $Username; password = $Password } | ConvertTo-Json -Compress
    $response = Invoke-RestMethod -Uri "$BaseUrl/auth/login" -Method Post -ContentType "application/json" -Body $body
    Assert-ApiOk $response "Login"
    if (-not $response.data.token) {
        throw "Login response did not include token"
    }
    $response
}
if (-not $login) { exit 1 }

$headers = @{ Authorization = "Bearer $($login.data.token)" }

$profiles = Test-Step "Find AgentScope and kernel run profiles" {
    $response = Invoke-RestMethod -Uri "$BaseUrl/api/run-profiles" -Headers $headers
    Assert-ApiOk $response "List run profiles"
    $agentScope = @($response.data | Where-Object { "$($_.id)" -eq "$AgentScopeRunProfileId" }) | Select-Object -First 1
    $kernel = @($response.data | Where-Object { "$($_.id)" -eq "$KernelRunProfileId" }) | Select-Object -First 1
    if (-not $agentScope) {
        throw "Run profile $AgentScopeRunProfileId was not returned"
    }
    if (-not $kernel) {
        throw "Run profile $KernelRunProfileId was not returned"
    }
    Assert-Equal $agentScope.executorEngine "agentscope" "AgentScope profile executor"
    Assert-Equal $kernel.executorEngine "kernel" "Kernel profile executor"
    @{ agentScope = $agentScope; kernel = $kernel }
}
if (-not $profiles) { exit 1 }

$agentScopeConversationId = Test-Step "Create AgentScope conversation and apply profile" {
    $created = Invoke-RestMethod -Uri "$BaseUrl/api/conversations" -Method Post -Headers $headers
    Assert-ApiOk $created "Create conversation"
    $conversationId = "$($created.data)"
    $applied = Invoke-RestMethod -Uri "$BaseUrl/api/conversations/$conversationId/run-profile/$AgentScopeRunProfileId/apply" `
        -Method Post -Headers $headers
    Assert-ApiOk $applied "Apply AgentScope run profile"
    Assert-Equal $applied.data.runProfileId $AgentScopeRunProfileId "Applied AgentScope runProfileId"
    $conversationId
}
if (-not $agentScopeConversationId) { exit 1 }

$agentScopeChat = Test-Step "Chat through AgentScope run profile" {
    Invoke-Chat -ConversationId $agentScopeConversationId -Headers $headers `
        -RunProfileId $AgentScopeRunProfileId -ChatMode "agent" `
        -Question "${agentScopeStudioMarker}: answer with one short sentence."
}

$agentScopeSnapshot = Test-Step "Verify AgentScope run context snapshot" {
    $snapshot = Get-LatestSnapshot -ConversationId $agentScopeConversationId
    Assert-Equal $snapshot.runProfileId $AgentScopeRunProfileId "AgentScope snapshot run_profile_id"
    Assert-Equal $snapshot.executorEngine "agentscope" "AgentScope snapshot executor_engine"
    Assert-SnapshotMatchesChat "AgentScope" $snapshot $agentScopeChat
    Assert-AgentScopeSnapshotTraceContext $snapshot
    if ($VerifyOtelTrace) {
        Assert-OtelSnapshotTraceContext "AgentScope" $snapshot
    }
    if ($VerifyStudio) {
        Assert-StudioRuntimeEvidence $snapshot $agentScopeStudioMarker
    }
    $snapshot | ConvertTo-Json -Compress | Write-Host
    $snapshot
}

Test-Step "Verify A2A endpoint boundary" {
    if ($script:a2aEnabled) {
        Assert-Equal (Invoke-HttpStatus "/a2a") 200 "A2A agent card status"
        Assert-Equal (Invoke-HttpStatus "/a2a/.well-known/agent-card.json") 404 "A2A legacy well-known status"

        $card = Invoke-RestMethod -Uri "$BaseUrl/a2a" -Method Get
        if ([string]::IsNullOrWhiteSpace("$($card.name)") -or "$($card.url)" -notlike "*/a2a") {
            throw "A2A agent card is missing name or endpoint url: $($card | ConvertTo-Json -Depth 20 -Compress)"
        }
        $tags = @($card.skills | ForEach-Object { @($_.tags) })
        if (-not ($tags | Where-Object { "$_" -like "seahorse:a2a:authMode=*" })) {
            throw "A2A agent card did not expose auth mode metadata"
        }
        $status = & curl.exe -sS -o NUL -w "%{http_code}" -X POST "$BaseUrl/a2a" `
            -H "Content-Type: application/json" --data "{}"
        if ($LASTEXITCODE -ne 0) {
            throw "curl failed for unauthorized A2A POST"
        }
        Assert-Equal ([int]$status) 401 "A2A unauthenticated POST status"
    } else {
        Assert-Equal (Invoke-HttpStatus "/a2a") 404 "A2A endpoint status"
        Assert-Equal (Invoke-HttpStatus "/a2a/.well-known/agent-card.json") 404 "A2A legacy well-known status"
    }
}

$kernelConversationId = Test-Step "Create kernel conversation and apply profile" {
    $created = Invoke-RestMethod -Uri "$BaseUrl/api/conversations" -Method Post -Headers $headers
    Assert-ApiOk $created "Create kernel conversation"
    $conversationId = "$($created.data)"
    $applied = Invoke-RestMethod -Uri "$BaseUrl/api/conversations/$conversationId/run-profile/$KernelRunProfileId/apply" `
        -Method Post -Headers $headers
    Assert-ApiOk $applied "Apply kernel run profile"
    Assert-Equal $applied.data.runProfileId $KernelRunProfileId "Applied kernel runProfileId"
    $conversationId
}

$kernelChat = Test-Step "Chat still works through kernel run profile" {
    Invoke-Chat -ConversationId $kernelConversationId -Headers $headers `
        -RunProfileId $KernelRunProfileId -ChatMode "agent" `
        -Question "Kernel smoke after AgentScope $(Get-Date -Format yyyyMMddHHmmss): answer with one short sentence."
}

$kernelSnapshot = Test-Step "Verify kernel run context snapshot" {
    $snapshot = Get-LatestSnapshot -ConversationId $kernelConversationId
    Assert-Equal $snapshot.runProfileId $KernelRunProfileId "Kernel snapshot run_profile_id"
    Assert-Equal $snapshot.executorEngine "kernel" "Kernel snapshot executor_engine"
    Assert-SnapshotMatchesChat "Kernel" $snapshot $kernelChat
    if ($VerifyOtelTrace) {
        Assert-OtelSnapshotTraceContext "Kernel" $snapshot
    }
    $snapshot | ConvertTo-Json -Compress | Write-Host
    $snapshot
}

Test-Step "Verify AgentScope and kernel SSE contract equivalence" {
    Assert-SseEquivalentContract $agentScopeChat $kernelChat
    Write-Host "AgentScope events: $($agentScopeChat.EventNames -join ',')"
    Write-Host "Kernel events: $($kernelChat.EventNames -join ',')"
}

Write-Host "`nSummary: $passed / $total passed, $failed failed" -ForegroundColor Cyan
Write-Host "AgentScope conversation ID: $agentScopeConversationId"
Write-Host "AgentScope run ID: $($agentScopeSnapshot.runId)"
Write-Host "AgentScope chat bytes: $($agentScopeChat.Length)"
Write-Host "AgentScope SSE events: $($agentScopeChat.DataEventCount), stream envelopes: $($agentScopeChat.StreamEventCount), response chars: $($agentScopeChat.ResponseChars)"
Write-Host "Kernel conversation ID: $kernelConversationId"
Write-Host "Kernel run ID: $($kernelSnapshot.runId)"
Write-Host "Kernel chat bytes: $($kernelChat.Length)"
Write-Host "Kernel SSE events: $($kernelChat.DataEventCount), stream envelopes: $($kernelChat.StreamEventCount), response chars: $($kernelChat.ResponseChars)"

if ($failed -gt 0) {
    exit 1
}
