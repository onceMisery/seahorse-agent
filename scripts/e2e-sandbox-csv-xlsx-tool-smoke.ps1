param(
    [string]$BaseUrl = "http://127.0.0.1:9090",
    [string]$Username = "admin",
    [string]$Password = "admin123",
    [string]$Marker = "seahorse-sandbox-csv-xlsx-smoke",
    [long]$KernelRunProfileId = -9101,
    [string]$PostgresContainer = "seahorse-postgres"
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Invoke-Api {
    param([string]$Method, [string]$Path, [hashtable]$Headers = @{}, [object]$Body = $null)
    $bodyText = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 12 -Compress }
    for ($attempt = 1; $attempt -le 4; $attempt++) {
        $args = @("-sS", "-w", "`n%{http_code}", "-X", $Method, "$BaseUrl$Path")
        foreach ($key in $Headers.Keys) { $args += @("-H", "${key}: $($Headers[$key])") }
        $temp = $null
        try {
            if ($null -ne $bodyText) {
                $temp = New-TemporaryFile
                Set-Content -LiteralPath $temp.FullName -Value $bodyText -Encoding UTF8 -NoNewline
                $args += @("-H", "Content-Type: application/json", "--data-binary", "@$($temp.FullName)")
            }
            $raw = & curl.exe @args
            if ($LASTEXITCODE -ne 0) { throw "curl exited with $LASTEXITCODE for $Method $Path" }
        } finally {
            if ($null -ne $temp) { Remove-Item -LiteralPath $temp.FullName -ErrorAction SilentlyContinue }
        }
        $lines = @($raw)
        $status = [int]$lines[-1]
        if ($status -eq 429 -and $attempt -lt 4) { Start-Sleep -Seconds 65; continue }
        if ($status -ne 200) { throw "Expected HTTP 200 for $Method $Path but got $status" }
        return (($lines[0..($lines.Count - 2)] -join "`n") | ConvertFrom-Json)
    }
}

function Require-Ok { param([object]$Response, [string]$Name)
    if ("$($Response.code)" -ne "0") { throw "$Name failed: $($Response | ConvertTo-Json -Depth 12 -Compress)" }
}

$login = Invoke-Api -Method POST -Path "/auth/login" -Body @{ username = $Username; password = $Password }
Require-Ok $login "Login"
$headers = @{ Authorization = "Bearer $($login.data.token)" }
$conversation = Invoke-Api -Method POST -Path "/api/conversations" -Headers $headers
Require-Ok $conversation "Create conversation"
$question = [Uri]::EscapeDataString("CSV XLSX smoke $Marker")
$chat = Invoke-WebRequest -Uri "$BaseUrl/rag/v3/chat?conversationId=$($conversation.data)&question=$question&runProfileId=$KernelRunProfileId&chatMode=agent" -Headers $headers -UseBasicParsing -TimeoutSec 180
$runMatch = [regex]::Match($chat.Content, '"runId"\s*:\s*"([^"]+)"')
if (-not $runMatch.Success) { throw "Chat did not return a run id" }
$runId = $runMatch.Groups[1].Value
$suffix = ([guid]::NewGuid().ToString("N")).Substring(0, 12)
$csv = [string]::Join([Environment]::NewLine, @("name,score,marker", "Ada,42,$Marker", "Grace,99,$Marker"))
$request = @{
    runId = $runId; stepId = "sandbox-csv-xlsx-$suffix"; toolCallId = "sandbox-csv-xlsx-$suffix"
    agentId = "legacy-react-agent"; tenantId = "default"; userId = "$($login.data.userId)"; agentIdentityId = "$($login.data.userId)"
    arguments = @{ sourceFormat = "csv"; targetFormat = "xlsx"; contentEncoding = "plain"; content = $csv }
    resourceRefs = @{}; idempotencyKey = ("{0}:{1}" -f $runId, $suffix); allowedToolIds = @("sandbox_file_convert")
}
$first = Invoke-Api -Method POST -Path "/api/tools/sandbox_file_convert/invoke" -Headers $headers -Body $request
Require-Ok $first "Request CSV to XLSX approval"
if ("$($first.data.error)" -ne "TOOL_APPROVAL_REQUIRED") { throw "Expected approval: $($first | ConvertTo-Json -Depth 12 -Compress)" }
$approved = Invoke-Api -Method POST -Path "/api/approvals/$($first.data.approvalId)/approve" -Headers $headers -Body @{ decisionComment = "CSV XLSX E2E" }
Require-Ok $approved "Approve CSV to XLSX"
$result = Invoke-Api -Method POST -Path "/api/tools/sandbox_file_convert/invoke" -Headers $headers -Body $request
Require-Ok $result "Invoke CSV to XLSX"
if ($result.data.success -ne $true) { throw "CSV to XLSX failed: $($result.data | ConvertTo-Json -Depth 12 -Compress)" }
$observation = $result.data.content | ConvertFrom-Json
if ("$($observation.executionStatus)" -ne "SUCCEEDED") { throw "Execution did not succeed: $($observation | ConvertTo-Json -Depth 12 -Compress)" }
$artifactId = (& docker exec $PostgresContainer psql -U seahorse -d seahorse -At -c "SELECT artifact_id FROM sa_sandbox_artifact WHERE execution_id = '$($observation.executionId)' AND media_type = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' AND scan_status = 'CLEAN';").Trim()
if ([string]::IsNullOrWhiteSpace($artifactId)) { throw "No CLEAN XLSX artifact persisted for execution $($observation.executionId)" }
$tempXlsx = Join-Path $env:TEMP "seahorse-csv-xlsx-$suffix.xlsx"
try {
    Invoke-WebRequest -Uri "$BaseUrl/api/sandbox/artifacts/$artifactId/download" -Headers $headers -UseBasicParsing -OutFile $tempXlsx
    $zip = [IO.Compression.ZipFile]::OpenRead($tempXlsx)
    try {
        $entry = $zip.GetEntry("xl/sharedStrings.xml")
        if ($null -eq $entry) { throw "Downloaded XLSX did not include shared strings" }
        $reader = [IO.StreamReader]::new($entry.Open())
        try { $xml = $reader.ReadToEnd() } finally { $reader.Dispose() }
    } finally { $zip.Dispose() }
    foreach ($expected in @("Ada", "Grace", $Marker)) {
        if ($xml -notlike "*$expected*") { throw "Downloaded XLSX did not retain '$expected'" }
    }
} finally { Remove-Item -LiteralPath $tempXlsx -ErrorAction SilentlyContinue }

Write-Host "PASS CSV to XLSX Tool Gateway smoke: artifact=$artifactId run=$runId"
