param(
    [string]$BaseUrl = "http://localhost:9090",
    [string]$Username = "admin",
    [string]$Password = "admin123",
    [long]$RunProfileId = -9105,
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
    param(
        [object]$Response,
        [string]$Name
    )
    if ($null -eq $Response -or $Response.code -ne "0") {
        throw "$Name API error: $($Response | ConvertTo-Json -Depth 20 -Compress)"
    }
}

function Assert-Equal {
    param(
        [object]$Actual,
        [object]$Expected,
        [string]$Name
    )
    if ("$Actual" -ne "$Expected") {
        throw "$Name expected '$Expected' but got '$Actual'"
    }
}

function Invoke-DbScalarRow {
    param([string]$Sql)
    $raw = & docker exec $PostgresContainer psql -U $PostgresUser -d $PostgresDatabase -t -A -F "|" -c $Sql
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

$profile = Test-Step "Find run profile $RunProfileId" {
    $response = Invoke-RestMethod -Uri "$BaseUrl/api/run-profiles" -Headers $headers
    Assert-ApiOk $response "List run profiles"
    $found = @($response.data | Where-Object { "$($_.id)" -eq "$RunProfileId" }) | Select-Object -First 1
    if (-not $found) {
        throw "Run profile $RunProfileId was not returned by /api/run-profiles"
    }
    if (-not $found.roleCardId) {
        throw "Run profile $RunProfileId does not bind a role card"
    }
    $found
}
if (-not $profile) { exit 1 }

$conversationId = Test-Step "Create conversation" {
    $response = Invoke-RestMethod -Uri "$BaseUrl/api/conversations" -Method Post -Headers $headers
    Assert-ApiOk $response "Create conversation"
    if (-not $response.data) {
        throw "Create conversation response did not include id"
    }
    "$($response.data)"
}
if (-not $conversationId) { exit 1 }

$applied = Test-Step "Apply run profile to conversation" {
    $response = Invoke-RestMethod -Uri "$BaseUrl/api/conversations/$conversationId/run-profile/$RunProfileId/apply" `
        -Method Post -Headers $headers
    Assert-ApiOk $response "Apply run profile"
    Assert-Equal $response.data.runProfileId $RunProfileId "Applied runProfileId"
    Assert-Equal $response.data.roleCardId $profile.roleCardId "Applied roleCardId"
    $response.data
}

Test-Step "Read applied run profile" {
    $response = Invoke-RestMethod -Uri "$BaseUrl/api/conversations/$conversationId/run-profile" -Headers $headers
    Assert-ApiOk $response "Get applied run profile"
    Assert-Equal $response.data.profile.id $RunProfileId "Conversation profile id"
    Assert-Equal $response.data.profile.roleCardId $profile.roleCardId "Conversation role card id"
}

$chat = Test-Step "Chat without explicit runProfileId" {
    $question = "Run profile inheritance smoke $(Get-Date -Format yyyyMMddHHmmss): answer with one short sentence."
    $encodedQuestion = [System.Uri]::EscapeDataString($question)
    $response = Invoke-WebRequest -Uri "$BaseUrl/rag/v3/chat?conversationId=$conversationId&question=$encodedQuestion" `
        -Headers $headers -UseBasicParsing -TimeoutSec 150
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
    }
}

Test-Step "Verify run context snapshot" {
    Start-Sleep -Seconds 2
    $sql = @"
select run_id,
       role_card_id,
       run_profile_id,
       executor_engine,
       coalesce(snapshot_json::jsonb #>> '{roleCard,name}', '') as role_card_name,
       coalesce(snapshot_json::jsonb #>> '{runProfile,name}', '') as run_profile_name,
       coalesce(snapshot_json::jsonb ->> 'explicitToolAllowlist', '') as explicit_tool_allowlist
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
    $parts = $row -split "\|", 7
    if ($parts.Count -lt 7) {
        throw "Unexpected snapshot row format: $row"
    }
    Assert-Equal $parts[1] $profile.roleCardId "Snapshot role_card_id"
    Assert-Equal $parts[2] $RunProfileId "Snapshot run_profile_id"
    Assert-Equal $parts[3] $profile.executorEngine "Snapshot executor_engine"
    if ([string]::IsNullOrWhiteSpace($parts[4])) {
        throw "Snapshot roleCard copy is missing"
    }
    if ([string]::IsNullOrWhiteSpace($parts[5])) {
        throw "Snapshot runProfile copy is missing"
    }
    [PSCustomObject]@{
        runId = $parts[0]
        roleCardId = $parts[1]
        runProfileId = $parts[2]
        executorEngine = $parts[3]
        roleCardName = $parts[4]
        runProfileName = $parts[5]
        explicitToolAllowlist = $parts[6]
    } | ConvertTo-Json -Compress | Write-Host
}

Write-Host "`nSummary: $passed / $total passed, $failed failed" -ForegroundColor Cyan
Write-Host "Conversation ID: $conversationId"
Write-Host "Run profile ID: $RunProfileId"
Write-Host "Role card ID: $($profile.roleCardId)"
Write-Host "Chat content type: $($chat.ContentType)"
Write-Host "Chat bytes: $($chat.Length)"

if ($failed -gt 0) {
    exit 1
}
