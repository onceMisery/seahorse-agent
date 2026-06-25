param(
    [string]$BaseUrl = "http://localhost:9090",
    [string]$Username = "admin",
    [string]$Password = "admin123",
    [long[]]$RunProfileIds = @(-9101, -9102),
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

function Sql-Quote {
    param([string]$Value)
    return "'" + ($Value -replace "'", "''") + "'"
}

if ($RunProfileIds.Count -lt 2) {
    throw "RunProfileIds must include at least two profiles"
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

$profiles = Test-Step "Verify run profiles" {
    $response = Invoke-RestMethod -Uri "$BaseUrl/api/run-profiles" -Headers $headers
    Assert-ApiOk $response "List run profiles"
    $foundProfiles = @()
    foreach ($profileId in $RunProfileIds) {
        $profile = @($response.data | Where-Object { "$($_.id)" -eq "$profileId" }) | Select-Object -First 1
        if (-not $profile) {
            throw "Run profile $profileId was not returned by /api/run-profiles"
        }
        if ("$($profile.executorEngine)" -ne "kernel") {
            throw "Run profile $profileId uses executorEngine '$($profile.executorEngine)', expected kernel"
        }
        $foundProfiles += $profile
    }
    $foundProfiles
}
if (-not $profiles) { exit 1 }

$conversationId = Test-Step "Create conversation" {
    $response = Invoke-RestMethod -Uri "$BaseUrl/api/conversations" -Method Post -Headers $headers
    Assert-ApiOk $response "Create conversation"
    if (-not $response.data) {
        throw "Create conversation response did not include id"
    }
    "$($response.data)"
}
if (-not $conversationId) { exit 1 }

$baseMessages = Test-Step "Send base chat" {
    $question = "Run experiment smoke $(Get-Date -Format yyyyMMddHHmmss): answer with one short sentence."
    $encodedQuestion = [System.Uri]::EscapeDataString($question)
    $response = Invoke-WebRequest -Uri "$BaseUrl/rag/v3/chat?conversationId=$conversationId&question=$encodedQuestion" `
        -Headers $headers -UseBasicParsing -TimeoutSec 180
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
    $userMessage = @($messages | Where-Object { $_.role -eq "user" } | Select-Object -Last 1)[0]
    $assistantMessage = @($messages | Where-Object { $_.role -eq "assistant" } | Select-Object -Last 1)[0]
    if (-not $userMessage -or -not $assistantMessage) {
        throw "Base chat did not create both user and assistant messages"
    }
    @{
        User = $userMessage
        Assistant = $assistantMessage
    }
}
if (-not $baseMessages) { exit 1 }

$experiment = Test-Step "Create run experiment and execute trials" {
    $body = @{
        conversationId = [Int64]$conversationId
        baseLeafMessageId = [Int64]$baseMessages.Assistant.id
        name = "e2e-run-experiment-$(Get-Date -Format yyyyMMddHHmmss)"
        runProfileIds = @($RunProfileIds)
    } | ConvertTo-Json -Compress
    $response = Invoke-RestMethod -Uri "$BaseUrl/api/run-experiments" `
        -Method Post -ContentType "application/json" -Body $body -Headers $headers -TimeoutSec 240
    Assert-ApiOk $response "Create run experiment"
    if (-not $response.data.experiment.id) {
        throw "Run experiment response did not include experiment id"
    }
    $trials = @($response.data.trials)
    Assert-Equal $trials.Count $RunProfileIds.Count "Trial count"
    foreach ($trial in $trials) {
        if ($trial.status -ne "SUCCEEDED") {
            throw "Trial $($trial.id) status was '$($trial.status)': $($trial.errorMessage)"
        }
        if (-not $trial.runId) {
            throw "Trial $($trial.id) did not include runId"
        }
        if (-not $trial.outputMessageId) {
            throw "Trial $($trial.id) did not include outputMessageId"
        }
        if (-not $trial.metricJson) {
            throw "Trial $($trial.id) did not include metricJson"
        }
    }
    $response.data
}
if (-not $experiment) { exit 1 }

$scored = Test-Step "Score first trial" {
    $firstTrial = @($experiment.trials)[0]
    $body = @{ score = @{ rating = 4; verdict = "smoke-pass" } } | ConvertTo-Json -Compress
    $response = Invoke-RestMethod -Uri "$BaseUrl/api/run-experiments/$($experiment.experiment.id)/trials/$($firstTrial.id)/score" `
        -Method Post -ContentType "application/json" -Body $body -Headers $headers
    Assert-ApiOk $response "Score run experiment trial"
    $updatedTrial = @($response.data.trials | Where-Object { "$($_.id)" -eq "$($firstTrial.id)" }) | Select-Object -First 1
    if (-not $updatedTrial.scoreJson -or $updatedTrial.scoreJson -notlike "*smoke-pass*") {
        throw "Trial scoreJson was not updated: $($updatedTrial.scoreJson)"
    }
    $response.data
}
if (-not $scored) { exit 1 }

$fork = Test-Step "Fork scored trial to branch" {
    $firstTrial = @($scored.trials)[0]
    $response = Invoke-RestMethod -Uri "$BaseUrl/api/run-experiments/$($scored.experiment.id)/trials/$($firstTrial.id)/fork-to-branch" `
        -Method Post -Headers $headers
    Assert-ApiOk $response "Fork trial to branch"
    Assert-Equal $response.data.outputMessageId $firstTrial.outputMessageId "Fork outputMessageId"
    if (-not $response.data.branch) {
        throw "Fork response did not include branch data"
    }
    @{
        TrialId = "$($firstTrial.id)"
        OutputMessageId = "$($firstTrial.outputMessageId)"
    }
}
if (-not $fork) { exit 1 }

Test-Step "Verify database experiment state" {
    $experimentRows = @(Invoke-DbRows "select status, conversation_id, base_leaf_message_id from sa_run_experiment where id = $($experiment.experiment.id) and deleted = 0;")
    if ($experimentRows.Count -ne 1) {
        throw "Expected one experiment row, got $($experimentRows.Count)"
    }
    $experimentParts = $experimentRows[0] -split "\|", 3
    Assert-Equal $experimentParts[0] "SUCCEEDED" "DB experiment status"
    Assert-Equal $experimentParts[1] $conversationId "DB experiment conversation_id"
    Assert-Equal $experimentParts[2] $baseMessages.Assistant.id "DB experiment base_leaf_message_id"

    $trialRows = @(Invoke-DbRows "select id, run_profile_id, status, run_id, output_message_id, score_json, metric_json from sa_run_experiment_trial where experiment_id = $($experiment.experiment.id) and deleted = 0 order by id;")
    Assert-Equal $trialRows.Count $RunProfileIds.Count "DB trial count"
    foreach ($row in $trialRows) {
        $parts = $row -split "\|", 7
        if ($parts.Count -lt 7) {
            throw "Unexpected trial row format: $row"
        }
        Assert-Equal $parts[2] "SUCCEEDED" "DB trial status"
        if ([string]::IsNullOrWhiteSpace($parts[3])) {
            throw "DB trial run_id is blank"
        }
        if ([string]::IsNullOrWhiteSpace($parts[4])) {
            throw "DB trial output_message_id is blank"
        }
        if ([string]::IsNullOrWhiteSpace($parts[6])) {
            throw "DB trial metric_json is blank"
        }
    }
}

Test-Step "Verify snapshots and output messages" {
    $runIds = @($scored.trials | ForEach-Object { Sql-Quote "$($_.runId)" }) -join ","
    $outputIds = @($scored.trials | ForEach-Object { "$($_.outputMessageId)" }) -join ","
    $snapshotCount = @(Invoke-DbRows "select count(*) from t_run_context_snapshot where run_id in ($runIds) and run_profile_id in ($($RunProfileIds -join ',')) and deleted = 0;")[0]
    Assert-Equal $snapshotCount $RunProfileIds.Count "Snapshot count"

    $messageRows = @(Invoke-DbRows "select id, coalesce(agent_run_id, ''), active from t_message where id in ($outputIds) and deleted = 0 order by id;")
    Assert-Equal $messageRows.Count $RunProfileIds.Count "Output message count"
    foreach ($row in $messageRows) {
        $parts = $row -split "\|", 3
        if ([string]::IsNullOrWhiteSpace($parts[1])) {
            throw "Output message $($parts[0]) has blank agent_run_id"
        }
    }
}

Write-Host "`nSummary: $passed / $total passed, $failed failed" -ForegroundColor Cyan
Write-Host "Conversation ID: $conversationId"
Write-Host "Base user message ID: $($baseMessages.User.id)"
Write-Host "Base assistant message ID: $($baseMessages.Assistant.id)"
Write-Host "Experiment ID: $($experiment.experiment.id)"
Write-Host "Forked trial ID: $($fork.TrialId)"
Write-Host "Forked output message ID: $($fork.OutputMessageId)"

if ($failed -gt 0) {
    exit 1
}
