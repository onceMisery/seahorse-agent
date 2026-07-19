param(
    [string]$BaseUrl = "http://127.0.0.1:9090",
    [string]$Username = "admin",
    [string]$Password = "admin123",
    [string]$Marker = "seahorse-sandbox-oci-runtime-fail-closed",
    [string]$UnavailableOciRuntime = "runsc",
    [string]$ExpectedRuntimeNodeId = "local-container-docker",
    [string]$WorkspaceMountSourceRoot = "",
    [string]$BackendContainer = "seahorse-backend",
    [string]$PostgresContainer = "seahorse-postgres",
    [string]$PostgresUser = "seahorse",
    [string]$PostgresDatabase = "seahorse"
)

$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd("/")
$repoRoot = Split-Path -Parent $PSScriptRoot
$composeFull = Join-Path $repoRoot "docker-compose.full.yml"
$composeSandbox = Join-Path $repoRoot "docker-compose.sandbox.yml"
if ([string]::IsNullOrWhiteSpace($WorkspaceMountSourceRoot)) {
    $WorkspaceMountSourceRoot = Join-Path $repoRoot "output/sandbox-workspaces"
}
$WorkspaceMountSourceRoot = [IO.Path]::GetFullPath($WorkspaceMountSourceRoot).Replace("\", "/")
$passed = 0
$total = 0
$deploymentChanged = $false
$verificationError = $null
$restoreError = $null
$restoreOciRuntime = $null
$sessionId = $null
$headers = $null

$workspaceEnvironmentName = "SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_WORKSPACE_MOUNT_SOURCE_ROOT"
$runtimeEnvironmentName = "SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_OCI_RUNTIME"
$previousWorkspaceEnvironment = [Environment]::GetEnvironmentVariable($workspaceEnvironmentName, "Process")
$previousRuntimeEnvironment = [Environment]::GetEnvironmentVariable($runtimeEnvironmentName, "Process")

function Test-Step {
    param([string]$Name, [scriptblock]$Action)
    $script:total++
    Write-Host "`n[$script:total] $Name" -ForegroundColor Cyan
    $result = & $Action
    $script:passed++
    Write-Host "  PASS" -ForegroundColor Green
    return $result
}

function Invoke-Json {
    param(
        [ValidateSet("GET", "POST")][string]$Method,
        [string]$Path,
        [hashtable]$Headers = @{},
        [object]$Body = $null
    )
    $parameters = @{
        Method = $Method
        Uri = "$BaseUrl$Path"
        Headers = $Headers
        TimeoutSec = 60
    }
    if ($null -ne $Body) {
        $parameters.ContentType = "application/json"
        $parameters.Body = $Body | ConvertTo-Json -Depth 20 -Compress
    }
    Invoke-RestMethod @parameters
}

function Assert-ApiOk {
    param([object]$Response, [string]$Name)
    if ($null -eq $Response -or "$($Response.code)" -ne "0") {
        throw "$Name API error: $($Response | ConvertTo-Json -Depth 20 -Compress)"
    }
}

function Invoke-PostgresScalar {
    param([string]$Sql)
    $rows = @(& docker exec $PostgresContainer psql -U $PostgresUser -d $PostgresDatabase -At -F "`t" -c $Sql)
    if ($LASTEXITCODE -ne 0) {
        throw "PostgreSQL query failed: $Sql"
    }
    $values = @($rows | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($values.Count -eq 0) {
        throw "PostgreSQL query returned no rows: $Sql"
    }
    return "$($values[0])"
}

function Wait-ForBackendHealth {
    param([int]$TimeoutSeconds = 180)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            $health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -TimeoutSec 5
            if ("$($health.status)" -eq "UP") {
                return
            }
        } catch {
            # Backend recreation briefly closes the HTTP listener.
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "Backend health did not become UP within $TimeoutSeconds seconds"
}

function Set-BackendOciRuntime {
    param([string]$OciRuntime)
    [Environment]::SetEnvironmentVariable($workspaceEnvironmentName, $WorkspaceMountSourceRoot, "Process")
    [Environment]::SetEnvironmentVariable($runtimeEnvironmentName, $OciRuntime, "Process")
    $arguments = @(
        "compose",
        "-f", $composeFull,
        "-f", $composeSandbox,
        "up", "-d", "--no-deps", "--force-recreate", "backend"
    )
    & docker @arguments | Out-Host
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to recreate backend with OCI runtime '$OciRuntime'"
    }
    Wait-ForBackendHealth
}

function Get-ContainerOciRuntime {
    $environment = @(& docker inspect $BackendContainer --format '{{range .Config.Env}}{{println .}}{{end}}')
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to inspect backend container environment"
    }
    $prefix = "$runtimeEnvironmentName="
    $entry = $environment | Where-Object { "$($_)".StartsWith($prefix, [StringComparison]::Ordinal) } | Select-Object -First 1
    if ($null -eq $entry) {
        throw "Backend container does not expose $runtimeEnvironmentName"
    }
    return "$entry".Substring($prefix.Length).Trim()
}

function Get-ContainerEnvironmentMap {
    $environment = @(& docker inspect $BackendContainer --format '{{range .Config.Env}}{{println .}}{{end}}')
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to inspect backend container environment"
    }
    $values = @{}
    foreach ($entry in $environment) {
        $separator = "$entry".IndexOf("=", [StringComparison]::Ordinal)
        if ($separator -le 0) {
            continue
        }
        $values["$entry".Substring(0, $separator)] = "$entry".Substring($separator + 1)
    }
    return $values
}

function Assert-ComposePreservesBackendEnvironment {
    [Environment]::SetEnvironmentVariable($workspaceEnvironmentName, $WorkspaceMountSourceRoot, "Process")
    [Environment]::SetEnvironmentVariable($runtimeEnvironmentName, $restoreOciRuntime, "Process")
    $arguments = @(
        "compose",
        "-f", $composeFull,
        "-f", $composeSandbox,
        "config", "--format", "json"
    )
    $rawConfig = @(& docker @arguments)
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to resolve the current compose environment"
    }
    $config = ($rawConfig -join "`n") | ConvertFrom-Json
    $expectedEnvironment = $config.services.backend.environment
    if ($null -eq $expectedEnvironment) {
        throw "Resolved compose config does not include backend environment"
    }
    $currentEnvironment = Get-ContainerEnvironmentMap
    $mismatchedKeys = @()
    foreach ($property in $expectedEnvironment.PSObject.Properties) {
        $expectedValue = if ($null -eq $property.Value) { "" } else { "$($property.Value)" }
        if (-not $currentEnvironment.ContainsKey($property.Name) -or
            "$($currentEnvironment[$property.Name])" -cne $expectedValue) {
            $mismatchedKeys += $property.Name
        }
    }
    if ($mismatchedKeys.Count -gt 0) {
        throw "Compose recreation would change backend environment keys: $($mismatchedKeys -join ',')"
    }
}

function New-AuthenticatedAgentRun {
    $login = Invoke-Json -Method POST -Path "/auth/login" -Body @{
        username = $Username
        password = $Password
    }
    Assert-ApiOk $login "Login"
    if (-not "$($login.data.token)") {
        throw "Login response did not include a token"
    }
    $tokenHeaders = @{ Authorization = "Bearer $($login.data.token)" }
    $agentRow = Invoke-PostgresScalar @"
SELECT d.agent_id, COALESCE(d.latest_version_id, v.version_id)
FROM sa_agent_definition d
LEFT JOIN sa_agent_version v ON v.agent_id = d.agent_id
WHERE d.tenant_id = 'default'
  AND COALESCE(d.latest_version_id, v.version_id) IS NOT NULL
ORDER BY d.updated_at DESC
LIMIT 1;
"@
    $agentParts = $agentRow -split "`t"
    if ($agentParts.Count -ne 2) {
        throw "Unable to select a versioned agent for the smoke"
    }
    $conversation = Invoke-Json -Method POST -Path "/api/conversations" -Headers $tokenHeaders
    Assert-ApiOk $conversation "Create conversation"
    $run = Invoke-Json -Method POST -Path "/api/agents/$($agentParts[0])/runs" -Headers $tokenHeaders -Body @{
        versionId = $agentParts[1]
        tenantId = "default"
        conversationId = "$($conversation.data)"
        triggerType = "API"
        inputSummary = $Marker
        traceId = "trace-$Marker"
    }
    Assert-ApiOk $run "Create agent run"
    if (-not "$($run.data.runId)") {
        throw "Create agent run response did not include runId"
    }
    return [pscustomobject]@{
        Headers = $tokenHeaders
        RunId = "$($run.data.runId)"
    }
}

try {
    Test-Step "Require an idle sandbox environment" {
        Wait-ForBackendHealth
        $state = Invoke-PostgresScalar @"
SELECT (SELECT COUNT(*) FROM sa_sandbox_session
        WHERE status NOT IN ('SUCCEEDED','FAILED','TIMED_OUT','CANCELLED')),
       (SELECT COUNT(*) FROM sa_sandbox_runtime_capacity_reservation),
       (SELECT COUNT(*) FROM sa_sandbox_runtime_node_create_operation);
"@
        if ($state -ne "0`t0`t0") {
            throw "Sandbox environment is not idle: non-terminal/reservations/create-operations=$state"
        }
        $managed = @(& docker ps -a --format '{{.Names}}' | Where-Object { $_ -like "seahorse-sandbox-*" })
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to inspect managed sandbox containers"
        }
        if ($managed.Count -ne 0) {
            throw "Managed sandbox containers are active: $($managed -join ',')"
        }
    } | Out-Null

    Test-Step "Require a real unavailable OCI runtime" {
        if ($UnavailableOciRuntime -notmatch '^[A-Za-z0-9][A-Za-z0-9_.-]{0,63}$') {
            throw "Unavailable OCI runtime name is invalid"
        }
        $script:restoreOciRuntime = Get-ContainerOciRuntime
        if ([string]::IsNullOrWhiteSpace($restoreOciRuntime)) {
            throw "Backend must start with an explicit restorable OCI runtime"
        }
        if ($UnavailableOciRuntime -eq $restoreOciRuntime) {
            throw "Unavailable and restore OCI runtimes must differ"
        }
        $registeredRuntimes = @(& docker info --format '{{range $name, $_ := .Runtimes}}{{println $name}}{{end}}')
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to inspect Docker OCI runtimes"
        }
        if ($registeredRuntimes | Where-Object { "$($_)".Trim() -eq $UnavailableOciRuntime }) {
            throw "OCI runtime '$UnavailableOciRuntime' is registered; this smoke requires a missing runtime"
        }
        if (-not ($registeredRuntimes | Where-Object { "$($_)".Trim() -eq $restoreOciRuntime })) {
            throw "Restore OCI runtime '$restoreOciRuntime' is not registered"
        }
        Assert-ComposePreservesBackendEnvironment
    } | Out-Null

    Test-Step "Recreate backend with unavailable OCI runtime" {
        $script:deploymentChanged = $true
        Set-BackendOciRuntime $UnavailableOciRuntime
        if ((Get-ContainerOciRuntime) -ne $UnavailableOciRuntime) {
            throw "Backend did not receive OCI runtime '$UnavailableOciRuntime'"
        }
    } | Out-Null

    $context = Test-Step "Login and create a real agent run" {
        New-AuthenticatedAgentRun
    }
    $headers = $context.Headers

    Test-Step "Verify runtime capability health fails closed" {
        $response = Invoke-Json -Method GET -Path "/api/sandbox/runtime/health" -Headers $headers
        Assert-ApiOk $response "Inspect sandbox runtime health"
        $health = $response.data
        if ("$($health.nodeId)" -ne $ExpectedRuntimeNodeId -or
            "$($health.ociRuntime)" -ne $UnavailableOciRuntime -or
            $health.ociRuntimeAvailable -ne $false -or
            "$($health.status)" -ne "UNAVAILABLE" -or
            $health.engineAvailable -ne $true) {
            throw "Unexpected OCI runtime health: $($health | ConvertTo-Json -Depth 20 -Compress)"
        }
        $messages = @($health.failureMessages)
        if ($messages.Count -ne 1 -or "$($messages[0])" -ne "configured OCI runtime is not available") {
            throw "OCI runtime failure reason is not value-bounded: $($messages -join ',')"
        }
        if (("$messages").Contains($UnavailableOciRuntime) -or ("$messages").Contains($restoreOciRuntime)) {
            throw "OCI runtime failure reason leaked a runtime name"
        }
    } | Out-Null

    Test-Step "Verify local node admission is unavailable" {
        $response = Invoke-Json -Method GET -Path "/api/sandbox/runtime/nodes" -Headers $headers
        Assert-ApiOk $response "Inspect sandbox runtime nodes"
        $node = @($response.data) | Where-Object { "$($_.nodeId)" -eq $ExpectedRuntimeNodeId } | Select-Object -First 1
        if ($null -eq $node -or
            "$($node.status)" -ne "UNAVAILABLE" -or
            "$($node.admissionStatus)" -ne "UNAVAILABLE" -or
            $node.admissionAvailable -ne $false -or
            "$($node.ociRuntime)" -ne $UnavailableOciRuntime -or
            $node.ociRuntimeAvailable -ne $false) {
            throw "Unexpected unavailable runtime node: $($node | ConvertTo-Json -Depth 20 -Compress)"
        }
    } | Out-Null

    $rejected = Test-Step "Reject a real session before runtime create" {
        $response = Invoke-Json -Method POST -Path "/api/sandbox/sessions" -Headers $headers -Body @{
            tenantId = "default"
            runId = $context.RunId
            runtimeType = "CODE_INTERPRETER"
            networkRequested = $false
            requestedHosts = @()
            requiredRuntimeNodeId = $ExpectedRuntimeNodeId
        }
        Assert-ApiOk $response "Create session with unavailable OCI runtime"
        if ("$($response.data.status)" -ne "FAILED" -or
            "$($response.data.reasonCode)" -ne "RUNTIME_NODE_UNAVAILABLE" -or
            "$($response.data.runtimeNodeId)") {
            throw "Unavailable OCI runtime did not reject before node assignment: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        return $response.data
    }
    $sessionId = "$($rejected.sessionId)"

    Test-Step "Verify rejection persistence and no runtime residue" {
        if ($sessionId -notmatch '^[A-Za-z0-9._-]{1,128}$') {
            throw "Rejected session id is missing or unsafe"
        }
        $safeSessionId = $sessionId.Replace("'", "''")
        $row = Invoke-PostgresScalar @"
SELECT status, reason_code, COALESCE(runtime_node_id, '<null>')
FROM sa_sandbox_session
WHERE session_id = '$safeSessionId';
"@
        if ($row -ne "FAILED`tRUNTIME_NODE_UNAVAILABLE`t<null>") {
            throw "Unexpected rejected session row: $row"
        }
        & docker exec -e "SESSION_ID=$sessionId" $BackendContainer sh -lc 'test ! -e "/var/lib/seahorse-sandbox/$SESSION_ID"'
        if ($LASTEXITCODE -ne 0) {
            throw "Unavailable runtime rejection created a workspace"
        }
        $managed = @(& docker ps -a --format '{{.Names}}' | Where-Object { $_ -like "seahorse-sandbox-*" })
        if ($LASTEXITCODE -ne 0 -or $managed.Count -ne 0) {
            throw "Unavailable runtime rejection created managed containers: $($managed -join ',')"
        }
        $residue = Invoke-PostgresScalar @"
SELECT (SELECT COUNT(*) FROM sa_sandbox_runtime_capacity_reservation),
       (SELECT COUNT(*) FROM sa_sandbox_runtime_node_create_operation),
       (SELECT COUNT(*) FROM sa_sandbox_session
        WHERE status NOT IN ('SUCCEEDED','FAILED','TIMED_OUT','CANCELLED'));
"@
        if ($residue -ne "0`t0`t0") {
            throw "Unavailable runtime rejection left runtime residue: $residue"
        }
    } | Out-Null
} catch {
    $verificationError = $_
} finally {
    try {
        if ($deploymentChanged -and -not [string]::IsNullOrWhiteSpace($restoreOciRuntime)) {
            Write-Host "`nRestoring backend OCI runtime '$restoreOciRuntime'" -ForegroundColor Cyan
            Set-BackendOciRuntime $restoreOciRuntime
            if ((Get-ContainerOciRuntime) -ne $restoreOciRuntime) {
                throw "Backend OCI runtime was not restored"
            }
            $login = Invoke-Json -Method POST -Path "/auth/login" -Body @{
                username = $Username
                password = $Password
            }
            Assert-ApiOk $login "Login after backend restore"
            $restoreHeaders = @{ Authorization = "Bearer $($login.data.token)" }
            $runtimeHealth = Invoke-Json -Method GET -Path "/api/sandbox/runtime/health" -Headers $restoreHeaders
            Assert-ApiOk $runtimeHealth "Inspect restored sandbox runtime health"
            if ("$($runtimeHealth.data.ociRuntime)" -ne $restoreOciRuntime -or
                $runtimeHealth.data.ociRuntimeAvailable -ne $true -or
                "$($runtimeHealth.data.status)" -ne "HEALTHY") {
                throw "Restored OCI runtime is not healthy: $($runtimeHealth.data | ConvertTo-Json -Depth 20 -Compress)"
            }
        }
    } catch {
        $restoreError = $_
    } finally {
        [Environment]::SetEnvironmentVariable($workspaceEnvironmentName, $previousWorkspaceEnvironment, "Process")
        [Environment]::SetEnvironmentVariable($runtimeEnvironmentName, $previousRuntimeEnvironment, "Process")
    }
}

if ($restoreError) {
    Write-Error "Backend restore failed: $($restoreError.Exception.Message)"
    exit 1
}
if ($verificationError) {
    Write-Error $verificationError.Exception.Message
    exit 1
}

Write-Host "`nSandbox OCI runtime fail-closed E2E: $passed/$total passed" -ForegroundColor Green
Write-Host "Unavailable runtime: $UnavailableOciRuntime"
Write-Host "Restored runtime: $restoreOciRuntime"
Write-Host "Rejected session: $sessionId"
