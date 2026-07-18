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
    [string]$Marker = "seahorse-runtime-node-admission-control-e2e"
)

$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd("/")
$WorkerBaseUrl = $WorkerBaseUrl.TrimEnd("/")
$passed = 0
$failed = 0
$total = 0
$failure = $null
$headers = $null
$runId = $null
$localLoadSessionId = $null
$existingWorkerSessionId = $null
$drainedAutomaticSessionId = $null
$explicitRejectedSessionId = $null
$resumedAutomaticSessionId = $null
$admissionAuditCount = 0

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
        throw
    }
}

function Invoke-Json {
    param(
        [ValidateSet("GET", "POST")][string]$Method,
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
        [IO.File]::WriteAllText($temp.FullName, $text, (New-Object Text.UTF8Encoding($false)))
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

function Assert-SafeId {
    param([string]$Value, [string]$Name)
    if ($Value -notmatch '^[A-Za-z0-9._-]{1,128}$') {
        throw "$Name is missing or unsafe"
    }
}

function Invoke-PostgresScalar {
    param([string]$Sql)
    $raw = & docker exec $PostgresContainer psql -U seahorse -d seahorse -At -F "`t" -c $Sql
    if ($LASTEXITCODE -ne 0) { throw "psql failed for SQL: $Sql" }
    $rows = @($raw | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($rows.Count -eq 0) { throw "SQL returned no rows: $Sql" }
    return "$($rows[0])"
}

function New-SandboxSession {
    param([string]$RequiredNodeId = "")
    $body = @{
        tenantId = "default"
        runId = $script:runId
        runtimeType = "CODE_INTERPRETER"
        networkRequested = $false
        requestedHosts = @()
    }
    if (-not [string]::IsNullOrWhiteSpace($RequiredNodeId)) {
        $body.requiredRuntimeNodeId = $RequiredNodeId
    }
    $response = Invoke-Json -Method POST -Path "/api/sandbox/sessions" -Headers $script:headers -Body $body
    Assert-ApiOk $response "Create sandbox session"
    return $response.data
}

function Close-SandboxSession {
    param([string]$SessionId)
    if ([string]::IsNullOrWhiteSpace($SessionId)) { return }
    Assert-SafeId $SessionId "session id"
    $response = Invoke-Json -Method POST -Path "/api/sandbox/sessions/$SessionId/close" -Headers $script:headers
    Assert-ApiOk $response "Close sandbox session $SessionId"
}

function Wait-NodeLoads {
    param([int]$MinimumLocal, [int]$ExpectedRemote)
    $deadline = (Get-Date).AddSeconds(50)
    do {
        $state = Invoke-PostgresScalar @"
SELECT
  (SELECT active_session_count FROM sa_sandbox_runtime_node WHERE node_id = '$LocalNodeId'),
  (SELECT active_session_count FROM sa_sandbox_runtime_node WHERE node_id = '$RemoteNodeId');
"@
        $parts = $state -split "`t"
        if ($parts.Count -eq 2 -and [int]$parts[0] -ge $MinimumLocal -and [int]$parts[1] -eq $ExpectedRemote) {
            return
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "node heartbeat loads did not converge: local/remote=$state"
}

function Get-Registration {
    param([string]$NodeId)
    $response = Invoke-Json -Method GET -Path "/api/admin/sandbox/runtime/registrations?limit=100" -Headers $script:headers
    Assert-ApiOk $response "List runtime registrations"
    $matches = @($response.data | Where-Object { "$($_.nodeId)" -eq $NodeId })
    if ($matches.Count -ne 1) { throw "expected exactly one registration for $NodeId" }
    return $matches[0]
}

function Assert-AdmissionAudit {
    param([bool]$Draining)
    $expectedCount = $script:admissionAuditCount + 1
    $auditResponse = Invoke-Json -Method GET `
        -Path "/api/audit-events?tenantId=default&resourceType=SANDBOX_RUNTIME_NODE&resourceId=$RemoteNodeId&eventType=SANDBOX_RUNTIME_NODE_ADMISSION_CHANGED&current=1&size=100" `
        -Headers $script:headers
    Assert-ApiOk $auditResponse "List runtime node admission audit events"
    $records = @($auditResponse.data.records)
    $databaseCount = [int](Invoke-PostgresScalar "SELECT COUNT(*) FROM sa_audit_event WHERE tenant_id='default' AND resource_type='SANDBOX_RUNTIME_NODE' AND resource_id='$RemoteNodeId' AND event_type='SANDBOX_RUNTIME_NODE_ADMISSION_CHANGED';")
    if ([int]$auditResponse.data.total -ne $expectedCount -or $databaseCount -ne $expectedCount -or
        $records.Count -lt 1) {
        throw "admission command did not emit exactly one API and database audit event"
    }

    $event = $records[0]
    if ("$($event.tenantId)" -ne "default" -or
        "$($event.eventType)" -ne "SANDBOX_RUNTIME_NODE_ADMISSION_CHANGED" -or
        "$($event.actorType)" -ne "USER" -or "$($event.actorId)" -ne $Username -or
        "$($event.resourceType)" -ne "SANDBOX_RUNTIME_NODE" -or
        "$($event.resourceId)" -ne $RemoteNodeId) {
        throw "runtime node admission audit identity is incorrect"
    }
    $payload = "$($event.redactedPayload)" | ConvertFrom-Json
    $payloadKeys = @($payload.PSObject.Properties.Name)
    if ($payloadKeys.Count -ne 1 -or $payloadKeys[0] -ne "draining" -or
        [bool]$payload.draining -ne $Draining) {
        throw "runtime node admission audit payload is not minimal or has the wrong state"
    }

    $databaseRow = Invoke-PostgresScalar @"
SELECT tenant_id, event_type, actor_type, actor_id, resource_type, resource_id,
       redacted_payload,
       occurred_at = (SELECT updated_at FROM sa_sandbox_runtime_node_admission_override WHERE node_id='$RemoteNodeId')
FROM sa_audit_event
WHERE tenant_id='default'
  AND resource_type='SANDBOX_RUNTIME_NODE'
  AND resource_id='$RemoteNodeId'
  AND event_type='SANDBOX_RUNTIME_NODE_ADMISSION_CHANGED'
ORDER BY occurred_at DESC, audit_id DESC
LIMIT 1;
"@
    $databaseParts = "$databaseRow" -split "`t", 8
    $expectedPayload = if ($Draining) { '{"draining":true}' } else { '{"draining":false}' }
    if ($databaseParts.Count -ne 8 -or $databaseParts[0] -ne "default" -or
        $databaseParts[1] -ne "SANDBOX_RUNTIME_NODE_ADMISSION_CHANGED" -or
        $databaseParts[2] -ne "USER" -or $databaseParts[3] -ne $Username -or
        $databaseParts[4] -ne "SANDBOX_RUNTIME_NODE" -or $databaseParts[5] -ne $RemoteNodeId -or
        $databaseParts[6] -ne $expectedPayload -or $databaseParts[7] -ne "t") {
        throw "database admission audit is not atomically correlated to the override: $databaseRow"
    }

    [string]$auditJson = $event | ConvertTo-Json -Depth 20 -Compress
    $forbiddenMarkers = @("transportUri", "transport_uri", "ownerId", "owner_id", "endpoint", "sharedSecret", "http://", "https://")
    foreach ($forbidden in $forbiddenMarkers) {
        if ($auditJson.IndexOf($forbidden, [StringComparison]::OrdinalIgnoreCase) -ge 0 -or
            $databaseParts[6].IndexOf($forbidden, [StringComparison]::OrdinalIgnoreCase) -ge 0) {
            throw "runtime node admission audit leaked infrastructure detail marker: $forbidden"
        }
    }
    $script:admissionAuditCount = $expectedCount
}

try {
    Assert-SafeId $LocalNodeId "local node id"
    Assert-SafeId $RemoteNodeId "remote node id"
    Assert-SafeId $Marker "marker"

    Test-Step "Require coordinator and worker health" {
        if ((Invoke-Json -Method GET -Path "/actuator/health").status -ne "UP") {
            throw "coordinator is not healthy"
        }
        if ((Invoke-Json -Method GET -Path "/actuator/health" -Origin $WorkerBaseUrl).status -ne "UP") {
            throw "worker is not healthy"
        }
    } | Out-Null

    $login = Test-Step "Login as administrator" {
        $response = Invoke-Json -Method POST -Path "/auth/login" -Body @{
            username = $Username
            password = $Password
        }
        Assert-ApiOk $response "Login"
        if (-not "$($response.data.token)") { throw "login response did not include token" }
        return $response
    }
    $headers = @{ Authorization = "Bearer $($login.data.token)" }
    $admissionAuditCount = [int](Invoke-PostgresScalar "SELECT COUNT(*) FROM sa_audit_event WHERE tenant_id='default' AND resource_type='SANDBOX_RUNTIME_NODE' AND resource_id='$RemoteNodeId' AND event_type='SANDBOX_RUNTIME_NODE_ADMISSION_CHANGED';")

    Test-Step "Resume worker to establish a clean admission baseline" {
        $response = Invoke-Json -Method POST `
            -Path "/api/admin/sandbox/runtime/registrations/$RemoteNodeId/resume" -Headers $headers
        Assert-ApiOk $response "Resume runtime node"
        if ($response.data.draining -ne $false -or "$($response.data.operatorId)" -ne $Username) {
            throw "unexpected resume response: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        Assert-AdmissionAudit -Draining $false
        $local = Get-Registration $LocalNodeId
        $remote = Get-Registration $RemoteNodeId
        if ("$($local.registrationStatus)" -ne "LIVE" -or "$($remote.registrationStatus)" -ne "LIVE" -or
            $remote.effectiveAdmissionAvailable -ne $true -or
            "$($remote.effectiveAdmissionStatus)" -notin @("AVAILABLE", "DEGRADED")) {
            throw "runtime-node baseline is not schedulable"
        }
    } | Out-Null

    $run = Test-Step "Create a real agent run for sandbox ownership" {
        $agentRow = Invoke-PostgresScalar "SELECT d.agent_id, COALESCE(d.latest_version_id, v.version_id) FROM sa_agent_definition d LEFT JOIN sa_agent_version v ON v.agent_id=d.agent_id WHERE d.tenant_id='default' AND COALESCE(d.latest_version_id, v.version_id) IS NOT NULL ORDER BY d.updated_at DESC LIMIT 1;"
        $parts = $agentRow -split "`t"
        if ($parts.Count -ne 2) { throw "no real agent/version fixture is available" }
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
        Assert-SafeId "$($response.data.runId)" "run id"
        return $response.data
    }
    $runId = "$($run.runId)"

    Test-Step "Create local load and an existing worker session" {
        $local = New-SandboxSession -RequiredNodeId $LocalNodeId
        $script:localLoadSessionId = "$($local.sessionId)"
        $worker = New-SandboxSession -RequiredNodeId $RemoteNodeId
        $script:existingWorkerSessionId = "$($worker.sessionId)"
        Assert-SafeId $script:localLoadSessionId "local load session id"
        Assert-SafeId $script:existingWorkerSessionId "worker session id"
        if ("$($local.status)" -ne "CREATED" -or "$($local.runtimeNodeId)" -ne $LocalNodeId) {
            throw "local load was not created on $LocalNodeId"
        }
        if ("$($worker.status)" -ne "CREATED" -or "$($worker.runtimeNodeId)" -ne $RemoteNodeId) {
            throw "existing session was not created on $RemoteNodeId"
        }
        & docker exec $CoordinatorContainer sh -lc "test -d '/var/lib/seahorse-sandbox/$script:localLoadSessionId'"
        if ($LASTEXITCODE -ne 0) { throw "local load workspace is missing" }
        & docker exec $WorkerContainer sh -lc "test -d '/var/lib/seahorse-sandbox/$script:existingWorkerSessionId'"
        if ($LASTEXITCODE -ne 0) { throw "worker session workspace is missing" }
        Wait-NodeLoads -MinimumLocal 1 -ExpectedRemote 1
    } | Out-Null

    $heartbeatBeforeDrain = Invoke-PostgresScalar "SELECT EXTRACT(EPOCH FROM heartbeat_at)::bigint FROM sa_sandbox_runtime_node WHERE node_id='$RemoteNodeId';"
    Test-Step "Drain worker through the admin API" {
        $response = Invoke-Json -Method POST `
            -Path "/api/admin/sandbox/runtime/registrations/$RemoteNodeId/drain" -Headers $headers
        Assert-ApiOk $response "Drain runtime node"
        if ($response.data.draining -ne $true -or "$($response.data.nodeId)" -ne $RemoteNodeId -or
            "$($response.data.operatorId)" -ne $Username) {
            throw "unexpected drain response: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        Assert-AdmissionAudit -Draining $true
    } | Out-Null

    Test-Step "Keep drain effective across a real worker heartbeat" {
        $deadline = (Get-Date).AddSeconds(50)
        do {
            $state = Invoke-PostgresScalar @"
SELECT EXTRACT(EPOCH FROM n.heartbeat_at)::bigint,
       n.admission_available, n.admission_status,
       o.draining, o.operator_id
FROM sa_sandbox_runtime_node n
JOIN sa_sandbox_runtime_node_admission_override o ON o.node_id = n.node_id
WHERE n.node_id = '$RemoteNodeId';
"@
            $parts = $state -split "`t"
            if ($parts.Count -eq 5 -and [long]$parts[0] -gt [long]$heartbeatBeforeDrain -and
                $parts[1] -eq "t" -and $parts[2] -eq "AVAILABLE" -and
                $parts[3] -eq "t" -and $parts[4] -eq $Username) {
                break
            }
            Start-Sleep -Seconds 2
        } while ((Get-Date) -lt $deadline)
        if ($parts.Count -ne 5 -or [long]$parts[0] -le [long]$heartbeatBeforeDrain -or
            $parts[1] -ne "t" -or $parts[2] -ne "AVAILABLE" -or $parts[3] -ne "t") {
            throw "heartbeat did not preserve observed AVAILABLE plus operator drain: $state"
        }
        $registration = Get-Registration $RemoteNodeId
        if ($registration.observedAdmissionAvailable -ne $true -or
            "$($registration.observedAdmissionStatus)" -ne "AVAILABLE" -or
            $registration.effectiveAdmissionAvailable -ne $false -or
            "$($registration.effectiveAdmissionStatus)" -ne "DRAINING" -or
            $registration.operatorDraining -ne $true) {
            throw "registration did not compose the effective drain state"
        }
    } | Out-Null

    Test-Step "Keep drain effective across a real worker restart" {
        $ownerBeforeRestart = Invoke-PostgresScalar "SELECT owner_id FROM sa_sandbox_runtime_node WHERE node_id='$RemoteNodeId';"
        & docker restart $WorkerContainer | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "failed to restart worker" }
        $deadline = (Get-Date).AddMinutes(3)
        do {
            try {
                $health = (Invoke-RestMethod -Uri "$WorkerBaseUrl/actuator/health" -TimeoutSec 5).status
            } catch {
                $health = ""
            }
            if ($health -eq "UP") {
                $state = Invoke-PostgresScalar @"
SELECT owner_id,
       CASE WHEN expires_at > CURRENT_TIMESTAMP THEN 'LIVE' ELSE 'STALE' END,
       admission_available, admission_status,
       (SELECT draining FROM sa_sandbox_runtime_node_admission_override WHERE node_id='$RemoteNodeId')
FROM sa_sandbox_runtime_node
WHERE node_id='$RemoteNodeId';
"@
                $parts = $state -split "`t"
                if ($parts.Count -eq 5 -and $parts[0] -ne $ownerBeforeRestart -and
                    $parts[1] -eq "LIVE" -and $parts[2] -eq "t" -and
                    $parts[3] -eq "AVAILABLE" -and $parts[4] -eq "t") {
                    break
                }
            }
            Start-Sleep -Seconds 3
        } while ((Get-Date) -lt $deadline)
        if ($parts.Count -ne 5 -or $parts[0] -eq $ownerBeforeRestart -or
            $parts[1] -ne "LIVE" -or $parts[2] -ne "t" -or
            $parts[3] -ne "AVAILABLE" -or $parts[4] -ne "t") {
            throw "worker restart did not preserve operator drain: health=$health state=$state"
        }
        $registration = Get-Registration $RemoteNodeId
        if ($registration.observedAdmissionAvailable -ne $true -or
            "$($registration.observedAdmissionStatus)" -ne "AVAILABLE" -or
            $registration.effectiveAdmissionAvailable -ne $false -or
            "$($registration.effectiveAdmissionStatus)" -ne "DRAINING") {
            throw "restarted worker registration did not retain effective drain"
        }
    } | Out-Null

    Test-Step "Execute and close the existing worker session while drained" {
        $closedWorkerSessionId = $existingWorkerSessionId
        $response = Invoke-Json -Method POST `
            -Path "/api/sandbox/sessions/$closedWorkerSessionId/execute" -Headers $headers -Body @{
                input = "print('$Marker-existing-worker')"
                networkRequested = $false
                requestedHosts = @()
            }
        Assert-ApiOk $response "Execute existing worker session"
        if ("$($response.data.execution.status)" -ne "SUCCEEDED" -or
            "$($response.data.execution.resultSummary)" -notlike "*$Marker-existing-worker*") {
            throw "existing worker session did not execute while drained"
        }
        Close-SandboxSession $closedWorkerSessionId
        $script:existingWorkerSessionId = $null
        & docker exec $WorkerContainer sh -lc "test ! -e '/var/lib/seahorse-sandbox/$closedWorkerSessionId'"
        if ($LASTEXITCODE -ne 0) { throw "existing worker workspace remains after close" }
    } | Out-Null

    Test-Step "Route automatic placement away from the drained worker" {
        $created = New-SandboxSession
        $script:drainedAutomaticSessionId = "$($created.sessionId)"
        Assert-SafeId $script:drainedAutomaticSessionId "drained automatic session id"
        if ("$($created.status)" -ne "CREATED" -or "$($created.runtimeNodeId)" -ne $LocalNodeId) {
            throw "automatic placement selected a drained worker"
        }
        Close-SandboxSession $script:drainedAutomaticSessionId
        $script:drainedAutomaticSessionId = $null
    } | Out-Null

    Test-Step "Reject explicit placement on the drained worker" {
        $created = New-SandboxSession -RequiredNodeId $RemoteNodeId
        $script:explicitRejectedSessionId = "$($created.sessionId)"
        Assert-SafeId $script:explicitRejectedSessionId "explicit rejected session id"
        if ("$($created.status)" -ne "FAILED" -or "$($created.reasonCode)" -ne "RUNTIME_NODE_DRAINING" -or
            -not [string]::IsNullOrWhiteSpace("$($created.runtimeNodeId)")) {
            throw "explicit drained-node request did not fail closed"
        }
        $row = Invoke-PostgresScalar "SELECT status, reason_code, COALESCE(runtime_node_id, '<null>') FROM sa_sandbox_session WHERE session_id='$script:explicitRejectedSessionId';"
        if ($row -ne "FAILED`tRUNTIME_NODE_DRAINING`t<null>") {
            throw "unexpected explicit rejection persistence: $row"
        }
    } | Out-Null

    Test-Step "Resume worker and restore effective admission" {
        $response = Invoke-Json -Method POST `
            -Path "/api/admin/sandbox/runtime/registrations/$RemoteNodeId/resume" -Headers $headers
        Assert-ApiOk $response "Resume runtime node"
        if ($response.data.draining -ne $false -or "$($response.data.operatorId)" -ne $Username) {
            throw "unexpected resume response"
        }
        Assert-AdmissionAudit -Draining $false
        $registration = Get-Registration $RemoteNodeId
        if ($registration.operatorDraining -ne $false -or
            $registration.effectiveAdmissionAvailable -ne $true -or
            "$($registration.effectiveAdmissionStatus)" -ne "AVAILABLE") {
            throw "worker admission did not recover after resume"
        }
        $override = Invoke-PostgresScalar "SELECT draining, operator_id FROM sa_sandbox_runtime_node_admission_override WHERE node_id='$RemoteNodeId';"
        if ($override -ne "f`t$Username") { throw "resume override was not persisted: $override" }
    } | Out-Null

    Test-Step "Restore automatic placement on the less-loaded worker" {
        Wait-NodeLoads -MinimumLocal 1 -ExpectedRemote 0
        $created = New-SandboxSession
        $script:resumedAutomaticSessionId = "$($created.sessionId)"
        Assert-SafeId $script:resumedAutomaticSessionId "resumed automatic session id"
        if ("$($created.status)" -ne "CREATED" -or "$($created.runtimeNodeId)" -ne $RemoteNodeId) {
            throw "automatic placement did not return to the resumed worker"
        }
        $response = Invoke-Json -Method POST `
            -Path "/api/sandbox/sessions/$resumedAutomaticSessionId/execute" -Headers $headers -Body @{
                input = "print('$Marker-resumed-worker')"
                networkRequested = $false
                requestedHosts = @()
            }
        Assert-ApiOk $response "Execute resumed worker session"
        if ("$($response.data.execution.status)" -ne "SUCCEEDED" -or
            "$($response.data.execution.resultSummary)" -notlike "*$Marker-resumed-worker*") {
            throw "resumed worker execution did not succeed"
        }
        Close-SandboxSession $script:resumedAutomaticSessionId
        $script:resumedAutomaticSessionId = $null
    } | Out-Null

    Test-Step "Close local load and require a clean runtime state" {
        Close-SandboxSession $localLoadSessionId
        $script:localLoadSessionId = $null
        $deadline = (Get-Date).AddSeconds(50)
        do {
            $state = Invoke-PostgresScalar @"
SELECT
  (SELECT COUNT(*) FROM sa_sandbox_session WHERE run_id='$runId' AND status NOT IN ('SUCCEEDED','FAILED','TIMED_OUT','CANCELLED')),
  (SELECT COUNT(*) FROM sa_sandbox_runtime_capacity_reservation),
  (SELECT active_session_count FROM sa_sandbox_runtime_node WHERE node_id='$LocalNodeId'),
  (SELECT active_session_count FROM sa_sandbox_runtime_node WHERE node_id='$RemoteNodeId'),
  (SELECT draining FROM sa_sandbox_runtime_node_admission_override WHERE node_id='$RemoteNodeId');
"@
            if ($state -eq "0`t0`t0`t0`tf") { break }
            Start-Sleep -Seconds 2
        } while ((Get-Date) -lt $deadline)
        if ($state -ne "0`t0`t0`t0`tf") { throw "runtime state did not converge after cleanup: $state" }
        $managed = @(& docker ps -a --format '{{.Names}}' --filter 'name=^seahorse-sandbox-')
        if ($managed.Count -ne 0) { throw "managed sandbox containers remain: $($managed -join ',')" }
    } | Out-Null
} catch {
    $failure = $_
} finally {
    if ($headers) {
        try {
            Invoke-Json -Method POST `
                -Path "/api/admin/sandbox/runtime/registrations/$RemoteNodeId/resume" -Headers $headers | Out-Null
        } catch {
            try {
                & docker exec $PostgresContainer psql -U seahorse -d seahorse -v ON_ERROR_STOP=1 `
                    -c "UPDATE sa_sandbox_runtime_node_admission_override SET draining=FALSE, operator_id='e2e-cleanup', updated_at=CURRENT_TIMESTAMP WHERE node_id='$RemoteNodeId';" | Out-Null
            } catch {
                if (-not $failure) { $failure = $_ }
            }
        }

        $cleanupIds = @(
            $existingWorkerSessionId,
            $drainedAutomaticSessionId,
            $resumedAutomaticSessionId,
            $localLoadSessionId
        ) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique
        foreach ($cleanupId in $cleanupIds) {
            try { Close-SandboxSession $cleanupId } catch { if (-not $failure) { $failure = $_ } }
        }

        if ($runId -and $runId -match '^[A-Za-z0-9._-]{1,128}$') {
            try {
                $activeIds = @(& docker exec $PostgresContainer psql -U seahorse -d seahorse -At -c "SELECT session_id FROM sa_sandbox_session WHERE run_id='$runId' AND status NOT IN ('SUCCEEDED','FAILED','TIMED_OUT','CANCELLED');")
                foreach ($activeId in @($activeIds | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })) {
                    try { Close-SandboxSession "$activeId" } catch { if (-not $failure) { $failure = $_ } }
                }
            } catch {
                if (-not $failure) { $failure = $_ }
            }
        }
    }
}

Write-Host "`nSummary: $passed / $total passed, $failed failed" -ForegroundColor Cyan
Write-Host "Coordinator: $BaseUrl"
Write-Host "Worker: $WorkerBaseUrl"
Write-Host "Run: $runId"
if ($failure) {
    Write-Error $failure.Exception.Message
    exit 1
}
if ($failed -gt 0) { exit 1 }
