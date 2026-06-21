param(
    [string]$MainUrl = "http://127.0.0.1:9090/a2a",
    [int]$RemotePort = 9092,
    [string]$NacosServer = "127.0.0.1:8848",
    [string]$NacosNamespace = "public",
    [string]$TenantId = "tenant-a",
    [string]$SharedSecret = "seahorse-local-a2a-token",
    [ValidateSet("shared-secret", "tenant-signed")]
    [string]$AuthMode = "shared-secret",
    [string]$AuthHeaderName = "X-Seahorse-A2A-Token",
    [string]$MainAgentName = "seahorse-a",
    [string]$BackendImage = "seahorse-agent-backend:latest",
    [string]$NacosContainerName = "seahorse-nacos",
    [string]$PostgresHost = "seahorse-postgres",
    [string]$PostgresDatabase = "seahorse",
    [string]$PostgresUsername = "seahorse",
    [string]$PostgresPassword = "seahorse",
    [int]$StartupTimeoutSeconds = 300
)

$ErrorActionPreference = "Stop"
# Structured output contract:
# E2E_AGENT=
# MAIN_CARD_OK
# MAIN_POST_NO_AUTH=
# MAIN_POST_WRONG_TOKEN=
# MAIN_POST_AUTH=
# REMOTE_DIRECT_OK
# NACOS_CONNECTOR_SMOKE_OK
# E2E_RESULT=PASS
$remoteAgentName = "seahorse-e2e-$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())"
$remoteContainerName = "seahorse-a2a-e2e-$remoteAgentName"
$remoteUrl = "http://localhost:$RemotePort/a2a"
$jsonRpcBody = @{
    jsonrpc = "2.0"
    id = [guid]::NewGuid().ToString()
    method = "message/send"
    params = @{
        message = @{
            role = "user"
            parts = @(@{ kind = "text"; text = "ping from agentscope e2e" })
            messageId = [guid]::NewGuid().ToString()
            metadata = @{ smoke = "true" }
            kind = "message"
        }
        metadata = @{ smoke = "true" }
    }
} | ConvertTo-Json -Depth 20 -Compress

function Write-Step {
    param([string]$Name, [string]$Value = "")
    if ([string]::IsNullOrWhiteSpace($Value)) {
        Write-Host $Name
    } else {
        Write-Host "$Name=$Value"
    }
}

function Invoke-RawHttp {
    param(
        [string]$Method,
        [string]$Url,
        [string]$Body = "",
        [hashtable]$Headers = @{}
    )

    $bodyFile = $null
    $args = @("-s", "-w", "`n%{http_code}", "-X", $Method, $Url)
    foreach ($key in $Headers.Keys) {
        $args += @("-H", "${key}: $($Headers[$key])")
    }
    if (-not [string]::IsNullOrEmpty($Body)) {
        $bodyFile = New-TemporaryFile
        [System.IO.File]::WriteAllText(
            $bodyFile.FullName,
            $Body,
            [System.Text.UTF8Encoding]::new($false))
        $args += @("-H", "Content-Type: application/json", "--data-binary", "@$($bodyFile.FullName)")
    }

    try {
        $raw = & curl.exe @args
        $exitCode = $LASTEXITCODE
    } finally {
        if ($null -ne $bodyFile) {
            Remove-Item -LiteralPath $bodyFile.FullName -ErrorAction SilentlyContinue
        }
    }
    if ($exitCode -ne 0) {
        throw "curl failed with exit $exitCode for $Method $Url"
    }

    $lines = @($raw)
    if ($lines.Count -eq 0) {
        throw "curl returned empty output for $Method $Url"
    }
    $status = [int]$lines[-1]
    $content = if ($lines.Count -gt 1) { ($lines[0..($lines.Count - 2)] -join "`n") } else { "" }
    return [PSCustomObject]@{ Status = $status; Body = $content }
}

function Assert-Status {
    param([string]$Name, [object]$Response, [int]$ExpectedStatus)
    Write-Step $Name $Response.Status
    if ($Response.Status -ne $ExpectedStatus) {
        throw "$Name expected HTTP $ExpectedStatus but got $($Response.Status). Body=$($Response.Body)"
    }
}

function Assert-AgentCard {
    param([string]$Name, [string]$Url)

    $response = Invoke-RawHttp -Method GET -Url $Url
    Assert-Status $Name $response 200
    $card = $response.Body | ConvertFrom-Json
    $text = $response.Body
    if ([string]::IsNullOrWhiteSpace([string]$card.name)) {
        throw "$Name returned an Agent Card without name"
    }
    if ($text -notmatch [regex]::Escape("seahorse:tenant:$TenantId")) {
        throw "$Name Agent Card missing tenant tag for $TenantId"
    }
    foreach ($tag in @("seahorse:m3:mode=M3", "seahorse:m3:namespace=seahorse-agent", "seahorse:m3:group=DEFAULT_GROUP", "seahorse:m3:clusterName=local")) {
        if ($text -notmatch [regex]::Escape($tag)) {
            throw "$Name Agent Card missing M3 tag $tag"
        }
    }
    return $card
}

function Assert-A2aAuthFlow {
    param([string]$Prefix, [string]$Url, [string]$AgentName)

    $noAuth = Invoke-RawHttp -Method POST -Url $Url -Body $jsonRpcBody
    Assert-Status "${Prefix}_POST_NO_AUTH" $noAuth 401

    $wrong = Invoke-RawHttp -Method POST -Url $Url -Body $jsonRpcBody `
        -Headers (New-A2aAuthHeaders -AgentName $AgentName -Secret "wrong-token")
    Assert-Status "${Prefix}_POST_WRONG_TOKEN" $wrong 401

    $ok = Invoke-RawHttp -Method POST -Url $Url -Body $jsonRpcBody `
        -Headers (New-A2aAuthHeaders -AgentName $AgentName -Secret $SharedSecret)
    Assert-Status "${Prefix}_POST_AUTH" $ok 200
    if ($ok.Body -notmatch "mock-streaming-chat") {
        throw "${Prefix}_POST_AUTH response did not include expected mock output. Body=$($ok.Body)"
    }
    return $ok
}

function ConvertTo-Hex {
    param([byte[]]$Bytes)
    return (($Bytes | ForEach-Object { $_.ToString("x2") }) -join "")
}

function Get-Sha256Hex {
    param([string]$Text)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        return ConvertTo-Hex -Bytes ($sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($Text)))
    } finally {
        $sha.Dispose()
    }
}

function Get-HmacSha256Hex {
    param([string]$Secret, [string]$Text)
    $hmac = [System.Security.Cryptography.HMACSHA256]::new([System.Text.Encoding]::UTF8.GetBytes($Secret))
    try {
        return ConvertTo-Hex -Bytes ($hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($Text)))
    } finally {
        $hmac.Dispose()
    }
}

function New-A2aAuthHeaders {
    param([string]$AgentName, [string]$Secret)
    if ($AuthMode -eq "shared-secret") {
        return @{ $AuthHeaderName = $Secret }
    }

    $timestamp = [DateTimeOffset]::UtcNow.ToString("o")
    $nonce = [guid]::NewGuid().ToString()
    $bodySha256 = Get-Sha256Hex -Text $jsonRpcBody
    $payload = "$TenantId`n$AgentName`n$timestamp`n$nonce`n$bodySha256"
    return @{
        "X-Seahorse-A2A-Tenant" = $TenantId
        "X-Seahorse-A2A-Agent" = $AgentName
        "X-Seahorse-A2A-Timestamp" = $timestamp
        "X-Seahorse-A2A-Nonce" = $nonce
        "X-Seahorse-A2A-Body-SHA256" = $bodySha256
        "X-Seahorse-A2A-Signature" = Get-HmacSha256Hex -Secret $Secret -Text $payload
    }
}

function Get-DockerNetwork {
    $raw = & docker.exe inspect $NacosContainerName 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "docker inspect failed for ${NacosContainerName}: $raw"
    }
    $container = @(($raw -join "`n") | ConvertFrom-Json)[0]
    $networks = $container.NetworkSettings.Networks.PSObject.Properties.Name
    if ($null -eq $networks -or @($networks).Count -eq 0) {
        throw "Cannot determine Docker network from $NacosContainerName"
    }
    return @($networks)[0]
}

function Start-RemoteBackend {
    param([string]$NetworkName)

    Remove-RemoteBackend
    $args = @(
        "run", "-d",
        "--name", $remoteContainerName,
        "--network", $NetworkName,
        "-p", "${RemotePort}:9090",
        "-e", "SERVER_PORT=9090",
        "-e", "SPRING_DATASOURCE_URL=jdbc:postgresql://${PostgresHost}:5432/${PostgresDatabase}",
        "-e", "SPRING_DATASOURCE_USERNAME=$PostgresUsername",
        "-e", "SPRING_DATASOURCE_PASSWORD=$PostgresPassword",
        "-e", "SEAHORSE_AGENT_EXECUTOR_ENGINE=kernel",
        "-e", "SEAHORSE_AGENT_ADAPTERS_AI_TYPE=mock",
        "-e", "SEAHORSE_AGENT_ADAPTERS_VECTOR_TYPE=noop",
        "-e", "SEAHORSE_AGENT_ADAPTERS_CACHE_TYPE=local",
        "-e", "SEAHORSE_AGENT_ADAPTERS_STORAGE_TYPE=local",
        "-e", "SEAHORSE_AGENT_ADAPTERS_MQ_TYPE=direct",
        "-e", "SEAHORSE_AGENT_ADAPTERS_OBSERVATION_TYPE=noop",
        "-e", "SEAHORSE_AGENT_ADAPTERS_REPOSITORY_TYPE=jdbc",
        "-e", "SEAHORSE_AGENTSCOPE_A2A_ENABLED=true",
        "-e", "SEAHORSE_AGENTSCOPE_A2A_REGISTER_ENABLED=true",
        "-e", "SEAHORSE_AGENTSCOPE_A2A_NACOS_SERVER=${NacosContainerName}:8848",
        "-e", "SEAHORSE_AGENTSCOPE_NACOS_SERVER_ADDR=${NacosContainerName}:8848",
        "-e", "SEAHORSE_AGENTSCOPE_NACOS_NAMESPACE=$NacosNamespace",
        "-e", "SEAHORSE_AGENTSCOPE_NACOS_GROUP=DEFAULT_GROUP",
        "-e", "SEAHORSE_AGENTSCOPE_NACOS_M3_ENABLED=true",
        "-e", "SEAHORSE_AGENTSCOPE_NACOS_M3_MODE=M3",
        "-e", "SEAHORSE_AGENTSCOPE_NACOS_M3_NAMESPACE=seahorse-agent",
        "-e", "SEAHORSE_AGENTSCOPE_NACOS_M3_GROUP=DEFAULT_GROUP",
        "-e", "SEAHORSE_AGENTSCOPE_NACOS_M3_CLUSTER_NAME=local",
        "-e", "SEAHORSE_AGENTSCOPE_A2A_TENANT_ID=$TenantId",
        "-e", "SEAHORSE_AGENTSCOPE_A2A_AGENT_NAME=$remoteAgentName",
        "-e", "SEAHORSE_AGENTSCOPE_A2A_URL=$remoteUrl",
        "-e", "SEAHORSE_AGENTSCOPE_A2A_HOST=localhost",
        "-e", "SEAHORSE_AGENTSCOPE_A2A_PORT=$RemotePort",
        "-e", "SEAHORSE_AGENTSCOPE_A2A_PATH=/a2a",
        "-e", "SEAHORSE_AGENTSCOPE_A2A_AUTH_MODE=$AuthMode",
        "-e", "SEAHORSE_AGENTSCOPE_A2A_AUTH_HEADER_NAME=$AuthHeaderName",
        "-e", "SEAHORSE_AGENTSCOPE_A2A_SHARED_SECRET=$SharedSecret",
        $BackendImage
    )
    $output = & docker.exe @args
    if ($LASTEXITCODE -ne 0) {
        throw "docker run failed for remote backend: $output"
    }
}

function Remove-RemoteBackend {
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & docker.exe inspect $remoteContainerName *> $null
        if ($LASTEXITCODE -eq 0) {
            & docker.exe rm -f $remoteContainerName *> $null
        }
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
}

function Wait-ForA2a {
    param([string]$Url, [int]$TimeoutSeconds)

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $lastError = ""
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-RawHttp -Method GET -Url $Url
            if ($response.Status -eq 200) {
                return
            }
            $lastError = "HTTP $($response.Status)"
        } catch {
            $lastError = $_.Exception.Message
        }
        Start-Sleep -Seconds 3
    }
    throw "Timed out waiting for $Url. Last error: $lastError"
}

function Invoke-LiveSmoke {
    $env:SEAHORSE_LIVE_A2A_SMOKE = "true"
    $env:SEAHORSE_LIVE_NACOS_SERVER = $NacosServer
    $env:SEAHORSE_LIVE_NACOS_NAMESPACE = $NacosNamespace
    $env:SEAHORSE_LIVE_A2A_TENANT_ID = $TenantId
    $env:SEAHORSE_LIVE_A2A_AGENT_NAME = $remoteAgentName
    $env:SEAHORSE_LIVE_A2A_EXPECTED_URL = $remoteUrl
    $env:SEAHORSE_LIVE_A2A_AUTH_MODE = $AuthMode
    $env:SEAHORSE_LIVE_A2A_AUTH_HEADER = $AuthHeaderName
    $env:SEAHORSE_LIVE_A2A_SHARED_SECRET = $SharedSecret
    $env:SEAHORSE_LIVE_A2A_EXPECTED_CONTENT = "mock-streaming-chat"

    & mvn -pl seahorse-agent-adapter-agent-agentscope -am `
        "-Dtest=AgentScopeA2ALiveSmokeTest" `
        "-Dsurefire.failIfNoSpecifiedTests=false" `
        "-DforkCount=0" `
        test
    if ($LASTEXITCODE -ne 0) {
        throw "AgentScopeA2ALiveSmokeTest failed"
    }
}

Write-Step "E2E_AGENT" $remoteAgentName

try {
    [void](Assert-AgentCard -Name "MAIN_CARD_OK" -Url $MainUrl)
    [void](Assert-A2aAuthFlow -Prefix "MAIN" -Url $MainUrl -AgentName $MainAgentName)

    $network = Get-DockerNetwork
    Start-RemoteBackend -NetworkName $network
    Wait-ForA2a -Url $remoteUrl -TimeoutSeconds $StartupTimeoutSeconds

    [void](Assert-AgentCard -Name "REMOTE_CARD_OK" -Url $remoteUrl)
    [void](Assert-A2aAuthFlow -Prefix "REMOTE" -Url $remoteUrl -AgentName $remoteAgentName)
    Write-Step "REMOTE_DIRECT_OK"

    Invoke-LiveSmoke
    Write-Step "NACOS_CONNECTOR_SMOKE_OK"
    Write-Step "E2E_RESULT" "PASS"
} catch {
    Write-Step "E2E_RESULT" "FAIL"
    Write-Error $_.Exception.Message
    exit 1
} finally {
    Remove-RemoteBackend
}
