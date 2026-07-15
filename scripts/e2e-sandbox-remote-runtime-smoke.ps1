param(
    [string]$BaseUrl = "http://127.0.0.1:9090",
    [string]$WorkerBaseUrl = "http://127.0.0.1:19092",
    [string]$Username = "admin",
    [string]$Password = "admin123",
    [string]$LocalNodeId = "local-container-docker",
    [string]$RemoteNodeId = "sandbox-node-b",
    [string]$CoordinatorContainer = "seahorse-backend",
    [string]$WorkerContainer = "seahorse-runtime-node-b",
    [string]$PostgresContainer = "seahorse-postgres",
    [string]$Marker = "seahorse-remote-runtime-e2e"
)

$ErrorActionPreference = "Stop"
$passed = 0
$failed = 0
$total = 0
$sessionId = $null
$localLoadSessionId = $null
$drainingSessionId = $null
$runId = $null
$workerPaused = $false
$remoteAdmissionAvailable = $null
$remoteAdmissionStatus = $null

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
        [int]$ExpectedStatus = 200,
        [string]$Origin = $BaseUrl
    )
    $temp = $null
    $args = @("-sS", "-w", "`n%{http_code}", "-X", $Method, "$Origin$Path")
    if ($null -ne $Body) {
        $text = if ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Depth 20 -Compress }
        $temp = New-TemporaryFile
        Set-Content -LiteralPath $temp.FullName -Value $text -Encoding UTF8 -NoNewline
        $args += @("-H", "Content-Type: application/json", "--data-binary", "@$($temp.FullName)")
    }
    foreach ($key in $Headers.Keys) {
        $args += @("-H", "${key}: $($Headers[$key])")
    }
    try {
        $raw = & curl.exe @args
        $exitCode = $LASTEXITCODE
    } finally {
        if ($temp) { Remove-Item -LiteralPath $temp.FullName -Force -ErrorAction SilentlyContinue }
    }
    if ($exitCode -ne 0) { throw "curl exited with $exitCode for $Method $Path" }
    $lines = @($raw)
    $status = [int]$lines[-1]
    $content = if ($lines.Count -gt 1) { $lines[0..($lines.Count - 2)] -join "`n" } else { "" }
    if ($status -ne $ExpectedStatus) {
        throw "Expected HTTP $ExpectedStatus but got $status for $Method $Path body=$content"
    }
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
    $raw = & docker exec $PostgresContainer psql -U seahorse -d seahorse -At -F "`t" -c $Sql
    if ($LASTEXITCODE -ne 0) { throw "psql failed for SQL: $Sql" }
    $rows = @($raw | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($rows.Count -eq 0) { throw "SQL returned no rows: $Sql" }
    return $rows[0]
}

function Invoke-PostgresCommand {
    param([string]$Sql)
    & docker exec $PostgresContainer psql -U seahorse -d seahorse -v ON_ERROR_STOP=1 -c $Sql | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "psql failed for SQL: $Sql" }
}

function Invoke-DownloadText {
    param([string]$Path, [hashtable]$Headers)
    $args = @("-sS", "-X", "GET", "$BaseUrl$Path")
    foreach ($key in $Headers.Keys) { $args += @("-H", "${key}: $($Headers[$key])") }
    $content = & curl.exe @args
    if ($LASTEXITCODE -ne 0) { throw "download failed for $Path" }
    return @($content) -join "`n"
}

function Resume-WorkerHeartbeat {
    if (-not $script:workerPaused) { return }
    & docker unpause $WorkerContainer | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "failed to resume worker heartbeat" }
    $script:workerPaused = $false
}

function Restore-RemoteAdmissionState {
    if ($null -eq $script:remoteAdmissionAvailable -or -not $script:remoteAdmissionStatus) { return }
    $safeRemoteNode = $RemoteNodeId.Replace("'", "''")
    $expectedAvailable = if ($script:remoteAdmissionAvailable) { "t" } else { "f" }
    $deadline = (Get-Date).AddSeconds(45)
    do {
        $state = Invoke-PostgresScalar "SELECT admission_available, admission_status FROM sa_sandbox_runtime_node WHERE node_id='$safeRemoteNode';"
        if ($state -eq "$expectedAvailable`t$script:remoteAdmissionStatus") {
            $script:remoteAdmissionAvailable = $null
            $script:remoteAdmissionStatus = $null
            return
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    $availableSql = if ($script:remoteAdmissionAvailable) { "TRUE" } else { "FALSE" }
    $safeStatus = $script:remoteAdmissionStatus.Replace("'", "''")
    Invoke-PostgresCommand "UPDATE sa_sandbox_runtime_node SET admission_available=$availableSql, admission_status='$safeStatus' WHERE node_id='$safeRemoteNode';"
    throw "worker heartbeat did not restore admission state before timeout"
}

function Remove-SessionFallback {
    param([string]$TargetSessionId, [string]$TargetContainer)
    if ($TargetSessionId -notmatch '^[A-Za-z0-9._-]{1,128}$') {
        throw "refusing fallback cleanup for unsafe session id"
    }
    $containerName = "seahorse-sandbox-$($TargetSessionId.ToLowerInvariant())"
    $existingContainer = @(& docker ps -aq --filter "name=^/$containerName$")
    if ($LASTEXITCODE -ne 0) { throw "failed to inspect managed sandbox container" }
    if ($existingContainer.Count -gt 0) {
        & docker rm -f $containerName | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "failed to remove managed sandbox container $containerName" }
    }
    & docker exec -e "SESSION_ID=$TargetSessionId" $TargetContainer sh -lc `
        'target="/var/lib/seahorse-sandbox/$SESSION_ID"; rm -rf -- "$target"; test ! -e "$target"'
    if ($LASTEXITCODE -ne 0) { throw "remote workspace remains after fallback cleanup" }
}

try {
    Test-Step "Require both backend processes healthy" {
        if ((Invoke-Json -Method GET -Path "/actuator/health").status -ne "UP") {
            throw "coordinator is not healthy"
        }
        if ((Invoke-Json -Method GET -Path "/actuator/health" -Origin $WorkerBaseUrl).status -ne "UP") {
            throw "worker is not healthy"
        }
    } | Out-Null

    Test-Step "Reject unsigned internal transport requests" {
        Invoke-Json -Method POST -Path "/internal/sandbox/runtime/sessions" -Body @{} -ExpectedStatus 401 | Out-Null
        Invoke-Json -Method POST -Path "/internal/sandbox/runtime/sessions" -Body @{} -ExpectedStatus 401 -Origin $WorkerBaseUrl | Out-Null
    } | Out-Null

    $login = Test-Step "Login as administrator" {
        $response = Invoke-Json -Method POST -Path "/auth/login" -Body @{ username = $Username; password = $Password }
        Assert-ApiOk $response "Login"
        if (-not $response.data.token) { throw "login response did not include token" }
        return $response
    }
    if (-not $login) { throw "login failed" }
    $headers = @{ Authorization = "Bearer $($login.data.token)" }

    Test-Step "Require local and remote node registrations LIVE" {
        $response = Invoke-Json -Method GET -Path "/api/admin/sandbox/runtime/registrations?limit=100" -Headers $headers
        Assert-ApiOk $response "List runtime registrations"
        $local = @($response.data | Where-Object { "$($_.nodeId)" -eq $LocalNodeId -and "$($_.registrationStatus)" -eq "LIVE" })
        $remote = @($response.data | Where-Object { "$($_.nodeId)" -eq $RemoteNodeId -and "$($_.registrationStatus)" -eq "LIVE" })
        if ($local.Count -ne 1 -or $remote.Count -ne 1) { throw "expected one LIVE local and remote registration" }
        $json = $response.data | ConvertTo-Json -Depth 20 -Compress
        if ($json -match "transportUri|transport_uri|sharedSecret|ownerId|owner_id") {
            throw "registration API leaked private transport fields"
        }
    } | Out-Null

    $run = Test-Step "Create a real agent run for sandbox ownership" {
        $agentRow = Invoke-PostgresScalar "SELECT d.agent_id, COALESCE(d.latest_version_id, v.version_id) FROM sa_agent_definition d LEFT JOIN sa_agent_version v ON v.agent_id=d.agent_id WHERE d.tenant_id='default' AND COALESCE(d.latest_version_id, v.version_id) IS NOT NULL ORDER BY d.updated_at DESC LIMIT 1;"
        $parts = $agentRow -split "`t"
        if ($parts.Count -ne 2) { throw "no real agent/version fixture available" }
        $conversation = Invoke-Json -Method POST -Path "/api/conversations" -Headers $headers
        Assert-ApiOk $conversation "Create conversation"
        $response = Invoke-Json -Method POST -Path "/api/agents/$($parts[0])/runs" -Headers $headers -Body @{
            versionId = $parts[1]
            tenantId = "default"
            conversationId = "$($conversation.data)"
            triggerType = "API"
            inputSummary = $Marker
            traceId = "trace-$Marker"
        }
        Assert-ApiOk $response "Create agent run"
        if (-not $response.data.runId) { throw "agent run response did not include runId" }
        return $response.data
    }
    if (-not $run) { throw "agent run creation failed" }
    $runId = "$($run.runId)"

    $localLoadSession = Test-Step "Create explicit local session as node load" {
        $response = Invoke-Json -Method POST -Path "/api/sandbox/sessions" -Headers $headers -Body @{
            tenantId = "default"
            runId = $runId
            runtimeType = "CODE_INTERPRETER"
            networkRequested = $false
            requestedHosts = @()
            requiredRuntimeNodeId = $LocalNodeId
        }
        Assert-ApiOk $response "Create explicit local sandbox session"
        $script:localLoadSessionId = "$($response.data.sessionId)"
        if ("$($response.data.status)" -ne "CREATED" -or "$($response.data.runtimeNodeId)" -ne $LocalNodeId) {
            throw "explicit local session was not created on $LocalNodeId"
        }
        & docker exec $CoordinatorContainer sh -lc "test -d '/var/lib/seahorse-sandbox/$script:localLoadSessionId'"
        if ($LASTEXITCODE -ne 0) { throw "local load workspace is missing" }
        & docker exec $WorkerContainer sh -lc "test ! -e '/var/lib/seahorse-sandbox/$script:localLoadSessionId'"
        if ($LASTEXITCODE -ne 0) { throw "worker unexpectedly owns the local load workspace" }
        return $response.data
    }
    if (-not $localLoadSession) { throw "local load session creation failed" }
    $localLoadSessionId = "$($localLoadSession.sessionId)"

    Test-Step "Wait for node-local heartbeat load metrics" {
        $safeLocalNode = $LocalNodeId.Replace("'", "''")
        $safeRemoteNode = $RemoteNodeId.Replace("'", "''")
        $deadline = (Get-Date).AddSeconds(45)
        do {
            $localCount = [int](Invoke-PostgresScalar "SELECT active_session_count FROM sa_sandbox_runtime_node WHERE node_id='$safeLocalNode';")
            $remoteCount = [int](Invoke-PostgresScalar "SELECT active_session_count FROM sa_sandbox_runtime_node WHERE node_id='$safeRemoteNode';")
            if ($localCount -ge 1 -and $remoteCount -eq 0) { return }
            Start-Sleep -Seconds 2
        } while ((Get-Date) -lt $deadline)
        throw "node-local heartbeat loads did not converge: local=$localCount remote=$remoteCount"
    } | Out-Null

    $session = Test-Step "Automatically place sandbox session on less-loaded remote node" {
        $response = Invoke-Json -Method POST -Path "/api/sandbox/sessions" -Headers $headers -Body @{
            tenantId = "default"
            runId = $runId
            runtimeType = "CODE_INTERPRETER"
            networkRequested = $false
            requestedHosts = @()
        }
        Assert-ApiOk $response "Create automatically placed sandbox session"
        $script:sessionId = "$($response.data.sessionId)"
        if ("$($response.data.status)" -ne "CREATED" -or "$($response.data.runtimeNodeId)" -ne $RemoteNodeId) {
            throw "automatic placement did not choose less-loaded node $RemoteNodeId"
        }
        return $response.data
    }
    if (-not $session) { throw "remote session creation failed" }
    $sessionId = "$($session.sessionId)"

    Test-Step "Require workspace ownership on remote node only" {
        & docker exec $WorkerContainer sh -lc "test -d '/var/lib/seahorse-sandbox/$sessionId'"
        if ($LASTEXITCODE -ne 0) { throw "remote workspace is missing" }
        & docker exec $CoordinatorContainer sh -lc "test ! -e '/var/lib/seahorse-sandbox/$sessionId'"
        if ($LASTEXITCODE -ne 0) { throw "coordinator unexpectedly owns the remote workspace" }
    } | Out-Null

    $execution = Test-Step "Execute Python remotely and transfer artifact" {
        $python = "from pathlib import Path`nPath('remote-e2e.txt').write_text('$Marker', encoding='utf-8')`nprint('$Marker')"
        $response = Invoke-Json -Method POST -Path "/api/sandbox/sessions/$sessionId/execute" -Headers $headers -Body @{
            input = $python
            networkRequested = $false
            requestedHosts = @()
        }
        Assert-ApiOk $response "Execute remote sandbox session"
        if ("$($response.data.execution.status)" -ne "SUCCEEDED") {
            throw "remote execution did not succeed: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $artifacts = @($response.data.artifacts)
        if ($artifacts.Count -lt 1) { throw "remote execution returned no prompt-visible artifact" }
        return $response.data
    }
    if (-not $execution) { throw "remote execution failed" }
    $artifactId = "$($execution.artifacts[0].artifactId)"

    Test-Step "Verify persisted remote execution and governed artifact" {
        $safeSession = $sessionId.Replace("'", "''")
        $safeArtifact = $artifactId.Replace("'", "''")
        $sessionRow = Invoke-PostgresScalar "SELECT status, runtime_node_id FROM sa_sandbox_session WHERE session_id='$safeSession';"
        if ($sessionRow -ne "CREATED`t$RemoteNodeId") { throw "unexpected session row: $sessionRow" }
        $artifactRow = Invoke-PostgresScalar "SELECT scan_status, sensitivity, CASE WHEN object_uri LIKE 'local://sandbox-artifacts/%' THEN 'stored' ELSE 'unexpected' END FROM sa_sandbox_artifact WHERE artifact_id='$safeArtifact';"
        if ($artifactRow -ne "CLEAN`tINTERNAL`tstored") { throw "unexpected artifact row: $artifactRow" }
    } | Out-Null

    Test-Step "Download the artifact through governed object storage" {
        $content = Invoke-DownloadText -Path "/api/sandbox/artifacts/$artifactId/download" -Headers $headers
        if ($content -notlike "*$Marker*") { throw "downloaded artifact did not contain marker" }
    } | Out-Null

    Test-Step "Close remote session and remove remote workspace" {
        $response = Invoke-Json -Method POST -Path "/api/sandbox/sessions/$sessionId/close" -Headers $headers
        Assert-ApiOk $response "Close remote sandbox session"
        if ("$($response.data.status)" -ne "CANCELLED") { throw "remote session was not cancelled" }
        & docker exec $WorkerContainer sh -lc "test ! -e '/var/lib/seahorse-sandbox/$sessionId'"
        if ($LASTEXITCODE -ne 0) { throw "remote workspace remains after close" }
        $script:sessionId = $null
    } | Out-Null

    Test-Step "Exclude draining remote node from automatic placement" {
        & docker pause $WorkerContainer | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "failed to pause worker heartbeat" }
        $script:workerPaused = $true
        $safeRemoteNode = $RemoteNodeId.Replace("'", "''")
        $originalState = (Invoke-PostgresScalar "SELECT admission_available, admission_status FROM sa_sandbox_runtime_node WHERE node_id='$safeRemoteNode';") -split "`t"
        if ($originalState.Count -ne 2) { throw "unable to capture remote admission state" }
        $script:remoteAdmissionAvailable = $originalState[0] -eq "t"
        $script:remoteAdmissionStatus = $originalState[1]
        Invoke-PostgresCommand "UPDATE sa_sandbox_runtime_node SET admission_available=FALSE, admission_status='DRAINING' WHERE node_id='$safeRemoteNode';"
        $response = Invoke-Json -Method POST -Path "/api/sandbox/sessions" -Headers $headers -Body @{
            tenantId = "default"
            runId = $runId
            runtimeType = "CODE_INTERPRETER"
            networkRequested = $false
            requestedHosts = @()
        }
        Assert-ApiOk $response "Create session while remote node is draining"
        $script:drainingSessionId = "$($response.data.sessionId)"
        if ("$($response.data.status)" -ne "CREATED" -or "$($response.data.runtimeNodeId)" -ne $LocalNodeId) {
            throw "automatic placement selected draining remote node"
        }
        $closeResponse = Invoke-Json -Method POST -Path "/api/sandbox/sessions/$script:drainingSessionId/close" -Headers $headers
        Assert-ApiOk $closeResponse "Close draining-placement session"
        $script:drainingSessionId = $null
        Resume-WorkerHeartbeat
        Restore-RemoteAdmissionState
    } | Out-Null

    Test-Step "Close explicit local load session" {
        $response = Invoke-Json -Method POST -Path "/api/sandbox/sessions/$localLoadSessionId/close" -Headers $headers
        Assert-ApiOk $response "Close explicit local sandbox session"
        if ("$($response.data.status)" -ne "CANCELLED") { throw "local load session was not cancelled" }
        & docker exec $CoordinatorContainer sh -lc "test ! -e '/var/lib/seahorse-sandbox/$localLoadSessionId'"
        if ($LASTEXITCODE -ne 0) { throw "local load workspace remains after close" }
        $script:localLoadSessionId = $null
    } | Out-Null

    Test-Step "Require no managed child containers remain" {
        $managed = @(docker ps -a --format "{{.Names}}" | Where-Object { $_ -like "seahorse-sandbox-*" -and $_ -ne $WorkerContainer })
        if ($managed.Count -ne 0) { throw "managed sandbox containers remain: $($managed -join ',')" }
    } | Out-Null
} finally {
    $environmentCleanupIssues = @()
    if ($workerPaused) {
        try { Resume-WorkerHeartbeat } catch { $environmentCleanupIssues += $_.Exception.Message }
    }
    if ($null -ne $remoteAdmissionAvailable -and $remoteAdmissionStatus) {
        try { Restore-RemoteAdmissionState } catch { $environmentCleanupIssues += $_.Exception.Message }
    }
    if ($environmentCleanupIssues.Count -gt 0) {
        $script:total++
        $script:failed++
        Write-Host "`n[$script:total] Restore worker admission state`n  FAIL: $($environmentCleanupIssues -join '; ')" -ForegroundColor Red
    }
    $cleanupTargets = @(
        @{ SessionId = $sessionId; Container = $WorkerContainer },
        @{ SessionId = $drainingSessionId; Container = $CoordinatorContainer },
        @{ SessionId = $localLoadSessionId; Container = $CoordinatorContainer }
    )
    foreach ($target in $cleanupTargets) {
        if (-not $target.SessionId -or -not $headers) { continue }
        $cleanupIssues = @()
        try {
            $cleanupResponse = Invoke-Json -Method POST -Path "/api/sandbox/sessions/$($target.SessionId)/close" -Headers $headers
            Assert-ApiOk $cleanupResponse "Cleanup sandbox session"
        } catch {
            $cleanupIssues += "close failed: $($_.Exception.Message)"
        }
        try {
            Remove-SessionFallback -TargetSessionId $target.SessionId -TargetContainer $target.Container
        } catch {
            $cleanupIssues += "fallback failed: $($_.Exception.Message)"
        }
        $script:total++
        if ($cleanupIssues.Count -eq 0) {
            $script:passed++
            Write-Host "`n[$script:total] Cleanup sandbox session $($target.SessionId)`n  PASS" -ForegroundColor Green
        } else {
            $script:failed++
            Write-Host "`n[$script:total] Cleanup sandbox session $($target.SessionId)`n  FAIL: $($cleanupIssues -join '; ')" -ForegroundColor Red
        }
    }
}

Write-Host "`nRemote sandbox runtime E2E: $passed/$total passed, $failed failed" -ForegroundColor $(if ($failed -eq 0) { "Green" } else { "Red" })
if ($failed -ne 0) { exit 1 }
