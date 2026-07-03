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
    if ($status -ne $ExpectedStatus) {
        throw "Expected HTTP $ExpectedStatus but got $status for $Method $Path body=$content"
    }
    if ([string]::IsNullOrWhiteSpace($content)) {
        return $null
    }
    return $content | ConvertFrom-Json
}

function Invoke-Text {
    param(
        [string]$Method,
        [string]$Path,
        [hashtable]$Headers = @{},
        [int]$ExpectedStatus = 200
    )

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
    if ($status -ne $ExpectedStatus) {
        throw "Expected HTTP $ExpectedStatus but got $status for $Method $Path body=$content"
    }
    return $content
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
    $runId = "sandbox-file-convert-run-$suffix"
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

    $observation = Test-Step "Invoke sandbox_file_convert through Tool Gateway" {
        $response = Invoke-Json -Method POST -Path "/api/tools/sandbox_file_convert/invoke" -Headers $headers -Body @{
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
        Assert-ApiOk $response "Invoke sandbox_file_convert"
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
        $safeSessionId = $sessionId.Replace("'", "''")
        $sessionRow = Invoke-PostgresScalar "SELECT runtime_type, profile_id, status FROM sa_sandbox_session WHERE session_id = '$safeSessionId';"
        $sessionParts = $sessionRow -split "`t"
        if ($sessionParts.Count -ne 3) {
            throw "Unexpected sa_sandbox_session row: $sessionRow"
        }
        if ($sessionParts[0] -ne "FILE_CONVERSION") {
            throw "Expected runtime_type FILE_CONVERSION but got '$($sessionParts[0])'"
        }
        if ($sessionParts[1] -ne "file-conversion") {
            throw "Expected profile_id file-conversion but got '$($sessionParts[1])'"
        }
        if ($sessionParts[2] -ne "CANCELLED") {
            throw "Expected closed session status CANCELLED but got '$($sessionParts[2])'"
        }

        $safeArtifactId = $artifactId.Replace("'", "''")
        $artifactRow = Invoke-PostgresScalar "SELECT object_uri, media_type, scan_status, sensitivity, scan_summary FROM sa_sandbox_artifact WHERE artifact_id = '$safeArtifactId';"
        $artifactParts = $artifactRow -split "`t"
        if ($artifactParts.Count -ne 5) {
            throw "Unexpected sa_sandbox_artifact row: $artifactRow"
        }
        if ($artifactParts[0] -like "file:*") {
            throw "sandbox file conversion artifact still points at file URI: $($artifactParts[0])"
        }
        if ($ExpectedObjectUriPrefix -and $artifactParts[0] -notlike "$ExpectedObjectUriPrefix*") {
            throw "Expected object_uri prefix '$ExpectedObjectUriPrefix' but got '$($artifactParts[0])'"
        }
        if ($artifactParts[1] -ne "application/json") {
            throw "Expected media_type application/json but got '$($artifactParts[1])'"
        }
        if ($artifactParts[2] -ne "CLEAN") {
            throw "Expected scan_status CLEAN but got '$($artifactParts[2])'"
        }
        if ($artifactParts[3] -ne "INTERNAL") {
            throw "Expected sensitivity INTERNAL but got '$($artifactParts[3])'"
        }
        if ($artifactParts[4] -ne "metadata scan passed") {
            throw "Expected scan_summary metadata scan passed but got '$($artifactParts[4])'"
        }
        $artifactParts[0]
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

    $jsonToCsvRunId = "sandbox-file-convert-json-csv-run-$suffix"
    $jsonToCsvToolCallId = "sandbox-file-convert-json-csv-call-$suffix"
    $jsonRows = @(
        [ordered]@{ name = "Lin"; score = "7"; marker = $Marker },
        [ordered]@{ name = "Katherine"; score = "11"; marker = $Marker }
    )
    $jsonContent = $jsonRows | ConvertTo-Json -Depth 10 -Compress

    $jsonToCsvObservation = Test-Step "Invoke sandbox_file_convert JSON to CSV through Tool Gateway" {
        $response = Invoke-Json -Method POST -Path "/api/tools/sandbox_file_convert/invoke" -Headers $headers -Body @{
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
        Assert-ApiOk $response "Invoke sandbox_file_convert JSON to CSV"
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
        $safeSessionId = $jsonToCsvSessionId.Replace("'", "''")
        $sessionRow = Invoke-PostgresScalar "SELECT runtime_type, profile_id, status FROM sa_sandbox_session WHERE session_id = '$safeSessionId';"
        $sessionParts = $sessionRow -split "`t"
        if ($sessionParts.Count -ne 3) {
            throw "Unexpected JSON to CSV sa_sandbox_session row: $sessionRow"
        }
        if ($sessionParts[0] -ne "FILE_CONVERSION") {
            throw "Expected JSON to CSV runtime_type FILE_CONVERSION but got '$($sessionParts[0])'"
        }
        if ($sessionParts[1] -ne "file-conversion") {
            throw "Expected JSON to CSV profile_id file-conversion but got '$($sessionParts[1])'"
        }
        if ($sessionParts[2] -ne "CANCELLED") {
            throw "Expected JSON to CSV closed session status CANCELLED but got '$($sessionParts[2])'"
        }

        $safeArtifactId = $jsonToCsvArtifactId.Replace("'", "''")
        $artifactRow = Invoke-PostgresScalar "SELECT object_uri, media_type, scan_status, sensitivity, scan_summary FROM sa_sandbox_artifact WHERE artifact_id = '$safeArtifactId';"
        $artifactParts = $artifactRow -split "`t"
        if ($artifactParts.Count -ne 5) {
            throw "Unexpected JSON to CSV sa_sandbox_artifact row: $artifactRow"
        }
        if ($artifactParts[0] -like "file:*") {
            throw "JSON to CSV artifact still points at file URI: $($artifactParts[0])"
        }
        if ($ExpectedObjectUriPrefix -and $artifactParts[0] -notlike "$ExpectedObjectUriPrefix*") {
            throw "Expected JSON to CSV object_uri prefix '$ExpectedObjectUriPrefix' but got '$($artifactParts[0])'"
        }
        if ($artifactParts[1] -ne "text/csv") {
            throw "Expected JSON to CSV media_type text/csv but got '$($artifactParts[1])'"
        }
        if ($artifactParts[2] -ne "CLEAN") {
            throw "Expected JSON to CSV scan_status CLEAN but got '$($artifactParts[2])'"
        }
        if ($artifactParts[3] -ne "INTERNAL") {
            throw "Expected JSON to CSV sensitivity INTERNAL but got '$($artifactParts[3])'"
        }
        if ($artifactParts[4] -ne "metadata scan passed") {
            throw "Expected JSON to CSV scan_summary metadata scan passed but got '$($artifactParts[4])'"
        }
        $artifactParts[0]
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

    $markdownRunId = "sandbox-file-convert-markdown-html-run-$suffix"
    $markdownToolCallId = "sandbox-file-convert-markdown-html-call-$suffix"
    $markdownContent = "# Sandbox Document`n`nHello **$Marker** from markdown.`n- first item`n"

    $markdownObservation = Test-Step "Invoke sandbox_file_convert Markdown to HTML through Tool Gateway" {
        $response = Invoke-Json -Method POST -Path "/api/tools/sandbox_file_convert/invoke" -Headers $headers -Body @{
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
        Assert-ApiOk $response "Invoke sandbox_file_convert Markdown to HTML"
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
        $safeSessionId = $markdownSessionId.Replace("'", "''")
        $sessionRow = Invoke-PostgresScalar "SELECT runtime_type, profile_id, status FROM sa_sandbox_session WHERE session_id = '$safeSessionId';"
        $sessionParts = $sessionRow -split "`t"
        if ($sessionParts.Count -ne 3) {
            throw "Unexpected Markdown to HTML sa_sandbox_session row: $sessionRow"
        }
        if ($sessionParts[0] -ne "FILE_CONVERSION") {
            throw "Expected Markdown to HTML runtime_type FILE_CONVERSION but got '$($sessionParts[0])'"
        }
        if ($sessionParts[1] -ne "file-conversion") {
            throw "Expected Markdown to HTML profile_id file-conversion but got '$($sessionParts[1])'"
        }
        if ($sessionParts[2] -ne "CANCELLED") {
            throw "Expected Markdown to HTML closed session status CANCELLED but got '$($sessionParts[2])'"
        }

        $safeArtifactId = $markdownArtifactId.Replace("'", "''")
        $artifactRow = Invoke-PostgresScalar "SELECT object_uri, media_type, scan_status, sensitivity, scan_summary FROM sa_sandbox_artifact WHERE artifact_id = '$safeArtifactId';"
        $artifactParts = $artifactRow -split "`t"
        if ($artifactParts.Count -ne 5) {
            throw "Unexpected Markdown to HTML sa_sandbox_artifact row: $artifactRow"
        }
        if ($artifactParts[0] -like "file:*") {
            throw "Markdown to HTML artifact still points at file URI: $($artifactParts[0])"
        }
        if ($ExpectedObjectUriPrefix -and $artifactParts[0] -notlike "$ExpectedObjectUriPrefix*") {
            throw "Expected Markdown to HTML object_uri prefix '$ExpectedObjectUriPrefix' but got '$($artifactParts[0])'"
        }
        if ($artifactParts[1] -ne "text/html") {
            throw "Expected Markdown to HTML media_type text/html but got '$($artifactParts[1])'"
        }
        if ($artifactParts[2] -ne "CLEAN") {
            throw "Expected Markdown to HTML scan_status CLEAN but got '$($artifactParts[2])'"
        }
        if ($artifactParts[3] -ne "INTERNAL") {
            throw "Expected Markdown to HTML sensitivity INTERNAL but got '$($artifactParts[3])'"
        }
        if ($artifactParts[4] -ne "metadata scan passed") {
            throw "Expected Markdown to HTML scan_summary metadata scan passed but got '$($artifactParts[4])'"
        }
        $artifactParts[0]
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

    $docxRunId = "sandbox-file-convert-docx-txt-run-$suffix"
    $docxToolCallId = "sandbox-file-convert-docx-txt-call-$suffix"
    $docxContent = New-DocxBase64 -Paragraphs @(
        "Sandbox DOCX $Marker",
        "DOCX conversion preserves paragraph text"
    )

    $docxObservation = Test-Step "Invoke sandbox_file_convert DOCX to TXT through Tool Gateway" {
        $response = Invoke-Json -Method POST -Path "/api/tools/sandbox_file_convert/invoke" -Headers $headers -Body @{
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
        Assert-ApiOk $response "Invoke sandbox_file_convert DOCX to TXT"
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
        $safeSessionId = $docxSessionId.Replace("'", "''")
        $sessionRow = Invoke-PostgresScalar "SELECT runtime_type, profile_id, status FROM sa_sandbox_session WHERE session_id = '$safeSessionId';"
        $sessionParts = $sessionRow -split "`t"
        if ($sessionParts.Count -ne 3) {
            throw "Unexpected DOCX to TXT sa_sandbox_session row: $sessionRow"
        }
        if ($sessionParts[0] -ne "FILE_CONVERSION") {
            throw "Expected DOCX to TXT runtime_type FILE_CONVERSION but got '$($sessionParts[0])'"
        }
        if ($sessionParts[1] -ne "file-conversion") {
            throw "Expected DOCX to TXT profile_id file-conversion but got '$($sessionParts[1])'"
        }
        if ($sessionParts[2] -ne "CANCELLED") {
            throw "Expected DOCX to TXT closed session status CANCELLED but got '$($sessionParts[2])'"
        }

        $safeArtifactId = $docxArtifactId.Replace("'", "''")
        $artifactRow = Invoke-PostgresScalar "SELECT object_uri, media_type, scan_status, sensitivity, scan_summary FROM sa_sandbox_artifact WHERE artifact_id = '$safeArtifactId';"
        $artifactParts = $artifactRow -split "`t"
        if ($artifactParts.Count -ne 5) {
            throw "Unexpected DOCX to TXT sa_sandbox_artifact row: $artifactRow"
        }
        if ($artifactParts[0] -like "file:*") {
            throw "DOCX to TXT artifact still points at file URI: $($artifactParts[0])"
        }
        if ($ExpectedObjectUriPrefix -and $artifactParts[0] -notlike "$ExpectedObjectUriPrefix*") {
            throw "Expected DOCX to TXT object_uri prefix '$ExpectedObjectUriPrefix' but got '$($artifactParts[0])'"
        }
        if ($artifactParts[1] -ne "text/plain") {
            throw "Expected DOCX to TXT media_type text/plain but got '$($artifactParts[1])'"
        }
        if ($artifactParts[2] -ne "CLEAN") {
            throw "Expected DOCX to TXT scan_status CLEAN but got '$($artifactParts[2])'"
        }
        if ($artifactParts[3] -ne "INTERNAL") {
            throw "Expected DOCX to TXT sensitivity INTERNAL but got '$($artifactParts[3])'"
        }
        if ($artifactParts[4] -ne "metadata scan passed") {
            throw "Expected DOCX to TXT scan_summary metadata scan passed but got '$($artifactParts[4])'"
        }
        $artifactParts[0]
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

    $pdfRunId = "sandbox-file-convert-pdf-txt-run-$suffix"
    $pdfToolCallId = "sandbox-file-convert-pdf-txt-call-$suffix"
    $pdfContent = New-PdfBase64 -Lines @(
        "Sandbox PDF $Marker",
        "PDF conversion extracts literal text"
    )

    $pdfObservation = Test-Step "Invoke sandbox_file_convert PDF to TXT through Tool Gateway" {
        $response = Invoke-Json -Method POST -Path "/api/tools/sandbox_file_convert/invoke" -Headers $headers -Body @{
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
        Assert-ApiOk $response "Invoke sandbox_file_convert PDF to TXT"
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
        $safeSessionId = $pdfSessionId.Replace("'", "''")
        $sessionRow = Invoke-PostgresScalar "SELECT runtime_type, profile_id, status FROM sa_sandbox_session WHERE session_id = '$safeSessionId';"
        $sessionParts = $sessionRow -split "`t"
        if ($sessionParts.Count -ne 3) {
            throw "Unexpected PDF to TXT sa_sandbox_session row: $sessionRow"
        }
        if ($sessionParts[0] -ne "FILE_CONVERSION") {
            throw "Expected PDF to TXT runtime_type FILE_CONVERSION but got '$($sessionParts[0])'"
        }
        if ($sessionParts[1] -ne "file-conversion") {
            throw "Expected PDF to TXT profile_id file-conversion but got '$($sessionParts[1])'"
        }
        if ($sessionParts[2] -ne "CANCELLED") {
            throw "Expected PDF to TXT closed session status CANCELLED but got '$($sessionParts[2])'"
        }

        $safeArtifactId = $pdfArtifactId.Replace("'", "''")
        $artifactRow = Invoke-PostgresScalar "SELECT object_uri, media_type, scan_status, sensitivity, scan_summary FROM sa_sandbox_artifact WHERE artifact_id = '$safeArtifactId';"
        $artifactParts = $artifactRow -split "`t"
        if ($artifactParts.Count -ne 5) {
            throw "Unexpected PDF to TXT sa_sandbox_artifact row: $artifactRow"
        }
        if ($artifactParts[0] -like "file:*") {
            throw "PDF to TXT artifact still points at file URI: $($artifactParts[0])"
        }
        if ($ExpectedObjectUriPrefix -and $artifactParts[0] -notlike "$ExpectedObjectUriPrefix*") {
            throw "Expected PDF to TXT object_uri prefix '$ExpectedObjectUriPrefix' but got '$($artifactParts[0])'"
        }
        if ($artifactParts[1] -ne "text/plain") {
            throw "Expected PDF to TXT media_type text/plain but got '$($artifactParts[1])'"
        }
        if ($artifactParts[2] -ne "CLEAN") {
            throw "Expected PDF to TXT scan_status CLEAN but got '$($artifactParts[2])'"
        }
        if ($artifactParts[3] -ne "INTERNAL") {
            throw "Expected PDF to TXT sensitivity INTERNAL but got '$($artifactParts[3])'"
        }
        if ($artifactParts[4] -ne "metadata scan passed") {
            throw "Expected PDF to TXT scan_summary metadata scan passed but got '$($artifactParts[4])'"
        }
        $artifactParts[0]
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

    Write-Host "`nSummary: $passed / $total passed, $failed failed" -ForegroundColor Cyan
    Write-Host "Backend: $BaseUrl"
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
