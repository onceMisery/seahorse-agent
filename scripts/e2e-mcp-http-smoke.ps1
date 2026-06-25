param(
    [string]$BaseUrl = "http://127.0.0.1:9096",
    [string]$Username = "admin",
    [string]$Password = "admin123",
    [string]$BackendImage = "seahorse-agent-backend:latest",
    [string]$BackendContainerName = "seahorse-mcp-http-smoke",
    [string]$McpServerContainerName = "seahorse-mcp-http-server-smoke",
    [string]$DockerNetwork = "seahorse-agent_default",
    [int]$HostPort = 9096,
    [int]$McpHostPort = 9301,
    [string]$PostgresHost = "seahorse-postgres",
    [string]$PostgresDatabase = "seahorse",
    [string]$PostgresUsername = "seahorse",
    [string]$PostgresPassword = "seahorse",
    [string]$BackendJarPath = "",
    [switch]$KeepContainers
)

$ErrorActionPreference = "Stop"
$passed = 0
$failed = 0
$total = 0

$RepoRoot = Split-Path -Parent $PSScriptRoot
$McpServerScript = Join-Path $RepoRoot "resources\docker\mcp-http-echo.js"
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
    param([string]$Name)
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & docker.exe inspect $Name *> $null
        if ($LASTEXITCODE -eq 0) {
            & docker.exe rm -f $Name *> $null
        }
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
}

function Invoke-JsonAt {
    param(
        [string]$Url,
        [string]$Method,
        [object]$Body = $null,
        [hashtable]$Headers = @{},
        [int]$ExpectedStatus = 200
    )

    $bodyText = $null
    if ($null -ne $Body) {
        $bodyText = if ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Depth 20 -Compress }
    }

    $tempBodyFile = $null
    $args = @("-sS", "-w", "`n%{http_code}", "-X", $Method, $Url)
    if ($bodyText) {
        $tempBodyFile = New-TemporaryFile
        $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
        [System.IO.File]::WriteAllText($tempBodyFile.FullName, $bodyText, $utf8NoBom)
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
        throw "curl exited with $exitCode for $Method $Url"
    }

    $lines = @($raw)
    if ($lines.Count -eq 0) {
        throw "empty curl output for $Method $Url"
    }
    $status = [int]$lines[-1]
    $content = if ($lines.Count -gt 1) { ($lines[0..($lines.Count - 2)] -join "`n") } else { "" }
    if ($status -ne $ExpectedStatus) {
        throw "Expected HTTP $ExpectedStatus but got $status for $Method $Url body=$content"
    }
    if ([string]::IsNullOrWhiteSpace($content)) {
        return $null
    }
    return $content | ConvertFrom-Json
}

function Invoke-Json {
    param(
        [string]$Method,
        [string]$Path,
        [object]$Body = $null,
        [hashtable]$Headers = @{},
        [int]$ExpectedStatus = 200
    )
    return Invoke-JsonAt -Url "$BaseUrl$Path" -Method $Method -Body $Body -Headers $Headers -ExpectedStatus $ExpectedStatus
}

function Assert-ApiOk {
    param([object]$Response, [string]$Name)
    if ($null -eq $Response -or "$($Response.code)" -ne "0") {
        throw "$Name API error: $($Response | ConvertTo-Json -Depth 20 -Compress)"
    }
}

function Wait-ForHealth {
    param([string]$Url, [int]$Attempts = 90)
    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        try {
            $health = Invoke-JsonAt -Url $Url -Method GET
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
    throw "Timed out waiting for health at $Url"
}

try {
    Test-Step "Start temporary HTTP MCP server" {
        if (-not (Test-Path -LiteralPath $McpServerScript)) {
            throw "MCP HTTP server script not found: $McpServerScript"
        }
        Remove-SmokeContainer -Name $McpServerContainerName
        $mountPath = "$($McpServerScript):/app/mcp-http-echo.js:ro"
        $args = @(
            "run", "-d",
            "--name", $McpServerContainerName,
            "--network", $DockerNetwork,
            "-p", "${McpHostPort}:3001",
            "-v", $mountPath,
            "--entrypoint", "node",
            $BackendImage,
            "/app/mcp-http-echo.js"
        )
        $output = & docker.exe @args
        if ($LASTEXITCODE -ne 0) {
            throw "docker run failed: $output"
        }
        $output
    } | Out-Null

    Test-Step "Wait for HTTP MCP server health" {
        Wait-ForHealth -Url "http://127.0.0.1:${McpHostPort}/health" -Attempts 30
    } | Out-Null

    Test-Step "Direct HTTP MCP JSON-RPC echo" {
        $init = Invoke-JsonAt -Url "http://127.0.0.1:${McpHostPort}/mcp" -Method POST -Body @{
            jsonrpc = "2.0"; id = 1; method = "initialize"; params = @{
                protocolVersion = "2026-02-28"; clientInfo = @{ name = "smoke"; version = "1.0.0" }
            }
        }
        if ($init.result.serverInfo.name -ne "seahorse-http-echo") {
            throw "Unexpected initialize result: $($init | ConvertTo-Json -Depth 20 -Compress)"
        }
        $tools = Invoke-JsonAt -Url "http://127.0.0.1:${McpHostPort}/mcp" -Method POST -Body @{
            jsonrpc = "2.0"; id = 2; method = "tools/list"; params = @{}
        }
        if (-not @($tools.result.tools | Where-Object { $_.name -eq "http.echo" })) {
            throw "Direct tools/list did not include http.echo"
        }
        $call = Invoke-JsonAt -Url "http://127.0.0.1:${McpHostPort}/mcp" -Method POST -Body @{
            jsonrpc = "2.0"; id = 3; method = "tools/call"; params = @{
                name = "http.echo"; arguments = @{ text = "direct smoke" }
            }
        }
        $text = @($call.result.content)[0].text
        if ($text -ne "http:direct smoke") {
            throw "Unexpected direct echo result: $text"
        }
    } | Out-Null

    Test-Step "Start temporary MCP HTTP-enabled backend" {
        Remove-SmokeContainer -Name $BackendContainerName
        $args = @(
            "run", "-d",
            "--name", $BackendContainerName,
            "--network", $DockerNetwork,
            "-p", "${HostPort}:9090",
            "-e", "SERVER_PORT=9090",
            "-e", "SEAHORSE_AGENT_PRODUCT_MODE=enterprise",
            "-e", "SEAHORSE_AGENT_ADVANCED_MCP_TOOL_ENABLED=true",
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
            "-e", "SEAHORSE_AGENT_ADAPTERS_MCP_CALL_TIMEOUT=5s",
            "-e", "SEAHORSE_AGENT_ADAPTERS_MCP_SERVERS_0_ENABLED=true",
            "-e", "SEAHORSE_AGENT_ADAPTERS_MCP_SERVERS_0_NAME=http-echo",
            "-e", "SEAHORSE_AGENT_ADAPTERS_MCP_SERVERS_0_TRANSPORT=STREAMABLE_HTTP",
            "-e", "SEAHORSE_AGENT_ADAPTERS_MCP_SERVERS_0_URL=http://${McpServerContainerName}:3001",
            "-e", "SEAHORSE_AGENT_ADAPTERS_MCP_SERVERS_0_AUTH_TYPE=NONE",
            "-e", "SEAHORSE_AGENT_ADAPTERS_MCP_SERVERS_1_ENABLED=true",
            "-e", "SEAHORSE_AGENT_ADAPTERS_MCP_SERVERS_1_NAME=broken-http",
            "-e", "SEAHORSE_AGENT_ADAPTERS_MCP_SERVERS_1_TRANSPORT=STREAMABLE_HTTP",
            "-e", "SEAHORSE_AGENT_ADAPTERS_MCP_SERVERS_1_URL=http://127.0.0.1:9",
            "-e", "SEAHORSE_AGENT_ADAPTERS_MCP_SERVERS_1_AUTH_TYPE=NONE",
            "-e", "SEAHORSE_AGENT_CHAT_AGENT_TOOLS_MCP_INCLUDE=http.echo",
            $BackendImage
        )
        if (Test-Path -LiteralPath $BackendJarPath) {
            $jarMount = "$($BackendJarPath):/app/app.jar:ro"
            $args = $args[0..($args.Count - 2)] + @("-v", $jarMount) + $args[-1]
        }
        $output = & docker.exe @args
        if ($LASTEXITCODE -ne 0) {
            throw "docker run failed: $output"
        }
        $output
    } | Out-Null

    Test-Step "Wait for smoke backend health" {
        Wait-ForHealth -Url "$BaseUrl/actuator/health"
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

    $servers = Test-Step "List MCP HTTP server states" {
        $response = Invoke-Json -Method GET -Path "/api/mcp/servers" -Headers $headers
        Assert-ApiOk $response "List MCP servers"
        $items = @($response.data)
        $ready = @($items | Where-Object { $_.name -eq "http-echo" })[0]
        if (-not $ready) {
            throw "http-echo was not returned by /api/mcp/servers"
        }
        if ($ready.status -ne "READY" -or $ready.transport -ne "STREAMABLE_HTTP") {
            throw "http-echo was not ready: $($ready | ConvertTo-Json -Depth 20 -Compress)"
        }
        if (-not @($ready.tools | Where-Object { $_.toolId -eq "http.echo" })) {
            throw "http-echo did not expose http.echo tool"
        }
        $failedServer = @($items | Where-Object { $_.name -eq "broken-http" })[0]
        if (-not $failedServer -or $failedServer.status -ne "FAILED") {
            throw "broken-http did not report FAILED: $($failedServer | ConvertTo-Json -Depth 20 -Compress)"
        }
        $items | ConvertTo-Json -Depth 20 -Compress | Write-Host
        $items
    }
    if (-not $servers) { exit 1 }

    Test-Step "Run MCP HTTP safe echo test call" {
        $response = Invoke-Json -Method POST -Path "/api/mcp/servers/http-echo/test" -Headers $headers
        Assert-ApiOk $response "Test MCP HTTP server"
        if ($response.data.success -ne $true) {
            throw "MCP HTTP test did not succeed: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($response.data.content)" -ne "http:seahorse mcp health check") {
            throw "Unexpected MCP HTTP echo content: $($response.data.content)"
        }
        $response.data | ConvertTo-Json -Compress | Write-Host
    } | Out-Null

    Test-Step "Verify MCP HTTP tool catalog entry" {
        $response = Invoke-Json -Method GET -Path "/api/tools?current=1&size=50&provider=MCP&keyword=http.echo" -Headers $headers
        Assert-ApiOk $response "List tools"
        $records = @($response.data.records)
        $echo = @($records | Where-Object { $_.toolId -eq "http.echo" -and $_.provider -eq "MCP" })[0]
        if (-not $echo) {
            throw "Tool catalog did not include provider=MCP toolId=http.echo"
        }
        if ($echo.enabled -ne $true) {
            throw "MCP HTTP echo tool is not enabled"
        }
        $echo | ConvertTo-Json -Compress | Write-Host
    } | Out-Null

    Test-Step "Refresh and restart MCP HTTP server" {
        $refresh = Invoke-Json -Method POST -Path "/api/mcp/servers/http-echo/refresh-tools" -Headers $headers
        Assert-ApiOk $refresh "Refresh MCP HTTP tools"
        if (-not @($refresh.data.tools | Where-Object { $_.toolId -eq "http.echo" })) {
            throw "Refresh result did not include http.echo tool"
        }
        $restart = Invoke-Json -Method POST -Path "/api/mcp/servers/http-echo/restart" -Headers $headers
        Assert-ApiOk $restart "Restart MCP HTTP server"
        if (-not @($restart.data.tools | Where-Object { $_.toolId -eq "http.echo" })) {
            throw "Restart result did not include http.echo tool"
        }
    } | Out-Null

    Test-Step "Verify failed MCP HTTP server is contained" {
        $response = Invoke-Json -Method POST -Path "/api/mcp/servers/broken-http/test" -Headers $headers
        Assert-ApiOk $response "Test broken MCP HTTP server"
        if ($response.data.success -eq $true) {
            throw "broken-http unexpectedly succeeded"
        }
        if ($response.data.status -ne "TOOL_NOT_FOUND") {
            throw "Unexpected broken-http test status: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
    } | Out-Null

    Test-Step "Read MCP HTTP stderr tail endpoint" {
        $response = Invoke-Json -Method GET -Path "/api/mcp/servers/http-echo/stderr-tail" -Headers $headers
        Assert-ApiOk $response "Read MCP HTTP stderr tail"
    } | Out-Null

    Write-Host "`nSummary: $passed / $total passed, $failed failed" -ForegroundColor Cyan
    Write-Host "Smoke backend: $BaseUrl"
    Write-Host "MCP HTTP server: http-echo"
    Write-Host "MCP HTTP tool: http.echo"
} catch {
    Write-Host "`nSummary: $passed / $total passed, $failed failed" -ForegroundColor Cyan
    Write-Error $_.Exception.Message
    exit 1
} finally {
    if (-not $KeepContainers) {
        Remove-SmokeContainer -Name $BackendContainerName
        Remove-SmokeContainer -Name $McpServerContainerName
    }
}

if ($failed -gt 0) {
    exit 1
}
