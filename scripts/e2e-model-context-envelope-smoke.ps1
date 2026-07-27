param(
    [string]$BaseUrl = "http://127.0.0.1:9090",
    [string]$Username = "admin",
    [string]$Password = "admin123",
    [long]$KernelRunProfileId = -9101,
    [string]$BackendContainer = "seahorse-backend",
    [string]$PostgresContainer = "seahorse-postgres",
    [string]$RedisContainer = "seahorse-model-context-e2e-redis",
    [string]$PostgresUser = "seahorse",
    [string]$PostgresDatabase = "seahorse",
    [string]$JaegerUrl = "http://127.0.0.1:16686",
    [string]$ProviderProxyUrl = "http://127.0.0.1:18081",
    [string]$ProviderProxyToken = "seahorse-model-envelope-e2e",
    [string]$ExpectedEstimatorMode = "CONSERVATIVE_FALLBACK",
    [string]$ExpectedEstimatorVersion = "utf8-byte-upper-bound-v1",
    [int]$HistoryMessageCount = 20,
    [int]$HistoryCharactersPerMessage = 5000,
    [int]$LongQuestionCharacters = 4000,
    [int]$PartitionHistoryMessageCount = 18,
    [int]$PartitionHistoryCharactersPerMessage = 3000,
    [int]$ExpandedSystemPromptCharacters = 1200,
    [int]$ExpandedSkillCharacters = 2000,
    [int]$ExpandedToolSchemaCharacters = 1800,
    [int]$ExpandedCurrentInputCharacters = 800,
    [string]$Marker = ""
)

$ErrorActionPreference = "Stop"
$passed = 0
$total = 0
$conversationIds = [System.Collections.Generic.List[string]]::new()
$runIds = [System.Collections.Generic.List[string]]::new()
$testStartedAt = [DateTimeOffset]::UtcNow
$fixtureProfileIds = [System.Collections.Generic.List[long]]::new()
$fixtureRoleCardIds = [System.Collections.Generic.List[long]]::new()
$fixtureConnectorIds = [System.Collections.Generic.List[string]]::new()
$fixtureSkillNames = [System.Collections.Generic.List[string]]::new()
$fixtureToolIds = [System.Collections.Generic.List[string]]::new()
$calibrationKey = $null
$executionError = $null
$expectedModelId = $null

function Test-Step {
    param([string]$Name, [scriptblock]$Action)

    $script:total++
    Write-Host "`n[$script:total] $Name" -ForegroundColor Cyan
    $result = & $Action
    $script:passed++
    Write-Host "  PASS" -ForegroundColor Green
    return $result
}

function Assert-True {
    param([bool]$Condition, [string]$Message)

    if (-not $Condition) {
        throw $Message
    }
}

function Assert-Equal {
    param([object]$Actual, [object]$Expected, [string]$Name)

    if ("$Actual" -cne "$Expected") {
        throw "$Name expected '$Expected' but got '$Actual'"
    }
}

function Test-JsonTreeContainsProperty {
    param(
        [AllowNull()][object]$Value,
        [string]$PropertyName
    )

    if ($null -eq $Value) {
        return $false
    }
    if ($Value -is [string]) {
        $text = "$Value".Trim()
        if (($text.StartsWith('{') -and $text.EndsWith('}')) -or
            ($text.StartsWith('[') -and $text.EndsWith(']'))) {
            try {
                return Test-JsonTreeContainsProperty -Value ($text | ConvertFrom-Json) `
                    -PropertyName $PropertyName
            } catch {
                return $false
            }
        }
        return $false
    }
    if ($Value -is [System.Collections.IDictionary]) {
        foreach ($key in $Value.Keys) {
            if ("$key" -ceq $PropertyName -or
                (Test-JsonTreeContainsProperty -Value $Value[$key] -PropertyName $PropertyName)) {
                return $true
            }
        }
        return $false
    }
    if ($Value -is [pscustomobject]) {
        foreach ($property in $Value.PSObject.Properties) {
            if ($property.Name -ceq $PropertyName -or
                (Test-JsonTreeContainsProperty -Value $property.Value -PropertyName $PropertyName)) {
                return $true
            }
        }
        return $false
    }
    if ($Value -is [System.Collections.IEnumerable]) {
        foreach ($item in $Value) {
            if (Test-JsonTreeContainsProperty -Value $item -PropertyName $PropertyName) {
                return $true
            }
        }
    }
    return $false
}

function ConvertTo-SqlLiteral {
    param([string]$Value)

    return "'" + $Value.Replace("'", "''") + "'"
}

function Invoke-PostgresRows {
    param([string]$Sql)

    $raw = & docker.exe exec $PostgresContainer psql -U $PostgresUser -d $PostgresDatabase -At -c $Sql
    if ($LASTEXITCODE -ne 0) {
        throw "psql exited with $LASTEXITCODE"
    }
    return @($raw | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
}

function Invoke-PostgresScalar {
    param([string]$Sql)

    $rows = @(Invoke-PostgresRows $Sql)
    if ($rows.Count -eq 0) {
        return $null
    }
    return "$($rows[0])"
}

function Invoke-PostgresNonQuery {
    param([string]$Sql)

    & docker.exe exec $PostgresContainer psql -v ON_ERROR_STOP=1 `
        -U $PostgresUser -d $PostgresDatabase -q -c $Sql | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "psql exited with $LASTEXITCODE"
    }
}

function Invoke-Redis {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)

    $output = & docker.exe exec $RedisContainer redis-cli --raw @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "redis-cli exited with $LASTEXITCODE"
    }
    return @($output)
}

function Invoke-ProviderProxy {
    param(
        [string]$Method,
        [string]$Path,
        [object]$Body = $null
    )

    $parameters = @{
        Method = $Method
        Uri = "$($ProviderProxyUrl.TrimEnd('/'))$Path"
        Headers = @{ Authorization = "Bearer $ProviderProxyToken" }
        TimeoutSec = 15
    }
    if ($null -ne $Body) {
        $parameters.ContentType = "application/json"
        $parameters.Body = $Body | ConvertTo-Json -Depth 10 -Compress
    }
    return Invoke-RestMethod @parameters
}

function Set-ProviderProxyConfig {
    param(
        [string]$CountMarker,
        [string]$ControlledTrigger = "",
        [string]$ReasoningMarker = "",
        [string]$ResponseContent = "CONTROLLED_REASONING_OK",
        [bool]$FailAfterReasoning = $false,
        [string]$ControlledToolId = "",
        [hashtable]$ControlledToolArguments = @{}
    )

    Invoke-ProviderProxy -Method POST -Path "/__e2e/config" -Body @{
        countMarker = $CountMarker
        controlledTrigger = $ControlledTrigger
        reasoningMarker = $ReasoningMarker
        responseContent = $ResponseContent
        failAfterReasoning = $FailAfterReasoning
        controlledToolId = $ControlledToolId
        controlledToolArguments = $ControlledToolArguments
    }
}

function Get-ProviderProxyStats {
    return Invoke-ProviderProxy -Method GET -Path "/__e2e/stats"
}

function Get-CalibrationEvidence {
    param([string]$Key)

    $value = "$(Invoke-Redis GET $Key | Select-Object -First 1)"
    $ttl = [long]"$(Invoke-Redis PTTL $Key | Select-Object -First 1)"
    Assert-True ($value -cmatch '^(\d+):(\d+)$') "Model calibration value is invalid"
    Assert-True ([long]$Matches[1] -gt 0) "Model calibration tokens are not positive"
    Assert-True ([long]$Matches[1] -le 16384) "Model calibration tokens exceeded the absolute cap"
    Assert-True ($ttl -gt 0 -and $ttl -le 86400000) "Model calibration TTL is outside the 24h bound"
    return [pscustomobject]@{
        Key = $Key
        Tokens = [long]$Matches[1]
        UpdatedAt = [long]$Matches[2]
        TtlMillis = $ttl
    }
}

function Set-ControlledCalibration {
    param(
        [object]$EnvelopeEvidence,
        [int]$Tokens = 1024
    )

    Assert-True ($Tokens -gt 0 -and $Tokens -le 16384) `
        "Controlled calibration tokens are outside the production bound"
    $identity = "CalibrationKey[modelId=$($EnvelopeEvidence.modelId.ToLowerInvariant()), " +
        "estimatorMode=$($EnvelopeEvidence.estimatorMode), " +
        "estimatorVersion=$($EnvelopeEvidence.estimatorVersion)]"
    $key = 'seahorse:agent:cache:model-context-envelope:calibration:v1:' + `
        (Get-Sha256Hex $identity)
    Assert-Equal $key $script:calibrationKey "Controlled calibration ownership key"
    $updatedAt = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    Invoke-Redis PSETEX $key 86400000 "${Tokens}:$updatedAt" | Out-Null
    return Get-CalibrationEvidence -Key $key
}

function Wait-BackendHealthy {
    for ($attempt = 1; $attempt -le 300; $attempt++) {
        try {
            $health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -TimeoutSec 5
            if ("$($health.status)" -ceq 'UP') {
                return
            }
        } catch {
            # The container is expected to reject requests while restarting.
        }
        Start-Sleep -Seconds 2
    }
    throw "Timed out waiting for backend health after restart"
}

function Get-DockerLogsSince {
    param(
        [string]$Container,
        [string]$Since
    )

    $previousErrorAction = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = @(& docker.exe logs --since $Since $Container 2>&1)
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorAction
    }
    if ($exitCode -ne 0) {
        throw "docker logs exited with $exitCode"
    }
    return ($output | ForEach-Object { "$_" }) -join "`n"
}

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        [hashtable]$Headers = @{},
        [object]$Body = $null
    )

    $arguments = @{
        Uri = "$BaseUrl$Path"
        Method = $Method
        Headers = $Headers
        TimeoutSec = 30
    }
    if ($null -ne $Body) {
        $arguments.ContentType = "application/json"
        $arguments.Body = $Body | ConvertTo-Json -Depth 20 -Compress
    }
    return Invoke-RestMethod @arguments
}

function Assert-ApiOk {
    param([object]$Response, [string]$Name)

    if ($null -eq $Response -or "$($Response.code)" -ne "0") {
        throw "$Name API error: $($Response | ConvertTo-Json -Depth 20 -Compress)"
    }
}

function Wait-ForValue {
    param(
        [scriptblock]$Query,
        [string]$Name,
        [int]$Attempts = 45,
        [int]$DelaySeconds = 1
    )

    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        $value = & $Query
        if (-not [string]::IsNullOrWhiteSpace("$value")) {
            return $value
        }
        Start-Sleep -Seconds $DelaySeconds
    }
    throw "Timed out waiting for $Name"
}

function New-Conversation {
    param([hashtable]$Headers)

    $response = Invoke-Api -Method POST -Path "/api/conversations" -Headers $Headers
    Assert-ApiOk $response "Create conversation"
    $conversationId = "$($response.data)"
    if ($conversationId -cnotmatch '^\d+$') {
        throw "Conversation id is not numeric: $conversationId"
    }
    $script:conversationIds.Add($conversationId)
    return $conversationId
}

function Add-LongHistory {
    param(
        [string]$ConversationId,
        [string]$UserId,
        [string]$HistoryMarker,
        [int]$MessageCount = $HistoryMessageCount,
        [int]$CharactersPerMessage = $HistoryCharactersPerMessage
    )

    $conversationLiteral = ConvertTo-SqlLiteral $ConversationId
    $userLiteral = ConvertTo-SqlLiteral $UserId
    $markerLiteral = ConvertTo-SqlLiteral $HistoryMarker
    Invoke-PostgresNonQuery @"
WITH bounds AS (
    SELECT COALESCE(MAX(id), 0) + 100 AS base_id FROM t_message
), fixture AS (
    SELECT bounds.base_id, generate_series(0, $($MessageCount - 1)) AS message_no
    FROM bounds
)
INSERT INTO t_message
    (id, conversation_id, user_id, role, content, agent_run_id, thinking_content, thinking_duration,
     parent_id, active, branch_root_id, sibling_seq, create_time, update_time, deleted, tenant_id)
SELECT base_id + message_no,
       $conversationLiteral::bigint,
       $userLiteral::bigint,
       CASE WHEN message_no % 2 = 0 THEN 'user' ELSE 'assistant' END,
       $markerLiteral || ':history:' || message_no || ':' || repeat(chr(65 + (message_no % 20)), $CharactersPerMessage),
       NULL, NULL, NULL,
       CASE WHEN message_no = 0 THEN NULL ELSE base_id + message_no - 1 END,
       1, NULL, 0,
       CURRENT_TIMESTAMP + (message_no * interval '1 millisecond'),
       CURRENT_TIMESTAMP + (message_no * interval '1 millisecond'),
       0, 'default'
FROM fixture;
"@
    $count = Invoke-PostgresScalar "SELECT count(*) FROM t_message WHERE conversation_id=$conversationLiteral::bigint AND content LIKE $markerLiteral || '%';"
    Assert-Equal $count $MessageCount "Persisted history count"
}

function New-FixedCostFixtures {
    param([string]$UserId)

    $userLiteral = ConvertTo-SqlLiteral $UserId
    $baselineProfileId = $script:fixtureIdBase
    $expandedProfileId = $script:fixtureIdBase + 1
    $toolBindingId = $script:fixtureIdBase + 2
    $baselineRoleCardId = $script:fixtureIdBase + 3
    $expandedRoleCardId = $script:fixtureIdBase + 4
    $connectorLiteral = ConvertTo-SqlLiteral $script:fixtureConnectorId
    $connectorVersionLiteral = ConvertTo-SqlLiteral $script:fixtureConnectorVersionId
    $operationLiteral = ConvertTo-SqlLiteral $script:fixtureOperationId
    $toolLiteral = ConvertTo-SqlLiteral $script:fixtureToolId
    $skillLiteral = ConvertTo-SqlLiteral $script:fixtureSkillName
    $revisionLiteral = ConvertTo-SqlLiteral $script:fixtureSkillRevisionId
    $skillContent = "$Marker-skill-body:" + ('S' * $ExpandedSkillCharacters)
    $skillContentLiteral = ConvertTo-SqlLiteral $skillContent
    $skillHash = Get-Sha256Hex $skillContent
    $skillHashLiteral = ConvertTo-SqlLiteral $skillHash
    $schemaDescriptionLiteral = ConvertTo-SqlLiteral `
        ("$Marker-tool-schema:" + ('T' * $ExpandedToolSchemaCharacters))
    $baselineRoleDefinitionLiteral = ConvertTo-SqlLiteral `
        "$Marker-partition-baseline-system: baseline"
    $expandedRoleDefinitionLiteral = ConvertTo-SqlLiteral `
        ("$Marker-partition-expanded-system:" + ('P' * $ExpandedSystemPromptCharacters))

    Invoke-PostgresNonQuery @"
BEGIN;
INSERT INTO sa_connector
    (connector_id, tenant_id, provider, name, description, base_url, status,
     created_by, created_at, updated_at)
VALUES
    ($connectorLiteral, 'default', 'OPENAPI', 'Envelope E2E connector',
     'Script-owned Envelope E2E connector', 'http://backend:9090', 'IMPORTED',
     $userLiteral, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO sa_connector_version
    (connector_version_id, connector_id, spec_hash, spec_json, imported_by, imported_at)
VALUES
    ($connectorVersionLiteral, $connectorLiteral, $skillHashLiteral,
     json_build_object('openapi', '3.0.0')::text, $userLiteral, CURRENT_TIMESTAMP);

INSERT INTO sa_connector_operation
    (operation_id, connector_id, connector_version_id, operation_key, original_operation_id,
     method, path, summary, description, schema_json, output_schema_json, tool_id,
     risk_level, action_type, resource_type, auth_type, status, requires_approval,
     created_at, updated_at)
VALUES
    ($operationLiteral, $connectorLiteral, $connectorVersionLiteral, 'healthProbe', 'healthProbe',
     'GET', '/actuator/health', 'Envelope health probe', 'Script-owned schema budget and approval probe',
     json_build_object(
         'type', 'object',
         'properties', json_build_object(
             'query', json_build_object('type', 'string', 'description', $schemaDescriptionLiteral)
         ),
         'required', json_build_array('query'),
         'additionalProperties', false
     )::text,
     '{}', $toolLiteral,
     'LOW', 'READ', 'EXTERNAL_API', 'NONE', 'ENABLED', true,
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO sa_tool_catalog
    (tool_id, provider, name, description, schema_json, output_schema_json,
     risk_level, action_type, resource_type, owner_team, enabled, requires_approval,
     created_at, updated_at)
VALUES
    ($toolLiteral, 'OPENAPI', 'Envelope health probe', 'Script-owned schema budget and approval probe',
     json_build_object(
         'type', 'object',
         'properties', json_build_object(
             'query', json_build_object('type', 'string', 'description', $schemaDescriptionLiteral)
         ),
         'required', json_build_array('query'),
         'additionalProperties', false
     )::text,
     '{}', 'LOW', 'READ', 'EXTERNAL_API', 'e2e', true, true,
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO sa_role_card
    (id, tenant_id, user_id, name, definition, avatar_ref, higher_perm, enabled,
     share_scope, approval_status, published, asset_source, preset_key, preset_version,
     readonly, create_time, update_time, deleted)
VALUES
    ($baselineRoleCardId, 'default', $userLiteral, 'Envelope baseline role',
     $baselineRoleDefinitionLiteral, NULL, 1, 0, 'PRIVATE', 'APPROVED', 0,
     'USER', NULL, 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ($expandedRoleCardId, 'default', $userLiteral, 'Envelope expanded role',
     $expandedRoleDefinitionLiteral, NULL, 1, 0, 'PRIVATE', 'APPROVED', 0,
     'USER', NULL, 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

INSERT INTO sa_run_profile
    (id, tenant_id, user_id, name, description, role_card_id, executor_engine,
     executor_config_json, model_config_json, memory_scope_json, guardrail_config_json,
     approval_status, approval_operator, approval_comment, approval_time,
     asset_source, preset_key, preset_version, readonly, enabled,
     create_time, update_time, deleted)
VALUES
    ($baselineProfileId, 'default', $userLiteral, 'Envelope baseline profile',
     'Script-owned baseline profile', $baselineRoleCardId, 'kernel', NULL,
     json_build_object('temperature', 0.1)::text,
     json_build_object('longTerm', false)::text,
     json_build_object('highRiskToolApproval', false)::text,
     'APPROVED', $userLiteral, 'e2e fixture', CURRENT_TIMESTAMP,
     'USER', NULL, 1, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ($expandedProfileId, 'default', $userLiteral, 'Envelope expanded profile',
     'Script-owned expanded profile', $expandedRoleCardId, 'kernel', NULL,
     json_build_object('temperature', 0.1)::text,
     json_build_object('longTerm', false)::text,
     json_build_object('highRiskToolApproval', false)::text,
     'APPROVED', $userLiteral, 'e2e fixture', CURRENT_TIMESTAMP,
     'USER', NULL, 1, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

INSERT INTO sa_run_profile_tool
    (id, tenant_id, profile_id, tool_id, provider, enabled, create_time, update_time, deleted)
VALUES
    ($toolBindingId, 'default', $expandedProfileId, $toolLiteral, 'OPENAPI', 1,
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

INSERT INTO sa_agent_skill
    (skill_name, tenant_id, category, source, status, enabled, latest_revision_id,
     description, tags_json, allowed_tools_json, created_by, updated_by,
     created_at, updated_at, deleted)
VALUES
    ($skillLiteral, 'default', 'CUSTOM', 'MANUAL', 'ACTIVE', 1, $revisionLiteral,
     'Script-owned Envelope E2E skill', '[]', json_build_array($toolLiteral)::text,
     $userLiteral, $userLiteral, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

INSERT INTO sa_agent_skill_revision
    (revision_id, skill_name, tenant_id, revision_no, content_hash, content,
     frontmatter_json, scan_decision, scan_result_json, created_by, created_at, deleted)
VALUES
    ($revisionLiteral, $skillLiteral, 'default', 1, $skillHashLiteral, $skillContentLiteral,
     '{}', 'ALLOW', json_build_object('reasons', json_build_array())::text,
     $userLiteral, CURRENT_TIMESTAMP, 0);
COMMIT;
"@
    $script:fixtureProfileIds.Add($baselineProfileId)
    $script:fixtureProfileIds.Add($expandedProfileId)
    $script:fixtureRoleCardIds.Add($baselineRoleCardId)
    $script:fixtureRoleCardIds.Add($expandedRoleCardId)
    $script:fixtureConnectorIds.Add($script:fixtureConnectorId)
    $script:fixtureSkillNames.Add($script:fixtureSkillName)
    $script:fixtureToolIds.Add($script:fixtureToolId)

    $fixtureCount = Invoke-PostgresScalar @"
SELECT (SELECT count(*) FROM sa_run_profile WHERE id IN ($baselineProfileId, $expandedProfileId))
     + (SELECT count(*) FROM sa_run_profile_tool WHERE id=$toolBindingId)
     + (SELECT count(*) FROM sa_connector WHERE connector_id=$connectorLiteral)
     + (SELECT count(*) FROM sa_connector_version WHERE connector_version_id=$connectorVersionLiteral)
     + (SELECT count(*) FROM sa_connector_operation WHERE operation_id=$operationLiteral)
     + (SELECT count(*) FROM sa_tool_catalog WHERE tool_id=$toolLiteral)
     + (SELECT count(*) FROM sa_role_card WHERE id IN ($baselineRoleCardId, $expandedRoleCardId))
     + (SELECT count(*) FROM sa_agent_skill WHERE tenant_id='default' AND skill_name=$skillLiteral)
     + (SELECT count(*) FROM sa_agent_skill_revision WHERE revision_id=$revisionLiteral);
"@
    Assert-Equal $fixtureCount 11 "Persisted fixed-cost fixture count"
}

function Get-Sha256Hex {
    param([string]$Value)

    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($Value)
        return ([System.BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-', '').ToLowerInvariant()
    } finally {
        $sha.Dispose()
    }
}

function Add-OversizedRecentTurn {
    param(
        [string]$ConversationId,
        [string]$UserId,
        [string]$HistoryMarker
    )

    $conversationLiteral = ConvertTo-SqlLiteral $ConversationId
    $userLiteral = ConvertTo-SqlLiteral $UserId
    $markerLiteral = ConvertTo-SqlLiteral $HistoryMarker
    Invoke-PostgresNonQuery @"
WITH bounds AS (
    SELECT COALESCE(MAX(id), 0) + 100 AS base_id FROM t_message
)
INSERT INTO t_message
    (id, conversation_id, user_id, role, content, agent_run_id, thinking_content, thinking_duration,
     parent_id, active, branch_root_id, sibling_seq, create_time, update_time, deleted, tenant_id)
SELECT base_id,
       $conversationLiteral::bigint,
       $userLiteral::bigint,
       'user',
       $markerLiteral || ':oversized-user:' || repeat('U', 12000),
       NULL::text, NULL::text, NULL::integer, NULL::bigint, 1, NULL::bigint, 0,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 'default'
FROM bounds
UNION ALL
SELECT base_id + 1,
       $conversationLiteral::bigint,
       $userLiteral::bigint,
       'assistant',
       $markerLiteral || ':oversized-assistant:' || repeat('A', 12000),
       NULL::text, NULL::text, NULL::integer, base_id, 1, NULL::bigint, 0,
       CURRENT_TIMESTAMP + interval '1 millisecond',
       CURRENT_TIMESTAMP + interval '1 millisecond',
       0, 'default'
FROM bounds;
"@
    $count = Invoke-PostgresScalar "SELECT count(*) FROM t_message WHERE conversation_id=$conversationLiteral::bigint AND content LIKE $markerLiteral || '%';"
    Assert-Equal $count 2 "Persisted oversized recent turn count"
}

function Invoke-KernelChat {
    param(
        [string]$ConversationId,
        [hashtable]$Headers,
        [string]$Question,
        [long]$RunProfileId = $KernelRunProfileId,
        [string[]]$SelectedSkillNames = @()
    )

    $query = "conversationId=$ConversationId" +
        "&question=$([System.Uri]::EscapeDataString($Question))" +
        "&runProfileId=$RunProfileId&chatMode=agent"
    foreach ($skillName in @($SelectedSkillNames)) {
        $query += "&selectedSkillNames=$([System.Uri]::EscapeDataString($skillName))"
    }
    $response = Invoke-WebRequest -Uri "$BaseUrl/rag/v3/chat?$query" `
        -Headers $Headers -UseBasicParsing -TimeoutSec 240
    Assert-Equal ([int]$response.StatusCode) 200 "Chat HTTP status"
    Assert-True ("$($response.Headers['Content-Type'])" -like '*text/event-stream*') `
        "Chat did not return text/event-stream"
    Assert-True $response.Content.Contains('[DONE]') "Chat SSE did not finish with [DONE]"
    Assert-True ($response.Content -notmatch '(?m)^event:\s*(error|recoverable_error)\s*$') `
        "Chat SSE included an error event"

    $runMatches = [regex]::Matches($response.Content, '"runId"\s*:\s*"([^"\\]+)"')
    if ($runMatches.Count -eq 0) {
        throw "Chat SSE did not include runId"
    }
    $runId = $runMatches[0].Groups[1].Value
    Assert-True ($runId -cmatch '^[A-Za-z0-9._-]+$') "Unsafe runId returned by chat: $runId"
    $script:runIds.Add($runId)
    return [pscustomobject]@{
        RunId = $runId
        Bytes = $response.RawContentLength
        Content = $response.Content
    }
}

function Get-PendingApproval {
    param(
        [string]$RunId,
        [hashtable]$Headers
    )

    $response = Invoke-Api -Method GET -Path "/api/agent-runs/$RunId/pending-approvals" `
        -Headers $Headers
    Assert-ApiOk $response "Read pending approval"
    $approvals = @($response.data)
    Assert-Equal $approvals.Count 1 "Pending approval count"
    $approval = $approvals[0]
    Assert-Equal $approval.runId $RunId "Pending approval run id"
    Assert-Equal $approval.toolId $script:fixtureToolId "Pending approval tool id"
    Assert-Equal $approval.status "PENDING" "Pending approval status"
    Assert-True ("$($approval.approvalId)" -cmatch '^approval:[A-Za-z0-9._-]+$') `
        "Pending approval id did not match the canonical approval id format"
    return $approval
}

function Get-ResumeEvidence {
    param(
        [string]$RunId,
        [hashtable]$Headers
    )

    $runLiteral = ConvertTo-SqlLiteral $RunId
    $modelStepCount = Wait-ForValue -Name "initial and resumed MODEL_TURN steps" -Query {
        $count = Invoke-PostgresScalar @"
SELECT count(*)
FROM sa_agent_step
WHERE run_id=$runLiteral AND step_type='MODEL_TURN';
"@
        if ([long]$count -ge 2) { return $count }
        return $null
    }
    $modelStepJson = Invoke-PostgresScalar @"
SELECT json_build_object(
           'inputJson', input_json,
           'outputJson', output_json,
           'errorCode', error_code,
           'errorMessage', error_message
       )::text
FROM sa_agent_step
WHERE run_id=$runLiteral AND step_type='MODEL_TURN'
ORDER BY step_no DESC, started_at DESC
LIMIT 1;
"@
    $toolStepJson = Invoke-PostgresScalar @"
SELECT json_build_object(
           'inputJson', input_json,
           'outputJson', output_json,
           'errorCode', error_code,
           'errorMessage', error_message
       )::text
FROM sa_agent_step
WHERE run_id=$runLiteral AND step_type='TOOL_CALL'
ORDER BY step_no DESC, started_at DESC
LIMIT 1;
"@
    $checkpointJson = Invoke-PostgresScalar @"
SELECT json_build_object(
           'checkpointId', checkpoint_id,
           'stateJson', state_json,
           'messageHistoryJson', message_history_json,
           'pendingToolCallJson', pending_tool_call_json
       )::text
FROM sa_agent_checkpoint
WHERE run_id=$runLiteral AND checkpoint_type='WAITING_APPROVAL'
ORDER BY sequence_no DESC, created_at DESC
LIMIT 1;
"@
    $resumeTraceJson = Wait-ForValue -Name "resume RAG trace" -Query {
        Invoke-PostgresScalar @"
SELECT json_build_object(
           'traceId', r.trace_id,
           'traceName', r.trace_name,
           'status', r.status,
           'nodeTypes', COALESCE((
               SELECT json_agg(n.node_type ORDER BY n.create_time, n.id)
               FROM t_rag_trace_node n
               WHERE n.trace_id=r.trace_id AND n.deleted=0
           ), '[]'::json)
       )::text
FROM t_rag_trace_run r
WHERE r.task_id=$runLiteral AND r.trace_name='agent-run-resume' AND r.deleted=0
ORDER BY r.create_time DESC, r.id DESC
LIMIT 1;
"@
    }
    $originalTraceId = Invoke-PostgresScalar `
        "SELECT trace_id FROM sa_agent_run WHERE run_id=$runLiteral LIMIT 1;"
    $originalOtelTraceId = Invoke-PostgresScalar @"
SELECT COALESCE(NULLIF(trace_context_json, '')::jsonb ->> 'otelTraceId', '')
FROM t_run_context_snapshot
WHERE run_id=$runLiteral AND deleted=0
ORDER BY create_time DESC
LIMIT 1;
"@
    $checkpointResponse = Invoke-Api -Method GET -Path "/api/agent-runs/$RunId/checkpoints" `
        -Headers $Headers
    Assert-ApiOk $checkpointResponse "Read sanitized approval checkpoint"
    $publicCheckpoints = @($checkpointResponse.data)
    $publicCheckpoint = @($publicCheckpoints | Where-Object {
        "$($_.checkpointId)" -ceq "$(($checkpointJson | ConvertFrom-Json).checkpointId)"
    }) | Select-Object -First 1
    Assert-True (-not [string]::IsNullOrWhiteSpace($modelStepJson)) `
        "Resumed MODEL_TURN step is missing"
    Assert-True (-not [string]::IsNullOrWhiteSpace($toolStepJson)) `
        "Resumed TOOL_CALL step is missing"
    Assert-True (-not [string]::IsNullOrWhiteSpace($checkpointJson)) `
        "Waiting approval checkpoint is missing"

    $modelStep = $modelStepJson | ConvertFrom-Json
    $toolStep = $toolStepJson | ConvertFrom-Json
    $checkpoint = $checkpointJson | ConvertFrom-Json
    $resumeTrace = $resumeTraceJson | ConvertFrom-Json
    return [pscustomobject]@{
        ModelStepCount = [long]$modelStepCount
        ModelStepJson = $modelStepJson
        ModelStep = $modelStep
        Envelope = $modelStep.inputJson | ConvertFrom-Json
        ModelOutput = $modelStep.outputJson | ConvertFrom-Json
        ToolStepJson = $toolStepJson
        ToolStep = $toolStep
        ToolInput = $toolStep.inputJson | ConvertFrom-Json
        ToolOutput = $toolStep.outputJson | ConvertFrom-Json
        CheckpointJson = $checkpointJson
        Checkpoint = $checkpoint
        CheckpointState = $checkpoint.stateJson | ConvertFrom-Json
        CheckpointMessages = $checkpoint.messageHistoryJson | ConvertFrom-Json
        PendingToolCall = $checkpoint.pendingToolCallJson | ConvertFrom-Json
        PublicCheckpoint = $publicCheckpoint
        ResumeTrace = $resumeTrace
        OriginalTraceId = $originalTraceId
        OriginalOtelTraceId = $originalOtelTraceId
    }
}

function Assert-ResumeEnvelope {
    param(
        [object]$Evidence,
        [string]$RawMarker
    )

    $envelope = $Evidence.Envelope
    $descriptor = $Evidence.CheckpointState.resumeDescriptor
    Assert-True ($Evidence.ModelStepCount -ge 2) "Resume did not append a second MODEL_TURN"
    Assert-Equal $envelope.schemaVersion "model-context-envelope-v1" "Resume schema version"
    Assert-Equal $envelope.mode "ENFORCE" "Resume mode"
    Assert-Equal $envelope.reasonCode "OK" "Resume reason code"
    Assert-Equal $envelope.payloadHashSource "OPENAI_COMPATIBLE_WIRE_JSON" `
        "Resume payload hash source"
    Assert-True ("$($envelope.contextWindowSource)" -like '*safe-profile:default-model') `
        "Resume did not use the explicit default-model safe profile"
    Assert-Equal $envelope.providerUsageAvailable $true "Resume provider usage availability"
    Assert-True ([long]$envelope.providerInputTokens -gt 0) `
        "Resume provider input usage is missing"
    Assert-True ([long]$envelope.selectedInputTokens -le [long]$envelope.effectiveWindow) `
        "Resume payload exceeded the effective context window"
    Assert-Equal $envelope.toolCount 0 "Resume final turn exposed tools"
    Assert-True ([long]$envelope.partitions.currentInput.tokens -gt 0) `
        "Resume current input partition is empty"

    Assert-Equal $descriptor.schemaVersion "agent-resume-descriptor-v1" `
        "Resume descriptor schema version"
    Assert-Equal $descriptor.modelId $script:expectedModelId "Resume descriptor model id"
    Assert-Equal $descriptor.sampling.temperature 0.1 "Resume descriptor temperature"
    Assert-Equal $descriptor.runtimeContextMode "SNAPSHOT" "Resume runtime context mode"
    Assert-True (-not [string]::IsNullOrWhiteSpace("$($descriptor.runtimeContextSnapshot)")) `
        "Resume runtime context snapshot is missing"
    Assert-True (-not [string]::IsNullOrWhiteSpace("$($descriptor.skillRuntimeContext)")) `
        "Resume skill runtime snapshot is missing"
    $skillRevision = @($descriptor.skillRevisions | Where-Object {
        "$($_.name)" -ceq $script:fixtureSkillName
    }) | Select-Object -First 1
    Assert-True ($null -ne $skillRevision) "Resume descriptor skill revision is missing"
    Assert-Equal $skillRevision.revisionId $script:fixtureSkillRevisionId `
        "Resume descriptor skill revision id"
    Assert-True ("$($skillRevision.contentHash)" -cmatch '^[0-9a-f]{64}$') `
        "Resume descriptor skill content hash is invalid"

    Assert-Equal $Evidence.PendingToolCall.toolId $script:fixtureToolId `
        "Checkpoint pending tool id"
    Assert-True ($null -eq $Evidence.PendingToolCall.PSObject.Properties['idempotencyKey']) `
        "Checkpoint persisted a raw idempotency key"
    $rawIdempotencyKey = "$($Evidence.Checkpoint.runId):$($Evidence.PendingToolCall.toolCallId)"
    $rawIdempotencyLiteral = ConvertTo-SqlLiteral $rawIdempotencyKey
    $resumeRunLiteral = ConvertTo-SqlLiteral "$($Evidence.Checkpoint.runId)"
    $resumeTenantLiteral = ConvertTo-SqlLiteral "$($Evidence.PendingToolCall.tenantId)"
    $rawIdempotencyRows = Invoke-PostgresScalar @"
SELECT (SELECT count(*) FROM sa_tool_invocation
        WHERE run_id=$resumeRunLiteral AND idempotency_key=$rawIdempotencyLiteral)
     + (SELECT count(*) FROM sa_agent_checkpoint
        WHERE run_id=$resumeRunLiteral
          AND position($rawIdempotencyLiteral in pending_tool_call_json) > 0)
     + (SELECT count(*) FROM sa_idempotency_key
        WHERE tenant_id=$resumeTenantLiteral AND idempotency_key=$rawIdempotencyLiteral);
"@
    Assert-Equal $rawIdempotencyRows 0 "Raw idempotency key durable rows"
    Assert-Equal $Evidence.ToolInput.toolId $script:fixtureToolId "Resumed tool step tool id"
    Assert-Equal $Evidence.ToolInput.toolCallId $Evidence.PendingToolCall.toolCallId `
        "Resumed tool call id"
    $assistantToolMessages = @($Evidence.CheckpointMessages | Where-Object {
        "$($_.role)" -eq 'ASSISTANT' -and @($_.toolCalls).Count -gt 0
    })
    Assert-True ($assistantToolMessages.Count -gt 0) `
        "Checkpoint did not persist the assistant structured tool call"
    $matchingCalls = @($assistantToolMessages.toolCalls | Where-Object {
        "$($_.toolCallId)" -eq "$($Evidence.ToolInput.toolCallId)" -and
        "$($_.toolId)" -eq $script:fixtureToolId
    })
    Assert-True ($matchingCalls.Count -gt 0) `
        "Checkpoint assistant tool call did not match the resumed tool result"
    Assert-Equal $Evidence.ToolOutput.success $true "Resumed tool execution success"
    Assert-True ("$($Evidence.ToolOutput.content)" -match 'UP') `
        "Real actuator health tool result did not contain UP"
    Assert-True ([long]$envelope.messageCount -ge 3) `
        "Resume Envelope did not include the structured tool-call/result pair"
    Assert-True ($null -eq $Evidence.ModelStep.errorCode) `
        "Resumed MODEL_TURN persisted an error code"
    Assert-True ($null -eq $Evidence.ModelStep.errorMessage) `
        "Resumed MODEL_TURN persisted an error message"
    Assert-True ($null -ne $Evidence.ModelOutput) "Resumed MODEL_TURN output is missing"
    Assert-True (-not $Evidence.ModelStepJson.Contains($RawMarker)) `
        "Resumed MODEL_TURN evidence leaked the fixture marker"
    Assert-True ($Evidence.ModelStepJson -notmatch '"(content|messages|tools|jsonSchema|description)"\s*:') `
        "Resumed MODEL_TURN input persisted raw checkpoint history"
    foreach ($field in @('thinking', 'thinkingContent', 'thinkingDuration')) {
        Assert-True (-not (Test-JsonTreeContainsProperty -Value $Evidence.CheckpointMessages `
                -PropertyName $field)) "Checkpoint persisted raw $field"
    }
    Assert-True ($null -ne $Evidence.PublicCheckpoint) "Sanitized checkpoint view is missing"
    $publicState = $Evidence.PublicCheckpoint.stateJson | ConvertFrom-Json
    $publicDescriptor = $publicState.resumeDescriptor
    Assert-True ($null -eq $publicDescriptor.runtimeContextSnapshot) `
        "Checkpoint API exposed the runtime context snapshot"
    Assert-True ($null -eq $publicDescriptor.skillRuntimeContext) `
        "Checkpoint API exposed the skill runtime snapshot"
    Assert-True ("$($publicDescriptor.runtimeContextHash)" -cmatch '^[0-9a-f]{64}$') `
        "Checkpoint API runtime context hash is invalid"
    Assert-True ("$($publicDescriptor.skillRuntimeContextHash)" -cmatch '^[0-9a-f]{64}$') `
        "Checkpoint API skill runtime hash is invalid"
    Assert-True ([long]$publicDescriptor.runtimeContextLength -gt 0) `
        "Checkpoint API runtime context length is missing"
    Assert-True ([long]$publicDescriptor.skillRuntimeContextLength -gt 0) `
        "Checkpoint API skill runtime length is missing"

    Assert-Equal $Evidence.ResumeTrace.traceName "agent-run-resume" "Resume RAG trace name"
    Assert-Equal $Evidence.ResumeTrace.status "SUCCESS" "Resume RAG trace status"
    Assert-True ("$($Evidence.ResumeTrace.traceId)" -cne "$($Evidence.OriginalTraceId)") `
        "Resume reused the original RAG trace"
    Assert-True (@($Evidence.ResumeTrace.nodeTypes) -ccontains 'AGENT_RESUME') `
        "Resume RAG trace is missing AGENT_RESUME"
    Assert-True (@($Evidence.ResumeTrace.nodeTypes) -ccontains 'AGENT_MODEL') `
        "Resume RAG trace is missing AGENT_MODEL"
}

function Invoke-KernelChatExpectFailure {
    param(
        [string]$ConversationId,
        [hashtable]$Headers,
        [string]$Question
    )

    $query = "conversationId=$ConversationId" +
        "&question=$([System.Uri]::EscapeDataString($Question))" +
        "&runProfileId=$KernelRunProfileId&chatMode=agent"
    $response = Invoke-WebRequest -Uri "$BaseUrl/rag/v3/chat?$query" `
        -Headers $Headers -UseBasicParsing -TimeoutSec 240
    Assert-Equal ([int]$response.StatusCode) 200 "Fail-closed chat HTTP status"
    Assert-True ("$($response.Headers['Content-Type'])" -like '*text/event-stream*') `
        "Fail-closed chat did not return text/event-stream"
    Assert-True ($response.Content -match '(?m)^event:\s*(error|recoverable_error)\s*$') `
        "Fail-closed chat did not emit a structured error event"

    $runMatches = [regex]::Matches($response.Content, '"runId"\s*:\s*"([^"\\]+)"')
    if ($runMatches.Count -eq 0) {
        throw "Fail-closed chat SSE did not include runId"
    }
    $runId = $runMatches[0].Groups[1].Value
    Assert-True ($runId -cmatch '^[A-Za-z0-9._-]+$') "Unsafe fail-closed runId: $runId"
    $script:runIds.Add($runId)
    return [pscustomobject]@{
        RunId = $runId
        Content = $response.Content
    }
}

function Get-EnvelopeEvidence {
    param(
        [string]$ConversationId,
        [string]$RunId
    )

    $runLiteral = ConvertTo-SqlLiteral $RunId
    $conversationLiteral = ConvertTo-SqlLiteral $ConversationId
    $stepJson = Wait-ForValue -Name "MODEL_TURN run step" -Query {
        Invoke-PostgresScalar @"
SELECT input_json
FROM sa_agent_step
WHERE run_id=$runLiteral AND step_type='MODEL_TURN'
ORDER BY step_no ASC, started_at ASC
LIMIT 1;
"@
    }
    $stepResultJson = Wait-ForValue -Name "MODEL_TURN output and error fields" -Query {
        Invoke-PostgresScalar @"
SELECT json_build_object(
           'outputJson', output_json,
           'errorCode', error_code,
           'errorMessage', error_message
       )::text
FROM sa_agent_step
WHERE run_id=$runLiteral AND step_type='MODEL_TURN'
ORDER BY step_no ASC, started_at ASC
LIMIT 1;
"@
    }
    $traceJson = Wait-ForValue -Name "AGENT_MODEL RAG trace node" -Query {
        Invoke-PostgresScalar @"
SELECT n.extra_data
FROM t_rag_trace_node n
JOIN t_rag_trace_run r ON r.trace_id=n.trace_id AND r.deleted=0
WHERE r.conversation_id=$conversationLiteral AND n.node_type='AGENT_MODEL' AND n.deleted=0
ORDER BY n.create_time ASC, n.id ASC
LIMIT 1;
"@
    }
    $otelTraceId = Wait-ForValue -Name "OTEL trace id" -Query {
        Invoke-PostgresScalar @"
SELECT COALESCE(NULLIF(trace_context_json, '')::jsonb ->> 'otelTraceId', '')
FROM t_run_context_snapshot
WHERE run_id=$runLiteral AND deleted=0
ORDER BY create_time DESC
LIMIT 1;
"@
    }
    $allTraceRowsJson = Wait-ForValue -Name "all RAG trace fields" -Query {
        Invoke-PostgresScalar @"
SELECT COALESCE(json_agg(row_to_json(rows) ORDER BY rows.kind, rows.id)::text, '[]')
FROM (
    SELECT 'run' AS kind, r.id, row_to_json(r) AS record
    FROM t_rag_trace_run r
    WHERE r.conversation_id=$conversationLiteral AND r.deleted=0
    UNION ALL
    SELECT 'node' AS kind, n.id, row_to_json(n) AS record
    FROM t_rag_trace_node n
    JOIN t_rag_trace_run r ON r.trace_id=n.trace_id AND r.deleted=0
    WHERE r.conversation_id=$conversationLiteral AND n.deleted=0
) rows;
"@
    }
    if ($otelTraceId -cnotmatch '^[0-9a-f]{32}$') {
        throw "Invalid OTEL trace id: $otelTraceId"
    }
    $stepResult = $stepResultJson | ConvertFrom-Json
    $stepOutput = if ([string]::IsNullOrWhiteSpace("$($stepResult.outputJson)")) {
        $null
    } else {
        $stepResult.outputJson | ConvertFrom-Json
    }

    return [pscustomobject]@{
        StepJson = $stepJson
        Step = $stepJson | ConvertFrom-Json
        StepResultJson = $stepResultJson
        StepResult = $stepResult
        StepOutput = $stepOutput
        TraceJson = $traceJson
        Trace = $traceJson | ConvertFrom-Json
        AllTraceRowsJson = $allTraceRowsJson
        OtelTraceId = $otelTraceId
    }
}

function Assert-Envelope {
    param(
        [string]$Name,
        [object]$Evidence,
        [string]$RawMarker,
        [int]$ExpectedHistoryMessageCount = $HistoryMessageCount
    )

    foreach ($copy in @($Evidence.Step, $Evidence.Trace)) {
        Assert-Equal $copy.schemaVersion "model-context-envelope-v1" "$Name schema version"
        Assert-Equal $copy.mode "ENFORCE" "$Name mode"
        Assert-Equal $copy.estimatorMode "CONSERVATIVE_FALLBACK" "$Name estimator mode"
        Assert-True ([long]$copy.safetyBuffer -ge 4096) `
            "$Name safety buffer dropped below the conservative baseline"
        Assert-True ([long]$copy.safetyBuffer -le ([long]$copy.contextWindow / 4)) `
            "$Name safety buffer exceeded the context-window ratio cap"
        Assert-True ("$($copy.contextWindowSource)" -like '*safe-profile:default-model') `
            "$Name did not use the explicit default-model safe profile"
        Assert-Equal $copy.reasonCode "OK" "$Name reason code"
        Assert-True ("$($copy.payloadHash)" -cmatch '^sha256:[0-9a-f]{64}$') `
            "$Name payload hash is invalid"
        Assert-Equal $copy.payloadHashSource "OPENAI_COMPATIBLE_WIRE_JSON" `
            "$Name payload hash source"
        Assert-Equal $copy.providerUsageAvailable $true "$Name provider usage availability"
        Assert-True ([long]$copy.providerInputTokens -gt 0) "$Name provider input usage is missing"
        Assert-True ([long]$copy.providerOutputTokens -ge 0) "$Name provider output usage is invalid"
        Assert-True ($null -ne $copy.estimatorDeltaTokens) "$Name estimator delta is missing"
        Assert-True ([long]$copy.providerInputTokens -le [long]$copy.effectiveWindow) `
            "$Name provider input usage exceeded the effective window"
        Assert-True ([long]$copy.estimatorDeltaTokens -ge 0) `
            "$Name conservative estimator undercounted provider input usage"
        Assert-Equal ($copy.contextWindow - $copy.outputReserve - $copy.safetyBuffer) `
            $copy.effectiveWindow "$Name effective window"
        Assert-Equal ($copy.effectiveWindow - $copy.fixedCost) $copy.historyBudget `
            "$Name history budget"
        Assert-True ([long]$copy.selectedInputTokens -le [long]$copy.effectiveWindow) `
            "$Name selected payload exceeded effective window"
        Assert-True ([long]$copy.remainingTokens -ge 0) "$Name remaining tokens became negative"
        Assert-True ([long]$copy.partitions.historicalMessages.tokens -gt 0) `
            "$Name did not retain any historical messages"
        $truncation = @($copy.decisions | Where-Object {
            "$($_.kind)" -eq "HISTORY_TRUNCATED" -and "$($_.reason)" -eq "HISTORY_BUDGET"
        })
        Assert-True ($truncation.Count -gt 0) "$Name did not prove dynamic history truncation"
        Assert-True ([int]$truncation[0].affectedMessages -gt 0) `
            "$Name truncation did not affect any messages"
        $selectedRefs = @($copy.selectedMessageRefs)
        $removedRefs = @($truncation[0].messageRefs)
        Assert-True ($selectedRefs.Count -gt 0) "$Name selected refs are missing"
        Assert-Equal ($selectedRefs.Count + $removedRefs.Count) $ExpectedHistoryMessageCount `
            "$Name replayable history ref coverage"
        $allRefs = @($selectedRefs + $removedRefs)
        Assert-Equal @($allRefs | Sort-Object -Unique).Count $allRefs.Count `
            "$Name replayable history refs uniqueness"
        Assert-True (@($allRefs | Where-Object { "$_" -cnotmatch '^history-message-\d+$' }).Count -eq 0) `
            "$Name replayable history refs contain an invalid id"
    }

    Assert-Equal $Evidence.Step.payloadHash $Evidence.Trace.payloadHash "$Name step/trace payload hash"
    $safeEvidence = $Evidence.StepJson + $Evidence.TraceJson + $Evidence.AllTraceRowsJson
    $safeEvidence += $Evidence.StepResultJson
    Assert-True (-not $safeEvidence.Contains($RawMarker)) "$Name evidence leaked the fixture marker"
    Assert-True ($safeEvidence -notmatch '"(content|messages|tools|jsonSchema|description)"\s*:') `
        "$Name evidence contained a raw prompt or tool-schema field"
    Assert-True ($null -ne $Evidence.StepOutput) "$Name MODEL_TURN output is missing"
    $outputFields = @($Evidence.StepOutput.PSObject.Properties.Name)
    Assert-True ($outputFields -cnotcontains 'thinking') `
        "$Name MODEL_TURN output persisted raw thinking"
    Assert-True ($outputFields -ccontains 'thinkingPresent') `
        "$Name MODEL_TURN output did not persist safe thinking metadata"
    Assert-True ($outputFields -ccontains 'thinkingChars') `
        "$Name MODEL_TURN output did not persist thinking character count"
    Assert-True ([long]$Evidence.StepOutput.thinkingChars -ge 0) `
        "$Name MODEL_TURN output persisted an invalid thinking character count"
    Assert-True ($null -eq $Evidence.StepResult.errorCode) "$Name MODEL_TURN persisted an error code"
    Assert-True ($null -eq $Evidence.StepResult.errorMessage) "$Name MODEL_TURN persisted an error message"
    Assert-True (-not (Test-JsonTreeContainsProperty `
            -Value ($Evidence.AllTraceRowsJson | ConvertFrom-Json) `
            -PropertyName 'thinking')) `
        "$Name RAG Trace persisted raw thinking"
}

function Assert-FailClosedEnvelope {
    param(
        [object]$Evidence,
        [string]$RawMarker
    )

    foreach ($copy in @($Evidence.Step, $Evidence.Trace)) {
        Assert-Equal $copy.schemaVersion "model-context-envelope-v1" "Fail-closed schema version"
        Assert-Equal $copy.mode "ENFORCE" "Fail-closed mode"
        Assert-Equal $copy.reasonCode "CONTEXT_BUDGET_EXCEEDED" "Fail-closed reason code"
        Assert-Equal $copy.payloadHashSource "KERNEL_CANONICAL" "Fail-closed payload hash source"
        Assert-Equal $copy.providerUsageAvailable $false "Fail-closed provider usage availability"
        Assert-True ("$($copy.payloadHash)" -cmatch '^sha256:[0-9a-f]{64}$') `
            "Fail-closed payload hash is invalid"
        Assert-True ([long]$copy.selectedInputTokens -gt [long]$copy.effectiveWindow) `
            "Fail-closed request did not prove the selected payload exceeded the effective window"
        $decision = @($copy.decisions | Where-Object {
            "$($_.kind)" -eq "FAIL_CLOSED" -and "$($_.reason)" -eq "CONTEXT_BUDGET_EXCEEDED"
        })
        Assert-True ($decision.Count -gt 0) "Fail-closed evidence is missing the decision"
    }

    Assert-Equal $Evidence.Step.payloadHash $Evidence.Trace.payloadHash `
        "Fail-closed step/trace payload hash"
    Assert-True ($null -eq $Evidence.StepOutput) "Fail-closed MODEL_TURN unexpectedly stored output"
    Assert-True ($null -ne $Evidence.StepResult.errorCode) "Fail-closed MODEL_TURN error code is missing"
    Assert-True ($null -ne $Evidence.StepResult.errorMessage) "Fail-closed MODEL_TURN error message is missing"
    $safeEvidence = $Evidence.StepJson + $Evidence.TraceJson + $Evidence.AllTraceRowsJson
    $safeEvidence += $Evidence.StepResultJson
    Assert-True (-not $safeEvidence.Contains($RawMarker)) "Fail-closed evidence leaked the fixture marker"
    Assert-True (-not (Test-JsonTreeContainsProperty `
            -Value ($Evidence.AllTraceRowsJson | ConvertFrom-Json) `
            -PropertyName 'thinking')) `
        "Fail-closed RAG Trace persisted raw thinking"
}

function Assert-JaegerEnvelopeTags {
    param(
        [string]$Name,
        [object]$Evidence,
        [string]$RawMarker
    )

    $trace = $null
    for ($attempt = 1; $attempt -le 45; $attempt++) {
        try {
            $response = Invoke-RestMethod -Uri "$($JaegerUrl.TrimEnd('/'))/api/traces/$($Evidence.OtelTraceId)" `
                -TimeoutSec 10
            $trace = @($response.data | Where-Object { $null -ne $_ }) | Select-Object -First 1
        } catch {
            $trace = $null
        }
        if ($null -ne $trace) {
            break
        }
        Start-Sleep -Seconds 1
    }
    if ($null -eq $trace) {
        throw "$Name trace was not queryable from Jaeger"
    }

    $modelSpans = @($trace.spans | Where-Object { "$($_.operationName)" -eq "model.call" })
    Assert-True ($modelSpans.Count -gt 0) "$Name Jaeger trace did not include model.call"
    $matchingSpan = $null
    foreach ($span in $modelSpans) {
        $tagMap = @{}
        foreach ($tag in @($span.tags)) {
            $tagMap["$($tag.key)"] = "$($tag.value)"
        }
        if ($tagMap['seahorse.context.payload_hash'] -eq "$($Evidence.Step.payloadHash)") {
            $matchingSpan = $span
            break
        }
    }
    Assert-True ($null -ne $matchingSpan) "$Name model.call did not carry the Envelope payload hash"

    $tags = @{}
    foreach ($tag in @($matchingSpan.tags)) {
        $tags["$($tag.key)"] = "$($tag.value)"
    }
    $expected = @{
        'seahorse.context.mode' = "$($Evidence.Step.mode)"
        'seahorse.context.payload_hash_source' = "$($Evidence.Step.payloadHashSource)"
        'seahorse.context.estimator_mode' = "$($Evidence.Step.estimatorMode)"
        'seahorse.context.window' = "$($Evidence.Step.contextWindow)"
        'seahorse.context.output_reserve' = "$($Evidence.Step.outputReserve)"
        'seahorse.context.safety_buffer' = "$($Evidence.Step.safetyBuffer)"
        'seahorse.context.effective_window' = "$($Evidence.Step.effectiveWindow)"
        'seahorse.context.selected_input_tokens' = "$($Evidence.Step.selectedInputTokens)"
        'seahorse.context.remaining_tokens' = "$($Evidence.Step.remainingTokens)"
        'seahorse.context.provider_usage_available' = "$($Evidence.Step.providerUsageAvailable)".ToLowerInvariant()
        'seahorse.context.reason_code' = "$($Evidence.Step.reasonCode)"
    }
    foreach ($key in $expected.Keys) {
        Assert-Equal $tags[$key] $expected[$key] "$Name Jaeger tag $key"
    }
    if ($Evidence.Step.providerUsageAvailable) {
        foreach ($field in @(
            @{ Tag = 'seahorse.context.provider_input_tokens'; Evidence = 'providerInputTokens' },
            @{ Tag = 'seahorse.context.provider_output_tokens'; Evidence = 'providerOutputTokens' },
            @{ Tag = 'seahorse.context.estimator_delta_tokens'; Evidence = 'estimatorDeltaTokens' }
        )) {
            Assert-Equal $tags[$field.Tag] $Evidence.Step.($field.Evidence) "$Name Jaeger tag $($field.Tag)"
        }
    }
    $tagJson = $matchingSpan.tags | ConvertTo-Json -Depth 10 -Compress
    Assert-True (-not $tagJson.Contains($RawMarker)) "$Name Jaeger tags leaked the fixture marker"
    $traceJson = $trace | ConvertTo-Json -Depth 100 -Compress
    Assert-True (-not $traceJson.Contains($RawMarker)) "$Name Jaeger trace leaked the fixture marker"
    Assert-True (-not (Test-JsonTreeContainsProperty -Value $trace -PropertyName 'thinking')) `
        "$Name Jaeger trace persisted raw thinking"
}

function Assert-JaegerResumeTrace {
    param(
        [object]$Evidence,
        [string]$RawMarker
    )

    $tags = @{ 'seahorse.trace.id' = "$($Evidence.ResumeTrace.traceId)" } |
        ConvertTo-Json -Compress
    $encodedTags = [System.Uri]::EscapeDataString($tags)
    $trace = $null
    for ($attempt = 1; $attempt -le 45; $attempt++) {
        try {
            $response = Invoke-RestMethod -Uri `
                "$($JaegerUrl.TrimEnd('/'))/api/traces?service=seahorse-agent-service&lookback=1h&limit=20&tags=$encodedTags" `
                -TimeoutSec 10
            $trace = @($response.data | Where-Object { $null -ne $_ }) | Select-Object -First 1
        } catch {
            $trace = $null
        }
        if ($null -ne $trace) {
            break
        }
        Start-Sleep -Seconds 1
    }
    Assert-True ($null -ne $trace) "Resume trace was not queryable from Jaeger"
    Assert-True ("$($trace.traceID)" -cmatch '^[0-9a-f]{32}$') `
        "Resume OTEL trace id is invalid"
    Assert-True ("$($trace.traceID)" -cne "$($Evidence.OriginalOtelTraceId)") `
        "Resume reused the ended original OTEL trace"

    $root = @($trace.spans | Where-Object { "$($_.operationName)" -ceq 'agent.run' }) |
        Select-Object -First 1
    Assert-True ($null -ne $root) "Resume Jaeger trace is missing agent.run"
    $rootTags = @{}
    foreach ($tag in @($root.tags)) {
        $rootTags["$($tag.key)"] = "$($tag.value)"
    }
    Assert-Equal $rootTags['seahorse.trace.name'] 'agent-run-resume' `
        "Resume Jaeger trace name"
    Assert-Equal $rootTags['seahorse.operation'] 'agent-run-resume' `
        "Resume Jaeger operation"
    Assert-Equal $rootTags['seahorse.resume.original_run_id'] $Evidence.PendingToolCall.runId `
        "Resume Jaeger original run id"
    Assert-Equal $rootTags['seahorse.resume.original_trace_id'] $Evidence.OriginalTraceId `
        "Resume Jaeger original trace id"
    Assert-Equal $rootTags['seahorse.resume.checkpoint_id'] $Evidence.Checkpoint.checkpointId `
        "Resume Jaeger checkpoint id"

    Assert-True (@($trace.spans | Where-Object { "$($_.operationName)" -ceq 'model.call' }).Count -gt 0) `
        "Resume Jaeger trace is missing model.call"
    $traceJson = $trace | ConvertTo-Json -Depth 100 -Compress
    Assert-True (-not $traceJson.Contains($RawMarker)) "Resume Jaeger trace leaked the fixture marker"
    Assert-True (-not (Test-JsonTreeContainsProperty -Value $trace -PropertyName 'thinking')) `
        "Resume Jaeger trace persisted raw thinking"
}

function Remove-Fixtures {
    $cleanupErrors = [System.Collections.Generic.List[string]]::new()
    foreach ($runId in @($runIds)) {
        if ($runId -cnotmatch '^[A-Za-z0-9._-]+$') {
            continue
        }
        $runLiteral = ConvertTo-SqlLiteral $runId
        try {
            Invoke-PostgresNonQuery @"
DELETE FROM sa_agent_artifact WHERE run_id=$runLiteral;
DELETE FROM sa_tool_invocation WHERE run_id=$runLiteral;
DELETE FROM sa_approval_request WHERE run_id=$runLiteral;
DELETE FROM sa_agent_run_event_buffer WHERE run_id=$runLiteral;
DELETE FROM sa_agent_run_lease WHERE run_id=$runLiteral;
DELETE FROM sa_agent_checkpoint WHERE run_id=$runLiteral;
DELETE FROM sa_agent_step WHERE run_id=$runLiteral;
DELETE FROM t_run_context_snapshot WHERE run_id=$runLiteral;
DELETE FROM sa_agent_run WHERE run_id=$runLiteral;
"@
        } catch {
            $cleanupErrors.Add("run ${runId}: $($_.Exception.Message)")
        }
    }

    foreach ($conversationId in @($conversationIds)) {
        if ($conversationId -cnotmatch '^\d+$') {
            continue
        }
        try {
            Invoke-PostgresNonQuery @"
DELETE FROM t_rag_trace_node
WHERE trace_id IN (SELECT trace_id FROM t_rag_trace_run WHERE conversation_id='$conversationId');
DELETE FROM t_rag_trace_run WHERE conversation_id='$conversationId';
DELETE FROM t_conversation_branch_cursor WHERE conversation_id=$conversationId;
DELETE FROM t_conversation_summary WHERE conversation_id=$conversationId;
DELETE FROM t_message WHERE conversation_id=$conversationId;
DELETE FROM t_conversation WHERE id=$conversationId;
"@
        } catch {
            $cleanupErrors.Add("conversation ${conversationId}: $($_.Exception.Message)")
        }
    }

    foreach ($profileId in @($fixtureProfileIds)) {
        try {
            Invoke-PostgresNonQuery @"
DELETE FROM sa_run_profile_tool WHERE profile_id=$profileId AND tenant_id='default';
DELETE FROM sa_run_profile WHERE id=$profileId AND tenant_id='default';
"@
        } catch {
            $cleanupErrors.Add("run profile ${profileId}: $($_.Exception.Message)")
        }
    }
    foreach ($roleCardId in @($fixtureRoleCardIds)) {
        try {
            Invoke-PostgresNonQuery "DELETE FROM sa_role_card WHERE id=$roleCardId AND tenant_id='default';"
        } catch {
            $cleanupErrors.Add("role card ${roleCardId}: $($_.Exception.Message)")
        }
    }
    foreach ($skillName in @($fixtureSkillNames)) {
        $skillLiteral = ConvertTo-SqlLiteral $skillName
        try {
            Invoke-PostgresNonQuery @"
DELETE FROM sa_agent_skill_binding WHERE tenant_id='default' AND skill_name=$skillLiteral;
DELETE FROM sa_agent_skill_revision WHERE tenant_id='default' AND skill_name=$skillLiteral;
DELETE FROM sa_agent_skill WHERE tenant_id='default' AND skill_name=$skillLiteral;
"@
        } catch {
            $cleanupErrors.Add("skill ${skillName}: $($_.Exception.Message)")
        }
    }
    foreach ($toolId in @($fixtureToolIds)) {
        $toolLiteral = ConvertTo-SqlLiteral $toolId
        try {
            Invoke-PostgresNonQuery "DELETE FROM sa_tool_catalog WHERE tool_id=$toolLiteral;"
        } catch {
            $cleanupErrors.Add("tool ${toolId}: $($_.Exception.Message)")
        }
    }
    foreach ($connectorId in @($fixtureConnectorIds)) {
        $connectorLiteral = ConvertTo-SqlLiteral $connectorId
        try {
            Invoke-PostgresNonQuery @"
DELETE FROM sa_connector_credential_binding WHERE connector_id=$connectorLiteral;
DELETE FROM sa_connector_operation WHERE connector_id=$connectorLiteral;
DELETE FROM sa_connector_version WHERE connector_id=$connectorLiteral;
DELETE FROM sa_connector WHERE connector_id=$connectorLiteral;
"@
        } catch {
            $cleanupErrors.Add("connector ${connectorId}: $($_.Exception.Message)")
        }
    }

    foreach ($runId in @($runIds)) {
        $runLiteral = ConvertTo-SqlLiteral $runId
        $residual = Invoke-PostgresScalar @"
SELECT (SELECT count(*) FROM sa_agent_run WHERE run_id=$runLiteral)
     + (SELECT count(*) FROM sa_agent_step WHERE run_id=$runLiteral)
     + (SELECT count(*) FROM sa_agent_checkpoint WHERE run_id=$runLiteral)
     + (SELECT count(*) FROM sa_approval_request WHERE run_id=$runLiteral)
     + (SELECT count(*) FROM sa_tool_invocation WHERE run_id=$runLiteral)
     + (SELECT count(*) FROM sa_agent_run_event_buffer WHERE run_id=$runLiteral)
     + (SELECT count(*) FROM sa_agent_run_lease WHERE run_id=$runLiteral)
     + (SELECT count(*) FROM sa_agent_artifact WHERE run_id=$runLiteral)
     + (SELECT count(*) FROM t_run_context_snapshot WHERE run_id=$runLiteral);
"@
        if ("$residual" -ne "0") {
            $cleanupErrors.Add("run ${runId}: residual row count=$residual")
        }
    }
    foreach ($conversationId in @($conversationIds)) {
        $residual = Invoke-PostgresScalar @"
SELECT (SELECT count(*) FROM t_conversation WHERE id=$conversationId)
     + (SELECT count(*) FROM t_message WHERE conversation_id=$conversationId)
     + (SELECT count(*) FROM t_rag_trace_run WHERE conversation_id='$conversationId');
"@
        if ("$residual" -ne "0") {
            $cleanupErrors.Add("conversation ${conversationId}: residual row count=$residual")
        }
    }
    foreach ($profileId in @($fixtureProfileIds)) {
        $residual = Invoke-PostgresScalar @"
SELECT (SELECT count(*) FROM sa_run_profile WHERE id=$profileId)
     + (SELECT count(*) FROM sa_run_profile_tool WHERE profile_id=$profileId);
"@
        if ("$residual" -ne "0") {
            $cleanupErrors.Add("run profile ${profileId}: residual row count=$residual")
        }
    }
    foreach ($roleCardId in @($fixtureRoleCardIds)) {
        $residual = Invoke-PostgresScalar "SELECT count(*) FROM sa_role_card WHERE id=$roleCardId;"
        if ("$residual" -ne "0") {
            $cleanupErrors.Add("role card ${roleCardId}: residual row count=$residual")
        }
    }
    foreach ($skillName in @($fixtureSkillNames)) {
        $skillLiteral = ConvertTo-SqlLiteral $skillName
        $residual = Invoke-PostgresScalar @"
SELECT (SELECT count(*) FROM sa_agent_skill WHERE tenant_id='default' AND skill_name=$skillLiteral)
     + (SELECT count(*) FROM sa_agent_skill_revision WHERE tenant_id='default' AND skill_name=$skillLiteral)
     + (SELECT count(*) FROM sa_agent_skill_binding WHERE tenant_id='default' AND skill_name=$skillLiteral);
"@
        if ("$residual" -ne "0") {
            $cleanupErrors.Add("skill ${skillName}: residual row count=$residual")
        }
    }
    foreach ($connectorId in @($fixtureConnectorIds)) {
        $connectorLiteral = ConvertTo-SqlLiteral $connectorId
        $residual = Invoke-PostgresScalar @"
SELECT (SELECT count(*) FROM sa_connector WHERE connector_id=$connectorLiteral)
     + (SELECT count(*) FROM sa_connector_version WHERE connector_id=$connectorLiteral)
     + (SELECT count(*) FROM sa_connector_operation WHERE connector_id=$connectorLiteral)
     + (SELECT count(*) FROM sa_connector_credential_binding WHERE connector_id=$connectorLiteral);
"@
        if ("$residual" -ne "0") {
            $cleanupErrors.Add("connector ${connectorId}: residual row count=$residual")
        }
    }
    foreach ($toolId in @($fixtureToolIds)) {
        $toolLiteral = ConvertTo-SqlLiteral $toolId
        $residual = Invoke-PostgresScalar "SELECT count(*) FROM sa_tool_catalog WHERE tool_id=$toolLiteral;"
        if ("$residual" -ne "0") {
            $cleanupErrors.Add("tool ${toolId}: residual row count=$residual")
        }
    }
    if ($cleanupErrors.Count -gt 0) {
        throw "Fixture cleanup failed: $($cleanupErrors -join '; ')"
    }
}

if ([string]::IsNullOrWhiteSpace($Marker)) {
    $Marker = "context-envelope-e2e-$([guid]::NewGuid().ToString('N'))"
}
if ($Marker -cnotmatch '^[A-Za-z0-9._-]+$') {
    throw "Marker must contain only letters, digits, dot, underscore, or hyphen"
}
$fixtureSuffix = $Marker.Substring([Math]::Max(0, $Marker.Length - 16))
$fixtureIdBase = [long](([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() * 1000) +
    (Get-Random -Minimum 100 -Maximum 900))
$fixtureConnectorId = "ctxenv_$fixtureSuffix"
$fixtureConnectorVersionId = "ctxenvv_$fixtureSuffix"
$fixtureOperationId = "ctxenvo_$fixtureSuffix"
$fixtureToolId = "openapi_ctxenv_$fixtureSuffix"
$fixtureSkillName = "ctxenv-$fixtureSuffix".ToLowerInvariant()
$fixtureSkillRevisionId = "ctxenvr_$fixtureSuffix"

try {
    Test-Step "Full-Docker backend is healthy with production Envelope and OTEL enabled" {
        $health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -TimeoutSec 15
        Assert-Equal $health.status "UP" "Backend health"
        $envLines = @(& docker.exe inspect $BackendContainer --format '{{range .Config.Env}}{{println .}}{{end}}')
        foreach ($required in @(
            'SEAHORSE_AGENT_CHAT_AGENT_CONTEXT_ENVELOPE_MODE=ENFORCE',
            'SEAHORSE_AGENT_CHAT_AGENT_CONTEXT_ENVELOPE_DEFAULT_CONTEXT_WINDOW_TOKENS=32768',
            'SEAHORSE_AGENT_CHAT_AGENT_CONTEXT_ENVELOPE_DEFAULT_MODEL_SAFE_PROFILE_ENABLED=true',
            'SEAHORSE_AGENT_CHAT_AGENT_CONTEXT_ENVELOPE_DEFAULT_OUTPUT_RESERVE_TOKENS=8192',
            'SEAHORSE_AGENT_CHAT_AGENT_CONTEXT_ENVELOPE_CONSERVATIVE_SAFETY_BUFFER_TOKENS=4096',
            'SPRING_DATA_REDIS_HOST=model-context-e2e-redis',
            'SPRING_DATA_REDIS_PORT=6379',
            'SEAHORSE_OBSERVABILITY_TRACING_ENABLED=true',
            'MANAGEMENT_TRACING_ENABLED=true'
        )) {
            Assert-True ($envLines -contains $required) "Missing backend environment setting: $required"
        }
        $modelSetting = @($envLines | Where-Object {
            "$_" -like 'SEAHORSE_AGENT_ADAPTERS_AI_CHAT_MODEL=*'
        }) | Select-Object -First 1
        Assert-True (-not [string]::IsNullOrWhiteSpace("$modelSetting")) `
            "Backend model id setting is missing"
        $script:expectedModelId = "$modelSetting".Substring("$modelSetting".IndexOf('=') + 1)
        Assert-True (-not [string]::IsNullOrWhiteSpace($script:expectedModelId)) `
            "Backend model id is blank"
        Assert-True ($envLines -contains `
                'SEAHORSE_AGENT_ADAPTERS_AI_BASE_URL=http://model-provider-e2e-proxy:8080/v1') `
            "Backend is not routed through the provider-side E2E recorder"
        $noProxySetting = @($envLines | Where-Object { "$_" -like 'NO_PROXY=*' }) |
            Select-Object -First 1
        Assert-True ("$noProxySetting" -match '(^|,)model-provider-e2e-proxy(,|$)') `
            "Backend HTTP proxy bypass is missing the provider-side E2E recorder"
        $proxyHealth = Invoke-RestMethod -Uri "$($ProviderProxyUrl.TrimEnd('/'))/healthz" -TimeoutSec 10
        Assert-Equal $proxyHealth.status "UP" "Provider-side recorder health"
        Set-ProviderProxyConfig -CountMarker $Marker | Out-Null
    } | Out-Null

    Test-Step "Clear the exact calibration key in the isolated E2E Redis" {
        $identity = "CalibrationKey[modelId=$($script:expectedModelId.ToLowerInvariant()), " +
            "estimatorMode=$ExpectedEstimatorMode, estimatorVersion=$ExpectedEstimatorVersion]"
        $key = 'seahorse:agent:cache:model-context-envelope:calibration:v1:' + `
            (Get-Sha256Hex $identity)
        $script:calibrationKey = $key
        Invoke-Redis UNLINK $key | Out-Null
        Assert-Equal "$(Invoke-Redis EXISTS $key | Select-Object -First 1)" "0" `
            "Cleared exact calibration key"
    } | Out-Null

    $login = Test-Step "Authenticate a real user" {
        $response = Invoke-Api -Method POST -Path "/auth/login" -Body @{
            username = $Username
            password = $Password
        }
        Assert-ApiOk $response "Login"
        Assert-True (-not [string]::IsNullOrWhiteSpace("$($response.data.token)")) `
            "Login response did not include a token"
        Assert-True ("$($response.data.userId)" -cmatch '^\d+$') `
            "Login response did not include a numeric user id"
        return $response.data
    }
    $headers = @{
        Authorization = "Bearer $($login.token)"
        'X-User-Id' = "$($login.userId)"
    }

    Test-Step "Create script-owned profile, OpenAPI tool, and skill fixtures" {
        New-FixedCostFixtures -UserId "$($login.userId)"
    } | Out-Null

    $shortConversationId = Test-Step "Persist deterministic long history for the short-input run" {
        $conversationId = New-Conversation -Headers $headers
        Add-LongHistory -ConversationId $conversationId -UserId "$($login.userId)" `
            -HistoryMarker "$Marker-short"
        return $conversationId
    }
    $longConversationId = Test-Step "Persist equivalent long history for the long-input run" {
        $conversationId = New-Conversation -Headers $headers
        Add-LongHistory -ConversationId $conversationId -UserId "$($login.userId)" `
            -HistoryMarker "$Marker-long"
        return $conversationId
    }
    $failClosedConversationId = Test-Step "Persist an oversized most-recent completed turn" {
        $conversationId = New-Conversation -Headers $headers
        Add-OversizedRecentTurn -ConversationId $conversationId -UserId "$($login.userId)" `
            -HistoryMarker "$Marker-fail"
        return $conversationId
    }
    $partitionBaselineConversationId = Test-Step "Persist baseline history for the role-card system prompt" {
        $conversationId = New-Conversation -Headers $headers
        Add-LongHistory -ConversationId $conversationId -UserId "$($login.userId)" `
            -HistoryMarker "$Marker-partition-baseline" `
            -MessageCount $PartitionHistoryMessageCount `
            -CharactersPerMessage $PartitionHistoryCharactersPerMessage
        return $conversationId
    }
    $partitionExpandedConversationId = Test-Step "Persist equivalent history for the enlarged role-card prompt" {
        $conversationId = New-Conversation -Headers $headers
        Add-LongHistory -ConversationId $conversationId -UserId "$($login.userId)" `
            -HistoryMarker "$Marker-partition-expanded" `
            -MessageCount $PartitionHistoryMessageCount `
            -CharactersPerMessage $PartitionHistoryCharactersPerMessage
        return $conversationId
    }
    $approvalConversationId = Test-Step "Create a dedicated conversation for approval and resume" {
        New-Conversation -Headers $headers
    }

    $shortQuestion = "$Marker-short-current: answer only CONTEXT_ENVELOPE_SHORT_OK and do not call tools."
    $longQuestion = "$Marker-long-current: answer only CONTEXT_ENVELOPE_LONG_OK and do not call tools. " + `
        ('L' * $LongQuestionCharacters)
    $shortChat = Test-Step "Execute the short-input request through real Kernel Agent SSE" {
        Invoke-KernelChat -ConversationId $shortConversationId -Headers $headers -Question $shortQuestion
    }
    $shortEvidence = Test-Step "Read safe Envelope evidence from the first real provider request" {
        $evidence = Get-EnvelopeEvidence -ConversationId $shortConversationId -RunId $shortChat.RunId
        Assert-Envelope -Name "Short input" -Evidence $evidence -RawMarker $Marker
        Assert-Equal $evidence.Step.estimatorMode $ExpectedEstimatorMode "Expected estimator mode"
        Assert-Equal $evidence.Step.estimatorVersion $ExpectedEstimatorVersion "Expected estimator version"
        return $evidence
    }
    $calibrationEvidence = Test-Step "Inject bounded calibration for the observed model and estimator" {
        Set-ControlledCalibration -EnvelopeEvidence $shortEvidence.Step
    }
    Test-Step "Restart backend before consuming shared calibration" {
        & docker.exe restart $BackendContainer | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "docker restart exited with $LASTEXITCODE"
        }
        Wait-BackendHealthy
    } | Out-Null
    $longChat = Test-Step "Execute the long-input request through real Kernel Agent SSE" {
        Invoke-KernelChat -ConversationId $longConversationId -Headers $headers -Question $longQuestion
    }
    $failClosedProviderCountBefore = Test-Step "Capture provider-side count before fail-closed request" {
        [long](Get-ProviderProxyStats).markerRequests
    }
    $failClosedQuestion = "$Marker-fail-current: this request must fail before the model provider."
    $failClosedChat = Test-Step "Fail closed before provider for an oversized recent turn" {
        Invoke-KernelChatExpectFailure -ConversationId $failClosedConversationId `
            -Headers $headers -Question $failClosedQuestion
    }
    $failClosedProviderCountAfter = Test-Step "Capture provider-side count after fail-closed request" {
        [long](Get-ProviderProxyStats).markerRequests
    }
    $partitionBaselineQuestion = "$Marker-partition-baseline-current: answer only CONTEXT_ENVELOPE_PARTITION_BASELINE_OK."
    $partitionExpandedQuestion = "$Marker-partition-expanded-current: answer only CONTEXT_ENVELOPE_PARTITION_EXPANDED_OK and do not call tools. " + `
        ('I' * $ExpandedCurrentInputCharacters)
    $partitionBaselineChat = Test-Step "Execute fixed-cost baseline through real Kernel Agent SSE" {
        Invoke-KernelChat -ConversationId $partitionBaselineConversationId -Headers $headers `
            -Question $partitionBaselineQuestion -RunProfileId $fixtureIdBase
    }
    $partitionExpandedChat = Test-Step "Execute enlarged system, skill, tool schema, and input together" {
        Invoke-KernelChat -ConversationId $partitionExpandedConversationId -Headers $headers `
            -Question $partitionExpandedQuestion -RunProfileId ($fixtureIdBase + 1) `
            -SelectedSkillNames @($fixtureSkillName)
    }
    $approvalQuestion = "$Marker-approval-current: You must call the tool named " + `
        "$fixtureToolId exactly once with query set to health. Do not answer without calling that exact tool."
    $approvalChat = Test-Step "Enter WAITING_APPROVAL through a real structured OpenAPI tool call" {
        $chat = Invoke-KernelChat -ConversationId $approvalConversationId -Headers $headers `
            -Question $approvalQuestion -RunProfileId ($fixtureIdBase + 1) `
            -SelectedSkillNames @($fixtureSkillName)
        Assert-True ($chat.Content.Contains('Waiting for tool approval.')) `
            "Approval chat did not enter the waiting state"
        return $chat
    }
    $pendingApproval = Test-Step "Read the real pending approval for the Kernel Agent run" {
        $run = Invoke-Api -Method GET -Path "/api/agent-runs/$($approvalChat.RunId)" -Headers $headers
        Assert-ApiOk $run "Read waiting Agent run"
        Assert-Equal $run.data.status "WAITING_APPROVAL" "Waiting Agent run status"
        Get-PendingApproval -RunId $approvalChat.RunId -Headers $headers
    }
    Test-Step "Approve the real OpenAPI tool invocation" {
        $response = Invoke-Api -Method POST `
            -Path "/api/approvals/$($pendingApproval.approvalId)/approve" `
            -Headers $headers -Body @{ decisionComment = 'Envelope resume E2E approval' }
        Assert-ApiOk $response "Approve Envelope tool invocation"
        Assert-Equal $response.data.status "APPROVED" "Approved request status"
    } | Out-Null
    Test-Step "Persist the post-crash RUNNING state with an expired resume lease" {
        $runLiteral = ConvertTo-SqlLiteral $approvalChat.RunId
        Invoke-PostgresNonQuery @"
BEGIN;
UPDATE sa_agent_run SET status='RUNNING' WHERE run_id=$runLiteral AND status='WAITING_APPROVAL';
INSERT INTO sa_agent_run_lease (run_id, worker_id, lease_until, heartbeat_at)
VALUES ($runLiteral, 'e2e-crashed-resume-owner', CURRENT_TIMESTAMP - interval '1 second',
        CURRENT_TIMESTAMP - interval '2 seconds')
ON CONFLICT (run_id) DO UPDATE
SET worker_id=EXCLUDED.worker_id,
    lease_until=EXCLUDED.lease_until,
    heartbeat_at=EXCLUDED.heartbeat_at;
COMMIT;
"@
        Assert-Equal (Invoke-PostgresScalar `
                "SELECT status FROM sa_agent_run WHERE run_id=$runLiteral LIMIT 1;") "RUNNING" `
            "Simulated crashed resume run status"
    } | Out-Null
    $resumedRun = Test-Step "Reclaim the expired lease and resume through the production API" {
        $response = Invoke-Api -Method POST -Path "/api/agent-runs/$($approvalChat.RunId)/resume" `
            -Headers $headers
        Assert-ApiOk $response "Resume approved Agent run"
        Assert-Equal $response.data.status "SUCCEEDED" "Resumed Agent run status"
        return $response.data
    }

    $longEvidence = Test-Step "Read long-input Envelope evidence from run step and RAG Trace" {
        $evidence = Get-EnvelopeEvidence -ConversationId $longConversationId -RunId $longChat.RunId
        Assert-Envelope -Name "Long input" -Evidence $evidence -RawMarker $Marker
        return $evidence
    }
    $failClosedEvidence = Test-Step "Read fail-closed Envelope evidence from run step and RAG Trace" {
        $evidence = Get-EnvelopeEvidence `
            -ConversationId $failClosedConversationId -RunId $failClosedChat.RunId
        Assert-FailClosedEnvelope -Evidence $evidence -RawMarker $Marker
        return $evidence
    }
    $partitionBaselineEvidence = Test-Step "Read fixed-cost baseline Envelope evidence" {
        $evidence = Get-EnvelopeEvidence `
            -ConversationId $partitionBaselineConversationId -RunId $partitionBaselineChat.RunId
        Assert-Envelope -Name "Fixed-cost baseline" -Evidence $evidence -RawMarker $Marker `
            -ExpectedHistoryMessageCount $PartitionHistoryMessageCount
        return $evidence
    }
    $partitionExpandedEvidence = Test-Step "Read four-partition enlarged Envelope evidence" {
        $evidence = Get-EnvelopeEvidence `
            -ConversationId $partitionExpandedConversationId -RunId $partitionExpandedChat.RunId
        Assert-Envelope -Name "Four-partition expanded" -Evidence $evidence -RawMarker $Marker `
            -ExpectedHistoryMessageCount $PartitionHistoryMessageCount
        return $evidence
    }
    $resumeEvidence = Test-Step "Prove the resumed final model turn used the Envelope" {
        $evidence = Get-ResumeEvidence -RunId $approvalChat.RunId -Headers $headers
        Assert-ResumeEnvelope -Evidence $evidence -RawMarker $Marker
        return $evidence
    }

    Test-Step "Prove the current input dynamically reduces history budget" {
        Assert-True ([long]$longEvidence.Step.partitions.currentInput.tokens -gt `
                [long]$shortEvidence.Step.partitions.currentInput.tokens) `
            "Long current input did not consume more tokens"
        Assert-True ([long]$longEvidence.Step.historyBudget -lt [long]$shortEvidence.Step.historyBudget) `
            "Long current input did not reduce history budget"
        Assert-True ([long]$longEvidence.Step.fixedCost -gt [long]$shortEvidence.Step.fixedCost) `
            "Long current input did not increase fixed cost"
    } | Out-Null

    Test-Step "Prove Redis calibration survives process restart" {
        $expectedSafetyBuffer = [Math]::Min(
            [long]$longEvidence.Step.contextWindow / 4,
            4096 + [long]$calibrationEvidence.Tokens)
        Assert-Equal $longEvidence.Step.safetyBuffer $expectedSafetyBuffer `
            "Restarted backend shared calibration safety buffer"
        Assert-Equal $longEvidence.Step.effectiveWindow `
            ([long]$longEvidence.Step.contextWindow - [long]$longEvidence.Step.outputReserve - `
                $expectedSafetyBuffer) `
            "Shared calibration effective context window"
        Assert-Equal "$(Invoke-Redis EXISTS $calibrationEvidence.Key | Select-Object -First 1)" "1" `
            "Calibration key disappeared after backend restart"
    } | Out-Null

    Test-Step "Prove fail-closed happened before provider invocation" {
        Assert-Equal $failClosedEvidence.Step.providerUsageAvailable $false `
            "Fail-closed provider usage"
        Assert-Equal $failClosedEvidence.Step.payloadHashSource "KERNEL_CANONICAL" `
            "Fail-closed pre-provider fingerprint"
        $failedRunStatus = Invoke-PostgresScalar `
            "SELECT status FROM sa_agent_run WHERE run_id=$(ConvertTo-SqlLiteral $failClosedChat.RunId) LIMIT 1;"
        Assert-Equal $failedRunStatus "FAILED" "Fail-closed Agent run status"
        Assert-Equal $failClosedProviderCountAfter $failClosedProviderCountBefore `
            "Provider-side request count changed for the fail-closed marker"
    } | Out-Null

    $reasoningTrigger = "$Marker-reasoning-trigger"
    $rawReasoningMarker = "$Marker-raw-reasoning-content"
    Test-Step "Configure a controlled provider reasoning response" {
        Set-ProviderProxyConfig -CountMarker $Marker -ControlledTrigger $reasoningTrigger `
            -ReasoningMarker $rawReasoningMarker -ResponseContent "CONTROLLED_REASONING_OK" | Out-Null
    } | Out-Null
    $reasoningConversationId = Test-Step "Create a dedicated controlled-reasoning conversation" {
        New-Conversation -Headers $headers
    }
    $reasoningChat = Test-Step "Receive real reasoning_content through the model adapter" {
        Invoke-KernelChat -ConversationId $reasoningConversationId -Headers $headers `
            -Question "${reasoningTrigger}: answer through the controlled provider response."
    }
    $reasoningEvidence = Test-Step "Prove raw reasoning is absent from every durable sink" {
        $evidence = Get-EnvelopeEvidence `
            -ConversationId $reasoningConversationId -RunId $reasoningChat.RunId
        Assert-Equal $evidence.Step.reasonCode "OK" "Controlled reasoning Envelope reason"
        Assert-Equal $evidence.StepOutput.thinkingPresent $true `
            "Controlled reasoning was not observed by the model adapter"
        Assert-Equal ([long]$evidence.StepOutput.thinkingChars) $rawReasoningMarker.Length `
            "Controlled reasoning character count"
        Assert-True (-not $reasoningChat.Content.Contains($rawReasoningMarker)) `
            "Controlled reasoning leaked through the chat SSE"
        $safeJson = $evidence.StepJson + $evidence.StepResultJson + $evidence.AllTraceRowsJson
        Assert-True (-not $safeJson.Contains($rawReasoningMarker)) `
            "Controlled reasoning leaked through run-step or RAG trace storage"
        foreach ($field in @(
            'thinking', 'thinkingContent', 'thinking_content',
            'reasoning', 'reasoningContent', 'reasoning_content',
            'chainOfThought', 'chain_of_thought'
        )) {
            Assert-True (-not (Test-JsonTreeContainsProperty -Value $evidence.StepOutput `
                    -PropertyName $field)) "MODEL_TURN output persisted raw field $field"
            Assert-True (-not (Test-JsonTreeContainsProperty `
                    -Value ($evidence.AllTraceRowsJson | ConvertFrom-Json) `
                    -PropertyName $field)) "RAG trace persisted raw field $field"
        }
        $runLiteral = ConvertTo-SqlLiteral $reasoningChat.RunId
        $conversationLiteral = ConvertTo-SqlLiteral $reasoningConversationId
        $rawLiteral = ConvertTo-SqlLiteral $rawReasoningMarker
        $rawSinkRows = Invoke-PostgresScalar @"
SELECT (SELECT count(*) FROM t_message m
        WHERE m.conversation_id=$conversationLiteral AND position($rawLiteral in row_to_json(m)::text) > 0)
     + (SELECT count(*) FROM sa_agent_run r
        WHERE r.run_id=$runLiteral AND position($rawLiteral in row_to_json(r)::text) > 0)
     + (SELECT count(*) FROM sa_agent_step s
        WHERE s.run_id=$runLiteral AND position($rawLiteral in row_to_json(s)::text) > 0)
     + (SELECT count(*) FROM sa_agent_checkpoint c
        WHERE c.run_id=$runLiteral AND position($rawLiteral in row_to_json(c)::text) > 0)
     + (SELECT count(*) FROM t_run_context_snapshot x
        WHERE x.run_id=$runLiteral AND position($rawLiteral in row_to_json(x)::text) > 0)
     + (SELECT count(*) FROM t_rag_trace_run r
        WHERE r.conversation_id=$conversationLiteral AND position($rawLiteral in row_to_json(r)::text) > 0)
     + (SELECT count(*) FROM t_rag_trace_node n
        JOIN t_rag_trace_run r ON r.trace_id=n.trace_id
        WHERE r.conversation_id=$conversationLiteral AND position($rawLiteral in row_to_json(n)::text) > 0);
"@
        Assert-Equal $rawSinkRows 0 "Durable rows containing controlled raw reasoning"
        return $evidence
    }
    Test-Step "Verify controlled reasoning is absent from Jaeger and logs" {
        Assert-JaegerEnvelopeTags -Name "Controlled reasoning" `
            -Evidence $reasoningEvidence -RawMarker $rawReasoningMarker
        $since = $testStartedAt.ToString("o")
        $backendLogs = Get-DockerLogsSince -Container $BackendContainer -Since $since
        $proxyLogs = Get-DockerLogsSince -Container "seahorse-model-provider-e2e-proxy" -Since $since
        Assert-True (-not $backendLogs.Contains($rawReasoningMarker)) `
            "Backend logs leaked controlled raw reasoning"
        Assert-True (-not $proxyLogs.Contains($rawReasoningMarker)) `
            "Provider recorder logs leaked controlled raw reasoning"
    } | Out-Null

    $toolReasoningTrigger = "$Marker-tool-reasoning-trigger"
    $rawToolReasoningMarker = "$Marker-raw-tool-reasoning-content"
    Test-Step "Configure reasoning followed by a real built-in tool call" {
        Set-ProviderProxyConfig -CountMarker $Marker -ControlledTrigger $toolReasoningTrigger `
            -ReasoningMarker $rawToolReasoningMarker -ResponseContent "CONTROLLED_TOOL_REASONING_OK" `
            -ControlledToolId "get_current_datetime" | Out-Null
    } | Out-Null
    $toolReasoningConversationId = Test-Step "Create a reasoning-and-tool conversation" {
        New-Conversation -Headers $headers
    }
    $toolReasoningChat = Test-Step "Execute reasoning, tool, and final model turns through real SSE" {
        Invoke-KernelChat -ConversationId $toolReasoningConversationId -Headers $headers `
            -Question "${toolReasoningTrigger}: call the controlled date-time tool."
    }
    $toolReasoningEvidence = Test-Step "Prove the tool ran and raw reasoning stayed ephemeral" {
        $evidence = Get-EnvelopeEvidence `
            -ConversationId $toolReasoningConversationId -RunId $toolReasoningChat.RunId
        Assert-Equal $evidence.StepOutput.thinkingPresent $true `
            "Tool-turn reasoning was not observed by the model adapter"
        Assert-Equal ([long]$evidence.StepOutput.thinkingChars) $rawToolReasoningMarker.Length `
            "Tool-turn reasoning character count"
        Assert-True (-not $toolReasoningChat.Content.Contains($rawToolReasoningMarker)) `
            "Tool-turn reasoning leaked through chat SSE"
        Assert-True $toolReasoningChat.Content.Contains("CONTROLLED_TOOL_REASONING_OK") `
            "Final controlled answer was missing from chat SSE"

        $runLiteral = ConvertTo-SqlLiteral $toolReasoningChat.RunId
        $conversationLiteral = ConvertTo-SqlLiteral $toolReasoningConversationId
        $toolInputJson = Wait-ForValue -Name "controlled TOOL_CALL input" -Query {
            Invoke-PostgresScalar @"
SELECT input_json
FROM sa_agent_step
WHERE run_id=$runLiteral AND step_type='TOOL_CALL'
ORDER BY step_no ASC
LIMIT 1;
"@
        }
        $toolOutputJson = Wait-ForValue -Name "controlled TOOL_CALL output" -Query {
            Invoke-PostgresScalar @"
SELECT output_json
FROM sa_agent_step
WHERE run_id=$runLiteral AND step_type='TOOL_CALL'
ORDER BY step_no ASC
LIMIT 1;
"@
        }
        $toolInput = $toolInputJson | ConvertFrom-Json
        $toolOutput = $toolOutputJson | ConvertFrom-Json
        Assert-Equal $toolInput.toolId "get_current_datetime" "Controlled tool id"
        Assert-Equal $toolOutput.success $true "Controlled tool result"
        $modelStepCount = Invoke-PostgresScalar `
            "SELECT count(*) FROM sa_agent_step WHERE run_id=$runLiteral AND step_type='MODEL_TURN';"
        Assert-Equal $modelStepCount 2 "Controlled model-turn count"
        $providerStats = Get-ProviderProxyStats
        Assert-Equal $providerStats.controlledToolCalls 1 "Controlled provider tool-call count"

        $rawLiteral = ConvertTo-SqlLiteral $rawToolReasoningMarker
        $rawSinkRows = Invoke-PostgresScalar @"
SELECT (SELECT count(*) FROM t_message m
        WHERE m.conversation_id=$conversationLiteral AND position($rawLiteral in row_to_json(m)::text) > 0)
     + (SELECT count(*) FROM sa_agent_run r
        WHERE r.run_id=$runLiteral AND position($rawLiteral in row_to_json(r)::text) > 0)
     + (SELECT count(*) FROM sa_agent_step s
        WHERE s.run_id=$runLiteral AND position($rawLiteral in row_to_json(s)::text) > 0)
     + (SELECT count(*) FROM sa_agent_checkpoint c
        WHERE c.run_id=$runLiteral AND position($rawLiteral in row_to_json(c)::text) > 0)
     + (SELECT count(*) FROM t_run_context_snapshot x
        WHERE x.run_id=$runLiteral AND position($rawLiteral in row_to_json(x)::text) > 0)
     + (SELECT count(*) FROM t_rag_trace_run r
        WHERE r.conversation_id=$conversationLiteral AND position($rawLiteral in row_to_json(r)::text) > 0)
     + (SELECT count(*) FROM t_rag_trace_node n
        JOIN t_rag_trace_run r ON r.trace_id=n.trace_id
        WHERE r.conversation_id=$conversationLiteral AND position($rawLiteral in row_to_json(n)::text) > 0);
"@
        Assert-Equal $rawSinkRows 0 "Durable rows containing tool-turn reasoning"
        return $evidence
    }
    Test-Step "Verify tool-turn reasoning is absent from Jaeger and logs" {
        Assert-JaegerEnvelopeTags -Name "Reasoning with tool call" `
            -Evidence $toolReasoningEvidence -RawMarker $rawToolReasoningMarker
        $since = $testStartedAt.ToString("o")
        $backendLogs = Get-DockerLogsSince -Container $BackendContainer -Since $since
        $proxyLogs = Get-DockerLogsSince -Container "seahorse-model-provider-e2e-proxy" -Since $since
        Assert-True (-not $backendLogs.Contains($rawToolReasoningMarker)) `
            "Backend logs leaked tool-turn reasoning"
        Assert-True (-not $proxyLogs.Contains($rawToolReasoningMarker)) `
            "Provider recorder logs leaked tool-turn reasoning"
    } | Out-Null

    $reasoningFailureTrigger = "$Marker-reasoning-failure-trigger"
    $rawFailureReasoningMarker = "$Marker-raw-reasoning-before-failure"
    Test-Step "Configure reasoning followed by a provider stream failure" {
        Set-ProviderProxyConfig -CountMarker $Marker -ControlledTrigger $reasoningFailureTrigger `
            -ReasoningMarker $rawFailureReasoningMarker -FailAfterReasoning $true | Out-Null
    } | Out-Null
    $reasoningFailureConversationId = Test-Step "Create a reasoning-failure conversation" {
        New-Conversation -Headers $headers
    }
    $reasoningFailureChat = Test-Step "Receive reasoning before the real model stream fails" {
        Invoke-KernelChatExpectFailure -ConversationId $reasoningFailureConversationId -Headers $headers `
            -Question "${reasoningFailureTrigger}: exercise the controlled provider failure."
    }
    $reasoningFailureEvidence = Test-Step "Prove failed-model sinks contain only stable error classification" {
        $evidence = Get-EnvelopeEvidence `
            -ConversationId $reasoningFailureConversationId -RunId $reasoningFailureChat.RunId
        Assert-Equal $evidence.Step.reasonCode "OK" "Reasoning-failure Envelope reason"
        Assert-Equal $evidence.Step.providerUsageAvailable $false `
            "Reasoning-failure provider usage availability"
        Assert-True ("$($evidence.StepResult.errorMessage)" -like 'MODEL_TURN_FAILED:*') `
            "Reasoning-failure step did not persist a stable model error classification"
        Assert-True (-not $reasoningFailureChat.Content.Contains($rawFailureReasoningMarker)) `
            "Failed model reasoning leaked through chat SSE"
        $runLiteral = ConvertTo-SqlLiteral $reasoningFailureChat.RunId
        $conversationLiteral = ConvertTo-SqlLiteral $reasoningFailureConversationId
        $rawLiteral = ConvertTo-SqlLiteral $rawFailureReasoningMarker
        $rawSinkRows = Invoke-PostgresScalar @"
SELECT (SELECT count(*) FROM t_message m
        WHERE m.conversation_id=$conversationLiteral AND position($rawLiteral in row_to_json(m)::text) > 0)
     + (SELECT count(*) FROM sa_agent_run r
        WHERE r.run_id=$runLiteral AND position($rawLiteral in row_to_json(r)::text) > 0)
     + (SELECT count(*) FROM sa_agent_step s
        WHERE s.run_id=$runLiteral AND position($rawLiteral in row_to_json(s)::text) > 0)
     + (SELECT count(*) FROM sa_agent_checkpoint c
        WHERE c.run_id=$runLiteral AND position($rawLiteral in row_to_json(c)::text) > 0)
     + (SELECT count(*) FROM t_run_context_snapshot x
        WHERE x.run_id=$runLiteral AND position($rawLiteral in row_to_json(x)::text) > 0)
     + (SELECT count(*) FROM t_rag_trace_run r
        WHERE r.conversation_id=$conversationLiteral AND position($rawLiteral in row_to_json(r)::text) > 0)
     + (SELECT count(*) FROM t_rag_trace_node n
        JOIN t_rag_trace_run r ON r.trace_id=n.trace_id
        WHERE r.conversation_id=$conversationLiteral AND position($rawLiteral in row_to_json(n)::text) > 0);
"@
        Assert-Equal $rawSinkRows 0 "Durable rows containing reasoning from the failed model turn"
        return $evidence
    }
    Test-Step "Verify failed-model reasoning is absent from Jaeger and logs" {
        Assert-JaegerEnvelopeTags -Name "Reasoning before failure" `
            -Evidence $reasoningFailureEvidence -RawMarker $rawFailureReasoningMarker
        $since = $testStartedAt.ToString("o")
        $backendLogs = Get-DockerLogsSince -Container $BackendContainer -Since $since
        $proxyLogs = Get-DockerLogsSince -Container "seahorse-model-provider-e2e-proxy" -Since $since
        Assert-True (-not $backendLogs.Contains($rawFailureReasoningMarker)) `
            "Backend logs leaked reasoning from the failed model turn"
        Assert-True (-not $proxyLogs.Contains($rawFailureReasoningMarker)) `
            "Provider recorder logs leaked reasoning from the failed model turn"
    } | Out-Null

    Test-Step "Prove all four fixed-cost partitions contract history together" {
        foreach ($pair in @(
            @{ Name = 'run step'; Baseline = $partitionBaselineEvidence.Step; Expanded = $partitionExpandedEvidence.Step },
            @{ Name = 'RAG Trace'; Baseline = $partitionBaselineEvidence.Trace; Expanded = $partitionExpandedEvidence.Trace }
        )) {
            foreach ($partition in @('system', 'skillBody', 'toolSchemas', 'currentInput')) {
                Assert-True ([long]$pair.Expanded.partitions.$partition.tokens -gt 0) `
                    "$($pair.Name) expanded $partition partition is empty"
                Assert-True ([long]$pair.Expanded.partitions.$partition.tokens -gt `
                        [long]$pair.Baseline.partitions.$partition.tokens) `
                    "$($pair.Name) expanded $partition partition did not increase"
            }
            Assert-True ([long]$pair.Expanded.fixedCost -gt [long]$pair.Baseline.fixedCost) `
                "$($pair.Name) enlarged partitions did not increase fixed cost"
            Assert-True ([long]$pair.Expanded.historyBudget -lt [long]$pair.Baseline.historyBudget) `
                "$($pair.Name) enlarged partitions did not contract history budget"
            Assert-True ([int]$pair.Expanded.toolCount -gt [int]$pair.Baseline.toolCount) `
                "$($pair.Name) expanded request did not expose real tool schemas"
        }
    } | Out-Null

    Test-Step "Scan API streams and backend logs for raw fixture history" {
        Assert-True (-not $shortChat.Content.Contains("$Marker-short:history:")) `
            "Short-input SSE leaked raw fixture history"
        Assert-True (-not $longChat.Content.Contains("$Marker-long:history:")) `
            "Long-input SSE leaked raw fixture history"
        Assert-True (-not $partitionBaselineChat.Content.Contains("$Marker-partition-baseline:history:")) `
            "Fixed-cost baseline SSE leaked raw fixture history"
        Assert-True (-not $partitionExpandedChat.Content.Contains("$Marker-partition-expanded:history:")) `
            "Four-partition SSE leaked raw fixture history"
        Assert-True (-not $approvalChat.Content.Contains("$Marker-approval-current")) `
            "Approval SSE leaked the raw fixture prompt"
        $since = $testStartedAt.ToString("o")
        $backendLogs = Get-DockerLogsSince -Container $BackendContainer -Since $since
        Assert-True (-not $backendLogs.Contains($Marker)) "Backend logs leaked the fixture marker"
    } | Out-Null

    Test-Step "Verify short-input model span tags in Jaeger" {
        Assert-JaegerEnvelopeTags -Name "Short input" -Evidence $shortEvidence -RawMarker $Marker
    } | Out-Null
    Test-Step "Verify long-input model span tags in Jaeger" {
        Assert-JaegerEnvelopeTags -Name "Long input" -Evidence $longEvidence -RawMarker $Marker
    } | Out-Null
    Test-Step "Verify fail-closed model span tags in Jaeger" {
        Assert-JaegerEnvelopeTags -Name "Fail closed" -Evidence $failClosedEvidence -RawMarker $Marker
    } | Out-Null
    Test-Step "Verify fixed-cost baseline model span tags in Jaeger" {
        Assert-JaegerEnvelopeTags -Name "Fixed-cost baseline" `
            -Evidence $partitionBaselineEvidence -RawMarker $Marker
    } | Out-Null
    Test-Step "Verify four-partition model span tags in Jaeger" {
        Assert-JaegerEnvelopeTags -Name "Four-partition expanded" `
            -Evidence $partitionExpandedEvidence -RawMarker $Marker
    } | Out-Null
    Test-Step "Verify the independent resume trace and model span in Jaeger" {
        Assert-JaegerResumeTrace -Evidence $resumeEvidence -RawMarker $Marker
    } | Out-Null

    Write-Host "`nEnvelope evidence summary:" -ForegroundColor Cyan
    [pscustomobject]@{
        shortRunId = $shortChat.RunId
        longRunId = $longChat.RunId
        failClosedRunId = $failClosedChat.RunId
        partitionBaselineRunId = $partitionBaselineChat.RunId
        partitionExpandedRunId = $partitionExpandedChat.RunId
        approvalResumeRunId = $approvalChat.RunId
        shortHistoryBudget = $shortEvidence.Step.historyBudget
        longHistoryBudget = $longEvidence.Step.historyBudget
        shortSelectedInputTokens = $shortEvidence.Step.selectedInputTokens
        longSelectedInputTokens = $longEvidence.Step.selectedInputTokens
        effectiveWindow = $shortEvidence.Step.effectiveWindow
        shortTruncatedMessages = @($shortEvidence.Step.decisions | Where-Object kind -eq 'HISTORY_TRUNCATED')[0].affectedMessages
        longTruncatedMessages = @($longEvidence.Step.decisions | Where-Object kind -eq 'HISTORY_TRUNCATED')[0].affectedMessages
        failClosedReason = $failClosedEvidence.Step.reasonCode
        partitionBaselineFixedCost = $partitionBaselineEvidence.Step.fixedCost
        partitionExpandedFixedCost = $partitionExpandedEvidence.Step.fixedCost
        partitionBaselineHistoryBudget = $partitionBaselineEvidence.Step.historyBudget
        partitionExpandedHistoryBudget = $partitionExpandedEvidence.Step.historyBudget
        expandedSystemTokens = $partitionExpandedEvidence.Step.partitions.system.tokens
        expandedSkillTokens = $partitionExpandedEvidence.Step.partitions.skillBody.tokens
        expandedToolSchemaTokens = $partitionExpandedEvidence.Step.partitions.toolSchemas.tokens
        expandedCurrentInputTokens = $partitionExpandedEvidence.Step.partitions.currentInput.tokens
        resumeModelStepCount = $resumeEvidence.ModelStepCount
        resumeContextWindowSource = $resumeEvidence.Envelope.contextWindowSource
        resumeProviderInputTokens = $resumeEvidence.Envelope.providerInputTokens
        calibrationTokens = $calibrationEvidence.Tokens
        calibratedSafetyBuffer = $longEvidence.Step.safetyBuffer
        resumeRagTraceId = $resumeEvidence.ResumeTrace.traceId
        resumedRunStatus = $resumedRun.status
    } | ConvertTo-Json -Compress | Write-Host
} catch {
    $script:executionError = $_
    throw
} finally {
    $cleanupFailures = [System.Collections.Generic.List[string]]::new()
    try {
        Remove-Fixtures
    } catch {
        $cleanupFailures.Add("fixtures: $($_.Exception.Message)")
    }
    try {
        if (-not [string]::IsNullOrWhiteSpace($script:calibrationKey)) {
            Invoke-Redis UNLINK $script:calibrationKey | Out-Null
        }
    } catch {
        $cleanupFailures.Add("calibration: $($_.Exception.Message)")
    }
    if ($cleanupFailures.Count -gt 0) {
        $message = "E2E cleanup failed: $($cleanupFailures -join '; ')"
        if ($null -ne $script:executionError) {
            Write-Warning $message
        } else {
            throw $message
        }
    }
}

Write-Host "`nSummary: $passed / $total passed" -ForegroundColor Cyan
