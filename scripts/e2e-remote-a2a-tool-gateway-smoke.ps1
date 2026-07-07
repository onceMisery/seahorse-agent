param(
    [string]$BaseUrl = "http://127.0.0.1:19097",
    [string]$Username = "admin",
    [string]$Password = "admin123",
    [string]$BackendImage = "seahorse-agent-backend:latest",
    [string]$BackendContainerName = "seahorse-remote-a2a-tool-smoke",
    [string]$DockerNetwork = "seahorse-agent_default",
    [int]$HostPort = 19097,
    [string]$NacosHost = "seahorse-nacos",
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
$RunSuffix = ([guid]::NewGuid().ToString('N')).Substring(0, 8)
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

function Invoke-RemoteA2aTool {
    param(
        [hashtable]$Headers,
        [hashtable]$Body,
        [string]$Name
    )

    $response = Invoke-Json -Method POST -Path "/api/tools/invoke_remote_a2a_agent/invoke" -Headers $Headers -Body $Body
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
    if ("$($approval.data.toolId)" -ne "invoke_remote_a2a_agent") {
        throw "$Name approval toolId was not invoke_remote_a2a_agent: $($approval.data | ConvertTo-Json -Depth 20 -Compress)"
    }
    if ("$($approval.data.status)" -ne "PENDING") {
        throw "$Name approval was not pending: $($approval.data | ConvertTo-Json -Depth 20 -Compress)"
    }

    $approved = Invoke-Json -Method POST -Path "/api/approvals/$approvalId/approve" -Headers $Headers -Body @{
        decisionComment = "Allow remote A2A Tool Gateway smoke test"
    }
    Assert-ApiOk $approved "Approve $Name"
    if ("$($approved.data.status)" -ne "APPROVED") {
        throw "$Name approval was not approved: $($approved.data | ConvertTo-Json -Depth 20 -Compress)"
    }

    $retry = Invoke-Json -Method POST -Path "/api/tools/invoke_remote_a2a_agent/invoke" -Headers $Headers -Body $Body
    Assert-ApiOk $retry "Retry $Name after approval"
    return $retry
}

try {
    Test-Step "Start temporary A2A-enabled backend" {
        Remove-SmokeContainer -Name $BackendContainerName
        $args = @(
            "run", "-d",
            "--name", $BackendContainerName,
            "--network", $DockerNetwork,
            "-p", "${HostPort}:9090",
            "-e", "SERVER_PORT=9090",
            "-e", "SEAHORSE_AGENT_PRODUCT_MODE=enterprise",
            "-e", "SEAHORSE_AGENT_ADVANCED_TOOL_CATALOG_MANAGEMENT_ENABLED=true",
            "-e", "SEAHORSE_AGENT_ADVANCED_AGENT_RUN_MANAGEMENT_ENABLED=true",
            "-e", "SEAHORSE_AGENT_ADVANCED_REMOTE_AGENT_ENABLED=true",
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
            "-e", "SEAHORSE_AGENTSCOPE_A2A_ENABLED=true",
            "-e", "SEAHORSE_AGENTSCOPE_A2A_REGISTER_ENABLED=false",
            "-e", "SEAHORSE_AGENTSCOPE_A2A_NACOS_SERVER=${NacosHost}:8848",
            "-e", "SEAHORSE_AGENTSCOPE_NACOS_SERVER_ADDR=${NacosHost}:8848",
            "-e", "SEAHORSE_AGENTSCOPE_NACOS_NAMESPACE=public",
            "-e", "SEAHORSE_AGENTSCOPE_NACOS_GROUP=DEFAULT_GROUP",
            "-e", "SEAHORSE_AGENTSCOPE_NACOS_M3_ENABLED=true",
            "-e", "SEAHORSE_AGENTSCOPE_NACOS_M3_MODE=M3",
            "-e", "SEAHORSE_AGENTSCOPE_NACOS_M3_NAMESPACE=seahorse-agent",
            "-e", "SEAHORSE_AGENTSCOPE_NACOS_M3_GROUP=DEFAULT_GROUP",
            "-e", "SEAHORSE_AGENTSCOPE_NACOS_M3_CLUSTER_NAME=local",
            "-e", "SEAHORSE_AGENTSCOPE_A2A_TENANT_ID=default",
            "-e", "SEAHORSE_AGENTSCOPE_A2A_AGENT_NAME=seahorse-a2a-tool-smoke",
            "-e", "SEAHORSE_AGENTSCOPE_A2A_URL=http://${BackendContainerName}:9090/a2a",
            "-e", "SEAHORSE_AGENTSCOPE_A2A_HOST=${BackendContainerName}",
            "-e", "SEAHORSE_AGENTSCOPE_A2A_PORT=9090",
            "-e", "SEAHORSE_AGENTSCOPE_A2A_PATH=/a2a",
            "-e", "SEAHORSE_AGENTSCOPE_A2A_AUTH_MODE=shared-secret",
            "-e", "SEAHORSE_AGENTSCOPE_A2A_AUTH_HEADER_NAME=X-Seahorse-A2A-Token",
            "-e", "SEAHORSE_AGENTSCOPE_A2A_SHARED_SECRET=seahorse-local-a2a-token",
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

    Test-Step "Verify remote A2A tool catalog entry" {
        $response = Invoke-Json -Method GET -Path "/api/tools?current=1&size=50&keyword=invoke_remote_a2a_agent" -Headers $headers
        Assert-ApiOk $response "List tools"
        $records = Get-PageRecords $response.data
        $tool = @($records | Where-Object { $_.toolId -eq "invoke_remote_a2a_agent" })[0]
        if (-not $tool) {
            throw "Tool catalog did not include invoke_remote_a2a_agent"
        }
        if ("$($tool.riskLevel)" -ne "HIGH" -or "$($tool.actionType)" -ne "EXECUTE" -or "$($tool.resourceType)" -ne "REMOTE_AGENT") {
            throw "Remote A2A catalog governance mismatch: $($tool | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ($tool.requiresApproval -ne $true) {
            throw "Remote A2A tool does not require approval: $($tool | ConvertTo-Json -Depth 20 -Compress)"
        }
        $tool | ConvertTo-Json -Compress | Write-Host
    } | Out-Null

    $runId = "remote-a2a-tool-smoke-run-$RunSuffix"
    $stepId = "remote-a2a-tool-smoke-step-$RunSuffix"
    $toolCallId = "remote-a2a-tool-smoke-call-$RunSuffix"
    $agentName = "missing-agent-$RunSuffix"
    $prompt = "remote A2A audit prompt $RunSuffix"
    $metadataVersion = "version-secret-$RunSuffix"
    $metadataSource = "source-secret-$RunSuffix"
    $body = @{
        runId = $runId
        stepId = $stepId
        toolCallId = $toolCallId
        agentId = "legacy-react-agent"
        tenantId = "default"
        userId = $Username
        agentIdentityId = $Username
        arguments = @{
            agentName = $agentName
            prompt = $prompt
            metadata = @{
                version = $metadataVersion
                source = $metadataSource
            }
        }
        resourceRefs = @{}
        idempotencyKey = "${runId}:${toolCallId}"
        allowedToolIds = @("invoke_remote_a2a_agent")
    }

    Test-Step "Invoke remote A2A tool through Tool Gateway" {
        $response = Invoke-RemoteA2aTool -Headers $headers -Body $body -Name "Invoke remote A2A tool"
        if ($response.data.success -ne $false) {
            throw "Remote A2A invocation unexpectedly succeeded: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $errorText = "$($response.data.error)"
        if (-not $errorText.Contains("invoke_remote_a2a_agent failed for agentName=$agentName")) {
            throw "Remote A2A failure did not include target agent diagnostic: $errorText"
        }
        foreach ($forbidden in @($prompt, $metadataVersion, $metadataSource)) {
            if ($errorText.Contains($forbidden)) {
                throw "Remote A2A failure leaked raw value '$forbidden': $errorText"
            }
        }
        $response.data | ConvertTo-Json -Compress | Write-Host
    } | Out-Null

    Test-Step "Verify remote A2A Tool Gateway audit summary" {
        $response = Invoke-Json -Method GET -Path "/api/tool-invocations?current=1&size=20&runId=$runId&toolId=invoke_remote_a2a_agent" -Headers $headers
        Assert-ApiOk $response "Read remote A2A tool audit"
        $records = Get-PageRecords $response.data
        $audit = @($records | Where-Object { $_.status -eq "FAILED" -and $_.toolId -eq "invoke_remote_a2a_agent" })[0]
        if (-not $audit) {
            throw "Remote A2A Tool Gateway invocation did not create FAILED tool audit: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $summary = "$($audit.argumentsSummary)"
        $metadataValueTotalLength = $metadataVersion.Length + $metadataSource.Length
        $metadataValueMaxLength = [Math]::Max($metadataVersion.Length, $metadataSource.Length)
        $metadataMapTextLength = 1 + "source".Length + 1 + $metadataSource.Length + 2 + "version".Length + 1 + $metadataVersion.Length + 1
        $argumentValueTotalLength = $agentName.Length + $prompt.Length + $metadataMapTextLength
        $argumentValueMaxLength = [Math]::Max([Math]::Max($agentName.Length, $prompt.Length), $metadataMapTextLength)
        $requiredFragments = @(
            '"toolId":"invoke_remote_a2a_agent"',
            '"agentNamePresent":true',
            """agentNameLength"":$($agentName.Length)",
            """promptLength"":$($prompt.Length)",
            '"metadataKeys":[',
            '"metadataCount":2',
            '"metadataValueCount":2',
            """metadataValueTotalLength"":$metadataValueTotalLength",
            """metadataValueMaxLength"":$metadataValueMaxLength",
            '"versionPresent":true',
            """versionLength"":$($metadataVersion.Length)",
            '"argumentCount":3',
            '"argumentValueCount":3',
            """argumentValueTotalLength"":$argumentValueTotalLength",
            """argumentValueMaxLength"":$argumentValueMaxLength"
        )
        foreach ($required in $requiredFragments) {
            if (-not $summary.Contains($required)) {
                throw "Remote A2A audit summary did not include $required`: $summary"
            }
        }
        foreach ($requiredKey in @('"version"', '"source"')) {
            if (-not $summary.Contains($requiredKey)) {
                throw "Remote A2A audit summary did not include metadata key $requiredKey`: $summary"
            }
        }
        foreach ($forbidden in @($agentName, $prompt, $metadataVersion, $metadataSource)) {
            if (-not [string]::IsNullOrWhiteSpace($forbidden) -and $summary.Contains($forbidden)) {
                throw "Remote A2A audit summary leaked raw value '$forbidden': $summary"
            }
        }
        $audit | ConvertTo-Json -Compress | Write-Host
    } | Out-Null

    Write-Host "`nSummary: $passed / $total passed, $failed failed" -ForegroundColor Cyan
    Write-Host "Smoke backend: $BaseUrl"
    Write-Host "Remote A2A tool: invoke_remote_a2a_agent"
} catch {
    Write-Host "`nSummary: $passed / $total passed, $failed failed" -ForegroundColor Cyan
    Write-Error $_.Exception.Message
    exit 1
} finally {
    if (-not $KeepContainer) {
        Remove-SmokeContainer -Name $BackendContainerName
    }
}

if ($failed -gt 0) {
    exit 1
}
