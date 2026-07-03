param(
    [string]$BaseUrl = "http://127.0.0.1:9090",
    [string]$Username = "admin",
    [string]$Password = "admin123",
    [string]$Marker = "seahorse-sandbox-runtime-profile-policy-smoke",
    [string]$PostgresContainer = "seahorse-postgres",
    [string]$PostgresUser = "seahorse",
    [string]$PostgresDatabase = "seahorse",
    [switch]$SkipHealth
)

$ErrorActionPreference = "Stop"
$passed = 0
$failed = 0
$total = 0
$headers = @{}
$createdSessionId = $null

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
    $raw = & docker exec $PostgresContainer psql -U $PostgresUser -d $PostgresDatabase -At -F "|" -c $Sql
    if ($LASTEXITCODE -ne 0) {
        throw "psql exited with $LASTEXITCODE for SQL: $Sql"
    }
    $rows = @($raw | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($rows.Count -eq 0) {
        throw "SQL returned no rows: $Sql"
    }
    return $rows[0]
}

function Sql-Literal {
    param([string]$Value)
    return ($Value -replace "'", "''")
}

function Upsert-CodeInterpreterPolicy {
    param([string]$Status, [int]$TtlSeconds)
    $response = Invoke-Json -Method POST -Path "/api/sandbox/runtime/profile-policies" -Headers $script:headers -Body @{
        tenantId = "default"
        runtimeType = "CODE_INTERPRETER"
        profileId = "python-small"
        status = $Status
        sessionTtlSeconds = $TtlSeconds
        networkAllowed = $false
    }
    Assert-ApiOk $response "Upsert runtime profile policy"
    return $response.data
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

    $script:headers = @{ Authorization = "Bearer $($login.data.token)" }
    $suffix = ([guid]::NewGuid().ToString('N')).Substring(0, 8)
    $runId = "sandbox-profile-policy-run-$suffix"
    $disabledRunId = "sandbox-profile-disabled-run-$suffix"

    Test-Step "Reset CODE_INTERPRETER policy to active default" {
        $policy = Upsert-CodeInterpreterPolicy -Status "ACTIVE" -TtlSeconds 3600
        if ("$($policy.status)" -ne "ACTIVE" -or [int]$policy.sessionTtlSeconds -ne 3600) {
            throw "Unexpected reset policy: $($policy | ConvertTo-Json -Depth 20 -Compress)"
        }
    } | Out-Null

    Test-Step "Write CODE_INTERPRETER TTL policy" {
        $policy = Upsert-CodeInterpreterPolicy -Status "ACTIVE" -TtlSeconds 120
        if ("$($policy.policyId)" -ne "sandbox-runtime-profile-default-code_interpreter") {
            throw "Unexpected policy id: $($policy | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($policy.profileId)" -ne "python-small" -or [int]$policy.sessionTtlSeconds -ne 120) {
            throw "Unexpected policy response: $($policy | ConvertTo-Json -Depth 20 -Compress)"
        }
    } | Out-Null

    Test-Step "Verify profile list reflects TTL policy" {
        $response = Invoke-Json -Method GET -Path "/api/sandbox/runtime/profiles?tenantId=default" -Headers $headers
        Assert-ApiOk $response "List runtime profiles"
        $profiles = @($response.data.profiles)
        $python = @($profiles | Where-Object { "$($_.runtimeType)" -eq "CODE_INTERPRETER" }) | Select-Object -First 1
        if (-not $python) {
            throw "CODE_INTERPRETER profile missing: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ([int]$python.sessionTtlSeconds -ne 120 -or "$($python.policyStatus)" -ne "ACTIVE") {
            throw "CODE_INTERPRETER profile did not reflect TTL policy: $($python | ConvertTo-Json -Depth 20 -Compress)"
        }
    } | Out-Null

    $created = Test-Step "Create session with runtime profile TTL policy" {
        $response = Invoke-Json -Method POST -Path "/api/sandbox/sessions" -Headers $headers -Body @{
            tenantId = "default"
            runId = $runId
            runtimeType = "CODE_INTERPRETER"
            networkRequested = $false
            requestedHosts = @()
        }
        Assert-ApiOk $response "Create sandbox session"
        if ("$($response.data.status)" -ne "CREATED") {
            throw "Expected CREATED session: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($response.data.profileId)" -ne "python-small") {
            throw "Expected python-small profile: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $response.data
    }
    if (-not $created) { exit 1 }
    $createdSessionId = "$($created.sessionId)"

    Test-Step "Verify session TTL persisted in PostgreSQL" {
        $safeSessionId = Sql-Literal $createdSessionId
        $row = Invoke-PostgresScalar "SELECT profile_id, status, reason_code, ROUND(EXTRACT(EPOCH FROM (expires_at - created_at)))::int FROM sa_sandbox_session WHERE session_id = '$safeSessionId';"
        $parts = "$row" -split "\|"
        if ($parts.Count -ne 4) {
            throw "Unexpected session policy row: $row"
        }
        $ttl = [int]$parts[3]
        if ($parts[0] -ne "python-small" -or $parts[1] -ne "CREATED" -or $parts[2] -ne "VALID_REQUEST" -or $ttl -lt 118 -or $ttl -gt 122) {
            throw "Unexpected session policy row: $row"
        }
    } | Out-Null

    Test-Step "Disable CODE_INTERPRETER runtime profile policy" {
        $policy = Upsert-CodeInterpreterPolicy -Status "DISABLED" -TtlSeconds 120
        if ("$($policy.status)" -ne "DISABLED") {
            throw "Expected DISABLED policy: $($policy | ConvertTo-Json -Depth 20 -Compress)"
        }
    } | Out-Null

    $rejected = Test-Step "Create session rejected by disabled runtime profile policy" {
        $response = Invoke-Json -Method POST -Path "/api/sandbox/sessions" -Headers $headers -Body @{
            tenantId = "default"
            runId = $disabledRunId
            runtimeType = "CODE_INTERPRETER"
            networkRequested = $false
            requestedHosts = @()
        }
        Assert-ApiOk $response "Create sandbox session under disabled profile"
        if ("$($response.data.status)" -ne "FAILED" -or "$($response.data.reasonCode)" -ne "RUNTIME_PROFILE_DISABLED") {
            throw "Expected RUNTIME_PROFILE_DISABLED failed session: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $response.data
    }
    if (-not $rejected) { exit 1 }

    Test-Step "Verify disabled rejection persisted in PostgreSQL" {
        $safeSessionId = Sql-Literal "$($rejected.sessionId)"
        $row = Invoke-PostgresScalar "SELECT status, reason_code, profile_id FROM sa_sandbox_session WHERE session_id = '$safeSessionId';"
        if ("$row" -ne "FAILED|RUNTIME_PROFILE_DISABLED|python-small") {
            throw "Unexpected disabled session row: $row"
        }
    } | Out-Null

    Test-Step "Restore CODE_INTERPRETER runtime profile policy" {
        $policy = Upsert-CodeInterpreterPolicy -Status "ACTIVE" -TtlSeconds 3600
        if ("$($policy.status)" -ne "ACTIVE" -or [int]$policy.sessionTtlSeconds -ne 3600) {
            throw "Failed to restore policy: $($policy | ConvertTo-Json -Depth 20 -Compress)"
        }
    } | Out-Null

    if ($createdSessionId) {
        Test-Step "Close created sandbox session" {
            $response = Invoke-Json -Method POST -Path "/api/sandbox/sessions/$createdSessionId/close" -Headers $headers
            Assert-ApiOk $response "Close sandbox session"
            if ("$($response.data.status)" -ne "CANCELLED") {
                throw "Expected closed session status CANCELLED: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
            }
        } | Out-Null
    }
} finally {
    if ($script:headers.Count -gt 0) {
        try {
            Upsert-CodeInterpreterPolicy -Status "ACTIVE" -TtlSeconds 3600 | Out-Null
        } catch {
            Write-Warning "Failed to restore CODE_INTERPRETER runtime profile policy: $($_.Exception.Message)"
        }
    }
}

Write-Host "`nSandbox runtime profile policy smoke complete: $passed/$total passed, $failed failed" -ForegroundColor Cyan
if ($failed -gt 0) {
    exit 1
}
