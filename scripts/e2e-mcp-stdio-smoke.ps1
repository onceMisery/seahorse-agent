param(
    [string]$BaseUrl = "http://127.0.0.1:9093",
    [string]$Username = "admin",
    [string]$Password = "admin123",
    [string]$BackendImage = "seahorse-agent-backend:latest",
    [string]$ContainerName = "seahorse-mcp-stdio-smoke",
    [string]$DockerNetwork = "seahorse-agent_default",
    [int]$HostPort = 9093,
    [string]$PostgresHost = "seahorse-postgres",
    [string]$PostgresDatabase = "seahorse",
    [string]$PostgresUsername = "seahorse",
    [string]$PostgresPassword = "seahorse",
    [string]$BackendJarPath = "",
    [switch]$KeepContainer
)

$ErrorActionPreference = "Stop"
$passed = 0
$failed = 0
$total = 0

$RepoRoot = Split-Path -Parent $PSScriptRoot
$McpServerScript = Join-Path $RepoRoot "resources\docker\mcp-stdio-echo.js"
$LeakSecret = "sk-stdio-secret-123456"
if ([string]::IsNullOrWhiteSpace($BackendJarPath)) {
    $BackendJarPath = Join-Path $RepoRoot "seahorse-agent-bootstrap\target\seahorse-agent-bootstrap-0.0.1-SNAPSHOT-exec.jar"
}

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

function Remove-SmokeContainer {
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & docker.exe inspect $ContainerName *> $null
        if ($LASTEXITCODE -eq 0) {
            & docker.exe rm -f $ContainerName *> $null
        }
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
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
    throw "Timed out waiting for smoke backend health"
}

try {
    Test-Step "Start temporary MCP-enabled backend" {
        Remove-SmokeContainer
        $args = @(
            "run", "-d",
            "--name", $ContainerName,
            "--network", $DockerNetwork,
            "-p", "${HostPort}:9090",
            "-e", "SERVER_PORT=9090",
            "-e", "SEAHORSE_AGENT_PRODUCT_MODE=enterprise",
            "-e", "SEAHORSE_AGENT_ADVANCED_MCP_TOOL_ENABLED=true",
            "-e", "SEAHORSE_AGENT_ADVANCED_TOOL_CATALOG_MANAGEMENT_ENABLED=true",
            "-e", "SEAHORSE_AGENT_ADVANCED_AGENT_RUN_MANAGEMENT_ENABLED=true",
            "-e", "SPRING_DATASOURCE_URL=jdbc:postgresql://${PostgresHost}:5432/${PostgresDatabase}",
            "-e", "SPRING_DATASOURCE_USERNAME=$PostgresUsername",
            "-e", "SPRING_DATASOURCE_PASSWORD=$PostgresPassword",
            "-e", "SEAHORSE_AGENT_ADAPTERS_REPOSITORY_TYPE=jdbc",
            "-e", "SEAHORSE_AGENT_ADAPTERS_AI_TYPE=mock",
            "-e", "SEAHORSE_AGENT_ADAPTERS_VECTOR_TYPE=noop",
            "-e", "SEAHORSE_AGENT_ADAPTERS_CACHE_TYPE=local",
            "-e", "SEAHORSE_AGENT_ADAPTERS_CACHE_REDIS_HOST=redis",
            "-e", "SEAHORSE_AGENT_ADAPTERS_CACHE_REDIS_PORT=6379",
            "-e", "SPRING_DATA_REDIS_HOST=redis",
            "-e", "SPRING_DATA_REDIS_PORT=6379",
            "-e", "MCP_STDIO_E2E_SECRET=$LeakSecret",
            "-e", "SEAHORSE_AGENT_ADAPTERS_STORAGE_TYPE=local",
            "-e", "SEAHORSE_AGENT_ADAPTERS_MQ_TYPE=direct",
            "-e", "SEAHORSE_AGENT_ADAPTERS_OBSERVATION_TYPE=noop",
            "-e", "SEAHORSE_AGENT_ADAPTERS_MCP_ENABLED=true",
            "-e", "SEAHORSE_AGENT_ADAPTERS_MCP_CALL_TIMEOUT=30s",
            "-e", "SEAHORSE_AGENT_ADAPTERS_MCP_STDIO_COMMAND_ALLOWLIST_0=node",
            "-e", "SEAHORSE_AGENT_ADAPTERS_MCP_SERVERS_0_ENABLED=true",
            "-e", "SEAHORSE_AGENT_ADAPTERS_MCP_SERVERS_0_NAME=local-echo",
            "-e", "SEAHORSE_AGENT_ADAPTERS_MCP_SERVERS_0_TRANSPORT=stdio",
            "-e", "SEAHORSE_AGENT_ADAPTERS_MCP_SERVERS_0_COMMAND=node",
            "-e", "SEAHORSE_AGENT_ADAPTERS_MCP_SERVERS_0_ARGS_0=/app/mcp-stdio-echo.js",
            "-e", "SEAHORSE_AGENT_ADAPTERS_MCP_SERVERS_1_ENABLED=true",
            "-e", "SEAHORSE_AGENT_ADAPTERS_MCP_SERVERS_1_NAME=blocked-shell",
            "-e", "SEAHORSE_AGENT_ADAPTERS_MCP_SERVERS_1_TRANSPORT=stdio",
            "-e", "SEAHORSE_AGENT_ADAPTERS_MCP_SERVERS_1_COMMAND=pwsh",
            "-e", "SEAHORSE_AGENT_ADAPTERS_MCP_SERVERS_1_ARGS_0=-NoLogo",
            "-e", "SEAHORSE_AGENT_CHAT_AGENT_TOOLS_MCP_INCLUDE=echo",
            $BackendImage
        )
        if (Test-Path -LiteralPath $BackendJarPath) {
            $jarMount = "$($BackendJarPath):/app/app.jar:ro"
            $args = $args[0..($args.Count - 2)] + @("-v", $jarMount) + $args[-1]
        }
        if (Test-Path -LiteralPath $McpServerScript) {
            $scriptMount = "$($McpServerScript):/app/mcp-stdio-echo.js:ro"
            $args = $args[0..($args.Count - 2)] + @("-v", $scriptMount) + $args[-1]
        }
        $output = & docker.exe @args
        if ($LASTEXITCODE -ne 0) {
            throw "docker run failed: $output"
        }
        $output
    } | Out-Null

    Test-Step "Wait for smoke backend health" {
        Wait-ForHealth
    } | Out-Null

    $login = Test-Step "Login" {
        $response = Invoke-Json -Method POST -Path "/auth/login" -Body @{ username = $Username; password = $Password }
        Assert-ApiOk $response "Login"
        if (-not $response.data.token) {
            throw "Login response did not include token"
        }
        $response
    }
    if (-not $login) { exit 1 }
    $headers = @{ Authorization = "Bearer $($login.data.token)" }
    $McpDiagnosticRunId = "mcp-server-test:local-echo"

    $server = Test-Step "List MCP stdio servers" {
        $response = Invoke-Json -Method GET -Path "/api/mcp/servers" -Headers $headers
        Assert-ApiOk $response "List MCP servers"
        $servers = @($response.data)
        $found = @($servers | Where-Object { $_.name -eq "local-echo" })[0]
        if (-not $found) {
            throw "local-echo was not returned by /api/mcp/servers"
        }
        if (-not @($found.tools | Where-Object { $_.toolId -eq "echo" })) {
            throw "local-echo did not expose echo tool"
        }
        $blocked = @($servers | Where-Object { $_.name -eq "blocked-shell" })[0]
        if (-not $blocked) {
            throw "blocked-shell was not returned by /api/mcp/servers"
        }
        if ("$($blocked.status)" -ne "FAILED") {
            throw "blocked-shell status was not FAILED: $($blocked | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($blocked.stderrTail)" -notlike "*stdio command not allowed: pwsh*") {
            throw "blocked-shell did not expose allowlist diagnostic: $($blocked.stderrTail)"
        }
        if (@($blocked.tools).Count -ne 0) {
            throw "blocked-shell should not expose tools: $($blocked.tools | ConvertTo-Json -Depth 20 -Compress)"
        }
        $found
    }
    if (-not $server) { exit 1 }

    Test-Step "MCP safe echo test call requires approval" {
        $response = Invoke-Json -Method POST -Path "/api/mcp/servers/local-echo/test" -Headers $headers
        Assert-ApiOk $response "Test MCP server"
        if ($response.data.success -ne $false) {
            throw "MCP test should be gated before approval: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($response.data.status)" -ne "APPROVAL_REQUIRED") {
            throw "MCP test status was not APPROVAL_REQUIRED: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($response.data.reasonCode)" -ne "TOOL_APPROVAL_REQUIRED") {
            throw "MCP test reason was not TOOL_APPROVAL_REQUIRED: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if (-not $response.data.approvalId) {
            throw "MCP test did not return approvalId"
        }
        $approval = Invoke-Json -Method GET -Path "/api/approvals/$($response.data.approvalId)" -Headers $headers
        Assert-ApiOk $approval "Read MCP test approval"
        if ("$($approval.data.runId)" -ne $McpDiagnosticRunId) {
            throw "MCP test approval runId mismatch: $($approval.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($approval.data.status)" -ne "PENDING") {
            throw "MCP test approval was not pending: $($approval.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $response.data | ConvertTo-Json -Compress | Write-Host
        $response.data.approvalId
    } | Out-Null

    $diagnosticApproval = Test-Step "Approve and run MCP safe echo test call" {
        $response = Invoke-Json -Method POST -Path "/api/mcp/servers/local-echo/test" -Headers $headers
        Assert-ApiOk $response "Read MCP diagnostic approval"
        if (-not $response.data.approvalId) {
            throw "MCP diagnostic approval id missing before approve"
        }
        $approvalId = "$($response.data.approvalId)"
        $approved = Invoke-Json -Method POST -Path "/api/approvals/$approvalId/approve" -Headers $headers -Body @{
            decisionComment = "Allow MCP diagnostic smoke test"
        }
        Assert-ApiOk $approved "Approve MCP diagnostic test"
        if ("$($approved.data.status)" -ne "APPROVED") {
            throw "MCP diagnostic approval was not approved: $($approved.data | ConvertTo-Json -Depth 20 -Compress)"
        }

        $result = Invoke-Json -Method POST -Path "/api/mcp/servers/local-echo/test" -Headers $headers
        Assert-ApiOk $result "Run approved MCP server test"
        if ($result.data.success -ne $true) {
            throw "Approved MCP test did not succeed: $($result.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($result.data.status)" -ne "SUCCESS") {
            throw "Approved MCP test status was not SUCCESS: $($result.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($result.data.content)" -notlike "*stdio:seahorse mcp health check*") {
            throw "Unexpected MCP echo content: $($result.data.content)"
        }
        $result.data | ConvertTo-Json -Compress | Write-Host
        $approved.data
    }
    if (-not $diagnosticApproval) { exit 1 }

    Test-Step "Verify MCP diagnostic tool audit" {
        $response = Invoke-Json -Method GET -Path "/api/tool-invocations?current=1&size=20&runId=$McpDiagnosticRunId&toolId=echo" -Headers $headers
        Assert-ApiOk $response "Read MCP diagnostic tool audit"
        $records = @($response.data.records)
        $succeeded = @($records | Where-Object { $_.status -eq "SUCCEEDED" -and $_.toolId -eq "echo" })[0]
        if (-not $succeeded) {
            throw "MCP diagnostic test did not create SUCCEEDED tool audit: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $succeeded | ConvertTo-Json -Compress | Write-Host
    } | Out-Null

    Test-Step "Verify MCP tool catalog entry" {
        $response = Invoke-Json -Method GET -Path "/api/tools?current=1&size=50&provider=MCP" -Headers $headers
        Assert-ApiOk $response "List tools"
        $records = @($response.data.records)
        $echo = @($records | Where-Object { $_.toolId -eq "echo" -and $_.provider -eq "MCP" })[0]
        if (-not $echo) {
            throw "Tool catalog did not include provider=MCP toolId=echo"
        }
        if ($echo.enabled -ne $true) {
            throw "MCP echo tool is not enabled"
        }
        if ("$($echo.riskLevel)" -ne "HIGH") {
            throw "MCP echo tool was not marked HIGH risk: $($echo | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ($echo.requiresApproval -ne $true) {
            throw "MCP echo tool does not require approval: $($echo | ConvertTo-Json -Depth 20 -Compress)"
        }
        $echo | ConvertTo-Json -Compress | Write-Host
    } | Out-Null

    Test-Step "Preflight MCP echo tool requires approval" {
        $runId = "mcp-stdio-smoke-run"
        $stepId = "mcp-stdio-preflight-step"
        $toolCallId = "mcp-stdio-preflight-call"
        $response = Invoke-Json -Method POST -Path "/api/tools/echo/preflight" -Headers $headers -Body @{
            runId = $runId
            stepId = $stepId
            toolCallId = $toolCallId
            agentId = "legacy-react-agent"
            tenantId = "default"
            userId = $Username
            agentIdentityId = $Username
            arguments = @{ text = "seahorse mcp governed preflight" }
            resourceRefs = @{}
            idempotencyKey = "${runId}:${toolCallId}"
            allowedToolIds = @("echo")
        }
        Assert-ApiOk $response "Preflight MCP echo tool"
        if ("$($response.data.effect)" -ne "APPROVAL_REQUIRED") {
            throw "MCP echo preflight did not require approval: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($response.data.reasonCode)" -ne "TOOL_APPROVAL_REQUIRED") {
            throw "MCP echo preflight reason was not TOOL_APPROVAL_REQUIRED: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if (-not $response.data.approvalId) {
            throw "MCP echo preflight did not return approvalId"
        }
        $approval = Invoke-Json -Method GET -Path "/api/approvals/$($response.data.approvalId)" -Headers $headers
        Assert-ApiOk $approval "Read MCP echo approval"
        if ("$($approval.data.toolId)" -ne "echo") {
            throw "Approval toolId was not echo: $($approval.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($approval.data.approvalType)" -ne "TOOL_EXECUTION") {
            throw "Approval type was not TOOL_EXECUTION: $($approval.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($approval.data.status)" -ne "PENDING") {
            throw "Approval status was not PENDING: $($approval.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $approval.data | ConvertTo-Json -Compress | Write-Host
    } | Out-Null

    Test-Step "Refresh and restart MCP server" {
        $refresh = Invoke-Json -Method POST -Path "/api/mcp/servers/local-echo/refresh-tools" -Headers $headers
        Assert-ApiOk $refresh "Refresh MCP tools"
        if (-not @($refresh.data.tools | Where-Object { $_.toolId -eq "echo" })) {
            throw "Refresh result did not include echo tool"
        }
        $restart = Invoke-Json -Method POST -Path "/api/mcp/servers/local-echo/restart" -Headers $headers
        Assert-ApiOk $restart "Restart MCP server"
        if (-not @($restart.data.tools | Where-Object { $_.toolId -eq "echo" })) {
            throw "Restart result did not include echo tool"
        }
    } | Out-Null

    Test-Step "Read MCP stderr tail" {
        $response = Invoke-Json -Method GET -Path "/api/mcp/servers/local-echo/stderr-tail" -Headers $headers
        Assert-ApiOk $response "Read MCP stderr tail"
        $stderrTail = "$($response.data)"
        if ($stderrTail.Contains($LeakSecret)) {
            throw "MCP stderr tail leaked raw secret: $stderrTail"
        }
        if (-not $stderrTail.Contains("[REDACTED]")) {
            throw "MCP stderr tail did not include redaction marker: $stderrTail"
        }
    } | Out-Null

    Write-Host "`nSummary: $passed / $total passed, $failed failed" -ForegroundColor Cyan
    Write-Host "Smoke backend: $BaseUrl"
    Write-Host "MCP server: local-echo"
    Write-Host "MCP tool: echo"
} catch {
    Write-Host "`nSummary: $passed / $total passed, $failed failed" -ForegroundColor Cyan
    Write-Error $_.Exception.Message
    exit 1
} finally {
    if (-not $KeepContainer) {
        Remove-SmokeContainer
    }
}

if ($failed -gt 0) {
    exit 1
}
