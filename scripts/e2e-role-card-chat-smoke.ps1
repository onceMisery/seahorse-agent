param(
    [string]$BaseUrl = "http://127.0.0.1:9090",
    [string]$Username = "admin",
    [string]$Password = "admin123",
    [long]$RoleCardId = -9003,
    [string]$PostgresContainer = "seahorse-postgres",
    [string]$PostgresUser = "seahorse",
    [string]$PostgresDatabase = "seahorse"
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

$roleCard = Test-Step "Find role card $RoleCardId" {
    $response = Invoke-RestMethod -Uri "$BaseUrl/api/role-cards" -Headers $headers
    Assert-ApiOk $response "List role cards"
    $found = @($response.data | Where-Object { "$($_.id)" -eq "$RoleCardId" }) | Select-Object -First 1
    if (-not $found) {
        throw "Role card $RoleCardId was not returned by /api/role-cards"
    }
    if ($found.published -ne 1 -or "$($found.approvalStatus)" -ne "APPROVED") {
        throw "Role card is not published/approved: $($found | ConvertTo-Json -Depth 20 -Compress)"
    }
    $found
}
if (-not $roleCard) { exit 1 }

$conversationId = Test-Step "Create conversation" {
    $response = Invoke-RestMethod -Uri "$BaseUrl/api/conversations" -Method Post -Headers $headers
    Assert-ApiOk $response "Create conversation"
    if (-not $response.data) {
        throw "Create conversation response did not include id"
    }
    "$($response.data)"
}
if (-not $conversationId) { exit 1 }

$chat = Test-Step "Chat with explicit roleCardId" {
    $marker = "ROLE_CARD_CHAT_SMOKE_$(Get-Date -Format yyyyMMddHHmmss)"
    $question = "Role card chat smoke $marker. Reply with one short sentence."
    $encodedQuestion = [System.Uri]::EscapeDataString($question)
    $url = "$BaseUrl/rag/v3/chat?conversationId=$conversationId&roleCardId=$RoleCardId&question=$encodedQuestion"
    $response = Invoke-WebRequest -Uri $url -Headers $headers -UseBasicParsing -TimeoutSec 150
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
    @{
        ContentType = $contentType
        Length = $response.Content.Length
        Marker = $marker
    }
}

$snapshot = Test-Step "Verify role card in run context snapshot" {
    Start-Sleep -Seconds 2
    $sql = @"
select run_id,
       role_card_id,
       coalesce(run_profile_id::text, '') as run_profile_id,
       executor_engine,
       coalesce(snapshot_json::jsonb #>> '{roleCard,name}', '') as role_card_name,
       coalesce(snapshot_json::jsonb #>> '{roleCard,definition}', '') as role_card_definition
from t_run_context_snapshot
where conversation_id = $conversationId
  and deleted = 0
order by create_time desc
limit 1;
"@
    $row = Invoke-DbScalarRow $sql
    if (-not $row) {
        throw "No t_run_context_snapshot row found for conversation $conversationId"
    }
    $parts = $row -split "\|", 6
    if ($parts.Count -lt 6) {
        throw "Unexpected snapshot row format: $row"
    }
    Assert-Equal $parts[1] $RoleCardId "Snapshot role_card_id"
    if ([string]::IsNullOrWhiteSpace($parts[3])) {
        throw "Snapshot executor_engine is missing"
    }
    if ([string]::IsNullOrWhiteSpace($parts[4])) {
        throw "Snapshot roleCard name copy is missing"
    }
    if ([string]::IsNullOrWhiteSpace($parts[5])) {
        throw "Snapshot roleCard definition copy is missing"
    }
    [PSCustomObject]@{
        runId = $parts[0]
        roleCardId = $parts[1]
        runProfileId = $parts[2]
        executorEngine = $parts[3]
        roleCardName = $parts[4]
    } | ConvertTo-Json -Compress | Write-Host
    @{
        RunId = $parts[0]
        RoleCardName = $parts[4]
        ExecutorEngine = $parts[3]
    }
}

Write-Host "`nSummary: $passed / $total passed, $failed failed" -ForegroundColor Cyan
Write-Host "Conversation ID: $conversationId"
Write-Host "Role card ID: $RoleCardId"
Write-Host "Role card name: $($roleCard.name)"
Write-Host "Run ID: $($snapshot.RunId)"
Write-Host "Executor engine: $($snapshot.ExecutorEngine)"
Write-Host "Chat content type: $($chat.ContentType)"
Write-Host "Chat bytes: $($chat.Length)"
Write-Host "Marker: $($chat.Marker)"

if ($failed -gt 0) {
    exit 1
}
