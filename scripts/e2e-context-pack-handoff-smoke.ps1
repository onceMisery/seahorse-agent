param(
    [string]$BaseUrl = "http://127.0.0.1:9090",
    [string]$Username = "admin",
    [string]$Password = "admin123",
    [string]$Marker = "seahorse-context-pack-handoff-smoke",
    [switch]$SkipHealth
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

function Get-PageRecords {
    param([object]$Page)
    if ($null -eq $Page) {
        return @()
    }
    if ($null -ne $Page.records) {
        return @($Page.records)
    }
    if ($null -ne $Page.list) {
        return @($Page.list)
    }
    if ($Page -is [array]) {
        return @($Page)
    }
    return @()
}

function Wait-ForHealth {
    param([int]$Attempts = 90)
    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        try {
            $health = Invoke-Json -Method GET -Path "/actuator/health"
            if ($health.status -eq "UP") {
                return
            }
        } catch {
            if ($attempt -ge $Attempts) {
                throw
            }
        }
        Start-Sleep -Seconds 2
    }
    throw "Timed out waiting for backend health"
}

try {
    if (-not $SkipHealth) {
        Test-Step "Wait for backend health" {
            Wait-ForHealth
        } | Out-Null
    }

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
    $suffix = ([guid]::NewGuid().ToString('N')).Substring(0, 8)
    $gatewayRunId = "handoff-gateway-smoke-run-$suffix"
    $parentRunId = "handoff-parent-smoke-run-$suffix"
    $stepId = "handoff-smoke-step-$suffix"
    $toolCallId = "handoff-smoke-call-$suffix"
    $contextPackId = "context-pack-handoff-smoke-$suffix"
    $contextSummary = @{
        marker = $Marker
        contextPackId = $contextPackId
        items = @(
            @{ sourceType = "DOCUMENT"; sourceId = "doc-$suffix"; summary = "handoff transfer smoke" }
        )
    } | ConvertTo-Json -Depth 20 -Compress

    Test-Step "Verify local_agent_handoff tool is visible" {
        $response = Invoke-Json -Method GET -Path "/api/tools?current=1&size=50&provider=BUILTIN&keyword=local_agent_handoff" -Headers $headers
        Assert-ApiOk $response "Tool catalog"
        $records = Get-PageRecords $response.data
        $tool = @($records | Where-Object { $_.toolId -eq "local_agent_handoff" }) | Select-Object -First 1
        if (-not $tool) {
            throw "local_agent_handoff was not returned by the built-in tool catalog"
        }
    } | Out-Null

    $invoke = Test-Step "Invoke local_agent_handoff through Tool Gateway" {
        $response = Invoke-Json -Method POST -Path "/api/tools/local_agent_handoff/invoke" -Headers $headers -Body @{
            runId = $gatewayRunId
            stepId = $stepId
            toolCallId = $toolCallId
            agentId = "legacy-react-agent"
            tenantId = "default"
            userId = "$($login.data.userId)"
            agentIdentityId = "$($login.data.userId)"
            arguments = @{
                tenantId = "default"
                parentRunId = $parentRunId
                sourceAgentId = "legacy-react-agent"
                targetAgentId = "legacy-react-agent"
                handoffReason = "context pack handoff smoke $Marker"
                contextPackId = $contextPackId
                inputSummary = "handoff smoke input $Marker"
                contextSummaryJson = $contextSummary
                depth = 1
                ancestorAgentIds = @()
                traceId = "trace-$suffix"
            }
            resourceRefs = @{}
            idempotencyKey = "${gatewayRunId}:${toolCallId}"
            allowedToolIds = @("local_agent_handoff")
        }
        Assert-ApiOk $response "Invoke local_agent_handoff"
        if ($response.data.success -ne $true) {
            throw "local_agent_handoff failed: $($response.data | ConvertTo-Json -Depth 30 -Compress)"
        }
        $content = "$($response.data.content)"
        $toolResult = $content | ConvertFrom-Json
        if (-not $toolResult.handoffId) {
            throw "Tool result did not include handoffId: $content"
        }
        if (-not $toolResult.childRunId) {
            throw "Tool result did not include childRunId: $content"
        }
        if ($toolResult.contextPackId -ne $contextPackId) {
            throw "Tool result contextPackId mismatch: $content"
        }
        if ($toolResult.status -ne "RUNNING") {
            throw "Tool result status mismatch: $content"
        }
        $toolResult
    }
    if (-not $invoke) { exit 1 }

    Test-Step "Verify handoff list exposes contextPackId" {
        $response = Invoke-Json -Method GET -Path "/api/agent-runs/$parentRunId/handoffs?tenantId=default" -Headers $headers
        Assert-ApiOk $response "List handoffs"
        $handoff = @($response.data | Where-Object { $_.handoffId -eq $invoke.handoffId }) | Select-Object -First 1
        if (-not $handoff) {
            throw "Handoff $($invoke.handoffId) not found in parent run list"
        }
        if ($handoff.contextPackId -ne $contextPackId) {
            throw "Listed handoff contextPackId mismatch: $($handoff | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ($handoff.childRunId -ne $invoke.childRunId) {
            throw "Listed handoff childRunId mismatch: $($handoff | ConvertTo-Json -Depth 20 -Compress)"
        }
    } | Out-Null

    Test-Step "Verify handoff detail stays sanitized" {
        $response = Invoke-Json -Method GET -Path "/api/agent-handoffs/$($invoke.handoffId)" -Headers $headers
        Assert-ApiOk $response "Handoff detail"
        if ($response.data.contextPackId -ne $contextPackId) {
            throw "Handoff detail contextPackId mismatch: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $json = $response.data | ConvertTo-Json -Depth 20 -Compress
        if ($json -like "*contextSummaryJson*" -or $json -like "*inputSummaryJson*") {
            throw "Handoff detail leaked raw summary fields: $json"
        }
    } | Out-Null

    Test-Step "Verify child A2A run metadata carries contextPackId" {
        $response = Invoke-Json -Method GET -Path "/api/agent-runs/$($invoke.childRunId)" -Headers $headers
        Assert-ApiOk $response "Child run detail"
        if ($response.data.triggerType -ne "A2A") {
            throw "Child run triggerType mismatch: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $metadata = "$($response.data.metadataJson)" | ConvertFrom-Json
        if ($metadata.handoffId -ne $invoke.handoffId) {
            throw "Child run metadata handoffId mismatch: $($metadata | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ($metadata.parentRunId -ne $parentRunId) {
            throw "Child run metadata parentRunId mismatch: $($metadata | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ($metadata.contextPackId -ne $contextPackId) {
            throw "Child run metadata contextPackId mismatch: $($metadata | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($metadata.contextSummaryJson)" -notlike "*$Marker*") {
            throw "Child run metadata context summary did not include marker"
        }
    } | Out-Null

    Test-Step "Verify Tool Gateway audit recorded the handoff tool invocation" {
        $response = Invoke-Json -Method GET -Path "/api/tool-invocations?current=1&size=20&runId=$gatewayRunId&toolId=local_agent_handoff" -Headers $headers
        Assert-ApiOk $response "Tool invocation audit"
        $records = Get-PageRecords $response.data
        $audit = @($records | Where-Object { $_.stepId -eq $stepId -and $_.toolId -eq "local_agent_handoff" }) | Select-Object -First 1
        if (-not $audit) {
            throw "Tool invocation audit record not found for $gatewayRunId/$stepId"
        }
        if ($audit.status -ne "SUCCEEDED") {
            throw "Tool invocation audit status mismatch: $($audit | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($audit.argumentsSummary)" -like "*$Marker*") {
            throw "Tool invocation audit leaked raw input marker: $($audit | ConvertTo-Json -Depth 20 -Compress)"
        }
    } | Out-Null

    Write-Host "`nSummary: $passed / $total passed, $failed failed" -ForegroundColor Cyan
    Write-Host "Backend: $BaseUrl"
    Write-Host "Handoff: $($invoke.handoffId)"
    Write-Host "ChildRun: $($invoke.childRunId)"
    Write-Host "ContextPack: $contextPackId"
} catch {
    Write-Host "`nSummary: $passed / $total passed, $failed failed" -ForegroundColor Cyan
    Write-Error $_.Exception.Message
    exit 1
}

if ($failed -gt 0) {
    exit 1
}
