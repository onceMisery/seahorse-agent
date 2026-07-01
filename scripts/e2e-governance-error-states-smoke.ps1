param(
    [string]$BaseUrl = "http://127.0.0.1:9090",
    [string]$AdminUsername = "admin",
    [string]$AdminPassword = "admin123",
    [string]$UserUsername = "demo_user_001",
    [string]$UserPassword = "demo123",
    [string]$BackendContainer = "seahorse-backend",
    [string]$PostgresContainer = "seahorse-postgres",
    [string]$PostgresUsername = "seahorse",
    [string]$PostgresDatabase = "seahorse",
    [string]$TenantId = "default",
    [switch]$SkipUserSeed
)

$ErrorActionPreference = "Stop"
$passed = 0
$failed = 0
$total = 0

. (Join-Path $PSScriptRoot "e2e-governance-user-seed.ps1")

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
    $json = if ([string]::IsNullOrWhiteSpace($content)) { $null } else { $content | ConvertFrom-Json }
    return [PSCustomObject]@{
        Status = $status
        Body = $json
        Raw = $content
    }
}

function Assert-Code {
    param([object]$Response, [string]$ExpectedCode, [string]$Name)
    if ($null -eq $Response.Body -or "$($Response.Body.code)" -ne $ExpectedCode) {
        throw "$Name expected code $ExpectedCode but got $($Response.Raw)"
    }
}

function Assert-ApiOk {
    param([object]$Response, [string]$Name)
    Assert-Code -Response $Response -ExpectedCode "0" -Name $Name
}

function Login {
    param([string]$Username, [string]$Password)
    $response = Invoke-Json -Method POST -Path "/auth/login" `
        -Body @{ username = $Username; password = $Password }
    Assert-ApiOk $response "Login $Username"
    if (-not $response.Body.data.token) {
        throw "Login $Username did not return token"
    }
    return $response.Body.data
}

Test-Step "Structured bad-login error" {
    $response = Invoke-Json -Method POST -Path "/auth/login" `
        -Body @{ username = $AdminUsername; password = "bad-password" } `
        -ExpectedStatus 400
    $code = "$($response.Body.code)"
    if ($code -ne "INVALID_ARGUMENT" -and $code -ne "VALIDATION_ERROR") {
        throw "Unexpected bad-login code $code"
    }
}

$admin = Test-Step "Admin login" {
    Login -Username $AdminUsername -Password $AdminPassword
}
if (-not $admin) { exit 1 }
$adminHeaders = @{ Authorization = "Bearer $($admin.token)"; "X-User-Id" = "$($admin.userId)" }

if (-not $SkipUserSeed) {
    Test-Step "Ensure normal user seed" {
        Ensure-SeahorseGovernanceNormalUser `
            -Username $UserUsername `
            -Password $UserPassword `
            -TenantId $TenantId `
            -PostgresContainer $PostgresContainer `
            -PostgresUsername $PostgresUsername `
            -PostgresDatabase $PostgresDatabase
    }
}

$user = Test-Step "Normal user login" {
    Login -Username $UserUsername -Password $UserPassword
}
if (-not $user) { exit 1 }
$userHeaders = @{ Authorization = "Bearer $($user.token)"; "X-User-Id" = "$($user.userId)" }

Test-Step "Admin governance APIs return data envelopes" {
    foreach ($path in @(
            "/api/tools?current=1&size=5",
            "/api/agents?current=1&size=5",
            "/api/approvals?current=1&size=5",
            "/api/access-decisions?current=1&size=5"
        )) {
        $response = Invoke-Json -Method GET -Path $path -Headers $adminHeaders
        Assert-ApiOk $response "Admin GET $path"
    }
}

Test-Step "Empty state returns an empty page, not an error" {
    $keyword = "__no_such_tool_codex_smoke__"
    $response = Invoke-Json -Method GET -Path "/api/tools?current=1&size=5&keyword=$keyword" -Headers $adminHeaders
    Assert-ApiOk $response "Empty tool search"
    if (@($response.Body.data.records).Count -ne 0) {
        throw "Empty tool search returned records"
    }
}

Test-Step "Not-found state is structured" {
    $response = Invoke-Json -Method GET -Path "/api/tools/no-such-tool-codex-smoke" `
        -Headers $adminHeaders -ExpectedStatus 404
    Assert-Code -Response $response -ExpectedCode "RESOURCE_NOT_FOUND" -Name "Unknown tool"
}

Test-Step "Normal user gets structured permission errors" {
    foreach ($path in @(
            "/api/tools?current=1&size=5",
            "/api/agents?current=1&size=5",
            "/api/approvals?current=1&size=5",
            "/api/access-decisions?current=1&size=5"
        )) {
        $response = Invoke-Json -Method GET -Path $path -Headers $userHeaders -ExpectedStatus 409
        Assert-Code -Response $response -ExpectedCode "CONFLICT" -Name "Permission check $path"
        if ([string]::IsNullOrWhiteSpace("$($response.Body.message)")) {
            throw "Permission check $path did not include message"
        }
    }
}

Test-Step "Configured-off MCP management returns service-unavailable envelope" {
    $envLines = @(& docker.exe inspect $BackendContainer --format '{{range .Config.Env}}{{println .}}{{end}}')
    if (-not ($envLines | Where-Object { $_ -eq "SEAHORSE_AGENT_ADAPTERS_MCP_ENABLED=false" })) {
        throw "Main backend is not in the expected MCP disabled state"
    }
    $response = Invoke-Json -Method GET -Path "/api/mcp/servers" -Headers $adminHeaders -ExpectedStatus 409
    Assert-Code -Response $response -ExpectedCode "CONFLICT" -Name "MCP disabled"
    if ("$($response.Body.message)" -ne "Service not available") {
        throw "Unexpected MCP disabled message: $($response.Raw)"
    }
}

Write-Host "`nSummary: $passed / $total passed, $failed failed" -ForegroundColor Cyan
Write-Host "Admin user ID: $($admin.userId)"
Write-Host "Normal user ID: $($user.userId)"

if ($failed -gt 0) {
    exit 1
}
