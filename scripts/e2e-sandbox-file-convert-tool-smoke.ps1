param(
    [string]$BaseUrl = "http://127.0.0.1:9090",
    [string]$Username = "admin",
    [string]$Password = "admin123",
    [string]$Marker = "seahorse-sandbox-file-convert-smoke",
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
    $runId = "sandbox-file-convert-run-$suffix"
    $toolCallId = "sandbox-file-convert-call-$suffix"
    $csvContent = "name,score,marker`nAda,42,$Marker`nGrace,99,$Marker`n"

    Test-Step "Verify sandbox_file_convert is cataloged" {
        $response = Invoke-Json -Method GET -Path "/api/tools?current=1&size=50&provider=BUILTIN&keyword=sandbox_file_convert" -Headers $headers
        Assert-ApiOk $response "List built-in tools"
        $records = @($response.data.records)
        $matched = @($records | Where-Object { "$($_.toolId)" -eq "sandbox_file_convert" })
        if ($matched.Count -ne 1) {
            throw "sandbox_file_convert not found in built-in tool catalog: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($matched[0].riskLevel)" -ne "HIGH") {
            throw "Expected sandbox_file_convert riskLevel=HIGH: $($matched[0] | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($matched[0].resourceType)" -ne "SANDBOX") {
            throw "Expected sandbox_file_convert resourceType=SANDBOX: $($matched[0] | ConvertTo-Json -Depth 20 -Compress)"
        }
    } | Out-Null

    $observation = Test-Step "Invoke sandbox_file_convert through Tool Gateway" {
        $response = Invoke-Json -Method POST -Path "/api/tools/sandbox_file_convert/invoke" -Headers $headers -Body @{
            runId = $runId
            stepId = "sandbox-file-convert-step-$suffix"
            toolCallId = $toolCallId
            agentId = "legacy-react-agent"
            tenantId = "default"
            userId = "$($login.data.userId)"
            agentIdentityId = "$($login.data.userId)"
            arguments = @{
                sourceFormat = "csv"
                targetFormat = "json"
                content = $csvContent
            }
            resourceRefs = @{}
            idempotencyKey = "${runId}:${toolCallId}"
            allowedToolIds = @("sandbox_file_convert")
        }
        Assert-ApiOk $response "Invoke sandbox_file_convert"
        if ($response.data.success -ne $true) {
            throw "sandbox_file_convert failed: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $content = "$($response.data.content)"
        $parsed = $content | ConvertFrom-Json
        if ("$($parsed.runtimeType)" -ne "FILE_CONVERSION") {
            throw "Expected FILE_CONVERSION runtime: $content"
        }
        if ("$($parsed.executionStatus)" -ne "SUCCEEDED") {
            throw "Expected SUCCEEDED execution: $content"
        }
        if ("$($parsed.conversion.sourceFormat)" -ne "csv" -or "$($parsed.conversion.targetFormat)" -ne "json") {
            throw "Unexpected conversion metadata: $content"
        }
        $artifacts = @($parsed.artifacts)
        if ($artifacts.Count -ne 1) {
            throw "Expected one converted artifact: $content"
        }
        if ("$($artifacts[0].mediaType)" -ne "application/json") {
            throw "Expected JSON artifact mediaType: $content"
        }
        if ("$($artifacts[0].scanStatus)" -ne "CLEAN") {
            throw "Expected CLEAN artifact scan status: $content"
        }
        if ("$($artifacts[0].scanSummary)" -ne "metadata scan passed") {
            throw "Expected metadata scan summary: $content"
        }
        if ($artifacts[0].promptVisible -ne $true) {
            throw "Expected prompt-visible converted artifact: $content"
        }
        $parsed
    }
    if (-not $observation) { exit 1 }

    $sessionId = "$($observation.sessionId)"
    $artifactId = "$(@($observation.artifacts)[0].artifactId)"

    $objectUri = Test-Step "Verify persisted FILE_CONVERSION session and JSON artifact" {
        $safeSessionId = $sessionId.Replace("'", "''")
        $sessionRow = Invoke-PostgresScalar "SELECT runtime_type, profile_id, status FROM sa_sandbox_session WHERE session_id = '$safeSessionId';"
        $sessionParts = $sessionRow -split "`t"
        if ($sessionParts.Count -ne 3) {
            throw "Unexpected sa_sandbox_session row: $sessionRow"
        }
        if ($sessionParts[0] -ne "FILE_CONVERSION") {
            throw "Expected runtime_type FILE_CONVERSION but got '$($sessionParts[0])'"
        }
        if ($sessionParts[1] -ne "file-conversion") {
            throw "Expected profile_id file-conversion but got '$($sessionParts[1])'"
        }
        if ($sessionParts[2] -ne "CANCELLED") {
            throw "Expected closed session status CANCELLED but got '$($sessionParts[2])'"
        }

        $safeArtifactId = $artifactId.Replace("'", "''")
        $artifactRow = Invoke-PostgresScalar "SELECT object_uri, media_type, scan_status, sensitivity, scan_summary FROM sa_sandbox_artifact WHERE artifact_id = '$safeArtifactId';"
        $artifactParts = $artifactRow -split "`t"
        if ($artifactParts.Count -ne 5) {
            throw "Unexpected sa_sandbox_artifact row: $artifactRow"
        }
        if ($artifactParts[0] -like "file:*") {
            throw "sandbox file conversion artifact still points at file URI: $($artifactParts[0])"
        }
        if ($ExpectedObjectUriPrefix -and $artifactParts[0] -notlike "$ExpectedObjectUriPrefix*") {
            throw "Expected object_uri prefix '$ExpectedObjectUriPrefix' but got '$($artifactParts[0])'"
        }
        if ($artifactParts[1] -ne "application/json") {
            throw "Expected media_type application/json but got '$($artifactParts[1])'"
        }
        if ($artifactParts[2] -ne "CLEAN") {
            throw "Expected scan_status CLEAN but got '$($artifactParts[2])'"
        }
        if ($artifactParts[3] -ne "INTERNAL") {
            throw "Expected sensitivity INTERNAL but got '$($artifactParts[3])'"
        }
        if ($artifactParts[4] -ne "metadata scan passed") {
            throw "Expected scan_summary metadata scan passed but got '$($artifactParts[4])'"
        }
        $artifactParts[0]
    }
    if (-not $objectUri) { exit 1 }

    Test-Step "Download converted JSON through governed artifact endpoint" {
        $content = Invoke-Text -Method GET -Path "/api/sandbox/artifacts/$artifactId/download" -Headers $headers
        $parsedRows = $content | ConvertFrom-Json
        $rows = if ($parsedRows -is [array]) { @($parsedRows) } else { @($parsedRows) }
        if ($rows.Count -eq 1 -and $rows[0] -is [array]) {
            $rows = @($rows[0])
        }
        if ($rows.Count -ne 2) {
            throw "Expected two converted JSON rows: $content"
        }
        if ("$($rows[0].name)" -ne "Ada" -or "$($rows[0].score)" -ne "42") {
            throw "First converted row mismatch: $content"
        }
        if ("$($rows[1].name)" -ne "Grace" -or "$($rows[1].score)" -ne "99") {
            throw "Second converted row mismatch: $content"
        }
        if ($content -notlike "*$Marker*") {
            throw "Downloaded JSON did not contain marker '$Marker': $content"
        }
        if ($content -match "objectUri|object_uri|storageRef|file:|local://|s3://") {
            throw "Downloaded artifact body leaked storage metadata: $content"
        }
    } | Out-Null

    if ($objectUri.StartsWith("local://sandbox-artifacts/")) {
        Test-Step "Verify local converted object exists in backend storage volume" {
            $key = $objectUri.Substring("local://sandbox-artifacts/".Length)
            if ($key.Contains("'") -or $Marker.Contains("'")) {
                throw "Cannot safely shell-quote key or marker"
            }
            $path = "$StorageRoot/sandbox-artifacts/$key"
            & docker exec $BackendContainer sh -lc "test -f '$path' && grep -F -q '$Marker' '$path'"
            if ($LASTEXITCODE -ne 0) {
                throw "Stored converted object not found or marker missing at $path"
            }
        } | Out-Null
    }

    $jsonToCsvRunId = "sandbox-file-convert-json-csv-run-$suffix"
    $jsonToCsvToolCallId = "sandbox-file-convert-json-csv-call-$suffix"
    $jsonRows = @(
        [ordered]@{ name = "Lin"; score = "7"; marker = $Marker },
        [ordered]@{ name = "Katherine"; score = "11"; marker = $Marker }
    )
    $jsonContent = $jsonRows | ConvertTo-Json -Depth 10 -Compress

    $jsonToCsvObservation = Test-Step "Invoke sandbox_file_convert JSON to CSV through Tool Gateway" {
        $response = Invoke-Json -Method POST -Path "/api/tools/sandbox_file_convert/invoke" -Headers $headers -Body @{
            runId = $jsonToCsvRunId
            stepId = "sandbox-file-convert-json-csv-step-$suffix"
            toolCallId = $jsonToCsvToolCallId
            agentId = "legacy-react-agent"
            tenantId = "default"
            userId = "$($login.data.userId)"
            agentIdentityId = "$($login.data.userId)"
            arguments = @{
                sourceFormat = "json"
                targetFormat = "csv"
                content = $jsonContent
            }
            resourceRefs = @{}
            idempotencyKey = "${jsonToCsvRunId}:${jsonToCsvToolCallId}"
            allowedToolIds = @("sandbox_file_convert")
        }
        Assert-ApiOk $response "Invoke sandbox_file_convert JSON to CSV"
        if ($response.data.success -ne $true) {
            throw "sandbox_file_convert JSON to CSV failed: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $content = "$($response.data.content)"
        $parsed = $content | ConvertFrom-Json
        if ("$($parsed.runtimeType)" -ne "FILE_CONVERSION") {
            throw "Expected FILE_CONVERSION runtime for JSON to CSV: $content"
        }
        if ("$($parsed.executionStatus)" -ne "SUCCEEDED") {
            throw "Expected SUCCEEDED JSON to CSV execution: $content"
        }
        if ("$($parsed.conversion.sourceFormat)" -ne "json" -or "$($parsed.conversion.targetFormat)" -ne "csv") {
            throw "Unexpected JSON to CSV conversion metadata: $content"
        }
        $artifacts = @($parsed.artifacts)
        if ($artifacts.Count -ne 1) {
            throw "Expected one JSON to CSV artifact: $content"
        }
        if ("$($artifacts[0].mediaType)" -ne "text/csv") {
            throw "Expected CSV artifact mediaType: $content"
        }
        if ("$($artifacts[0].scanStatus)" -ne "CLEAN") {
            throw "Expected CLEAN JSON to CSV artifact scan status: $content"
        }
        if ("$($artifacts[0].scanSummary)" -ne "metadata scan passed") {
            throw "Expected JSON to CSV metadata scan summary: $content"
        }
        if ($artifacts[0].promptVisible -ne $true) {
            throw "Expected prompt-visible JSON to CSV artifact: $content"
        }
        $parsed
    }
    if (-not $jsonToCsvObservation) { exit 1 }

    $jsonToCsvSessionId = "$($jsonToCsvObservation.sessionId)"
    $jsonToCsvArtifactId = "$(@($jsonToCsvObservation.artifacts)[0].artifactId)"

    $jsonToCsvObjectUri = Test-Step "Verify persisted JSON to CSV session and artifact" {
        $safeSessionId = $jsonToCsvSessionId.Replace("'", "''")
        $sessionRow = Invoke-PostgresScalar "SELECT runtime_type, profile_id, status FROM sa_sandbox_session WHERE session_id = '$safeSessionId';"
        $sessionParts = $sessionRow -split "`t"
        if ($sessionParts.Count -ne 3) {
            throw "Unexpected JSON to CSV sa_sandbox_session row: $sessionRow"
        }
        if ($sessionParts[0] -ne "FILE_CONVERSION") {
            throw "Expected JSON to CSV runtime_type FILE_CONVERSION but got '$($sessionParts[0])'"
        }
        if ($sessionParts[1] -ne "file-conversion") {
            throw "Expected JSON to CSV profile_id file-conversion but got '$($sessionParts[1])'"
        }
        if ($sessionParts[2] -ne "CANCELLED") {
            throw "Expected JSON to CSV closed session status CANCELLED but got '$($sessionParts[2])'"
        }

        $safeArtifactId = $jsonToCsvArtifactId.Replace("'", "''")
        $artifactRow = Invoke-PostgresScalar "SELECT object_uri, media_type, scan_status, sensitivity, scan_summary FROM sa_sandbox_artifact WHERE artifact_id = '$safeArtifactId';"
        $artifactParts = $artifactRow -split "`t"
        if ($artifactParts.Count -ne 5) {
            throw "Unexpected JSON to CSV sa_sandbox_artifact row: $artifactRow"
        }
        if ($artifactParts[0] -like "file:*") {
            throw "JSON to CSV artifact still points at file URI: $($artifactParts[0])"
        }
        if ($ExpectedObjectUriPrefix -and $artifactParts[0] -notlike "$ExpectedObjectUriPrefix*") {
            throw "Expected JSON to CSV object_uri prefix '$ExpectedObjectUriPrefix' but got '$($artifactParts[0])'"
        }
        if ($artifactParts[1] -ne "text/csv") {
            throw "Expected JSON to CSV media_type text/csv but got '$($artifactParts[1])'"
        }
        if ($artifactParts[2] -ne "CLEAN") {
            throw "Expected JSON to CSV scan_status CLEAN but got '$($artifactParts[2])'"
        }
        if ($artifactParts[3] -ne "INTERNAL") {
            throw "Expected JSON to CSV sensitivity INTERNAL but got '$($artifactParts[3])'"
        }
        if ($artifactParts[4] -ne "metadata scan passed") {
            throw "Expected JSON to CSV scan_summary metadata scan passed but got '$($artifactParts[4])'"
        }
        $artifactParts[0]
    }
    if (-not $jsonToCsvObjectUri) { exit 1 }

    Test-Step "Download converted CSV through governed artifact endpoint" {
        $content = Invoke-Text -Method GET -Path "/api/sandbox/artifacts/$jsonToCsvArtifactId/download" -Headers $headers
        $rows = @($content -split "`r?`n" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | ConvertFrom-Csv)
        if ($rows.Count -ne 2) {
            throw "Expected two converted CSV rows: $content"
        }
        if ("$($rows[0].name)" -ne "Lin" -or "$($rows[0].score)" -ne "7") {
            throw "First converted CSV row mismatch: $content"
        }
        if ("$($rows[1].name)" -ne "Katherine" -or "$($rows[1].score)" -ne "11") {
            throw "Second converted CSV row mismatch: $content"
        }
        if ($content -notlike "*$Marker*") {
            throw "Downloaded CSV did not contain marker '$Marker': $content"
        }
        if ($content -match "objectUri|object_uri|storageRef|file:|local://|s3://") {
            throw "Downloaded CSV artifact body leaked storage metadata: $content"
        }
    } | Out-Null

    if ($jsonToCsvObjectUri.StartsWith("local://sandbox-artifacts/")) {
        Test-Step "Verify local converted CSV object exists in backend storage volume" {
            $key = $jsonToCsvObjectUri.Substring("local://sandbox-artifacts/".Length)
            if ($key.Contains("'") -or $Marker.Contains("'")) {
                throw "Cannot safely shell-quote JSON to CSV key or marker"
            }
            $path = "$StorageRoot/sandbox-artifacts/$key"
            & docker exec $BackendContainer sh -lc "test -f '$path' && grep -F -q '$Marker' '$path'"
            if ($LASTEXITCODE -ne 0) {
                throw "Stored JSON to CSV object not found or marker missing at $path"
            }
        } | Out-Null
    }

    Write-Host "`nSummary: $passed / $total passed, $failed failed" -ForegroundColor Cyan
    Write-Host "Backend: $BaseUrl"
    Write-Host "Tool: sandbox_file_convert"
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
