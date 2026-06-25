param(
    [string]$BaseUrl = "http://localhost:9090",
    [string]$Username = "admin",
    [string]$Password = "admin123",
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

function Invoke-DbRows {
    param([string]$Sql)
    $raw = & docker exec $PostgresContainer psql -U $PostgresUser -d $PostgresDatabase -t -A -F "|" -c $Sql
    if ($LASTEXITCODE -ne 0) {
        throw "psql failed with exit code $LASTEXITCODE"
    }
    return @($raw | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
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

$conversationId = Test-Step "Create conversation" {
    $response = Invoke-RestMethod -Uri "$BaseUrl/api/conversations" -Method Post -Headers $headers
    Assert-ApiOk $response "Create conversation"
    if (-not $response.data) {
        throw "Create conversation response did not include id"
    }
    "$($response.data)"
}
if (-not $conversationId) { exit 1 }

$initialMessages = Test-Step "Send chat to create message path" {
    $question = "Message tree branch smoke $(Get-Date -Format yyyyMMddHHmmss): answer with one short word."
    $encodedQuestion = [System.Uri]::EscapeDataString($question)
    $response = Invoke-WebRequest -Uri "$BaseUrl/rag/v3/chat?conversationId=$conversationId&question=$encodedQuestion" `
        -Headers $headers -UseBasicParsing -TimeoutSec 150
    if ([int]$response.StatusCode -ne 200) {
        throw "Chat returned HTTP $($response.StatusCode)"
    }
    if ("$($response.Headers['Content-Type'])" -notlike "*text/event-stream*") {
        throw "Chat did not return text/event-stream"
    }
    if ($response.Content -notlike "*[DONE]*") {
        throw "Chat SSE did not include [DONE]"
    }
    Start-Sleep -Seconds 2
    $messagesResponse = Invoke-RestMethod -Uri "$BaseUrl/api/conversations/$conversationId/messages" -Headers $headers
    Assert-ApiOk $messagesResponse "List messages"
    $messages = @($messagesResponse.data)
    if ($messages.Count -lt 2) {
        throw "Expected at least 2 messages after chat, got $($messages.Count)"
    }
    $userMessage = @($messages | Where-Object { $_.role -eq "user" } | Select-Object -Last 1)[0]
    $assistantMessage = @($messages | Where-Object { $_.role -eq "assistant" } | Select-Object -Last 1)[0]
    if (-not $userMessage -or -not $assistantMessage) {
        throw "Chat did not create both user and assistant messages"
    }
    @{
        User = $userMessage
        Assistant = $assistantMessage
    }
}
if (-not $initialMessages) { exit 1 }

$forkResult = Test-Step "Fork assistant branch" {
    $forkContent = "branch smoke alternate answer"
    $body = @{
        anchorMessageId = [Int64]$initialMessages.Assistant.id
        content = $forkContent
        role = "assistant"
        regenerate = $false
    } | ConvertTo-Json -Compress
    $response = Invoke-RestMethod -Uri "$BaseUrl/api/conversations/$conversationId/messages/fork" `
        -Method Post -ContentType "application/json" -Body $body -Headers $headers
    Assert-ApiOk $response "Fork message"
    if (-not $response.data.newMessageId) {
        throw "Fork response did not include newMessageId"
    }
    Assert-Equal $response.data.parentId $initialMessages.User.id "Fork parentId"
    @{
        NewMessageId = "$($response.data.newMessageId)"
        Content = $forkContent
    }
}
if (-not $forkResult) { exit 1 }

Test-Step "Switch branch and save cursor" {
    $switchBody = @{ targetNodeId = [Int64]$forkResult.NewMessageId } | ConvertTo-Json -Compress
    $switchResponse = Invoke-RestMethod -Uri "$BaseUrl/api/conversations/$conversationId/messages/branch/switch" `
        -Method Post -ContentType "application/json" -Body $switchBody -Headers $headers
    Assert-ApiOk $switchResponse "Switch branch"
    $activeMessages = @($switchResponse.data | ForEach-Object { $_.message })
    if (-not ($activeMessages | Where-Object { "$($_.id)" -eq "$($forkResult.NewMessageId)" -and $_.active -eq 1 })) {
        throw "Switched tree does not mark fork message active"
    }

    $cursorBody = @{ leafMessageId = [Int64]$forkResult.NewMessageId } | ConvertTo-Json -Compress
    $saveCursorResponse = Invoke-RestMethod -Uri "$BaseUrl/api/conversations/$conversationId/branch-cursor" `
        -Method Post -ContentType "application/json" -Body $cursorBody -Headers $headers
    Assert-ApiOk $saveCursorResponse "Save cursor"
    Assert-Equal $saveCursorResponse.data.leafMessageId $forkResult.NewMessageId "Saved cursor leaf"
}

Test-Step "Reload cursor and active tree" {
    $cursorResponse = Invoke-RestMethod -Uri "$BaseUrl/api/conversations/$conversationId/branch-cursor" -Headers $headers
    Assert-ApiOk $cursorResponse "Load cursor"
    Assert-Equal $cursorResponse.data.leafMessageId $forkResult.NewMessageId "Loaded cursor leaf"

    $treeResponse = Invoke-RestMethod -Uri "$BaseUrl/api/conversations/$conversationId/messages/tree" -Headers $headers
    Assert-ApiOk $treeResponse "Load active tree"
    $treeMessages = @($treeResponse.data | ForEach-Object { $_.message })
    if (-not ($treeMessages | Where-Object { "$($_.id)" -eq "$($forkResult.NewMessageId)" -and $_.active -eq 1 })) {
        throw "Reloaded active tree does not include active fork leaf"
    }
    if ($treeMessages.Count -lt 2) {
        throw "Reloaded tree should contain at least root user message and fork leaf"
    }
}

Test-Step "Verify database branch state" {
    $sql = @"
select id,
       coalesce(parent_id::text, ''),
       role,
       active,
       sibling_seq,
       content
from t_message
where conversation_id = $conversationId
  and deleted = 0
order by coalesce(parent_id, 0), sibling_seq, create_time;
"@
    $rows = @(Invoke-DbRows $sql)
    if ($rows.Count -lt 3) {
        throw "Expected at least 3 stored messages after fork, got $($rows.Count)"
    }
    $oldAssistant = $null
    $newAssistant = $null
    foreach ($row in $rows) {
        $parts = $row -split "\|", 6
        if ($parts.Count -lt 6) {
            throw "Unexpected message row format: $row"
        }
        if ("$($parts[0])" -eq "$($initialMessages.Assistant.id)") {
            $oldAssistant = $parts
        }
        if ("$($parts[0])" -eq "$($forkResult.NewMessageId)") {
            $newAssistant = $parts
        }
    }
    if (-not $oldAssistant) {
        throw "Original assistant message missing in DB"
    }
    if (-not $newAssistant) {
        throw "Fork assistant message missing in DB"
    }
    Assert-Equal $oldAssistant[3] "0" "Original assistant active flag"
    Assert-Equal $newAssistant[1] $initialMessages.User.id "Fork parent_id"
    Assert-Equal $newAssistant[2] "assistant" "Fork role"
    Assert-Equal $newAssistant[3] "1" "Fork active flag"
    Assert-Equal $newAssistant[4] "1" "Fork sibling_seq"

    $cursorRows = @(Invoke-DbRows "select leaf_message_id from t_conversation_branch_cursor where conversation_id = $conversationId and deleted = 0;")
    if ($cursorRows.Count -eq 0) {
        throw "No branch cursor row found"
    }
    Assert-Equal $cursorRows[0] $forkResult.NewMessageId "DB cursor leaf"
}

Write-Host "`nSummary: $passed / $total passed, $failed failed" -ForegroundColor Cyan
Write-Host "Conversation ID: $conversationId"
Write-Host "Original user message ID: $($initialMessages.User.id)"
Write-Host "Original assistant message ID: $($initialMessages.Assistant.id)"
Write-Host "Fork message ID: $($forkResult.NewMessageId)"

if ($failed -gt 0) {
    exit 1
}
