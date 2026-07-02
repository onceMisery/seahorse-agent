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
    [switch]$UseScheduledSweep,
    [int]$ScheduledSweepWaitSeconds = 45,
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

function Remove-DockerContainerBestEffort {
    param([string]$Name)
    try {
        & docker rm -f $Name 2>$null | Out-Null
    } catch {
        return
    }
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
    $runId = "sandbox-artifact-storage-run-$suffix"
    $toolCallId = "sandbox-artifact-storage-call-$suffix"
    $escapedMarker = $Marker.Replace("\", "\\").Replace("'", "\'")
    $code = "from pathlib import Path`nPath('answer-storage.txt').write_text('artifact $escapedMarker', encoding='utf-8')`nprint('$escapedMarker')"

    $observation = Test-Step "Invoke sandbox_python and capture artifact metadata" {
        $response = Invoke-Json -Method POST -Path "/api/tools/sandbox_python/invoke" -Headers $headers -Body @{
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
        Assert-ApiOk $response "Invoke sandbox_python"
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
    $sessionId = "$($observation.sessionId)"
    $objectUri = Test-Step "Verify persisted sandbox artifact uses object storage URI" {
        $safeArtifactId = $artifactId.Replace("'", "''")
        $row = Invoke-PostgresScalar "SELECT object_uri, scan_status, sensitivity FROM sa_sandbox_artifact WHERE artifact_id = '$safeArtifactId';"
        $parts = $row -split "`t"
        if ($parts.Count -ne 3) {
            throw "Unexpected sa_sandbox_artifact row: $row"
        }
        if ($parts[0] -like "file:*") {
            throw "sandbox artifact still points at file URI: $($parts[0])"
        }
        if ($ExpectedObjectUriPrefix -and $parts[0] -notlike "$ExpectedObjectUriPrefix*") {
            throw "Expected object_uri prefix '$ExpectedObjectUriPrefix' but got '$($parts[0])'"
        }
        if ($parts[1] -ne "CLEAN") {
            throw "Expected CLEAN scan_status but got '$($parts[1])'"
        }
        if ($parts[2] -ne "INTERNAL") {
            throw "Expected INTERNAL sensitivity but got '$($parts[2])'"
        }
        $parts[0]
    }
    if (-not $objectUri) { exit 1 }

    Test-Step "Verify persisted sandbox session profile and TTL metadata" {
        $safeSessionId = $sessionId.Replace("'", "''")
        $row = Invoke-PostgresScalar "SELECT profile_id, (expires_at > created_at) FROM sa_sandbox_session WHERE session_id = '$safeSessionId';"
        $parts = $row -split "`t"
        if ($parts.Count -ne 2) {
            throw "Unexpected sa_sandbox_session row: $row"
        }
        if ($parts[0] -ne "python-small") {
            throw "Expected python-small profile but got '$($parts[0])'"
        }
        if ($parts[1] -ne "t") {
            throw "Expected expires_at to be after created_at but got '$($parts[1])'"
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
        if (-not "$($matched[0].expiresAt)") {
            throw "Sandbox session API did not include expiresAt: $($matched[0] | ConvertTo-Json -Depth 20 -Compress)"
        }
        $sessionJson = $matched[0] | ConvertTo-Json -Depth 20 -Compress
        if ($sessionJson -match "objectUri|object_uri|storageRef|file:|local://|s3://") {
            throw "Sandbox session API leaked storage URI fields: $sessionJson"
        }
    } | Out-Null

    $expiredSweepStepName = if ($UseScheduledSweep) {
        "Wait for scheduled sandbox session sweep as TIMED_OUT"
    } else {
        "Sweep expired sandbox session as TIMED_OUT"
    }

    Test-Step $expiredSweepStepName {
        $expiredRunId = "sandbox-expired-sweep-run-$suffix"
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
        $activeRunId = "sandbox-orphan-runtime-active-run-$suffix"
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
    $secretCode = "from pathlib import Path`nPath('answer-secret-content.txt').write_text('api_key = `"$secretToken`"', encoding='utf-8')`nprint('$secretMarker')"
    $secretObservation = Test-Step "Invoke sandbox_python with content-sensitive artifact" {
        $response = Invoke-Json -Method POST -Path "/api/tools/sandbox_python/invoke" -Headers $headers -Body @{
            runId = "sandbox-artifact-secret-run-$suffix"
            stepId = "sandbox-artifact-secret-step-$suffix"
            toolCallId = "sandbox-artifact-secret-call-$suffix"
            agentId = "legacy-react-agent"
            tenantId = "default"
            userId = "$($login.data.userId)"
            agentIdentityId = "$($login.data.userId)"
            arguments = @{ code = $secretCode }
            resourceRefs = @{}
            idempotencyKey = "sandbox-artifact-secret-run-${suffix}:sandbox-artifact-secret-call-$suffix"
            allowedToolIds = @("sandbox_python")
        }
        Assert-ApiOk $response "Invoke sandbox_python secret artifact"
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
        if (-not "$($parsed.sessionId)") {
            throw "sandbox_python observation did not include secret scan sessionId: $content"
        }
        $parsed
    }
    if (-not $secretObservation) { exit 1 }

    $secretSessionId = "$($secretObservation.sessionId)"
    $secretArtifactId = Test-Step "Verify content-sensitive sandbox artifact is blocked before object storage" {
        $safeSecretSessionId = $secretSessionId.Replace("'", "''")
        $row = Invoke-PostgresScalar "SELECT artifact_id, object_uri, scan_status, sensitivity FROM sa_sandbox_artifact WHERE session_id = '$safeSecretSessionId' ORDER BY created_at DESC LIMIT 1;"
        $parts = $row -split "`t"
        if ($parts.Count -ne 4) {
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
        $artifactJson = $matched[0] | ConvertTo-Json -Depth 20 -Compress
        if ($artifactJson -match "objectUri|object_uri|storageRef|file:|local://|s3://|$secretToken") {
            throw "Content-sensitive artifact API leaked storage or secret content: $artifactJson"
        }

        $detail = Invoke-Json -Method GET -Path "/api/sandbox/artifacts/$secretArtifactId" -Headers $headers
        Assert-ApiOk $detail "Get content-sensitive sandbox artifact detail"
        if ($detail.data.downloadable -ne $false) {
            throw "Expected content-sensitive artifact downloadable=false: $($detail.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $detailJson = $detail.data | ConvertTo-Json -Depth 20 -Compress
        if ($detailJson -match "objectUri|object_uri|storageRef|file:|local://|s3://|$secretToken") {
            throw "Content-sensitive artifact detail leaked storage or secret content: $detailJson"
        }
    } | Out-Null

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
