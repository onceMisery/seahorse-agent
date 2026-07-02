param(
    [string]$BaseUrl = "http://127.0.0.1:9090",
    [string]$Username = "admin",
    [string]$Password = "admin123",
    [string]$Marker = "seahorse-tool-gateway-quota-smoke",
    [switch]$SkipHealth
)

$ErrorActionPreference = "Stop"
$passed = 0
$failed = 0
$total = 0
$createdPolicyId = $null

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
        [int]$ExpectedStatus = 200
    )

    $bodyText = $null
    if ($null -ne $Body) {
        $bodyText = if ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Depth 20 -Compress }
    }

    $tempBodyFile = $null
    $args = @("-sS", "-w", "`n%{http_code}", "-X", $Method, "$BaseUrl$Path")
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
            $health = Invoke-Json -Method GET -Path "/actuator/health"
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
    $runId = "quota-smoke-run-$suffix"
    $stepId = "quota-smoke-step-$suffix"
    $toolCallId = "quota-smoke-call-$suffix"
    $createdPolicyId = "quota-smoke-policy-$suffix"

    Test-Step "Create run quota policy with zero calls" {
        $response = Invoke-Json -Method POST -Path "/api/quotas/policies" -Headers $headers -Body @{
            policyId = $createdPolicyId
            tenantId = "default"
            scope = "RUN"
            subjectId = $runId
            status = "ACTIVE"
            callLimit = 0
            warnRatio = 1.0
        }
        Assert-ApiOk $response "Create quota policy"
        if ($response.data.policyId -ne $createdPolicyId) {
            throw "Unexpected quota policy response: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
    } | Out-Null

    Test-Step "Invoke sandbox_python and expect quota denial" {
        $response = Invoke-Json -Method POST -Path "/api/tools/sandbox_python/invoke" -Headers $headers -Body @{
            runId = $runId
            stepId = $stepId
            toolCallId = $toolCallId
            agentId = "legacy-react-agent"
            tenantId = "default"
            userId = "$($login.data.userId)"
            agentIdentityId = "$($login.data.userId)"
            arguments = @{ code = "print('$Marker')" }
            resourceRefs = @{}
            idempotencyKey = "${runId}:${toolCallId}"
            allowedToolIds = @("sandbox_python")
        }
        Assert-ApiOk $response "Invoke sandbox_python"
        if ($response.data.success -ne $false) {
            throw "Expected quota denial but invocation succeeded: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ($response.data.error -ne "QUOTA_HARD_LIMIT_EXCEEDED") {
            throw "Expected QUOTA_HARD_LIMIT_EXCEEDED but got '$($response.data.error)'"
        }
        if (-not [string]::IsNullOrEmpty($response.data.content)) {
            throw "Quota-denied invocation unexpectedly returned content: $($response.data.content)"
        }
        $response.data | ConvertTo-Json -Compress | Write-Host
    } | Out-Null
} finally {
    if ($createdPolicyId) {
        try {
            Invoke-Json -Method POST -Path "/api/quotas/policies/$createdPolicyId/disable" -Headers $headers | Out-Null
        } catch {
            Write-Warning "Failed to disable quota policy ${createdPolicyId}: $($_.Exception.Message)"
        }
    }
}

Write-Host "`nSummary: $passed / $total passed, $failed failed" -ForegroundColor Cyan
Write-Host "Backend: $BaseUrl"
Write-Host "Tool: sandbox_python"

if ($failed -gt 0) {
    exit 1
}
