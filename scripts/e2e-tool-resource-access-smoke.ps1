param(
    [string]$BaseUrl = "http://127.0.0.1:9090",
    [string]$Username = "admin",
    [string]$Password = "admin123",
    [string]$PostgresContainer = "seahorse-postgres",
    [string]$PostgresUser = "seahorse",
    [string]$PostgresDatabase = "seahorse",
    [string]$Marker = "",
    [switch]$SkipHealth
)

$ErrorActionPreference = "Stop"
$passed = 0
$failed = 0
$total = 0
$createdRuleIds = [System.Collections.Generic.List[string]]::new()
$headers = $null

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
        $bodyText = if ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Depth 20 -Compress }
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
        throw "$Name API error: $($Response | ConvertTo-Json -Depth 20 -Compress)"
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

    return "'" + $Value.Replace("'", "''") + "'"
}

function Invoke-PostgresRows {
    param([string]$Sql)

    $raw = & docker exec $PostgresContainer psql -U $PostgresUser -d $PostgresDatabase -At -F "|" -c $Sql
    if ($LASTEXITCODE -ne 0) {
        throw "psql exited with $LASTEXITCODE for SQL: $Sql"
    }
    return @($raw | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
}

function New-AclRule {
    param([string]$ResourceId, [string]$Effect)

    $response = Invoke-Json -Method POST -Path "/api/resource-acl-rules" -Headers $headers -Body @{
        tenantId = "default"
        resourceType = "TOOL"
        resourceId = $ResourceId
        subjectType = "USER_DELEGATED_AGENT"
        subjectId = $Username
        action = "READ"
        effect = $Effect
        priority = 100
    }
    Assert-ApiOk $response "Create $Effect resource ACL"
    if ([string]::IsNullOrWhiteSpace("$($response.data.ruleId)")) {
        throw "Create $Effect resource ACL did not return ruleId"
    }
    $createdRuleIds.Add("$($response.data.ruleId)")
    return $response.data
}

function Invoke-ToolSearch {
    param([string]$RunId, [string]$ResourceId, [string]$Suffix)

    return Invoke-Json -Method POST -Path "/api/tools/tool_search/invoke" -Headers $headers -Body @{
        runId = $RunId
        stepId = "step-$Suffix"
        toolCallId = "call-$Suffix-$Marker"
        agentId = "legacy-react-agent"
        tenantId = "untrusted-tenant"
        userId = "untrusted-user"
        arguments = @{
            query = "sandbox"
            limit = 5
            _seahorseAllowedToolIds = @("tool_search", "sandbox_python")
        }
        resourceRefs = @{ target = $ResourceId }
        idempotencyKey = "idem-$Suffix-$Marker"
        allowedToolIds = @("tool_search", "sandbox_python")
    }
}

function Get-PageRecords {
    param([object]$Response, [string]$Name)

    Assert-ApiOk $Response $Name
    Write-Output -NoEnumerate @($Response.data.records)
}

if ([string]::IsNullOrWhiteSpace($Marker)) {
    $Marker = "seahorse-tool-resource-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())"
}
$allowResourceId = "$Marker-allow-resource"
$denyResourceId = "$Marker-deny-resource"
$allowRunId = "$Marker-allow-run"
$denyRunId = "$Marker-deny-run"

try {
    if (-not $SkipHealth) {
        Test-Step "Backend health is UP" {
            $health = Invoke-Json -Method GET -Path "/actuator/health"
            Assert-Equal $health.status "UP" "backend health"
        } | Out-Null
    }

    $login = Test-Step "Login as real operator" {
        $response = Invoke-Json -Method POST -Path "/auth/login" -Body @{
            username = $Username
            password = $Password
        }
        Assert-ApiOk $response "Login"
        if ([string]::IsNullOrWhiteSpace("$($response.data.token)")) {
            throw "Login did not return token"
        }
        return $response.data
    }
    if ($null -eq $login) {
        throw "Cannot continue without authentication"
    }
    $headers = @{
        Authorization = "Bearer $($login.token)"
        "X-User-Id" = "$($login.userId)"
    }

    Test-Step "Catalog exposes governed TOOL READ tool_search" {
        $response = Invoke-Json -Method GET -Path "/api/tools?current=1&size=10&keyword=tool_search" -Headers $headers
        $records = Get-PageRecords $response "Tool catalog"
        $tool = @($records | Where-Object { "$($_.toolId)" -eq "tool_search" })[0]
        if ($null -eq $tool) {
            throw "tool_search is missing from the real catalog"
        }
        Assert-Equal $tool.riskLevel "LOW" "tool_search riskLevel"
        Assert-Equal $tool.actionType "READ" "tool_search actionType"
        Assert-Equal $tool.resourceType "TOOL" "tool_search resourceType"
        Assert-Equal $tool.requiresApproval $false "tool_search requiresApproval"
    } | Out-Null

    Test-Step "Create ALLOW and DENY ACL rules for the authenticated operator" {
        New-AclRule -ResourceId $allowResourceId -Effect "ALLOW" | Out-Null
        New-AclRule -ResourceId $denyResourceId -Effect "DENY" | Out-Null
    } | Out-Null

    $allowResponse = Test-Step "ALLOW executes the real tool_search implementation" {
        $response = Invoke-ToolSearch -RunId $allowRunId -ResourceId $allowResourceId -Suffix "allow"
        Assert-ApiOk $response "ALLOW tool invocation"
        Assert-Equal $response.data.success $true "ALLOW success"
        if ("$($response.data.content)" -notlike "*sandbox_python*") {
            throw "ALLOW content did not contain real sandbox_python catalog metadata"
        }
        return $response
    }

    $denyResponse = Test-Step "DENY fails closed before tool execution without resource leakage" {
        $response = Invoke-ToolSearch -RunId $denyRunId -ResourceId $denyResourceId -Suffix "deny"
        Assert-ApiOk $response "DENY tool invocation"
        Assert-Equal $response.data.success $false "DENY success"
        Assert-Equal $response.data.error "RESOURCE_FORBIDDEN" "DENY error"
        if ($null -ne $response.data.content) {
            throw "DENY response unexpectedly contained tool output"
        }
        $serialized = $response | ConvertTo-Json -Depth 20 -Compress
        if ($serialized.Contains($denyResourceId)) {
            throw "DENY response leaked the resource id"
        }
        return $response
    }

    Test-Step "Unified access decision API exposes exact ALLOW and DENY evidence" {
        $response = Invoke-Json -Method GET -Path "/api/access-decisions?tenantId=default&subjectType=USER_DELEGATED_AGENT&subjectId=$Username&action=READ&resourceType=TOOL&current=1&size=100" -Headers $headers
        $records = Get-PageRecords $response "Access decision page"
        $allow = @($records | Where-Object { "$($_.resourceId)" -eq $allowResourceId })[0]
        $deny = @($records | Where-Object { "$($_.resourceId)" -eq $denyResourceId })[0]
        if ($null -eq $allow -or $null -eq $deny) {
            throw "Access decision API did not return both marker resources"
        }
        Assert-Equal $allow.subjectId $Username "ALLOW subjectId"
        Assert-Equal $allow.effect "ALLOW" "ALLOW effect"
        Assert-Equal $allow.reasonCode "RESOURCE_ACL_ALLOW" "ALLOW reasonCode"
        Assert-Equal $deny.subjectId $Username "DENY subjectId"
        Assert-Equal $deny.effect "DENY" "DENY effect"
        Assert-Equal $deny.reasonCode "RESOURCE_ACL_DENY" "DENY reasonCode"
    } | Out-Null

    Test-Step "PostgreSQL persisted canonical access decisions" {
        $allowLiteral = ConvertTo-SqlLiteral $allowResourceId
        $denyLiteral = ConvertTo-SqlLiteral $denyResourceId
        $rows = Invoke-PostgresRows @"
SELECT subject_type, subject_id, action, resource_type, effect, reason_code
FROM sa_access_decision_log
WHERE resource_id IN ($allowLiteral, $denyLiteral)
ORDER BY effect;
"@
        Assert-Equal $rows.Count 2 "access decision row count"
        if ($rows -notcontains "USER_DELEGATED_AGENT|$Username|READ|TOOL|ALLOW|RESOURCE_ACL_ALLOW") {
            throw "PostgreSQL ALLOW decision did not match the canonical request"
        }
        if ($rows -notcontains "USER_DELEGATED_AGENT|$Username|READ|TOOL|DENY|RESOURCE_ACL_DENY") {
            throw "PostgreSQL DENY decision did not match the canonical request"
        }
    } | Out-Null

    Test-Step "Tool invocation audit proves success and pre-execution denial" {
        $allowAudit = Invoke-Json -Method GET -Path "/api/tool-invocations?current=1&size=20&runId=$allowRunId&toolId=tool_search" -Headers $headers
        $denyAudit = Invoke-Json -Method GET -Path "/api/tool-invocations?current=1&size=20&runId=$denyRunId&toolId=tool_search" -Headers $headers
        $allowRecords = Get-PageRecords $allowAudit "ALLOW tool audit"
        $denyRecords = Get-PageRecords $denyAudit "DENY tool audit"
        Assert-Equal $allowRecords.Count 1 "ALLOW audit row count"
        Assert-Equal $denyRecords.Count 1 "DENY audit row count"
        Assert-Equal $allowRecords[0].status "SUCCEEDED" "ALLOW audit status"
        Assert-Equal $allowRecords[0].userId $Username "ALLOW audit user"
        Assert-Equal $denyRecords[0].status "DENIED" "DENY audit status"
        Assert-Equal $denyRecords[0].errorMessage "RESOURCE_FORBIDDEN" "DENY audit error"
        if ($denyRecords[0].resultSummary -notlike '*"contentPresent":false*') {
            throw "DENY audit did not prove that no tool content was produced"
        }
        $serialized = @($allowRecords[0], $denyRecords[0]) | ConvertTo-Json -Depth 20 -Compress
        if ($serialized.Contains($allowResourceId) -or $serialized.Contains($denyResourceId)) {
            throw "Tool invocation audit leaked a resource id"
        }
    } | Out-Null

    Test-Step "PostgreSQL tool audit stores only governed summaries" {
        $allowRunLiteral = ConvertTo-SqlLiteral $allowRunId
        $denyRunLiteral = ConvertTo-SqlLiteral $denyRunId
        $rows = Invoke-PostgresRows @"
SELECT run_id, status, user_id, policy_decision_id, COALESCE(error_message, ''),
       arguments_summary, result_summary
FROM sa_tool_invocation
WHERE run_id IN ($allowRunLiteral, $denyRunLiteral)
ORDER BY run_id;
"@
        Assert-Equal $rows.Count 2 "tool invocation row count"
        $joined = $rows -join "`n"
        if ($joined -notlike "*|SUCCEEDED|$Username|builtin-tool-allow|*") {
            throw "PostgreSQL did not persist the successful governed invocation"
        }
        if ($joined -notlike "*|DENIED|$Username|builtin-resource-forbidden|RESOURCE_FORBIDDEN|*") {
            throw "PostgreSQL did not persist the pre-execution denial"
        }
        if ($joined.Contains($allowResourceId) -or $joined.Contains($denyResourceId)) {
            throw "PostgreSQL tool audit leaked a resource id"
        }
    } | Out-Null
} finally {
    if ($null -ne $headers) {
        foreach ($ruleId in $createdRuleIds) {
            try {
                $cleanup = Invoke-Json -Method POST -Path "/api/resource-acl-rules/$ruleId/disable" -Headers $headers
                Assert-ApiOk $cleanup "Disable resource ACL $ruleId"
            } catch {
                Write-Host "  CLEANUP WARN: could not disable $ruleId - $($_.Exception.Message)" -ForegroundColor Yellow
                $failed++
            }
        }
    }
}

Write-Host "`nTool resource access smoke: $passed/$total passed, $failed failed. Marker: $Marker" -ForegroundColor $(if ($failed -eq 0) { "Green" } else { "Red" })
if ($failed -gt 0) {
    exit 1
}
