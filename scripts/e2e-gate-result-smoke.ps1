param(
    [string]$BaseUrl = "http://127.0.0.1:9090",
    [string]$Username = "admin",
    [string]$Password = "admin123"
)

$ErrorActionPreference = "Stop"
$passed = 0
$failed = 0
$total = 0
$createdModelConfigKey = $null
$createdPipelineId = $null

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

function First-Record {
    param([object]$Response, [string]$Name)
    Assert-ApiOk $Response $Name
    $records = @($Response.data.records)
    Assert-True ($records.Count -gt 0) "$Name returned no records"
    return $records[0]
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

Test-Step "Tool GateResult API uses real catalog metadata" {
    $response = Invoke-Api -Method GET -Path "/api/tools/$($tool.toolId)/gate-result" -Headers $headers
    Assert-GateResult `
        -Response $response `
        -SubjectType "TOOL" `
        -SubjectIdPattern "^$([regex]::Escape([string]$tool.toolId))$" `
        -ExpectedItemCodes @("TOOL_ENABLED", "TOOL_RISK_LEVEL_DECLARED", "TOOL_ACTION_TYPE_DECLARED", "TOOL_INPUT_SCHEMA_VALID") `
        -Name "Tool GateResult"
}

$skill = Test-Step "Select real skill catalog entry" {
    First-Record (Invoke-Api -Method GET -Path "/api/skills?tenantId=default&current=1&size=10" -Headers $headers) `
        "Skill catalog page"
}
if (-not $skill) { exit 1 }

Test-Step "Skill GateResult API uses latest real revision" {
    $encodedSkill = [System.Uri]::EscapeDataString([string]$skill.name)
    $response = Invoke-Api -Method GET -Path "/api/skills/$encodedSkill/gate-result?tenantId=default" -Headers $headers
    Assert-GateResult `
        -Response $response `
        -SubjectType "SKILL" `
        -SubjectIdPattern "^default:" `
        -ExpectedItemCodes @("SKILL_SECURITY_SCAN") `
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

Test-Step "Run Profile GateResult API executes real production gate check" {
    $response = Invoke-Api -Method POST -Path "/api/run-profiles/$($runProfile.id)/production-gate/gate-result" -Headers $headers
    Assert-GateResult `
        -Response $response `
        -SubjectType "RUN_PROFILE" `
        -SubjectIdPattern "^$([regex]::Escape([string]$runProfile.id))$" `
        -ExpectedItemCodes @("RUN_PROFILE_RISK_ASSESSED", "RUN_PROFILE_EXECUTOR_SUPPORTED", "RUN_PROFILE_HIGH_RISK_APPROVAL_GOVERNED") `
        -Name "Run Profile GateResult"
}

$agent = Test-Step "Select real published agent" {
    First-Record (Invoke-Api -Method GET -Path "/api/agents?current=1&size=10" -Headers $headers) `
        "Agent catalog page"
}
if (-not $agent) { exit 1 }

Test-Step "Generate real agent production gate report" {
    $response = Invoke-Api -Method POST -Path "/api/agents/$($agent.agentId)/production-gate" -Headers $headers
    Assert-ApiOk $response "Generate agent production gate"
    Assert-Equal $response.data.agentId $agent.agentId "Agent production gate agentId"
    Assert-True (-not [string]::IsNullOrWhiteSpace([string]$response.data.reportId)) "Agent production gate reportId missing"
    $response.data
}

Test-Step "Agent GateResult API projects latest production gate report" {
    $response = Invoke-Api -Method GET -Path "/api/agents/$($agent.agentId)/production-gate/gate-result" -Headers $headers
    Assert-GateResult `
        -Response $response `
        -SubjectType "AGENT" `
        -SubjectIdPattern "^$([regex]::Escape([string]$agent.agentId))$" `
        -ExpectedItemCodes @("TOOL_RISK_REVIEWED", "HIGH_RISK_APPROVAL_PRESENT", "AUDIT_LEDGER_ENABLED") `
        -Name "Agent GateResult"
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

Test-Step "Ingestion Pipeline GateResult API projects real pipeline" {
    $response = Invoke-Api -Method GET -Path "/ingestion/pipelines/$($pipeline.id)/gate-result" -Headers $headers
    Assert-GateResult `
        -Response $response `
        -SubjectType "INGESTION_PIPELINE" `
        -SubjectIdPattern "^$([regex]::Escape([string]$pipeline.id))$" `
        -ExpectedItemCodes @("INGESTION_PIPELINE_NODES_PRESENT", "INGESTION_PIPELINE_NODE_IDS_PRESENT", "INGESTION_PIPELINE_CHAIN_ACYCLIC") `
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

Test-Step "Model Config GateResult API projects real config" {
    $encodedKey = [System.Uri]::EscapeDataString($script:createdModelConfigKey)
    $response = Invoke-Api -Method GET -Path "/admin/ai-config/$encodedKey/gate-result?tenantId=default" -Headers $headers
    Assert-GateResult `
        -Response $response `
        -SubjectType "MODEL_CONFIG" `
        -SubjectIdPattern "^default:" `
        -ExpectedItemCodes @("MODEL_CONFIG_KEY_PRESENT", "MODEL_CONFIG_VALUE_PRESENT", "MODEL_CONFIG_JSON_VALUE_VALID", "MODEL_CONFIG_SENSITIVE_VALUE_ENCRYPTED") `
        -Name "Model Config GateResult"
}

Test-Step "Cleanup temporary model config" {
    if ([string]::IsNullOrWhiteSpace($script:createdModelConfigKey)) {
        return
    }
    $encodedKey = [System.Uri]::EscapeDataString($script:createdModelConfigKey)
    $response = Invoke-Api -Method DELETE -Path "/admin/ai-config/$encodedKey?tenantId=default" -Headers $headers
    Assert-ApiOk $response "Delete model config"
}

Test-Step "Cleanup temporary ingestion pipeline" {
    if ([string]::IsNullOrWhiteSpace($script:createdPipelineId)) {
        return
    }
    $response = Invoke-Api -Method DELETE -Path "/ingestion/pipelines/$script:createdPipelineId" -Headers $headers
    Assert-ApiOk $response "Delete ingestion pipeline"
}

Write-Host "`nSummary: $passed / $total passed, $failed failed" -ForegroundColor Cyan
Write-Host "Marker: $marker"
Write-Host "Tool: $($tool.toolId)"
Write-Host "Skill: $($skill.name)"
Write-Host "Run profile: $($runProfile.id)"
Write-Host "Agent: $($agent.agentId)"
Write-Host "Pipeline: $($pipeline.id)"

if ($failed -gt 0) {
    exit 1
}
