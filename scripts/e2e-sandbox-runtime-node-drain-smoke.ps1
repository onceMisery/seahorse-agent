param(
    [string]$BaseUrl = "http://127.0.0.1:9090",
    [string]$Username = "admin",
    [string]$Password = "admin123",
    [string]$Marker = "seahorse-sandbox-runtime-node-drain-smoke",
    [string]$ExpectedRuntimeNodeId = "local-container-docker",
    [Parameter(Mandatory = $true)]
    [string]$ExistingSessionId,
    [string]$PostgresContainer = "seahorse-postgres",
    [string]$PostgresUser = "seahorse",
    [string]$PostgresDatabase = "seahorse",
    [string]$BackendContainer = "seahorse-backend",
    [string]$SandboxWorkspaceRoot = "/var/lib/seahorse-sandbox"
)

$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd("/")
$passed = 0
$failed = 0
$total = 0
$sessionId = $null

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
        [hashtable]$Headers = @{},
        [object]$Body = $null
    )
    $params = @{
        Method = $Method
        Uri = "$BaseUrl$Path"
        Headers = $Headers
        TimeoutSec = 30
    }
    if ($null -ne $Body) {
        $params.ContentType = "application/json"
        $params.Body = $Body | ConvertTo-Json -Depth 20 -Compress
    }
    Invoke-RestMethod @params
}

function Assert-ApiOk {
    param([object]$Response, [string]$Name)
    if ($null -eq $Response -or "$($Response.code)" -ne "0") {
        throw "$Name failed: $($Response | ConvertTo-Json -Depth 20 -Compress)"
    }
}

function Invoke-PostgresScalar {
    param([string]$Sql)
    $result = & docker exec $PostgresContainer psql -U $PostgresUser -d $PostgresDatabase -At -F "`t" -c $Sql
    if ($LASTEXITCODE -ne 0) {
        throw "PostgreSQL query failed: $Sql"
    }
    "$result".Trim()
}

try {
    Test-Step "Wait for backend health" {
        $deadline = (Get-Date).AddMinutes(2)
        do {
            try {
                $health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -TimeoutSec 5
                if ("$($health.status)" -eq "UP") {
                    return
                }
            } catch {
                Start-Sleep -Seconds 2
            }
        } while ((Get-Date) -lt $deadline)
        throw "Backend health did not become UP"
    } | Out-Null

    $login = Test-Step "Login" {
        $response = Invoke-Json -Method POST -Path "/auth/login" -Body @{
            username = $Username
            password = $Password
        }
        Assert-ApiOk $response "Login"
        if (-not "$($response.data.token)") {
            throw "Login response did not include token"
        }
        $response
    }
    $headers = @{ Authorization = "Bearer $($login.data.token)" }

    Test-Step "Inspect draining runtime health" {
        $response = Invoke-Json -Method GET -Path "/api/sandbox/runtime/health" -Headers $headers
        Assert-ApiOk $response "Inspect sandbox runtime health"
        if ("$($response.data.runtime)" -ne "container" -or
            "$($response.data.nodeId)" -ne $ExpectedRuntimeNodeId -or
            $response.data.admissionEnabled -ne $false -or
            $response.data.engineAvailable -ne $true -or
            $response.data.workspaceAvailable -ne $true -or
            $response.data.activeSessionCapacityAvailable -ne $true) {
            throw "Unexpected draining runtime health: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
    } | Out-Null

    Test-Step "Inspect draining runtime node" {
        $response = Invoke-Json -Method GET -Path "/api/sandbox/runtime/nodes" -Headers $headers
        Assert-ApiOk $response "Inspect sandbox runtime nodes"
        $nodes = @($response.data)
        if ($nodes.Count -ne 1) {
            throw "Expected one sandbox runtime node: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $node = $nodes[0]
        if ("$($node.nodeId)" -ne $ExpectedRuntimeNodeId -or
            "$($node.status)" -ne "HEALTHY" -or
            "$($node.admissionStatus)" -ne "DRAINING" -or
            $node.admissionAvailable -ne $false) {
            throw "Unexpected draining runtime node: $($node | ConvertTo-Json -Depth 20 -Compress)"
        }
    } | Out-Null

    Test-Step "Execute existing sandbox session while node is draining" {
        $existingMarker = "$Marker-existing-session"
        $response = Invoke-Json -Method POST -Path "/api/sandbox/sessions/$ExistingSessionId/execute" -Headers $headers -Body @{
            input = "print('$existingMarker')"
            networkRequested = $false
            requestedHosts = @()
        }
        Assert-ApiOk $response "Execute existing sandbox session while draining"
        if ("$($response.data.execution.status)" -ne "SUCCEEDED" -or
            "$($response.data.execution.resultSummary)" -notlike "*$existingMarker*") {
            throw "Existing sandbox session did not execute while draining: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
    } | Out-Null

    Test-Step "Close existing sandbox session while node is draining" {
        $response = Invoke-Json -Method POST -Path "/api/sandbox/sessions/$ExistingSessionId/close" -Headers $headers
        Assert-ApiOk $response "Close existing sandbox session while draining"
        if ("$($response.data.status)" -ne "CANCELLED" -or
            "$($response.data.runtimeNodeId)" -ne $ExpectedRuntimeNodeId) {
            throw "Existing sandbox session did not close while draining: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
    } | Out-Null

    $rejected = Test-Step "Reject new sandbox session while node is draining" {
        $response = Invoke-Json -Method POST -Path "/api/sandbox/sessions" -Headers $headers -Body @{
            tenantId = "default"
            runId = "$Marker-run"
            runtimeType = "CODE_INTERPRETER"
            networkRequested = $false
            requestedHosts = @()
        }
        Assert-ApiOk $response "Create sandbox session while draining"
        if ("$($response.data.status)" -ne "FAILED" -or
            "$($response.data.reasonCode)" -ne "RUNTIME_NODE_DRAINING" -or
            "$($response.data.runtimeNodeId)") {
            throw "Draining node did not reject session before assignment: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $response.data
    }
    $sessionId = "$($rejected.sessionId)"

    Test-Step "Verify draining rejection persistence and runtime isolation" {
        if (-not $sessionId -or $sessionId.Contains("'")) {
            throw "Unsafe or missing rejected session id"
        }
        $row = Invoke-PostgresScalar "SELECT status, reason_code, COALESCE(runtime_node_id, '<null>') FROM sa_sandbox_session WHERE session_id = '$sessionId';"
        $parts = $row -split "`t"
        if ($parts.Count -ne 3 -or $parts[0] -ne "FAILED" -or
            $parts[1] -ne "RUNTIME_NODE_DRAINING" -or $parts[2] -ne "<null>") {
            throw "Unexpected draining session database row: $row"
        }
        & docker exec $BackendContainer sh -lc "test ! -e '$SandboxWorkspaceRoot/$sessionId'"
        if ($LASTEXITCODE -ne 0) {
            throw "Draining rejection created a sandbox workspace"
        }
        $managedContainers = @(& docker ps -a --format "{{.Names}}" --filter "name=seahorse-sandbox-$sessionId")
        if ($managedContainers.Count -ne 0) {
            throw "Draining rejection created managed containers: $($managedContainers -join ',')"
        }
    } | Out-Null

    Write-Host "`nSummary: $passed / $total passed, $failed failed" -ForegroundColor Cyan
    Write-Host "Backend: $BaseUrl"
    Write-Host "Node: $ExpectedRuntimeNodeId"
    Write-Host "Rejected session: $sessionId"
} catch {
    Write-Host "`nSummary: $passed / $total passed, $failed failed" -ForegroundColor Cyan
    Write-Error $_.Exception.Message
    exit 1
}

if ($failed -gt 0) {
    exit 1
}
