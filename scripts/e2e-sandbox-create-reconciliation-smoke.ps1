param(
    [string]$BaseUrl = "http://127.0.0.1:9090",
    [string]$WorkerBaseUrl = "http://127.0.0.1:19092",
    [string]$Username = "admin",
    [string]$Password = "admin123",
    [string]$RemoteNodeId = "sandbox-node-b",
    [string]$CoordinatorContainer = "seahorse-backend",
    [string]$WorkerContainer = "seahorse-runtime-node-b",
    [string]$PostgresContainer = "seahorse-postgres",
    [string]$WorkerNetworkAlias = "sandbox-runtime-node-b",
    [string]$ProxyNetworkAlias = "sandbox-create-loss-proxy",
    [string]$Marker = "seahorse-create-reconciliation-e2e"
)

$ErrorActionPreference = "Stop"
$proxyContainer = "seahorse-create-loss-proxy-$([guid]::NewGuid().ToString('N').Substring(0, 8))"
$networkName = $null
$createJob = $null
$releaseFile = "/tmp/release-close-response"
$runId = $null
$sessionId = $null
$originalActiveSessionLimit = $null
$originalTransportUri = $null
$passed = 0
$total = 0

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
        [string]$Method,
        [string]$Path,
        [object]$Body = $null,
        [hashtable]$Headers = @{},
        [string]$Origin = $BaseUrl
    )
    $temp = $null
    $args = @("-sS", "-w", "`n%{http_code}", "-X", $Method, "$Origin$Path")
    if ($null -ne $Body) {
        $json = if ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Depth 20 -Compress }
        $temp = New-TemporaryFile
        [IO.File]::WriteAllText($temp.FullName, $json, (New-Object Text.UTF8Encoding($false)))
        $args += @("-H", "Content-Type: application/json", "--data-binary", "@$($temp.FullName)")
    }
    foreach ($key in $Headers.Keys) { $args += @("-H", "${key}: $($Headers[$key])") }
    try {
        $raw = @(& curl.exe @args)
        if ($LASTEXITCODE -ne 0) { throw "curl failed for $Method $Path" }
    } finally {
        if ($temp) { Remove-Item -LiteralPath $temp.FullName -Force -ErrorAction SilentlyContinue }
    }
    $status = [int]$raw[-1]
    $content = if ($raw.Count -gt 1) { $raw[0..($raw.Count - 2)] -join "`n" } else { "" }
    if ($status -ne 200) { throw "Expected HTTP 200 but got $status for $Method $Path body=$content" }
    if ([string]::IsNullOrWhiteSpace($content)) { return $null }
    return $content | ConvertFrom-Json
}

function Assert-ApiOk {
    param([object]$Response, [string]$Name)
    if ($null -eq $Response -or "$($Response.code)" -ne "0") {
        throw "$Name API error: $($Response | ConvertTo-Json -Depth 20 -Compress)"
    }
}

function Invoke-PostgresScalar {
    param([string]$Sql)
    $rows = @(& docker exec $PostgresContainer psql -U seahorse -d seahorse -At -F "`t" -c $Sql)
    if ($LASTEXITCODE -ne 0) { throw "psql failed for SQL: $Sql" }
    $values = @($rows | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($values.Count -eq 0) { throw "SQL returned no rows: $Sql" }
    return "$($values[0])"
}

function Invoke-PostgresCommand {
    param([string]$Sql)
    & docker exec $PostgresContainer psql -U seahorse -d seahorse -v ON_ERROR_STOP=1 -c $Sql | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "psql failed for SQL: $Sql" }
}

function Start-SessionCreateJob {
    param([string]$Authorization, [string]$TargetRunId)
    $body = @{
        tenantId = "default"
        runId = $TargetRunId
        runtimeType = "CODE_INTERPRETER"
        networkRequested = $false
        requestedHosts = @()
        requiredRuntimeNodeId = $RemoteNodeId
    } | ConvertTo-Json -Depth 10 -Compress
    return Start-Job -ScriptBlock {
        param($Url, $Token, $Json)
        $temp = [IO.Path]::GetTempFileName()
        try {
            [IO.File]::WriteAllText($temp, $Json, (New-Object Text.UTF8Encoding($false)))
            $raw = @(& curl.exe -sS -w "`n%{http_code}" -X POST $Url `
                    -H "Authorization: $Token" -H "Content-Type: application/json" `
                    --data-binary "@$temp")
            [pscustomobject]@{ ExitCode = $LASTEXITCODE; Raw = $raw -join "`n" }
        } finally {
            Remove-Item -LiteralPath $temp -Force -ErrorAction SilentlyContinue
        }
    } -ArgumentList "$BaseUrl/api/sandbox/sessions", $Authorization, $body
}

function Receive-SessionCreateJob {
    param([object]$Job)
    Wait-Job -Job $Job -Timeout 90 | Out-Null
    if ($Job.State -ne "Completed") { throw "session create request did not complete" }
    $result = $Job | Receive-Job
    if ([int]$result.ExitCode -ne 0) { throw "session create curl failed with $($result.ExitCode)" }
    $lines = @("$($result.Raw)" -split "`n")
    $status = [int]$lines[-1]
    $content = $lines[0..($lines.Count - 2)] -join "`n"
    if ($status -ne 200) { throw "session create returned HTTP $status body=$content" }
    return $content | ConvertFrom-Json
}

function Wait-ProxyEvent {
    param([string]$Event, [int]$TimeoutSeconds = 30)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $logs = @(& docker logs $proxyContainer 2>&1)
        if ($LASTEXITCODE -eq 0 -and ($logs -match ('"event":"' + [regex]::Escape($Event) + '"'))) {
            return $logs
        }
        Start-Sleep -Milliseconds 200
    } while ((Get-Date) -lt $deadline)
    throw "proxy event $Event was not observed"
}

function Get-ProxyEvents {
    $events = @()
    foreach ($line in @(& docker logs $proxyContainer 2>&1)) {
        if ("$line" -notmatch '^\{') { continue }
        try { $events += "$line" | ConvertFrom-Json } catch { }
    }
    return $events
}

function Wait-CoordinatorProxyHealth {
    param([int]$TimeoutSeconds = 90)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $exitCode = 1
        try {
            & docker exec $CoordinatorContainer sh -lc `
                "curl -fsS --max-time 5 http://$ProxyNetworkAlias`:9090/actuator/health >/dev/null 2>&1" 2>$null
            $exitCode = $LASTEXITCODE
        } catch {
            $exitCode = 1
        }
        if ($exitCode -eq 0) { return }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "coordinator cannot reach a healthy worker through response-loss proxy"
}

function Remove-ResponseLossProxy {
    $existing = @(& docker ps -aq --filter "name=^/$proxyContainer$")
    if ($LASTEXITCODE -ne 0) { throw "failed to inspect response-loss proxy" }
    if ($existing.Count -eq 0) { return }
    & docker rm -f $proxyContainer | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "failed to remove response-loss proxy" }
}

try {
    Test-Step "Require coordinator and worker healthy" {
        if ((Invoke-Json GET "/actuator/health").status -ne "UP") { throw "coordinator is not healthy" }
        if ((Invoke-Json GET "/actuator/health" -Origin $WorkerBaseUrl).status -ne "UP") {
            throw "worker is not healthy"
        }
    } | Out-Null

    $login = Test-Step "Login and create a real agent run" {
        $response = Invoke-Json POST "/auth/login" -Body @{ username = $Username; password = $Password }
        Assert-ApiOk $response "Login"
        $tokenHeaders = @{ Authorization = "Bearer $($response.data.token)" }
        $agentRow = Invoke-PostgresScalar "SELECT d.agent_id, COALESCE(d.latest_version_id, v.version_id) FROM sa_agent_definition d LEFT JOIN sa_agent_version v ON v.agent_id=d.agent_id WHERE d.tenant_id='default' AND COALESCE(d.latest_version_id, v.version_id) IS NOT NULL ORDER BY d.updated_at DESC LIMIT 1;"
        $parts = $agentRow -split "`t"
        $conversation = Invoke-Json POST "/api/conversations" -Headers $tokenHeaders
        Assert-ApiOk $conversation "Create conversation"
        $run = Invoke-Json POST "/api/agents/$($parts[0])/runs" -Headers $tokenHeaders -Body @{
            versionId = $parts[1]
            tenantId = "default"
            conversationId = "$($conversation.data)"
            triggerType = "API"
            inputSummary = $Marker
            traceId = "trace-$Marker"
        }
        Assert-ApiOk $run "Create agent run"
        return [pscustomobject]@{ Headers = $tokenHeaders; RunId = "$($run.data.runId)" }
    }
    $runId = $login.RunId
    $headers = $login.Headers

    Test-Step "Install one-shot response-loss proxy in the worker network" {
        $networks = (& docker inspect $WorkerContainer --format '{{json .NetworkSettings.Networks}}') | ConvertFrom-Json
        $names = @($networks.PSObject.Properties.Name)
        if ($names.Count -ne 1) { throw "worker must have exactly one Docker network" }
        $script:networkName = $names[0]
        $script:originalActiveSessionLimit = Invoke-PostgresScalar "SELECT active_session_limit FROM sa_sandbox_runtime_node WHERE node_id='$RemoteNodeId';"
        $script:originalTransportUri = Invoke-PostgresScalar "SELECT transport_uri FROM sa_sandbox_runtime_node WHERE node_id='$RemoteNodeId';"
        $proxyPath = (Resolve-Path "$PSScriptRoot/sandbox-create-response-loss-proxy.py").Path

        & docker run -d --name $proxyContainer --network $script:networkName `
            --network-alias $ProxyNetworkAlias `
            --mount "type=bind,source=$proxyPath,target=/opt/response-loss-proxy.py,readonly" `
            python:3.11-alpine python /opt/response-loss-proxy.py `
            --upstream-host $WorkerNetworkAlias --upstream-port 9090 --release-file $releaseFile | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "failed to start response-loss proxy" }
        Wait-ProxyEvent "proxy-ready" | Out-Null
        Wait-CoordinatorProxyHealth
    } | Out-Null

    Test-Step "Align with a fresh worker heartbeat and force one finite slot" {
        $before = Invoke-PostgresScalar "SELECT heartbeat_at FROM sa_sandbox_runtime_node WHERE node_id='$RemoteNodeId';"
        $deadline = (Get-Date).AddSeconds(25)
        do {
            Start-Sleep -Milliseconds 500
            $after = Invoke-PostgresScalar "SELECT heartbeat_at FROM sa_sandbox_runtime_node WHERE node_id='$RemoteNodeId';"
        } while ($after -eq $before -and (Get-Date) -lt $deadline)
        if ($after -eq $before) { throw "worker heartbeat did not advance" }
        $proxyTransportUri = "http://$ProxyNetworkAlias`:9090/internal/sandbox/runtime"
        Invoke-PostgresCommand "UPDATE sa_sandbox_runtime_node SET active_session_limit=1, transport_uri='$proxyTransportUri' WHERE node_id='$RemoteNodeId';"
        $updated = Invoke-PostgresScalar "SELECT active_session_limit, transport_uri FROM sa_sandbox_runtime_node WHERE node_id='$RemoteNodeId';"
        if ($updated -ne "1`t$proxyTransportUri") {
            throw "worker finite capacity and proxy transport URI were not applied"
        }
    } | Out-Null

    Test-Step "Lose create response and hold cleanup confirmation" {
        $script:createJob = Start-SessionCreateJob -Authorization $headers.Authorization -TargetRunId $runId
        Wait-ProxyEvent "close-response-held" -TimeoutSeconds 30 | Out-Null
        $events = Get-ProxyEvents
        $createRequests = @($events | Where-Object { $_.event -eq "create-request-received" })
        $dropped = @($events | Where-Object { $_.event -eq "create-response-dropped" })
        $ownership = @($events | Where-Object { $_.event -eq "ownership-response-forwarded" })
        $held = @($events | Where-Object { $_.event -eq "close-response-held" })
        if ($createRequests.Count -ne 1 -or $dropped.Count -ne 1 `
                -or $ownership.Count -ne 1 -or $held.Count -ne 1) {
            throw "expected one create request, create loss, ownership query, and held close"
        }
        $script:sessionId = "$($dropped[0].sessionId)"
        if ($sessionId -notmatch '^[A-Za-z0-9._-]{1,128}$') { throw "proxy did not capture a safe session id" }
        if ("$($ownership[0].sessionId)" -ne $sessionId -or "$($ownership[0].ownership)" -ne "OWNED") {
            throw "ownership reconciliation did not confirm the coordinator session"
        }
        if ("$($held[0].sessionId)" -ne $sessionId) { throw "cleanup targeted a different session id" }
    } | Out-Null

    Test-Step "Keep reservation until matching close response is returned" {
        $safeSession = $sessionId.Replace("'", "''")
        $state = Invoke-PostgresScalar "SELECT (SELECT COUNT(*) FROM sa_sandbox_runtime_capacity_reservation WHERE node_id='$RemoteNodeId'), (SELECT COUNT(*) FROM sa_sandbox_session WHERE session_id='$safeSession')"
        if ($state -ne "1`t0") { throw "expected reservation=1 and persisted session=0 while close confirmation is held, got $state" }
        & docker exec -e "SESSION_ID=$sessionId" $WorkerContainer sh -lc 'test ! -e "/var/lib/seahorse-sandbox/$SESSION_ID"'
        if ($LASTEXITCODE -ne 0) { throw "worker workspace remains after runtime close" }
        & docker exec -e "SESSION_ID=$sessionId" $CoordinatorContainer sh -lc 'test ! -e "/var/lib/seahorse-sandbox/$SESSION_ID"'
        if ($LASTEXITCODE -ne 0) { throw "coordinator unexpectedly owns the remote workspace" }
    } | Out-Null

    $response = Test-Step "Release cleanup confirmation and persist one failed session" {
        & docker exec $proxyContainer touch $releaseFile
        if ($LASTEXITCODE -ne 0) { throw "failed to release close response gate" }
        $result = Receive-SessionCreateJob $createJob
        $script:createJob | Remove-Job -Force -ErrorAction SilentlyContinue
        $script:createJob = $null
        Assert-ApiOk $result "Create sandbox session"
        $matchesExpectedFailure = "$($result.data.sessionId)" -eq $sessionId `
            -and "$($result.data.status)" -eq "FAILED" `
            -and "$($result.data.reasonCode)" -eq "RUNTIME_NODE_UNAVAILABLE"
        if (-not $matchesExpectedFailure) {
            throw "ambiguous create did not fail closed with the coordinator session id"
        }
        return $result
    }

    Test-Step "Require no retry, reservation, workspace, or child-container residue" {
        $events = Get-ProxyEvents
        if (@($events | Where-Object { $_.event -eq "create-request-received" }).Count -ne 1) {
            throw "runtime create was attempted more than once"
        }
        $safeSession = $sessionId.Replace("'", "''")
        $safeRun = $runId.Replace("'", "''")
        $state = Invoke-PostgresScalar @"
SELECT (SELECT COUNT(*) FROM sa_sandbox_session WHERE run_id='$safeRun'),
       (SELECT status FROM sa_sandbox_session WHERE session_id='$safeSession'),
       (SELECT COALESCE(runtime_node_id, '') FROM sa_sandbox_session WHERE session_id='$safeSession'),
       (SELECT COUNT(*) FROM sa_sandbox_runtime_capacity_reservation WHERE node_id='$RemoteNodeId');
"@
        if ($state -ne "1`tFAILED`t`t0") { throw "unexpected persisted reconciliation state: $state" }
        $managed = @(docker ps -a --format '{{.Names}}' | Where-Object { $_ -like "seahorse-sandbox-*" })
        if ($managed.Count -ne 0) { throw "managed sandbox child containers remain: $($managed -join ',')" }
    } | Out-Null
} finally {
    if ($createJob) {
        try { & docker exec $proxyContainer touch $releaseFile 2>$null | Out-Null } catch { }
        try { Wait-Job $createJob -Timeout 10 | Out-Null } catch { }
        $createJob | Remove-Job -Force -ErrorAction SilentlyContinue
    }
    try { Remove-ResponseLossProxy } catch { Write-Error "response-loss proxy cleanup failed: $($_.Exception.Message)" }
    if ($null -ne $originalActiveSessionLimit -and $null -ne $originalTransportUri) {
        try {
            $safeTransportUri = $originalTransportUri.Replace("'", "''")
            Invoke-PostgresCommand "UPDATE sa_sandbox_runtime_node SET active_session_limit=$originalActiveSessionLimit, transport_uri='$safeTransportUri' WHERE node_id='$RemoteNodeId';"
        } catch { Write-Error "worker registration restore failed: $($_.Exception.Message)" }
    }
}

Test-Step "Verify proxy removal and runtime registration restoration" {
    $remainingProxy = @(& docker ps -aq --filter "name=^/$proxyContainer$")
    if ($LASTEXITCODE -ne 0 -or $remainingProxy.Count -ne 0) {
        throw "response-loss proxy remains after cleanup"
    }
    $restored = Invoke-PostgresScalar "SELECT active_session_limit, transport_uri FROM sa_sandbox_runtime_node WHERE node_id='$RemoteNodeId';"
    if ($restored -ne "$originalActiveSessionLimit`t$originalTransportUri") {
        throw "runtime registration was not restored: $restored"
    }
    $residue = Invoke-PostgresScalar "SELECT (SELECT COUNT(*) FROM sa_sandbox_runtime_capacity_reservation), (SELECT COUNT(*) FROM sa_sandbox_session WHERE status NOT IN ('SUCCEEDED','FAILED','TIMED_OUT','CANCELLED'));"
    if ($residue -ne "0`t0") { throw "runtime residue remains after cleanup: $residue" }
} | Out-Null

Write-Host "`nSandbox create reconciliation E2E: $passed/$total passed" -ForegroundColor Green
