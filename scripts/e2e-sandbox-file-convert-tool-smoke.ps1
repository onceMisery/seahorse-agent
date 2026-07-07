param(
    [string]$BaseUrl = "http://127.0.0.1:9090",
    [string]$Username = "admin",
    [string]$Password = "admin123",
    [string]$Marker = "seahorse-sandbox-file-convert-smoke",
    [string]$PostgresContainer = "seahorse-postgres",
    [string]$PostgresUser = "seahorse",
    [string]$PostgresDatabase = "seahorse",
    [string]$BackendContainer = "seahorse-backend",
    [string]$StorageRoot = "/app/seahorse-agent-storage",
    [string]$ExpectedObjectUriPrefix = "local://sandbox-artifacts/",
    [long]$KernelRunProfileId = -9101,
    [switch]$SkipHealth
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

    for ($attempt = 1; $attempt -le 4; $attempt++) {
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
        if ($status -eq 429 -and $attempt -lt 4) {
            Write-Host "  Rate limited for $Method $Path; waiting for retry window ($attempt/3)" -ForegroundColor Yellow
            Start-Sleep -Seconds 65
            continue
        }
        if ($status -ne $ExpectedStatus) {
            throw "Expected HTTP $ExpectedStatus but got $status for $Method $Path body=$content"
        }
        if ([string]::IsNullOrWhiteSpace($content)) {
            return $null
        }
        return $content | ConvertFrom-Json
    }
}

function Invoke-Text {
    param(
        [string]$Method,
        [string]$Path,
        [hashtable]$Headers = @{},
        [int]$ExpectedStatus = 200
    )

    for ($attempt = 1; $attempt -le 4; $attempt++) {
        $args = @("-sS", "-w", "`n%{http_code}", "-X", $Method, "$BaseUrl$Path")
        foreach ($key in $Headers.Keys) {
            $args += @("-H", "${key}: $($Headers[$key])")
        }

        $raw = & curl.exe @args
        if ($LASTEXITCODE -ne 0) {
            throw "curl exited with $LASTEXITCODE for $Method $Path"
        }
        $lines = @($raw)
        if ($lines.Count -eq 0) {
            throw "empty curl output for $Method $Path"
        }
        $status = [int]$lines[-1]
        $content = if ($lines.Count -gt 1) { ($lines[0..($lines.Count - 2)] -join "`n") } else { "" }
        if ($status -eq 429 -and $attempt -lt 4) {
            Write-Host "  Rate limited for $Method $Path; waiting for retry window ($attempt/3)" -ForegroundColor Yellow
            Start-Sleep -Seconds 65
            continue
        }
        if ($status -ne $ExpectedStatus) {
            throw "Expected HTTP $ExpectedStatus but got $status for $Method $Path body=$content"
        }
        return $content
    }
}

function Assert-ApiOk {
    param([object]$Response, [string]$Name)
    if ($null -eq $Response -or "$($Response.code)" -ne "0") {
        throw "$Name API error: $($Response | ConvertTo-Json -Depth 20 -Compress)"
    }
}

function Get-PageRecords {
    param([object]$Page)
    if ($null -eq $Page) {
        return @()
    }
    if ($null -ne $Page.records) {
        return @($Page.records)
    }
    if ($null -ne $Page.list) {
        return @($Page.list)
    }
    if ($Page -is [System.Array]) {
        return @($Page)
    }
    return @()
}

function Invoke-SandboxFileConvertTool {
    param(
        [hashtable]$Headers,
        [hashtable]$Body,
        [string]$Name
    )

    $response = Invoke-Json -Method POST -Path "/api/tools/sandbox_file_convert/invoke" -Headers $Headers -Body $Body
    Assert-ApiOk $response $Name

    $requiresApproval = $response.data.success -eq $false -and (
        "$($response.data.error)" -eq "TOOL_APPROVAL_REQUIRED" -or
        "$($response.data.reasonCode)" -eq "TOOL_APPROVAL_REQUIRED"
    )
    if (-not $requiresApproval) {
        return $response
    }

    if (-not $response.data.approvalId) {
        throw "$Name required approval but did not return approvalId: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
    }
    $approvalId = "$($response.data.approvalId)"
    $approval = Invoke-Json -Method GET -Path "/api/approvals/$approvalId" -Headers $Headers
    Assert-ApiOk $approval "Read $Name approval"
    if ("$($approval.data.runId)" -ne "$($Body.runId)" -or "$($approval.data.stepId)" -ne "$($Body.stepId)") {
        throw "$Name approval did not match invocation identity: $($approval.data | ConvertTo-Json -Depth 20 -Compress)"
    }
    if ("$($approval.data.status)" -ne "PENDING") {
        throw "$Name approval was not pending: $($approval.data | ConvertTo-Json -Depth 20 -Compress)"
    }

    $approved = Invoke-Json -Method POST -Path "/api/approvals/$approvalId/approve" -Headers $Headers -Body @{
        decisionComment = "Allow sandbox file conversion smoke test"
    }
    Assert-ApiOk $approved "Approve $Name"
    if ("$($approved.data.status)" -ne "APPROVED") {
        throw "$Name approval was not approved: $($approved.data | ConvertTo-Json -Depth 20 -Compress)"
    }

    $retry = Invoke-Json -Method POST -Path "/api/tools/sandbox_file_convert/invoke" -Headers $Headers -Body $Body
    Assert-ApiOk $retry "Retry $Name after approval"
    return $retry
}

function Invoke-ExpectedSandboxFileConvertFailure {
    param(
        [hashtable]$Headers,
        [hashtable]$Body,
        [string]$Name,
        [string]$ExpectedError,
        [string[]]$ForbiddenValues = @()
    )

    $response = Invoke-SandboxFileConvertTool -Headers $Headers -Body $Body -Name $Name
    if ($response.data.success -eq $true) {
        throw "$Name unexpectedly succeeded: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
    }
    $payload = $response.data | ConvertTo-Json -Depth 20 -Compress
    if ($payload -notlike "*$ExpectedError*") {
        throw "$Name did not include expected error '$ExpectedError': $payload"
    }
    foreach ($forbidden in $ForbiddenValues) {
        if (-not [string]::IsNullOrWhiteSpace("$forbidden") -and $payload -like "*$forbidden*") {
            throw "$Name leaked raw file conversion value '$forbidden': $payload"
        }
    }
    return $response
}

function New-RealAgentRunId {
    param(
        [hashtable]$Headers,
        [string]$Marker,
        [long]$RunProfileId
    )

    $created = Invoke-Json -Method POST -Path "/api/conversations" -Headers $Headers
    Assert-ApiOk $created "Create file conversion smoke conversation"
    if (-not $created.data) {
        throw "Create conversation response did not include id"
    }
    $conversationId = "$($created.data)"
    $question = "Sandbox file conversion smoke $Marker. Reply with one short sentence."
    $encodedQuestion = [System.Uri]::EscapeDataString($question)
    $response = Invoke-WebRequest -Uri "$BaseUrl/rag/v3/chat?conversationId=$conversationId&question=$encodedQuestion&runProfileId=$RunProfileId&chatMode=agent" `
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

    $runId = ""
    $matches = [regex]::Matches($response.Content, '"runId"\s*:\s*"([^"]+)"')
    if ($matches.Count -gt 0) {
        $runId = $matches[0].Groups[1].Value
    }
    if ([string]::IsNullOrWhiteSpace($runId)) {
        throw "Chat SSE did not include runId"
    }

    $safeRunId = $runId.Replace("'", "''")
    $row = Invoke-PostgresScalar "SELECT run_id FROM sa_agent_run WHERE run_id = '$safeRunId';"
    if ($row -ne $runId) {
        throw "Agent run was not persisted before tool invocation: $runId"
    }
    return [PSCustomObject]@{
        ConversationId = $conversationId
        RunId = $runId
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
    $raw = & docker exec $PostgresContainer psql -U $PostgresUser -d $PostgresDatabase -At -F "`t" -c $Sql
    if ($LASTEXITCODE -ne 0) {
        throw "psql exited with $LASTEXITCODE for SQL: $Sql"
    }
    $rows = @($raw | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($rows.Count -eq 0) {
        throw "SQL returned no rows: $Sql"
    }
    return $rows[0]
}

function Assert-PersistedFileConversionArtifact {
    param(
        [string]$ArtifactId,
        [string]$ExpectedMediaType,
        [string]$Label
    )

    $safeArtifactId = $ArtifactId.Replace("'", "''")
    $artifactRow = Invoke-PostgresScalar "SELECT session_id, object_uri, media_type, scan_status, sensitivity, scan_summary FROM sa_sandbox_artifact WHERE artifact_id = '$safeArtifactId';"
    $artifactParts = $artifactRow -split "`t"
    if ($artifactParts.Count -ne 6) {
        throw "Unexpected $Label sa_sandbox_artifact row: $artifactRow"
    }
    $persistedSessionId = $artifactParts[0]
    $objectUri = $artifactParts[1]
    if ($objectUri -like "file:*") {
        throw "$Label artifact still points at file URI: $objectUri"
    }
    if ($ExpectedObjectUriPrefix -and $objectUri -notlike "$ExpectedObjectUriPrefix*") {
        throw "Expected $Label object_uri prefix '$ExpectedObjectUriPrefix' but got '$objectUri'"
    }
    if ($artifactParts[2] -ne $ExpectedMediaType) {
        throw "Expected $Label media_type $ExpectedMediaType but got '$($artifactParts[2])'"
    }
    if ($artifactParts[3] -ne "CLEAN") {
        throw "Expected $Label scan_status CLEAN but got '$($artifactParts[3])'"
    }
    if ($artifactParts[4] -ne "INTERNAL") {
        throw "Expected $Label sensitivity INTERNAL but got '$($artifactParts[4])'"
    }
    if ($artifactParts[5] -ne "metadata scan passed") {
        throw "Expected $Label scan_summary metadata scan passed but got '$($artifactParts[5])'"
    }

    $safeSessionId = $persistedSessionId.Replace("'", "''")
    $sessionRow = Invoke-PostgresScalar "SELECT runtime_type, profile_id, status FROM sa_sandbox_session WHERE session_id = '$safeSessionId';"
    $sessionParts = $sessionRow -split "`t"
    if ($sessionParts.Count -ne 3) {
        throw "Unexpected $Label sa_sandbox_session row: $sessionRow"
    }
    if ($sessionParts[0] -ne "FILE_CONVERSION") {
        throw "Expected $Label runtime_type FILE_CONVERSION but got '$($sessionParts[0])'"
    }
    if ($sessionParts[1] -ne "file-conversion") {
        throw "Expected $Label profile_id file-conversion but got '$($sessionParts[1])'"
    }
    if ($sessionParts[2] -ne "CANCELLED") {
        throw "Expected $Label closed session status CANCELLED but got '$($sessionParts[2])'"
    }
    return $objectUri
}

function Add-ZipTextEntry {
    param(
        [System.IO.Compression.ZipArchive]$Archive,
        [string]$Name,
        [string]$Content
    )
    $entry = $Archive.CreateEntry($Name)
    $stream = $entry.Open()
    $writer = [System.IO.StreamWriter]::new($stream, [System.Text.UTF8Encoding]::new($false))
    try {
        $writer.Write($Content)
    } finally {
        $writer.Dispose()
        $stream.Dispose()
    }
}

function New-ZipBase64 {
    param([hashtable]$Entries)
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $tempPath = [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), "seahorse-zip-$([guid]::NewGuid().ToString('N')).zip")
    $fileStream = [System.IO.File]::Open($tempPath, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::ReadWrite)
    $archive = [System.IO.Compression.ZipArchive]::new($fileStream, [System.IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach ($name in $Entries.Keys) {
            Add-ZipTextEntry -Archive $archive -Name "$name" -Content "$($Entries[$name])"
        }
    } finally {
        $archive.Dispose()
        $fileStream.Dispose()
    }
    try {
        return [Convert]::ToBase64String([System.IO.File]::ReadAllBytes($tempPath))
    } finally {
        Remove-Item -LiteralPath $tempPath -ErrorAction SilentlyContinue
    }
}

function New-DocxBase64 {
    param([string[]]$Paragraphs)
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $tempPath = [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), "seahorse-docx-$([guid]::NewGuid().ToString('N')).docx")
    $fileStream = [System.IO.File]::Open($tempPath, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::ReadWrite)
    $archive = [System.IO.Compression.ZipArchive]::new($fileStream, [System.IO.Compression.ZipArchiveMode]::Create)
    try {
        Add-ZipTextEntry -Archive $archive -Name "[Content_Types].xml" -Content @'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>
'@
        Add-ZipTextEntry -Archive $archive -Name "_rels/.rels" -Content @'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>
'@
        $body = ($Paragraphs | ForEach-Object {
                $escaped = [System.Security.SecurityElement]::Escape($_)
                "<w:p><w:r><w:t>$escaped</w:t></w:r></w:p>"
            }) -join ""
        $documentXml = @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>$body</w:body>
</w:document>
"@
        Add-ZipTextEntry -Archive $archive -Name "word/document.xml" -Content $documentXml
    } finally {
        $archive.Dispose()
        $fileStream.Dispose()
    }
    try {
        return [Convert]::ToBase64String([System.IO.File]::ReadAllBytes($tempPath))
    } finally {
        Remove-Item -LiteralPath $tempPath -ErrorAction SilentlyContinue
    }
}

function New-OdtBase64 {
    param([string[]]$Paragraphs)
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $tempPath = [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), "seahorse-odt-$([guid]::NewGuid().ToString('N')).odt")
    $fileStream = [System.IO.File]::Open($tempPath, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::ReadWrite)
    $archive = [System.IO.Compression.ZipArchive]::new($fileStream, [System.IO.Compression.ZipArchiveMode]::Create)
    try {
        Add-ZipTextEntry -Archive $archive -Name "mimetype" -Content "application/vnd.oasis.opendocument.text"
        $body = ($Paragraphs | ForEach-Object {
                $escaped = [System.Security.SecurityElement]::Escape($_)
                "<text:p>$escaped</text:p>"
            }) -join ""
        $contentXml = @"
<?xml version="1.0" encoding="UTF-8"?>
<office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0">
  <office:body><office:text>$body</office:text></office:body>
</office:document-content>
"@
        Add-ZipTextEntry -Archive $archive -Name "content.xml" -Content $contentXml
    } finally {
        $archive.Dispose()
        $fileStream.Dispose()
    }
    try {
        return [Convert]::ToBase64String([System.IO.File]::ReadAllBytes($tempPath))
    } finally {
        Remove-Item -LiteralPath $tempPath -ErrorAction SilentlyContinue
    }
}

function New-OdsBase64 {
    param(
        [string]$Marker,
        [string]$SecondValue = "ODS conversion extracts first table"
    )
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $tempPath = [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), "seahorse-ods-$([guid]::NewGuid().ToString('N')).ods")
    $fileStream = [System.IO.File]::Open($tempPath, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::ReadWrite)
    $archive = [System.IO.Compression.ZipArchive]::new($fileStream, [System.IO.Compression.ZipArchiveMode]::Create)
    try {
        Add-ZipTextEntry -Archive $archive -Name "mimetype" -Content "application/vnd.oasis.opendocument.spreadsheet"
        $escapedMarker = [System.Security.SecurityElement]::Escape("Sandbox ODS $Marker")
        $escapedSecondValue = [System.Security.SecurityElement]::Escape($SecondValue)
        $contentXml = @"
<?xml version="1.0" encoding="UTF-8"?>
<office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0" xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0">
  <office:body>
    <office:spreadsheet>
      <table:table table:name="Sheet1">
        <table:table-row>
          <table:table-cell><text:p>label</text:p></table:table-cell>
          <table:table-cell><text:p>value</text:p></table:table-cell>
        </table:table-row>
        <table:table-row>
          <table:table-cell><text:p>$escapedMarker</text:p></table:table-cell>
          <table:table-cell><text:p>$escapedSecondValue</text:p></table:table-cell>
        </table:table-row>
      </table:table>
    </office:spreadsheet>
  </office:body>
</office:document-content>
"@
        Add-ZipTextEntry -Archive $archive -Name "content.xml" -Content $contentXml
    } finally {
        $archive.Dispose()
        $fileStream.Dispose()
    }
    try {
        return [Convert]::ToBase64String([System.IO.File]::ReadAllBytes($tempPath))
    } finally {
        Remove-Item -LiteralPath $tempPath -ErrorAction SilentlyContinue
    }
}

function New-OdpBase64 {
    param([string[]]$Paragraphs)
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $tempPath = [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), "seahorse-odp-$([guid]::NewGuid().ToString('N')).odp")
    $fileStream = [System.IO.File]::Open($tempPath, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::ReadWrite)
    $archive = [System.IO.Compression.ZipArchive]::new($fileStream, [System.IO.Compression.ZipArchiveMode]::Create)
    try {
        Add-ZipTextEntry -Archive $archive -Name "mimetype" -Content "application/vnd.oasis.opendocument.presentation"
        $slides = ($Paragraphs | ForEach-Object {
                $escaped = [System.Security.SecurityElement]::Escape($_)
                "<draw:page draw:name=`"slide-$([guid]::NewGuid().ToString('N'))`"><draw:frame><draw:text-box><text:p>$escaped</text:p></draw:text-box></draw:frame></draw:page>"
            }) -join ""
        $contentXml = @"
<?xml version="1.0" encoding="UTF-8"?>
<office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:draw="urn:oasis:names:tc:opendocument:xmlns:drawing:1.0" xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0">
  <office:body><office:presentation>$slides</office:presentation></office:body>
</office:document-content>
"@
        Add-ZipTextEntry -Archive $archive -Name "content.xml" -Content $contentXml
    } finally {
        $archive.Dispose()
        $fileStream.Dispose()
    }
    try {
        return [Convert]::ToBase64String([System.IO.File]::ReadAllBytes($tempPath))
    } finally {
        Remove-Item -LiteralPath $tempPath -ErrorAction SilentlyContinue
    }
}

function New-XlsxBase64 {
    param(
        [string]$Marker,
        [string]$SecondValue = "42"
    )
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $tempPath = [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), "seahorse-xlsx-$([guid]::NewGuid().ToString('N')).xlsx")
    $fileStream = [System.IO.File]::Open($tempPath, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::ReadWrite)
    $archive = [System.IO.Compression.ZipArchive]::new($fileStream, [System.IO.Compression.ZipArchiveMode]::Create)
    try {
        Add-ZipTextEntry -Archive $archive -Name "[Content_Types].xml" -Content @'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>
</Types>
'@
        Add-ZipTextEntry -Archive $archive -Name "_rels/.rels" -Content @'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>
'@
        Add-ZipTextEntry -Archive $archive -Name "xl/workbook.xml" -Content @'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets><sheet name="Sheet1" sheetId="1" r:id="rId1"/></sheets>
</workbook>
'@
        $escapedMarker = [System.Security.SecurityElement]::Escape("Sandbox XLSX $Marker")
        Add-ZipTextEntry -Archive $archive -Name "xl/sharedStrings.xml" -Content @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <si><t>label</t></si>
  <si><t>value</t></si>
  <si><t>$escapedMarker</t></si>
</sst>
"@
        $escapedSecondValue = [System.Security.SecurityElement]::Escape($SecondValue)
        Add-ZipTextEntry -Archive $archive -Name "xl/worksheets/sheet1.xml" -Content @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetData>
    <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c></row>
    <row r="2"><c r="A2" t="s"><v>2</v></c><c r="B2" t="str"><v>$escapedSecondValue</v></c></row>
  </sheetData>
</worksheet>
"@
    } finally {
        $archive.Dispose()
        $fileStream.Dispose()
    }
    try {
        return [Convert]::ToBase64String([System.IO.File]::ReadAllBytes($tempPath))
    } finally {
        Remove-Item -LiteralPath $tempPath -ErrorAction SilentlyContinue
    }
}

function New-PptxBase64 {
    param(
        [string]$Marker,
        [string]$SecondValue = "PPTX conversion extracts slide text"
    )
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $tempPath = [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), "seahorse-pptx-$([guid]::NewGuid().ToString('N')).pptx")
    $fileStream = [System.IO.File]::Open($tempPath, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::ReadWrite)
    $archive = [System.IO.Compression.ZipArchive]::new($fileStream, [System.IO.Compression.ZipArchiveMode]::Create)
    try {
        Add-ZipTextEntry -Archive $archive -Name "[Content_Types].xml" -Content @'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>
  <Override PartName="/ppt/slides/slide1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>
</Types>
'@
        Add-ZipTextEntry -Archive $archive -Name "_rels/.rels" -Content @'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
</Relationships>
'@
        Add-ZipTextEntry -Archive $archive -Name "ppt/presentation.xml" -Content @'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"/>
'@
        $escapedMarker = [System.Security.SecurityElement]::Escape("Sandbox PPTX $Marker")
        $escapedSecondValue = [System.Security.SecurityElement]::Escape($SecondValue)
        Add-ZipTextEntry -Archive $archive -Name "ppt/slides/slide1.xml" -Content @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
  <p:cSld>
    <p:spTree>
      <p:sp><p:txBody><a:p><a:r><a:t>$escapedMarker</a:t></a:r></a:p></p:txBody></p:sp>
      <p:sp><p:txBody><a:p><a:r><a:t>$escapedSecondValue</a:t></a:r></a:p></p:txBody></p:sp>
    </p:spTree>
  </p:cSld>
</p:sld>
"@
    } finally {
        $archive.Dispose()
        $fileStream.Dispose()
    }
    try {
        return [Convert]::ToBase64String([System.IO.File]::ReadAllBytes($tempPath))
    } finally {
        Remove-Item -LiteralPath $tempPath -ErrorAction SilentlyContinue
    }
}

function ConvertTo-PdfLiteral {
    param([string]$Value)
    return $Value.Replace('\', '\\').Replace('(', '\(').Replace(')', '\)')
}

function New-PdfBase64 {
    param([string[]]$Lines)
    $textCommands = @($Lines | ForEach-Object {
            $escaped = ConvertTo-PdfLiteral $_
            "($escaped) Tj"
        }) -join "`n0 -18 Td`n"
    $stream = @"
BT
/F1 12 Tf
72 720 Td
$textCommands
ET
"@
    $streamLength = [System.Text.Encoding]::ASCII.GetByteCount($stream)
    $pdf = @"
%PDF-1.4
1 0 obj
<< /Type /Catalog /Pages 2 0 R >>
endobj
2 0 obj
<< /Type /Pages /Kids [3 0 R] /Count 1 >>
endobj
3 0 obj
<< /Type /Page /Parent 2 0 R /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>
endobj
4 0 obj
<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>
endobj
5 0 obj
<< /Length $streamLength >>
stream
$stream
endstream
endobj
trailer
<< /Root 1 0 R >>
%%EOF
"@
    return [Convert]::ToBase64String([System.Text.Encoding]::ASCII.GetBytes($pdf))
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
    $smokeRun = Test-Step "Create real agent run for governed tool artifact binding" {
        New-RealAgentRunId -Headers $headers -Marker $Marker -RunProfileId $KernelRunProfileId
    }
    if (-not $smokeRun) { exit 1 }

    $runId = "$($smokeRun.RunId)"
    $toolCallId = "sandbox-file-convert-call-$suffix"
    $csvContent = "name,score,marker`nAda,42,$Marker`nGrace,99,$Marker`n"

    Test-Step "Verify sandbox_file_convert is cataloged" {
        $response = Invoke-Json -Method GET -Path "/api/tools?current=1&size=50&provider=BUILTIN&keyword=sandbox_file_convert" -Headers $headers
        Assert-ApiOk $response "List built-in tools"
        $records = @($response.data.records)
        $matched = @($records | Where-Object { "$($_.toolId)" -eq "sandbox_file_convert" })
        if ($matched.Count -ne 1) {
            throw "sandbox_file_convert not found in built-in tool catalog: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($matched[0].riskLevel)" -ne "HIGH") {
            throw "Expected sandbox_file_convert riskLevel=HIGH: $($matched[0] | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($matched[0].resourceType)" -ne "SANDBOX") {
            throw "Expected sandbox_file_convert resourceType=SANDBOX: $($matched[0] | ConvertTo-Json -Depth 20 -Compress)"
        }
    } | Out-Null

    $unsupportedContent = "<root>file-convert-unsupported-secret-$suffix</root>"
    $plainPdfContent = "%PDF-1.4 file-convert-pdf-plain-secret-$suffix"
    $invalidBase64Content = "not-base64-file-convert-invalid-secret-$suffix!"
    $encryptedPdfText = "%PDF-1.4`n1 0 obj`n<< /Encrypt 2 0 R /Title (file-convert-encrypted-pdf-secret-$suffix) >>`nendobj"
    $encryptedPdfContent = [Convert]::ToBase64String([System.Text.Encoding]::ASCII.GetBytes($encryptedPdfText))
    $activeDocxMacroMarker = "file-convert-docx-macro-secret-$suffix"
    $activeDocxContent = New-ZipBase64 -Entries @{
        "word/document.xml" = "<w:document><w:p><w:r><w:t>unsafe docx $activeDocxMacroMarker</w:t></w:r></w:p></w:document>"
        "word/vbaProject.bin" = "macro bytes $activeDocxMacroMarker"
    }
    $activePptxMacroMarker = "file-convert-pptx-macro-secret-$suffix"
    $activePptxContent = New-ZipBase64 -Entries @{
        "ppt/slides/slide1.xml" = "<p:sld xmlns:p=`"http://schemas.openxmlformats.org/presentationml/2006/main`" xmlns:a=`"http://schemas.openxmlformats.org/drawingml/2006/main`"><p:cSld><p:spTree><p:sp><p:txBody><a:p><a:r><a:t>unsafe pptx $activePptxMacroMarker</a:t></a:r></a:p></p:txBody></p:sp></p:spTree></p:cSld></p:sld>"
        "ppt/vbaProject.bin" = "macro bytes $activePptxMacroMarker"
    }
    $activeXlsxMacroMarker = "file-convert-xlsx-macro-secret-$suffix"
    $activeXlsxContent = New-ZipBase64 -Entries @{
        "xl/worksheets/sheet1.xml" = "<worksheet xmlns=`"http://schemas.openxmlformats.org/spreadsheetml/2006/main`"><sheetData><row><c t=`"inlineStr`"><is><t>unsafe xlsx $activeXlsxMacroMarker</t></is></c></row></sheetData></worksheet>"
        "xl/vbaProject.bin" = "macro bytes $activeXlsxMacroMarker"
    }
    $activeXlsxActiveXMarker = "file-convert-xlsx-activex-secret-$suffix"
    $activeXlsxActiveXContent = New-ZipBase64 -Entries @{
        "xl/worksheets/sheet1.xml" = "<worksheet xmlns=`"http://schemas.openxmlformats.org/spreadsheetml/2006/main`"><sheetData><row><c t=`"inlineStr`"><is><t>unsafe xlsx activex $activeXlsxActiveXMarker</t></is></c></row></sheetData></worksheet>"
        "xl/activeX/activeX1.xml" = "<ax:ocx ax:classid=`"clsid:$activeXlsxActiveXMarker`" xmlns:ax=`"http://schemas.microsoft.com/office/2006/activeX`" />"
    }
    $activeXlsxEmbeddedMarker = "file-convert-xlsx-embedded-secret-$suffix"
    $activeXlsxEmbeddedContent = New-ZipBase64 -Entries @{
        "xl/worksheets/sheet1.xml" = "<worksheet xmlns=`"http://schemas.openxmlformats.org/spreadsheetml/2006/main`"><sheetData><row><c t=`"inlineStr`"><is><t>unsafe xlsx embedded $activeXlsxEmbeddedMarker</t></is></c></row></sheetData></worksheet>"
        "xl/embeddings/oleObject1.bin" = "embedded object bytes $activeXlsxEmbeddedMarker"
    }
    $activeXlsxExternalLinkMarker = "file-convert-xlsx-external-link-secret-$suffix"
    $activeXlsxExternalLinkContent = New-ZipBase64 -Entries @{
        "xl/worksheets/sheet1.xml" = "<worksheet xmlns=`"http://schemas.openxmlformats.org/spreadsheetml/2006/main`"><sheetData><row><c t=`"inlineStr`"><is><t>unsafe xlsx external link $activeXlsxExternalLinkMarker</t></is></c></row></sheetData></worksheet>"
        "xl/externalLinks/externalLink1.xml" = "<externalLink xmlns=`"http://schemas.openxmlformats.org/spreadsheetml/2006/main`"><externalBook><sheetNames><sheetName val=`"$activeXlsxExternalLinkMarker`" /></sheetNames></externalBook></externalLink>"
    }
    $activeOdtMacroMarker = "file-convert-odt-macro-secret-$suffix"
    $activeOdtContent = New-ZipBase64 -Entries @{
        "content.xml" = "<office:document-content xmlns:office=`"urn:oasis:names:tc:opendocument:xmlns:office:1.0`" xmlns:text=`"urn:oasis:names:tc:opendocument:xmlns:text:1.0`"><office:body><office:text><text:p>unsafe odt $activeOdtMacroMarker</text:p></office:text></office:body></office:document-content>"
        "Scripts/macro.js" = "alert('$activeOdtMacroMarker')"
    }
    $activeOdsMacroMarker = "file-convert-ods-macro-secret-$suffix"
    $activeOdsContent = New-ZipBase64 -Entries @{
        "content.xml" = "<office:document-content xmlns:office=`"urn:oasis:names:tc:opendocument:xmlns:office:1.0`" xmlns:table=`"urn:oasis:names:tc:opendocument:xmlns:table:1.0`" xmlns:text=`"urn:oasis:names:tc:opendocument:xmlns:text:1.0`"><office:body><office:spreadsheet><table:table><table:table-row><table:table-cell><text:p>unsafe ods $activeOdsMacroMarker</text:p></table:table-cell></table:table-row></table:table></office:spreadsheet></office:body></office:document-content>"
        "Scripts/macro.js" = "alert('$activeOdsMacroMarker')"
    }
    $activeOdpMacroMarker = "file-convert-odp-macro-secret-$suffix"
    $activeOdpContent = New-ZipBase64 -Entries @{
        "content.xml" = "<office:document-content xmlns:office=`"urn:oasis:names:tc:opendocument:xmlns:office:1.0`" xmlns:draw=`"urn:oasis:names:tc:opendocument:xmlns:drawing:1.0`" xmlns:text=`"urn:oasis:names:tc:opendocument:xmlns:text:1.0`"><office:body><office:presentation><draw:page draw:name=`"page1`"><draw:frame><draw:text-box><text:p>unsafe odp $activeOdpMacroMarker</text:p></draw:text-box></draw:frame></draw:page></office:presentation></office:body></office:document-content>"
        "Scripts/macro.js" = "alert('$activeOdpMacroMarker')"
    }
    $fileConvertFailureCases = @(
        @{
            Name = "unsupported-conversion"
            StepId = "sandbox-file-convert-unsupported-fail-step-$suffix"
            ToolCallId = "sandbox-file-convert-unsupported-fail-call-$suffix"
            ExpectedError = "supported conversions"
            Arguments = @{
                sourceFormat = "xml"
                targetFormat = "json"
                content = $unsupportedContent
            }
            Required = @(
                '"toolId":"sandbox_file_convert"',
                '"runtimeType":"FILE_CONVERSION"',
                '"sourceFormat":"unsupported"',
                '"sourceFormatPresent":true',
                '"sourceFormatLength":3',
                '"targetFormat":"json"',
                '"targetFormatPresent":true',
                '"contentEncoding":"plain"',
                '"contentEncodingPresent":true',
                '"binaryInput":false',
                '"networkRequested":false',
                '"argumentCount":3',
                '"contentLength":'
            )
            Forbidden = @($unsupportedContent, "file-convert-unsupported-secret-$suffix", "<root>")
        },
        @{
            Name = "pdf-without-base64"
            StepId = "sandbox-file-convert-pdf-encoding-fail-step-$suffix"
            ToolCallId = "sandbox-file-convert-pdf-encoding-fail-call-$suffix"
            ExpectedError = "pdf contentEncoding must be base64"
            Arguments = @{
                sourceFormat = "pdf"
                targetFormat = "txt"
                content = $plainPdfContent
            }
            Required = @(
                '"toolId":"sandbox_file_convert"',
                '"runtimeType":"FILE_CONVERSION"',
                '"sourceFormat":"pdf"',
                '"sourceFormatPresent":true',
                '"targetFormat":"txt"',
                '"targetFormatPresent":true',
                '"contentEncoding":"plain"',
                '"contentEncodingPresent":true',
                '"binaryInput":false',
                '"networkRequested":false',
                '"argumentCount":3',
                '"contentLength":'
            )
            Forbidden = @($plainPdfContent, "file-convert-pdf-plain-secret-$suffix", "%PDF-1.4")
        },
        @{
            Name = "invalid-base64"
            StepId = "sandbox-file-convert-invalid-base64-fail-step-$suffix"
            ToolCallId = "sandbox-file-convert-invalid-base64-fail-call-$suffix"
            ExpectedError = "file conversion content is not valid base64"
            Arguments = @{
                sourceFormat = "pdf"
                targetFormat = "txt"
                contentEncoding = "base64"
                content = $invalidBase64Content
            }
            Required = @(
                '"toolId":"sandbox_file_convert"',
                '"runtimeType":"FILE_CONVERSION"',
                '"sourceFormat":"pdf"',
                '"sourceFormatPresent":true',
                '"targetFormat":"txt"',
                '"targetFormatPresent":true',
                '"contentEncoding":"base64"',
                '"contentEncodingPresent":true',
                '"binaryInput":true',
                '"networkRequested":false',
                '"argumentCount":4',
                '"contentLength":'
            )
            Forbidden = @($invalidBase64Content, "file-convert-invalid-secret-$suffix")
        },
        @{
            Name = "encrypted-pdf"
            StepId = "sandbox-file-convert-encrypted-pdf-fail-step-$suffix"
            ToolCallId = "sandbox-file-convert-encrypted-pdf-fail-call-$suffix"
            ExpectedError = "encrypted pdf is not supported"
            Arguments = @{
                sourceFormat = "pdf"
                targetFormat = "txt"
                contentEncoding = "base64"
                content = $encryptedPdfContent
            }
            Required = @(
                '"toolId":"sandbox_file_convert"',
                '"runtimeType":"FILE_CONVERSION"',
                '"sourceFormat":"pdf"',
                '"sourceFormatPresent":true',
                '"targetFormat":"txt"',
                '"targetFormatPresent":true',
                '"contentEncoding":"base64"',
                '"contentEncodingPresent":true',
                '"binaryInput":true',
                '"networkRequested":false',
                '"argumentCount":4',
                '"contentLength":'
            )
            Forbidden = @($encryptedPdfContent, $encryptedPdfText, "file-convert-encrypted-pdf-secret-$suffix", "/Encrypt")
        },
        @{
            Name = "docx-active-content"
            StepId = "sandbox-file-convert-docx-active-fail-step-$suffix"
            ToolCallId = "sandbox-file-convert-docx-active-fail-call-$suffix"
            ExpectedError = "docx active content is not supported"
            Arguments = @{
                sourceFormat = "docx"
                targetFormat = "txt"
                contentEncoding = "base64"
                content = $activeDocxContent
            }
            Required = @(
                '"toolId":"sandbox_file_convert"',
                '"runtimeType":"FILE_CONVERSION"',
                '"sourceFormat":"docx"',
                '"sourceFormatPresent":true',
                '"targetFormat":"txt"',
                '"targetFormatPresent":true',
                '"contentEncoding":"base64"',
                '"contentEncodingPresent":true',
                '"binaryInput":true',
                '"networkRequested":false',
                '"argumentCount":4',
                '"contentLength":'
            )
            Forbidden = @($activeDocxContent, $activeDocxMacroMarker, "vbaProject.bin", "unsafe docx")
        },
        @{
            Name = "pptx-active-content"
            StepId = "sandbox-file-convert-pptx-active-fail-step-$suffix"
            ToolCallId = "sandbox-file-convert-pptx-active-fail-call-$suffix"
            ExpectedError = "pptx active content is not supported"
            Arguments = @{
                sourceFormat = "pptx"
                targetFormat = "txt"
                contentEncoding = "base64"
                content = $activePptxContent
            }
            Required = @(
                '"toolId":"sandbox_file_convert"',
                '"runtimeType":"FILE_CONVERSION"',
                '"sourceFormat":"pptx"',
                '"sourceFormatPresent":true',
                '"targetFormat":"txt"',
                '"targetFormatPresent":true',
                '"contentEncoding":"base64"',
                '"contentEncodingPresent":true',
                '"binaryInput":true',
                '"networkRequested":false',
                '"argumentCount":4',
                '"contentLength":'
            )
            Forbidden = @($activePptxContent, $activePptxMacroMarker, "vbaProject.bin", "unsafe pptx")
        },
        @{
            Name = "xlsx-active-content"
            StepId = "sandbox-file-convert-xlsx-active-fail-step-$suffix"
            ToolCallId = "sandbox-file-convert-xlsx-active-fail-call-$suffix"
            ExpectedError = "xlsx active content is not supported"
            Arguments = @{
                sourceFormat = "xlsx"
                targetFormat = "csv"
                contentEncoding = "base64"
                content = $activeXlsxContent
            }
            Required = @(
                '"toolId":"sandbox_file_convert"',
                '"runtimeType":"FILE_CONVERSION"',
                '"sourceFormat":"xlsx"',
                '"sourceFormatPresent":true',
                '"targetFormat":"csv"',
                '"targetFormatPresent":true',
                '"contentEncoding":"base64"',
                '"contentEncodingPresent":true',
                '"binaryInput":true',
                '"networkRequested":false',
                '"argumentCount":4',
                '"contentLength":'
            )
            Forbidden = @($activeXlsxContent, $activeXlsxMacroMarker, "vbaProject.bin", "unsafe xlsx")
        },
        @{
            Name = "xlsx-activex-content"
            StepId = "sandbox-file-convert-xlsx-activex-fail-step-$suffix"
            ToolCallId = "sandbox-file-convert-xlsx-activex-fail-call-$suffix"
            ExpectedError = "xlsx active content is not supported"
            Arguments = @{
                sourceFormat = "xlsx"
                targetFormat = "csv"
                contentEncoding = "base64"
                content = $activeXlsxActiveXContent
            }
            Required = @(
                '"toolId":"sandbox_file_convert"',
                '"runtimeType":"FILE_CONVERSION"',
                '"sourceFormat":"xlsx"',
                '"sourceFormatPresent":true',
                '"targetFormat":"csv"',
                '"targetFormatPresent":true',
                '"contentEncoding":"base64"',
                '"contentEncodingPresent":true',
                '"binaryInput":true',
                '"networkRequested":false',
                '"argumentCount":4',
                '"contentLength":'
            )
            Forbidden = @($activeXlsxActiveXContent, $activeXlsxActiveXMarker, "xl/activeX/activeX1.xml", "xl/activex/activex1.xml", "unsafe xlsx activex")
        },
        @{
            Name = "xlsx-embedded-content"
            StepId = "sandbox-file-convert-xlsx-embedded-fail-step-$suffix"
            ToolCallId = "sandbox-file-convert-xlsx-embedded-fail-call-$suffix"
            ExpectedError = "xlsx active content is not supported"
            Arguments = @{
                sourceFormat = "xlsx"
                targetFormat = "csv"
                contentEncoding = "base64"
                content = $activeXlsxEmbeddedContent
            }
            Required = @(
                '"toolId":"sandbox_file_convert"',
                '"runtimeType":"FILE_CONVERSION"',
                '"sourceFormat":"xlsx"',
                '"sourceFormatPresent":true',
                '"targetFormat":"csv"',
                '"targetFormatPresent":true',
                '"contentEncoding":"base64"',
                '"contentEncodingPresent":true',
                '"binaryInput":true',
                '"networkRequested":false',
                '"argumentCount":4',
                '"contentLength":'
            )
            Forbidden = @($activeXlsxEmbeddedContent, $activeXlsxEmbeddedMarker, "xl/embeddings/oleObject1.bin", "xl/embeddings/oleobject1.bin", "unsafe xlsx embedded")
        },
        @{
            Name = "xlsx-external-link-content"
            StepId = "sandbox-file-convert-xlsx-external-link-fail-step-$suffix"
            ToolCallId = "sandbox-file-convert-xlsx-external-link-fail-call-$suffix"
            ExpectedError = "xlsx active content is not supported"
            Arguments = @{
                sourceFormat = "xlsx"
                targetFormat = "csv"
                contentEncoding = "base64"
                content = $activeXlsxExternalLinkContent
            }
            Required = @(
                '"toolId":"sandbox_file_convert"',
                '"runtimeType":"FILE_CONVERSION"',
                '"sourceFormat":"xlsx"',
                '"sourceFormatPresent":true',
                '"targetFormat":"csv"',
                '"targetFormatPresent":true',
                '"contentEncoding":"base64"',
                '"contentEncodingPresent":true',
                '"binaryInput":true',
                '"networkRequested":false',
                '"argumentCount":4',
                '"contentLength":'
            )
            Forbidden = @($activeXlsxExternalLinkContent, $activeXlsxExternalLinkMarker, "xl/externalLinks/externalLink1.xml", "xl/externallinks/externallink1.xml", "unsafe xlsx external link")
        },
        @{
            Name = "odt-active-content"
            StepId = "sandbox-file-convert-odt-active-fail-step-$suffix"
            ToolCallId = "sandbox-file-convert-odt-active-fail-call-$suffix"
            ExpectedError = "odt active content is not supported"
            Arguments = @{
                sourceFormat = "odt"
                targetFormat = "txt"
                contentEncoding = "base64"
                content = $activeOdtContent
            }
            Required = @(
                '"toolId":"sandbox_file_convert"',
                '"runtimeType":"FILE_CONVERSION"',
                '"sourceFormat":"odt"',
                '"sourceFormatPresent":true',
                '"targetFormat":"txt"',
                '"targetFormatPresent":true',
                '"contentEncoding":"base64"',
                '"contentEncodingPresent":true',
                '"binaryInput":true',
                '"networkRequested":false',
                '"argumentCount":4',
                '"contentLength":'
            )
            Forbidden = @($activeOdtContent, $activeOdtMacroMarker, "Scripts/macro.js", "unsafe odt")
        },
        @{
            Name = "ods-active-content"
            StepId = "sandbox-file-convert-ods-active-fail-step-$suffix"
            ToolCallId = "sandbox-file-convert-ods-active-fail-call-$suffix"
            ExpectedError = "ods active content is not supported"
            Arguments = @{
                sourceFormat = "ods"
                targetFormat = "csv"
                contentEncoding = "base64"
                content = $activeOdsContent
            }
            Required = @(
                '"toolId":"sandbox_file_convert"',
                '"runtimeType":"FILE_CONVERSION"',
                '"sourceFormat":"ods"',
                '"sourceFormatPresent":true',
                '"targetFormat":"csv"',
                '"targetFormatPresent":true',
                '"contentEncoding":"base64"',
                '"contentEncodingPresent":true',
                '"binaryInput":true',
                '"networkRequested":false',
                '"argumentCount":4',
                '"contentLength":'
            )
            Forbidden = @($activeOdsContent, $activeOdsMacroMarker, "Scripts/macro.js", "unsafe ods")
        },
        @{
            Name = "odp-active-content"
            StepId = "sandbox-file-convert-odp-active-fail-step-$suffix"
            ToolCallId = "sandbox-file-convert-odp-active-fail-call-$suffix"
            ExpectedError = "odp active content is not supported"
            Arguments = @{
                sourceFormat = "odp"
                targetFormat = "txt"
                contentEncoding = "base64"
                content = $activeOdpContent
            }
            Required = @(
                '"toolId":"sandbox_file_convert"',
                '"runtimeType":"FILE_CONVERSION"',
                '"sourceFormat":"odp"',
                '"sourceFormatPresent":true',
                '"targetFormat":"txt"',
                '"targetFormatPresent":true',
                '"contentEncoding":"base64"',
                '"contentEncodingPresent":true',
                '"binaryInput":true',
                '"networkRequested":false',
                '"argumentCount":4',
                '"contentLength":'
            )
            Forbidden = @($activeOdpContent, $activeOdpMacroMarker, "Scripts/macro.js", "unsafe odp")
        }
    )

    Test-Step "Verify sandbox_file_convert invalid inputs fail closed" {
        foreach ($case in @($fileConvertFailureCases)) {
            $requestBody = @{
                runId = $runId
                stepId = "$($case.StepId)"
                toolCallId = "$($case.ToolCallId)"
                agentId = "legacy-react-agent"
                tenantId = "default"
                userId = "$($login.data.userId)"
                agentIdentityId = "$($login.data.userId)"
                arguments = $case.Arguments
                resourceRefs = @{}
                idempotencyKey = "${runId}:$($case.ToolCallId)"
                allowedToolIds = @("sandbox_file_convert")
            }
            Invoke-ExpectedSandboxFileConvertFailure `
                -Headers $headers `
                -Body $requestBody `
                -Name "Invoke sandbox_file_convert expected failure $($case.Name)" `
                -ExpectedError "$($case.ExpectedError)" `
                -ForbiddenValues @($case.Forbidden) | Out-Null
        }
    } | Out-Null

    $observation = Test-Step "Invoke sandbox_file_convert through Tool Gateway" {
        $requestBody = @{
            runId = $runId
            stepId = "sandbox-file-convert-step-$suffix"
            toolCallId = $toolCallId
            agentId = "legacy-react-agent"
            tenantId = "default"
            userId = "$($login.data.userId)"
            agentIdentityId = "$($login.data.userId)"
            arguments = @{
                sourceFormat = "csv"
                targetFormat = "json"
                content = $csvContent
            }
            resourceRefs = @{}
            idempotencyKey = "${runId}:${toolCallId}"
            allowedToolIds = @("sandbox_file_convert")
        }
        $response = Invoke-SandboxFileConvertTool -Headers $headers -Body $requestBody -Name "Invoke sandbox_file_convert"
        if ($response.data.success -ne $true) {
            throw "sandbox_file_convert failed: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $content = "$($response.data.content)"
        $parsed = $content | ConvertFrom-Json
        if ("$($parsed.runtimeType)" -ne "FILE_CONVERSION") {
            throw "Expected FILE_CONVERSION runtime: $content"
        }
        if ("$($parsed.executionStatus)" -ne "SUCCEEDED") {
            throw "Expected SUCCEEDED execution: $content"
        }
        if ("$($parsed.conversion.sourceFormat)" -ne "csv" -or "$($parsed.conversion.targetFormat)" -ne "json") {
            throw "Unexpected conversion metadata: $content"
        }
        $artifacts = @($parsed.artifacts)
        if ($artifacts.Count -ne 1) {
            throw "Expected one converted artifact: $content"
        }
        if ("$($artifacts[0].mediaType)" -ne "application/json") {
            throw "Expected JSON artifact mediaType: $content"
        }
        if ("$($artifacts[0].scanStatus)" -ne "CLEAN") {
            throw "Expected CLEAN artifact scan status: $content"
        }
        if ("$($artifacts[0].scanSummary)" -ne "metadata scan passed") {
            throw "Expected metadata scan summary: $content"
        }
        if ($artifacts[0].promptVisible -ne $true) {
            throw "Expected prompt-visible converted artifact: $content"
        }
        $parsed
    }
    if (-not $observation) { exit 1 }

    $sessionId = "$($observation.sessionId)"
    $artifactId = "$(@($observation.artifacts)[0].artifactId)"

    $objectUri = Test-Step "Verify persisted FILE_CONVERSION session and JSON artifact" {
        Assert-PersistedFileConversionArtifact -ArtifactId $artifactId -ExpectedMediaType "application/json" -Label "FILE_CONVERSION JSON"
    }
    if (-not $objectUri) { exit 1 }

    Test-Step "Download converted JSON through governed artifact endpoint" {
        $content = Invoke-Text -Method GET -Path "/api/sandbox/artifacts/$artifactId/download" -Headers $headers
        $parsedRows = $content | ConvertFrom-Json
        $rows = if ($parsedRows -is [array]) { @($parsedRows) } else { @($parsedRows) }
        if ($rows.Count -eq 1 -and $rows[0] -is [array]) {
            $rows = @($rows[0])
        }
        if ($rows.Count -ne 2) {
            throw "Expected two converted JSON rows: $content"
        }
        if ("$($rows[0].name)" -ne "Ada" -or "$($rows[0].score)" -ne "42") {
            throw "First converted row mismatch: $content"
        }
        if ("$($rows[1].name)" -ne "Grace" -or "$($rows[1].score)" -ne "99") {
            throw "Second converted row mismatch: $content"
        }
        if ($content -notlike "*$Marker*") {
            throw "Downloaded JSON did not contain marker '$Marker': $content"
        }
        if ($content -match "objectUri|object_uri|storageRef|file:|local://|s3://") {
            throw "Downloaded artifact body leaked storage metadata: $content"
        }
    } | Out-Null

    if ($objectUri.StartsWith("local://sandbox-artifacts/")) {
        Test-Step "Verify local converted object exists in backend storage volume" {
            $key = $objectUri.Substring("local://sandbox-artifacts/".Length)
            if ($key.Contains("'") -or $Marker.Contains("'")) {
                throw "Cannot safely shell-quote key or marker"
            }
            $path = "$StorageRoot/sandbox-artifacts/$key"
            & docker exec $BackendContainer sh -lc "test -f '$path' && grep -F -q '$Marker' '$path'"
            if ($LASTEXITCODE -ne 0) {
                throw "Stored converted object not found or marker missing at $path"
            }
        } | Out-Null
    }

    $jsonToCsvRunId = $runId
    $jsonToCsvToolCallId = "sandbox-file-convert-json-csv-call-$suffix"
    $jsonRows = @(
        [ordered]@{ name = "Lin"; score = "7"; marker = $Marker },
        [ordered]@{ name = "Katherine"; score = "11"; marker = $Marker }
    )
    $jsonContent = $jsonRows | ConvertTo-Json -Depth 10 -Compress

    $jsonToCsvObservation = Test-Step "Invoke sandbox_file_convert JSON to CSV through Tool Gateway" {
        $requestBody = @{
            runId = $jsonToCsvRunId
            stepId = "sandbox-file-convert-json-csv-step-$suffix"
            toolCallId = $jsonToCsvToolCallId
            agentId = "legacy-react-agent"
            tenantId = "default"
            userId = "$($login.data.userId)"
            agentIdentityId = "$($login.data.userId)"
            arguments = @{
                sourceFormat = "json"
                targetFormat = "csv"
                content = $jsonContent
            }
            resourceRefs = @{}
            idempotencyKey = "${jsonToCsvRunId}:${jsonToCsvToolCallId}"
            allowedToolIds = @("sandbox_file_convert")
        }
        $response = Invoke-SandboxFileConvertTool -Headers $headers -Body $requestBody -Name "Invoke sandbox_file_convert JSON to CSV"
        if ($response.data.success -ne $true) {
            throw "sandbox_file_convert JSON to CSV failed: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $content = "$($response.data.content)"
        $parsed = $content | ConvertFrom-Json
        if ("$($parsed.runtimeType)" -ne "FILE_CONVERSION") {
            throw "Expected FILE_CONVERSION runtime for JSON to CSV: $content"
        }
        if ("$($parsed.executionStatus)" -ne "SUCCEEDED") {
            throw "Expected SUCCEEDED JSON to CSV execution: $content"
        }
        if ("$($parsed.conversion.sourceFormat)" -ne "json" -or "$($parsed.conversion.targetFormat)" -ne "csv") {
            throw "Unexpected JSON to CSV conversion metadata: $content"
        }
        $artifacts = @($parsed.artifacts)
        if ($artifacts.Count -ne 1) {
            throw "Expected one JSON to CSV artifact: $content"
        }
        if ("$($artifacts[0].mediaType)" -ne "text/csv") {
            throw "Expected CSV artifact mediaType: $content"
        }
        if ("$($artifacts[0].scanStatus)" -ne "CLEAN") {
            throw "Expected CLEAN JSON to CSV artifact scan status: $content"
        }
        if ("$($artifacts[0].scanSummary)" -ne "metadata scan passed") {
            throw "Expected JSON to CSV metadata scan summary: $content"
        }
        if ($artifacts[0].promptVisible -ne $true) {
            throw "Expected prompt-visible JSON to CSV artifact: $content"
        }
        $parsed
    }
    if (-not $jsonToCsvObservation) { exit 1 }

    $jsonToCsvSessionId = "$($jsonToCsvObservation.sessionId)"
    $jsonToCsvArtifactId = "$(@($jsonToCsvObservation.artifacts)[0].artifactId)"

    $jsonToCsvObjectUri = Test-Step "Verify persisted JSON to CSV session and artifact" {
        Assert-PersistedFileConversionArtifact -ArtifactId $jsonToCsvArtifactId -ExpectedMediaType "text/csv" -Label "JSON to CSV"
    }
    if (-not $jsonToCsvObjectUri) { exit 1 }

    Test-Step "Download converted CSV through governed artifact endpoint" {
        $content = Invoke-Text -Method GET -Path "/api/sandbox/artifacts/$jsonToCsvArtifactId/download" -Headers $headers
        $rows = @($content -split "`r?`n" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | ConvertFrom-Csv)
        if ($rows.Count -ne 2) {
            throw "Expected two converted CSV rows: $content"
        }
        if ("$($rows[0].name)" -ne "Lin" -or "$($rows[0].score)" -ne "7") {
            throw "First converted CSV row mismatch: $content"
        }
        if ("$($rows[1].name)" -ne "Katherine" -or "$($rows[1].score)" -ne "11") {
            throw "Second converted CSV row mismatch: $content"
        }
        if ($content -notlike "*$Marker*") {
            throw "Downloaded CSV did not contain marker '$Marker': $content"
        }
        if ($content -match "objectUri|object_uri|storageRef|file:|local://|s3://") {
            throw "Downloaded CSV artifact body leaked storage metadata: $content"
        }
    } | Out-Null

    if ($jsonToCsvObjectUri.StartsWith("local://sandbox-artifacts/")) {
        Test-Step "Verify local converted CSV object exists in backend storage volume" {
            $key = $jsonToCsvObjectUri.Substring("local://sandbox-artifacts/".Length)
            if ($key.Contains("'") -or $Marker.Contains("'")) {
                throw "Cannot safely shell-quote JSON to CSV key or marker"
            }
            $path = "$StorageRoot/sandbox-artifacts/$key"
            & docker exec $BackendContainer sh -lc "test -f '$path' && grep -F -q '$Marker' '$path'"
            if ($LASTEXITCODE -ne 0) {
                throw "Stored JSON to CSV object not found or marker missing at $path"
            }
        } | Out-Null
    }

    $markdownRunId = $runId
    $markdownToolCallId = "sandbox-file-convert-markdown-html-call-$suffix"
    $markdownContent = "# Sandbox Document`n`nHello **$Marker** from markdown.`n- first item`n"

    $markdownObservation = Test-Step "Invoke sandbox_file_convert Markdown to HTML through Tool Gateway" {
        $requestBody = @{
            runId = $markdownRunId
            stepId = "sandbox-file-convert-markdown-html-step-$suffix"
            toolCallId = $markdownToolCallId
            agentId = "legacy-react-agent"
            tenantId = "default"
            userId = "$($login.data.userId)"
            agentIdentityId = "$($login.data.userId)"
            arguments = @{
                sourceFormat = "markdown"
                targetFormat = "html"
                content = $markdownContent
            }
            resourceRefs = @{}
            idempotencyKey = "${markdownRunId}:${markdownToolCallId}"
            allowedToolIds = @("sandbox_file_convert")
        }
        $response = Invoke-SandboxFileConvertTool -Headers $headers -Body $requestBody -Name "Invoke sandbox_file_convert Markdown to HTML"
        if ($response.data.success -ne $true) {
            throw "sandbox_file_convert Markdown to HTML failed: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $content = "$($response.data.content)"
        $parsed = $content | ConvertFrom-Json
        if ("$($parsed.runtimeType)" -ne "FILE_CONVERSION") {
            throw "Expected FILE_CONVERSION runtime for Markdown to HTML: $content"
        }
        if ("$($parsed.executionStatus)" -ne "SUCCEEDED") {
            throw "Expected SUCCEEDED Markdown to HTML execution: $content"
        }
        if ("$($parsed.conversion.sourceFormat)" -ne "markdown" -or "$($parsed.conversion.targetFormat)" -ne "html") {
            throw "Unexpected Markdown to HTML conversion metadata: $content"
        }
        $artifacts = @($parsed.artifacts)
        if ($artifacts.Count -ne 1) {
            throw "Expected one Markdown to HTML artifact: $content"
        }
        if ("$($artifacts[0].mediaType)" -ne "text/html") {
            throw "Expected HTML artifact mediaType: $content"
        }
        if ("$($artifacts[0].scanStatus)" -ne "CLEAN") {
            throw "Expected CLEAN Markdown to HTML artifact scan status: $content"
        }
        if ("$($artifacts[0].scanSummary)" -ne "metadata scan passed") {
            throw "Expected Markdown to HTML metadata scan summary: $content"
        }
        if ($artifacts[0].promptVisible -ne $true) {
            throw "Expected prompt-visible Markdown to HTML artifact: $content"
        }
        $parsed
    }
    if (-not $markdownObservation) { exit 1 }

    $markdownSessionId = "$($markdownObservation.sessionId)"
    $markdownArtifactId = "$(@($markdownObservation.artifacts)[0].artifactId)"

    $markdownObjectUri = Test-Step "Verify persisted Markdown to HTML session and artifact" {
        Assert-PersistedFileConversionArtifact -ArtifactId $markdownArtifactId -ExpectedMediaType "text/html" -Label "Markdown to HTML"
    }
    if (-not $markdownObjectUri) { exit 1 }

    Test-Step "Download converted HTML through governed artifact endpoint" {
        $content = Invoke-Text -Method GET -Path "/api/sandbox/artifacts/$markdownArtifactId/download" -Headers $headers
        if ($content -notlike "*<h1>Sandbox Document</h1>*") {
            throw "Downloaded HTML did not include heading: $content"
        }
        if ($content -notlike "*<strong>$Marker</strong>*") {
            throw "Downloaded HTML did not include strong marker '$Marker': $content"
        }
        if ($content -notlike "*<li>first item</li>*") {
            throw "Downloaded HTML did not include list item: $content"
        }
        if ($content -match "objectUri|object_uri|storageRef|file:|local://|s3://") {
            throw "Downloaded HTML artifact body leaked storage metadata: $content"
        }
    } | Out-Null

    if ($markdownObjectUri.StartsWith("local://sandbox-artifacts/")) {
        Test-Step "Verify local converted HTML object exists in backend storage volume" {
            $key = $markdownObjectUri.Substring("local://sandbox-artifacts/".Length)
            if ($key.Contains("'") -or $Marker.Contains("'")) {
                throw "Cannot safely shell-quote Markdown to HTML key or marker"
            }
            $path = "$StorageRoot/sandbox-artifacts/$key"
            & docker exec $BackendContainer sh -lc "test -f '$path' && grep -F -q '$Marker' '$path'"
            if ($LASTEXITCODE -ne 0) {
                throw "Stored Markdown to HTML object not found or marker missing at $path"
            }
        } | Out-Null
    }

    $docxRunId = $runId
    $docxToolCallId = "sandbox-file-convert-docx-txt-call-$suffix"
    $docxContent = New-DocxBase64 -Paragraphs @(
        "Sandbox DOCX $Marker",
        "DOCX conversion preserves paragraph text"
    )

    $docxObservation = Test-Step "Invoke sandbox_file_convert DOCX to TXT through Tool Gateway" {
        $requestBody = @{
            runId = $docxRunId
            stepId = "sandbox-file-convert-docx-txt-step-$suffix"
            toolCallId = $docxToolCallId
            agentId = "legacy-react-agent"
            tenantId = "default"
            userId = "$($login.data.userId)"
            agentIdentityId = "$($login.data.userId)"
            arguments = @{
                sourceFormat = "docx"
                targetFormat = "txt"
                contentEncoding = "base64"
                content = $docxContent
            }
            resourceRefs = @{}
            idempotencyKey = "${docxRunId}:${docxToolCallId}"
            allowedToolIds = @("sandbox_file_convert")
        }
        $response = Invoke-SandboxFileConvertTool -Headers $headers -Body $requestBody -Name "Invoke sandbox_file_convert DOCX to TXT"
        if ($response.data.success -ne $true) {
            throw "sandbox_file_convert DOCX to TXT failed: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $content = "$($response.data.content)"
        $parsed = $content | ConvertFrom-Json
        if ("$($parsed.runtimeType)" -ne "FILE_CONVERSION") {
            throw "Expected FILE_CONVERSION runtime for DOCX to TXT: $content"
        }
        if ("$($parsed.executionStatus)" -ne "SUCCEEDED") {
            throw "Expected SUCCEEDED DOCX to TXT execution: $content"
        }
        if ("$($parsed.conversion.sourceFormat)" -ne "docx" -or "$($parsed.conversion.targetFormat)" -ne "txt" -or "$($parsed.conversion.contentEncoding)" -ne "base64") {
            throw "Unexpected DOCX to TXT conversion metadata: $content"
        }
        $artifacts = @($parsed.artifacts)
        if ($artifacts.Count -ne 1) {
            throw "Expected one DOCX to TXT artifact: $content"
        }
        if ("$($artifacts[0].mediaType)" -ne "text/plain") {
            throw "Expected TXT artifact mediaType: $content"
        }
        if ("$($artifacts[0].scanStatus)" -ne "CLEAN") {
            throw "Expected CLEAN DOCX to TXT artifact scan status: $content"
        }
        if ("$($artifacts[0].scanSummary)" -ne "metadata scan passed") {
            throw "Expected DOCX to TXT metadata scan summary: $content"
        }
        if ($artifacts[0].promptVisible -ne $true) {
            throw "Expected prompt-visible DOCX to TXT artifact: $content"
        }
        $parsed
    }
    if (-not $docxObservation) { exit 1 }

    $docxSessionId = "$($docxObservation.sessionId)"
    $docxArtifactId = "$(@($docxObservation.artifacts)[0].artifactId)"

    $docxObjectUri = Test-Step "Verify persisted DOCX to TXT session and artifact" {
        Assert-PersistedFileConversionArtifact -ArtifactId $docxArtifactId -ExpectedMediaType "text/plain" -Label "DOCX to TXT"
    }
    if (-not $docxObjectUri) { exit 1 }

    Test-Step "Download converted TXT through governed artifact endpoint" {
        $content = Invoke-Text -Method GET -Path "/api/sandbox/artifacts/$docxArtifactId/download" -Headers $headers
        if ($content -notlike "*Sandbox DOCX $Marker*") {
            throw "Downloaded TXT did not include marker '$Marker': $content"
        }
        if ($content -notlike "*DOCX conversion preserves paragraph text*") {
            throw "Downloaded TXT did not include second paragraph: $content"
        }
        if ($content -match "objectUri|object_uri|storageRef|file:|local://|s3://") {
            throw "Downloaded TXT artifact body leaked storage metadata: $content"
        }
    } | Out-Null

    if ($docxObjectUri.StartsWith("local://sandbox-artifacts/")) {
        Test-Step "Verify local converted TXT object exists in backend storage volume" {
            $key = $docxObjectUri.Substring("local://sandbox-artifacts/".Length)
            if ($key.Contains("'") -or $Marker.Contains("'")) {
                throw "Cannot safely shell-quote DOCX to TXT key or marker"
            }
            $path = "$StorageRoot/sandbox-artifacts/$key"
            & docker exec $BackendContainer sh -lc "test -f '$path' && grep -F -q '$Marker' '$path'"
            if ($LASTEXITCODE -ne 0) {
                throw "Stored DOCX to TXT object not found or marker missing at $path"
            }
        } | Out-Null
    }

    $docxHtmlRunId = $runId
    $docxHtmlToolCallId = "sandbox-file-convert-docx-html-call-$suffix"
    $docxHtmlContent = New-DocxBase64 -Paragraphs @(
        "Sandbox DOCX HTML $Marker",
        "DOCX HTML conversion renders paragraph text"
    )

    $docxHtmlObservation = Test-Step "Invoke sandbox_file_convert DOCX to HTML through Tool Gateway" {
        $requestBody = @{
            runId = $docxHtmlRunId
            stepId = "sandbox-file-convert-docx-html-step-$suffix"
            toolCallId = $docxHtmlToolCallId
            agentId = "legacy-react-agent"
            tenantId = "default"
            userId = "$($login.data.userId)"
            agentIdentityId = "$($login.data.userId)"
            arguments = @{
                sourceFormat = "docx"
                targetFormat = "html"
                contentEncoding = "base64"
                content = $docxHtmlContent
            }
            resourceRefs = @{}
            idempotencyKey = "${docxHtmlRunId}:${docxHtmlToolCallId}"
            allowedToolIds = @("sandbox_file_convert")
        }
        $response = Invoke-SandboxFileConvertTool -Headers $headers -Body $requestBody -Name "Invoke sandbox_file_convert DOCX to HTML"
        if ($response.data.success -ne $true) {
            throw "sandbox_file_convert DOCX to HTML failed: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $content = "$($response.data.content)"
        $parsed = $content | ConvertFrom-Json
        if ("$($parsed.runtimeType)" -ne "FILE_CONVERSION") {
            throw "Expected FILE_CONVERSION runtime for DOCX to HTML: $content"
        }
        if ("$($parsed.executionStatus)" -ne "SUCCEEDED") {
            throw "Expected SUCCEEDED DOCX to HTML execution: $content"
        }
        if ("$($parsed.conversion.sourceFormat)" -ne "docx" -or "$($parsed.conversion.targetFormat)" -ne "html" -or "$($parsed.conversion.contentEncoding)" -ne "base64") {
            throw "Unexpected DOCX to HTML conversion metadata: $content"
        }
        $artifacts = @($parsed.artifacts)
        if ($artifacts.Count -ne 1) {
            throw "Expected one DOCX to HTML artifact: $content"
        }
        if ("$($artifacts[0].mediaType)" -ne "text/html") {
            throw "Expected DOCX to HTML artifact mediaType text/html: $content"
        }
        if ("$($artifacts[0].scanStatus)" -ne "CLEAN") {
            throw "Expected CLEAN DOCX to HTML artifact scan status: $content"
        }
        if ("$($artifacts[0].scanSummary)" -ne "metadata scan passed") {
            throw "Expected DOCX to HTML metadata scan summary: $content"
        }
        if ($artifacts[0].promptVisible -ne $true) {
            throw "Expected prompt-visible DOCX to HTML artifact: $content"
        }
        $parsed
    }
    if (-not $docxHtmlObservation) { exit 1 }

    $docxHtmlArtifactId = "$(@($docxHtmlObservation.artifacts)[0].artifactId)"

    $docxHtmlObjectUri = Test-Step "Verify persisted DOCX to HTML session and artifact" {
        Assert-PersistedFileConversionArtifact -ArtifactId $docxHtmlArtifactId -ExpectedMediaType "text/html" -Label "DOCX to HTML"
    }
    if (-not $docxHtmlObjectUri) { exit 1 }

    Test-Step "Download converted DOCX HTML through governed artifact endpoint" {
        $content = Invoke-Text -Method GET -Path "/api/sandbox/artifacts/$docxHtmlArtifactId/download" -Headers $headers
        if ($content -notlike "*<p>Sandbox DOCX HTML $Marker</p>*") {
            throw "Downloaded DOCX HTML did not include marker paragraph '$Marker': $content"
        }
        if ($content -notlike "*<p>DOCX HTML conversion renders paragraph text</p>*") {
            throw "Downloaded DOCX HTML did not include second paragraph: $content"
        }
        if ($content -match "objectUri|object_uri|storageRef|file:|local://|s3://") {
            throw "Downloaded DOCX HTML artifact body leaked storage metadata: $content"
        }
    } | Out-Null

    if ($docxHtmlObjectUri.StartsWith("local://sandbox-artifacts/")) {
        Test-Step "Verify local converted DOCX HTML object exists in backend storage volume" {
            $key = $docxHtmlObjectUri.Substring("local://sandbox-artifacts/".Length)
            if ($key.Contains("'") -or $Marker.Contains("'")) {
                throw "Cannot safely shell-quote DOCX to HTML key or marker"
            }
            $path = "$StorageRoot/sandbox-artifacts/$key"
            & docker exec $BackendContainer sh -lc "test -f '$path' && grep -F -q '$Marker' '$path'"
            if ($LASTEXITCODE -ne 0) {
                throw "Stored DOCX to HTML object not found or marker missing at $path"
            }
        } | Out-Null
    }

    $odtRunId = $runId
    $odtToolCallId = "sandbox-file-convert-odt-txt-call-$suffix"
    $odtContent = New-OdtBase64 -Paragraphs @(
        "Sandbox ODT $Marker",
        "ODT conversion extracts paragraph text"
    )

    $odtObservation = Test-Step "Invoke sandbox_file_convert ODT to TXT through Tool Gateway" {
        $requestBody = @{
            runId = $odtRunId
            stepId = "sandbox-file-convert-odt-txt-step-$suffix"
            toolCallId = $odtToolCallId
            agentId = "legacy-react-agent"
            tenantId = "default"
            userId = "$($login.data.userId)"
            agentIdentityId = "$($login.data.userId)"
            arguments = @{
                sourceFormat = "odt"
                targetFormat = "txt"
                contentEncoding = "base64"
                content = $odtContent
            }
            resourceRefs = @{}
            idempotencyKey = "${odtRunId}:${odtToolCallId}"
            allowedToolIds = @("sandbox_file_convert")
        }
        $response = Invoke-SandboxFileConvertTool -Headers $headers -Body $requestBody -Name "Invoke sandbox_file_convert ODT to TXT"
        if ($response.data.success -ne $true) {
            throw "sandbox_file_convert ODT to TXT failed: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $content = "$($response.data.content)"
        $parsed = $content | ConvertFrom-Json
        if ("$($parsed.runtimeType)" -ne "FILE_CONVERSION" -or "$($parsed.executionStatus)" -ne "SUCCEEDED") {
            throw "Expected succeeded FILE_CONVERSION ODT to TXT execution: $content"
        }
        if ("$($parsed.conversion.sourceFormat)" -ne "odt" -or "$($parsed.conversion.targetFormat)" -ne "txt" -or "$($parsed.conversion.contentEncoding)" -ne "base64") {
            throw "Unexpected ODT to TXT conversion metadata: $content"
        }
        $artifacts = @($parsed.artifacts)
        if ($artifacts.Count -ne 1 -or "$($artifacts[0].mediaType)" -ne "text/plain" -or "$($artifacts[0].scanStatus)" -ne "CLEAN") {
            throw "Expected one clean ODT TXT artifact: $content"
        }
        if ("$($artifacts[0].scanSummary)" -ne "metadata scan passed" -or $artifacts[0].promptVisible -ne $true) {
            throw "Expected prompt-visible ODT TXT metadata-scanned artifact: $content"
        }
        $parsed
    }
    if (-not $odtObservation) { exit 1 }

    $odtArtifactId = "$(@($odtObservation.artifacts)[0].artifactId)"
    $odtObjectUri = Test-Step "Verify persisted ODT to TXT session and artifact" {
        Assert-PersistedFileConversionArtifact -ArtifactId $odtArtifactId -ExpectedMediaType "text/plain" -Label "ODT to TXT"
    }
    if (-not $odtObjectUri) { exit 1 }

    Test-Step "Download converted ODT TXT through governed artifact endpoint" {
        $content = Invoke-Text -Method GET -Path "/api/sandbox/artifacts/$odtArtifactId/download" -Headers $headers
        if ($content -notlike "*Sandbox ODT $Marker*" -or $content -notlike "*ODT conversion extracts paragraph text*") {
            throw "Downloaded ODT TXT did not include expected paragraphs: $content"
        }
        if ($content -match "objectUri|object_uri|storageRef|file:|local://|s3://") {
            throw "Downloaded ODT TXT artifact body leaked storage metadata: $content"
        }
    } | Out-Null

    if ($odtObjectUri.StartsWith("local://sandbox-artifacts/")) {
        Test-Step "Verify local converted ODT TXT object exists in backend storage volume" {
            $key = $odtObjectUri.Substring("local://sandbox-artifacts/".Length)
            if ($key.Contains("'") -or $Marker.Contains("'")) {
                throw "Cannot safely shell-quote ODT to TXT key or marker"
            }
            $path = "$StorageRoot/sandbox-artifacts/$key"
            & docker exec $BackendContainer sh -lc "test -f '$path' && grep -F -q '$Marker' '$path'"
            if ($LASTEXITCODE -ne 0) {
                throw "Stored ODT to TXT object not found or marker missing at $path"
            }
        } | Out-Null
    }

    $odtHtmlRunId = $runId
    $odtHtmlToolCallId = "sandbox-file-convert-odt-html-call-$suffix"
    $odtHtmlContent = New-OdtBase64 -Paragraphs @(
        "Sandbox ODT HTML $Marker",
        "ODT HTML conversion renders paragraph text"
    )

    $odtHtmlObservation = Test-Step "Invoke sandbox_file_convert ODT to HTML through Tool Gateway" {
        $requestBody = @{
            runId = $odtHtmlRunId
            stepId = "sandbox-file-convert-odt-html-step-$suffix"
            toolCallId = $odtHtmlToolCallId
            agentId = "legacy-react-agent"
            tenantId = "default"
            userId = "$($login.data.userId)"
            agentIdentityId = "$($login.data.userId)"
            arguments = @{
                sourceFormat = "odt"
                targetFormat = "html"
                contentEncoding = "base64"
                content = $odtHtmlContent
            }
            resourceRefs = @{}
            idempotencyKey = "${odtHtmlRunId}:${odtHtmlToolCallId}"
            allowedToolIds = @("sandbox_file_convert")
        }
        $response = Invoke-SandboxFileConvertTool -Headers $headers -Body $requestBody -Name "Invoke sandbox_file_convert ODT to HTML"
        if ($response.data.success -ne $true) {
            throw "sandbox_file_convert ODT to HTML failed: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $content = "$($response.data.content)"
        $parsed = $content | ConvertFrom-Json
        if ("$($parsed.runtimeType)" -ne "FILE_CONVERSION" -or "$($parsed.executionStatus)" -ne "SUCCEEDED") {
            throw "Expected succeeded FILE_CONVERSION ODT to HTML execution: $content"
        }
        if ("$($parsed.conversion.sourceFormat)" -ne "odt" -or "$($parsed.conversion.targetFormat)" -ne "html" -or "$($parsed.conversion.contentEncoding)" -ne "base64") {
            throw "Unexpected ODT to HTML conversion metadata: $content"
        }
        $artifacts = @($parsed.artifacts)
        if ($artifacts.Count -ne 1 -or "$($artifacts[0].mediaType)" -ne "text/html" -or "$($artifacts[0].scanStatus)" -ne "CLEAN") {
            throw "Expected one clean ODT HTML artifact: $content"
        }
        if ("$($artifacts[0].scanSummary)" -ne "metadata scan passed" -or $artifacts[0].promptVisible -ne $true) {
            throw "Expected prompt-visible ODT HTML metadata-scanned artifact: $content"
        }
        $parsed
    }
    if (-not $odtHtmlObservation) { exit 1 }

    $odtHtmlArtifactId = "$(@($odtHtmlObservation.artifacts)[0].artifactId)"
    $odtHtmlObjectUri = Test-Step "Verify persisted ODT to HTML session and artifact" {
        Assert-PersistedFileConversionArtifact -ArtifactId $odtHtmlArtifactId -ExpectedMediaType "text/html" -Label "ODT to HTML"
    }
    if (-not $odtHtmlObjectUri) { exit 1 }

    Test-Step "Download converted ODT HTML through governed artifact endpoint" {
        $content = Invoke-Text -Method GET -Path "/api/sandbox/artifacts/$odtHtmlArtifactId/download" -Headers $headers
        if ($content -notlike "*<p>Sandbox ODT HTML $Marker</p>*" -or $content -notlike "*<p>ODT HTML conversion renders paragraph text</p>*") {
            throw "Downloaded ODT HTML did not include expected paragraphs: $content"
        }
        if ($content -match "objectUri|object_uri|storageRef|file:|local://|s3://") {
            throw "Downloaded ODT HTML artifact body leaked storage metadata: $content"
        }
    } | Out-Null

    if ($odtHtmlObjectUri.StartsWith("local://sandbox-artifacts/")) {
        Test-Step "Verify local converted ODT HTML object exists in backend storage volume" {
            $key = $odtHtmlObjectUri.Substring("local://sandbox-artifacts/".Length)
            if ($key.Contains("'") -or $Marker.Contains("'")) {
                throw "Cannot safely shell-quote ODT to HTML key or marker"
            }
            $path = "$StorageRoot/sandbox-artifacts/$key"
            & docker exec $BackendContainer sh -lc "test -f '$path' && grep -F -q '$Marker' '$path'"
            if ($LASTEXITCODE -ne 0) {
                throw "Stored ODT to HTML object not found or marker missing at $path"
            }
        } | Out-Null
    }

    $odpRunId = $runId
    $odpToolCallId = "sandbox-file-convert-odp-txt-call-$suffix"
    $odpContent = New-OdpBase64 -Paragraphs @(
        "Sandbox ODP $Marker",
        "ODP conversion extracts slide text"
    )

    $odpObservation = Test-Step "Invoke sandbox_file_convert ODP to TXT through Tool Gateway" {
        $requestBody = @{
            runId = $odpRunId
            stepId = "sandbox-file-convert-odp-txt-step-$suffix"
            toolCallId = $odpToolCallId
            agentId = "legacy-react-agent"
            tenantId = "default"
            userId = "$($login.data.userId)"
            agentIdentityId = "$($login.data.userId)"
            arguments = @{
                sourceFormat = "odp"
                targetFormat = "txt"
                contentEncoding = "base64"
                content = $odpContent
            }
            resourceRefs = @{}
            idempotencyKey = "${odpRunId}:${odpToolCallId}"
            allowedToolIds = @("sandbox_file_convert")
        }
        $response = Invoke-SandboxFileConvertTool -Headers $headers -Body $requestBody -Name "Invoke sandbox_file_convert ODP to TXT"
        if ($response.data.success -ne $true) {
            throw "sandbox_file_convert ODP to TXT failed: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $content = "$($response.data.content)"
        $parsed = $content | ConvertFrom-Json
        if ("$($parsed.runtimeType)" -ne "FILE_CONVERSION" -or "$($parsed.executionStatus)" -ne "SUCCEEDED") {
            throw "Expected succeeded FILE_CONVERSION ODP to TXT execution: $content"
        }
        if ("$($parsed.conversion.sourceFormat)" -ne "odp" -or "$($parsed.conversion.targetFormat)" -ne "txt" -or "$($parsed.conversion.contentEncoding)" -ne "base64") {
            throw "Unexpected ODP to TXT conversion metadata: $content"
        }
        $artifacts = @($parsed.artifacts)
        if ($artifacts.Count -ne 1 -or "$($artifacts[0].mediaType)" -ne "text/plain" -or "$($artifacts[0].scanStatus)" -ne "CLEAN") {
            throw "Expected one clean ODP TXT artifact: $content"
        }
        if ("$($artifacts[0].scanSummary)" -ne "metadata scan passed" -or $artifacts[0].promptVisible -ne $true) {
            throw "Expected prompt-visible ODP TXT metadata-scanned artifact: $content"
        }
        $parsed
    }
    if (-not $odpObservation) { exit 1 }

    $odpArtifactId = "$(@($odpObservation.artifacts)[0].artifactId)"
    $odpObjectUri = Test-Step "Verify persisted ODP to TXT session and artifact" {
        Assert-PersistedFileConversionArtifact -ArtifactId $odpArtifactId -ExpectedMediaType "text/plain" -Label "ODP to TXT"
    }
    if (-not $odpObjectUri) { exit 1 }

    Test-Step "Download converted ODP TXT through governed artifact endpoint" {
        $content = Invoke-Text -Method GET -Path "/api/sandbox/artifacts/$odpArtifactId/download" -Headers $headers
        if ($content -notlike "*Sandbox ODP $Marker*" -or $content -notlike "*ODP conversion extracts slide text*") {
            throw "Downloaded ODP TXT did not include expected slide text: $content"
        }
        if ($content -match "objectUri|object_uri|storageRef|file:|local://|s3://") {
            throw "Downloaded ODP TXT artifact body leaked storage metadata: $content"
        }
    } | Out-Null

    if ($odpObjectUri.StartsWith("local://sandbox-artifacts/")) {
        Test-Step "Verify local converted ODP TXT object exists in backend storage volume" {
            $key = $odpObjectUri.Substring("local://sandbox-artifacts/".Length)
            if ($key.Contains("'") -or $Marker.Contains("'")) {
                throw "Cannot safely shell-quote ODP to TXT key or marker"
            }
            $path = "$StorageRoot/sandbox-artifacts/$key"
            & docker exec $BackendContainer sh -lc "test -f '$path' && grep -F -q '$Marker' '$path'"
            if ($LASTEXITCODE -ne 0) {
                throw "Stored ODP to TXT object not found or marker missing at $path"
            }
        } | Out-Null
    }

    $odpHtmlRunId = $runId
    $odpHtmlToolCallId = "sandbox-file-convert-odp-html-call-$suffix"
    $odpHtmlContent = New-OdpBase64 -Paragraphs @(
        "Sandbox ODP HTML $Marker",
        "ODP HTML conversion renders slide text"
    )

    $odpHtmlObservation = Test-Step "Invoke sandbox_file_convert ODP to HTML through Tool Gateway" {
        $requestBody = @{
            runId = $odpHtmlRunId
            stepId = "sandbox-file-convert-odp-html-step-$suffix"
            toolCallId = $odpHtmlToolCallId
            agentId = "legacy-react-agent"
            tenantId = "default"
            userId = "$($login.data.userId)"
            agentIdentityId = "$($login.data.userId)"
            arguments = @{
                sourceFormat = "odp"
                targetFormat = "html"
                contentEncoding = "base64"
                content = $odpHtmlContent
            }
            resourceRefs = @{}
            idempotencyKey = "${odpHtmlRunId}:${odpHtmlToolCallId}"
            allowedToolIds = @("sandbox_file_convert")
        }
        $response = Invoke-SandboxFileConvertTool -Headers $headers -Body $requestBody -Name "Invoke sandbox_file_convert ODP to HTML"
        if ($response.data.success -ne $true) {
            throw "sandbox_file_convert ODP to HTML failed: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $content = "$($response.data.content)"
        $parsed = $content | ConvertFrom-Json
        if ("$($parsed.runtimeType)" -ne "FILE_CONVERSION" -or "$($parsed.executionStatus)" -ne "SUCCEEDED") {
            throw "Expected succeeded FILE_CONVERSION ODP to HTML execution: $content"
        }
        if ("$($parsed.conversion.sourceFormat)" -ne "odp" -or "$($parsed.conversion.targetFormat)" -ne "html" -or "$($parsed.conversion.contentEncoding)" -ne "base64") {
            throw "Unexpected ODP to HTML conversion metadata: $content"
        }
        $artifacts = @($parsed.artifacts)
        if ($artifacts.Count -ne 1 -or "$($artifacts[0].mediaType)" -ne "text/html" -or "$($artifacts[0].scanStatus)" -ne "CLEAN") {
            throw "Expected one clean ODP HTML artifact: $content"
        }
        if ("$($artifacts[0].scanSummary)" -ne "metadata scan passed" -or $artifacts[0].promptVisible -ne $true) {
            throw "Expected prompt-visible ODP HTML metadata-scanned artifact: $content"
        }
        $parsed
    }
    if (-not $odpHtmlObservation) { exit 1 }

    $odpHtmlArtifactId = "$(@($odpHtmlObservation.artifacts)[0].artifactId)"
    $odpHtmlObjectUri = Test-Step "Verify persisted ODP to HTML session and artifact" {
        Assert-PersistedFileConversionArtifact -ArtifactId $odpHtmlArtifactId -ExpectedMediaType "text/html" -Label "ODP to HTML"
    }
    if (-not $odpHtmlObjectUri) { exit 1 }

    Test-Step "Download converted ODP HTML through governed artifact endpoint" {
        $content = Invoke-Text -Method GET -Path "/api/sandbox/artifacts/$odpHtmlArtifactId/download" -Headers $headers
        if ($content -notlike "*<p>Sandbox ODP HTML $Marker</p>*" -or $content -notlike "*<p>ODP HTML conversion renders slide text</p>*") {
            throw "Downloaded ODP HTML did not include expected paragraphs: $content"
        }
        if ($content -match "objectUri|object_uri|storageRef|file:|local://|s3://") {
            throw "Downloaded ODP HTML artifact body leaked storage metadata: $content"
        }
    } | Out-Null

    if ($odpHtmlObjectUri.StartsWith("local://sandbox-artifacts/")) {
        Test-Step "Verify local converted ODP HTML object exists in backend storage volume" {
            $key = $odpHtmlObjectUri.Substring("local://sandbox-artifacts/".Length)
            if ($key.Contains("'") -or $Marker.Contains("'")) {
                throw "Cannot safely shell-quote ODP to HTML key or marker"
            }
            $path = "$StorageRoot/sandbox-artifacts/$key"
            & docker exec $BackendContainer sh -lc "test -f '$path' && grep -F -q '$Marker' '$path'"
            if ($LASTEXITCODE -ne 0) {
                throw "Stored ODP to HTML object not found or marker missing at $path"
            }
        } | Out-Null
    }

    $odsRunId = $runId
    $odsToolCallId = "sandbox-file-convert-ods-csv-call-$suffix"
    $odsContent = New-OdsBase64 -Marker $Marker -SecondValue "ODS conversion extracts first table"

    $odsObservation = Test-Step "Invoke sandbox_file_convert ODS to CSV through Tool Gateway" {
        $requestBody = @{
            runId = $odsRunId
            stepId = "sandbox-file-convert-ods-csv-step-$suffix"
            toolCallId = $odsToolCallId
            agentId = "legacy-react-agent"
            tenantId = "default"
            userId = "$($login.data.userId)"
            agentIdentityId = "$($login.data.userId)"
            arguments = @{
                sourceFormat = "ods"
                targetFormat = "csv"
                contentEncoding = "base64"
                content = $odsContent
            }
            resourceRefs = @{}
            idempotencyKey = "${odsRunId}:${odsToolCallId}"
            allowedToolIds = @("sandbox_file_convert")
        }
        $response = Invoke-SandboxFileConvertTool -Headers $headers -Body $requestBody -Name "Invoke sandbox_file_convert ODS to CSV"
        if ($response.data.success -ne $true) {
            throw "sandbox_file_convert ODS to CSV failed: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $content = "$($response.data.content)"
        $parsed = $content | ConvertFrom-Json
        if ("$($parsed.runtimeType)" -ne "FILE_CONVERSION" -or "$($parsed.executionStatus)" -ne "SUCCEEDED") {
            throw "Expected succeeded FILE_CONVERSION ODS to CSV execution: $content"
        }
        if ("$($parsed.conversion.sourceFormat)" -ne "ods" -or "$($parsed.conversion.targetFormat)" -ne "csv" -or "$($parsed.conversion.contentEncoding)" -ne "base64") {
            throw "Unexpected ODS to CSV conversion metadata: $content"
        }
        $artifacts = @($parsed.artifacts)
        if ($artifacts.Count -ne 1 -or "$($artifacts[0].mediaType)" -ne "text/csv" -or "$($artifacts[0].scanStatus)" -ne "CLEAN") {
            throw "Expected one clean ODS CSV artifact: $content"
        }
        if ("$($artifacts[0].scanSummary)" -ne "metadata scan passed" -or $artifacts[0].promptVisible -ne $true) {
            throw "Expected prompt-visible ODS CSV metadata-scanned artifact: $content"
        }
        $parsed
    }
    if (-not $odsObservation) { exit 1 }

    $odsArtifactId = "$(@($odsObservation.artifacts)[0].artifactId)"
    $odsObjectUri = Test-Step "Verify persisted ODS to CSV session and artifact" {
        Assert-PersistedFileConversionArtifact -ArtifactId $odsArtifactId -ExpectedMediaType "text/csv" -Label "ODS to CSV"
    }
    if (-not $odsObjectUri) { exit 1 }

    Test-Step "Download converted ODS CSV through governed artifact endpoint" {
        $content = Invoke-Text -Method GET -Path "/api/sandbox/artifacts/$odsArtifactId/download" -Headers $headers
        if ($content -notlike "*Sandbox ODS $Marker*" -or $content -notlike "*ODS conversion extracts first table*") {
            throw "Downloaded ODS CSV did not include expected cells: $content"
        }
        if ($content -match "objectUri|object_uri|storageRef|file:|local://|s3://") {
            throw "Downloaded ODS CSV artifact body leaked storage metadata: $content"
        }
    } | Out-Null

    if ($odsObjectUri.StartsWith("local://sandbox-artifacts/")) {
        Test-Step "Verify local converted ODS CSV object exists in backend storage volume" {
            $key = $odsObjectUri.Substring("local://sandbox-artifacts/".Length)
            if ($key.Contains("'") -or $Marker.Contains("'")) {
                throw "Cannot safely shell-quote ODS to CSV key or marker"
            }
            $path = "$StorageRoot/sandbox-artifacts/$key"
            & docker exec $BackendContainer sh -lc "test -f '$path' && grep -F -q '$Marker' '$path'"
            if ($LASTEXITCODE -ne 0) {
                throw "Stored ODS to CSV object not found or marker missing at $path"
            }
        } | Out-Null
    }

    $odsHtmlRunId = $runId
    $odsHtmlToolCallId = "sandbox-file-convert-ods-html-call-$suffix"
    $odsHtmlContent = New-OdsBase64 -Marker $Marker -SecondValue "ODS HTML conversion renders first table"

    $odsHtmlObservation = Test-Step "Invoke sandbox_file_convert ODS to HTML through Tool Gateway" {
        $requestBody = @{
            runId = $odsHtmlRunId
            stepId = "sandbox-file-convert-ods-html-step-$suffix"
            toolCallId = $odsHtmlToolCallId
            agentId = "legacy-react-agent"
            tenantId = "default"
            userId = "$($login.data.userId)"
            agentIdentityId = "$($login.data.userId)"
            arguments = @{
                sourceFormat = "ods"
                targetFormat = "html"
                contentEncoding = "base64"
                content = $odsHtmlContent
            }
            resourceRefs = @{}
            idempotencyKey = "${odsHtmlRunId}:${odsHtmlToolCallId}"
            allowedToolIds = @("sandbox_file_convert")
        }
        $response = Invoke-SandboxFileConvertTool -Headers $headers -Body $requestBody -Name "Invoke sandbox_file_convert ODS to HTML"
        if ($response.data.success -ne $true) {
            throw "sandbox_file_convert ODS to HTML failed: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $content = "$($response.data.content)"
        $parsed = $content | ConvertFrom-Json
        if ("$($parsed.runtimeType)" -ne "FILE_CONVERSION" -or "$($parsed.executionStatus)" -ne "SUCCEEDED") {
            throw "Expected succeeded FILE_CONVERSION ODS to HTML execution: $content"
        }
        if ("$($parsed.conversion.sourceFormat)" -ne "ods" -or "$($parsed.conversion.targetFormat)" -ne "html" -or "$($parsed.conversion.contentEncoding)" -ne "base64") {
            throw "Unexpected ODS to HTML conversion metadata: $content"
        }
        $artifacts = @($parsed.artifacts)
        if ($artifacts.Count -ne 1 -or "$($artifacts[0].mediaType)" -ne "text/html" -or "$($artifacts[0].scanStatus)" -ne "CLEAN") {
            throw "Expected one clean ODS HTML artifact: $content"
        }
        if ("$($artifacts[0].scanSummary)" -ne "metadata scan passed" -or $artifacts[0].promptVisible -ne $true) {
            throw "Expected prompt-visible ODS HTML metadata-scanned artifact: $content"
        }
        $parsed
    }
    if (-not $odsHtmlObservation) { exit 1 }

    $odsHtmlArtifactId = "$(@($odsHtmlObservation.artifacts)[0].artifactId)"
    $odsHtmlObjectUri = Test-Step "Verify persisted ODS to HTML session and artifact" {
        Assert-PersistedFileConversionArtifact -ArtifactId $odsHtmlArtifactId -ExpectedMediaType "text/html" -Label "ODS to HTML"
    }
    if (-not $odsHtmlObjectUri) { exit 1 }

    Test-Step "Download converted ODS HTML through governed artifact endpoint" {
        $content = Invoke-Text -Method GET -Path "/api/sandbox/artifacts/$odsHtmlArtifactId/download" -Headers $headers
        if ($content -notlike "*<table>*" -or $content -notlike "*<td>Sandbox ODS $Marker</td>*" -or $content -notlike "*<td>ODS HTML conversion renders first table</td>*") {
            throw "Downloaded ODS HTML did not include expected table cells: $content"
        }
        if ($content -match "objectUri|object_uri|storageRef|file:|local://|s3://") {
            throw "Downloaded ODS HTML artifact body leaked storage metadata: $content"
        }
    } | Out-Null

    if ($odsHtmlObjectUri.StartsWith("local://sandbox-artifacts/")) {
        Test-Step "Verify local converted ODS HTML object exists in backend storage volume" {
            $key = $odsHtmlObjectUri.Substring("local://sandbox-artifacts/".Length)
            if ($key.Contains("'") -or $Marker.Contains("'")) {
                throw "Cannot safely shell-quote ODS to HTML key or marker"
            }
            $path = "$StorageRoot/sandbox-artifacts/$key"
            & docker exec $BackendContainer sh -lc "test -f '$path' && grep -F -q '$Marker' '$path'"
            if ($LASTEXITCODE -ne 0) {
                throw "Stored ODS to HTML object not found or marker missing at $path"
            }
        } | Out-Null
    }

    $pdfRunId = $runId
    $pdfToolCallId = "sandbox-file-convert-pdf-txt-call-$suffix"
    $pdfContent = New-PdfBase64 -Lines @(
        "Sandbox PDF $Marker",
        "PDF conversion extracts literal text"
    )

    $pdfObservation = Test-Step "Invoke sandbox_file_convert PDF to TXT through Tool Gateway" {
        $requestBody = @{
            runId = $pdfRunId
            stepId = "sandbox-file-convert-pdf-txt-step-$suffix"
            toolCallId = $pdfToolCallId
            agentId = "legacy-react-agent"
            tenantId = "default"
            userId = "$($login.data.userId)"
            agentIdentityId = "$($login.data.userId)"
            arguments = @{
                sourceFormat = "pdf"
                targetFormat = "txt"
                contentEncoding = "base64"
                content = $pdfContent
            }
            resourceRefs = @{}
            idempotencyKey = "${pdfRunId}:${pdfToolCallId}"
            allowedToolIds = @("sandbox_file_convert")
        }
        $response = Invoke-SandboxFileConvertTool -Headers $headers -Body $requestBody -Name "Invoke sandbox_file_convert PDF to TXT"
        if ($response.data.success -ne $true) {
            throw "sandbox_file_convert PDF to TXT failed: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $content = "$($response.data.content)"
        $parsed = $content | ConvertFrom-Json
        if ("$($parsed.runtimeType)" -ne "FILE_CONVERSION") {
            throw "Expected FILE_CONVERSION runtime for PDF to TXT: $content"
        }
        if ("$($parsed.executionStatus)" -ne "SUCCEEDED") {
            throw "Expected SUCCEEDED PDF to TXT execution: $content"
        }
        if ("$($parsed.conversion.sourceFormat)" -ne "pdf" -or "$($parsed.conversion.targetFormat)" -ne "txt" -or "$($parsed.conversion.contentEncoding)" -ne "base64") {
            throw "Unexpected PDF to TXT conversion metadata: $content"
        }
        $artifacts = @($parsed.artifacts)
        if ($artifacts.Count -ne 1) {
            throw "Expected one PDF to TXT artifact: $content"
        }
        if ("$($artifacts[0].mediaType)" -ne "text/plain") {
            throw "Expected PDF to TXT artifact mediaType text/plain: $content"
        }
        if ("$($artifacts[0].scanStatus)" -ne "CLEAN") {
            throw "Expected CLEAN PDF to TXT artifact scan status: $content"
        }
        if ("$($artifacts[0].scanSummary)" -ne "metadata scan passed") {
            throw "Expected PDF to TXT metadata scan summary: $content"
        }
        if ($artifacts[0].promptVisible -ne $true) {
            throw "Expected prompt-visible PDF to TXT artifact: $content"
        }
        $parsed
    }
    if (-not $pdfObservation) { exit 1 }

    $pdfSessionId = "$($pdfObservation.sessionId)"
    $pdfArtifactId = "$(@($pdfObservation.artifacts)[0].artifactId)"

    $pdfObjectUri = Test-Step "Verify persisted PDF to TXT session and artifact" {
        Assert-PersistedFileConversionArtifact -ArtifactId $pdfArtifactId -ExpectedMediaType "text/plain" -Label "PDF to TXT"
    }
    if (-not $pdfObjectUri) { exit 1 }

    Test-Step "Download converted PDF TXT through governed artifact endpoint" {
        $content = Invoke-Text -Method GET -Path "/api/sandbox/artifacts/$pdfArtifactId/download" -Headers $headers
        if ($content -notlike "*Sandbox PDF $Marker*") {
            throw "Downloaded PDF TXT did not include marker '$Marker': $content"
        }
        if ($content -notlike "*PDF conversion extracts literal text*") {
            throw "Downloaded PDF TXT did not include second line: $content"
        }
        if ($content -match "objectUri|object_uri|storageRef|file:|local://|s3://") {
            throw "Downloaded PDF TXT artifact body leaked storage metadata: $content"
        }
    } | Out-Null

    if ($pdfObjectUri.StartsWith("local://sandbox-artifacts/")) {
        Test-Step "Verify local converted PDF TXT object exists in backend storage volume" {
            $key = $pdfObjectUri.Substring("local://sandbox-artifacts/".Length)
            if ($key.Contains("'") -or $Marker.Contains("'")) {
                throw "Cannot safely shell-quote PDF to TXT key or marker"
            }
            $path = "$StorageRoot/sandbox-artifacts/$key"
            & docker exec $BackendContainer sh -lc "test -f '$path' && grep -F -q '$Marker' '$path'"
            if ($LASTEXITCODE -ne 0) {
                throw "Stored PDF to TXT object not found or marker missing at $path"
            }
        } | Out-Null
    }

    $pdfHtmlRunId = $runId
    $pdfHtmlToolCallId = "sandbox-file-convert-pdf-html-call-$suffix"
    $pdfHtmlContent = New-PdfBase64 -Lines @(
        "Sandbox PDF HTML $Marker",
        "PDF HTML conversion renders literal text"
    )

    $pdfHtmlObservation = Test-Step "Invoke sandbox_file_convert PDF to HTML through Tool Gateway" {
        $requestBody = @{
            runId = $pdfHtmlRunId
            stepId = "sandbox-file-convert-pdf-html-step-$suffix"
            toolCallId = $pdfHtmlToolCallId
            agentId = "legacy-react-agent"
            tenantId = "default"
            userId = "$($login.data.userId)"
            agentIdentityId = "$($login.data.userId)"
            arguments = @{
                sourceFormat = "pdf"
                targetFormat = "html"
                contentEncoding = "base64"
                content = $pdfHtmlContent
            }
            resourceRefs = @{}
            idempotencyKey = "${pdfHtmlRunId}:${pdfHtmlToolCallId}"
            allowedToolIds = @("sandbox_file_convert")
        }
        $response = Invoke-SandboxFileConvertTool -Headers $headers -Body $requestBody -Name "Invoke sandbox_file_convert PDF to HTML"
        if ($response.data.success -ne $true) {
            throw "sandbox_file_convert PDF to HTML failed: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $content = "$($response.data.content)"
        $parsed = $content | ConvertFrom-Json
        if ("$($parsed.runtimeType)" -ne "FILE_CONVERSION") {
            throw "Expected FILE_CONVERSION runtime for PDF to HTML: $content"
        }
        if ("$($parsed.executionStatus)" -ne "SUCCEEDED") {
            throw "Expected SUCCEEDED PDF to HTML execution: $content"
        }
        if ("$($parsed.conversion.sourceFormat)" -ne "pdf" -or "$($parsed.conversion.targetFormat)" -ne "html" -or "$($parsed.conversion.contentEncoding)" -ne "base64") {
            throw "Unexpected PDF to HTML conversion metadata: $content"
        }
        $artifacts = @($parsed.artifacts)
        if ($artifacts.Count -ne 1) {
            throw "Expected one PDF to HTML artifact: $content"
        }
        if ("$($artifacts[0].mediaType)" -ne "text/html") {
            throw "Expected PDF to HTML artifact mediaType text/html: $content"
        }
        if ("$($artifacts[0].scanStatus)" -ne "CLEAN") {
            throw "Expected CLEAN PDF to HTML artifact scan status: $content"
        }
        if ("$($artifacts[0].scanSummary)" -ne "metadata scan passed") {
            throw "Expected PDF to HTML metadata scan summary: $content"
        }
        if ($artifacts[0].promptVisible -ne $true) {
            throw "Expected prompt-visible PDF to HTML artifact: $content"
        }
        $parsed
    }
    if (-not $pdfHtmlObservation) { exit 1 }

    $pdfHtmlArtifactId = "$(@($pdfHtmlObservation.artifacts)[0].artifactId)"

    $pdfHtmlObjectUri = Test-Step "Verify persisted PDF to HTML session and artifact" {
        Assert-PersistedFileConversionArtifact -ArtifactId $pdfHtmlArtifactId -ExpectedMediaType "text/html" -Label "PDF to HTML"
    }
    if (-not $pdfHtmlObjectUri) { exit 1 }

    Test-Step "Download converted PDF HTML through governed artifact endpoint" {
        $content = Invoke-Text -Method GET -Path "/api/sandbox/artifacts/$pdfHtmlArtifactId/download" -Headers $headers
        if ($content -notlike "*<p>Sandbox PDF HTML $Marker</p>*") {
            throw "Downloaded PDF HTML did not include marker paragraph '$Marker': $content"
        }
        if ($content -notlike "*<p>PDF HTML conversion renders literal text</p>*") {
            throw "Downloaded PDF HTML did not include second paragraph: $content"
        }
        if ($content -match "objectUri|object_uri|storageRef|file:|local://|s3://") {
            throw "Downloaded PDF HTML artifact body leaked storage metadata: $content"
        }
    } | Out-Null

    if ($pdfHtmlObjectUri.StartsWith("local://sandbox-artifacts/")) {
        Test-Step "Verify local converted PDF HTML object exists in backend storage volume" {
            $key = $pdfHtmlObjectUri.Substring("local://sandbox-artifacts/".Length)
            if ($key.Contains("'") -or $Marker.Contains("'")) {
                throw "Cannot safely shell-quote PDF to HTML key or marker"
            }
            $path = "$StorageRoot/sandbox-artifacts/$key"
            & docker exec $BackendContainer sh -lc "test -f '$path' && grep -F -q '$Marker' '$path'"
            if ($LASTEXITCODE -ne 0) {
                throw "Stored PDF to HTML object not found or marker missing at $path"
            }
        } | Out-Null
    }

    $xlsxRunId = $runId
    $xlsxToolCallId = "sandbox-file-convert-xlsx-csv-call-$suffix"
    $xlsxContent = New-XlsxBase64 -Marker $Marker -SecondValue "XLSX conversion extracts first worksheet"

    $xlsxObservation = Test-Step "Invoke sandbox_file_convert XLSX to CSV through Tool Gateway" {
        $requestBody = @{
            runId = $xlsxRunId
            stepId = "sandbox-file-convert-xlsx-csv-step-$suffix"
            toolCallId = $xlsxToolCallId
            agentId = "legacy-react-agent"
            tenantId = "default"
            userId = "$($login.data.userId)"
            agentIdentityId = "$($login.data.userId)"
            arguments = @{
                sourceFormat = "xlsx"
                targetFormat = "csv"
                contentEncoding = "base64"
                content = $xlsxContent
            }
            resourceRefs = @{}
            idempotencyKey = "${xlsxRunId}:${xlsxToolCallId}"
            allowedToolIds = @("sandbox_file_convert")
        }
        $response = Invoke-SandboxFileConvertTool -Headers $headers -Body $requestBody -Name "Invoke sandbox_file_convert XLSX to CSV"
        if ($response.data.success -ne $true) {
            throw "sandbox_file_convert XLSX to CSV failed: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $content = "$($response.data.content)"
        $parsed = $content | ConvertFrom-Json
        if ("$($parsed.runtimeType)" -ne "FILE_CONVERSION") {
            throw "Expected FILE_CONVERSION runtime for XLSX to CSV: $content"
        }
        if ("$($parsed.executionStatus)" -ne "SUCCEEDED") {
            throw "Expected SUCCEEDED XLSX to CSV execution: $content"
        }
        if ("$($parsed.conversion.sourceFormat)" -ne "xlsx" -or "$($parsed.conversion.targetFormat)" -ne "csv" -or "$($parsed.conversion.contentEncoding)" -ne "base64") {
            throw "Unexpected XLSX to CSV conversion metadata: $content"
        }
        $artifacts = @($parsed.artifacts)
        if ($artifacts.Count -ne 1) {
            throw "Expected one XLSX to CSV artifact: $content"
        }
        if ("$($artifacts[0].mediaType)" -ne "text/csv") {
            throw "Expected XLSX to CSV artifact mediaType text/csv: $content"
        }
        if ("$($artifacts[0].scanStatus)" -ne "CLEAN") {
            throw "Expected CLEAN XLSX to CSV artifact scan status: $content"
        }
        if ("$($artifacts[0].scanSummary)" -ne "metadata scan passed") {
            throw "Expected XLSX to CSV metadata scan summary: $content"
        }
        if ($artifacts[0].promptVisible -ne $true) {
            throw "Expected prompt-visible XLSX to CSV artifact: $content"
        }
        $parsed
    }
    if (-not $xlsxObservation) { exit 1 }

    $xlsxSessionId = "$($xlsxObservation.sessionId)"
    $xlsxArtifactId = "$(@($xlsxObservation.artifacts)[0].artifactId)"

    $xlsxObjectUri = Test-Step "Verify persisted XLSX to CSV session and artifact" {
        Assert-PersistedFileConversionArtifact -ArtifactId $xlsxArtifactId -ExpectedMediaType "text/csv" -Label "XLSX to CSV"
    }
    if (-not $xlsxObjectUri) { exit 1 }

    Test-Step "Download converted XLSX CSV through governed artifact endpoint" {
        $content = Invoke-Text -Method GET -Path "/api/sandbox/artifacts/$xlsxArtifactId/download" -Headers $headers
        if ($content -notlike "*Sandbox XLSX $Marker*") {
            throw "Downloaded XLSX CSV did not include marker '$Marker': $content"
        }
        if ($content -notlike "*XLSX conversion extracts first worksheet*") {
            throw "Downloaded XLSX CSV did not include worksheet value: $content"
        }
        if ($content -match "objectUri|object_uri|storageRef|file:|local://|s3://") {
            throw "Downloaded XLSX CSV artifact body leaked storage metadata: $content"
        }
    } | Out-Null

    if ($xlsxObjectUri.StartsWith("local://sandbox-artifacts/")) {
        Test-Step "Verify local converted XLSX CSV object exists in backend storage volume" {
            $key = $xlsxObjectUri.Substring("local://sandbox-artifacts/".Length)
            if ($key.Contains("'") -or $Marker.Contains("'")) {
                throw "Cannot safely shell-quote XLSX to CSV key or marker"
            }
            $path = "$StorageRoot/sandbox-artifacts/$key"
            & docker exec $BackendContainer sh -lc "test -f '$path' && grep -F -q '$Marker' '$path'"
            if ($LASTEXITCODE -ne 0) {
                throw "Stored XLSX to CSV object not found or marker missing at $path"
            }
        } | Out-Null
    }

    $xlsxHtmlRunId = $runId
    $xlsxHtmlToolCallId = "sandbox-file-convert-xlsx-html-call-$suffix"
    $xlsxHtmlContent = New-XlsxBase64 -Marker $Marker -SecondValue "XLSX HTML conversion renders first worksheet"

    $xlsxHtmlObservation = Test-Step "Invoke sandbox_file_convert XLSX to HTML through Tool Gateway" {
        $requestBody = @{
            runId = $xlsxHtmlRunId
            stepId = "sandbox-file-convert-xlsx-html-step-$suffix"
            toolCallId = $xlsxHtmlToolCallId
            agentId = "legacy-react-agent"
            tenantId = "default"
            userId = "$($login.data.userId)"
            agentIdentityId = "$($login.data.userId)"
            arguments = @{
                sourceFormat = "xlsx"
                targetFormat = "html"
                contentEncoding = "base64"
                content = $xlsxHtmlContent
            }
            resourceRefs = @{}
            idempotencyKey = "${xlsxHtmlRunId}:${xlsxHtmlToolCallId}"
            allowedToolIds = @("sandbox_file_convert")
        }
        $response = Invoke-SandboxFileConvertTool -Headers $headers -Body $requestBody -Name "Invoke sandbox_file_convert XLSX to HTML"
        if ($response.data.success -ne $true) {
            throw "sandbox_file_convert XLSX to HTML failed: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $content = "$($response.data.content)"
        $parsed = $content | ConvertFrom-Json
        if ("$($parsed.runtimeType)" -ne "FILE_CONVERSION") {
            throw "Expected FILE_CONVERSION runtime for XLSX to HTML: $content"
        }
        if ("$($parsed.executionStatus)" -ne "SUCCEEDED") {
            throw "Expected SUCCEEDED XLSX to HTML execution: $content"
        }
        if ("$($parsed.conversion.sourceFormat)" -ne "xlsx" -or "$($parsed.conversion.targetFormat)" -ne "html" -or "$($parsed.conversion.contentEncoding)" -ne "base64") {
            throw "Unexpected XLSX to HTML conversion metadata: $content"
        }
        $artifacts = @($parsed.artifacts)
        if ($artifacts.Count -ne 1) {
            throw "Expected one XLSX to HTML artifact: $content"
        }
        if ("$($artifacts[0].mediaType)" -ne "text/html") {
            throw "Expected XLSX to HTML artifact mediaType text/html: $content"
        }
        if ("$($artifacts[0].scanStatus)" -ne "CLEAN") {
            throw "Expected CLEAN XLSX to HTML artifact scan status: $content"
        }
        if ("$($artifacts[0].scanSummary)" -ne "metadata scan passed") {
            throw "Expected XLSX to HTML metadata scan summary: $content"
        }
        if ($artifacts[0].promptVisible -ne $true) {
            throw "Expected prompt-visible XLSX to HTML artifact: $content"
        }
        $parsed
    }
    if (-not $xlsxHtmlObservation) { exit 1 }

    $xlsxHtmlArtifactId = "$(@($xlsxHtmlObservation.artifacts)[0].artifactId)"

    $xlsxHtmlObjectUri = Test-Step "Verify persisted XLSX to HTML session and artifact" {
        Assert-PersistedFileConversionArtifact -ArtifactId $xlsxHtmlArtifactId -ExpectedMediaType "text/html" -Label "XLSX to HTML"
    }
    if (-not $xlsxHtmlObjectUri) { exit 1 }

    Test-Step "Download converted XLSX HTML through governed artifact endpoint" {
        $content = Invoke-Text -Method GET -Path "/api/sandbox/artifacts/$xlsxHtmlArtifactId/download" -Headers $headers
        if ($content -notlike "*<table>*") {
            throw "Downloaded XLSX HTML did not include table: $content"
        }
        if ($content -notlike "*<td>Sandbox XLSX $Marker</td>*") {
            throw "Downloaded XLSX HTML did not include marker cell '$Marker': $content"
        }
        if ($content -notlike "*<td>XLSX HTML conversion renders first worksheet</td>*") {
            throw "Downloaded XLSX HTML did not include worksheet value: $content"
        }
        if ($content -match "objectUri|object_uri|storageRef|file:|local://|s3://") {
            throw "Downloaded XLSX HTML artifact body leaked storage metadata: $content"
        }
    } | Out-Null

    if ($xlsxHtmlObjectUri.StartsWith("local://sandbox-artifacts/")) {
        Test-Step "Verify local converted XLSX HTML object exists in backend storage volume" {
            $key = $xlsxHtmlObjectUri.Substring("local://sandbox-artifacts/".Length)
            if ($key.Contains("'") -or $Marker.Contains("'")) {
                throw "Cannot safely shell-quote XLSX to HTML key or marker"
            }
            $path = "$StorageRoot/sandbox-artifacts/$key"
            & docker exec $BackendContainer sh -lc "test -f '$path' && grep -F -q '$Marker' '$path'"
            if ($LASTEXITCODE -ne 0) {
                throw "Stored XLSX to HTML object not found or marker missing at $path"
            }
        } | Out-Null
    }

    $pptxRunId = $runId
    $pptxToolCallId = "sandbox-file-convert-pptx-txt-call-$suffix"
    $pptxContent = New-PptxBase64 -Marker $Marker -SecondValue "PPTX conversion extracts slide text"

    $pptxObservation = Test-Step "Invoke sandbox_file_convert PPTX to TXT through Tool Gateway" {
        $requestBody = @{
            runId = $pptxRunId
            stepId = "sandbox-file-convert-pptx-txt-step-$suffix"
            toolCallId = $pptxToolCallId
            agentId = "legacy-react-agent"
            tenantId = "default"
            userId = "$($login.data.userId)"
            agentIdentityId = "$($login.data.userId)"
            arguments = @{
                sourceFormat = "pptx"
                targetFormat = "txt"
                contentEncoding = "base64"
                content = $pptxContent
            }
            resourceRefs = @{}
            idempotencyKey = "${pptxRunId}:${pptxToolCallId}"
            allowedToolIds = @("sandbox_file_convert")
        }
        $response = Invoke-SandboxFileConvertTool -Headers $headers -Body $requestBody -Name "Invoke sandbox_file_convert PPTX to TXT"
        if ($response.data.success -ne $true) {
            throw "sandbox_file_convert PPTX to TXT failed: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $content = "$($response.data.content)"
        $parsed = $content | ConvertFrom-Json
        if ("$($parsed.runtimeType)" -ne "FILE_CONVERSION") {
            throw "Expected FILE_CONVERSION runtime for PPTX to TXT: $content"
        }
        if ("$($parsed.executionStatus)" -ne "SUCCEEDED") {
            throw "Expected SUCCEEDED PPTX to TXT execution: $content"
        }
        if ("$($parsed.conversion.sourceFormat)" -ne "pptx" -or "$($parsed.conversion.targetFormat)" -ne "txt" -or "$($parsed.conversion.contentEncoding)" -ne "base64") {
            throw "Unexpected PPTX to TXT conversion metadata: $content"
        }
        $artifacts = @($parsed.artifacts)
        if ($artifacts.Count -ne 1) {
            throw "Expected one PPTX to TXT artifact: $content"
        }
        if ("$($artifacts[0].mediaType)" -ne "text/plain") {
            throw "Expected PPTX to TXT artifact mediaType text/plain: $content"
        }
        if ("$($artifacts[0].scanStatus)" -ne "CLEAN") {
            throw "Expected CLEAN PPTX to TXT artifact scan status: $content"
        }
        if ("$($artifacts[0].scanSummary)" -ne "metadata scan passed") {
            throw "Expected PPTX to TXT metadata scan summary: $content"
        }
        if ($artifacts[0].promptVisible -ne $true) {
            throw "Expected prompt-visible PPTX to TXT artifact: $content"
        }
        $parsed
    }
    if (-not $pptxObservation) { exit 1 }

    $pptxSessionId = "$($pptxObservation.sessionId)"
    $pptxArtifactId = "$(@($pptxObservation.artifacts)[0].artifactId)"

    $pptxObjectUri = Test-Step "Verify persisted PPTX to TXT session and artifact" {
        Assert-PersistedFileConversionArtifact -ArtifactId $pptxArtifactId -ExpectedMediaType "text/plain" -Label "PPTX to TXT"
    }
    if (-not $pptxObjectUri) { exit 1 }

    Test-Step "Download converted PPTX TXT through governed artifact endpoint" {
        $content = Invoke-Text -Method GET -Path "/api/sandbox/artifacts/$pptxArtifactId/download" -Headers $headers
        if ($content -notlike "*Sandbox PPTX $Marker*") {
            throw "Downloaded PPTX TXT did not include marker '$Marker': $content"
        }
        if ($content -notlike "*PPTX conversion extracts slide text*") {
            throw "Downloaded PPTX TXT did not include slide value: $content"
        }
        if ($content -match "objectUri|object_uri|storageRef|file:|local://|s3://") {
            throw "Downloaded PPTX TXT artifact body leaked storage metadata: $content"
        }
    } | Out-Null

    if ($pptxObjectUri.StartsWith("local://sandbox-artifacts/")) {
        Test-Step "Verify local converted PPTX TXT object exists in backend storage volume" {
            $key = $pptxObjectUri.Substring("local://sandbox-artifacts/".Length)
            if ($key.Contains("'") -or $Marker.Contains("'")) {
                throw "Cannot safely shell-quote PPTX to TXT key or marker"
            }
            $path = "$StorageRoot/sandbox-artifacts/$key"
            & docker exec $BackendContainer sh -lc "test -f '$path' && grep -F -q '$Marker' '$path'"
            if ($LASTEXITCODE -ne 0) {
                throw "Stored PPTX to TXT object not found or marker missing at $path"
            }
        } | Out-Null
    }

    $pptxHtmlRunId = $runId
    $pptxHtmlToolCallId = "sandbox-file-convert-pptx-html-call-$suffix"
    $pptxHtmlContent = New-PptxBase64 -Marker $Marker -SecondValue "PPTX HTML conversion renders slide text"

    $pptxHtmlObservation = Test-Step "Invoke sandbox_file_convert PPTX to HTML through Tool Gateway" {
        $requestBody = @{
            runId = $pptxHtmlRunId
            stepId = "sandbox-file-convert-pptx-html-step-$suffix"
            toolCallId = $pptxHtmlToolCallId
            agentId = "legacy-react-agent"
            tenantId = "default"
            userId = "$($login.data.userId)"
            agentIdentityId = "$($login.data.userId)"
            arguments = @{
                sourceFormat = "pptx"
                targetFormat = "html"
                contentEncoding = "base64"
                content = $pptxHtmlContent
            }
            resourceRefs = @{}
            idempotencyKey = "${pptxHtmlRunId}:${pptxHtmlToolCallId}"
            allowedToolIds = @("sandbox_file_convert")
        }
        $response = Invoke-SandboxFileConvertTool -Headers $headers -Body $requestBody -Name "Invoke sandbox_file_convert PPTX to HTML"
        if ($response.data.success -ne $true) {
            throw "sandbox_file_convert PPTX to HTML failed: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $content = "$($response.data.content)"
        $parsed = $content | ConvertFrom-Json
        if ("$($parsed.runtimeType)" -ne "FILE_CONVERSION") {
            throw "Expected FILE_CONVERSION runtime for PPTX to HTML: $content"
        }
        if ("$($parsed.executionStatus)" -ne "SUCCEEDED") {
            throw "Expected SUCCEEDED PPTX to HTML execution: $content"
        }
        if ("$($parsed.conversion.sourceFormat)" -ne "pptx" -or "$($parsed.conversion.targetFormat)" -ne "html" -or "$($parsed.conversion.contentEncoding)" -ne "base64") {
            throw "Unexpected PPTX to HTML conversion metadata: $content"
        }
        $artifacts = @($parsed.artifacts)
        if ($artifacts.Count -ne 1) {
            throw "Expected one PPTX to HTML artifact: $content"
        }
        if ("$($artifacts[0].mediaType)" -ne "text/html") {
            throw "Expected PPTX to HTML artifact mediaType text/html: $content"
        }
        if ("$($artifacts[0].scanStatus)" -ne "CLEAN") {
            throw "Expected CLEAN PPTX to HTML artifact scan status: $content"
        }
        if ("$($artifacts[0].scanSummary)" -ne "metadata scan passed") {
            throw "Expected PPTX to HTML metadata scan summary: $content"
        }
        if ($artifacts[0].promptVisible -ne $true) {
            throw "Expected prompt-visible PPTX to HTML artifact: $content"
        }
        $parsed
    }
    if (-not $pptxHtmlObservation) { exit 1 }

    $pptxHtmlArtifactId = "$(@($pptxHtmlObservation.artifacts)[0].artifactId)"

    $pptxHtmlObjectUri = Test-Step "Verify persisted PPTX to HTML session and artifact" {
        Assert-PersistedFileConversionArtifact -ArtifactId $pptxHtmlArtifactId -ExpectedMediaType "text/html" -Label "PPTX to HTML"
    }
    if (-not $pptxHtmlObjectUri) { exit 1 }

    Test-Step "Download converted PPTX HTML through governed artifact endpoint" {
        $content = Invoke-Text -Method GET -Path "/api/sandbox/artifacts/$pptxHtmlArtifactId/download" -Headers $headers
        if ($content -notlike "*<p>Sandbox PPTX $Marker PPTX HTML conversion renders slide text</p>*") {
            throw "Downloaded PPTX HTML did not include slide paragraph '$Marker': $content"
        }
        if ($content -notlike "*<!doctype html>*" -or $content -notlike "*</html>*") {
            throw "Downloaded PPTX HTML did not include HTML document wrapper: $content"
        }
        if ($content -match "objectUri|object_uri|storageRef|file:|local://|s3://") {
            throw "Downloaded PPTX HTML artifact body leaked storage metadata: $content"
        }
    } | Out-Null

    if ($pptxHtmlObjectUri.StartsWith("local://sandbox-artifacts/")) {
        Test-Step "Verify local converted PPTX HTML object exists in backend storage volume" {
            $key = $pptxHtmlObjectUri.Substring("local://sandbox-artifacts/".Length)
            if ($key.Contains("'") -or $Marker.Contains("'")) {
                throw "Cannot safely shell-quote PPTX to HTML key or marker"
            }
            $path = "$StorageRoot/sandbox-artifacts/$key"
            & docker exec $BackendContainer sh -lc "test -f '$path' && grep -F -q '$Marker' '$path'"
            if ($LASTEXITCODE -ne 0) {
                throw "Stored PPTX to HTML object not found or marker missing at $path"
            }
        } | Out-Null
    }

    Test-Step "Verify sandbox_file_convert Tool Gateway audit summaries" {
        $response = Invoke-Json -Method GET -Path "/api/tool-invocations?current=1&size=100&runId=$runId&toolId=sandbox_file_convert" -Headers $headers
        Assert-ApiOk $response "Read sandbox_file_convert tool audit"
        $records = Get-PageRecords $response.data
        $expectedSteps = @(
            @{
                StepId = "sandbox-file-convert-step-$suffix"
                Status = "SUCCEEDED"
                Required = @(
                    '"toolId":"sandbox_file_convert"',
                    '"runtimeType":"FILE_CONVERSION"',
                    '"sourceFormat":"csv"',
                    '"sourceFormatPresent":true',
                    '"targetFormat":"json"',
                    '"targetFormatPresent":true',
                    '"contentEncoding":"plain"',
                    '"contentEncodingPresent":true',
                    '"binaryInput":false',
                    '"networkRequested":false',
                    '"argumentKeys"',
                    '"argumentCount":3',
                    '"contentLength":'
                )
                Forbidden = @($Marker, "name,score,marker", "Ada", "Grace", $csvContent)
            },
            @{
                StepId = "sandbox-file-convert-markdown-html-step-$suffix"
                Status = "SUCCEEDED"
                Required = @(
                    '"toolId":"sandbox_file_convert"',
                    '"runtimeType":"FILE_CONVERSION"',
                    '"sourceFormat":"markdown"',
                    '"targetFormat":"html"',
                    '"contentEncoding":"plain"',
                    '"binaryInput":false',
                    '"networkRequested":false',
                    '"argumentCount":3',
                    '"contentLength":'
                )
                Forbidden = @($Marker, "Sandbox Document", "Hello **", $markdownContent)
            },
            @{
                StepId = "sandbox-file-convert-docx-txt-step-$suffix"
                Status = "SUCCEEDED"
                Required = @(
                    '"toolId":"sandbox_file_convert"',
                    '"runtimeType":"FILE_CONVERSION"',
                    '"sourceFormat":"docx"',
                    '"targetFormat":"txt"',
                    '"contentEncoding":"base64"',
                    '"contentEncodingPresent":true',
                    '"binaryInput":true',
                    '"networkRequested":false',
                    '"argumentCount":4',
                    '"contentLength":'
                )
                Forbidden = @($Marker, "Sandbox DOCX", "DOCX conversion preserves paragraph text", $docxContent)
            },
            @{
                StepId = "sandbox-file-convert-pdf-html-step-$suffix"
                Status = "SUCCEEDED"
                Required = @(
                    '"toolId":"sandbox_file_convert"',
                    '"runtimeType":"FILE_CONVERSION"',
                    '"sourceFormat":"pdf"',
                    '"targetFormat":"html"',
                    '"contentEncoding":"base64"',
                    '"binaryInput":true',
                    '"networkRequested":false',
                    '"argumentCount":4',
                    '"contentLength":'
                )
                Forbidden = @($Marker, "Sandbox PDF HTML", "PDF HTML conversion renders literal text", $pdfHtmlContent)
            },
            @{
                StepId = "sandbox-file-convert-xlsx-csv-step-$suffix"
                Status = "SUCCEEDED"
                Required = @(
                    '"toolId":"sandbox_file_convert"',
                    '"runtimeType":"FILE_CONVERSION"',
                    '"sourceFormat":"xlsx"',
                    '"targetFormat":"csv"',
                    '"contentEncoding":"base64"',
                    '"binaryInput":true',
                    '"networkRequested":false',
                    '"argumentCount":4',
                    '"contentLength":'
                )
                Forbidden = @($Marker, "XLSX conversion extracts first worksheet", $xlsxContent)
            },
            @{
                StepId = "sandbox-file-convert-pptx-html-step-$suffix"
                Status = "SUCCEEDED"
                Required = @(
                    '"toolId":"sandbox_file_convert"',
                    '"runtimeType":"FILE_CONVERSION"',
                    '"sourceFormat":"pptx"',
                    '"targetFormat":"html"',
                    '"contentEncoding":"base64"',
                    '"binaryInput":true',
                    '"networkRequested":false',
                    '"argumentCount":4',
                    '"contentLength":'
                )
                Forbidden = @($Marker, "Sandbox PPTX", "PPTX HTML conversion renders slide text", $pptxHtmlContent)
            }
        )
        foreach ($case in @($fileConvertFailureCases)) {
            $expectedSteps += @{
                StepId = "$($case.StepId)"
                Status = "FAILED"
                Required = @($case.Required)
                Forbidden = @($case.Forbidden)
            }
        }
        foreach ($expected in $expectedSteps) {
            $audit = @($records | Where-Object { "$($_.stepId)" -eq "$($expected.StepId)" -and "$($_.toolId)" -eq "sandbox_file_convert" }) | Select-Object -First 1
            if (-not $audit) {
                throw "sandbox_file_convert audit record not found for step $($expected.StepId): $($response.data | ConvertTo-Json -Depth 20 -Compress)"
            }
            $expectedStatus = if ($expected.Status) { "$($expected.Status)" } else { "SUCCEEDED" }
            if ("$($audit.status)" -ne $expectedStatus) {
                throw "sandbox_file_convert audit status mismatch for step $($expected.StepId): $($audit | ConvertTo-Json -Depth 20 -Compress)"
            }
            $summary = "$($audit.argumentsSummary)"
            foreach ($required in @($expected.Required)) {
                if ($summary -notlike "*$required*") {
                    throw "sandbox_file_convert audit summary for step $($expected.StepId) did not include $required`: $summary"
                }
            }
            foreach ($forbidden in @($expected.Forbidden + @("objectUri", "object_uri", "storageRef", "file:", "local://", "s3://"))) {
                if (-not [string]::IsNullOrWhiteSpace("$forbidden") -and $summary -like "*$forbidden*") {
                    throw "sandbox_file_convert audit summary leaked raw file conversion value '$forbidden': $summary"
                }
            }
        }
    } | Out-Null

    Write-Host "`nSummary: $passed / $total passed, $failed failed" -ForegroundColor Cyan
    Write-Host "Backend: $BaseUrl"
    Write-Host "Conversation: $($smokeRun.ConversationId)"
    Write-Host "Run: $runId"
    Write-Host "Tool: sandbox_file_convert"
    Write-Host "Artifact: $artifactId"
    Write-Host "Object URI: $objectUri"
} catch {
    Write-Host "`nSummary: $passed / $total passed, $failed failed" -ForegroundColor Cyan
    Write-Error $_.Exception.Message
    exit 1
}

if ($failed -gt 0) {
    exit 1
}
