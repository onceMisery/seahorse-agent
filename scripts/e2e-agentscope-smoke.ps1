param(
    [string]$BaseUrl = "http://127.0.0.1:9090",
    [string]$Username = "admin",
    [string]$Password = "admin123",
    [long]$AgentScopeRunProfileId = -9104,
    [long]$KernelRunProfileId = -9101,
    [string]$BackendContainer = "seahorse-backend",
    [string]$PostgresContainer = "seahorse-postgres",
    [string]$PostgresUser = "seahorse",
    [string]$PostgresDatabase = "seahorse"
)

$ErrorActionPreference = "Stop"
$passed = 0
$failed = 0
$total = 0
$a2aEnabled = $false

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

function Assert-ApiOk {
    param([object]$Response, [string]$Name)
    if ($null -eq $Response -or "$($Response.code)" -ne "0") {
        throw "$Name API error: $($Response | ConvertTo-Json -Depth 20 -Compress)"
    }
}

function Assert-Equal {
    param([object]$Actual, [object]$Expected, [string]$Name)
    if ("$Actual" -ne "$Expected") {
        throw "$Name expected '$Expected' but got '$Actual'"
    }
}

function Invoke-DbScalarRow {
    param([string]$Sql)
    $raw = & docker.exe exec $PostgresContainer psql -U $PostgresUser -d $PostgresDatabase -t -A -F "|" -c $Sql
    if ($LASTEXITCODE -ne 0) {
        throw "psql failed with exit code $LASTEXITCODE"
    }
    $rows = @($raw | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($rows.Count -eq 0) {
        return $null
    }
    return $rows[0]
}

function Invoke-HttpStatus {
    param([string]$Path)
    $status = & curl.exe -sS -o NUL -w "%{http_code}" "$BaseUrl$Path"
    if ($LASTEXITCODE -ne 0) {
        throw "curl failed for $Path"
    }
    return [int]$status
}

function Invoke-Chat {
    param(
        [string]$ConversationId,
        [hashtable]$Headers,
        [string]$Question
    )
    $encodedQuestion = [System.Uri]::EscapeDataString($Question)
    $response = Invoke-WebRequest -Uri "$BaseUrl/rag/v3/chat?conversationId=$ConversationId&question=$encodedQuestion" `
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
    return @{
        ContentType = $contentType
        Length = $response.Content.Length
    }
}

function Get-LatestSnapshot {
    param([string]$ConversationId)
    Start-Sleep -Seconds 2
    $sql = @"
select run_id,
       coalesce(role_card_id::text, ''),
       coalesce(run_profile_id::text, ''),
       executor_engine,
       coalesce(snapshot_json::jsonb #>> '{runProfile,name}', '') as run_profile_name
from t_run_context_snapshot
where conversation_id = $ConversationId
  and deleted = 0
order by create_time desc
limit 1;
"@
    $row = Invoke-DbScalarRow $sql
    if (-not $row) {
        throw "No t_run_context_snapshot row found for conversation $ConversationId"
    }
    $parts = $row -split "\|", 5
    if ($parts.Count -lt 5) {
        throw "Unexpected snapshot row format: $row"
    }
    return [PSCustomObject]@{
        runId = $parts[0]
        roleCardId = $parts[1]
        runProfileId = $parts[2]
        executorEngine = $parts[3]
        runProfileName = $parts[4]
    }
}

Test-Step "Verify AgentScope runtime flags" {
    $envLines = @(& docker.exe inspect $BackendContainer --format '{{range .Config.Env}}{{println .}}{{end}}')
    if (-not ($envLines | Where-Object { $_ -eq "SEAHORSE_AGENTSCOPE_EXECUTOR_ENABLED=true" })) {
        throw "SEAHORSE_AGENTSCOPE_EXECUTOR_ENABLED=true was not found on $BackendContainer"
    }
    if ($envLines | Where-Object { $_ -eq "SEAHORSE_AGENTSCOPE_A2A_ENABLED=true" }) {
        $script:a2aEnabled = $true
    } elseif ($envLines | Where-Object { $_ -eq "SEAHORSE_AGENTSCOPE_A2A_ENABLED=false" }) {
        $script:a2aEnabled = $false
    } else {
        throw "SEAHORSE_AGENTSCOPE_A2A_ENABLED was not found on $BackendContainer"
    }
}

$login = Test-Step "Login" {
    $body = @{ username = $Username; password = $Password } | ConvertTo-Json -Compress
    $response = Invoke-RestMethod -Uri "$BaseUrl/auth/login" -Method Post -ContentType "application/json" -Body $body
    Assert-ApiOk $response "Login"
    if (-not $response.data.token) {
        throw "Login response did not include token"
    }
    $response
}
if (-not $login) { exit 1 }

$headers = @{ Authorization = "Bearer $($login.data.token)" }

$profiles = Test-Step "Find AgentScope and kernel run profiles" {
    $response = Invoke-RestMethod -Uri "$BaseUrl/api/run-profiles" -Headers $headers
    Assert-ApiOk $response "List run profiles"
    $agentScope = @($response.data | Where-Object { "$($_.id)" -eq "$AgentScopeRunProfileId" }) | Select-Object -First 1
    $kernel = @($response.data | Where-Object { "$($_.id)" -eq "$KernelRunProfileId" }) | Select-Object -First 1
    if (-not $agentScope) {
        throw "Run profile $AgentScopeRunProfileId was not returned"
    }
    if (-not $kernel) {
        throw "Run profile $KernelRunProfileId was not returned"
    }
    Assert-Equal $agentScope.executorEngine "agentscope" "AgentScope profile executor"
    Assert-Equal $kernel.executorEngine "kernel" "Kernel profile executor"
    @{ agentScope = $agentScope; kernel = $kernel }
}
if (-not $profiles) { exit 1 }

$agentScopeConversationId = Test-Step "Create AgentScope conversation and apply profile" {
    $created = Invoke-RestMethod -Uri "$BaseUrl/api/conversations" -Method Post -Headers $headers
    Assert-ApiOk $created "Create conversation"
    $conversationId = "$($created.data)"
    $applied = Invoke-RestMethod -Uri "$BaseUrl/api/conversations/$conversationId/run-profile/$AgentScopeRunProfileId/apply" `
        -Method Post -Headers $headers
    Assert-ApiOk $applied "Apply AgentScope run profile"
    Assert-Equal $applied.data.runProfileId $AgentScopeRunProfileId "Applied AgentScope runProfileId"
    $conversationId
}
if (-not $agentScopeConversationId) { exit 1 }

$agentScopeChat = Test-Step "Chat through AgentScope run profile" {
    Invoke-Chat -ConversationId $agentScopeConversationId -Headers $headers `
        -Question "AgentScope smoke $(Get-Date -Format yyyyMMddHHmmss): answer with one short sentence."
}

$agentScopeSnapshot = Test-Step "Verify AgentScope run context snapshot" {
    $snapshot = Get-LatestSnapshot -ConversationId $agentScopeConversationId
    Assert-Equal $snapshot.runProfileId $AgentScopeRunProfileId "AgentScope snapshot run_profile_id"
    Assert-Equal $snapshot.executorEngine "agentscope" "AgentScope snapshot executor_engine"
    $snapshot | ConvertTo-Json -Compress | Write-Host
    $snapshot
}

Test-Step "Verify A2A endpoint boundary" {
    if ($script:a2aEnabled) {
        Assert-Equal (Invoke-HttpStatus "/a2a") 200 "A2A agent card status"
        Assert-Equal (Invoke-HttpStatus "/a2a/.well-known/agent-card.json") 404 "A2A legacy well-known status"

        $card = Invoke-RestMethod -Uri "$BaseUrl/a2a" -Method Get
        if ([string]::IsNullOrWhiteSpace("$($card.name)") -or "$($card.url)" -notlike "*/a2a") {
            throw "A2A agent card is missing name or endpoint url: $($card | ConvertTo-Json -Depth 20 -Compress)"
        }
        $tags = @($card.skills | ForEach-Object { @($_.tags) })
        if (-not ($tags | Where-Object { "$_" -like "seahorse:a2a:authMode=*" })) {
            throw "A2A agent card did not expose auth mode metadata"
        }
        $status = & curl.exe -sS -o NUL -w "%{http_code}" -X POST "$BaseUrl/a2a" `
            -H "Content-Type: application/json" --data "{}"
        if ($LASTEXITCODE -ne 0) {
            throw "curl failed for unauthorized A2A POST"
        }
        Assert-Equal ([int]$status) 401 "A2A unauthenticated POST status"
    } else {
        Assert-Equal (Invoke-HttpStatus "/a2a") 404 "A2A endpoint status"
        Assert-Equal (Invoke-HttpStatus "/a2a/.well-known/agent-card.json") 404 "A2A legacy well-known status"
    }
}

$kernelConversationId = Test-Step "Create kernel conversation and apply profile" {
    $created = Invoke-RestMethod -Uri "$BaseUrl/api/conversations" -Method Post -Headers $headers
    Assert-ApiOk $created "Create kernel conversation"
    $conversationId = "$($created.data)"
    $applied = Invoke-RestMethod -Uri "$BaseUrl/api/conversations/$conversationId/run-profile/$KernelRunProfileId/apply" `
        -Method Post -Headers $headers
    Assert-ApiOk $applied "Apply kernel run profile"
    Assert-Equal $applied.data.runProfileId $KernelRunProfileId "Applied kernel runProfileId"
    $conversationId
}

$kernelChat = Test-Step "Chat still works through kernel run profile" {
    Invoke-Chat -ConversationId $kernelConversationId -Headers $headers `
        -Question "Kernel smoke after AgentScope $(Get-Date -Format yyyyMMddHHmmss): answer with one short sentence."
}

$kernelSnapshot = Test-Step "Verify kernel run context snapshot" {
    $snapshot = Get-LatestSnapshot -ConversationId $kernelConversationId
    Assert-Equal $snapshot.runProfileId $KernelRunProfileId "Kernel snapshot run_profile_id"
    Assert-Equal $snapshot.executorEngine "kernel" "Kernel snapshot executor_engine"
    $snapshot | ConvertTo-Json -Compress | Write-Host
    $snapshot
}

Write-Host "`nSummary: $passed / $total passed, $failed failed" -ForegroundColor Cyan
Write-Host "AgentScope conversation ID: $agentScopeConversationId"
Write-Host "AgentScope run ID: $($agentScopeSnapshot.runId)"
Write-Host "AgentScope chat bytes: $($agentScopeChat.Length)"
Write-Host "Kernel conversation ID: $kernelConversationId"
Write-Host "Kernel run ID: $($kernelSnapshot.runId)"
Write-Host "Kernel chat bytes: $($kernelChat.Length)"

if ($failed -gt 0) {
    exit 1
}
