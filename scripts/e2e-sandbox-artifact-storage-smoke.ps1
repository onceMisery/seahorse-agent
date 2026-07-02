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
    [string]$ExpectedObjectUriPrefix = "local://sandbox-artifacts/",
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
