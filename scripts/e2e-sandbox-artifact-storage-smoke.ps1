param(
    [string]$BaseUrl = "http://127.0.0.1:9090",
    [string]$Username = "admin",
    [string]$Password = "admin123",
    [string]$Marker = "seahorse-sandbox-artifact-storage-smoke",
    [string]$PostgresContainer = "seahorse-postgres",
    [string]$PostgresUser = "seahorse",
    [string]$PostgresDatabase = "seahorse",
    [string]$BackendContainer = "seahorse-backend",
    [string]$StorageRoot = "/app/seahorse-agent-storage",
    [string]$SandboxWorkspaceRoot = "/var/lib/seahorse-sandbox",
    [string]$ExpectedObjectUriPrefix = "local://sandbox-artifacts/",
    [int]$ExpectedRuntimeActiveSessionLimit = 0,
    [long]$ExpectedWorkspaceMinFreeBytes = 0,
    [long]$KernelRunProfileId = -9101,
    [switch]$VerifyCapacityAdmission,
    [switch]$VerifyWorkspaceDiskAdmission,
    [switch]$UseScheduledSweep,
    [int]$ScheduledSweepWaitSeconds = 45,
    [switch]$VerifyExternalVirusScanner,
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
        [int]$ExpectedStatus = 200,
        [switch]$QuietErrors
    )

    $bodyText = $null
    if ($null -ne $Body) {
        $bodyText = if ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Depth 20 -Compress }
    }

    $tempBodyFile = $null
    $silentArg = if ($QuietErrors) { "-s" } else { "-sS" }
    $args = @($silentArg, "-w", "`n%{http_code}", "-X", $Method, "$BaseUrl$Path")
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

function Invoke-Text {
    param(
        [string]$Method,
        [string]$Path,
        [hashtable]$Headers = @{},
        [int]$ExpectedStatus = 200
    )

    $args = @("-sS", "-w", "`n%{http_code}", "-X", $Method, "$BaseUrl$Path")
    foreach ($key in $Headers.Keys) {
        $args += @("-H", "${key}: $($Headers[$key])")
    }

    $raw = & curl.exe @args
    if ($LASTEXITCODE -ne 0) {
        throw "curl exited with $LASTEXITCODE for $Method $Path"
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
    return $content
}

function Invoke-BinaryFile {
    param(
        [string]$Method,
        [string]$Path,
        [hashtable]$Headers = @{},
        [string]$OutputPath,
        [int]$ExpectedStatus = 200
    )

    $args = @("-sS", "-w", "`n%{http_code}", "-X", $Method, "$BaseUrl$Path", "-o", $OutputPath)
    foreach ($key in $Headers.Keys) {
        $args += @("-H", "${key}: $($Headers[$key])")
    }

    $raw = & curl.exe @args
    if ($LASTEXITCODE -ne 0) {
        throw "curl exited with $LASTEXITCODE for $Method $Path"
    }
    $lines = @($raw)
    if ($lines.Count -eq 0) {
        throw "empty curl status output for $Method $Path"
    }
    $status = [int]$lines[-1]
    if ($status -ne $ExpectedStatus) {
        $body = if (Test-Path -LiteralPath $OutputPath) { Get-Content -Raw -LiteralPath $OutputPath -ErrorAction SilentlyContinue } else { "" }
        throw "Expected HTTP $ExpectedStatus but got $status for $Method $Path body=$body"
    }
}

function Assert-ApiOk {
    param([object]$Response, [string]$Name)
    if ($null -eq $Response -or "$($Response.code)" -ne "0") {
        throw "$Name API error: $($Response | ConvertTo-Json -Depth 20 -Compress)"
    }
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
        decisionComment = "Allow sandbox python artifact storage smoke test"
    }
    Assert-ApiOk $approved "Approve $Name"
    if ("$($approved.data.status)" -ne "APPROVED") {
        throw "$Name approval was not approved: $($approved.data | ConvertTo-Json -Depth 20 -Compress)"
    }

    $retry = Invoke-Json -Method POST -Path "/api/tools/sandbox_python/invoke" -Headers $Headers -Body $Body
    Assert-ApiOk $retry "Retry $Name after approval"
    return $retry
}

function New-RealAgentRunId {
    param(
        [hashtable]$Headers,
        [string]$Marker,
        [long]$RunProfileId
    )

    $created = Invoke-Json -Method POST -Path "/api/conversations" -Headers $Headers
    Assert-ApiOk $created "Create artifact storage smoke conversation"
    if (-not $created.data) {
        throw "Create conversation response did not include id"
    }
    $conversationId = "$($created.data)"
    $question = "Sandbox artifact storage smoke $Marker. Reply with one short sentence."
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
        throw "Agent run was not persisted before sandbox_python invocation: $runId"
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
            $health = Invoke-Json -Method GET -Path "/actuator/health" -QuietErrors
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

function Invoke-PostgresNonQuery {
    param([string]$Sql)
    & docker exec $PostgresContainer psql -U $PostgresUser -d $PostgresDatabase -q -c $Sql | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "psql exited with $LASTEXITCODE for SQL: $Sql"
    }
}

function Get-SandboxArtifactSessionId {
    param([string]$ArtifactId)
    $safeArtifactId = $ArtifactId.Replace("'", "''")
    return Invoke-PostgresScalar "SELECT session_id FROM sa_sandbox_artifact WHERE artifact_id = '$safeArtifactId';"
}

function Get-LatestSandboxSessionIdForRun {
    param([string]$RunId)
    $safeRunId = $RunId.Replace("'", "''")
    return Invoke-PostgresScalar "SELECT session_id FROM sa_sandbox_session WHERE run_id = '$safeRunId' ORDER BY created_at DESC LIMIT 1;"
}

function Remove-DockerContainerBestEffort {
    param([string]$Name)
    try {
        & docker rm -f $Name 2>$null | Out-Null
    } catch {
        return
    }
}

function Test-DockerContainerExists {
    param([string]$Name)

    $names = & docker ps -a --format "{{.Names}}"
    if ($LASTEXITCODE -ne 0) {
        throw "docker ps exited with $LASTEXITCODE while checking container $Name"
    }
    return (@($names | Where-Object { "$_" -eq $Name }).Count -gt 0)
}

function Wait-ForSandboxSessionTimedOut {
    param(
        [string]$SessionId,
        [int]$TimeoutSeconds = 45
    )
    $safeSessionId = $SessionId.Replace("'", "''")
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $lastRow = ""
    while ((Get-Date) -lt $deadline) {
        $row = Invoke-PostgresScalar "SELECT status, reason_code FROM sa_sandbox_session WHERE session_id = '$safeSessionId';"
        $lastRow = $row
        $parts = $row -split "`t"
        if ($parts.Count -eq 2 -and $parts[0] -eq "TIMED_OUT" -and $parts[1] -eq "RUNTIME_TIMED_OUT") {
            return $parts
        }
        Start-Sleep -Seconds 2
    }
    throw "Timed out waiting for scheduled sweep to mark session $SessionId as TIMED_OUT; last row: $lastRow"
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

    if ($VerifyWorkspaceDiskAdmission) {
        Test-Step "Inspect low-disk sandbox runtime health" {
            if ($ExpectedWorkspaceMinFreeBytes -le 0) {
                throw "Workspace disk admission verification requires ExpectedWorkspaceMinFreeBytes > 0"
            }
            $response = Invoke-Json -Method GET -Path "/api/sandbox/runtime/health" -Headers $headers
            Assert-ApiOk $response "Inspect low-disk sandbox runtime health"
            if ("$($response.data.runtime)" -ne "container") {
                throw "Expected container runtime health but got: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
            }
            if ("$($response.data.engine)" -ne "docker") {
                throw "Expected docker engine but got: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
            }
            if ($response.data.engineAvailable -ne $true -or $response.data.workspaceAvailable -ne $true) {
                throw "Expected available engine/workspace for low-disk admission smoke: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
            }
            if ([long]$response.data.workspaceMinFreeBytes -ne $ExpectedWorkspaceMinFreeBytes) {
                throw "Expected workspaceMinFreeBytes=${ExpectedWorkspaceMinFreeBytes}: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
            }
            if ($response.data.workspaceDiskAvailable -ne $false) {
                throw "Expected workspaceDiskAvailable=false: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
            }
            if ("$($response.data.workspaceDiskStatus)" -ne "LOW") {
                throw "Expected workspaceDiskStatus=LOW: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
            }
            if ("$($response.data.status)" -ne "DEGRADED") {
                throw "Expected low-disk runtime health status=DEGRADED: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
            }
            if ($response.data.activeSessionCapacityAvailable -ne $true) {
                throw "Expected activeSessionCapacityAvailable=true for low-disk admission smoke: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
            }
            $healthJson = $response.data | ConvertTo-Json -Depth 20 -Compress
            if ($healthJson -match [regex]::Escape($SandboxWorkspaceRoot)) {
                throw "Sandbox runtime health leaked workspace root path: $healthJson"
            }
        } | Out-Null

        Test-Step "Reject sandbox session when workspace disk threshold is not met" {
            $rejected = Invoke-Json -Method POST -Path "/api/sandbox/sessions" -Headers $headers -Body @{
                tenantId = "default"
                runId = "sandbox-disk-admission-rejected-run-$suffix"
                runtimeType = "CODE_INTERPRETER"
                networkRequested = $false
                requestedHosts = @()
            }
            Assert-ApiOk $rejected "Create sandbox session below workspace disk threshold"
            if ("$($rejected.data.status)" -ne "FAILED") {
                throw "Expected low-disk session status FAILED: $($rejected.data | ConvertTo-Json -Depth 20 -Compress)"
            }
            if ("$($rejected.data.reasonCode)" -ne "RUNTIME_WORKSPACE_DISK_LOW") {
                throw "Expected low-disk reasonCode RUNTIME_WORKSPACE_DISK_LOW: $($rejected.data | ConvertTo-Json -Depth 20 -Compress)"
            }

            $safeRejectedSessionId = "$($rejected.data.sessionId)".Replace("'", "''")
            $row = Invoke-PostgresScalar "SELECT status, reason_code FROM sa_sandbox_session WHERE session_id = '$safeRejectedSessionId';"
            $parts = $row -split "`t"
            if ($parts.Count -ne 2) {
                throw "Unexpected rejected low-disk session DB row: $row"
            }
            if ($parts[0] -ne "FAILED") {
                throw "Expected DB status FAILED for low-disk session but got '$($parts[0])'"
            }
            if ($parts[1] -ne "RUNTIME_WORKSPACE_DISK_LOW") {
                throw "Expected DB reason_code RUNTIME_WORKSPACE_DISK_LOW but got '$($parts[1])'"
            }

            if ("$($rejected.data.sessionId)".Contains("'")) {
                throw "Cannot safely shell-quote rejected low-disk session id"
            }
            & docker exec $BackendContainer sh -lc "test ! -d '$SandboxWorkspaceRoot/$($rejected.data.sessionId)'"
            if ($LASTEXITCODE -ne 0) {
                throw "Rejected low-disk session unexpectedly created workspace $SandboxWorkspaceRoot/$($rejected.data.sessionId)"
            }
        } | Out-Null

        Write-Host "`nSummary: $passed / $total passed, $failed failed" -ForegroundColor Cyan
        Write-Host "Backend: $BaseUrl"
        if ($failed -gt 0) { exit 1 }
        return
    }

    $smokeRun = Test-Step "Create real agent run for governed sandbox artifact binding" {
        New-RealAgentRunId -Headers $headers -Marker $Marker -RunProfileId $KernelRunProfileId
    }
    if (-not $smokeRun) { exit 1 }

    $runId = "$($smokeRun.RunId)"
    $toolCallId = "sandbox-artifact-storage-call-$suffix"
    $escapedMarker = $Marker.Replace("\", "\\").Replace("'", "\'")
    $code = "from pathlib import Path`nPath('answer-storage.txt').write_text('artifact $escapedMarker', encoding='utf-8')`nprint('$escapedMarker')"

    $observation = Test-Step "Invoke sandbox_python and capture artifact metadata" {
        $response = Invoke-SandboxPythonTool -Headers $headers -Name "Invoke sandbox_python" -Body @{
            runId = $runId
            stepId = "sandbox-artifact-storage-step-$suffix"
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
        if ($response.data.success -ne $true) {
            throw "sandbox_python failed: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $content = "$($response.data.content)"
        if ($content -notlike "*$Marker*") {
            throw "sandbox_python content did not contain marker '$Marker': $content"
        }
        $parsed = $content | ConvertFrom-Json
        $artifacts = @($parsed.artifacts)
        if ($artifacts.Count -lt 1) {
            throw "sandbox_python observation did not include artifacts: $content"
        }
        if (-not $artifacts[0].artifactId) {
            throw "artifact metadata did not include artifactId: $content"
        }
        $parsed
    }
    if (-not $observation) { exit 1 }

    $artifactId = "$(@($observation.artifacts)[0].artifactId)"
    $objectUri = Test-Step "Verify persisted sandbox artifact uses object storage URI" {
        $safeArtifactId = $artifactId.Replace("'", "''")
        $row = Invoke-PostgresScalar "SELECT session_id, object_uri, scan_status, sensitivity, scan_summary, redaction_summary_json FROM sa_sandbox_artifact WHERE artifact_id = '$safeArtifactId';"
        $parts = $row -split "`t"
        if ($parts.Count -ne 6) {
            throw "Unexpected sa_sandbox_artifact row: $row"
        }
        if ($parts[1] -like "file:*") {
            throw "sandbox artifact still points at file URI: $($parts[1])"
        }
        if ($ExpectedObjectUriPrefix -and $parts[1] -notlike "$ExpectedObjectUriPrefix*") {
            throw "Expected object_uri prefix '$ExpectedObjectUriPrefix' but got '$($parts[1])'"
        }
        if ($parts[2] -ne "CLEAN") {
            throw "Expected CLEAN scan_status but got '$($parts[2])'"
        }
        if ($parts[3] -ne "INTERNAL") {
            throw "Expected INTERNAL sensitivity but got '$($parts[3])'"
        }
        if ($parts[4] -ne "metadata scan passed") {
            throw "Expected metadata scan summary but got '$($parts[4])'"
        }
        if ($parts[5] -notlike '*"decision":"CLEAN"*' -or $parts[5] -notlike '*"contentScanned":true*') {
            throw "Expected CLEAN content-scanned redaction summary but got '$($parts[5])'"
        }
        [pscustomobject]@{ SessionId = $parts[0]; ObjectUri = $parts[1] }
    }
    if (-not $objectUri) { exit 1 }
    $sessionId = "$($objectUri.SessionId)"
    $objectUri = "$($objectUri.ObjectUri)"

    Test-Step "Verify persisted sandbox session profile, node, and TTL metadata" {
        $safeSessionId = $sessionId.Replace("'", "''")
        $row = Invoke-PostgresScalar "SELECT profile_id, runtime_node_id, (expires_at > created_at) FROM sa_sandbox_session WHERE session_id = '$safeSessionId';"
        $parts = $row -split "`t"
        if ($parts.Count -ne 3) {
            throw "Unexpected sa_sandbox_session row: $row"
        }
        if ($parts[0] -ne "python-small") {
            throw "Expected python-small profile but got '$($parts[0])'"
        }
        if ($parts[1] -ne "local-container-docker") {
            throw "Expected runtime_node_id=local-container-docker but got '$($parts[1])'"
        }
        if ($parts[2] -ne "t") {
            throw "Expected expires_at to be after created_at but got '$($parts[2])'"
        }
    } | Out-Null

    Test-Step "Verify sandbox session list includes session without storage URI" {
        $response = Invoke-Json -Method GET -Path "/api/sandbox/sessions?tenantId=default&limit=20" -Headers $headers
        Assert-ApiOk $response "List sandbox sessions"
        $matched = @($response.data | Where-Object { "$($_.sessionId)" -eq $sessionId })
        if ($matched.Count -ne 1) {
            throw "Session $sessionId not found in sandbox session API response"
        }
        if ("$($matched[0].profileId)" -ne "python-small") {
            throw "Expected sandbox session API profileId=python-small: $($matched[0] | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($matched[0].runtimeNodeId)" -ne "local-container-docker") {
            throw "Expected sandbox session API runtimeNodeId=local-container-docker: $($matched[0] | ConvertTo-Json -Depth 20 -Compress)"
        }
        if (-not "$($matched[0].expiresAt)") {
            throw "Sandbox session API did not include expiresAt: $($matched[0] | ConvertTo-Json -Depth 20 -Compress)"
        }
        $sessionJson = $matched[0] | ConvertTo-Json -Depth 20 -Compress
        if ($sessionJson -match "objectUri|object_uri|storageRef|file:|local://|s3://") {
            throw "Sandbox session API leaked storage URI fields: $sessionJson"
        }
    } | Out-Null

    Test-Step "Inspect sandbox runtime health through backend Docker socket" {
        $response = Invoke-Json -Method GET -Path "/api/sandbox/runtime/health" -Headers $headers
        Assert-ApiOk $response "Inspect sandbox runtime health"
        if ("$($response.data.runtime)" -ne "container") {
            throw "Expected container runtime health but got: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($response.data.engine)" -ne "docker") {
            throw "Expected docker engine but got: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ($response.data.engineAvailable -ne $true) {
            throw "Expected sandbox runtime engineAvailable=true: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ($response.data.workspaceAvailable -ne $true) {
            throw "Expected sandbox runtime workspaceAvailable=true: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ($null -eq $response.data.PSObject.Properties["workspaceFreeBytes"]) {
            throw "Sandbox runtime health did not include workspaceFreeBytes: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ($null -eq $response.data.PSObject.Properties["workspaceMinFreeBytes"]) {
            throw "Sandbox runtime health did not include workspaceMinFreeBytes: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $expectedWorkspaceDiskAvailable = -not $VerifyWorkspaceDiskAdmission
        if ($response.data.workspaceDiskAvailable -ne $expectedWorkspaceDiskAvailable) {
            throw "Expected sandbox runtime workspaceDiskAvailable=${expectedWorkspaceDiskAvailable}: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $workspaceFreeBytes = [long]$response.data.workspaceFreeBytes
        $workspaceMinFreeBytes = [long]$response.data.workspaceMinFreeBytes
        if ($workspaceFreeBytes -lt 0) {
            throw "Expected workspaceFreeBytes >= 0: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ($workspaceMinFreeBytes -ne $ExpectedWorkspaceMinFreeBytes) {
            throw "Expected workspaceMinFreeBytes=${ExpectedWorkspaceMinFreeBytes}: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $expectedWorkspaceDiskStatus = if ($VerifyWorkspaceDiskAdmission) { "LOW" } elseif ($ExpectedWorkspaceMinFreeBytes -gt 0) { "AVAILABLE" } else { "UNBOUNDED" }
        if ("$($response.data.workspaceDiskStatus)" -ne $expectedWorkspaceDiskStatus) {
            throw "Expected workspaceDiskStatus=${expectedWorkspaceDiskStatus}: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ($VerifyWorkspaceDiskAdmission -and "$($response.data.status)" -ne "DEGRADED") {
            throw "Expected low-disk runtime health status=DEGRADED: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ([int]$response.data.failedContainerInspectionCount -ne 0) {
            throw "Expected failedContainerInspectionCount=0: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ([int]$response.data.activeSessionLimit -ne $ExpectedRuntimeActiveSessionLimit) {
            throw "Expected activeSessionLimit=${ExpectedRuntimeActiveSessionLimit}: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ($response.data.activeSessionCapacityAvailable -ne $true) {
            throw "Expected activeSessionCapacityAvailable=true: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $activeSessionCount = [int]$response.data.activeSessionCount
        $activeSessionRemaining = [int]$response.data.activeSessionRemaining
        if ($ExpectedRuntimeActiveSessionLimit -gt 0) {
            $expectedRemaining = [Math]::Max($ExpectedRuntimeActiveSessionLimit - $activeSessionCount, 0)
            if ($activeSessionRemaining -ne $expectedRemaining) {
                throw "Expected activeSessionRemaining=${expectedRemaining}: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
            }
            if ("$($response.data.capacityStatus)" -ne "AVAILABLE") {
                throw "Expected capacityStatus=AVAILABLE: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
            }
        } else {
            if ($activeSessionRemaining -ne 0) {
                throw "Expected unbounded activeSessionRemaining=0: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
            }
            if ("$($response.data.capacityStatus)" -ne "UNBOUNDED") {
                throw "Expected capacityStatus=UNBOUNDED: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
            }
        }
        $healthJson = $response.data | ConvertTo-Json -Depth 20 -Compress
        if ($healthJson -match [regex]::Escape($SandboxWorkspaceRoot)) {
            throw "Sandbox runtime health leaked workspace root path: $healthJson"
        }
    } | Out-Null

    Test-Step "Inspect sandbox runtime node health" {
        $response = Invoke-Json -Method GET -Path "/api/sandbox/runtime/nodes" -Headers $headers
        Assert-ApiOk $response "Inspect sandbox runtime nodes"
        $nodes = @($response.data)
        if ($nodes.Count -ne 1) {
            throw "Expected one local sandbox runtime node: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $node = $nodes[0]
        if ("$($node.runtime)" -ne "container") {
            throw "Expected container runtime node but got: $($node | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($node.engine)" -ne "docker") {
            throw "Expected docker runtime node but got: $($node | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($node.nodeId)" -ne "local-container-docker") {
            throw "Expected local-container-docker node id but got: $($node | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ($node.engineAvailable -ne $true -or $node.workspaceAvailable -ne $true) {
            throw "Expected runtime node engine/workspace availability: $($node | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ($node.admissionAvailable -ne $true) {
            throw "Expected runtime node admissionAvailable=true: $($node | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($node.admissionStatus)" -notin @("AVAILABLE", "DEGRADED")) {
            throw "Expected runtime node admissionStatus AVAILABLE or DEGRADED: $($node | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ([int]$node.activeSessionLimit -ne $ExpectedRuntimeActiveSessionLimit) {
            throw "Expected runtime node activeSessionLimit=${ExpectedRuntimeActiveSessionLimit}: $($node | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ($node.workspaceDiskAvailable -ne $true) {
            throw "Expected runtime node workspaceDiskAvailable=true: $($node | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ($null -eq $node.PSObject.Properties["workspaceFreeBytes"] -or $null -eq $node.PSObject.Properties["workspaceMinFreeBytes"]) {
            throw "Runtime node health did not include workspace disk fields: $($node | ConvertTo-Json -Depth 20 -Compress)"
        }
        $nodeJson = $node | ConvertTo-Json -Depth 20 -Compress
        if ($nodeJson -match [regex]::Escape($SandboxWorkspaceRoot)) {
            throw "Sandbox runtime node health leaked workspace root path: $nodeJson"
        }
    } | Out-Null

    Test-Step "Inspect sandbox runtime governance profiles" {
        $response = Invoke-Json -Method GET -Path "/api/sandbox/runtime/profiles" -Headers $headers
        Assert-ApiOk $response "Inspect sandbox runtime profiles"
        if ("$($response.data.defaultNetworkPolicy)" -notin @("DENY_ALL", "ALLOWLISTED")) {
            throw "Expected defaultNetworkPolicy DENY_ALL or ALLOWLISTED: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ([int]$response.data.defaultTtlSeconds -ne 3600) {
            throw "Expected defaultTtlSeconds=3600: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $profiles = @($response.data.profiles)
        if ($profiles.Count -ne 4) {
            throw "Expected 4 runtime profiles: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $pythonProfile = @($profiles | Where-Object { "$($_.runtimeType)" -eq "CODE_INTERPRETER" -and "$($_.profileId)" -eq "python-small" })
        if ($pythonProfile.Count -ne 1 -or $pythonProfile[0].supportedByContainerRuntime -ne $true -or "$($pythonProfile[0].status)" -ne "SUPPORTED" -or $pythonProfile[0].networkAllowed -ne $false) {
            throw "Expected supported python-small no-network profile: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $fileProfile = @($profiles | Where-Object { "$($_.runtimeType)" -eq "FILE_CONVERSION" -and "$($_.profileId)" -eq "file-conversion" })
        if ($fileProfile.Count -ne 1 -or $fileProfile[0].supportedByContainerRuntime -ne $true -or "$($fileProfile[0].status)" -ne "SUPPORTED" -or $fileProfile[0].networkAllowed -ne $false) {
            throw "Expected supported file-conversion no-network profile: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $browserProfile = @($profiles | Where-Object { "$($_.runtimeType)" -eq "BROWSER_AUTOMATION" -and "$($_.profileId)" -eq "browser-readonly" })
        if ($browserProfile.Count -ne 1 -or $browserProfile[0].supportedByContainerRuntime -ne $true -or "$($browserProfile[0].status)" -ne "SUPPORTED" -or $browserProfile[0].networkAllowed -ne $false) {
            throw "Expected supported browser-readonly no-network profile: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $shellProfile = @($profiles | Where-Object { "$($_.runtimeType)" -eq "SHELL" -and "$($_.profileId)" -eq "shell-restricted" })
        if ($shellProfile.Count -ne 1 -or $shellProfile[0].supportedByContainerRuntime -ne $false -or "$($shellProfile[0].status)" -ne "PLANNED" -or $shellProfile[0].networkAllowed -ne $false) {
            throw "Expected planned shell-restricted no-network profile: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $profilesJson = $response.data | ConvertTo-Json -Depth 20 -Compress
        if ($profilesJson -match [regex]::Escape($SandboxWorkspaceRoot)) {
            throw "Sandbox runtime profiles leaked workspace root path: $profilesJson"
        }
    } | Out-Null

    Test-Step "Inspect sandbox artifact scanner policy" {
        $response = Invoke-Json -Method GET -Path "/api/sandbox/runtime/artifact-scanner-policy" -Headers $headers
        Assert-ApiOk $response "Inspect sandbox artifact scanner policy"
        $expectedScannerId = if ($VerifyExternalVirusScanner) { "clamav-plus-local-bounded" } else { "default-local-bounded" }
        $expectedScannerMode = if ($VerifyExternalVirusScanner) { "LOCAL_BOUNDED_AND_EXTERNAL_CLAMAV" } else { "LOCAL_METADATA_AND_BOUNDED_CONTENT" }
        if ("$($response.data.scannerId)" -ne $expectedScannerId) {
            throw "Expected $expectedScannerId scanner policy: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($response.data.scannerMode)" -ne $expectedScannerMode) {
            throw "Expected $expectedScannerMode scanner mode: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ($response.data.failClosed -ne $true -or $response.data.rawFindingValuesPersisted -ne $false) {
            throw "Expected fail-closed value-free scanner policy: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $scannerWindowMatches = [int]$response.data.maxContentScanBytes -eq 262144 `
            -and [int]$response.data.maxBinarySignatureScanBytes -eq 262144 `
            -and [int]$response.data.maxArchiveScanEntries -eq 128 `
            -and [int]$response.data.maxArchiveEntryScanBytes -eq 262144
        if (-not $scannerWindowMatches) {
            throw "Expected scanner byte/entry limits: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ([int64]$response.data.maxCompressedArchiveDecompressedBytes -ne 33554432) {
            throw "Expected scanner compressed archive decompressed byte limit: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $downloadOnlyMediaTypes = @($response.data.downloadOnlyMediaTypes)
        if ($downloadOnlyMediaTypes -notcontains "application/gzip" -or $downloadOnlyMediaTypes -notcontains "application/x-gzip" -or $downloadOnlyMediaTypes -notcontains "application/zip" -or $downloadOnlyMediaTypes -notcontains "application/x-tar" -or $downloadOnlyMediaTypes -notcontains "video/webm") {
            throw "Expected governed download-only media types in scanner policy: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $blockedCategories = @($response.data.blockedCategories)
        if ($blockedCategories -notcontains "OFFICE_MACRO" -or $blockedCategories -notcontains "PDF_ACTIVE_CONTENT") {
            throw "Expected scanner policy blocked categories: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $unsupportedCapabilities = @($response.data.unsupportedCapabilities)
        if ((-not $VerifyExternalVirusScanner -and $unsupportedCapabilities -notcontains "external virus scanning") -or $unsupportedCapabilities -notcontains "full PDF rendering/OCR") {
            throw "Expected scanner policy unsupported capability list: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $scannerPolicyJson = $response.data | ConvertTo-Json -Depth 20 -Compress
        if ($scannerPolicyJson -match [regex]::Escape($SandboxWorkspaceRoot)) {
            throw "Sandbox artifact scanner policy leaked workspace root path: $scannerPolicyJson"
        }
    } | Out-Null

    Test-Step "Inspect sandbox artifact scanner health" {
        $response = Invoke-Json -Method GET -Path "/api/sandbox/runtime/artifact-scanner-health" -Headers $headers
        Assert-ApiOk $response "Inspect sandbox artifact scanner health"
        $expectedScannerId = if ($VerifyExternalVirusScanner) { "clamav-plus-local-bounded" } else { "default-local-bounded" }
        if ("$($response.data.scannerId)" -ne $expectedScannerId -or "$($response.data.status)" -ne "AVAILABLE" -or $response.data.available -ne $true) {
            throw "Expected available $expectedScannerId scanner health: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ($response.data.externalEngine -ne $VerifyExternalVirusScanner) {
            throw "Unexpected externalEngine scanner health posture: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $scannerHealthJson = $response.data | ConvertTo-Json -Depth 20 -Compress
        if ($scannerHealthJson -match "clamav:|3310|$SandboxWorkspaceRoot") {
            throw "Sandbox artifact scanner health leaked engine or workspace details: $scannerHealthJson"
        }
    } | Out-Null

    if ($VerifyExternalVirusScanner) {
        $virusMarker = "external scanner artifact"
        $virusCode = @'
from pathlib import Path
import base64
Path("external-clean.txt").write_text("clean external scanner artifact", encoding="utf-8")
Path("external-infected.txt").write_bytes(base64.b64decode("U0VBSE9SU0UtQ0xBTUFWLUUyRS1NQVJLRVI="))
print("external scanner artifact")
'@
        $virusObservation = Test-Step "Invoke sandbox_python with clean and malware-signature artifacts" {
            $response = Invoke-SandboxPythonTool -Headers $headers -Name "Invoke sandbox_python external virus artifacts" -Body @{
                runId = $runId
                stepId = "sandbox-artifact-virus-step-$suffix"
                toolCallId = "sandbox-artifact-virus-call-$suffix"
                agentId = "legacy-react-agent"
                tenantId = "default"
                userId = "$($login.data.userId)"
                agentIdentityId = "$($login.data.userId)"
                arguments = @{ code = $virusCode }
                resourceRefs = @{}
                idempotencyKey = "${runId}:sandbox-artifact-virus-call-$suffix"
                allowedToolIds = @("sandbox_python")
            }
            if ($response.data.success -ne $true) {
                throw "sandbox_python external virus invocation failed: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
            }
            $content = "$($response.data.content)"
            if ($content -notlike "*$virusMarker*" -or $content -match "EICAR|X5O!P%@") {
                throw "External virus observation did not preserve marker or leaked test content"
            }
            $parsed = $content | ConvertFrom-Json
            if (@($parsed.artifacts).Count -ne 1 -or "$(@($parsed.artifacts)[0].mediaType)" -ne "text/plain") {
                throw "Expected only the clean external-scanned artifact to be prompt-visible"
            }
            $parsed
        }
        if (-not $virusObservation) { exit 1 }

        $virusSessionId = Get-LatestSandboxSessionIdForRun -RunId $runId
        $virusArtifactId = Test-Step "Verify EICAR artifact is blocked before object storage" {
            $safeVirusSessionId = $virusSessionId.Replace("'", "''")
            $row = Invoke-PostgresScalar "SELECT artifact_id, object_uri, scan_status, sensitivity, scan_summary, redaction_summary_json FROM sa_sandbox_artifact WHERE session_id = '$safeVirusSessionId' AND object_uri LIKE '%external-infected.txt' ORDER BY created_at DESC LIMIT 1;"
            $parts = $row -split "`t"
            if ($parts.Count -ne 6) {
                throw "Unexpected external virus artifact row"
            }
            if ($parts[1] -like "$ExpectedObjectUriPrefix*" -or $parts[2] -ne "BLOCKED" -or $parts[3] -ne "CONFIDENTIAL" -or $parts[4] -ne "external virus scan blocked artifact") {
                throw "Expected malware-signature artifact to be blocked before object storage"
            }
            if ($parts[5] -notlike '*"MALWARE"*' -or $parts[5] -match "EICAR|X5O!P%@") {
                throw "Expected value-free malware redaction summary"
            }
            $parts[0]
        }
        if (-not $virusArtifactId) { exit 1 }

        Test-Step "Verify malware-signature artifact API remains metadata-only" {
            $detail = Invoke-Json -Method GET -Path "/api/sandbox/artifacts/$virusArtifactId" -Headers $headers
            Assert-ApiOk $detail "Get external virus artifact detail"
            if ($detail.data.downloadable -ne $false -or $detail.data.promptVisible -ne $false -or "$($detail.data.scanStatus)" -ne "BLOCKED") {
                throw "Expected malware-signature artifact to be non-downloadable and prompt-hidden"
            }
            $detailJson = $detail.data | ConvertTo-Json -Depth 20 -Compress
            if ($detailJson -notmatch "MALWARE" -or $detailJson -match "objectUri|object_uri|storageRef|file://|local://|s3://|EICAR|X5O!P%@") {
                throw "External virus artifact API leaked storage or scanner finding values"
            }
        } | Out-Null
    }

    $expiredSweepStepName = if ($UseScheduledSweep) {
        "Wait for scheduled sandbox session sweep as TIMED_OUT"
    } else {
        "Sweep expired sandbox session as TIMED_OUT"
    }

    Test-Step $expiredSweepStepName {
        $expiredRunId = $runId
        $create = Invoke-Json -Method POST -Path "/api/sandbox/sessions" -Headers $headers -Body @{
            tenantId = "default"
            runId = $expiredRunId
            runtimeType = "CODE_INTERPRETER"
            networkRequested = $false
            requestedHosts = @()
        }
        Assert-ApiOk $create "Create expired sweep sandbox session"
        $expiredSessionId = "$($create.data.sessionId)"
        if (-not $expiredSessionId) {
            throw "Create sandbox session response did not include sessionId"
        }
        $safeExpiredSessionId = $expiredSessionId.Replace("'", "''")
        Invoke-PostgresNonQuery "UPDATE sa_sandbox_session SET created_at = now() - interval '2 hours', expires_at = now() - interval '1 hour', updated_at = now() - interval '2 hours' WHERE session_id = '$safeExpiredSessionId';"

        if ($UseScheduledSweep) {
            Wait-ForSandboxSessionTimedOut -SessionId $expiredSessionId -TimeoutSeconds $ScheduledSweepWaitSeconds | Out-Null
            $response = Invoke-Json -Method GET -Path "/api/sandbox/sessions?tenantId=default&limit=20" -Headers $headers
            Assert-ApiOk $response "List sandbox sessions after scheduled sweep"
            $matched = @($response.data | Where-Object { "$($_.sessionId)" -eq $expiredSessionId })
            if ($matched.Count -ne 1) {
                throw "Scheduled-swept session $expiredSessionId not found in sandbox session API response"
            }
            if ("$($matched[0].status)" -ne "TIMED_OUT") {
                throw "Expected scheduled-swept session status TIMED_OUT: $($matched[0] | ConvertTo-Json -Depth 20 -Compress)"
            }
            return
        }

        $sweep = Invoke-Json -Method POST -Path "/api/sandbox/sessions/expired:sweep?tenantId=default&limit=20" -Headers $headers
        Assert-ApiOk $sweep "Sweep expired sandbox sessions"
        if ([int]$sweep.data.closedCount -lt 1) {
            throw "Expected sweep closedCount >= 1: $($sweep.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ([int]$sweep.data.failedCount -ne 0) {
            throw "Expected sweep failedCount=0: $($sweep.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $closed = @($sweep.data.closedSessions | Where-Object { "$($_.sessionId)" -eq $expiredSessionId })
        if ($closed.Count -ne 1) {
            throw "Expired session $expiredSessionId not found in sweep closedSessions: $($sweep.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($closed[0].status)" -ne "TIMED_OUT") {
            throw "Expected sweep closed session status TIMED_OUT: $($closed[0] | ConvertTo-Json -Depth 20 -Compress)"
        }
        $row = Invoke-PostgresScalar "SELECT status, reason_code FROM sa_sandbox_session WHERE session_id = '$safeExpiredSessionId';"
        $parts = $row -split "`t"
        if ($parts.Count -ne 2) {
            throw "Unexpected expired session DB row: $row"
        }
        if ($parts[0] -ne "TIMED_OUT") {
            throw "Expected DB status TIMED_OUT after sweep but got '$($parts[0])'"
        }
        if ($parts[1] -ne "RUNTIME_TIMED_OUT") {
            throw "Expected DB reason_code RUNTIME_TIMED_OUT after sweep but got '$($parts[1])'"
        }
    } | Out-Null

    Test-Step "Sweep orphaned sandbox runtime workspace while preserving active session workspace" {
        $activeRunId = $runId
        $create = Invoke-Json -Method POST -Path "/api/sandbox/sessions" -Headers $headers -Body @{
            tenantId = "default"
            runId = $activeRunId
            runtimeType = "CODE_INTERPRETER"
            networkRequested = $false
            requestedHosts = @()
        }
        Assert-ApiOk $create "Create active sandbox session for orphan runtime sweep"
        $activeSessionId = "$($create.data.sessionId)"
        if (-not $activeSessionId) {
            throw "Create active sandbox session response did not include sessionId"
        }

        $orphanName = "sandbox_container_orphan_$suffix"
        if ($activeSessionId.Contains("'") -or $orphanName.Contains("'") -or $SandboxWorkspaceRoot.Contains("'")) {
            throw "Cannot safely shell-quote sandbox workspace paths"
        }
        $activePath = "$SandboxWorkspaceRoot/$activeSessionId"
        $orphanPath = "$SandboxWorkspaceRoot/$orphanName"
        $orphanContainerName = "seahorse-sandbox-orphan-live-$suffix"
        & docker exec $BackendContainer sh -lc "test -d '$activePath' && mkdir -p '$orphanPath' && printf '%s\n' '$Marker' > '$orphanPath/orphan.txt' && touch -d '2 hours ago' '$orphanPath' '$orphanPath/orphan.txt'"
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to prepare active and orphan sandbox workspaces"
        }

        Remove-DockerContainerBestEffort -Name $orphanContainerName
        & docker run -d --name $orphanContainerName python:3.11-alpine sh -lc "sleep 300" | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to start orphan sandbox container $orphanContainerName"
        }

        try {
            $sweep = Invoke-Json -Method POST -Path "/api/sandbox/runtime/orphans:sweep" -Headers $headers
            Assert-ApiOk $sweep "Sweep orphaned sandbox runtime resources"
            if ([int]$sweep.data.failedWorkspaceCount -ne 0) {
                throw "Expected failedWorkspaceCount=0: $($sweep.data | ConvertTo-Json -Depth 20 -Compress)"
            }
            if ([int]$sweep.data.failedContainerInspectionCount -ne 0) {
                throw "Expected failedContainerInspectionCount=0: $($sweep.data | ConvertTo-Json -Depth 20 -Compress)"
            }
            $removed = @($sweep.data.removedWorkspaceNames | Where-Object { "$_" -eq $orphanName })
            if ($removed.Count -ne 1) {
                throw "Expected orphan workspace $orphanName in removedWorkspaceNames: $($sweep.data | ConvertTo-Json -Depth 20 -Compress)"
            }
            if ([int]$sweep.data.skippedActiveWorkspaceCount -lt 1) {
                throw "Expected skippedActiveWorkspaceCount >= 1: $($sweep.data | ConvertTo-Json -Depth 20 -Compress)"
            }
            if ([int]$sweep.data.inspectedContainerCount -lt 1) {
                throw "Expected inspectedContainerCount >= 1: $($sweep.data | ConvertTo-Json -Depth 20 -Compress)"
            }
            $orphanContainers = @($sweep.data.orphanContainerNames | Where-Object { "$_" -eq $orphanContainerName })
            if ($orphanContainers.Count -ne 1) {
                throw "Expected orphan container $orphanContainerName in orphanContainerNames: $($sweep.data | ConvertTo-Json -Depth 20 -Compress)"
            }

            & docker exec $BackendContainer sh -lc "test ! -e '$orphanPath' && test -d '$activePath'"
            if ($LASTEXITCODE -ne 0) {
                throw "Orphan workspace was not removed or active workspace was deleted"
            }

            $dryRun = Invoke-Json -Method POST -Path "/api/sandbox/runtime/orphan-containers:reap?dryRun=true" -Headers $headers
            Assert-ApiOk $dryRun "Dry-run orphan sandbox runtime container reap"
            if ($dryRun.data.dryRun -ne $true) {
                throw "Expected dryRun=true for orphan container reap preview: $($dryRun.data | ConvertTo-Json -Depth 20 -Compress)"
            }
            if ([int]$dryRun.data.failedContainerInspectionCount -ne 0) {
                throw "Expected dry-run failedContainerInspectionCount=0: $($dryRun.data | ConvertTo-Json -Depth 20 -Compress)"
            }
            if ([int]$dryRun.data.failedContainerCount -ne 0) {
                throw "Expected dry-run failedContainerCount=0: $($dryRun.data | ConvertTo-Json -Depth 20 -Compress)"
            }
            if ([int]$dryRun.data.reapedContainerCount -ne 0) {
                throw "Dry-run should not reap containers: $($dryRun.data | ConvertTo-Json -Depth 20 -Compress)"
            }
            $dryRunOrphans = @($dryRun.data.orphanContainerNames | Where-Object { "$_" -eq $orphanContainerName })
            if ($dryRunOrphans.Count -ne 1) {
                throw "Expected dry-run orphan container $orphanContainerName in orphanContainerNames: $($dryRun.data | ConvertTo-Json -Depth 20 -Compress)"
            }
            if (-not (Test-DockerContainerExists -Name $orphanContainerName)) {
                throw "Dry-run unexpectedly removed orphan container $orphanContainerName"
            }

            $reap = Invoke-Json -Method POST -Path "/api/sandbox/runtime/orphan-containers:reap?dryRun=false" -Headers $headers
            Assert-ApiOk $reap "Reap orphan sandbox runtime containers"
            if ($reap.data.dryRun -ne $false) {
                throw "Expected dryRun=false for orphan container reap: $($reap.data | ConvertTo-Json -Depth 20 -Compress)"
            }
            if ([int]$reap.data.failedContainerInspectionCount -ne 0) {
                throw "Expected reap failedContainerInspectionCount=0: $($reap.data | ConvertTo-Json -Depth 20 -Compress)"
            }
            if ([int]$reap.data.failedContainerCount -ne 0) {
                throw "Expected reap failedContainerCount=0: $($reap.data | ConvertTo-Json -Depth 20 -Compress)"
            }
            $reapedContainers = @($reap.data.reapedContainerNames | Where-Object { "$_" -eq $orphanContainerName })
            if ($reapedContainers.Count -ne 1) {
                throw "Expected reaped container $orphanContainerName in reapedContainerNames: $($reap.data | ConvertTo-Json -Depth 20 -Compress)"
            }
            if (Test-DockerContainerExists -Name $orphanContainerName) {
                throw "Orphan container still exists after reap: $orphanContainerName"
            }
        } finally {
            Remove-DockerContainerBestEffort -Name $orphanContainerName
        }

        $closed = Invoke-Json -Method POST -Path "/api/sandbox/sessions/$activeSessionId/close" -Headers $headers
        Assert-ApiOk $closed "Close active sandbox session after orphan runtime sweep"
        & docker exec $BackendContainer sh -lc "test ! -e '$activePath'"
        if ($LASTEXITCODE -ne 0) {
            throw "Active sandbox workspace still exists after close: $activePath"
        }
    } | Out-Null

    Test-Step "Verify sandbox artifact API does not expose storage URI" {
        $response = Invoke-Json -Method GET -Path "/api/sandbox/sessions/$sessionId/artifacts" -Headers $headers
        Assert-ApiOk $response "List sandbox artifacts"
        $matched = @($response.data | Where-Object { "$($_.artifactId)" -eq $artifactId })
        if ($matched.Count -ne 1) {
            throw "Artifact $artifactId not found in sandbox artifact API response"
        }
        $artifactJson = $matched[0] | ConvertTo-Json -Depth 20 -Compress
        if ($artifactJson -match "objectUri|object_uri|storageRef|file:|local://|s3://") {
            throw "Sandbox artifact API leaked storage URI fields: $artifactJson"
        }
        if ("$($matched[0].scanSummary)" -ne "metadata scan passed") {
            throw "Expected sandbox artifact API scanSummary=metadata scan passed: $artifactJson"
        }
        if ("$($matched[0].redactionSummaryJson)" -notlike '*"decision":"CLEAN"*') {
            throw "Expected sandbox artifact API redactionSummaryJson decision CLEAN: $artifactJson"
        }
    } | Out-Null

    Test-Step "Verify sandbox artifact detail exposes download policy without storage URI" {
        $response = Invoke-Json -Method GET -Path "/api/sandbox/artifacts/$artifactId" -Headers $headers
        Assert-ApiOk $response "Get sandbox artifact detail"
        if ("$($response.data.artifactId)" -ne $artifactId) {
            throw "Artifact detail id mismatch: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ($response.data.promptVisible -ne $true) {
            throw "Expected promptVisible=true in artifact detail: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ($response.data.downloadable -ne $true) {
            throw "Expected downloadable=true in artifact detail: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($response.data.contentType)" -ne "text/plain") {
            throw "Expected contentType=text/plain in artifact detail: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if (-not "$($response.data.filename)") {
            throw "Artifact detail did not include filename: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($response.data.scanSummary)" -ne "metadata scan passed") {
            throw "Expected artifact detail scanSummary=metadata scan passed: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($response.data.redactionSummaryJson)" -notlike '*"decision":"CLEAN"*') {
            throw "Expected artifact detail redactionSummaryJson decision CLEAN: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $detailJson = $response.data | ConvertTo-Json -Depth 20 -Compress
        if ($detailJson -match "objectUri|object_uri|storageRef|file:|local://|s3://") {
            throw "Sandbox artifact detail leaked storage URI fields: $detailJson"
        }
    } | Out-Null

    Test-Step "Download governed sandbox artifact from object storage" {
        $content = Invoke-Text -Method GET -Path "/api/sandbox/artifacts/$artifactId/download" -Headers $headers
        if ($content -notlike "*$Marker*") {
            throw "Downloaded artifact did not contain marker '$Marker': $content"
        }
        if ($content -match "objectUri|object_uri|storageRef|file:|local://|s3://") {
            throw "Downloaded artifact body unexpectedly leaked storage metadata: $content"
        }
    } | Out-Null

    if ($objectUri.StartsWith("local://sandbox-artifacts/")) {
        Test-Step "Verify local object exists in backend storage volume" {
            $key = $objectUri.Substring("local://sandbox-artifacts/".Length)
            if ($key.Contains("'") -or $Marker.Contains("'")) {
                throw "Cannot safely shell-quote key or marker"
            }
            $path = "$StorageRoot/sandbox-artifacts/$key"
            & docker exec $BackendContainer sh -lc "test -f '$path' && grep -F -q '$Marker' '$path'"
            if ($LASTEXITCODE -ne 0) {
                throw "Stored local object not found or marker missing at $path"
            }
        } | Out-Null
    }

    $secretToken = "sk-seahorse-secret-$suffix-1234567890"
    $secretMarker = "$Marker-secret-scan"
    $secretCode = "from pathlib import Path`nPath('answer-content-scan.txt').write_text('api_key = `"$secretToken`"', encoding='utf-8')`nprint('$secretMarker')"
    $secretObservation = Test-Step "Invoke sandbox_python with content-sensitive artifact" {
        $response = Invoke-SandboxPythonTool -Headers $headers -Name "Invoke sandbox_python secret artifact" -Body @{
            runId = $runId
            stepId = "sandbox-artifact-secret-step-$suffix"
            toolCallId = "sandbox-artifact-secret-call-$suffix"
            agentId = "legacy-react-agent"
            tenantId = "default"
            userId = "$($login.data.userId)"
            agentIdentityId = "$($login.data.userId)"
            arguments = @{ code = $secretCode }
            resourceRefs = @{}
            idempotencyKey = "${runId}:sandbox-artifact-secret-call-$suffix"
            allowedToolIds = @("sandbox_python")
        }
        if ($response.data.success -ne $true) {
            throw "sandbox_python secret artifact invocation failed: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $content = "$($response.data.content)"
        if ($content -notlike "*$secretMarker*") {
            throw "sandbox_python secret artifact content did not contain marker '$secretMarker': $content"
        }
        if ($content -like "*$secretToken*") {
            throw "sandbox_python observation leaked secret content: $content"
        }
        $parsed = $content | ConvertFrom-Json
        if (@($parsed.artifacts).Count -ne 0) {
            throw "Content-sensitive artifact should not be prompt-visible: $content"
        }
        $parsed
    }
    if (-not $secretObservation) { exit 1 }

    $secretSessionId = Get-LatestSandboxSessionIdForRun -RunId $runId
    $secretArtifactId = Test-Step "Verify content-sensitive sandbox artifact is blocked before object storage" {
        $safeSecretSessionId = $secretSessionId.Replace("'", "''")
        $row = Invoke-PostgresScalar "SELECT artifact_id, object_uri, scan_status, sensitivity, scan_summary, redaction_summary_json FROM sa_sandbox_artifact WHERE session_id = '$safeSecretSessionId' ORDER BY created_at DESC LIMIT 1;"
        $parts = $row -split "`t"
        if ($parts.Count -ne 6) {
            throw "Unexpected secret artifact row: $row"
        }
        if ($parts[1] -like "$ExpectedObjectUriPrefix*") {
            throw "Content-sensitive artifact was copied to object storage: $($parts[1])"
        }
        if ($parts[2] -ne "BLOCKED") {
            throw "Expected BLOCKED scan_status for content-sensitive artifact but got '$($parts[2])'"
        }
        if ($parts[3] -ne "SECRET") {
            throw "Expected SECRET sensitivity for content-sensitive artifact but got '$($parts[3])'"
        }
        if ($parts[4] -ne "sensitive artifact content") {
            throw "Expected sensitive artifact scan summary but got '$($parts[4])'"
        }
        if ($parts[5] -notlike '*"decision":"BLOCKED"*' -or $parts[5] -notlike '*"SECRET"*') {
            throw "Expected BLOCKED SECRET redaction summary but got '$($parts[5])'"
        }
        if ($parts[5] -like "*$secretToken*") {
            throw "Redaction summary leaked secret token: $($parts[5])"
        }
        $parts[0]
    }
    if (-not $secretArtifactId) { exit 1 }

    Test-Step "Verify content-sensitive sandbox artifact API exposes only blocked metadata" {
        $response = Invoke-Json -Method GET -Path "/api/sandbox/sessions/$secretSessionId/artifacts" -Headers $headers
        Assert-ApiOk $response "List content-sensitive sandbox artifacts"
        $matched = @($response.data | Where-Object { "$($_.artifactId)" -eq $secretArtifactId })
        if ($matched.Count -ne 1) {
            throw "Content-sensitive artifact $secretArtifactId not found in sandbox artifact API response"
        }
        if ($matched[0].promptVisible -ne $false) {
            throw "Expected content-sensitive artifact promptVisible=false: $($matched[0] | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($matched[0].scanStatus)" -ne "BLOCKED") {
            throw "Expected content-sensitive artifact scanStatus=BLOCKED: $($matched[0] | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($matched[0].scanSummary)" -ne "sensitive artifact content") {
            throw "Expected content-sensitive artifact scanSummary=sensitive artifact content: $($matched[0] | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($matched[0].redactionSummaryJson)" -notlike '*"decision":"BLOCKED"*' -or "$($matched[0].redactionSummaryJson)" -notlike '*"SECRET"*') {
            throw "Expected content-sensitive artifact redactionSummaryJson BLOCKED/SECRET: $($matched[0] | ConvertTo-Json -Depth 20 -Compress)"
        }
        $artifactJson = $matched[0] | ConvertTo-Json -Depth 20 -Compress
        if ($artifactJson -match "objectUri|object_uri|storageRef|file:|local://|s3://|$secretToken") {
            throw "Content-sensitive artifact API leaked storage or secret content: $artifactJson"
        }

        $detail = Invoke-Json -Method GET -Path "/api/sandbox/artifacts/$secretArtifactId" -Headers $headers
        Assert-ApiOk $detail "Get content-sensitive sandbox artifact detail"
        if ($detail.data.downloadable -ne $false) {
            throw "Expected content-sensitive artifact downloadable=false: $($detail.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($detail.data.scanSummary)" -ne "sensitive artifact content") {
            throw "Expected content-sensitive artifact detail scanSummary=sensitive artifact content: $($detail.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($detail.data.redactionSummaryJson)" -notlike '*"decision":"BLOCKED"*' -or "$($detail.data.redactionSummaryJson)" -notlike '*"SECRET"*') {
            throw "Expected content-sensitive artifact detail redactionSummaryJson BLOCKED/SECRET: $($detail.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $detailJson = $detail.data | ConvertTo-Json -Depth 20 -Compress
        if ($detailJson -match "objectUri|object_uri|storageRef|file:|local://|s3://|$secretToken") {
            throw "Content-sensitive artifact detail leaked storage or secret content: $detailJson"
        }
    } | Out-Null

    $binaryMarker = "$Marker-binary-signature-scan"
    $binaryCode = "from pathlib import Path`nPath('active.pdf').write_bytes(b'%PDF-1.7\n1 0 obj\n<< /OpenAction 2 0 R >>\nendobj\n')`nPath('chart.png').write_bytes(b'MZ\x00\x00seahorse')`nprint('$binaryMarker')"
    $binaryObservation = Test-Step "Invoke sandbox_python with binary-signature artifacts" {
        $response = Invoke-SandboxPythonTool -Headers $headers -Name "Invoke sandbox_python binary-signature artifacts" -Body @{
            runId = $runId
            stepId = "sandbox-artifact-binary-step-$suffix"
            toolCallId = "sandbox-artifact-binary-call-$suffix"
            agentId = "legacy-react-agent"
            tenantId = "default"
            userId = "$($login.data.userId)"
            agentIdentityId = "$($login.data.userId)"
            arguments = @{ code = $binaryCode }
            resourceRefs = @{}
            idempotencyKey = "${runId}:sandbox-artifact-binary-call-$suffix"
            allowedToolIds = @("sandbox_python")
        }
        if ($response.data.success -ne $true) {
            throw "sandbox_python binary-signature invocation failed: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $content = "$($response.data.content)"
        if ($content -notlike "*$binaryMarker*") {
            throw "sandbox_python binary-signature content did not contain marker '$binaryMarker': $content"
        }
        $parsed = $content | ConvertFrom-Json
        if (@($parsed.artifacts).Count -ne 0) {
            throw "Binary-signature artifacts should not be prompt-visible: $content"
        }
        $parsed
    }
    if (-not $binaryObservation) { exit 1 }

    $binarySessionId = Get-LatestSandboxSessionIdForRun -RunId $runId
    Test-Step "Verify PDF active-content artifact is blocked before object storage" {
        $safeBinarySessionId = $binarySessionId.Replace("'", "''")
        $row = Invoke-PostgresScalar "SELECT artifact_id, object_uri, media_type, scan_status, sensitivity, scan_summary, redaction_summary_json FROM sa_sandbox_artifact WHERE session_id = '$safeBinarySessionId' AND media_type = 'application/pdf' ORDER BY created_at DESC LIMIT 1;"
        $parts = $row -split "`t"
        if ($parts.Count -ne 7) {
            throw "Unexpected PDF binary-signature artifact row: $row"
        }
        if ($parts[1] -like "$ExpectedObjectUriPrefix*") {
            throw "PDF active-content artifact was copied to object storage: $($parts[1])"
        }
        if ($parts[2] -ne "application/pdf" -or $parts[3] -ne "BLOCKED" -or $parts[4] -ne "CONFIDENTIAL") {
            throw "Expected blocked CONFIDENTIAL PDF artifact but got: $row"
        }
        if ($parts[5] -ne "pdf active content") {
            throw "Expected PDF active-content scan summary but got '$($parts[5])'"
        }
        if ($parts[6] -notlike '*"decision":"BLOCKED"*' -or $parts[6] -notlike '*"PDF_ACTIVE_CONTENT"*') {
            throw "Expected BLOCKED PDF_ACTIVE_CONTENT redaction summary but got '$($parts[6])'"
        }
        if ($parts[6] -like "*OpenAction*") {
            throw "PDF redaction summary leaked active-content marker: $($parts[6])"
        }
        $parts[0]
    } | Out-Null

    $executableArtifactId = Test-Step "Verify executable masquerading artifact is blocked before object storage" {
        $safeBinarySessionId = $binarySessionId.Replace("'", "''")
        $row = Invoke-PostgresScalar "SELECT artifact_id, object_uri, media_type, scan_status, sensitivity, scan_summary, redaction_summary_json FROM sa_sandbox_artifact WHERE session_id = '$safeBinarySessionId' AND media_type = 'image/png' ORDER BY created_at DESC LIMIT 1;"
        $parts = $row -split "`t"
        if ($parts.Count -ne 7) {
            throw "Unexpected executable masquerading artifact row: $row"
        }
        if ($parts[1] -like "$ExpectedObjectUriPrefix*") {
            throw "Executable masquerading artifact was copied to object storage: $($parts[1])"
        }
        if ($parts[2] -ne "image/png" -or $parts[3] -ne "BLOCKED" -or $parts[4] -ne "CONFIDENTIAL") {
            throw "Expected blocked CONFIDENTIAL image/png artifact but got: $row"
        }
        if ($parts[5] -ne "executable binary artifact content") {
            throw "Expected executable binary scan summary but got '$($parts[5])'"
        }
        if ($parts[6] -notlike '*"decision":"BLOCKED"*' -or $parts[6] -notlike '*"EXECUTABLE_BINARY"*') {
            throw "Expected BLOCKED EXECUTABLE_BINARY redaction summary but got '$($parts[6])'"
        }
        $parts[0]
    }
    if (-not $executableArtifactId) { exit 1 }

    Test-Step "Verify binary-signature artifact API exposes only blocked metadata" {
        $response = Invoke-Json -Method GET -Path "/api/sandbox/sessions/$binarySessionId/artifacts" -Headers $headers
        Assert-ApiOk $response "List binary-signature sandbox artifacts"
        $blockedArtifacts = @($response.data | Where-Object { "$($_.scanStatus)" -eq "BLOCKED" })
        if ($blockedArtifacts.Count -lt 2) {
            throw "Expected at least two blocked binary-signature artifacts: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $artifactJson = $response.data | ConvertTo-Json -Depth 20 -Compress
        if ($artifactJson -match "objectUri|object_uri|storageRef|file:|local://|s3://|OpenAction") {
            throw "Binary-signature artifact API leaked storage or active-content details: $artifactJson"
        }

        $detail = Invoke-Json -Method GET -Path "/api/sandbox/artifacts/$executableArtifactId" -Headers $headers
        Assert-ApiOk $detail "Get executable masquerading artifact detail"
        if ($detail.data.downloadable -ne $false -or $detail.data.promptVisible -ne $false) {
            throw "Expected executable masquerading artifact to be non-downloadable and prompt-hidden: $($detail.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($detail.data.redactionSummaryJson)" -notlike '*"EXECUTABLE_BINARY"*') {
            throw "Expected executable detail redaction summary category: $($detail.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $detailJson = $detail.data | ConvertTo-Json -Depth 20 -Compress
        if ($detailJson -match "objectUri|object_uri|storageRef|file:|local://|s3://") {
            throw "Executable masquerading detail leaked storage metadata: $detailJson"
        }
    } | Out-Null

    $archiveMarker = "$Marker-archive-introspection"
    $archiveMarkerPattern = [regex]::Escape($archiveMarker)
    $escapedArchiveMarker = $archiveMarker.Replace("\", "\\").Replace("'", "\'")
    $archiveCode = @"
import io
import gzip
import tarfile
import zipfile

class ZeroStream:
    def __init__(self, remaining):
        self.remaining = remaining

    def read(self, size=-1):
        if self.remaining <= 0:
            return b''
        if size < 0 or size > 1024 * 1024:
            size = 1024 * 1024
        size = min(size, self.remaining)
        self.remaining -= size
        return b'\0' * size

with zipfile.ZipFile('safe-bundle.zip', 'w') as archive:
    archive.writestr('docs/readme.txt', 'safe archive $escapedArchiveMarker')
with zipfile.ZipFile('unsafe-bundle.zip', 'w') as archive:
    archive.writestr('bin/payload.exe', b'MZ\x00\x00seahorse')
with zipfile.ZipFile('path-traversal-bundle.zip', 'w') as archive:
    archive.writestr('../outside.txt', 'unsafe archive path $escapedArchiveMarker')
nested_zip_buffer = io.BytesIO()
with zipfile.ZipFile(nested_zip_buffer, 'w') as nested:
    nested.writestr('docs/inner.txt', 'nested archive $escapedArchiveMarker')
with zipfile.ZipFile('nested-bundle.zip', 'w') as archive:
    archive.writestr('nested/inner.zip', nested_zip_buffer.getvalue())
safe_tar_data = b'safe tar archive $escapedArchiveMarker'
with tarfile.open('safe-bundle.tar', 'w', format=tarfile.USTAR_FORMAT) as archive:
    entry = tarfile.TarInfo('docs/readme.txt')
    entry.size = len(safe_tar_data)
    archive.addfile(entry, io.BytesIO(safe_tar_data))
unsafe_tar_data = b'MZ\x00\x00seahorse'
with tarfile.open('unsafe-bundle.tar', 'w', format=tarfile.USTAR_FORMAT) as archive:
    entry = tarfile.TarInfo('bin/payload.exe')
    entry.size = len(unsafe_tar_data)
    archive.addfile(entry, io.BytesIO(unsafe_tar_data))
path_traversal_tar_data = b'unsafe tar path $escapedArchiveMarker'
with tarfile.open('path-traversal-bundle.tar', 'w', format=tarfile.USTAR_FORMAT) as archive:
    entry = tarfile.TarInfo('../outside.txt')
    entry.size = len(path_traversal_tar_data)
    archive.addfile(entry, io.BytesIO(path_traversal_tar_data))
safe_targz_data = b'safe gzip tar archive $escapedArchiveMarker'
with tarfile.open('safe-bundle.tar.gz', 'w:gz', format=tarfile.USTAR_FORMAT) as archive:
    entry = tarfile.TarInfo('docs/readme.txt')
    entry.size = len(safe_targz_data)
    archive.addfile(entry, io.BytesIO(safe_targz_data))
unsafe_targz_data = b'MZ\x00\x00seahorse'
with tarfile.open('unsafe-bundle.tar.gz', 'w:gz', format=tarfile.USTAR_FORMAT) as archive:
    entry = tarfile.TarInfo('bin/payload.exe')
    entry.size = len(unsafe_targz_data)
    archive.addfile(entry, io.BytesIO(unsafe_targz_data))
with tarfile.open('overbudget-bundle.tar.gz', 'w:gz', format=tarfile.USTAR_FORMAT) as archive:
    entry = tarfile.TarInfo('docs/large.bin')
    entry.size = 33 * 1024 * 1024
    archive.addfile(entry, ZeroStream(entry.size))
with gzip.open('plain-bundle.gz', 'wb') as archive:
    archive.write(b'plain gzip archive $escapedArchiveMarker')
print('$escapedArchiveMarker')
"@
    $archiveObservation = Test-Step "Invoke sandbox_python with archive artifacts" {
        $response = Invoke-SandboxPythonTool -Headers $headers -Name "Invoke sandbox_python archive artifacts" -Body @{
            runId = $runId
            stepId = "sandbox-artifact-archive-step-$suffix"
            toolCallId = "sandbox-artifact-archive-call-$suffix"
            agentId = "legacy-react-agent"
            tenantId = "default"
            userId = "$($login.data.userId)"
            agentIdentityId = "$($login.data.userId)"
            arguments = @{ code = $archiveCode }
            resourceRefs = @{}
            idempotencyKey = "${runId}:sandbox-artifact-archive-call-$suffix"
            allowedToolIds = @("sandbox_python")
        }
        if ($response.data.success -ne $true) {
            throw "sandbox_python archive invocation failed: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $content = "$($response.data.content)"
        if ($content -notlike "*$archiveMarker*") {
            throw "sandbox_python archive content did not contain marker '$archiveMarker': $content"
        }
        $parsed = $content | ConvertFrom-Json
        if (@($parsed.artifacts).Count -ne 0) {
            throw "Archive artifacts should not be prompt-visible: $content"
        }
        $parsed
    }
    if (-not $archiveObservation) { exit 1 }

    $archiveSessionId = Get-LatestSandboxSessionIdForRun -RunId $runId
    $safeArchive = Test-Step "Verify clean ZIP archive is governed download-only" {
        $safeArchiveSessionId = $archiveSessionId.Replace("'", "''")
        $row = Invoke-PostgresScalar "SELECT artifact_id, object_uri, media_type, scan_status, sensitivity, scan_summary, redaction_summary_json FROM sa_sandbox_artifact WHERE session_id = '$safeArchiveSessionId' AND object_uri LIKE '%-safe-bundle.zip' ORDER BY created_at DESC LIMIT 1;"
        $parts = $row -split "`t"
        if ($parts.Count -ne 7) {
            throw "Unexpected clean ZIP artifact row: $row"
        }
        if ($ExpectedObjectUriPrefix -and $parts[1] -notlike "$ExpectedObjectUriPrefix*") {
            throw "Clean ZIP archive was not copied to governed object storage: $($parts[1])"
        }
        if ($parts[2] -ne "application/zip" -or $parts[3] -ne "CLEAN" -or $parts[4] -ne "INTERNAL") {
            throw "Expected CLEAN INTERNAL application/zip archive but got: $row"
        }
        if ($parts[5] -ne "metadata scan passed") {
            throw "Expected clean ZIP scan summary metadata scan passed but got '$($parts[5])'"
        }
        if ($parts[6] -notlike '*"decision":"CLEAN"*' -or $parts[6] -notlike '*"contentScanned":true*') {
            throw "Expected CLEAN content-scanned ZIP redaction summary but got '$($parts[6])'"
        }
        [pscustomobject]@{ ArtifactId = $parts[0]; ObjectUri = $parts[1] }
    }
    if (-not $safeArchive) { exit 1 }

    $safeTarArchive = Test-Step "Verify clean TAR archive is governed download-only" {
        $safeArchiveSessionId = $archiveSessionId.Replace("'", "''")
        $row = Invoke-PostgresScalar "SELECT artifact_id, object_uri, media_type, scan_status, sensitivity, scan_summary, redaction_summary_json FROM sa_sandbox_artifact WHERE session_id = '$safeArchiveSessionId' AND object_uri LIKE '%-safe-bundle.tar' ORDER BY created_at DESC LIMIT 1;"
        $parts = $row -split "`t"
        if ($parts.Count -ne 7) {
            throw "Unexpected clean TAR artifact row: $row"
        }
        if ($ExpectedObjectUriPrefix -and $parts[1] -notlike "$ExpectedObjectUriPrefix*") {
            throw "Clean TAR archive was not copied to governed object storage: $($parts[1])"
        }
        if ($parts[2] -ne "application/x-tar" -or $parts[3] -ne "CLEAN" -or $parts[4] -ne "INTERNAL") {
            throw "Expected CLEAN INTERNAL application/x-tar archive but got: $row"
        }
        if ($parts[5] -ne "metadata scan passed") {
            throw "Expected clean TAR scan summary metadata scan passed but got '$($parts[5])'"
        }
        if ($parts[6] -notlike '*"decision":"CLEAN"*' -or $parts[6] -notlike '*"contentScanned":true*') {
            throw "Expected CLEAN content-scanned TAR redaction summary but got '$($parts[6])'"
        }
        [pscustomobject]@{ ArtifactId = $parts[0]; ObjectUri = $parts[1] }
    }
    if (-not $safeTarArchive) { exit 1 }

    $safeGzipTarArchive = Test-Step "Verify clean TAR.GZ archive is governed download-only" {
        $safeArchiveSessionId = $archiveSessionId.Replace("'", "''")
        $row = Invoke-PostgresScalar "SELECT artifact_id, object_uri, media_type, scan_status, sensitivity, scan_summary, redaction_summary_json FROM sa_sandbox_artifact WHERE session_id = '$safeArchiveSessionId' AND object_uri LIKE '%-safe-bundle.tar.gz' ORDER BY created_at DESC LIMIT 1;"
        $parts = $row -split "`t"
        if ($parts.Count -ne 7) {
            throw "Unexpected clean TAR.GZ artifact row: $row"
        }
        if ($ExpectedObjectUriPrefix -and $parts[1] -notlike "$ExpectedObjectUriPrefix*") {
            throw "Clean TAR.GZ archive was not copied to governed object storage: $($parts[1])"
        }
        if ($parts[2] -ne "application/gzip" -or $parts[3] -ne "CLEAN" -or $parts[4] -ne "INTERNAL") {
            throw "Expected CLEAN INTERNAL application/gzip archive but got: $row"
        }
        if ($parts[5] -ne "metadata scan passed") {
            throw "Expected clean TAR.GZ scan summary metadata scan passed but got '$($parts[5])'"
        }
        if ($parts[6] -notlike '*"decision":"CLEAN"*' -or $parts[6] -notlike '*"contentScanned":true*') {
            throw "Expected CLEAN content-scanned TAR.GZ redaction summary but got '$($parts[6])'"
        }
        [pscustomobject]@{ ArtifactId = $parts[0]; ObjectUri = $parts[1] }
    }
    if (-not $safeGzipTarArchive) { exit 1 }

    Test-Step "Download governed clean ZIP archive" {
        Add-Type -AssemblyName System.IO.Compression
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $tempZip = [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), "seahorse-archive-$suffix.zip")
        try {
            Invoke-BinaryFile -Method GET -Path "/api/sandbox/artifacts/$($safeArchive.ArtifactId)/download" -Headers $headers -OutputPath $tempZip
            $archive = [System.IO.Compression.ZipFile]::OpenRead($tempZip)
            try {
                $entry = $archive.GetEntry("docs/readme.txt")
                if ($null -eq $entry) {
                    throw "Downloaded clean ZIP did not contain docs/readme.txt"
                }
                $stream = $entry.Open()
                $reader = [System.IO.StreamReader]::new($stream, [System.Text.Encoding]::UTF8)
                try {
                    $content = $reader.ReadToEnd()
                } finally {
                    $reader.Dispose()
                    $stream.Dispose()
                }
                if ($content -notlike "*$archiveMarker*") {
                    throw "Downloaded clean ZIP entry did not contain marker '$archiveMarker': $content"
                }
            } finally {
                $archive.Dispose()
            }
        } finally {
            Remove-Item -LiteralPath $tempZip -ErrorAction SilentlyContinue
        }
    } | Out-Null

    Test-Step "Download governed clean TAR archive" {
        $tempTar = [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), "seahorse-archive-$suffix.tar")
        try {
            Invoke-BinaryFile -Method GET -Path "/api/sandbox/artifacts/$($safeTarArchive.ArtifactId)/download" -Headers $headers -OutputPath $tempTar
            $content = [System.Text.Encoding]::UTF8.GetString([System.IO.File]::ReadAllBytes($tempTar))
            if (-not $content.Contains($archiveMarker)) {
                throw "Downloaded clean TAR did not contain marker '$archiveMarker'"
            }
        } finally {
            Remove-Item -LiteralPath $tempTar -ErrorAction SilentlyContinue
        }
    } | Out-Null

    Test-Step "Download governed clean TAR.GZ archive" {
        Add-Type -AssemblyName System.IO.Compression
        $tempGzipTar = [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), "seahorse-archive-$suffix.tar.gz")
        $source = $null
        $gzip = $null
        $reader = $null
        try {
            Invoke-BinaryFile -Method GET -Path "/api/sandbox/artifacts/$($safeGzipTarArchive.ArtifactId)/download" -Headers $headers -OutputPath $tempGzipTar
            $source = [System.IO.File]::OpenRead($tempGzipTar)
            $gzip = [System.IO.Compression.GZipStream]::new($source, [System.IO.Compression.CompressionMode]::Decompress)
            $reader = [System.IO.StreamReader]::new($gzip, [System.Text.Encoding]::UTF8)
            $content = $reader.ReadToEnd()
            if (-not $content.Contains($archiveMarker)) {
                throw "Downloaded clean TAR.GZ did not contain marker '$archiveMarker'"
            }
        } finally {
            if ($reader) { $reader.Dispose() }
            elseif ($gzip) { $gzip.Dispose() }
            elseif ($source) { $source.Dispose() }
            Remove-Item -LiteralPath $tempGzipTar -ErrorAction SilentlyContinue
        }
    } | Out-Null

    if ($safeArchive.ObjectUri.StartsWith("local://sandbox-artifacts/")) {
        Test-Step "Verify local clean ZIP object exists in backend storage volume" {
            $key = $safeArchive.ObjectUri.Substring("local://sandbox-artifacts/".Length)
            if ($key.Contains("'") -or $archiveMarker.Contains("'")) {
                throw "Cannot safely shell-quote archive key or marker"
            }
            $path = "$StorageRoot/sandbox-artifacts/$key"
            & docker exec $BackendContainer sh -lc "test -f '$path' && grep -F -q '$archiveMarker' '$path'"
            if ($LASTEXITCODE -ne 0) {
                throw "Stored clean ZIP object not found or marker missing at $path"
            }
        } | Out-Null
    }

    if ($safeTarArchive.ObjectUri.StartsWith("local://sandbox-artifacts/")) {
        Test-Step "Verify local clean TAR object exists in backend storage volume" {
            $key = $safeTarArchive.ObjectUri.Substring("local://sandbox-artifacts/".Length)
            if ($key.Contains("'") -or $archiveMarker.Contains("'")) {
                throw "Cannot safely shell-quote TAR archive key or marker"
            }
            $path = "$StorageRoot/sandbox-artifacts/$key"
            & docker exec $BackendContainer sh -lc "test -f '$path' && grep -F -q '$archiveMarker' '$path'"
            if ($LASTEXITCODE -ne 0) {
                throw "Stored clean TAR object not found or marker missing at $path"
            }
        } | Out-Null
    }

    if ($safeGzipTarArchive.ObjectUri.StartsWith("local://sandbox-artifacts/")) {
        Test-Step "Verify local clean TAR.GZ object exists in backend storage volume" {
            $key = $safeGzipTarArchive.ObjectUri.Substring("local://sandbox-artifacts/".Length)
            if ($key.Contains("'")) {
                throw "Cannot safely shell-quote TAR.GZ archive key"
            }
            $path = "$StorageRoot/sandbox-artifacts/$key"
            & docker exec $BackendContainer sh -lc "test -s '$path'"
            if ($LASTEXITCODE -ne 0) {
                throw "Stored clean TAR.GZ object not found at $path"
            }
        } | Out-Null
    }

    $unsafeArchiveArtifactId = Test-Step "Verify unsafe ZIP archive is blocked before object storage" {
        $safeArchiveSessionId = $archiveSessionId.Replace("'", "''")
        $row = Invoke-PostgresScalar "SELECT artifact_id, object_uri, media_type, scan_status, sensitivity, scan_summary, redaction_summary_json FROM sa_sandbox_artifact WHERE session_id = '$safeArchiveSessionId' AND object_uri LIKE '%/unsafe-bundle.zip' ORDER BY created_at DESC LIMIT 1;"
        $parts = $row -split "`t"
        if ($parts.Count -ne 7) {
            throw "Unexpected unsafe ZIP artifact row: $row"
        }
        if ($parts[1] -like "$ExpectedObjectUriPrefix*") {
            throw "Unsafe ZIP archive was copied to object storage: $($parts[1])"
        }
        if ($parts[2] -ne "application/zip" -or $parts[3] -ne "BLOCKED" -or $parts[4] -ne "CONFIDENTIAL") {
            throw "Expected BLOCKED CONFIDENTIAL application/zip archive but got: $row"
        }
        if ($parts[5] -ne "archive executable content") {
            throw "Expected unsafe ZIP scan summary archive executable content but got '$($parts[5])'"
        }
        if ($parts[6] -notlike '*"decision":"BLOCKED"*' -or $parts[6] -notlike '*"ARCHIVE_EXECUTABLE_BINARY"*') {
            throw "Expected BLOCKED ARCHIVE_EXECUTABLE_BINARY redaction summary but got '$($parts[6])'"
        }
        if ($parts[6] -like "*payload.exe*") {
            throw "Unsafe ZIP redaction summary leaked entry name: $($parts[6])"
        }
        $parts[0]
    }
    if (-not $unsafeArchiveArtifactId) { exit 1 }

    $pathTraversalArchiveArtifactId = Test-Step "Verify path-traversal ZIP archive is blocked before object storage" {
        $safeArchiveSessionId = $archiveSessionId.Replace("'", "''")
        $row = Invoke-PostgresScalar "SELECT artifact_id, object_uri, media_type, scan_status, sensitivity, scan_summary, redaction_summary_json FROM sa_sandbox_artifact WHERE session_id = '$safeArchiveSessionId' AND object_uri LIKE '%/path-traversal-bundle.zip' ORDER BY created_at DESC LIMIT 1;"
        $parts = $row -split "`t"
        if ($parts.Count -ne 7) {
            throw "Unexpected path-traversal ZIP artifact row: $row"
        }
        if ($parts[1] -like "$ExpectedObjectUriPrefix*") {
            throw "Path-traversal ZIP archive was copied to object storage: $($parts[1])"
        }
        if ($parts[2] -ne "application/zip" -or $parts[3] -ne "BLOCKED" -or $parts[4] -ne "CONFIDENTIAL") {
            throw "Expected BLOCKED CONFIDENTIAL application/zip archive but got: $row"
        }
        if ($parts[5] -ne "unsafe archive entry") {
            throw "Expected path-traversal ZIP scan summary unsafe archive entry but got '$($parts[5])'"
        }
        if ($parts[6] -notlike '*"decision":"BLOCKED"*' -or $parts[6] -notlike '*"ARCHIVE_UNSAFE_ENTRY"*') {
            throw "Expected BLOCKED ARCHIVE_UNSAFE_ENTRY ZIP redaction summary but got '$($parts[6])'"
        }
        if ($parts[6] -like "*outside.txt*") {
            throw "Path-traversal ZIP redaction summary leaked entry name: $($parts[6])"
        }
        $parts[0]
    }
    if (-not $pathTraversalArchiveArtifactId) { exit 1 }

    $nestedArchiveArtifactId = Test-Step "Verify nested ZIP archive is blocked before object storage" {
        $safeArchiveSessionId = $archiveSessionId.Replace("'", "''")
        $row = Invoke-PostgresScalar "SELECT artifact_id, object_uri, media_type, scan_status, sensitivity, scan_summary, redaction_summary_json FROM sa_sandbox_artifact WHERE session_id = '$safeArchiveSessionId' AND object_uri LIKE '%/nested-bundle.zip' ORDER BY created_at DESC LIMIT 1;"
        $parts = $row -split "`t"
        if ($parts.Count -ne 7) {
            throw "Unexpected nested ZIP artifact row: $row"
        }
        if ($parts[1] -like "$ExpectedObjectUriPrefix*") {
            throw "Nested ZIP archive was copied to object storage: $($parts[1])"
        }
        if ($parts[2] -ne "application/zip" -or $parts[3] -ne "BLOCKED" -or $parts[4] -ne "CONFIDENTIAL") {
            throw "Expected BLOCKED CONFIDENTIAL application/zip nested archive but got: $row"
        }
        if ($parts[5] -ne "nested archive content") {
            throw "Expected nested ZIP scan summary nested archive content but got '$($parts[5])'"
        }
        if ($parts[6] -notlike '*"decision":"BLOCKED"*' -or $parts[6] -notlike '*"ARCHIVE_NESTED_ARCHIVE"*') {
            throw "Expected BLOCKED ARCHIVE_NESTED_ARCHIVE redaction summary but got '$($parts[6])'"
        }
        if ($parts[6] -like "*inner.zip*" -or $parts[6].Contains($archiveMarker)) {
            throw "Nested ZIP redaction summary leaked entry name or marker: $($parts[6])"
        }
        $parts[0]
    }
    if (-not $nestedArchiveArtifactId) { exit 1 }

    $unsafeTarArchiveArtifactId = Test-Step "Verify unsafe TAR archive is blocked before object storage" {
        $safeArchiveSessionId = $archiveSessionId.Replace("'", "''")
        $row = Invoke-PostgresScalar "SELECT artifact_id, object_uri, media_type, scan_status, sensitivity, scan_summary, redaction_summary_json FROM sa_sandbox_artifact WHERE session_id = '$safeArchiveSessionId' AND object_uri LIKE '%/unsafe-bundle.tar' ORDER BY created_at DESC LIMIT 1;"
        $parts = $row -split "`t"
        if ($parts.Count -ne 7) {
            throw "Unexpected unsafe TAR artifact row: $row"
        }
        if ($parts[1] -like "$ExpectedObjectUriPrefix*") {
            throw "Unsafe TAR archive was copied to object storage: $($parts[1])"
        }
        if ($parts[2] -ne "application/x-tar" -or $parts[3] -ne "BLOCKED" -or $parts[4] -ne "CONFIDENTIAL") {
            throw "Expected BLOCKED CONFIDENTIAL application/x-tar archive but got: $row"
        }
        if ($parts[5] -ne "archive executable content") {
            throw "Expected unsafe TAR scan summary archive executable content but got '$($parts[5])'"
        }
        if ($parts[6] -notlike '*"decision":"BLOCKED"*' -or $parts[6] -notlike '*"ARCHIVE_EXECUTABLE_BINARY"*') {
            throw "Expected BLOCKED ARCHIVE_EXECUTABLE_BINARY TAR redaction summary but got '$($parts[6])'"
        }
        if ($parts[6] -like "*payload.exe*") {
            throw "Unsafe TAR redaction summary leaked entry name: $($parts[6])"
        }
        $parts[0]
    }
    if (-not $unsafeTarArchiveArtifactId) { exit 1 }

    $pathTraversalTarArchiveArtifactId = Test-Step "Verify path-traversal TAR archive is blocked before object storage" {
        $safeArchiveSessionId = $archiveSessionId.Replace("'", "''")
        $row = Invoke-PostgresScalar "SELECT artifact_id, object_uri, media_type, scan_status, sensitivity, scan_summary, redaction_summary_json FROM sa_sandbox_artifact WHERE session_id = '$safeArchiveSessionId' AND object_uri LIKE '%/path-traversal-bundle.tar' ORDER BY created_at DESC LIMIT 1;"
        $parts = $row -split "`t"
        if ($parts.Count -ne 7) {
            throw "Unexpected path-traversal TAR artifact row: $row"
        }
        if ($parts[1] -like "$ExpectedObjectUriPrefix*") {
            throw "Path-traversal TAR archive was copied to object storage: $($parts[1])"
        }
        if ($parts[2] -ne "application/x-tar" -or $parts[3] -ne "BLOCKED" -or $parts[4] -ne "CONFIDENTIAL") {
            throw "Expected BLOCKED CONFIDENTIAL application/x-tar archive but got: $row"
        }
        if ($parts[5] -ne "unsafe archive entry") {
            throw "Expected path-traversal TAR scan summary unsafe archive entry but got '$($parts[5])'"
        }
        if ($parts[6] -notlike '*"decision":"BLOCKED"*' -or $parts[6] -notlike '*"ARCHIVE_UNSAFE_ENTRY"*') {
            throw "Expected BLOCKED ARCHIVE_UNSAFE_ENTRY TAR redaction summary but got '$($parts[6])'"
        }
        if ($parts[6] -like "*outside.txt*") {
            throw "Path-traversal TAR redaction summary leaked entry name: $($parts[6])"
        }
        $parts[0]
    }
    if (-not $pathTraversalTarArchiveArtifactId) { exit 1 }

    $unsafeGzipTarArchiveArtifactId = Test-Step "Verify unsafe TAR.GZ archive is blocked before object storage" {
        $safeArchiveSessionId = $archiveSessionId.Replace("'", "''")
        $row = Invoke-PostgresScalar "SELECT artifact_id, object_uri, media_type, scan_status, sensitivity, scan_summary, redaction_summary_json FROM sa_sandbox_artifact WHERE session_id = '$safeArchiveSessionId' AND object_uri LIKE '%/unsafe-bundle.tar.gz' ORDER BY created_at DESC LIMIT 1;"
        $parts = $row -split "`t"
        if ($parts.Count -ne 7) {
            throw "Unexpected unsafe TAR.GZ artifact row: $row"
        }
        if ($parts[1] -like "$ExpectedObjectUriPrefix*") {
            throw "Unsafe TAR.GZ archive was copied to object storage: $($parts[1])"
        }
        if ($parts[2] -ne "application/gzip" -or $parts[3] -ne "BLOCKED" -or $parts[4] -ne "CONFIDENTIAL") {
            throw "Expected BLOCKED CONFIDENTIAL application/gzip archive but got: $row"
        }
        if ($parts[5] -ne "archive executable content") {
            throw "Expected unsafe TAR.GZ scan summary archive executable content but got '$($parts[5])'"
        }
        if ($parts[6] -notlike '*"decision":"BLOCKED"*' -or $parts[6] -notlike '*"ARCHIVE_EXECUTABLE_BINARY"*') {
            throw "Expected BLOCKED ARCHIVE_EXECUTABLE_BINARY TAR.GZ redaction summary but got '$($parts[6])'"
        }
        if ($parts[6] -like "*payload.exe*") {
            throw "Unsafe TAR.GZ redaction summary leaked entry name: $($parts[6])"
        }
        $parts[0]
    }
    if (-not $unsafeGzipTarArchiveArtifactId) { exit 1 }

    $overBudgetGzipTarArchiveArtifactId = Test-Step "Verify over-budget TAR.GZ archive is blocked before object storage" {
        $safeArchiveSessionId = $archiveSessionId.Replace("'", "''")
        $row = Invoke-PostgresScalar "SELECT artifact_id, object_uri, media_type, scan_status, sensitivity, scan_summary, redaction_summary_json FROM sa_sandbox_artifact WHERE session_id = '$safeArchiveSessionId' AND object_uri LIKE '%/overbudget-bundle.tar.gz' ORDER BY created_at DESC LIMIT 1;"
        $parts = $row -split "`t"
        if ($parts.Count -ne 7) {
            throw "Unexpected over-budget TAR.GZ artifact row: $row"
        }
        if ($parts[1] -like "$ExpectedObjectUriPrefix*") {
            throw "Over-budget TAR.GZ archive was copied to object storage: $($parts[1])"
        }
        if ($parts[2] -ne "application/gzip" -or $parts[3] -ne "BLOCKED" -or $parts[4] -ne "SECRET") {
            throw "Expected BLOCKED SECRET application/gzip archive but got: $row"
        }
        if ($parts[5] -ne "archive content scan failed") {
            throw "Expected over-budget TAR.GZ scan summary archive content scan failed but got '$($parts[5])'"
        }
        if ($parts[6] -notlike '*"decision":"BLOCKED"*' -or $parts[6] -notlike '*"ARCHIVE_SCAN_ERROR"*') {
            throw "Expected BLOCKED ARCHIVE_SCAN_ERROR TAR.GZ redaction summary but got '$($parts[6])'"
        }
        if ($parts[6] -like "*large.bin*") {
            throw "Over-budget TAR.GZ redaction summary leaked entry name: $($parts[6])"
        }
        $parts[0]
    }
    if (-not $overBudgetGzipTarArchiveArtifactId) { exit 1 }

    $plainGzipArtifactId = Test-Step "Verify plain GZIP archive is blocked before object storage" {
        $safeArchiveSessionId = $archiveSessionId.Replace("'", "''")
        $row = Invoke-PostgresScalar "SELECT artifact_id, object_uri, media_type, scan_status, sensitivity, scan_summary, redaction_summary_json FROM sa_sandbox_artifact WHERE session_id = '$safeArchiveSessionId' AND object_uri LIKE '%/plain-bundle.gz' ORDER BY created_at DESC LIMIT 1;"
        $parts = $row -split "`t"
        if ($parts.Count -ne 7) {
            throw "Unexpected plain GZIP artifact row: $row"
        }
        if ($parts[1] -like "$ExpectedObjectUriPrefix*") {
            throw "Plain GZIP archive was copied to object storage: $($parts[1])"
        }
        if ($parts[2] -ne "application/gzip" -or $parts[3] -ne "BLOCKED" -or $parts[4] -ne "SECRET") {
            throw "Expected BLOCKED SECRET application/gzip plain archive but got: $row"
        }
        if ($parts[5] -ne "archive content scan failed") {
            throw "Expected plain GZIP scan summary archive content scan failed but got '$($parts[5])'"
        }
        if ($parts[6] -notlike '*"decision":"BLOCKED"*' -or $parts[6] -notlike '*"ARCHIVE_SCAN_ERROR"*') {
            throw "Expected BLOCKED ARCHIVE_SCAN_ERROR plain GZIP redaction summary but got '$($parts[6])'"
        }
        if ($parts[6].Contains($archiveMarker)) {
            throw "Plain GZIP redaction summary leaked content marker: $($parts[6])"
        }
        $parts[0]
    }
    if (-not $plainGzipArtifactId) { exit 1 }

    Test-Step "Verify archive artifact API exposes governed metadata" {
        $response = Invoke-Json -Method GET -Path "/api/sandbox/sessions/$archiveSessionId/artifacts" -Headers $headers
        Assert-ApiOk $response "List archive sandbox artifacts"
        $safeMatched = @($response.data | Where-Object { "$($_.artifactId)" -eq "$($safeArchive.ArtifactId)" })
        if ($safeMatched.Count -ne 1 -or $safeMatched[0].promptVisible -ne $false) {
            throw "Expected clean ZIP to be listed as prompt-hidden: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $safeTarMatched = @($response.data | Where-Object { "$($_.artifactId)" -eq "$($safeTarArchive.ArtifactId)" })
        if ($safeTarMatched.Count -ne 1 -or $safeTarMatched[0].promptVisible -ne $false) {
            throw "Expected clean TAR to be listed as prompt-hidden: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $safeGzipTarMatched = @($response.data | Where-Object { "$($_.artifactId)" -eq "$($safeGzipTarArchive.ArtifactId)" })
        if ($safeGzipTarMatched.Count -ne 1 -or $safeGzipTarMatched[0].promptVisible -ne $false) {
            throw "Expected clean TAR.GZ to be listed as prompt-hidden: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $unsafeMatched = @($response.data | Where-Object { "$($_.artifactId)" -eq "$unsafeArchiveArtifactId" })
        if ($unsafeMatched.Count -ne 1 -or $unsafeMatched[0].promptVisible -ne $false) {
            throw "Expected unsafe ZIP to be listed as prompt-hidden: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $pathTraversalMatched = @($response.data | Where-Object { "$($_.artifactId)" -eq "$pathTraversalArchiveArtifactId" })
        if ($pathTraversalMatched.Count -ne 1 -or $pathTraversalMatched[0].promptVisible -ne $false) {
            throw "Expected path-traversal ZIP to be listed as prompt-hidden: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $nestedMatched = @($response.data | Where-Object { "$($_.artifactId)" -eq "$nestedArchiveArtifactId" })
        if ($nestedMatched.Count -ne 1 -or $nestedMatched[0].promptVisible -ne $false) {
            throw "Expected nested ZIP to be listed as prompt-hidden: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $unsafeTarMatched = @($response.data | Where-Object { "$($_.artifactId)" -eq "$unsafeTarArchiveArtifactId" })
        if ($unsafeTarMatched.Count -ne 1 -or $unsafeTarMatched[0].promptVisible -ne $false) {
            throw "Expected unsafe TAR to be listed as prompt-hidden: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $pathTraversalTarMatched = @($response.data | Where-Object { "$($_.artifactId)" -eq "$pathTraversalTarArchiveArtifactId" })
        if ($pathTraversalTarMatched.Count -ne 1 -or $pathTraversalTarMatched[0].promptVisible -ne $false) {
            throw "Expected path-traversal TAR to be listed as prompt-hidden: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $unsafeGzipTarMatched = @($response.data | Where-Object { "$($_.artifactId)" -eq "$unsafeGzipTarArchiveArtifactId" })
        if ($unsafeGzipTarMatched.Count -ne 1 -or $unsafeGzipTarMatched[0].promptVisible -ne $false) {
            throw "Expected unsafe TAR.GZ to be listed as prompt-hidden: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $overBudgetGzipTarMatched = @($response.data | Where-Object { "$($_.artifactId)" -eq "$overBudgetGzipTarArchiveArtifactId" })
        if ($overBudgetGzipTarMatched.Count -ne 1 -or $overBudgetGzipTarMatched[0].promptVisible -ne $false) {
            throw "Expected over-budget TAR.GZ to be listed as prompt-hidden: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $plainGzipMatched = @($response.data | Where-Object { "$($_.artifactId)" -eq "$plainGzipArtifactId" })
        if ($plainGzipMatched.Count -ne 1 -or $plainGzipMatched[0].promptVisible -ne $false) {
            throw "Expected plain GZIP to be listed as prompt-hidden: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $artifactJson = $response.data | ConvertTo-Json -Depth 20 -Compress
        if ($artifactJson -match "objectUri|object_uri|storageRef|file:|local://|s3://|payload.exe|outside.txt|large.bin|$archiveMarkerPattern") {
            throw "Archive artifact API leaked storage or unsafe entry details: $artifactJson"
        }
        if ($artifactJson -match "inner.zip") {
            throw "Archive artifact API leaked nested archive entry details: $artifactJson"
        }

        $safeDetail = Invoke-Json -Method GET -Path "/api/sandbox/artifacts/$($safeArchive.ArtifactId)" -Headers $headers
        Assert-ApiOk $safeDetail "Get clean ZIP artifact detail"
        if ($safeDetail.data.downloadable -ne $true -or $safeDetail.data.promptVisible -ne $false) {
            throw "Expected clean ZIP detail to be downloadable and prompt-hidden: $($safeDetail.data | ConvertTo-Json -Depth 20 -Compress)"
        }

        $safeTarDetail = Invoke-Json -Method GET -Path "/api/sandbox/artifacts/$($safeTarArchive.ArtifactId)" -Headers $headers
        Assert-ApiOk $safeTarDetail "Get clean TAR artifact detail"
        if ($safeTarDetail.data.downloadable -ne $true -or $safeTarDetail.data.promptVisible -ne $false) {
            throw "Expected clean TAR detail to be downloadable and prompt-hidden: $($safeTarDetail.data | ConvertTo-Json -Depth 20 -Compress)"
        }

        $safeGzipTarDetail = Invoke-Json -Method GET -Path "/api/sandbox/artifacts/$($safeGzipTarArchive.ArtifactId)" -Headers $headers
        Assert-ApiOk $safeGzipTarDetail "Get clean TAR.GZ artifact detail"
        if ($safeGzipTarDetail.data.downloadable -ne $true -or $safeGzipTarDetail.data.promptVisible -ne $false) {
            throw "Expected clean TAR.GZ detail to be downloadable and prompt-hidden: $($safeGzipTarDetail.data | ConvertTo-Json -Depth 20 -Compress)"
        }

        $detail = Invoke-Json -Method GET -Path "/api/sandbox/artifacts/$unsafeArchiveArtifactId" -Headers $headers
        Assert-ApiOk $detail "Get unsafe ZIP artifact detail"
        if ($detail.data.downloadable -ne $false -or $detail.data.promptVisible -ne $false) {
            throw "Expected unsafe ZIP detail to be non-downloadable and prompt-hidden: $($detail.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($detail.data.redactionSummaryJson)" -notlike '*"ARCHIVE_EXECUTABLE_BINARY"*') {
            throw "Expected unsafe ZIP detail archive category: $($detail.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $detailJson = $detail.data | ConvertTo-Json -Depth 20 -Compress
        if ($detailJson -match "objectUri|object_uri|storageRef|file:|local://|s3://|payload.exe") {
            throw "Unsafe ZIP detail leaked storage or unsafe entry details: $detailJson"
        }

        $pathTraversalDetail = Invoke-Json -Method GET -Path "/api/sandbox/artifacts/$pathTraversalArchiveArtifactId" -Headers $headers
        Assert-ApiOk $pathTraversalDetail "Get path-traversal ZIP artifact detail"
        if ($pathTraversalDetail.data.downloadable -ne $false -or $pathTraversalDetail.data.promptVisible -ne $false) {
            throw "Expected path-traversal ZIP detail to be non-downloadable and prompt-hidden: $($pathTraversalDetail.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($pathTraversalDetail.data.redactionSummaryJson)" -notlike '*"ARCHIVE_UNSAFE_ENTRY"*') {
            throw "Expected path-traversal ZIP detail archive unsafe entry category: $($pathTraversalDetail.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $pathTraversalDetailJson = $pathTraversalDetail.data | ConvertTo-Json -Depth 20 -Compress
        if ($pathTraversalDetailJson -match "objectUri|object_uri|storageRef|file:|local://|s3://|outside.txt") {
            throw "Path-traversal ZIP detail leaked storage or unsafe entry details: $pathTraversalDetailJson"
        }

        $nestedDetail = Invoke-Json -Method GET -Path "/api/sandbox/artifacts/$nestedArchiveArtifactId" -Headers $headers
        Assert-ApiOk $nestedDetail "Get nested ZIP artifact detail"
        if ($nestedDetail.data.downloadable -ne $false -or $nestedDetail.data.promptVisible -ne $false) {
            throw "Expected nested ZIP detail to be non-downloadable and prompt-hidden: $($nestedDetail.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($nestedDetail.data.redactionSummaryJson)" -notlike '*"ARCHIVE_NESTED_ARCHIVE"*') {
            throw "Expected nested ZIP detail archive category: $($nestedDetail.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $nestedDetailJson = $nestedDetail.data | ConvertTo-Json -Depth 20 -Compress
        if ($nestedDetailJson -match "objectUri|object_uri|storageRef|file:|local://|s3://|inner.zip|$archiveMarkerPattern") {
            throw "Nested ZIP detail leaked storage or nested entry details: $nestedDetailJson"
        }

        $tarDetail = Invoke-Json -Method GET -Path "/api/sandbox/artifacts/$unsafeTarArchiveArtifactId" -Headers $headers
        Assert-ApiOk $tarDetail "Get unsafe TAR artifact detail"
        if ($tarDetail.data.downloadable -ne $false -or $tarDetail.data.promptVisible -ne $false) {
            throw "Expected unsafe TAR detail to be non-downloadable and prompt-hidden: $($tarDetail.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($tarDetail.data.redactionSummaryJson)" -notlike '*"ARCHIVE_EXECUTABLE_BINARY"*') {
            throw "Expected unsafe TAR detail archive category: $($tarDetail.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $tarDetailJson = $tarDetail.data | ConvertTo-Json -Depth 20 -Compress
        if ($tarDetailJson -match "objectUri|object_uri|storageRef|file:|local://|s3://|payload.exe") {
            throw "Unsafe TAR detail leaked storage or unsafe entry details: $tarDetailJson"
        }

        $pathTraversalTarDetail = Invoke-Json -Method GET -Path "/api/sandbox/artifacts/$pathTraversalTarArchiveArtifactId" -Headers $headers
        Assert-ApiOk $pathTraversalTarDetail "Get path-traversal TAR artifact detail"
        if ($pathTraversalTarDetail.data.downloadable -ne $false -or $pathTraversalTarDetail.data.promptVisible -ne $false) {
            throw "Expected path-traversal TAR detail to be non-downloadable and prompt-hidden: $($pathTraversalTarDetail.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($pathTraversalTarDetail.data.redactionSummaryJson)" -notlike '*"ARCHIVE_UNSAFE_ENTRY"*') {
            throw "Expected path-traversal TAR detail archive unsafe entry category: $($pathTraversalTarDetail.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $pathTraversalTarDetailJson = $pathTraversalTarDetail.data | ConvertTo-Json -Depth 20 -Compress
        if ($pathTraversalTarDetailJson -match "objectUri|object_uri|storageRef|file:|local://|s3://|outside.txt") {
            throw "Path-traversal TAR detail leaked storage or unsafe entry details: $pathTraversalTarDetailJson"
        }

        $gzipTarDetail = Invoke-Json -Method GET -Path "/api/sandbox/artifacts/$unsafeGzipTarArchiveArtifactId" -Headers $headers
        Assert-ApiOk $gzipTarDetail "Get unsafe TAR.GZ artifact detail"
        if ($gzipTarDetail.data.downloadable -ne $false -or $gzipTarDetail.data.promptVisible -ne $false) {
            throw "Expected unsafe TAR.GZ detail to be non-downloadable and prompt-hidden: $($gzipTarDetail.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($gzipTarDetail.data.redactionSummaryJson)" -notlike '*"ARCHIVE_EXECUTABLE_BINARY"*') {
            throw "Expected unsafe TAR.GZ detail archive category: $($gzipTarDetail.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $gzipTarDetailJson = $gzipTarDetail.data | ConvertTo-Json -Depth 20 -Compress
        if ($gzipTarDetailJson -match "objectUri|object_uri|storageRef|file:|local://|s3://|payload.exe") {
            throw "Unsafe TAR.GZ detail leaked storage or unsafe entry details: $gzipTarDetailJson"
        }

        $overBudgetGzipTarDetail = Invoke-Json -Method GET -Path "/api/sandbox/artifacts/$overBudgetGzipTarArchiveArtifactId" -Headers $headers
        Assert-ApiOk $overBudgetGzipTarDetail "Get over-budget TAR.GZ artifact detail"
        if ($overBudgetGzipTarDetail.data.downloadable -ne $false -or $overBudgetGzipTarDetail.data.promptVisible -ne $false) {
            throw "Expected over-budget TAR.GZ detail to be non-downloadable and prompt-hidden: $($overBudgetGzipTarDetail.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($overBudgetGzipTarDetail.data.redactionSummaryJson)" -notlike '*"ARCHIVE_SCAN_ERROR"*') {
            throw "Expected over-budget TAR.GZ detail archive scan error category: $($overBudgetGzipTarDetail.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $overBudgetGzipTarDetailJson = $overBudgetGzipTarDetail.data | ConvertTo-Json -Depth 20 -Compress
        if ($overBudgetGzipTarDetailJson -match "objectUri|object_uri|storageRef|file:|local://|s3://|large.bin") {
            throw "Over-budget TAR.GZ detail leaked storage or unsafe entry details: $overBudgetGzipTarDetailJson"
        }

        $plainGzipDetail = Invoke-Json -Method GET -Path "/api/sandbox/artifacts/$plainGzipArtifactId" -Headers $headers
        Assert-ApiOk $plainGzipDetail "Get plain GZIP artifact detail"
        if ($plainGzipDetail.data.downloadable -ne $false -or $plainGzipDetail.data.promptVisible -ne $false) {
            throw "Expected plain GZIP detail to be non-downloadable and prompt-hidden: $($plainGzipDetail.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($plainGzipDetail.data.redactionSummaryJson)" -notlike '*"ARCHIVE_SCAN_ERROR"*') {
            throw "Expected plain GZIP detail archive scan error category: $($plainGzipDetail.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $plainGzipDetailJson = $plainGzipDetail.data | ConvertTo-Json -Depth 20 -Compress
        if ($plainGzipDetailJson -match "objectUri|object_uri|storageRef|file:|local://|s3://|$archiveMarkerPattern") {
            throw "Plain GZIP detail leaked storage or content details: $plainGzipDetailJson"
        }
    } | Out-Null

    $officeMarker = "$Marker-office-ooxml"
    $escapedOfficeMarker = $officeMarker.Replace("\", "\\").Replace("'", "\'")
    $officeMediaType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    $officeCode = @"
import zipfile

content_types = '''<?xml version="1.0" encoding="UTF-8"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>'''
document_xml = '''<?xml version="1.0" encoding="UTF-8"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body><w:p><w:r><w:t>safe office $escapedOfficeMarker</w:t></w:r></w:p></w:body>
</w:document>'''
with zipfile.ZipFile('safe-report.docx', 'w') as archive:
    archive.writestr('[Content_Types].xml', content_types)
    archive.writestr('word/document.xml', document_xml)
with zipfile.ZipFile('macro-report.docx', 'w') as archive:
    archive.writestr('[Content_Types].xml', content_types)
    archive.writestr('word/document.xml', document_xml)
    archive.writestr('word/vbaProject.bin', b'SEAHORSE-MACRO-MARKER')
print('$officeMarker')
"@
    $officeObservation = Test-Step "Invoke sandbox_python with Office Open XML artifacts" {
        $response = Invoke-SandboxPythonTool -Headers $headers -Name "Invoke sandbox_python Office artifacts" -Body @{
            runId = $runId
            stepId = "sandbox-artifact-office-step-$suffix"
            toolCallId = "sandbox-artifact-office-call-$suffix"
            agentId = "legacy-react-agent"
            tenantId = "default"
            userId = "$($login.data.userId)"
            agentIdentityId = "$($login.data.userId)"
            arguments = @{ code = $officeCode }
            resourceRefs = @{}
            idempotencyKey = "${runId}:sandbox-artifact-office-call-$suffix"
            allowedToolIds = @("sandbox_python")
        }
        if ($response.data.success -ne $true) {
            throw "sandbox_python Office invocation failed: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $content = "$($response.data.content)"
        if ($content -notlike "*$officeMarker*") {
            throw "sandbox_python Office content did not contain marker '$officeMarker': $content"
        }
        $parsed = $content | ConvertFrom-Json
        if (@($parsed.artifacts).Count -ne 0) {
            throw "Office artifacts should not be prompt-visible: $content"
        }
        $parsed
    }
    if (-not $officeObservation) { exit 1 }

    $officeSessionId = Get-LatestSandboxSessionIdForRun -RunId $runId
    $safeOffice = Test-Step "Verify clean Office Open XML artifact is governed download-only" {
        $safeOfficeSessionId = $officeSessionId.Replace("'", "''")
        $row = Invoke-PostgresScalar "SELECT artifact_id, object_uri, media_type, scan_status, sensitivity, scan_summary, redaction_summary_json FROM sa_sandbox_artifact WHERE session_id = '$safeOfficeSessionId' AND object_uri LIKE '%safe-report.docx' ORDER BY created_at DESC LIMIT 1;"
        $parts = $row -split "`t"
        if ($parts.Count -ne 7) {
            throw "Unexpected clean Office artifact row: $row"
        }
        if ($ExpectedObjectUriPrefix -and $parts[1] -notlike "$ExpectedObjectUriPrefix*") {
            throw "Clean Office artifact was not copied to governed object storage: $($parts[1])"
        }
        if ($parts[2] -ne $officeMediaType -or $parts[3] -ne "CLEAN" -or $parts[4] -ne "INTERNAL") {
            throw "Expected CLEAN INTERNAL Office artifact but got: $row"
        }
        if ($parts[5] -ne "metadata scan passed") {
            throw "Expected clean Office scan summary metadata scan passed but got '$($parts[5])'"
        }
        if ($parts[6] -notlike '*"decision":"CLEAN"*' -or $parts[6] -notlike '*"contentScanned":true*') {
            throw "Expected CLEAN content-scanned Office redaction summary but got '$($parts[6])'"
        }
        [pscustomobject]@{ ArtifactId = $parts[0]; ObjectUri = $parts[1] }
    }
    if (-not $safeOffice) { exit 1 }

    Test-Step "Download governed clean Office Open XML artifact" {
        Add-Type -AssemblyName System.IO.Compression
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $tempDocx = [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), "seahorse-office-$suffix.docx")
        try {
            Invoke-BinaryFile -Method GET -Path "/api/sandbox/artifacts/$($safeOffice.ArtifactId)/download" -Headers $headers -OutputPath $tempDocx
            $archive = [System.IO.Compression.ZipFile]::OpenRead($tempDocx)
            try {
                $entry = $archive.GetEntry("word/document.xml")
                if ($null -eq $entry) {
                    throw "Downloaded clean Office artifact did not contain word/document.xml"
                }
                $stream = $entry.Open()
                $reader = [System.IO.StreamReader]::new($stream, [System.Text.Encoding]::UTF8)
                try {
                    $content = $reader.ReadToEnd()
                } finally {
                    $reader.Dispose()
                    $stream.Dispose()
                }
                if ($content -notlike "*$officeMarker*") {
                    throw "Downloaded clean Office document did not contain marker '$officeMarker': $content"
                }
            } finally {
                $archive.Dispose()
            }
        } finally {
            Remove-Item -LiteralPath $tempDocx -ErrorAction SilentlyContinue
        }
    } | Out-Null

    if ($safeOffice.ObjectUri.StartsWith("local://sandbox-artifacts/")) {
        Test-Step "Verify local clean Office object exists in backend storage volume" {
            $key = $safeOffice.ObjectUri.Substring("local://sandbox-artifacts/".Length)
            if ($key.Contains("'")) {
                throw "Cannot safely shell-quote Office object key"
            }
            $path = "$StorageRoot/sandbox-artifacts/$key"
            & docker exec $BackendContainer sh -lc "test -f '$path'"
            if ($LASTEXITCODE -ne 0) {
                throw "Stored clean Office object not found at $path"
            }
        } | Out-Null
    }

    $macroOfficeArtifactId = Test-Step "Verify Office Open XML macro artifact is blocked before object storage" {
        $safeOfficeSessionId = $officeSessionId.Replace("'", "''")
        $row = Invoke-PostgresScalar "SELECT artifact_id, object_uri, media_type, scan_status, sensitivity, scan_summary, redaction_summary_json FROM sa_sandbox_artifact WHERE session_id = '$safeOfficeSessionId' AND object_uri LIKE '%/macro-report.docx' ORDER BY created_at DESC LIMIT 1;"
        $parts = $row -split "`t"
        if ($parts.Count -ne 7) {
            throw "Unexpected Office macro artifact row: $row"
        }
        if ($parts[1] -like "$ExpectedObjectUriPrefix*") {
            throw "Office macro artifact was copied to object storage: $($parts[1])"
        }
        if ($parts[2] -ne $officeMediaType -or $parts[3] -ne "BLOCKED" -or $parts[4] -ne "CONFIDENTIAL") {
            throw "Expected BLOCKED CONFIDENTIAL Office macro artifact but got: $row"
        }
        if ($parts[5] -ne "office macro artifact content") {
            throw "Expected Office macro scan summary office macro artifact content but got '$($parts[5])'"
        }
        if ($parts[6] -notlike '*"decision":"BLOCKED"*' -or $parts[6] -notlike '*"OFFICE_MACRO"*') {
            throw "Expected BLOCKED OFFICE_MACRO redaction summary but got '$($parts[6])'"
        }
        if ($parts[6] -like "*vbaProject.bin*" -or $parts[6] -like "*SEAHORSE-MACRO-MARKER*") {
            throw "Office macro redaction summary leaked macro details: $($parts[6])"
        }
        $parts[0]
    }
    if (-not $macroOfficeArtifactId) { exit 1 }

    Test-Step "Verify Office artifact API exposes governed metadata" {
        $response = Invoke-Json -Method GET -Path "/api/sandbox/sessions/$officeSessionId/artifacts" -Headers $headers
        Assert-ApiOk $response "List Office sandbox artifacts"
        $safeMatched = @($response.data | Where-Object { "$($_.artifactId)" -eq "$($safeOffice.ArtifactId)" })
        if ($safeMatched.Count -ne 1 -or $safeMatched[0].promptVisible -ne $false) {
            throw "Expected clean Office artifact to be listed as prompt-hidden: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $macroMatched = @($response.data | Where-Object { "$($_.artifactId)" -eq "$macroOfficeArtifactId" })
        if ($macroMatched.Count -ne 1 -or $macroMatched[0].promptVisible -ne $false) {
            throw "Expected Office macro artifact to be listed as prompt-hidden: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $artifactJson = $response.data | ConvertTo-Json -Depth 20 -Compress
        if ($artifactJson -match "objectUri|object_uri|storageRef|file:|local://|s3://|vbaProject.bin|SEAHORSE-MACRO-MARKER") {
            throw "Office artifact API leaked storage or macro details: $artifactJson"
        }

        $safeDetail = Invoke-Json -Method GET -Path "/api/sandbox/artifacts/$($safeOffice.ArtifactId)" -Headers $headers
        Assert-ApiOk $safeDetail "Get clean Office artifact detail"
        if ($safeDetail.data.downloadable -ne $true -or $safeDetail.data.promptVisible -ne $false) {
            throw "Expected clean Office detail to be downloadable and prompt-hidden: $($safeDetail.data | ConvertTo-Json -Depth 20 -Compress)"
        }

        $detail = Invoke-Json -Method GET -Path "/api/sandbox/artifacts/$macroOfficeArtifactId" -Headers $headers
        Assert-ApiOk $detail "Get Office macro artifact detail"
        if ($detail.data.downloadable -ne $false -or $detail.data.promptVisible -ne $false) {
            throw "Expected Office macro detail to be non-downloadable and prompt-hidden: $($detail.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($detail.data.redactionSummaryJson)" -notlike '*"OFFICE_MACRO"*') {
            throw "Expected Office macro detail category: $($detail.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $detailJson = $detail.data | ConvertTo-Json -Depth 20 -Compress
        if ($detailJson -match "objectUri|object_uri|storageRef|file:|local://|s3://|vbaProject.bin|SEAHORSE-MACRO-MARKER") {
            throw "Office macro detail leaked storage or macro details: $detailJson"
        }
    } | Out-Null

    if ($VerifyCapacityAdmission) {
        Test-Step "Reject sandbox session when runtime capacity is saturated" {
            if ($ExpectedRuntimeActiveSessionLimit -le 0) {
                throw "Capacity admission verification requires ExpectedRuntimeActiveSessionLimit > 0"
            }
            if ($ExpectedRuntimeActiveSessionLimit -gt 5) {
                throw "Capacity admission verification is limited to small configured limits; got $ExpectedRuntimeActiveSessionLimit"
            }

            $createdSessionIds = @()
            try {
                $before = Invoke-Json -Method GET -Path "/api/sandbox/runtime/health" -Headers $headers
                Assert-ApiOk $before "Inspect sandbox runtime health before capacity admission"
                $activeCount = [int]$before.data.activeSessionCount
                $sessionsToCreate = [Math]::Max($ExpectedRuntimeActiveSessionLimit - $activeCount, 0)

                for ($i = 0; $i -lt $sessionsToCreate; $i++) {
                    $create = Invoke-Json -Method POST -Path "/api/sandbox/sessions" -Headers $headers -Body @{
                        tenantId = "default"
                        runId = "sandbox-capacity-fill-run-$suffix-$i"
                        runtimeType = "CODE_INTERPRETER"
                        networkRequested = $false
                        requestedHosts = @()
                    }
                    Assert-ApiOk $create "Create sandbox session to fill runtime capacity"
                    if ("$($create.data.status)" -ne "CREATED") {
                        throw "Expected capacity filler session to be CREATED: $($create.data | ConvertTo-Json -Depth 20 -Compress)"
                    }
                    $createdSessionIds += "$($create.data.sessionId)"
                }

                $saturated = Invoke-Json -Method GET -Path "/api/sandbox/runtime/health" -Headers $headers
                Assert-ApiOk $saturated "Inspect saturated sandbox runtime health"
                if ($saturated.data.activeSessionCapacityAvailable -ne $false) {
                    throw "Expected activeSessionCapacityAvailable=false after filling capacity: $($saturated.data | ConvertTo-Json -Depth 20 -Compress)"
                }
                if ("$($saturated.data.capacityStatus)" -ne "SATURATED") {
                    throw "Expected capacityStatus=SATURATED after filling capacity: $($saturated.data | ConvertTo-Json -Depth 20 -Compress)"
                }

                $rejected = Invoke-Json -Method POST -Path "/api/sandbox/sessions" -Headers $headers -Body @{
                    tenantId = "default"
                    runId = "sandbox-capacity-rejected-run-$suffix"
                    runtimeType = "CODE_INTERPRETER"
                    networkRequested = $false
                    requestedHosts = @()
                }
                Assert-ApiOk $rejected "Create sandbox session beyond runtime capacity"
                if ("$($rejected.data.status)" -ne "FAILED") {
                    throw "Expected over-capacity session status FAILED: $($rejected.data | ConvertTo-Json -Depth 20 -Compress)"
                }
                if ("$($rejected.data.reasonCode)" -ne "RUNTIME_CAPACITY_EXCEEDED") {
                    throw "Expected over-capacity reasonCode RUNTIME_CAPACITY_EXCEEDED: $($rejected.data | ConvertTo-Json -Depth 20 -Compress)"
                }

                $safeRejectedSessionId = "$($rejected.data.sessionId)".Replace("'", "''")
                $row = Invoke-PostgresScalar "SELECT status, reason_code FROM sa_sandbox_session WHERE session_id = '$safeRejectedSessionId';"
                $parts = $row -split "`t"
                if ($parts.Count -ne 2) {
                    throw "Unexpected rejected capacity session DB row: $row"
                }
                if ($parts[0] -ne "FAILED") {
                    throw "Expected DB status FAILED for over-capacity session but got '$($parts[0])'"
                }
                if ($parts[1] -ne "RUNTIME_CAPACITY_EXCEEDED") {
                    throw "Expected DB reason_code RUNTIME_CAPACITY_EXCEEDED but got '$($parts[1])'"
                }
            } finally {
                foreach ($createdSessionId in $createdSessionIds) {
                    try {
                        Invoke-Json -Method POST -Path "/api/sandbox/sessions/$createdSessionId/close" -Headers $headers | Out-Null
                    } catch {
                        Write-Host "  WARN: failed to close capacity filler session ${createdSessionId}: $($_.Exception.Message)" -ForegroundColor Yellow
                    }
                }
            }
        } | Out-Null
    }

    Write-Host "`nSummary: $passed / $total passed, $failed failed" -ForegroundColor Cyan
    Write-Host "Backend: $BaseUrl"
    Write-Host "Artifact: $artifactId"
    Write-Host "Object URI: $objectUri"
} catch {
    Write-Host "`nSummary: $passed / $total passed, $failed failed" -ForegroundColor Cyan
    Write-Error $_.Exception.Message
    exit 1
}

if ($failed -gt 0) {
    exit 1
}
