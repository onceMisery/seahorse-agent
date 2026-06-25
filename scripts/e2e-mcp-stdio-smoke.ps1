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
    [switch]$KeepContainer
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
            "-e", "SEAHORSE_AGENT_ADVANCED_TOOL_CATALOG_MANAGEMENT_ENABLED=true",
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
            "-e", "SEAHORSE_AGENT_ADAPTERS_STORAGE_TYPE=local",
            "-e", "SEAHORSE_AGENT_ADAPTERS_MQ_TYPE=direct",
            "-e", "SEAHORSE_AGENT_ADAPTERS_OBSERVATION_TYPE=noop",
            "-e", "SEAHORSE_AGENT_ADAPTERS_MCP_ENABLED=true",
            "-e", "SEAHORSE_AGENT_ADAPTERS_MCP_CALL_TIMEOUT=30s",
            "-e", "SEAHORSE_AGENT_ADAPTERS_MCP_SERVERS_0_ENABLED=true",
            "-e", "SEAHORSE_AGENT_ADAPTERS_MCP_SERVERS_0_NAME=local-echo",
            "-e", "SEAHORSE_AGENT_ADAPTERS_MCP_SERVERS_0_TRANSPORT=stdio",
            "-e", "SEAHORSE_AGENT_ADAPTERS_MCP_SERVERS_0_COMMAND=node",
            "-e", "SEAHORSE_AGENT_ADAPTERS_MCP_SERVERS_0_ARGS_0=/app/mcp-stdio-echo.js",
            "-e", "SEAHORSE_AGENT_CHAT_AGENT_TOOLS_MCP_INCLUDE=echo",
            $BackendImage
        )
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
        $found
    }
    if (-not $server) { exit 1 }

    Test-Step "Run MCP safe echo test call" {
        $response = Invoke-Json -Method POST -Path "/api/mcp/servers/local-echo/test" -Headers $headers
        Assert-ApiOk $response "Test MCP server"
        if ($response.data.success -ne $true) {
            throw "MCP test did not succeed: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($response.data.content)" -notlike "*stdio:seahorse mcp health check*") {
            throw "Unexpected MCP echo content: $($response.data.content)"
        }
        $response.data | ConvertTo-Json -Compress | Write-Host
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
        $echo | ConvertTo-Json -Compress | Write-Host
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
