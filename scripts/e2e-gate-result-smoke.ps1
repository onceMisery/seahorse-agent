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
$createdModelConfigKey = $null
$createdCrossTenantModelConfigKey = $null
$crossTenantId = $null
$createdPipelineId = $null
$createdKnowledgeBaseId = $null

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

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        [hashtable]$Headers = @{},
        [object]$Body = $null,
        [int]$ExpectedStatus = 200
    )

    $bodyText = $null
    if ($null -ne $Body) {
        $bodyText = if ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Depth 40 -Compress }
    }

    $tempBodyFile = $null
    $args = @("-sS", "-w", "`n%{http_code}", "-X", $Method, "$BaseUrl$Path")
    if ($bodyText) {
        $tempBodyFile = New-TemporaryFile
        Set-Content -LiteralPath $tempBodyFile.FullName -Value $bodyText -Encoding UTF8 -NoNewline
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
        [hashtable]$Headers = @{},
        [hashtable]$FormFields = @{}
    )

    $args = @("-sS", "-w", "`n%{http_code}", "-X", "POST", "$BaseUrl$Path", "-F", "file=@$FilePath")
    foreach ($key in $FormFields.Keys) {
        $args += @("-F", "${key}=$($FormFields[$key])")
    }
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
    if ([string]::IsNullOrWhiteSpace($content)) {
        return $null
    }
    return $content | ConvertFrom-Json
}

function Assert-ApiOk {
    param([object]$Response, [string]$Name)
    if ($null -eq $Response -or "$($Response.code)" -ne "0") {
        throw "$Name API error: $($Response | ConvertTo-Json -Depth 30 -Compress)"
    }
}

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        throw $Message
    }
}

function Assert-Equal {
    param([object]$Actual, [object]$Expected, [string]$Name)
    if ("$Actual" -ne "$Expected") {
        throw "$Name expected '$Expected' but got '$Actual'"
    }
}

function ConvertTo-SqlLiteral {
    param([string]$Value)
    return "'" + ($Value -replace "'", "''") + "'"
}

function Invoke-PostgresScalar {
    param([string]$Sql)
    $output = & docker.exe exec $PostgresContainer psql `
        -U $PostgresUser `
        -d $PostgresDatabase `
        -v "ON_ERROR_STOP=1" `
        -t `
        -A `
        -c $Sql 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "psql failed with exit code $LASTEXITCODE`: $output"
    }
    return (@($output) -join "`n").Trim()
}

function Get-PersistedGateResult {
    param(
        [string]$SubjectType,
        [string]$SubjectId,
        [string]$TenantId = "default"
    )
    $subjectTypeLiteral = ConvertTo-SqlLiteral $SubjectType
    $subjectIdLiteral = ConvertTo-SqlLiteral $SubjectId
    $tenantIdLiteral = ConvertTo-SqlLiteral $TenantId
    $json = Invoke-PostgresScalar @"
SELECT json_build_object(
    'tenantId', tenant_id,
    'subjectType', subject_type,
    'subjectId', subject_id,
    'status', status,
    'passed', passed,
    'blockingCodes', blocking_codes_json::json,
    'items', items_json::json,
    'checkedAt', checked_at,
    'sourceType', source_type,
    'sourceId', source_id
)::text
FROM sa_gate_result
WHERE tenant_id = $tenantIdLiteral
  AND subject_type = $subjectTypeLiteral
  AND subject_id = $subjectIdLiteral
ORDER BY checked_at DESC, pk_id DESC
LIMIT 1;
"@
    Assert-True (-not [string]::IsNullOrWhiteSpace($json)) "Persisted $SubjectType/$SubjectId gate row missing"
    return $json | ConvertFrom-Json
}

function Assert-GateResult {
    param(
        [object]$Response,
        [string]$SubjectType,
        [string]$SubjectIdPattern,
        [string[]]$ExpectedItemCodes,
        [string]$Name
    )
    Assert-ApiOk $Response $Name
    $gate = $Response.data
    if ($null -eq $gate) {
        throw "$Name missing data"
    }
    Assert-Equal $gate.subjectType $SubjectType "$Name subjectType"
    Assert-True (-not [string]::IsNullOrWhiteSpace([string]$gate.subjectId)) "$Name subjectId missing"
    if (-not [string]::IsNullOrWhiteSpace($SubjectIdPattern) -and [string]$gate.subjectId -notmatch $SubjectIdPattern) {
        throw "$Name subjectId '$($gate.subjectId)' did not match $SubjectIdPattern"
    }
    Assert-True (-not [string]::IsNullOrWhiteSpace([string]$gate.status)) "$Name status missing"
    Assert-True ($null -ne $gate.passed) "$Name passed flag missing"
    Assert-True (-not [string]::IsNullOrWhiteSpace([string]$gate.checkedAt)) "$Name checkedAt missing"
    Assert-True (-not [string]::IsNullOrWhiteSpace([string]$gate.sourceType)) "$Name sourceType missing"
    Assert-True (-not [string]::IsNullOrWhiteSpace([string]$gate.sourceId)) "$Name sourceId missing"
    $items = @($gate.items)
    Assert-True ($items.Count -gt 0) "$Name returned no gate items"
    $codes = @($items | ForEach-Object { [string]$_.code })
    foreach ($code in $ExpectedItemCodes) {
        if ($codes -notcontains $code) {
            throw "$Name missing item code $code; actual=[$($codes -join ',')]"
        }
    }
    return $gate
}

function Assert-GateResultPersisted {
    param(
        [object]$Gate,
        [string]$SubjectType,
        [string[]]$ExpectedItemCodes,
        [string]$ExpectedSourceType,
        [hashtable]$Headers,
        [string]$Name
    )
    $encodedSubjectType = [System.Uri]::EscapeDataString($SubjectType)
    $encodedSubjectId = [System.Uri]::EscapeDataString([string]$Gate.subjectId)
    $response = Invoke-Api `
        -Method GET `
        -Path "/api/gate-results/$encodedSubjectType/$encodedSubjectId" `
        -Headers $Headers
    $persisted = Assert-GateResult `
        -Response $response `
        -SubjectType $SubjectType `
        -SubjectIdPattern "^$([regex]::Escape([string]$Gate.subjectId))$" `
        -ExpectedItemCodes $ExpectedItemCodes `
        -Name "Persisted $Name"
    Assert-Equal $persisted.status $Gate.status "$Name persisted API status"
    Assert-Equal $persisted.sourceId $Gate.sourceId "$Name persisted API sourceId"

    $row = Get-PersistedGateResult -SubjectType $SubjectType -SubjectId ([string]$Gate.subjectId)
    Assert-Equal $row.tenantId "default" "$Name persisted tenant"
    Assert-Equal $row.status $Gate.status "$Name persisted database status"
    Assert-Equal $row.sourceType $ExpectedSourceType "$Name persisted sourceType"
    Assert-Equal $row.sourceId $Gate.sourceId "$Name persisted database sourceId"
    Assert-True (@($row.items).Count -gt 0) "$Name persisted items missing"
    Assert-True ($null -ne $row.blockingCodes) "$Name persisted blockingCodes missing"

    $historyResponse = Invoke-Api `
        -Method GET `
        -Path "/api/gate-results/$encodedSubjectType/$encodedSubjectId/history?limit=5" `
        -Headers $Headers
    Assert-ApiOk $historyResponse "$Name history"
    $historyRecords = @($historyResponse.data)
    Assert-True ($historyRecords.Count -gt 0) "$Name history returned no records"
    Assert-True ($historyRecords.Count -le 5) "$Name history exceeded requested limit"
    $newest = $historyRecords[0]
    Assert-Equal $newest.subjectId ([string]$Gate.subjectId) "$Name history newest subjectId"
    Assert-Equal $newest.status $Gate.status "$Name history newest status"
    Assert-Equal $newest.sourceId $Gate.sourceId "$Name history newest sourceId"
    foreach ($record in $historyRecords) {
        Assert-Equal $record.subjectId ([string]$Gate.subjectId) "$Name history record subjectId"
    }
    $descending = $true
    for ($i = 1; $i -lt $historyRecords.Count; $i++) {
        if ([string]$historyRecords[$i].checkedAt -gt [string]$historyRecords[$i - 1].checkedAt) {
            $descending = $false
        }
    }
    Assert-True $descending "$Name history not ordered newest-first"
}

function First-Record {
    param([object]$Response, [string]$Name)
    Assert-ApiOk $Response $Name
    $records = @($Response.data.records)
    Assert-True ($records.Count -gt 0) "$Name returned no records"
    return $records[0]
}

function Wait-ForKnowledgeChunks {
    param(
        [string]$DocumentId,
        [hashtable]$Headers,
        [int]$Attempts = 30,
        [int]$DelaySeconds = 2
    )

    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        $response = Invoke-Api -Method GET -Path "/knowledge-base/docs/$DocumentId/chunks?current=1&size=10" -Headers $Headers
        Assert-ApiOk $response "Knowledge document chunks"
        $records = @($response.data.records)
        if ($records.Count -gt 0) {
            return $records
        }
        if ($attempt -lt $Attempts) {
            Start-Sleep -Seconds $DelaySeconds
        }
    }

    throw "document $DocumentId did not produce chunks after $Attempts attempts"
}

$login = Test-Step "Login" {
    $response = Invoke-Api -Method POST -Path "/auth/login" -Body @{
        username = $Username
        password = $Password
    }
    Assert-ApiOk $response "Login"
    Assert-True (-not [string]::IsNullOrWhiteSpace([string]$response.data.token)) "Login did not return token"
    Assert-True (-not [string]::IsNullOrWhiteSpace([string]$response.data.userId)) "Login did not return userId"
    $response.data
}
if (-not $login) { exit 1 }

$headers = @{
    Authorization = "Bearer $($login.token)"
    "X-User-Id" = "$($login.userId)"
}
$suffix = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$marker = "CODX_GATE_RESULT_$suffix"

$tool = Test-Step "Select real tool catalog entry" {
    First-Record (Invoke-Api -Method GET -Path "/api/tools?current=1&size=10&keyword=tool_search" -Headers $headers) `
        "Tool catalog page"
}
if (-not $tool) { exit 1 }

$toolGateResult = Test-Step "Tool GateResult API uses real catalog metadata" {
    $response = Invoke-Api -Method GET -Path "/api/tools/$($tool.toolId)/gate-result" -Headers $headers
    return Assert-GateResult `
        -Response $response `
        -SubjectType "TOOL" `
        -SubjectIdPattern "^$([regex]::Escape([string]$tool.toolId))$" `
        -ExpectedItemCodes @("TOOL_ENABLED", "TOOL_RISK_LEVEL_DECLARED", "TOOL_ACTION_TYPE_DECLARED", "TOOL_INPUT_SCHEMA_VALID") `
        -Name "Tool GateResult"
}
if (-not $toolGateResult) { exit 1 }

Test-Step "Tool GateResult is readable from API and durable in PostgreSQL" {
    Assert-GateResultPersisted `
        -Gate $toolGateResult `
        -SubjectType "TOOL" `
        -ExpectedItemCodes @("TOOL_ENABLED", "TOOL_RISK_LEVEL_DECLARED", "TOOL_INPUT_SCHEMA_VALID") `
        -ExpectedSourceType "ToolCatalogEntry" `
        -Headers $headers `
        -Name "Tool GateResult"
}

$skill = Test-Step "Select real skill catalog entry" {
    First-Record (Invoke-Api -Method GET -Path "/api/skills?tenantId=default&current=1&size=10" -Headers $headers) `
        "Skill catalog page"
}
if (-not $skill) { exit 1 }

$skillGateResult = Test-Step "Skill GateResult API uses latest real revision" {
    $encodedSkill = [System.Uri]::EscapeDataString([string]$skill.name)
    $response = Invoke-Api -Method GET -Path "/api/skills/$encodedSkill/gate-result?tenantId=default" -Headers $headers
    return Assert-GateResult `
        -Response $response `
        -SubjectType "SKILL" `
        -SubjectIdPattern "^default:" `
        -ExpectedItemCodes @("SKILL_SECURITY_SCAN") `
        -Name "Skill GateResult"
}
if (-not $skillGateResult) { exit 1 }

Test-Step "Skill GateResult is readable from API and durable in PostgreSQL" {
    Assert-GateResultPersisted `
        -Gate $skillGateResult `
        -SubjectType "SKILL" `
        -ExpectedItemCodes @("SKILL_SECURITY_SCAN") `
        -ExpectedSourceType "AgentSkillRevision" `
        -Headers $headers `
        -Name "Skill GateResult"
}

$runProfile = Test-Step "Select real run profile" {
    $response = Invoke-Api -Method GET -Path "/api/run-profiles" -Headers $headers
    Assert-ApiOk $response "Run profile list"
    $profiles = @($response.data)
    Assert-True ($profiles.Count -gt 0) "Run profile list returned no records"
    $profiles[0]
}
if (-not $runProfile) { exit 1 }

$runProfileGate = Test-Step "Run Profile GateResult API executes real production gate check" {
    $response = Invoke-Api -Method POST -Path "/api/run-profiles/$($runProfile.id)/production-gate/gate-result" -Headers $headers
    return Assert-GateResult `
        -Response $response `
        -SubjectType "RUN_PROFILE" `
        -SubjectIdPattern "^$([regex]::Escape([string]$runProfile.id))$" `
        -ExpectedItemCodes @("RUN_PROFILE_RISK_ASSESSED", "RUN_PROFILE_EXECUTOR_SUPPORTED", "RUN_PROFILE_HIGH_RISK_APPROVAL_GOVERNED") `
        -Name "Run Profile GateResult"
}
if (-not $runProfileGate) { exit 1 }

Test-Step "Run Profile unified latest API reads persisted GateResult" {
    $response = Invoke-Api -Method GET -Path "/api/gate-results/run_profile/$($runProfile.id)" -Headers $headers
    $persisted = Assert-GateResult `
        -Response $response `
        -SubjectType "RUN_PROFILE" `
        -SubjectIdPattern "^$([regex]::Escape([string]$runProfile.id))$" `
        -ExpectedItemCodes @("RUN_PROFILE_RISK_ASSESSED", "RUN_PROFILE_EXECUTOR_SUPPORTED") `
        -Name "Persisted Run Profile GateResult"
    Assert-Equal $persisted.sourceId $runProfileGate.sourceId "Persisted Run Profile sourceId"
}

Test-Step "Run Profile GateResult row is durable in PostgreSQL" {
    $row = Get-PersistedGateResult -SubjectType "RUN_PROFILE" -SubjectId ([string]$runProfile.id)
    Assert-Equal $row.tenantId "default" "Run Profile persisted tenant"
    Assert-Equal $row.status $runProfileGate.status "Run Profile persisted status"
    Assert-Equal $row.sourceType "RunProfileProductionGateCheck" "Run Profile persisted sourceType"
    Assert-True (@($row.items).Count -gt 0) "Run Profile persisted items missing"
}

$agent = Test-Step "Select real published agent" {
    First-Record (Invoke-Api -Method GET -Path "/api/agents?current=1&size=10" -Headers $headers) `
        "Agent catalog page"
}
if (-not $agent) { exit 1 }

$agentReport = Test-Step "Generate real agent production gate report" {
    $response = Invoke-Api -Method POST -Path "/api/agents/$($agent.agentId)/production-gate" -Headers $headers
    Assert-ApiOk $response "Generate agent production gate"
    Assert-Equal $response.data.agentId $agent.agentId "Agent production gate agentId"
    Assert-True (-not [string]::IsNullOrWhiteSpace([string]$response.data.reportId)) "Agent production gate reportId missing"
    $response.data
}
if (-not $agentReport) { exit 1 }

Test-Step "Agent GateResult API projects latest production gate report" {
    $response = Invoke-Api -Method GET -Path "/api/agents/$($agent.agentId)/production-gate/gate-result" -Headers $headers
    Assert-GateResult `
        -Response $response `
        -SubjectType "AGENT" `
        -SubjectIdPattern "^$([regex]::Escape([string]$agent.agentId))$" `
        -ExpectedItemCodes @("TOOL_RISK_REVIEWED", "HIGH_RISK_APPROVAL_PRESENT", "AUDIT_LEDGER_ENABLED") `
        -Name "Agent GateResult"
}

Test-Step "Agent unified latest API reads persisted GateResult" {
    $response = Invoke-Api -Method GET -Path "/api/gate-results/AGENT/$($agent.agentId)" -Headers $headers
    $persisted = Assert-GateResult `
        -Response $response `
        -SubjectType "AGENT" `
        -SubjectIdPattern "^$([regex]::Escape([string]$agent.agentId))$" `
        -ExpectedItemCodes @("TOOL_RISK_REVIEWED", "HIGH_RISK_APPROVAL_PRESENT", "AUDIT_LEDGER_ENABLED") `
        -Name "Persisted Agent GateResult"
    Assert-Equal $persisted.sourceId $agentReport.reportId "Persisted Agent sourceId"
}

Test-Step "Agent GateResult row is durable in PostgreSQL" {
    $row = Get-PersistedGateResult -SubjectType "AGENT" -SubjectId ([string]$agent.agentId)
    Assert-Equal $row.tenantId "default" "Agent persisted tenant"
    Assert-Equal $row.sourceId $agentReport.reportId "Agent persisted sourceId"
    Assert-True (@($row.items).Count -gt 0) "Agent persisted items missing"
    Assert-True ($null -ne $row.blockingCodes) "Agent persisted blockingCodes missing"
}

Test-Step "Unified latest API isolates GateResult by tenant" {
    $isolationTenant = ConvertTo-SqlLiteral "$marker-isolation"
    $agentIdLiteral = ConvertTo-SqlLiteral ([string]$agent.agentId)
    $isolationGateId = ConvertTo-SqlLiteral "gr_isolation_$suffix"
    try {
        Invoke-PostgresScalar @"
INSERT INTO sa_gate_result (
    gate_id, tenant_id, subject_type, subject_id, status, passed,
    blocking_codes_json, items_json, checked_at, source_type, source_id, created_at
) VALUES (
    $isolationGateId, $isolationTenant, 'AGENT', $agentIdLiteral, 'FAIL', false,
    json_build_array('TENANT_ISOLATION_SENTINEL')::text,
    json_build_array(json_build_object(
        'code', 'TENANT_ISOLATION_SENTINEL',
        'status', 'FAIL',
        'message', 'must stay isolated'))::text,
    CURRENT_TIMESTAMP + INTERVAL '1 day', 'TenantIsolationProbe', 'tenant-isolation-sentinel', CURRENT_TIMESTAMP
);
"@ | Out-Null
        $response = Invoke-Api -Method GET -Path "/api/gate-results/AGENT/$($agent.agentId)" -Headers $headers
        Assert-ApiOk $response "Tenant-isolated Agent GateResult"
        Assert-Equal $response.data.sourceId $agentReport.reportId "Tenant-isolated sourceId"
    } finally {
        Invoke-PostgresScalar "DELETE FROM sa_gate_result WHERE gate_id = $isolationGateId;" | Out-Null
    }
}

$pipeline = Test-Step "Create real ingestion pipeline for GateResult" {
    $response = Invoke-Api -Method POST -Path "/ingestion/pipelines" -Headers $headers -Body @{
        name = "$marker-pipeline"
        description = "Codex real GateResult smoke pipeline"
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
                    chunkSize = 80
                    overlapSize = 0
                    embed = $false
                }
            }
        )
    }
    Assert-ApiOk $response "Create ingestion pipeline"
    Assert-True (-not [string]::IsNullOrWhiteSpace([string]$response.data.id)) "Created pipeline did not return id"
    $script:createdPipelineId = [string]$response.data.id
    $response.data
}
if (-not $pipeline) { exit 1 }

$pipelineGateResult = Test-Step "Ingestion Pipeline GateResult API projects real pipeline" {
    $response = Invoke-Api -Method GET -Path "/ingestion/pipelines/$($pipeline.id)/gate-result" -Headers $headers
    return Assert-GateResult `
        -Response $response `
        -SubjectType "INGESTION_PIPELINE" `
        -SubjectIdPattern "^$([regex]::Escape([string]$pipeline.id))$" `
        -ExpectedItemCodes @("INGESTION_PIPELINE_NODES_PRESENT", "INGESTION_PIPELINE_NODE_IDS_PRESENT", "INGESTION_PIPELINE_CHAIN_ACYCLIC") `
        -Name "Ingestion Pipeline GateResult"
}
if (-not $pipelineGateResult) { exit 1 }

Test-Step "Ingestion Pipeline GateResult is readable from API and durable in PostgreSQL" {
    Assert-GateResultPersisted `
        -Gate $pipelineGateResult `
        -SubjectType "INGESTION_PIPELINE" `
        -ExpectedItemCodes @("INGESTION_PIPELINE_NODES_PRESENT", "INGESTION_PIPELINE_CHAIN_ACYCLIC") `
        -ExpectedSourceType "IngestionPipelineRecord" `
        -Headers $headers `
        -Name "Ingestion Pipeline GateResult"
}

$modelConfig = Test-Step "Create real model config for GateResult" {
    $script:createdModelConfigKey = "codex.gateResult.$suffix"
    $response = Invoke-Api -Method POST -Path "/admin/ai-config" -Headers $headers -Body @{
        tenantId = "default"
        configKey = $script:createdModelConfigKey
        configValue = '{"provider":"codex-smoke","enabled":true}'
        configType = "JSON"
        encrypted = $false
        description = "Codex real GateResult smoke config"
    }
    Assert-ApiOk $response "Create model config"
    Assert-Equal $response.data.configKey $script:createdModelConfigKey "Created model config key"
    $response.data
}
if (-not $modelConfig) { exit 1 }

$modelConfigGateResult = Test-Step "Model Config GateResult API projects real config" {
    $encodedKey = [System.Uri]::EscapeDataString($script:createdModelConfigKey)
    $response = Invoke-Api -Method GET -Path "/admin/ai-config/$encodedKey/gate-result?tenantId=default" -Headers $headers
    return Assert-GateResult `
        -Response $response `
        -SubjectType "MODEL_CONFIG" `
        -SubjectIdPattern "^default:" `
        -ExpectedItemCodes @("MODEL_CONFIG_KEY_PRESENT", "MODEL_CONFIG_VALUE_PRESENT", "MODEL_CONFIG_JSON_VALUE_VALID", "MODEL_CONFIG_SENSITIVE_VALUE_ENCRYPTED") `
        -Name "Model Config GateResult"
}
if (-not $modelConfigGateResult) { exit 1 }

Test-Step "Model Config GateResult is readable from API and durable in PostgreSQL" {
    Assert-GateResultPersisted `
        -Gate $modelConfigGateResult `
        -SubjectType "MODEL_CONFIG" `
        -ExpectedItemCodes @("MODEL_CONFIG_KEY_PRESENT", "MODEL_CONFIG_JSON_VALUE_VALID") `
        -ExpectedSourceType "AiModelConfig" `
        -Headers $headers `
        -Name "Model Config GateResult"
}

Test-Step "Cross-tenant Model Config GateResult keeps source tenant ownership" {
    $script:crossTenantId = "$marker-tenant"
    $script:createdCrossTenantModelConfigKey = "codex.gateResult.crossTenant.$suffix"
    $created = Invoke-Api -Method POST -Path "/admin/ai-config" -Headers $headers -Body @{
        tenantId = $script:crossTenantId
        configKey = $script:createdCrossTenantModelConfigKey
        configValue = '{"provider":"codex-cross-tenant-smoke","enabled":true}'
        configType = "JSON"
        encrypted = $false
        description = "Codex cross-tenant GateResult ownership smoke"
    }
    Assert-ApiOk $created "Create cross-tenant model config"

    $encodedKey = [System.Uri]::EscapeDataString($script:createdCrossTenantModelConfigKey)
    $encodedTenant = [System.Uri]::EscapeDataString($script:crossTenantId)
    $response = Invoke-Api `
        -Method GET `
        -Path "/admin/ai-config/$encodedKey/gate-result?tenantId=$encodedTenant" `
        -Headers $headers
    $gate = Assert-GateResult `
        -Response $response `
        -SubjectType "MODEL_CONFIG" `
        -SubjectIdPattern "^$([regex]::Escape($script:crossTenantId)):" `
        -ExpectedItemCodes @("MODEL_CONFIG_KEY_PRESENT", "MODEL_CONFIG_JSON_VALUE_VALID") `
        -Name "Cross-tenant Model Config GateResult"

    $row = Get-PersistedGateResult `
        -SubjectType "MODEL_CONFIG" `
        -SubjectId ([string]$gate.subjectId) `
        -TenantId $script:crossTenantId
    Assert-Equal $row.sourceId $gate.sourceId "Cross-tenant persisted sourceId"
    $defaultTenantCount = Invoke-PostgresScalar @"
SELECT COUNT(*)
FROM sa_gate_result
WHERE tenant_id = 'default'
  AND subject_type = 'MODEL_CONFIG'
  AND subject_id = $(ConvertTo-SqlLiteral ([string]$gate.subjectId));
"@
    Assert-Equal $defaultTenantCount "0" "Cross-tenant GateResult default-tenant pollution count"
}

$ragComparison = Test-Step "Create real RAG strategy comparison for GateResult" {
    $kb = Invoke-Api -Method POST -Path "/knowledge-base" -Headers $headers -Body @{
        name = "$marker-rag-gate"
        embeddingModel = "nomic-embed-text"
        collectionName = "gate$($suffix.ToString().Substring([Math]::Max(0, $suffix.ToString().Length - 12)))"
    }
    Assert-ApiOk $kb "Create RAG GateResult knowledge base"
    Assert-True (-not [string]::IsNullOrWhiteSpace([string]$kb.data)) "Create knowledge base did not return id"
    $script:createdKnowledgeBaseId = [string]$kb.data

    $docFile = New-TemporaryFile
    Set-Content -LiteralPath $docFile.FullName -Encoding UTF8 -Value @"
# Seahorse RAG GateResult $marker

Seahorse Agent uses nomic-embed-text for local embedding with dimension 768.
The retrieval pipeline includes vector search, keyword retrieval, RRF fusion, and rerank.
Production gates use retrieval evaluation, trace evidence, audit events, and cost checks.
"@
    try {
        $upload = Invoke-MultipartFile `
            -Path "/knowledge-base/$script:createdKnowledgeBaseId/docs/upload" `
            -FilePath $docFile.FullName `
            -Headers $headers `
            -FormFields @{ chunkSize = "256"; chunkOverlap = "32" }
        Assert-ApiOk $upload "Upload RAG GateResult document"
        $docId = [string]$upload.data.id
        Assert-True (-not [string]::IsNullOrWhiteSpace($docId)) "Upload did not return document id"

        $chunkStart = Invoke-Api -Method POST -Path "/knowledge-base/docs/$docId/chunk" -Headers $headers
        Assert-ApiOk $chunkStart "Chunk RAG GateResult document"
        $chunks = @(Wait-ForKnowledgeChunks -DocumentId $docId -Headers $headers)
        $chunkIds = @($chunks | Select-Object -First 3 | ForEach-Object { [string]$_.id })
        Assert-True ($chunkIds.Count -gt 0) "RAG GateResult document produced no chunk ids"

        $dataset = Invoke-Api -Method POST -Path "/knowledge-base/$script:createdKnowledgeBaseId/retrieval-evaluation-datasets" -Headers $headers -Body @{
            datasetId = ""
            name = "$marker-rag-dataset"
            description = "Codex real RAG Strategy GateResult smoke dataset"
            enabled = $true
            cases = @(
                @{
                    caseId = "$marker-case-1"
                    question = "What embedding model and dimension does Seahorse use?"
                    expectedKbIds = @($script:createdKnowledgeBaseId)
                    expectedDocIds = @($docId)
                    expectedChunkIds = $chunkIds
                    negativeChunkIds = @()
                    tags = @("gate-result", "embedding")
                    minRecall = 0.5
                },
                @{
                    caseId = "$marker-case-2"
                    question = "What evidence is used for production gates?"
                    expectedKbIds = @($script:createdKnowledgeBaseId)
                    expectedDocIds = @($docId)
                    expectedChunkIds = $chunkIds
                    negativeChunkIds = @()
                    tags = @("gate-result", "production-gate")
                    minRecall = 0.5
                }
            )
        }
        Assert-ApiOk $dataset "Create RAG GateResult dataset"
        $datasetId = [string]$dataset.data.datasetId
        Assert-True (-not [string]::IsNullOrWhiteSpace($datasetId)) "Dataset create did not return datasetId"

        $comparison = Invoke-Api -Method POST -Path "/knowledge-base/$script:createdKnowledgeBaseId/retrieval-evaluation-datasets/$datasetId/compare" -Headers $headers -Body @{
            baselineStrategyName = "vector_only"
            topK = 5
            strategies = @(
                @{ strategyName = "vector_only"; topK = 5; options = @{} },
                @{ strategyName = "hybrid_rrf"; topK = 5; options = @{} }
            )
        }
        Assert-ApiOk $comparison "Create RAG GateResult comparison"
        Assert-True (-not [string]::IsNullOrWhiteSpace([string]$comparison.data.winnerStrategyName)) "Comparison did not return winnerStrategyName"

        $comparisons = Invoke-Api -Method GET -Path "/knowledge-base/$script:createdKnowledgeBaseId/retrieval-evaluation-datasets/$datasetId/comparisons?limit=10" -Headers $headers
        Assert-ApiOk $comparisons "List RAG GateResult comparisons"
        $saved = @($comparisons.data | Where-Object {
                [string]$_.datasetId -eq $datasetId -and
                [string]$_.winnerStrategyName -eq [string]$comparison.data.winnerStrategyName
            })[0]
        Assert-True ($null -ne $saved) "Saved RAG comparison was not returned"
        Assert-True (-not [string]::IsNullOrWhiteSpace([string]$saved.comparisonId)) "Saved comparison did not include comparisonId"
        [PSCustomObject]@{
            kbId = $script:createdKnowledgeBaseId
            docId = $docId
            datasetId = $datasetId
            comparisonId = [string]$saved.comparisonId
            winner = [string]$saved.winnerStrategyName
        }
    } finally {
        Remove-Item -LiteralPath $docFile.FullName -ErrorAction SilentlyContinue
    }
}
if (-not $ragComparison) { exit 1 }

$ragGateResult = Test-Step "RAG Strategy GateResult API projects real comparison evidence" {
    $response = Invoke-Api `
        -Method GET `
        -Path "/knowledge-base/$($ragComparison.kbId)/retrieval-evaluation-datasets/$($ragComparison.datasetId)/comparisons/$($ragComparison.comparisonId)/gate-result" `
        -Headers $headers
    Assert-GateResult `
        -Response $response `
        -SubjectType "RAG_STRATEGY" `
        -SubjectIdPattern "^$([regex]::Escape([string]$ragComparison.kbId)):" `
        -ExpectedItemCodes @("RAG_BASELINE_PRESENT", "RAG_WINNER_PRESENT", "RAG_EVALUABLE_CASES_PRESENT", "RAG_RECALL_NOT_REGRESSED", "RAG_PRECISION_NOT_REGRESSED") `
        -Name "RAG Strategy GateResult"
}
if (-not $ragGateResult) { exit 1 }

Test-Step "RAG Strategy GateResult is readable from API and durable in PostgreSQL" {
    Assert-GateResultPersisted `
        -Gate $ragGateResult `
        -SubjectType "RAG_STRATEGY" `
        -ExpectedItemCodes @("RAG_BASELINE_PRESENT", "RAG_WINNER_PRESENT", "RAG_EVALUABLE_CASES_PRESENT") `
        -ExpectedSourceType "RetrievalEvaluationComparisonRecord" `
        -Headers $headers `
        -Name "RAG Strategy GateResult"
}

Test-Step "Cleanup temporary model config" {
    if ([string]::IsNullOrWhiteSpace($script:createdModelConfigKey)) {
        return
    }
    $encodedKey = [System.Uri]::EscapeDataString($script:createdModelConfigKey)
    $response = Invoke-Api -Method DELETE -Path "/admin/ai-config/$encodedKey?tenantId=default" -Headers $headers
    Assert-ApiOk $response "Delete model config"
}

Test-Step "Cleanup temporary cross-tenant model config" {
    if ([string]::IsNullOrWhiteSpace($script:createdCrossTenantModelConfigKey) -or
            [string]::IsNullOrWhiteSpace($script:crossTenantId)) {
        return
    }
    $encodedKey = [System.Uri]::EscapeDataString($script:createdCrossTenantModelConfigKey)
    $encodedTenant = [System.Uri]::EscapeDataString($script:crossTenantId)
    $response = Invoke-Api `
        -Method DELETE `
        -Path "/admin/ai-config/$encodedKey?tenantId=$encodedTenant" `
        -Headers $headers
    Assert-ApiOk $response "Delete cross-tenant model config"
}

Test-Step "Cleanup temporary ingestion pipeline" {
    if ([string]::IsNullOrWhiteSpace($script:createdPipelineId)) {
        return
    }
    $response = Invoke-Api -Method DELETE -Path "/ingestion/pipelines/$script:createdPipelineId" -Headers $headers
    Assert-ApiOk $response "Delete ingestion pipeline"
}

Test-Step "Cleanup temporary RAG knowledge base" {
    if ([string]::IsNullOrWhiteSpace($script:createdKnowledgeBaseId)) {
        return
    }
    $response = Invoke-Api -Method DELETE -Path "/knowledge-base/$script:createdKnowledgeBaseId" -Headers $headers
    Assert-ApiOk $response "Delete knowledge base"
}

Write-Host "`nSummary: $passed / $total passed, $failed failed" -ForegroundColor Cyan
Write-Host "Marker: $marker"
Write-Host "Tool: $($tool.toolId)"
Write-Host "Skill: $($skill.name)"
Write-Host "Run profile: $($runProfile.id)"
Write-Host "Agent: $($agent.agentId)"
Write-Host "Pipeline: $($pipeline.id)"
Write-Host "RAG comparison: $($ragComparison.comparisonId)"

if ($failed -gt 0) {
    exit 1
}
