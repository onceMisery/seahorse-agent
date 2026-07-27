param(
    [string]$BaseUrl = "http://127.0.0.1:9090",
    [string]$Username = "admin",
    [string]$Password = "admin123",
    [string]$NonAdminUsername = "demo_user_001",
    [string]$NonAdminPassword = "demo123",
    [string]$PublicLargeTextUrl = "https://raw.githubusercontent.com/onceMisery/seahorse-agent/main/README.md",
    [string]$PostgresContainer = "seahorse-postgres",
    [string]$PostgresUser = "seahorse",
    [string]$PostgresDatabase = "seahorse",
    [string]$BackendContainer = "seahorse-backend",
    [string]$StorageRoot = "/app/seahorse-agent-storage",
    [string]$Marker = "",
    [switch]$SkipHealth
)

$ErrorActionPreference = "Stop"
$passed = 0
$failed = 0
$total = 0
$runError = $null
$adminHeaders = $null
$nonAdminHeaders = $null
$conversationId = $null
$runId = $null
$artifactId = $null
$storageRef = $null
$objectPath = $null
$tenantMutated = $false
$downloadFile = $null

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
        [string]$Method,
        [string]$Path,
        [object]$Body = $null,
        [hashtable]$Headers = @{},
        [int]$ExpectedStatus = 200,
        [switch]$QuietErrors
    )

    $bodyText = $null
    if ($null -ne $Body) {
        $bodyText = if ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Depth 30 -Compress }
    }

    $tempBodyFile = $null
    $silentArg = if ($QuietErrors) { "-s" } else { "-sS" }
    $arguments = @($silentArg, "-w", "`n%{http_code}", "-X", $Method, "$BaseUrl$Path")
    if ($bodyText) {
        $tempBodyFile = New-TemporaryFile
        Set-Content -LiteralPath $tempBodyFile.FullName -Value $bodyText -Encoding UTF8 -NoNewline
        $arguments += @("-H", "Content-Type: application/json", "--data-binary", "@$($tempBodyFile.FullName)")
    }
    foreach ($key in $Headers.Keys) {
        $arguments += @("-H", "${key}: $($Headers[$key])")
    }

    try {
        $raw = & curl.exe @arguments
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

function Invoke-BinaryFile {
    param(
        [string]$Path,
        [hashtable]$Headers,
        [string]$OutputPath
    )

    $arguments = @("-sS", "-w", "`n%{http_code}", "$BaseUrl$Path", "-o", $OutputPath)
    foreach ($key in $Headers.Keys) {
        $arguments += @("-H", "${key}: $($Headers[$key])")
    }
    $raw = & curl.exe @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "curl exited with $LASTEXITCODE for GET $Path"
    }
    $lines = @($raw)
    if ($lines.Count -eq 0 -or [int]$lines[-1] -ne 200) {
        $status = if ($lines.Count -gt 0) { $lines[-1] } else { "unknown" }
        throw "Expected HTTP 200 but got $status for GET $Path"
    }
}

function Assert-ApiOk {
    param([object]$Response, [string]$Name)

    if ($null -eq $Response -or "$($Response.code)" -ne "0") {
        throw "$Name API error: $($Response | ConvertTo-Json -Depth 30 -Compress)"
    }
}

function Assert-Equal {
    param([object]$Actual, [object]$Expected, [string]$Name)

    if ("$Actual" -cne "$Expected") {
        throw "$Name expected '$Expected' but got '$Actual'"
    }
}

function ConvertTo-SqlLiteral {
    param([string]$Value)

    return "'" + $Value.Replace("'", "''") + "'"
}

function Invoke-PostgresScalar {
    param([string]$Sql)

    $raw = & docker exec $PostgresContainer psql -U $PostgresUser -d $PostgresDatabase -At -c $Sql
    if ($LASTEXITCODE -ne 0) {
        throw "psql exited with $LASTEXITCODE for SQL: $Sql"
    }
    $rows = @($raw | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($rows.Count -eq 0) {
        throw "SQL returned no rows: $Sql"
    }
    return $rows[0]
}

function Invoke-PostgresRows {
    param([string]$Sql)

    $raw = & docker exec $PostgresContainer psql -U $PostgresUser -d $PostgresDatabase -At -c $Sql
    if ($LASTEXITCODE -ne 0) {
        throw "psql exited with $LASTEXITCODE for SQL: $Sql"
    }
    return @($raw | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
}

function Invoke-PostgresNonQuery {
    param([string]$Sql)

    & docker exec $PostgresContainer psql -U $PostgresUser -d $PostgresDatabase -q -c $Sql | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "psql exited with $LASTEXITCODE for SQL: $Sql"
    }
}

function Wait-ForHealth {
    param([int]$Attempts = 90)

    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        try {
            $health = Invoke-Json -Method GET -Path "/actuator/health" -QuietErrors
            if ("$($health.status)" -eq "UP") {
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

function Invoke-GovernedTool {
    param(
        [string]$ToolId,
        [hashtable]$Headers,
        [hashtable]$Body,
        [string]$Name
    )

    $response = Invoke-Json -Method POST -Path "/api/tools/$ToolId/invoke" -Headers $Headers -Body $Body
    Assert-ApiOk $response $Name
    $requiresApproval = $response.data.success -eq $false -and (
        "$($response.data.error)" -eq "TOOL_APPROVAL_REQUIRED" -or
        "$($response.data.reasonCode)" -eq "TOOL_APPROVAL_REQUIRED"
    )
    if (-not $requiresApproval) {
        return $response
    }
    if (-not "$($response.data.approvalId)") {
        throw "$Name required approval without approvalId"
    }
    $approvalId = "$($response.data.approvalId)"
    $approved = Invoke-Json -Method POST -Path "/api/approvals/$approvalId/approve" -Headers $Headers -Body @{
        decisionComment = "Allow tool result spill production smoke"
    }
    Assert-ApiOk $approved "Approve $Name"
    $retry = Invoke-Json -Method POST -Path "/api/tools/$ToolId/invoke" -Headers $Headers -Body $Body
    Assert-ApiOk $retry "Retry $Name"
    return $retry
}

function New-ToolBody {
    param(
        [string]$ToolId,
        [string]$InvocationRunId,
        [string]$Suffix,
        [hashtable]$Arguments,
        [string]$AgentId,
        [string]$VersionId
    )

    return @{
        runId = $InvocationRunId
        stepId = "spill-step-$Suffix"
        toolCallId = "spill-call-$Suffix"
        agentId = $AgentId
        versionId = $VersionId
        tenantId = "untrusted-tenant"
        userId = "untrusted-user"
        agentIdentityId = "untrusted-agent"
        arguments = $Arguments
        resourceRefs = @{}
        idempotencyKey = "spill-idem-$Suffix"
        allowedToolIds = @($ToolId)
    }
}

function Remove-SmokeFixtures {
    $cleanupObjectPaths = [System.Collections.Generic.HashSet[string]]::new(
        [System.StringComparer]::Ordinal)

    if ($tenantMutated -and $artifactId -and $runId) {
        try {
            $artifactLiteral = ConvertTo-SqlLiteral $artifactId
            $runLiteral = ConvertTo-SqlLiteral $runId
            Invoke-PostgresNonQuery "UPDATE sa_agent_artifact SET tenant_id='default' WHERE artifact_id=$artifactLiteral AND run_id=$runLiteral;"
            $script:tenantMutated = $false
        } catch {
            Write-Host "  CLEANUP WARN: could not restore artifact tenant - $($_.Exception.Message)" -ForegroundColor Yellow
            $script:failed++
        }
    }

    if ($objectPath) {
        [void]$cleanupObjectPaths.Add($objectPath)
    }

    if ($runId) {
        try {
            $runLiteral = ConvertTo-SqlLiteral $runId
            $artifactRows = Invoke-PostgresRows @"
SELECT row_to_json(t)::text FROM (
  SELECT artifact_id, storage_ref
  FROM sa_agent_artifact
  WHERE run_id=$runLiteral
    AND provenance_json::jsonb ->> 'kind' = 'tool_result_spill'
) t;
"@
            foreach ($artifactRow in $artifactRows) {
                $cleanupArtifact = $artifactRow | ConvertFrom-Json
                $cleanupArtifactId = "$($cleanupArtifact.artifact_id)"
                $cleanupStorageRef = "$($cleanupArtifact.storage_ref)"
                if ($cleanupArtifactId -cnotmatch '^[A-Za-z0-9._-]+$') {
                    throw "Unsafe cleanup artifact id: $cleanupArtifactId"
                }
                if ($cleanupStorageRef -cnotmatch '^local://agent-artifacts/([A-Za-z0-9._-]+)$') {
                    throw "Unsafe cleanup storage reference for artifact $cleanupArtifactId"
                }
                $cleanupObjectName = $Matches[1]
                if (-not $cleanupObjectName.EndsWith(
                        "-tool-result-$cleanupArtifactId.txt",
                        [StringComparison]::Ordinal)) {
                    throw "Cleanup object is not bound to artifact $cleanupArtifactId"
                }
                [void]$cleanupObjectPaths.Add("$StorageRoot/agent-artifacts/$cleanupObjectName")
            }
        } catch {
            Write-Host "  CLEANUP WARN: could not resolve marker-owned artifact objects - $($_.Exception.Message)" -ForegroundColor Yellow
            $script:failed++
        }
    }

    foreach ($cleanupObjectPath in $cleanupObjectPaths) {
        try {
            & docker exec $BackendContainer rm -f -- $cleanupObjectPath
            if ($LASTEXITCODE -ne 0) {
                throw "docker exec rm exited with $LASTEXITCODE"
            }
            & docker exec $BackendContainer test -e $cleanupObjectPath
            if ($LASTEXITCODE -eq 0) {
                throw "object still exists after removal"
            }
            if ($LASTEXITCODE -ne 1) {
                throw "docker exec test exited with $LASTEXITCODE"
            }
        } catch {
            Write-Host "  CLEANUP WARN: could not remove exact object path $cleanupObjectPath - $($_.Exception.Message)" -ForegroundColor Yellow
            $script:failed++
        }
    }

    if ($runId) {
        try {
            $runLiteral = ConvertTo-SqlLiteral $runId
            $wrongRunStepLiteral = ConvertTo-SqlLiteral "spill-step-$Marker-read-wrong-run"
            Invoke-PostgresNonQuery @"
DELETE FROM sa_agent_artifact
WHERE run_id=$runLiteral
  AND provenance_json::jsonb ->> 'kind' = 'tool_result_spill';
DELETE FROM sa_tool_invocation WHERE run_id=$runLiteral;
DELETE FROM sa_tool_invocation WHERE step_id=$wrongRunStepLiteral;
DELETE FROM sa_approval_request WHERE run_id=$runLiteral OR step_id=$wrongRunStepLiteral;
DELETE FROM sa_agent_run_event_buffer WHERE run_id=$runLiteral;
DELETE FROM sa_agent_run_lease WHERE run_id=$runLiteral;
DELETE FROM sa_agent_checkpoint WHERE run_id=$runLiteral;
DELETE FROM sa_agent_step WHERE run_id=$runLiteral;
DELETE FROM t_run_context_snapshot WHERE run_id=$runLiteral;
DELETE FROM sa_agent_run WHERE run_id=$runLiteral;
"@
        } catch {
            Write-Host "  CLEANUP WARN: could not remove run fixtures - $($_.Exception.Message)" -ForegroundColor Yellow
            $script:failed++
        }
    }

    if ($conversationId -and "$conversationId" -match '^\d+$') {
        try {
            Invoke-PostgresNonQuery @"
DELETE FROM t_conversation_branch_cursor WHERE conversation_id=$conversationId;
DELETE FROM t_conversation_summary WHERE conversation_id=$conversationId;
DELETE FROM t_message WHERE conversation_id=$conversationId;
DELETE FROM t_conversation WHERE conversation_id=$conversationId;
"@
        } catch {
            Write-Host "  CLEANUP WARN: could not remove conversation fixture - $($_.Exception.Message)" -ForegroundColor Yellow
            $script:failed++
        }
    }

    if ($downloadFile) {
        Remove-Item -LiteralPath $downloadFile -Force -ErrorAction SilentlyContinue
    }

    if ($runId) {
        try {
            $runLiteral = ConvertTo-SqlLiteral $runId
            $wrongRunStepLiteral = ConvertTo-SqlLiteral "spill-step-$Marker-read-wrong-run"
            $residual = Invoke-PostgresScalar @"
SELECT
  (SELECT count(*) FROM sa_agent_run WHERE run_id=$runLiteral) || '|' ||
  (SELECT count(*) FROM sa_tool_invocation WHERE run_id=$runLiteral OR step_id=$wrongRunStepLiteral) || '|' ||
  (SELECT count(*) FROM sa_approval_request WHERE run_id=$runLiteral OR step_id=$wrongRunStepLiteral) || '|' ||
  (SELECT count(*) FROM sa_agent_artifact WHERE run_id=$runLiteral);
"@
            $counts = "$residual" -split '\|'
            if ($counts.Count -ne 4 -or @($counts | Where-Object { $_ -ne '0' }).Count -gt 0) {
                throw "cleanup residual counts run|audit|approval|artifact=$residual"
            }
        } catch {
            Write-Host "  CLEANUP WARN: could not prove zero run residuals - $($_.Exception.Message)" -ForegroundColor Yellow
            $script:failed++
        }
    }

    if ($conversationId -and "$conversationId" -match '^\d+$') {
        try {
            $conversationResidual = Invoke-PostgresScalar "SELECT count(*) FROM t_conversation WHERE conversation_id=$conversationId;"
            if ("$conversationResidual" -ne '0') {
                throw "cleanup residual conversation count=$conversationResidual"
            }
        } catch {
            Write-Host "  CLEANUP WARN: could not prove zero conversation residuals - $($_.Exception.Message)" -ForegroundColor Yellow
            $script:failed++
        }
    }
}

if ([string]::IsNullOrWhiteSpace($Marker)) {
    $Marker = "spill-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())"
}

try {
    if (-not $SkipHealth) {
        Test-Step "Backend health is UP" {
            Wait-ForHealth
        } | Out-Null
    }

    $login = Test-Step "Authenticate real admin and non-admin users" {
        $admin = Invoke-Json -Method POST -Path "/auth/login" -Body @{
            username = $Username
            password = $Password
        }
        Assert-ApiOk $admin "Admin login"
        $nonAdmin = Invoke-Json -Method POST -Path "/auth/login" -Body @{
            username = $NonAdminUsername
            password = $NonAdminPassword
        }
        Assert-ApiOk $nonAdmin "Non-admin login"
        if (-not "$($admin.data.token)" -or -not "$($nonAdmin.data.token)") {
            throw "Login response did not include both tokens"
        }
        return [pscustomobject]@{
            Admin = $admin.data
            NonAdmin = $nonAdmin.data
        }
    }
    $adminHeaders = @{
        Authorization = "Bearer $($login.Admin.token)"
        "X-User-Id" = "$($login.Admin.userId)"
    }
    $nonAdminHeaders = @{
        Authorization = "Bearer $($login.NonAdmin.token)"
        "X-User-Id" = "$($login.NonAdmin.userId)"
    }

    Test-Step "Catalog exposes web_fetch and read_tool_result" {
        foreach ($toolId in @("web_fetch", "read_tool_result")) {
            $response = Invoke-Json -Method GET -Path "/api/tools?current=1&size=20&keyword=$toolId" -Headers $adminHeaders
            Assert-ApiOk $response "Tool catalog $toolId"
            $tool = @($response.data.records | Where-Object { "$($_.toolId)" -eq $toolId })[0]
            if ($null -eq $tool -or $tool.enabled -ne $true) {
                throw "$toolId is not enabled in the real tool catalog"
            }
            Assert-Equal $tool.riskLevel "LOW" "$toolId riskLevel"
            Assert-Equal $tool.actionType "READ" "$toolId actionType"
        }
    } | Out-Null

    $fixture = Test-Step "Create a persisted conversation and API-triggered agent run" {
        $agentRow = Invoke-PostgresScalar @"
SELECT d.agent_id || '|' || COALESCE(d.latest_version_id, v.version_id)
FROM sa_agent_definition d
LEFT JOIN sa_agent_version v ON v.agent_id=d.agent_id
WHERE d.tenant_id='default'
  AND COALESCE(d.latest_version_id, v.version_id) IS NOT NULL
ORDER BY d.updated_at DESC
LIMIT 1;
"@
        $parts = $agentRow -split '\|'
        if ($parts.Count -ne 2) {
            throw "No real agent/version fixture is available"
        }
        $conversation = Invoke-Json -Method POST -Path "/api/conversations" -Headers $adminHeaders
        Assert-ApiOk $conversation "Create conversation"
        if ("$($conversation.data)" -notmatch '^\d+$') {
            throw "Conversation id is not a safe numeric id: $($conversation.data)"
        }
        $script:conversationId = "$($conversation.data)"
        $run = Invoke-Json -Method POST -Path "/api/agents/$($parts[0])/runs" -Headers $adminHeaders -Body @{
            versionId = $parts[1]
            tenantId = "default"
            conversationId = "$($conversation.data)"
            triggerType = "API"
            inputSummary = "Tool result spill smoke $Marker"
            traceId = "trace-$Marker"
        }
        Assert-ApiOk $run "Create agent run"
        if (-not "$($run.data.runId)") {
            throw "Create agent run response did not include runId"
        }
        $script:runId = "$($run.data.runId)"
        return [pscustomobject]@{
            AgentId = $parts[0]
            VersionId = $parts[1]
            ConversationId = "$($conversation.data)"
            RunId = "$($run.data.runId)"
        }
    }
    $conversationId = $fixture.ConversationId
    $runId = $fixture.RunId

    Test-Step "Reject oversized audit identifiers before JDBC persistence" {
        $body = New-ToolBody `
            -ToolId "read_tool_result" `
            -InvocationRunId $runId `
            -Suffix "$Marker-overflow" `
            -Arguments @{ artifactId = "not-created"; offset = 0; limit = 32 } `
            -AgentId $fixture.AgentId `
            -VersionId $fixture.VersionId
        $body.stepId = "s" * 65
        $response = Invoke-Json `
            -Method POST `
            -Path "/api/tools/read_tool_result/invoke" `
            -Headers $adminHeaders `
            -Body $body `
            -ExpectedStatus 400
        Assert-Equal $response.code "INVALID_ARGUMENT" "oversized identifier error code"
        Assert-Equal $response.message "stepId must not exceed 64 characters" `
            "oversized identifier error message"
    } | Out-Null

    $pointer = Test-Step "Invoke real web_fetch and receive a governed spill pointer" {
        $body = New-ToolBody `
            -ToolId "web_fetch" `
            -InvocationRunId $runId `
            -Suffix "$Marker-web-fetch" `
            -Arguments @{ url = $PublicLargeTextUrl; maxChars = 20000 } `
            -AgentId $fixture.AgentId `
            -VersionId $fixture.VersionId
        $response = Invoke-GovernedTool -ToolId "web_fetch" -Headers $adminHeaders -Body $body -Name "web_fetch"
        if ($response.data.success -ne $true) {
            throw "web_fetch failed: $($response.data | ConvertTo-Json -Depth 30 -Compress)"
        }
        $content = "$($response.data.content)"
        if ($content.Length -ge 8192) {
            throw "Gateway returned the full oversized result instead of a bounded pointer"
        }
        $parsed = $content | ConvertFrom-Json
        if (-not "$($parsed.artifactId)") {
            throw "Pointer artifactId is missing"
        }
        $script:artifactId = "$($parsed.artifactId)"
        Assert-Equal $parsed.kind "tool_result_spill" "pointer kind"
        Assert-Equal $parsed.toolId "web_fetch" "pointer toolId"
        Assert-Equal $parsed.readToolId "read_tool_result" "pointer readToolId"
        Assert-Equal $parsed.contentType "text/plain; charset=utf-8" "pointer contentType"
        if ([int]$parsed.contentChars -le 8192 -or [int]$parsed.contentBytes -lt [int]$parsed.contentChars) {
            throw "Pointer length metadata does not prove an oversized UTF-8 result"
        }
        if ("$($parsed.contentSha256)" -cnotmatch '^[0-9a-f]{64}$') {
            throw "Pointer contentSha256 is not lowercase SHA-256"
        }
        if ("$($parsed.preview)".Length -gt 803) {
            throw "Pointer preview is invalid"
        }
        if ($content -match 'storageRef|storage_ref|local://|s3://') {
            throw "Pointer leaked an internal storage reference"
        }
        return $parsed
    }
    $artifactId = "$($pointer.artifactId)"

    $dbArtifact = Test-Step "Verify canonical AgentArtifact row and active object reference" {
        $artifactLiteral = ConvertTo-SqlLiteral $artifactId
        $row = Invoke-PostgresScalar @"
SELECT row_to_json(t)::text FROM (
  SELECT artifact_id, run_id, tenant_id, user_id, artifact_type, mime_type,
         storage_ref, preview_text, provenance_json, scan_status
  FROM sa_agent_artifact
  WHERE artifact_id=$artifactLiteral
) t;
"@
        $artifact = $row | ConvertFrom-Json
        Assert-Equal $artifact.artifact_id $artifactId "DB artifactId"
        Assert-Equal $artifact.run_id $runId "DB runId"
        Assert-Equal $artifact.tenant_id "default" "DB tenantId"
        Assert-Equal $artifact.user_id $Username "DB userId"
        Assert-Equal $artifact.artifact_type "FILE" "DB artifactType"
        Assert-Equal $artifact.mime_type "text/plain; charset=utf-8" "DB mimeType"
        Assert-Equal $artifact.scan_status "CLEAN" "DB scanStatus"
        Assert-Equal $artifact.preview_text $pointer.preview "DB preview"
        $provenance = "$($artifact.provenance_json)" | ConvertFrom-Json
        Assert-Equal $provenance.kind "tool_result_spill" "provenance kind"
        Assert-Equal $provenance.contentChars $pointer.contentChars "provenance contentChars"
        Assert-Equal $provenance.contentBytes $pointer.contentBytes "provenance contentBytes"
        Assert-Equal $provenance.contentSha256 $pointer.contentSha256 "provenance contentSha256"
        Assert-Equal $provenance.contentType $pointer.contentType "provenance contentType"
        if ("$($artifact.storage_ref)" -cnotmatch '^local://agent-artifacts/([A-Za-z0-9._-]+)$') {
            throw "Unexpected active ObjectStorage reference: $($artifact.storage_ref)"
        }
        $objectName = $Matches[1]
        if (-not $objectName.EndsWith("-tool-result-$artifactId.txt", [StringComparison]::Ordinal)) {
            throw "Object name is not bound to the returned artifact id: $objectName"
        }
        $script:storageRef = "$($artifact.storage_ref)"
        $script:objectPath = "$StorageRoot/agent-artifacts/$objectName"
        return [pscustomobject]@{
            Artifact = $artifact
            ObjectName = $objectName
        }
    }
    $storageRef = "$($dbArtifact.Artifact.storage_ref)"
    $objectPath = "$StorageRoot/agent-artifacts/$($dbArtifact.ObjectName)"

    Test-Step "Artifact APIs expose metadata without storageRef" {
        $detail = Invoke-Json -Method GET -Path "/api/agent-artifacts/$artifactId" -Headers $adminHeaders
        Assert-ApiOk $detail "Agent artifact detail"
        Assert-Equal $detail.data.artifactId $artifactId "artifact API id"
        Assert-Equal $detail.data.runId $runId "artifact API runId"
        Assert-Equal $detail.data.mimeType "text/plain; charset=utf-8" "artifact API mimeType"
        $list = Invoke-Json -Method GET -Path "/api/agent-runs/$runId/artifacts" -Headers $adminHeaders
        Assert-ApiOk $list "Agent artifact list"
        $matched = @($list.data | Where-Object { "$($_.artifactId)" -eq $artifactId })
        if ($matched.Count -ne 1) {
            throw "Run artifact API did not return exactly one spill artifact"
        }
        $serialized = @($detail.data, $matched[0]) | ConvertTo-Json -Depth 30 -Compress
        if ($serialized -match 'storageRef|storage_ref|local://|s3://') {
            throw "Agent artifact API leaked an internal storage reference"
        }
    } | Out-Null

    $fullContent = Test-Step "Download the exact object and verify bytes, characters, SHA-256, and preview" {
        & docker exec $BackendContainer test -f $objectPath
        if ($LASTEXITCODE -ne 0) {
            throw "Expected object file is missing: $objectPath"
        }
        $objectSize = & docker exec $BackendContainer stat -c '%s' $objectPath
        if ($LASTEXITCODE -ne 0) {
            throw "Could not stat object file: $objectPath"
        }
        Assert-Equal "$objectSize" "$($pointer.contentBytes)" "object byte size"

        $script:downloadFile = [System.IO.Path]::GetTempFileName()
        Invoke-BinaryFile -Path "/api/agent-artifacts/$artifactId/download" -Headers $adminHeaders -OutputPath $script:downloadFile
        $fileInfo = Get-Item -LiteralPath $script:downloadFile
        Assert-Equal $fileInfo.Length $pointer.contentBytes "download byte size"
        $sha256 = (Get-FileHash -LiteralPath $script:downloadFile -Algorithm SHA256).Hash.ToLowerInvariant()
        Assert-Equal $sha256 $pointer.contentSha256 "download SHA-256"
        $text = [System.IO.File]::ReadAllText($script:downloadFile, [System.Text.Encoding]::UTF8)
        Assert-Equal $text.Length $pointer.contentChars "download character count"
        $expectedPreview = $text.Substring(0, [Math]::Min(800, $text.Length))
        if ($text.Length -gt 800) {
            $expectedPreview += "..."
        }
        Assert-Equal $expectedPreview $pointer.preview "download preview"
        if ($text -notlike '*UNTRUSTED_EXTERNAL_CONTENT*' -or $text -notlike '*contentText*') {
            throw "Downloaded content is not the real web_fetch observation"
        }
        return $text
    }

    Test-Step "Read a middle-to-tail range through read_tool_result" {
        $offset = [Math]::Max(0, $fullContent.Length - 900)
        $limit = 600
        $expectedLength = [Math]::Min($limit, $fullContent.Length - $offset)
        $expected = $fullContent.Substring($offset, $expectedLength)
        $body = New-ToolBody `
            -ToolId "read_tool_result" `
            -InvocationRunId $runId `
            -Suffix "$Marker-read-owned" `
            -Arguments @{ artifactId = $artifactId; offset = $offset; limit = $limit } `
            -AgentId $fixture.AgentId `
            -VersionId $fixture.VersionId
        $response = Invoke-GovernedTool -ToolId "read_tool_result" -Headers $adminHeaders -Body $body -Name "read_tool_result owned"
        if ($response.data.success -ne $true) {
            throw "Owned read failed: $($response.data | ConvertTo-Json -Depth 30 -Compress)"
        }
        $range = "$($response.data.content)" | ConvertFrom-Json
        Assert-Equal $range.artifactId $artifactId "range artifactId"
        Assert-Equal $range.offset $offset "range offset"
        Assert-Equal $range.returnedChars $expectedLength "range returnedChars"
        Assert-Equal $range.content $expected "range content"
        Assert-Equal $range.hasMore ($offset + $expectedLength -lt $fullContent.Length) "range hasMore"
    } | Out-Null

    Test-Step "Wrong run is denied through the real Gateway" {
        $body = New-ToolBody `
            -ToolId "read_tool_result" `
            -InvocationRunId "$runId-other" `
            -Suffix "$Marker-read-wrong-run" `
            -Arguments @{ artifactId = $artifactId; offset = 0; limit = 32 } `
            -AgentId $fixture.AgentId `
            -VersionId $fixture.VersionId
        $response = Invoke-GovernedTool -ToolId "read_tool_result" -Headers $adminHeaders -Body $body -Name "read_tool_result wrong run"
        Assert-Equal $response.data.success $false "wrong run success"
        Assert-Equal $response.data.error "Tool result artifact was not found or is not accessible" "wrong run error"
    } | Out-Null

    Test-Step "Wrong authenticated user is denied through the real Gateway" {
        $body = New-ToolBody `
            -ToolId "read_tool_result" `
            -InvocationRunId $runId `
            -Suffix "$Marker-read-wrong-user" `
            -Arguments @{ artifactId = $artifactId; offset = 0; limit = 32 } `
            -AgentId $fixture.AgentId `
            -VersionId $fixture.VersionId
        $response = Invoke-GovernedTool -ToolId "read_tool_result" -Headers $nonAdminHeaders -Body $body -Name "read_tool_result wrong user"
        Assert-Equal $response.data.success $false "wrong user success"
        Assert-Equal $response.data.error "Tool result artifact was not found or is not accessible" "wrong user error"
    } | Out-Null

    Test-Step "Wrong tenant is denied through the real repository-backed read path" {
        $artifactLiteral = ConvertTo-SqlLiteral $artifactId
        Invoke-PostgresNonQuery "UPDATE sa_agent_artifact SET tenant_id='spill-e2e-other' WHERE artifact_id=$artifactLiteral;"
        $script:tenantMutated = $true
        $body = New-ToolBody `
            -ToolId "read_tool_result" `
            -InvocationRunId $runId `
            -Suffix "$Marker-read-wrong-tenant" `
            -Arguments @{ artifactId = $artifactId; offset = 0; limit = 32 } `
            -AgentId $fixture.AgentId `
            -VersionId $fixture.VersionId
        $response = Invoke-GovernedTool -ToolId "read_tool_result" -Headers $adminHeaders -Body $body -Name "read_tool_result wrong tenant"
        Assert-Equal $response.data.success $false "wrong tenant success"
        Assert-Equal $response.data.error "Tool result artifact was not found or is not accessible" "wrong tenant error"
        Invoke-PostgresNonQuery "UPDATE sa_agent_artifact SET tenant_id='default' WHERE artifact_id=$artifactLiteral;"
        $script:tenantMutated = $false
    } | Out-Null

    Test-Step "REST and PostgreSQL audits contain bounded summaries only" {
        $tailLength = [Math]::Min(160, $fullContent.Length)
        $tailSample = $fullContent.Substring($fullContent.Length - $tailLength, $tailLength)
        $apiAudit = Invoke-Json -Method GET -Path "/api/tool-invocations?current=1&size=50&runId=$runId" -Headers $adminHeaders
        Assert-ApiOk $apiAudit "Tool invocation audit"
        $apiRecords = @($apiAudit.data.records)
        $webRecords = @($apiRecords | Where-Object { "$($_.toolId)" -eq "web_fetch" })
        if ($webRecords.Count -ne 1) {
            throw "Expected exactly one web_fetch audit record but got $($webRecords.Count)"
        }
        Assert-Equal $webRecords[0].status "SUCCEEDED" "web_fetch audit status"
        if ([int]$webRecords[0].resultSummary.Length -gt 1000) {
            throw "web_fetch audit summary is unexpectedly large"
        }
        $apiJson = $apiRecords | ConvertTo-Json -Depth 30 -Compress
        if ($apiJson.Contains($storageRef) -or $apiJson.Contains($artifactId) -or $apiJson.Contains($tailSample)) {
            throw "Tool audit API leaked storage, artifact pointer, or full-result tail content"
        }

        $runLiteral = ConvertTo-SqlLiteral $runId
        $dbRows = Invoke-PostgresRows @"
SELECT row_to_json(t)::text FROM (
  SELECT tool_id, status, arguments_summary, result_summary, COALESCE(error_message, '') AS error_message
  FROM sa_tool_invocation
  WHERE run_id=$runLiteral
  ORDER BY started_at, invocation_id
) t;
"@
        if ($dbRows.Count -lt 4) {
            throw "Expected persisted web/read audit rows but got $($dbRows.Count)"
        }
        $dbJson = $dbRows -join "`n"
        if ($dbJson.Contains($storageRef) -or $dbJson.Contains($artifactId) -or $dbJson.Contains($tailSample)) {
            throw "PostgreSQL tool audit leaked storage, artifact pointer, or full-result tail content"
        }
        $dbWeb = @($dbRows | ForEach-Object { $_ | ConvertFrom-Json } | Where-Object { "$($_.tool_id)" -eq "web_fetch" })
        if ($dbWeb.Count -ne 1 -or "$($dbWeb[0].status)" -ne "SUCCEEDED") {
            throw "PostgreSQL did not persist exactly one successful web_fetch invocation"
        }
    } | Out-Null
} catch {
    $runError = $_
} finally {
    Remove-SmokeFixtures
}

Write-Host "`nTool result spill smoke: $passed/$total passed, $failed failed. Marker: $Marker" -ForegroundColor $(if ($failed -eq 0 -and $null -eq $runError) { "Green" } else { "Red" })
if ($null -ne $runError) {
    Write-Host "Failure: $($runError.Exception.Message)" -ForegroundColor Red
}
if ($failed -gt 0 -or $null -ne $runError) {
    exit 1
}
