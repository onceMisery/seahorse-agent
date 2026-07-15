param(
    [string]$BaseUrl = "http://127.0.0.1:9090",
    [string]$Username = "admin",
    [string]$Password = "admin123",
    [string]$Marker = "seahorse-sandbox-python-smoke",
    [string]$PostgresContainer = "seahorse-postgres",
    [string]$PostgresUser = "seahorse",
    [string]$PostgresDatabase = "seahorse",
    [long]$KernelRunProfileId = -9101,
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
    if ($Page -is [System.Array]) {
        return @($Page)
    }
    return @()
}

function Invoke-SandboxPythonTool {
    param(
        [hashtable]$Headers,
        [hashtable]$Body,
        [string]$Name
    )

    $response = Invoke-Json -Method POST -Path "/api/tools/sandbox_python/invoke" -Headers $Headers -Body $Body
    Assert-ApiOk $response $Name

    $requiresApproval = $response.data.success -eq $false -and (
        "$($response.data.error)" -eq "TOOL_APPROVAL_REQUIRED" -or
        "$($response.data.reasonCode)" -eq "TOOL_APPROVAL_REQUIRED"
    )
    if (-not $requiresApproval) {
        return $response
    }

    if (-not $response.data.approvalId) {
        throw "$Name required approval but did not return approvalId: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
    }
    $approvalId = "$($response.data.approvalId)"
    $approval = Invoke-Json -Method GET -Path "/api/approvals/$approvalId" -Headers $Headers
    Assert-ApiOk $approval "Read $Name approval"
    if ("$($approval.data.runId)" -ne "$($Body.runId)" -or "$($approval.data.stepId)" -ne "$($Body.stepId)") {
        throw "$Name approval did not match invocation identity: $($approval.data | ConvertTo-Json -Depth 20 -Compress)"
    }
    if ("$($approval.data.status)" -ne "PENDING") {
        throw "$Name approval was not pending: $($approval.data | ConvertTo-Json -Depth 20 -Compress)"
    }

    $approved = Invoke-Json -Method POST -Path "/api/approvals/$approvalId/approve" -Headers $Headers -Body @{
        decisionComment = "Allow sandbox python smoke test"
    }
    Assert-ApiOk $approved "Approve $Name"
    if ("$($approved.data.status)" -ne "APPROVED") {
        throw "$Name approval was not approved: $($approved.data | ConvertTo-Json -Depth 20 -Compress)"
    }

    $retry = Invoke-Json -Method POST -Path "/api/tools/sandbox_python/invoke" -Headers $Headers -Body $Body
    Assert-ApiOk $retry "Retry $Name after approval"
    return $retry
}

function Invoke-PostgresScalar {
    param([string]$Sql)
    $raw = & docker exec $PostgresContainer psql -U $PostgresUser -d $PostgresDatabase -At -F "`t" -c $Sql
    if ($LASTEXITCODE -ne 0) {
        throw "psql exited with $LASTEXITCODE for SQL: $Sql"
    }
    $rows = @($raw | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($rows.Count -eq 0) {
        throw "SQL returned no rows: $Sql"
    }
    return $rows[0]
}

function New-RealAgentRunId {
    param(
        [hashtable]$Headers,
        [string]$Marker,
        [long]$RunProfileId
    )

    $created = Invoke-Json -Method POST -Path "/api/conversations" -Headers $Headers
    Assert-ApiOk $created "Create sandbox python smoke conversation"
    if (-not $created.data) {
        throw "Create conversation response did not include id"
    }
    $conversationId = "$($created.data)"
    $question = "Sandbox Python smoke $Marker. Reply with one short sentence."
    $encodedQuestion = [System.Uri]::EscapeDataString($question)
    $response = Invoke-WebRequest -Uri "$BaseUrl/rag/v3/chat?conversationId=$conversationId&question=$encodedQuestion&runProfileId=$RunProfileId&chatMode=agent" `
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

    $runId = ""
    $matches = [regex]::Matches($response.Content, '"runId"\s*:\s*"([^"]+)"')
    if ($matches.Count -gt 0) {
        $runId = $matches[0].Groups[1].Value
    }
    if ([string]::IsNullOrWhiteSpace($runId)) {
        throw "Chat SSE did not include runId"
    }

    $safeRunId = $runId.Replace("'", "''")
    $row = Invoke-PostgresScalar "SELECT run_id FROM sa_agent_run WHERE run_id = '$safeRunId';"
    if ($row -ne $runId) {
        throw "Agent run was not persisted before tool invocation: $runId"
    }
    return [PSCustomObject]@{
        ConversationId = $conversationId
        RunId = $runId
    }
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
    $smokeRun = Test-Step "Create real agent run for governed sandbox_python binding" {
        New-RealAgentRunId -Headers $headers -Marker $Marker -RunProfileId $KernelRunProfileId
    }
    if (-not $smokeRun) { exit 1 }

    $runId = "$($smokeRun.RunId)"
    $toolCallId = "sandbox-python-smoke-call-$suffix"
    $escapedMarker = $Marker.Replace("\", "\\").Replace("'", "\'")
    $code = "import os`nfrom pathlib import Path`nstatus = dict(line.split(':', 1) for line in Path('/proc/self/status').read_text(encoding='utf-8').splitlines() if ':' in line)`nassert os.geteuid() != 0, os.geteuid()`nassert status.get('NoNewPrivs', '').strip() == '1', status.get('NoNewPrivs')`nassert status.get('CapEff', '').strip() == '0000000000000000', status.get('CapEff')`nassert not os.access('/', os.W_OK), 'sandbox root filesystem is writable'`nPath('answer.txt').write_text('artifact $escapedMarker', encoding='utf-8')`nprint('$escapedMarker')"

    Test-Step "Invoke sandbox_python through Tool Gateway" {
        $requestBody = @{
            runId = $runId
            stepId = "sandbox-python-smoke-step-$suffix"
            toolCallId = $toolCallId
            agentId = "legacy-react-agent"
            tenantId = "default"
            userId = "$($login.data.userId)"
            agentIdentityId = "$($login.data.userId)"
            arguments = @{ code = $code }
            resourceRefs = @{}
            idempotencyKey = "${runId}:${toolCallId}"
            allowedToolIds = @("sandbox_python")
        }
        $response = Invoke-SandboxPythonTool -Headers $headers -Body $requestBody -Name "Invoke sandbox_python"
        Assert-ApiOk $response "Invoke sandbox_python"
        if ($response.data.success -ne $true) {
            throw "sandbox_python failed: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $content = "$($response.data.content)"
        if ($content -notlike "*$Marker*") {
            throw "sandbox_python content did not contain marker '$Marker': $content"
        }
        if ($content -notlike "*`"executionStatus`":`"SUCCEEDED`"*") {
            throw "sandbox_python content did not report SUCCEEDED: $content"
        }
        if ($content -notlike "*`"mediaType`":`"text/plain`"*") {
            throw "sandbox_python content did not include text artifact metadata: $content"
        }
        if ($content -notlike "*`"scanStatus`":`"CLEAN`"*") {
            throw "sandbox_python content did not include CLEAN artifact scan status: $content"
        }
        if ($content -notlike "*`"promptVisible`":true*") {
            throw "sandbox_python content did not include prompt-visible artifact: $content"
        }
        $response.data | ConvertTo-Json -Compress | Write-Host
    } | Out-Null

    $quotaToolCallId = "sandbox-python-workspace-quota-call-$suffix"
    $quotaCode = "from pathlib import Path`nPath('quota-a.bin').write_bytes(b'a' * 40000000)`nPath('quota-b.bin').write_bytes(b'b' * 40000000)`nprint('workspace quota probe')"
    Test-Step "Reject sandbox_python workspace exceeding cumulative file quota" {
        $requestBody = @{
            runId = $runId
            stepId = "sandbox-python-workspace-quota-step-$suffix"
            toolCallId = $quotaToolCallId
            agentId = "legacy-react-agent"
            tenantId = "default"
            userId = "$($login.data.userId)"
            agentIdentityId = "$($login.data.userId)"
            arguments = @{ code = $quotaCode }
            resourceRefs = @{}
            idempotencyKey = "${runId}:${quotaToolCallId}"
            allowedToolIds = @("sandbox_python")
        }
        $response = Invoke-SandboxPythonTool -Headers $headers -Body $requestBody -Name "Invoke sandbox_python workspace quota failure"
        Assert-ApiOk $response "Invoke sandbox_python workspace quota failure"
        if ($response.data.success -ne $false -or "$($response.data.error)" -notlike "*sandbox workspace exceeds session file limit*" -or "$($response.data.content)" -notin @("", $null)) {
            throw "sandbox_python workspace quota did not fail closed: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
    } | Out-Null

    $fileCountToolCallId = "sandbox-python-workspace-file-count-call-$suffix"
    $fileCountCode = "from pathlib import Path`nfor index in range(300):`n    Path(f'count-{index}.txt').write_text('x', encoding='utf-8')`nprint('workspace file count probe')"
    Test-Step "Reject sandbox_python workspace exceeding file count limit" {
        $requestBody = @{
            runId = $runId
            stepId = "sandbox-python-workspace-file-count-step-$suffix"
            toolCallId = $fileCountToolCallId
            agentId = "legacy-react-agent"
            tenantId = "default"
            userId = "$($login.data.userId)"
            agentIdentityId = "$($login.data.userId)"
            arguments = @{ code = $fileCountCode }
            resourceRefs = @{}
            idempotencyKey = "${runId}:${fileCountToolCallId}"
            allowedToolIds = @("sandbox_python")
        }
        $response = Invoke-SandboxPythonTool -Headers $headers -Body $requestBody -Name "Invoke sandbox_python workspace file count failure"
        Assert-ApiOk $response "Invoke sandbox_python workspace file count failure"
        if ($response.data.success -ne $false -or "$($response.data.error)" -notlike "*sandbox workspace exceeds session file count limit*" -or "$($response.data.content)" -notin @("", $null)) {
            throw "sandbox_python workspace file count did not fail closed: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
    } | Out-Null

    Test-Step "Verify sandbox_python Tool Gateway audit summary" {
        $response = Invoke-Json -Method GET -Path "/api/tool-invocations?current=1&size=20&runId=$runId&toolId=sandbox_python" -Headers $headers
        Assert-ApiOk $response "Read sandbox_python tool audit"
        $records = Get-PageRecords $response.data
        $audit = @($records | Where-Object { "$($_.stepId)" -eq "sandbox-python-smoke-step-$suffix" -and "$($_.toolId)" -eq "sandbox_python" }) | Select-Object -First 1
        if (-not $audit) {
            throw "sandbox_python audit record not found for run $runId step sandbox-python-smoke-step-$suffix`: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($audit.status)" -ne "SUCCEEDED") {
            throw "sandbox_python audit status mismatch: $($audit | ConvertTo-Json -Depth 20 -Compress)"
        }
        $summary = "$($audit.argumentsSummary)"
        foreach ($required in @(
                '"toolId":"sandbox_python"',
                '"runtimeType":"CODE_INTERPRETER"',
                '"networkRequested":false',
                '"requestedHostsPresent":false',
                '"requestedHostCount":0',
                '"argumentKeys":["code"]',
                '"argumentCount":1',
                '"argumentValueCount":1',
                '"argumentValueTotalLength":',
                '"argumentValueMaxLength":',
                '"codeLength":'
            )) {
            if (-not $summary.Contains($required)) {
                throw "sandbox_python audit summary did not include $required`: $summary"
            }
        }
        foreach ($forbidden in @(
                $Marker,
                $escapedMarker,
                $code,
                "Path('answer.txt')",
                "write_text",
                "print(",
                "artifact $Marker",
                "answer.txt"
            )) {
            if (-not [string]::IsNullOrWhiteSpace("$forbidden") -and $summary.Contains("$forbidden")) {
                throw "sandbox_python audit summary leaked raw code value '$forbidden': $summary"
            }
        }

        $quotaAudit = @($records | Where-Object { "$($_.stepId)" -eq "sandbox-python-workspace-quota-step-$suffix" -and "$($_.toolId)" -eq "sandbox_python" }) | Select-Object -First 1
        if (-not $quotaAudit -or "$($quotaAudit.status)" -ne "FAILED") {
            throw "sandbox_python workspace quota audit was not failed: $($quotaAudit | ConvertTo-Json -Depth 20 -Compress)"
        }
        $quotaSummary = "$($quotaAudit.argumentsSummary)"
        foreach ($forbidden in @($quotaCode, "quota-a.bin", "quota-b.bin", "40000000", "workspace quota probe")) {
            if (-not [string]::IsNullOrWhiteSpace("$forbidden") -and $quotaSummary.Contains($forbidden)) {
                throw "sandbox_python workspace quota audit leaked raw code value '$forbidden': $quotaSummary"
            }
        }

        $fileCountAudit = @($records | Where-Object { "$($_.stepId)" -eq "sandbox-python-workspace-file-count-step-$suffix" -and "$($_.toolId)" -eq "sandbox_python" }) | Select-Object -First 1
        if (-not $fileCountAudit -or "$($fileCountAudit.status)" -ne "FAILED") {
            throw "sandbox_python workspace file count audit was not failed: $($fileCountAudit | ConvertTo-Json -Depth 20 -Compress)"
        }
        $fileCountSummary = "$($fileCountAudit.argumentsSummary)"
        foreach ($forbidden in @($fileCountCode, "count-{index}.txt", "range(300)", "workspace file count probe")) {
            if (-not [string]::IsNullOrWhiteSpace("$forbidden") -and $fileCountSummary.Contains($forbidden)) {
                throw "sandbox_python workspace file count audit leaked raw code value '$forbidden': $fileCountSummary"
            }
        }
    } | Out-Null

    Write-Host "`nSummary: $passed / $total passed, $failed failed" -ForegroundColor Cyan
    Write-Host "Backend: $BaseUrl"
    Write-Host "Conversation: $($smokeRun.ConversationId)"
    Write-Host "Run: $runId"
    Write-Host "Tool: sandbox_python"
} catch {
    Write-Host "`nSummary: $passed / $total passed, $failed failed" -ForegroundColor Cyan
    Write-Error $_.Exception.Message
    exit 1
}

if ($failed -gt 0) {
    exit 1
}
