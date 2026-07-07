param(
    [string]$BaseUrl = "http://127.0.0.1:9090",
    [string]$Username = "admin",
    [string]$Password = "admin123",
    [string]$Marker = "seahorse-sandbox-browser-smoke",
    [string]$PostgresContainer = "seahorse-postgres",
    [string]$PostgresUser = "seahorse",
    [string]$PostgresDatabase = "seahorse",
    [string]$BackendContainer = "seahorse-backend",
    [string]$BrowserImage = "seahorse-sandbox-browser:playwright-1.48.0",
    [string]$BrowserImageDockerfile = "resources/docker/Dockerfile.sandbox-browser-runtime",
    [string]$BrowserImageBuildContext = ".",
    [string]$ExternalHost = "host.docker.internal",
    [int]$ExternalPort = 18080,
    [string]$AssetHost = "assets.docker.internal",
    [int]$AssetPort = 18081,
    [string]$StorageRoot = "/app/seahorse-agent-storage",
    [string]$ExpectedObjectUriPrefix = "local://sandbox-artifacts/",
    [long]$KernelRunProfileId = -9101,
    [switch]$SkipBrowserImageBuild,
    [switch]$SkipHealth
)

$ErrorActionPreference = "Stop"
$passed = 0
$failed = 0
$total = 0
$externalHttpContainerName = $null
$externalHttpRoot = $null
$assetHttpContainerName = $null
$assetHttpRoot = $null
$headers = $null
$browserProfileNetworkEnabled = $false
$baselineNonTerminalSandboxSessions = $null

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

function Invoke-SandboxBrowserTool {
    param(
        [hashtable]$Headers,
        [hashtable]$Body,
        [string]$Name
    )

    $response = Invoke-Json -Method POST -Path "/api/tools/sandbox_browser/invoke" -Headers $Headers -Body $Body
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
        decisionComment = "Allow sandbox browser smoke test"
    }
    Assert-ApiOk $approved "Approve $Name"
    if ("$($approved.data.status)" -ne "APPROVED") {
        throw "$Name approval was not approved: $($approved.data | ConvertTo-Json -Depth 20 -Compress)"
    }

    $retry = Invoke-Json -Method POST -Path "/api/tools/sandbox_browser/invoke" -Headers $Headers -Body $Body
    Assert-ApiOk $retry "Retry $Name after approval"
    return $retry
}

function Invoke-ExpectedSandboxBrowserUrlFailure {
    param(
        [hashtable]$Headers,
        [hashtable]$Body,
        [string]$Name,
        [string]$ExpectedMessage,
        [string[]]$ForbiddenValues = @()
    )

    $response = Invoke-SandboxBrowserTool -Headers $Headers -Body $Body -Name $Name
    Assert-ApiOk $response $Name
    if ($response.data.success -eq $true) {
        throw "$Name unexpectedly succeeded: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
    }
    $payload = "$($response.data | ConvertTo-Json -Depth 20 -Compress)"
    if ($payload -notlike "*$ExpectedMessage*") {
        throw "$Name did not include expected failure '$ExpectedMessage': $payload"
    }
    foreach ($forbidden in @($ForbiddenValues)) {
        if (-not [string]::IsNullOrWhiteSpace("$forbidden") -and $payload -like "*$forbidden*") {
            throw "$Name leaked forbidden URL value '$forbidden': $payload"
        }
    }
    return $response
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

function Resolve-SandboxSessionIdFromArtifact {
    param([string]$ArtifactId, [string]$Label)
    $safeArtifactId = $ArtifactId.Replace("'", "''")
    $sessionId = Invoke-PostgresScalar "SELECT session_id FROM sa_sandbox_artifact WHERE artifact_id = '$safeArtifactId';"
    if ([string]::IsNullOrWhiteSpace($sessionId)) {
        throw "Could not resolve sandbox session id for ${Label}: $ArtifactId"
    }
    return $sessionId
}

function New-RealAgentRunId {
    param(
        [hashtable]$Headers,
        [string]$Marker,
        [long]$RunProfileId
    )

    $created = Invoke-Json -Method POST -Path "/api/conversations" -Headers $Headers
    Assert-ApiOk $created "Create browser smoke conversation"
    if (-not $created.data) {
        throw "Create conversation response did not include id"
    }
    $conversationId = "$($created.data)"
    $question = "Sandbox browser smoke $Marker. Reply with one short sentence."
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

function Invoke-NativeCommandCapture {
    param(
        [string]$Command,
        [string[]]$Arguments
    )
    $commands = @(Get-Command $Command -CommandType Application -ErrorAction Stop)
    $resolved = @($commands | Where-Object { "$($_.Extension)" -eq ".exe" } | Select-Object -First 1)
    if ($resolved.Count -eq 0) {
        $resolved = @($commands | Select-Object -First 1)
    }
    $resolvedCommand = "$($resolved[0].Source)"
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new($resolvedCommand)
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.Arguments = ($Arguments | ForEach-Object {
        $argument = "$_"
        if ($argument -match '[\s"]') {
            '"' + ($argument -replace '"', '\"') + '"'
        } else {
            $argument
        }
    }) -join " "
    $process = [System.Diagnostics.Process]::Start($startInfo)
    if ($null -eq $process) {
        throw "failed to start native command $resolvedCommand"
    }
    try {
        $stdout = $process.StandardOutput.ReadToEnd()
        $stderr = $process.StandardError.ReadToEnd()
        $process.WaitForExit()
        $output = @()
        foreach ($chunk in @($stdout, $stderr)) {
            if (-not [string]::IsNullOrEmpty($chunk)) {
                $output += @($chunk -split "\r?\n" | Where-Object { $_ -ne "" })
            }
        }
        [pscustomobject]@{
            ExitCode = $process.ExitCode
            Output = $output
        }
    } finally {
        $process.Dispose()
    }
}

function Ensure-DockerImage {
    param([string]$Image)
    if ([string]::IsNullOrWhiteSpace($Image)) {
        throw "BrowserImage must not be blank"
    }
    $imageList = Invoke-NativeCommandCapture -Command "docker" -Arguments @("image", "ls", "--quiet", $Image)
    if ($imageList.ExitCode -ne 0) {
        throw "docker image ls failed with $($imageList.ExitCode) for image ${Image}: $($imageList.Output -join "`n")"
    }
    $imageIds = @($imageList.Output | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($imageIds.Count -gt 0) {
        return
    }

    if (-not $SkipBrowserImageBuild -and (Test-Path -LiteralPath $BrowserImageDockerfile)) {
        Write-Host "  Building $Image for first-run browser sandbox execution" -ForegroundColor DarkGray
        $build = Invoke-NativeCommandCapture -Command "docker" -Arguments @(
            "build",
            "-f",
            $BrowserImageDockerfile,
            "-t",
            $Image,
            $BrowserImageBuildContext
        )
        $build.Output | ForEach-Object { Write-Host "  $_" -ForegroundColor DarkGray }
        if ($build.ExitCode -ne 0) {
            throw "docker build failed with $($build.ExitCode) for image ${Image}: $($build.Output -join "`n")"
        }
        return
    }

    Write-Host "  Pulling $Image for first-run browser sandbox execution" -ForegroundColor DarkGray
    $pull = Invoke-NativeCommandCapture -Command "docker" -Arguments @("pull", $Image)
    $pull.Output | ForEach-Object { Write-Host "  $_" -ForegroundColor DarkGray }
    if ($pull.ExitCode -ne 0) {
        throw "docker pull failed with $($pull.ExitCode) for image ${Image}: $($pull.Output -join "`n")"
    }
}

function Start-ExternalHttpFixture {
    param(
        [string]$Name,
        [int]$Port,
        [string]$HostName,
        [string]$Marker,
        [string]$AssetUrl = ""
    )
    if ([string]::IsNullOrWhiteSpace($Name)) {
        throw "External HTTP container name must not be blank"
    }
    $root = Join-Path ([System.IO.Path]::GetTempPath()) $Name
    if (Test-Path -LiteralPath $root) {
        Remove-Item -LiteralPath $root -Recurse -Force
    }
    New-Item -ItemType Directory -Path $root | Out-Null
    $cookieName = "seahorse_browser_session"
    $cookieValue = "$Marker-session"
    $storageValue = "$Marker-local-storage-secret"
    $authMarker = "$Marker-authenticated"
    $serverScript = @"
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

MARKER = "$Marker"
COOKIE_NAME = "$cookieName"
COOKIE_VALUE = "$cookieValue"
STORAGE_VALUE = "$storageValue"
AUTH_MARKER = "$authMarker"
ASSET_URL = "$AssetUrl"

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path.startswith("/asset"):
            body = f"window.__seahorseAssetMarker = 'asset-ok:{MARKER}';".encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/javascript; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        cookie = self.headers.get("Cookie", "")
        authenticated = f"{COOKIE_NAME}={COOKIE_VALUE}" in cookie
        auth_html = f"<p>{AUTH_MARKER}</p>" if authenticated else "<p>anonymous</p>"
        asset_html = f'<script src="{ASSET_URL}"></script><script>fetch("http://example.invalid/blocked?marker={MARKER}", {{cache: "no-store"}}).catch(() => {{ document.body.dataset.blocked = "true"; }});</script>' if ASSET_URL else ""
        body = f"""<!doctype html>
<html>
<head><title>External {MARKER}</title></head>
<body><main><h1>{MARKER}</h1><p>Seahorse browser sandbox URL mode marker.</p>{auth_html}<p id="storage-status"></p></main><script>const restored = localStorage.getItem("seahorse_session_marker"); document.getElementById("storage-status").textContent = restored ? "storage-restored" : "storage-missing"; localStorage.setItem("seahorse_session_marker", "{STORAGE_VALUE}");</script>{asset_html}</body>
</html>
""".encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format, *args):
        return

ThreadingHTTPServer(("0.0.0.0", 8080), Handler).serve_forever()
"@
    Set-Content -LiteralPath (Join-Path $root "server.py") -Value $serverScript -Encoding UTF8

    $run = Invoke-NativeCommandCapture -Command "docker" -Arguments @(
        "run",
        "-d",
        "--rm",
        "--name",
        $Name,
        "-p",
        "${Port}:8080",
        "-v",
        "${root}:/srv:ro",
        "-w",
        "/srv",
        "python:3.11-alpine",
        "python",
        "server.py"
    )
    if ($run.ExitCode -ne 0) {
        throw "docker run failed for external HTTP fixture: $($run.Output -join "`n")"
    }

    $localUrl = "http://127.0.0.1:$Port/index.html?marker=$Marker"
    for ($attempt = 1; $attempt -le 30; $attempt++) {
        $probe = Invoke-NativeCommandCapture -Command "curl" -Arguments @("-fsS", $localUrl)
        $content = $probe.Output -join "`n"
        if ($probe.ExitCode -eq 0 -and "$content" -like "*$Marker*") {
            return [pscustomobject]@{
                Name = $Name
                Root = $root
                Url = "http://${HostName}:$Port/index.html?marker=$Marker"
                CookieName = $cookieName
                CookieValue = $cookieValue
                StorageValue = $storageValue
                AuthMarker = $authMarker
                AssetUrl = $AssetUrl
            }
        }
        Start-Sleep -Seconds 1
    }
    throw "Timed out waiting for external HTTP fixture at $localUrl"
}

function Stop-ExternalHttpFixture {
    param(
        [string]$Name,
        [string]$Root
    )
    if (-not [string]::IsNullOrWhiteSpace($Name)) {
        try {
            $stop = Invoke-NativeCommandCapture -Command "docker" -Arguments @("rm", "-f", $Name)
            if ($stop.ExitCode -ne 0) {
                Write-Host "  WARN: failed to remove external HTTP fixture ${Name}: $($stop.Output -join "`n")" -ForegroundColor Yellow
            }
        } catch {
            Write-Host "  WARN: failed to remove external HTTP fixture ${Name}: $($_.Exception.Message)" -ForegroundColor Yellow
        }
    }
    if (-not [string]::IsNullOrWhiteSpace($Root) -and (Test-Path -LiteralPath $Root)) {
        try {
            Remove-Item -LiteralPath $Root -Recurse -Force
        } catch {
            Write-Host "  WARN: failed to remove external HTTP fixture workspace ${Root}: $($_.Exception.Message)" -ForegroundColor Yellow
        }
    }
}

try {
    if (-not $SkipHealth) {
        Test-Step "Wait for backend health" {
            Wait-ForHealth
        } | Out-Null
    }

    $baselineNonTerminalSandboxSessions = [int](Invoke-PostgresScalar "SELECT count(*) FROM sa_sandbox_session WHERE status NOT IN ('SUCCEEDED','FAILED','CANCELLED','TIMED_OUT');")

    $imageReady = Test-Step "Ensure browser runtime image is available" {
        Ensure-DockerImage -Image $BrowserImage
        $true
    }
    if (-not $imageReady) { exit 1 }

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
    $smokeRun = Test-Step "Create real agent run for governed browser tool binding" {
        New-RealAgentRunId -Headers $headers -Marker $Marker -RunProfileId $KernelRunProfileId
    }
    if (-not $smokeRun) { exit 1 }
    $runId = "$($smokeRun.RunId)"
    $toolCallId = "sandbox-browser-call-$suffix"
    $html = "<!doctype html><html><head><title>Browser $Marker</title></head><body><main><h1>$Marker</h1><p>Seahorse browser sandbox smoke.</p><img alt='blocked' src='https://example.invalid/$Marker/pixel.png' /></main></body></html>"

    Test-Step "Verify sandbox_browser is cataloged" {
        $response = Invoke-Json -Method GET -Path "/api/tools?current=1&size=50&provider=BUILTIN&keyword=sandbox_browser" -Headers $headers
        Assert-ApiOk $response "List built-in tools"
        $records = @($response.data.records)
        $matched = @($records | Where-Object { "$($_.toolId)" -eq "sandbox_browser" })
        if ($matched.Count -ne 1) {
            throw "sandbox_browser not found in built-in tool catalog: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($matched[0].riskLevel)" -ne "HIGH") {
            throw "Expected sandbox_browser riskLevel=HIGH: $($matched[0] | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($matched[0].resourceType)" -ne "SANDBOX") {
            throw "Expected sandbox_browser resourceType=SANDBOX: $($matched[0] | ConvertTo-Json -Depth 20 -Compress)"
        }
    } | Out-Null

    $observation = Test-Step "Invoke sandbox_browser through Tool Gateway" {
        $body = @{
            runId = $runId
            stepId = "sandbox-browser-step-$suffix"
            toolCallId = $toolCallId
            agentId = "legacy-react-agent"
            tenantId = "default"
            userId = "$($login.data.userId)"
            agentIdentityId = "$($login.data.userId)"
            arguments = @{
                action = "snapshot"
                html = $html
                viewportWidth = 1024
                viewportHeight = 640
                screenshot = $true
                har = $true
                video = $true
            }
            resourceRefs = @{}
            idempotencyKey = "${runId}:${toolCallId}"
            allowedToolIds = @("sandbox_browser")
        }
        $response = Invoke-SandboxBrowserTool -Headers $headers -Body $body -Name "Invoke sandbox_browser"
        Assert-ApiOk $response "Invoke sandbox_browser"
        if ($response.data.success -ne $true) {
            throw "sandbox_browser failed: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $content = "$($response.data.content)"
        $parsed = $content | ConvertFrom-Json
        if ("$($parsed.runtimeType)" -ne "BROWSER_AUTOMATION") {
            throw "Expected BROWSER_AUTOMATION runtime: $content"
        }
        if ("$($parsed.executionStatus)" -ne "SUCCEEDED") {
            throw "Expected SUCCEEDED execution: $content"
        }
        if ("$($parsed.browser.action)" -ne "snapshot" -or "$($parsed.browser.networkAllowed)" -ne "False") {
            throw "Unexpected browser metadata: $content"
        }
        if ($parsed.browser.video -ne $true) {
            throw "Expected browser video flag in observation: $content"
        }
        $artifacts = @($parsed.artifacts)
        if ($artifacts.Count -ne 3) {
            throw "Expected prompt-visible browser result JSON, screenshot, and HAR artifacts: $content"
        }
        $jsonArtifact = @($artifacts | Where-Object { "$($_.mediaType)" -eq "application/json" })
        $pngArtifact = @($artifacts | Where-Object { "$($_.mediaType)" -eq "image/png" })
        $harArtifact = @($artifacts | Where-Object { "$($_.mediaType)" -eq "application/har+json" })
        if ($jsonArtifact.Count -ne 1 -or $pngArtifact.Count -ne 1 -or $harArtifact.Count -ne 1) {
            throw "Expected application/json, image/png, and application/har+json artifacts: $content"
        }
        if ($jsonArtifact[0].promptVisible -ne $true -or $pngArtifact[0].promptVisible -ne $true -or $harArtifact[0].promptVisible -ne $true) {
            throw "Expected prompt-visible browser artifacts: $content"
        }
        $parsed
    }
    if (-not $observation) { exit 1 }

    $jsonArtifactId = "$(@($observation.artifacts | Where-Object { "$($_.mediaType)" -eq "application/json" })[0].artifactId)"
    $pngArtifactId = "$(@($observation.artifacts | Where-Object { "$($_.mediaType)" -eq "image/png" })[0].artifactId)"
    $harArtifactId = "$(@($observation.artifacts | Where-Object { "$($_.mediaType)" -eq "application/har+json" })[0].artifactId)"
    $sessionId = Resolve-SandboxSessionIdFromArtifact -ArtifactId $jsonArtifactId -Label "inline browser result"

    $egressFixture = Test-Step "Start local HTTP fixture for sandbox_browser URL mode" {
        $assetUrl = "http://${AssetHost}:$AssetPort/asset.txt?marker=$Marker-url-asset"
        $assetFixture = Start-ExternalHttpFixture `
            -Name "seahorse-browser-asset-smoke-$suffix" `
            -Port $AssetPort `
            -HostName $AssetHost `
            -Marker "$Marker-url-asset"
        $script:assetHttpContainerName = "$($assetFixture.Name)"
        $script:assetHttpRoot = "$($assetFixture.Root)"
        Start-ExternalHttpFixture `
            -Name "seahorse-browser-egress-smoke-$suffix" `
            -Port $ExternalPort `
            -HostName $ExternalHost `
            -Marker "$Marker-url" `
            -AssetUrl $assetUrl
    }
    if (-not $egressFixture) { exit 1 }
    $externalHttpContainerName = "$($egressFixture.Name)"
    $externalHttpRoot = "$($egressFixture.Root)"
    $externalUrl = "$($egressFixture.Url)"
    $externalCookieName = "$($egressFixture.CookieName)"
    $externalCookieValue = "$($egressFixture.CookieValue)"
    $externalStorageValue = "$($egressFixture.StorageValue)"
    $externalAuthMarker = "$($egressFixture.AuthMarker)"
    $assetUrl = "$($egressFixture.AssetUrl)"

    $egressConfigOk = Test-Step "Verify sandbox URL egress allowlist is configured" {
        $profilesResponse = Invoke-Json -Method GET -Path "/api/sandbox/runtime/profiles" -Headers $headers
        Assert-ApiOk $profilesResponse "Get sandbox runtime profiles"
        if ("$($profilesResponse.data.defaultNetworkPolicy)" -ne "ALLOWLISTED") {
            throw "Expected defaultNetworkPolicy=ALLOWLISTED for URL egress smoke: $($profilesResponse.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $hosts = @($profilesResponse.data.allowlistedHosts)
        if ($hosts -notcontains $ExternalHost) {
            throw "Expected allowlistedHosts to contain ${ExternalHost}: $($profilesResponse.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ($hosts -notcontains $AssetHost) {
            throw "Expected allowlistedHosts to contain ${AssetHost}: $($profilesResponse.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $true
    }
    if (-not $egressConfigOk) { exit 1 }

    $profileNetworkEnabled = Test-Step "Enable browser runtime profile network for URL mode" {
        $response = Invoke-Json -Method POST -Path "/api/sandbox/runtime/profile-policies" -Headers $headers -Body @{
            tenantId = "default"
            runtimeType = "BROWSER_AUTOMATION"
            profileId = "browser-readonly"
            status = "ACTIVE"
            sessionTtlSeconds = 3600
            networkAllowed = $true
        }
        Assert-ApiOk $response "Enable browser runtime profile network"
        if ($response.data.networkAllowed -ne $true) {
            throw "Expected browser runtime profile networkAllowed=true: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $script:browserProfileNetworkEnabled = $true
        $true
    }
    if (-not $profileNetworkEnabled) { exit 1 }

    $urlFailureCases = @(
        @{
            Name = "userinfo"
            StepId = "sandbox-browser-url-userinfo-fail-step-$suffix"
            ToolCallId = "sandbox-browser-url-userinfo-fail-call-$suffix"
            Url = "http://alice:super-secret-userinfo-${suffix}@${ExternalHost}:$ExternalPort/index.html"
            ExpectedMessage = "must not include userinfo credentials"
            ForbiddenValues = @("alice:super-secret-userinfo-${suffix}", "super-secret-userinfo-${suffix}")
        },
        @{
            Name = "fragment"
            StepId = "sandbox-browser-url-fragment-fail-step-$suffix"
            ToolCallId = "sandbox-browser-url-fragment-fail-call-$suffix"
            Url = "${externalUrl}#access_token=fragment-secret-${suffix}"
            ExpectedMessage = "must not include fragment identifiers"
            ForbiddenValues = @("access_token=fragment-secret-${suffix}", "fragment-secret-${suffix}")
        },
        @{
            Name = "credential-query"
            StepId = "sandbox-browser-url-query-fail-step-$suffix"
            ToolCallId = "sandbox-browser-url-query-fail-call-$suffix"
            Url = "http://${ExternalHost}:$ExternalPort/index.html?access_token=query-secret-${suffix}"
            ExpectedMessage = "url query must not include credential parameters"
            ForbiddenValues = @("access_token=query-secret-${suffix}", "query-secret-${suffix}")
        },
        @{
            Name = "allowed-host-query-secret"
            StepId = "sandbox-browser-allowed-host-query-fail-step-$suffix"
            ToolCallId = "sandbox-browser-allowed-host-query-fail-call-$suffix"
            Url = $externalUrl
            AllowedHosts = @("${ExternalHost}?api_key=allowed-host-secret-${suffix}")
            ExpectedMessage = "allowedHosts must contain host names only"
            ForbiddenValues = @("api_key=allowed-host-secret-${suffix}", "allowed-host-secret-${suffix}")
        },
        @{
            Name = "localhost"
            StepId = "sandbox-browser-url-localhost-fail-step-$suffix"
            ToolCallId = "sandbox-browser-url-localhost-fail-call-$suffix"
            Url = "http://localhost:$ExternalPort/index.html"
            AllowedHosts = @("localhost")
            ExpectedMessage = "must be a valid dotted DNS host, not localhost or an IP literal"
            ForbiddenValues = @()
        },
        @{
            Name = "ipv4-literal"
            StepId = "sandbox-browser-url-ipv4-fail-step-$suffix"
            ToolCallId = "sandbox-browser-url-ipv4-fail-call-$suffix"
            Url = "http://127.0.0.1:$ExternalPort/index.html"
            AllowedHosts = @("127.0.0.1")
            ExpectedMessage = "must be a valid dotted DNS host, not localhost or an IP literal"
            ForbiddenValues = @()
        },
        @{
            Name = "ipv6-literal"
            StepId = "sandbox-browser-url-ipv6-fail-step-$suffix"
            ToolCallId = "sandbox-browser-url-ipv6-fail-call-$suffix"
            Url = "http://[::1]:$ExternalPort/index.html"
            AllowedHosts = @($ExternalHost)
            ExpectedMessage = "must be a valid dotted DNS host, not localhost or an IP literal"
            ForbiddenValues = @()
        },
        @{
            Name = "single-label-host"
            StepId = "sandbox-browser-url-single-label-fail-step-$suffix"
            ToolCallId = "sandbox-browser-url-single-label-fail-call-$suffix"
            Url = "http://metadata/index.html"
            AllowedHosts = @("metadata")
            ExpectedMessage = "must be a valid dotted DNS host, not localhost or an IP literal"
            ForbiddenValues = @()
        },
        @{
            Name = "malformed-dns-host"
            StepId = "sandbox-browser-url-malformed-host-fail-step-$suffix"
            ToolCallId = "sandbox-browser-url-malformed-host-fail-call-$suffix"
            Url = "http://aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.test/index.html"
            AllowedHosts = @("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.test")
            ExpectedMessage = "must be a valid dotted DNS host, not localhost or an IP literal"
            ForbiddenValues = @()
        }
    )

    Test-Step "Verify sandbox_browser URL secret inputs fail closed" {
        foreach ($case in @($urlFailureCases)) {
            $body = @{
                runId = $runId
                stepId = "$($case.StepId)"
                toolCallId = "$($case.ToolCallId)"
                agentId = "legacy-react-agent"
                tenantId = "default"
                userId = "$($login.data.userId)"
                agentIdentityId = "$($login.data.userId)"
                arguments = @{
                    action = "snapshot"
                    url = "$($case.Url)"
                    allowedHosts = @(if ($case.AllowedHosts) { $case.AllowedHosts } else { $ExternalHost })
                    viewportWidth = 1024
                    viewportHeight = 640
                    screenshot = $false
                    har = $false
                    video = $false
                }
                resourceRefs = @{}
                idempotencyKey = "${runId}:$($case.ToolCallId)"
                allowedToolIds = @("sandbox_browser")
            }
            Invoke-ExpectedSandboxBrowserUrlFailure `
                -Headers $headers `
                -Body $body `
                -Name "Invoke sandbox_browser URL $($case.Name) fail-closed" `
                -ExpectedMessage "$($case.ExpectedMessage)" `
                -ForbiddenValues @($case.ForbiddenValues) | Out-Null
        }
    } | Out-Null

    $cookieFailureCases = @(
        @{
            Name = "cookie-domain-not-allowed"
            StepId = "sandbox-browser-cookie-domain-allowlist-fail-step-$suffix"
            ToolCallId = "sandbox-browser-cookie-domain-allowlist-fail-call-$suffix"
            ExpectedMessage = "cookie domain must be included in allowedHosts"
            Cookies = @(
                @{
                    name = "seahorse_browser_session"
                    value = "cookie-domain-allowlist-secret-${suffix}"
                    domain = $AssetHost
                    path = "/"
                }
            )
            ForbiddenValues = @("cookie-domain-allowlist-secret-${suffix}")
        },
        @{
            Name = "cookie-domain-host-mismatch"
            StepId = "sandbox-browser-cookie-domain-mismatch-fail-step-$suffix"
            ToolCallId = "sandbox-browser-cookie-domain-mismatch-fail-call-$suffix"
            ExpectedMessage = "cookie domain must match the target URL host"
            Cookies = @(
                @{
                    name = "seahorse_browser_session"
                    value = "cookie-domain-mismatch-secret-${suffix}"
                    domain = $AssetHost
                    path = "/"
                }
            )
            AllowedHosts = @($ExternalHost, $AssetHost)
            ForbiddenValues = @("cookie-domain-mismatch-secret-${suffix}")
        }
    )

    Test-Step "Verify sandbox_browser cookie domain secret inputs fail closed" {
        foreach ($case in @($cookieFailureCases)) {
            $body = @{
                runId = $runId
                stepId = "$($case.StepId)"
                toolCallId = "$($case.ToolCallId)"
                agentId = "legacy-react-agent"
                tenantId = "default"
                userId = "$($login.data.userId)"
                agentIdentityId = "$($login.data.userId)"
                arguments = @{
                    action = "snapshot"
                    url = $externalUrl
                    allowedHosts = @(if ($case.AllowedHosts) { $case.AllowedHosts } else { $ExternalHost })
                    cookies = @($case.Cookies)
                    viewportWidth = 1024
                    viewportHeight = 640
                    screenshot = $false
                    har = $false
                    video = $false
                }
                resourceRefs = @{}
                idempotencyKey = "${runId}:$($case.ToolCallId)"
                allowedToolIds = @("sandbox_browser")
            }
            Invoke-ExpectedSandboxBrowserUrlFailure `
                -Headers $headers `
                -Body $body `
                -Name "Invoke sandbox_browser cookie $($case.Name) fail-closed" `
                -ExpectedMessage "$($case.ExpectedMessage)" `
                -ForbiddenValues @($case.ForbiddenValues) | Out-Null
        }
    } | Out-Null

    $sessionStateFailureCases = @(
        @{
            Name = "leading-dot-cookie-domain"
            StepId = "sandbox-browser-session-cookie-domain-fail-step-$suffix"
            ToolCallId = "sandbox-browser-session-cookie-domain-fail-call-$suffix"
            ExpectedMessage = "cookie domain must be a host name only"
            SessionState = @{
                cookies = @(
                    @{
                        name = "restored_session"
                        value = "session-cookie-secret-${suffix}"
                        domain = ".${ExternalHost}"
                        path = "/"
                    }
                )
            }
            ForbiddenValues = @("session-cookie-secret-${suffix}")
        },
        @{
            Name = "unsupported-cookie-field"
            StepId = "sandbox-browser-session-cookie-field-fail-step-$suffix"
            ToolCallId = "sandbox-browser-session-cookie-field-fail-call-$suffix"
            ExpectedMessage = "sessionState cookie contains unsupported fields"
            SessionState = @{
                cookies = @(
                    @{
                        name = "restored_session"
                        value = "unsupported-cookie-secret-${suffix}"
                        domain = $ExternalHost
                        path = "/"
                        storageRef = "session-storage-ref-secret-${suffix}"
                    }
                )
            }
            ForbiddenValues = @("unsupported-cookie-secret-${suffix}", "session-storage-ref-secret-${suffix}")
        },
        @{
            Name = "cookie-domain-not-allowed"
            StepId = "sandbox-browser-session-cookie-allowlist-fail-step-$suffix"
            ToolCallId = "sandbox-browser-session-cookie-allowlist-fail-call-$suffix"
            ExpectedMessage = "sessionState cookie domain must be included in allowedHosts"
            SessionState = @{
                cookies = @(
                    @{
                        name = "restored_session"
                        value = "session-cookie-allowlist-secret-${suffix}"
                        domain = $AssetHost
                        path = "/"
                    }
                )
            }
            ForbiddenValues = @("session-cookie-allowlist-secret-${suffix}")
        },
        @{
            Name = "cookie-domain-host-mismatch"
            StepId = "sandbox-browser-session-cookie-host-fail-step-$suffix"
            ToolCallId = "sandbox-browser-session-cookie-host-fail-call-$suffix"
            ExpectedMessage = "sessionState cookie domain must match the target URL host"
            AllowedHosts = @($ExternalHost, $AssetHost)
            SessionState = @{
                cookies = @(
                    @{
                        name = "restored_session"
                        value = "session-cookie-host-mismatch-secret-${suffix}"
                        domain = $AssetHost
                        path = "/"
                    }
                )
            }
            ForbiddenValues = @("session-cookie-host-mismatch-secret-${suffix}")
        },
        @{
            Name = "origin-port-mismatch"
            StepId = "sandbox-browser-session-origin-port-fail-step-$suffix"
            ToolCallId = "sandbox-browser-session-origin-port-fail-call-$suffix"
            ExpectedMessage = "sessionState origin must match the target URL origin"
            SessionState = @{
                origins = @(
                    @{
                        origin = "http://${ExternalHost}:$($ExternalPort + 1)"
                        localStorage = @(
                            @{
                                name = "seahorse_session_marker"
                                value = "origin-storage-secret-${suffix}"
                            }
                        )
                    }
                )
            }
            ForbiddenValues = @("origin-storage-secret-${suffix}")
        },
        @{
            Name = "origin-host-not-allowed"
            StepId = "sandbox-browser-session-origin-allowlist-fail-step-$suffix"
            ToolCallId = "sandbox-browser-session-origin-allowlist-fail-call-$suffix"
            ExpectedMessage = "sessionState origin host must be included in allowedHosts"
            SessionState = @{
                origins = @(
                    @{
                        origin = "http://${AssetHost}:$AssetPort"
                        localStorage = @(
                            @{
                                name = "seahorse_session_marker"
                                value = "origin-allowlist-secret-${suffix}"
                            }
                        )
                    }
                )
            }
            ForbiddenValues = @("origin-allowlist-secret-${suffix}")
        },
        @{
            Name = "origin-host-mismatch"
            StepId = "sandbox-browser-session-origin-host-fail-step-$suffix"
            ToolCallId = "sandbox-browser-session-origin-host-fail-call-$suffix"
            ExpectedMessage = "sessionState origin host must match the target URL host"
            AllowedHosts = @($ExternalHost, $AssetHost)
            SessionState = @{
                origins = @(
                    @{
                        origin = "http://${AssetHost}:$AssetPort"
                        localStorage = @(
                            @{
                                name = "seahorse_session_marker"
                                value = "origin-host-mismatch-secret-${suffix}"
                            }
                        )
                    }
                )
            }
            ForbiddenValues = @("origin-host-mismatch-secret-${suffix}")
        },
        @{
            Name = "origin-credential-parts"
            StepId = "sandbox-browser-session-origin-credential-fail-step-$suffix"
            ToolCallId = "sandbox-browser-session-origin-credential-fail-call-$suffix"
            ExpectedMessage = "sessionState origin must be an origin only"
            SessionState = @{
                origins = @(
                    @{
                        origin = "http://alice:origin-userinfo-secret-${suffix}@${ExternalHost}:$ExternalPort/path?token=origin-query-secret-${suffix}#fragment"
                        localStorage = @(
                            @{
                                name = "seahorse_session_marker"
                                value = "origin-credential-secret-${suffix}"
                            }
                        )
                    }
                )
            }
            ForbiddenValues = @("alice:origin-userinfo-secret-${suffix}", "origin-userinfo-secret-${suffix}", "token=origin-query-secret-${suffix}", "origin-query-secret-${suffix}", "origin-credential-secret-${suffix}")
        }
    )

    Test-Step "Verify sandbox_browser sessionState secret inputs fail closed" {
        foreach ($case in @($sessionStateFailureCases)) {
            $body = @{
                runId = $runId
                stepId = "$($case.StepId)"
                toolCallId = "$($case.ToolCallId)"
                agentId = "legacy-react-agent"
                tenantId = "default"
                userId = "$($login.data.userId)"
                agentIdentityId = "$($login.data.userId)"
                arguments = @{
                    action = "snapshot"
                    url = $externalUrl
                    allowedHosts = @(if ($case.AllowedHosts) { $case.AllowedHosts } else { $ExternalHost })
                    sessionState = $case.SessionState
                    viewportWidth = 1024
                    viewportHeight = 640
                    screenshot = $false
                    har = $false
                    video = $false
                    captureSessionState = $false
                }
                resourceRefs = @{}
                idempotencyKey = "${runId}:$($case.ToolCallId)"
                allowedToolIds = @("sandbox_browser")
            }
            Invoke-ExpectedSandboxBrowserUrlFailure `
                -Headers $headers `
                -Body $body `
                -Name "Invoke sandbox_browser sessionState $($case.Name) fail-closed" `
                -ExpectedMessage "$($case.ExpectedMessage)" `
                -ForbiddenValues @($case.ForbiddenValues) | Out-Null
        }
    } | Out-Null

    $sessionStateArtifactFailureCases = @(
        @{
            Name = "invalid-artifact-id"
            StepId = "sandbox-browser-session-artifact-id-fail-step-$suffix"
            ToolCallId = "sandbox-browser-session-artifact-id-fail-call-$suffix"
            ExpectedMessage = "sessionStateArtifactId is invalid"
            SessionStateArtifactId = "sandbox_artifact_invalid/session-state-secret-${suffix}"
            ForbiddenValues = @("sandbox_artifact_invalid/session-state-secret-${suffix}", "session-state-secret-${suffix}")
        },
        @{
            Name = "explicit-and-artifact"
            StepId = "sandbox-browser-session-artifact-conflict-fail-step-$suffix"
            ToolCallId = "sandbox-browser-session-artifact-conflict-fail-call-$suffix"
            ExpectedMessage = "provide either sessionState or sessionStateArtifactId, not both"
            SessionStateArtifactId = "sandbox_artifact_conflict_secret_${suffix}"
            SessionState = @{
                cookies = @(
                    @{
                        name = "restored_session"
                        value = "session-artifact-conflict-cookie-secret-${suffix}"
                        domain = $ExternalHost
                        path = "/"
                    }
                )
            }
            ForbiddenValues = @("sandbox_artifact_conflict_secret_${suffix}", "session-artifact-conflict-cookie-secret-${suffix}")
        }
    )

    Test-Step "Verify sandbox_browser sessionState artifact inputs fail closed" {
        foreach ($case in @($sessionStateArtifactFailureCases)) {
            $arguments = @{
                action = "snapshot"
                url = $externalUrl
                allowedHosts = @($ExternalHost)
                sessionStateArtifactId = "$($case.SessionStateArtifactId)"
                viewportWidth = 1024
                viewportHeight = 640
                screenshot = $false
                har = $false
                video = $false
                captureSessionState = $false
            }
            if ($case.SessionState) {
                $arguments.sessionState = $case.SessionState
            }
            $body = @{
                runId = $runId
                stepId = "$($case.StepId)"
                toolCallId = "$($case.ToolCallId)"
                agentId = "legacy-react-agent"
                tenantId = "default"
                userId = "$($login.data.userId)"
                agentIdentityId = "$($login.data.userId)"
                arguments = $arguments
                resourceRefs = @{}
                idempotencyKey = "${runId}:$($case.ToolCallId)"
                allowedToolIds = @("sandbox_browser")
            }
            Invoke-ExpectedSandboxBrowserUrlFailure `
                -Headers $headers `
                -Body $body `
                -Name "Invoke sandbox_browser sessionState artifact $($case.Name) fail-closed" `
                -ExpectedMessage "$($case.ExpectedMessage)" `
                -ForbiddenValues @($case.ForbiddenValues) | Out-Null
        }
    } | Out-Null

    $urlObservation = Test-Step "Invoke sandbox_browser URL mode through Tool Gateway" {
        $urlToolCallId = "sandbox-browser-url-call-$suffix"
        $body = @{
            runId = $runId
            stepId = "sandbox-browser-url-step-$suffix"
            toolCallId = $urlToolCallId
            agentId = "legacy-react-agent"
            tenantId = "default"
            userId = "$($login.data.userId)"
            agentIdentityId = "$($login.data.userId)"
            arguments = @{
                action = "snapshot"
                url = $externalUrl
                allowedHosts = @($ExternalHost, $AssetHost)
                cookies = @(
                    @{
                        name = $externalCookieName
                        value = $externalCookieValue
                        domain = $ExternalHost
                        path = "/"
                        httpOnly = $true
                        secure = $false
                        sameSite = "Lax"
                    }
                )
                viewportWidth = 1024
                viewportHeight = 640
                screenshot = $true
                har = $true
                video = $false
                captureSessionState = $true
            }
            resourceRefs = @{}
            idempotencyKey = "${runId}:${urlToolCallId}"
            allowedToolIds = @("sandbox_browser")
        }
        $response = Invoke-SandboxBrowserTool -Headers $headers -Body $body -Name "Invoke sandbox_browser URL mode"
        Assert-ApiOk $response "Invoke sandbox_browser URL mode"
        if ($response.data.success -ne $true) {
            throw "sandbox_browser URL mode failed: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $content = "$($response.data.content)"
        $parsed = $content | ConvertFrom-Json
        if ("$($parsed.runtimeType)" -ne "BROWSER_AUTOMATION" -or "$($parsed.executionStatus)" -ne "SUCCEEDED") {
            throw "Expected succeeded BROWSER_AUTOMATION URL mode execution: $content"
        }
        if ($parsed.browser.networkAllowed -ne $true) {
            throw "Expected browser.networkAllowed=true for URL mode: $content"
        }
        $redactedExternalUrl = "http://${ExternalHost}:$ExternalPort/index.html?<redacted-query>"
        if ("$($parsed.browser.url)" -ne $externalUrl -and "$($parsed.browser.url)" -ne $redactedExternalUrl) {
            throw "Expected browser URL ${externalUrl} or governed redacted URL ${redactedExternalUrl}: $content"
        }
        if (@($parsed.browser.allowedHosts) -notcontains $ExternalHost) {
            throw "Expected browser allowedHosts to contain ${ExternalHost}: $content"
        }
        if (@($parsed.browser.allowedHosts) -notcontains $AssetHost) {
            throw "Expected browser allowedHosts to contain ${AssetHost}: $content"
        }
        if ([int]$parsed.browser.cookieCount -ne 1 -or @($parsed.browser.cookieDomains) -notcontains $ExternalHost) {
            throw "Expected browser cookie metadata for ${ExternalHost}: $content"
        }
        if ($parsed.browser.sessionState.captureRequested -ne $true) {
            throw "Expected browser sessionState capture metadata: $content"
        }
        if ($content -like "*$externalCookieValue*") {
            throw "Browser observation leaked cookie value: $content"
        }
        if ($content -like "*$externalStorageValue*") {
            throw "Browser observation leaked session storage value: $content"
        }
        $artifacts = @($parsed.artifacts)
        if ($artifacts.Count -ne 4) {
            throw "Expected URL mode result JSON, screenshot, HAR, and session summary artifacts: $content"
        }
        $jsonArtifacts = @($artifacts | Where-Object { "$($_.mediaType)" -eq "application/json" })
        if ($jsonArtifacts.Count -ne 2) {
            throw "Expected URL mode result JSON plus session summary JSON artifacts: $content"
        }
        $parsed
    }
    if (-not $urlObservation) { exit 1 }

    $urlJsonArtifactIds = @($urlObservation.artifacts | Where-Object { "$($_.mediaType)" -eq "application/json" } | ForEach-Object { "$($_.artifactId)" })
    $urlJsonArtifactId = $null
    $urlSessionSummaryArtifactId = $null
    $urlSessionStateArtifactId = $null
    $urlHarArtifactId = "$(@($urlObservation.artifacts | Where-Object { "$($_.mediaType)" -eq "application/har+json" })[0].artifactId)"
    $urlSessionId = Resolve-SandboxSessionIdFromArtifact -ArtifactId $urlHarArtifactId -Label "URL mode HAR"

    Test-Step "Verify persisted URL mode browser session" {
        $safeSessionId = $urlSessionId.Replace("'", "''")
        $sessionRow = Invoke-PostgresScalar "SELECT runtime_type, profile_id, status FROM sa_sandbox_session WHERE session_id = '$safeSessionId';"
        $sessionParts = $sessionRow -split "`t"
        if ($sessionParts.Count -ne 3 -or $sessionParts[0] -ne "BROWSER_AUTOMATION" -or $sessionParts[1] -ne "browser-readonly" -or $sessionParts[2] -ne "CANCELLED") {
            throw "Unexpected URL mode sandbox session row: $sessionRow"
        }
    } | Out-Null

    Test-Step "Download governed URL mode browser JSON artifacts" {
        foreach ($artifactId in $urlJsonArtifactIds) {
            $content = Invoke-Text -Method GET -Path "/api/sandbox/artifacts/$artifactId/download" -Headers $headers
            if ($content -like "*$externalCookieValue*") {
                throw "Downloaded URL mode JSON artifact leaked cookie value: $content"
            }
            if ($content -like "*$externalStorageValue*") {
                throw "Downloaded URL mode JSON artifact leaked session storage value: $content"
            }
            if ($content -match "objectUri|object_uri|storageRef|file:|local://|s3://") {
                throw "Downloaded URL mode JSON artifact leaked storage reference: $content"
            }
            if ($content -like "*$Marker-url*" -and $content -like "*$externalAuthMarker*") {
                if ($content -notlike "*`"source`": `"url`"*" -and $content -notlike "*`"source`":`"url`"*") {
                    throw "Downloaded URL mode browser result did not include source=url: $content"
                }
                if ($content -notlike "*host.docker.internal*") {
                    throw "Downloaded URL mode browser result did not include allowlisted host: $content"
                }
                if ($content -notlike "*assets.docker.internal*") {
                    throw "Downloaded URL mode browser result did not include asset allowlisted host: $content"
                }
                $script:urlJsonArtifactId = $artifactId
            } elseif ($content -like "*localStorageCount*" -and ($content -like "*`"count`": 1*" -or $content -like "*`"count`":1*")) {
                if ($content -notlike "*host.docker.internal*") {
                    throw "Downloaded session summary did not include cookie domain: $content"
                }
                $script:urlSessionSummaryArtifactId = $artifactId
            }
        }
        if ([string]::IsNullOrWhiteSpace($script:urlJsonArtifactId)) {
            throw "Could not identify URL mode browser result artifact from JSON artifacts: $($urlJsonArtifactIds -join ', ')"
        }
        if ([string]::IsNullOrWhiteSpace($script:urlSessionSummaryArtifactId)) {
            throw "Could not identify URL mode session summary artifact from JSON artifacts: $($urlJsonArtifactIds -join ', ')"
        }
    } | Out-Null

    Test-Step "Download governed URL mode browser HAR artifact" {
        $content = Invoke-Text -Method GET -Path "/api/sandbox/artifacts/$urlHarArtifactId/download" -Headers $headers
        $har = $content | ConvertFrom-Json
        $entries = @($har.log.entries)
        $mainRequests = @($entries | Where-Object { "$($_.request.url)" -eq $externalUrl })
        $assetRequests = @($entries | Where-Object { "$($_.request.url)" -eq $assetUrl })
        $blockedRequests = @($entries | Where-Object { "$($_.request.url)" -like "http://example.invalid/*$Marker*" })
        if ($mainRequests.Count -lt 1) {
            throw "Downloaded URL mode HAR did not include main URL ${externalUrl}: $content"
        }
        if ($mainRequests[0]._blocked -eq $true) {
            throw "Downloaded URL mode HAR marked main URL as blocked: $content"
        }
        if ([int]$mainRequests[0].response.status -ne 200) {
            throw "Downloaded URL mode HAR expected main URL status 200: $content"
        }
        if ($assetRequests.Count -lt 1) {
            throw "Downloaded URL mode HAR did not include allowed asset URL ${assetUrl}: $content"
        }
        if ($assetRequests[0]._blocked -eq $true -or [int]$assetRequests[0].response.status -ne 200) {
            throw "Downloaded URL mode HAR expected allowed asset request to return 200 unblocked: $content"
        }
        if ($blockedRequests.Count -lt 1 -or $blockedRequests[0]._blocked -ne $true) {
            throw "Downloaded URL mode HAR did not mark non-allowlisted request as blocked: $content"
        }
        if ($content -like "*$externalCookieValue*") {
            throw "Downloaded URL mode HAR leaked cookie value: $content"
        }
        if ($content -match "objectUri|object_uri|storageRef|file:|local://|s3://") {
            throw "Downloaded URL mode HAR leaked storage reference: $content"
        }
    } | Out-Null

    Test-Step "Verify governed URL mode session state artifacts" {
        $safeUrlSessionId = $urlSessionId.Replace("'", "''")
        $summaryRow = Invoke-PostgresScalar "SELECT artifact_id, media_type, scan_status, sensitivity, scan_summary FROM sa_sandbox_artifact WHERE session_id = '$safeUrlSessionId' AND object_uri LIKE '%browser-session-summary.json';"
        $summaryParts = $summaryRow -split "`t"
        if ($summaryParts.Count -ne 5 -or $summaryParts[1] -ne "application/json" -or $summaryParts[2] -ne "CLEAN" -or $summaryParts[3] -ne "INTERNAL") {
            throw "Unexpected session summary artifact row: $summaryRow"
        }
        if ($summaryParts[0] -ne $script:urlSessionSummaryArtifactId) {
            throw "Session summary artifact id mismatch, observation=$script:urlSessionSummaryArtifactId db=$($summaryParts[0])"
        }

        $stateRow = Invoke-PostgresScalar "SELECT artifact_id, media_type, scan_status, sensitivity, scan_summary FROM sa_sandbox_artifact WHERE session_id = '$safeUrlSessionId' AND object_uri LIKE '%browser-session-state.json';"
        $stateParts = $stateRow -split "`t"
        if ($stateParts.Count -ne 5 -or $stateParts[1] -ne "application/json" -or $stateParts[2] -ne "BLOCKED" -or $stateParts[3] -ne "SECRET") {
            throw "Unexpected session state artifact row: $stateRow"
        }
        if ("$($stateParts[4])" -ne "sensitive artifact metadata") {
            throw "Expected sensitive artifact metadata scan summary for session state: $stateRow"
        }
        $script:urlSessionStateArtifactId = "$($stateParts[0])"

        $detail = Invoke-Json -Method GET -Path "/api/sandbox/artifacts/$script:urlSessionStateArtifactId" -Headers $headers
        Assert-ApiOk $detail "Get governed session state artifact detail"
        if ($detail.data.downloadable -ne $false -or $detail.data.promptVisible -ne $false) {
            throw "Expected session state artifact to be non-downloadable and prompt hidden: $($detail.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ("$($detail.data.sensitivity)" -ne "SECRET" -or "$($detail.data.scanStatus)" -ne "BLOCKED") {
            throw "Expected SECRET/BLOCKED session state detail: $($detail.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $detailJson = $detail.data | ConvertTo-Json -Depth 20 -Compress
        if ($detailJson -match "objectUri|object_uri|storageRef|file:|local://|s3://|$externalCookieValue|$externalStorageValue") {
            throw "Session state artifact detail leaked storage or session values: $detailJson"
        }
    } | Out-Null

    $replayObservation = Test-Step "Invoke sandbox_browser URL mode with request session state replay" {
        $replayToolCallId = "sandbox-browser-replay-call-$suffix"
        $externalOrigin = "http://${ExternalHost}:$ExternalPort"
        $sessionState = @{
            cookies = @(
                @{
                    name = $externalCookieName
                    value = $externalCookieValue
                    domain = $ExternalHost
                    path = "/"
                    expires = -1
                    httpOnly = $true
                    secure = $false
                    sameSite = "Lax"
                }
            )
            origins = @(
                @{
                    origin = $externalOrigin
                    localStorage = @(
                        @{
                            name = "seahorse_session_marker"
                            value = $externalStorageValue
                        }
                    )
                }
            )
        }
        $body = @{
            runId = $runId
            stepId = "sandbox-browser-replay-step-$suffix"
            toolCallId = $replayToolCallId
            agentId = "legacy-react-agent"
            tenantId = "default"
            userId = "$($login.data.userId)"
            agentIdentityId = "$($login.data.userId)"
            arguments = @{
                action = "snapshot"
                url = $externalUrl
                allowedHosts = @($ExternalHost, $AssetHost)
                sessionState = $sessionState
                viewportWidth = 1024
                viewportHeight = 640
                screenshot = $true
                har = $true
                video = $false
                captureSessionState = $false
            }
            resourceRefs = @{}
            idempotencyKey = "${runId}:${replayToolCallId}"
            allowedToolIds = @("sandbox_browser")
        }
        $response = Invoke-SandboxBrowserTool -Headers $headers -Body $body -Name "Invoke sandbox_browser URL mode session replay"
        Assert-ApiOk $response "Invoke sandbox_browser URL mode session replay"
        if ($response.data.success -ne $true) {
            throw "sandbox_browser URL mode session replay failed: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $content = "$($response.data.content)"
        $parsed = $content | ConvertFrom-Json
        if ("$($parsed.runtimeType)" -ne "BROWSER_AUTOMATION" -or "$($parsed.executionStatus)" -ne "SUCCEEDED") {
            throw "Expected succeeded BROWSER_AUTOMATION replay execution: $content"
        }
        if ($parsed.browser.networkAllowed -ne $true) {
            throw "Expected browser.networkAllowed=true for replay URL mode: $content"
        }
        if ([int]$parsed.browser.cookieCount -ne 0) {
            throw "Expected no explicit cookie metadata for session replay request: $content"
        }
        if ($parsed.browser.sessionState.replayRequested -ne $true -or $parsed.browser.sessionState.captureRequested -ne $false) {
            throw "Expected replay-only sessionState metadata: $content"
        }
        if ($content -like "*$externalCookieValue*" -or $content -like "*$externalStorageValue*") {
            throw "Browser replay observation leaked session values: $content"
        }
        $artifacts = @($parsed.artifacts)
        if ($artifacts.Count -ne 3) {
            throw "Expected replay result JSON, screenshot, and HAR artifacts only: $content"
        }
        $jsonArtifacts = @($artifacts | Where-Object { "$($_.mediaType)" -eq "application/json" })
        $harArtifacts = @($artifacts | Where-Object { "$($_.mediaType)" -eq "application/har+json" })
        if ($jsonArtifacts.Count -ne 1 -or $harArtifacts.Count -ne 1) {
            throw "Expected one replay JSON artifact and one replay HAR artifact: $content"
        }
        $parsed
    }
    if (-not $replayObservation) { exit 1 }

    $replayJsonArtifactId = "$(@($replayObservation.artifacts | Where-Object { "$($_.mediaType)" -eq "application/json" })[0].artifactId)"
    $replayHarArtifactId = "$(@($replayObservation.artifacts | Where-Object { "$($_.mediaType)" -eq "application/har+json" })[0].artifactId)"
    $replaySessionId = Resolve-SandboxSessionIdFromArtifact -ArtifactId $replayHarArtifactId -Label "replay HAR"

    Test-Step "Download governed replay browser JSON artifact" {
        $content = Invoke-Text -Method GET -Path "/api/sandbox/artifacts/$replayJsonArtifactId/download" -Headers $headers
        if ($content -notlike "*$externalAuthMarker*") {
            throw "Replay browser result did not use restored cookie auth marker: $content"
        }
        if ($content -notlike "*storage-restored*") {
            throw "Replay browser result did not show restored localStorage marker: $content"
        }
        if ($content -like "*$externalCookieValue*" -or $content -like "*$externalStorageValue*") {
            throw "Replay browser result leaked session values: $content"
        }
        if ($content -notlike "*`"replayed`": true*" -and $content -notlike "*`"replayed`":true*") {
            throw "Replay browser result did not include safe replay metadata: $content"
        }
        if ($content -notlike "*localStorageCount*") {
            throw "Replay browser result did not include value-free localStorage count: $content"
        }
        if ($content -match "objectUri|object_uri|storageRef|file:|local://|s3://") {
            throw "Replay browser result leaked storage reference: $content"
        }
    } | Out-Null

    Test-Step "Download governed replay browser HAR artifact" {
        $content = Invoke-Text -Method GET -Path "/api/sandbox/artifacts/$replayHarArtifactId/download" -Headers $headers
        $har = $content | ConvertFrom-Json
        $entries = @($har.log.entries)
        $mainRequests = @($entries | Where-Object { "$($_.request.url)" -eq $externalUrl })
        if ($mainRequests.Count -lt 1 -or [int]$mainRequests[0].response.status -ne 200 -or $mainRequests[0]._blocked -eq $true) {
            throw "Replay HAR did not contain an allowed 200 main request: $content"
        }
        if ($content -like "*$externalCookieValue*" -or $content -like "*$externalStorageValue*") {
            throw "Replay HAR leaked session values: $content"
        }
        if ($content -match "objectUri|object_uri|storageRef|file:|local://|s3://") {
            throw "Replay HAR leaked storage reference: $content"
        }
    } | Out-Null

    Test-Step "Verify request session state replay inputs stay transient" {
        $safeReplaySessionId = $replaySessionId.Replace("'", "''")
        $transientCount = Invoke-PostgresScalar "SELECT count(*) FROM sa_sandbox_artifact WHERE session_id = '$safeReplaySessionId' AND (object_uri LIKE '%browser-session-state-input.json' OR object_uri LIKE '%browser-session-state.json' OR object_uri LIKE '%browser-session-summary.json');"
        if ([int]$transientCount -ne 0) {
            throw "Expected no collected session-state artifacts for replay-only request but got $transientCount"
        }
    } | Out-Null

    $artifactReplayObservation = Test-Step "Invoke sandbox_browser URL mode with captured session state artifact replay" {
        $artifactReplayToolCallId = "sandbox-browser-artifact-replay-call-$suffix"
        $body = @{
            runId = $runId
            stepId = "sandbox-browser-artifact-replay-step-$suffix"
            toolCallId = $artifactReplayToolCallId
            agentId = "legacy-react-agent"
            tenantId = "default"
            userId = "$($login.data.userId)"
            agentIdentityId = "$($login.data.userId)"
            arguments = @{
                action = "snapshot"
                url = $externalUrl
                allowedHosts = @($ExternalHost, $AssetHost)
                sessionStateArtifactId = $script:urlSessionStateArtifactId
                viewportWidth = 1024
                viewportHeight = 640
                screenshot = $true
                har = $true
                video = $false
                captureSessionState = $false
            }
            resourceRefs = @{}
            idempotencyKey = "${runId}:${artifactReplayToolCallId}"
            allowedToolIds = @("sandbox_browser")
        }
        $response = Invoke-SandboxBrowserTool -Headers $headers -Body $body -Name "Invoke sandbox_browser URL mode session artifact replay"
        Assert-ApiOk $response "Invoke sandbox_browser URL mode session artifact replay"
        if ($response.data.success -ne $true) {
            throw "sandbox_browser URL mode session artifact replay failed: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $content = "$($response.data.content)"
        $parsed = $content | ConvertFrom-Json
        if ("$($parsed.runtimeType)" -ne "BROWSER_AUTOMATION" -or "$($parsed.executionStatus)" -ne "SUCCEEDED") {
            throw "Expected succeeded BROWSER_AUTOMATION artifact replay execution: $content"
        }
        if ($parsed.browser.networkAllowed -ne $true) {
            throw "Expected browser.networkAllowed=true for artifact replay URL mode: $content"
        }
        if ([int]$parsed.browser.cookieCount -ne 0) {
            throw "Expected no explicit cookie metadata for artifact replay request: $content"
        }
        if ($parsed.browser.sessionState.replayRequested -ne $true -or $parsed.browser.sessionState.captureRequested -ne $false -or $parsed.browser.sessionState.artifactReplayRequested -ne $true) {
            throw "Expected artifact replay-only sessionState metadata: $content"
        }
        if ($content -like "*$externalCookieValue*" -or $content -like "*$externalStorageValue*" -or $content -like "*$script:urlSessionStateArtifactId*") {
            throw "Browser artifact replay observation leaked session value or artifact id: $content"
        }
        $artifacts = @($parsed.artifacts)
        if ($artifacts.Count -ne 3) {
            throw "Expected artifact replay result JSON, screenshot, and HAR artifacts only: $content"
        }
        $jsonArtifacts = @($artifacts | Where-Object { "$($_.mediaType)" -eq "application/json" })
        $harArtifacts = @($artifacts | Where-Object { "$($_.mediaType)" -eq "application/har+json" })
        if ($jsonArtifacts.Count -ne 1 -or $harArtifacts.Count -ne 1) {
            throw "Expected one artifact replay JSON artifact and one artifact replay HAR artifact: $content"
        }
        $parsed
    }
    if (-not $artifactReplayObservation) { exit 1 }

    $artifactReplayJsonArtifactId = "$(@($artifactReplayObservation.artifacts | Where-Object { "$($_.mediaType)" -eq "application/json" })[0].artifactId)"
    $artifactReplayHarArtifactId = "$(@($artifactReplayObservation.artifacts | Where-Object { "$($_.mediaType)" -eq "application/har+json" })[0].artifactId)"
    $artifactReplaySessionId = Resolve-SandboxSessionIdFromArtifact -ArtifactId $artifactReplayHarArtifactId -Label "artifact replay HAR"

    Test-Step "Download governed artifact replay browser JSON artifact" {
        $content = Invoke-Text -Method GET -Path "/api/sandbox/artifacts/$artifactReplayJsonArtifactId/download" -Headers $headers
        if ($content -notlike "*$externalAuthMarker*") {
            throw "Artifact replay browser result did not use restored cookie auth marker: $content"
        }
        if ($content -notlike "*storage-restored*") {
            throw "Artifact replay browser result did not show restored localStorage marker: $content"
        }
        if ($content -like "*$externalCookieValue*" -or $content -like "*$externalStorageValue*" -or $content -like "*$script:urlSessionStateArtifactId*") {
            throw "Artifact replay browser result leaked session values or artifact id: $content"
        }
        if ($content -notlike "*`"replayed`": true*" -and $content -notlike "*`"replayed`":true*") {
            throw "Artifact replay browser result did not include safe replay metadata: $content"
        }
        if ($content -notlike "*localStorageCount*") {
            throw "Artifact replay browser result did not include value-free localStorage count: $content"
        }
        if ($content -match "objectUri|object_uri|storageRef|file:|local://|s3://") {
            throw "Artifact replay browser result leaked storage reference: $content"
        }
    } | Out-Null

    Test-Step "Download governed artifact replay browser HAR artifact" {
        $content = Invoke-Text -Method GET -Path "/api/sandbox/artifacts/$artifactReplayHarArtifactId/download" -Headers $headers
        $har = $content | ConvertFrom-Json
        $entries = @($har.log.entries)
        $mainRequests = @($entries | Where-Object { "$($_.request.url)" -eq $externalUrl })
        if ($mainRequests.Count -lt 1 -or [int]$mainRequests[0].response.status -ne 200 -or $mainRequests[0]._blocked -eq $true) {
            throw "Artifact replay HAR did not contain an allowed 200 main request: $content"
        }
        if ($content -like "*$externalCookieValue*" -or $content -like "*$externalStorageValue*" -or $content -like "*$script:urlSessionStateArtifactId*") {
            throw "Artifact replay HAR leaked session values or artifact id: $content"
        }
        if ($content -match "objectUri|object_uri|storageRef|file:|local://|s3://") {
            throw "Artifact replay HAR leaked storage reference: $content"
        }
    } | Out-Null

    Test-Step "Verify artifact session state replay inputs stay transient" {
        $safeArtifactReplaySessionId = $artifactReplaySessionId.Replace("'", "''")
        $transientCount = Invoke-PostgresScalar "SELECT count(*) FROM sa_sandbox_artifact WHERE session_id = '$safeArtifactReplaySessionId' AND (object_uri LIKE '%browser-session-state-input.json' OR object_uri LIKE '%browser-session-state.json' OR object_uri LIKE '%browser-session-summary.json');"
        if ([int]$transientCount -ne 0) {
            throw "Expected no collected session-state artifacts for artifact replay-only request but got $transientCount"
        }
    } | Out-Null

    Test-Step "Verify sandbox_browser Tool Gateway audit summaries" {
        $response = Invoke-Json -Method GET -Path "/api/tool-invocations?current=1&size=50&runId=$runId&toolId=sandbox_browser" -Headers $headers
        Assert-ApiOk $response "Read sandbox_browser tool audit"
        $records = Get-PageRecords $response.data
        $expectedSteps = @(
            @{
                StepId = "sandbox-browser-step-$suffix"
                Status = "SUCCEEDED"
                Required = @(
                    '"toolId":"sandbox_browser"',
                    '"mode":"inline"',
                    '"networkRequested":false',
                    '"htmlPresent":true',
                    '"har":true',
                    '"video":true'
                )
            },
            @{
                StepId = "sandbox-browser-url-step-$suffix"
                Status = "SUCCEEDED"
                Required = @(
                    '"toolId":"sandbox_browser"',
                    '"mode":"url"',
                    '"networkRequested":true',
                    '"allowedHostCount":2',
                    '"cookieCount":1',
                    '"captureSessionState":true',
                    '"sessionStateReplayRequested":false'
                )
            },
            @{
                StepId = "sandbox-browser-replay-step-$suffix"
                Status = "SUCCEEDED"
                Required = @(
                    '"toolId":"sandbox_browser"',
                    '"mode":"url"',
                    '"networkRequested":true',
                    '"sessionStateReplayRequested":true',
                    '"sessionStateCookieCount":1',
                    '"sessionStateOriginCount":1',
                    '"sessionStateLocalStorageItemCount":1',
                    '"captureSessionState":false'
                )
            },
            @{
                StepId = "sandbox-browser-artifact-replay-step-$suffix"
                Status = "SUCCEEDED"
                Required = @(
                    '"toolId":"sandbox_browser"',
                    '"mode":"url"',
                    '"networkRequested":true',
                    '"sessionStateArtifactReplayRequested":true',
                    '"sessionStateReplayRequested":false',
                    '"captureSessionState":false'
                )
            }
        )
        foreach ($case in @($urlFailureCases)) {
            $expectedSteps += @{
                StepId = "$($case.StepId)"
                Status = "FAILED"
                Required = @(
                    '"toolId":"sandbox_browser"',
                    '"mode":"url"',
                    '"networkRequested":true',
                    '"allowedHostCount":1',
                    '"cookieCount":0',
                    '"captureSessionState":false'
                )
                Forbidden = @($case.ForbiddenValues)
            }
        }
        foreach ($case in @($cookieFailureCases)) {
            $expectedSteps += @{
                StepId = "$($case.StepId)"
                Status = "FAILED"
                Required = @(
                    '"toolId":"sandbox_browser"',
                    '"mode":"url"',
                    '"networkRequested":true',
                    """allowedHostCount"":$(if ($case.AllowedHosts) { 2 } else { 1 })",
                    '"cookieCount":1',
                    '"captureSessionState":false'
                )
                Forbidden = @($case.ForbiddenValues)
            }
        }
        foreach ($case in @($sessionStateFailureCases)) {
            $expectedSteps += @{
                StepId = "$($case.StepId)"
                Status = "FAILED"
                Required = @(
                    '"toolId":"sandbox_browser"',
                    '"mode":"url"',
                    '"networkRequested":true',
                    """allowedHostCount"":$(if ($case.AllowedHosts) { 2 } else { 1 })",
                    '"cookieCount":0',
                    '"sessionStateReplayRequested":true',
                    '"captureSessionState":false'
                )
                Forbidden = @($case.ForbiddenValues)
            }
        }
        foreach ($case in @($sessionStateArtifactFailureCases)) {
            $expectedSteps += @{
                StepId = "$($case.StepId)"
                Status = "FAILED"
                Required = @(
                    '"toolId":"sandbox_browser"',
                    '"mode":"url"',
                    '"networkRequested":true',
                    '"allowedHostCount":1',
                    '"cookieCount":0',
                    '"sessionStateArtifactReplayRequested":true',
                    '"captureSessionState":false'
                )
                Forbidden = @($case.ForbiddenValues)
            }
        }
        foreach ($expected in $expectedSteps) {
            $audit = @($records | Where-Object { "$($_.stepId)" -eq "$($expected.StepId)" -and "$($_.toolId)" -eq "sandbox_browser" }) | Select-Object -First 1
            if (-not $audit) {
                throw "sandbox_browser audit record not found for step $($expected.StepId): $($response.data | ConvertTo-Json -Depth 20 -Compress)"
            }
            $expectedStatus = if ($expected.Status) { "$($expected.Status)" } else { "SUCCEEDED" }
            if ("$($audit.status)" -ne $expectedStatus) {
                throw "sandbox_browser audit status mismatch for step $($expected.StepId): $($audit | ConvertTo-Json -Depth 20 -Compress)"
            }
            $summary = "$($audit.argumentsSummary)"
            foreach ($required in @($expected.Required)) {
                if ($summary -notlike "*$required*") {
                    throw "sandbox_browser audit summary for step $($expected.StepId) did not include $required`: $summary"
                }
            }
            $forbiddenValues = @(
                $Marker,
                $externalUrl,
                $assetUrl,
                $externalCookieName,
                $externalCookieValue,
                $externalStorageValue,
                $externalAuthMarker,
                $script:urlSessionStateArtifactId
            ) + @($expected.Forbidden)
            foreach ($forbidden in $forbiddenValues) {
                if (-not [string]::IsNullOrWhiteSpace("$forbidden") -and $summary -like "*$forbidden*") {
                    throw "sandbox_browser audit summary leaked raw browser/session value '$forbidden': $summary"
                }
            }
        }
    } | Out-Null

    Test-Step "Restore browser runtime profile network deny" {
        $response = Invoke-Json -Method POST -Path "/api/sandbox/runtime/profile-policies" -Headers $headers -Body @{
            tenantId = "default"
            runtimeType = "BROWSER_AUTOMATION"
            profileId = "browser-readonly"
            status = "ACTIVE"
            sessionTtlSeconds = 3600
            networkAllowed = $false
        }
        Assert-ApiOk $response "Restore browser runtime profile network"
        $script:browserProfileNetworkEnabled = $false
    } | Out-Null

    $artifactEvidence = Test-Step "Verify persisted BROWSER_AUTOMATION session and artifacts" {
        $safeSessionId = $sessionId.Replace("'", "''")
        $sessionRow = Invoke-PostgresScalar "SELECT runtime_type, profile_id, status FROM sa_sandbox_session WHERE session_id = '$safeSessionId';"
        $sessionParts = $sessionRow -split "`t"
        if ($sessionParts.Count -ne 3) {
            throw "Unexpected sa_sandbox_session row: $sessionRow"
        }
        if ($sessionParts[0] -ne "BROWSER_AUTOMATION") {
            throw "Expected runtime_type BROWSER_AUTOMATION but got '$($sessionParts[0])'"
        }
        if ($sessionParts[1] -ne "browser-readonly") {
            throw "Expected profile_id browser-readonly but got '$($sessionParts[1])'"
        }
        if ($sessionParts[2] -ne "CANCELLED") {
            throw "Expected closed session status CANCELLED but got '$($sessionParts[2])'"
        }

        $safeJsonArtifactId = $jsonArtifactId.Replace("'", "''")
        $jsonRow = Invoke-PostgresScalar "SELECT object_uri, media_type, scan_status, sensitivity, scan_summary FROM sa_sandbox_artifact WHERE artifact_id = '$safeJsonArtifactId';"
        $jsonParts = $jsonRow -split "`t"
        if ($jsonParts.Count -ne 5 -or $jsonParts[1] -ne "application/json" -or $jsonParts[2] -ne "CLEAN" -or $jsonParts[3] -ne "INTERNAL") {
            throw "Unexpected browser JSON artifact row: $jsonRow"
        }
        if ($jsonParts[0] -like "file:*") {
            throw "browser result artifact still points at file URI: $($jsonParts[0])"
        }

        $safePngArtifactId = $pngArtifactId.Replace("'", "''")
        $pngRow = Invoke-PostgresScalar "SELECT object_uri, media_type, scan_status, sensitivity, scan_summary FROM sa_sandbox_artifact WHERE artifact_id = '$safePngArtifactId';"
        $pngParts = $pngRow -split "`t"
        if ($pngParts.Count -ne 5 -or $pngParts[1] -ne "image/png" -or $pngParts[2] -ne "CLEAN" -or $pngParts[3] -ne "INTERNAL") {
            throw "Unexpected browser PNG artifact row: $pngRow"
        }
        if ($pngParts[0] -like "file:*") {
            throw "browser screenshot artifact still points at file URI: $($pngParts[0])"
        }

        $safeHarArtifactId = $harArtifactId.Replace("'", "''")
        $harRow = Invoke-PostgresScalar "SELECT object_uri, media_type, scan_status, sensitivity, scan_summary FROM sa_sandbox_artifact WHERE artifact_id = '$safeHarArtifactId';"
        $harParts = $harRow -split "`t"
        if ($harParts.Count -ne 5 -or $harParts[1] -ne "application/har+json" -or $harParts[2] -ne "CLEAN" -or $harParts[3] -ne "INTERNAL") {
            throw "Unexpected browser HAR artifact row: $harRow"
        }
        if ($harParts[0] -like "file:*") {
            throw "browser HAR artifact still points at file URI: $($harParts[0])"
        }

        $videoRow = Invoke-PostgresScalar "SELECT artifact_id, object_uri, media_type, scan_status, sensitivity, scan_summary FROM sa_sandbox_artifact WHERE session_id = '$safeSessionId' AND media_type = 'video/webm' ORDER BY created_at DESC LIMIT 1;"
        $videoParts = $videoRow -split "`t"
        if ($videoParts.Count -ne 6 -or $videoParts[2] -ne "video/webm" -or $videoParts[3] -ne "CLEAN" -or $videoParts[4] -ne "INTERNAL") {
            throw "Unexpected browser video artifact row: $videoRow"
        }
        if ($videoParts[1] -like "file:*") {
            throw "browser video artifact still points at file URI: $($videoParts[1])"
        }
        [pscustomobject]@{
            ObjectUris = @($jsonParts[0], $pngParts[0], $harParts[0], $videoParts[1])
            VideoArtifactId = $videoParts[0]
        }
    }
    if (-not $artifactEvidence) { exit 1 }
    $objectUris = @($artifactEvidence.ObjectUris)
    $videoArtifactId = "$($artifactEvidence.VideoArtifactId)"

    Test-Step "Download governed browser result artifact" {
        $content = Invoke-Text -Method GET -Path "/api/sandbox/artifacts/$jsonArtifactId/download" -Headers $headers
        if ($content -notlike "*$Marker*") {
            throw "Downloaded browser result did not contain marker '$Marker': $content"
        }
        if ($content -notlike "*`"title`"*" -or $content -notlike "*`"textLength`"*") {
            throw "Downloaded browser result did not include title/textLength: $content"
        }
        if ($content -match "objectUri|object_uri|storageRef|file:|local://|s3://") {
            throw "Downloaded browser result leaked storage reference: $content"
        }
    } | Out-Null

    Test-Step "Download governed browser HAR artifact" {
        $content = Invoke-Text -Method GET -Path "/api/sandbox/artifacts/$harArtifactId/download" -Headers $headers
        if ($content -notlike "*`"log`"*" -or $content -notlike "*`"entries`"*") {
            throw "Downloaded browser HAR did not include log entries: $content"
        }
        if ($content -notlike "*example.invalid*" -or $content -notlike "*$Marker*") {
            throw "Downloaded browser HAR did not include blocked external request marker: $content"
        }
        if ($content -notlike "*`"_blocked`": true*" -and $content -notlike "*`"_blocked`":true*") {
            throw "Downloaded browser HAR did not mark external request as blocked: $content"
        }
        if ($content -match "objectUri|object_uri|storageRef|file:|local://|s3://") {
            throw "Downloaded browser HAR leaked storage reference: $content"
        }
    } | Out-Null

    Test-Step "Verify governed browser video detail and download" {
        $detail = Invoke-Json -Method GET -Path "/api/sandbox/artifacts/$videoArtifactId" -Headers $headers
        Assert-ApiOk $detail "Get browser video artifact"
        if ($detail.data.promptVisible -ne $false) {
            throw "Expected video artifact to stay prompt blocked: $($detail.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        if ($detail.data.downloadable -ne $true -or "$($detail.data.contentType)" -ne "video/webm") {
            throw "Expected downloadable video/webm artifact detail: $($detail.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $tempVideo = New-TemporaryFile
        try {
            $raw = & curl.exe -sS -w "`n%{http_code}" -H "Authorization: Bearer $($login.data.token)" -o $tempVideo.FullName "$BaseUrl/api/sandbox/artifacts/$videoArtifactId/download"
            if ($LASTEXITCODE -ne 0) {
                throw "curl exited with $LASTEXITCODE for video download"
            }
            $lines = @($raw)
            $status = [int]$lines[-1]
            if ($status -ne 200) {
                throw "Expected HTTP 200 for video download but got $status"
            }
            $bytes = [System.IO.File]::ReadAllBytes($tempVideo.FullName)
            if ($bytes.Length -lt 4) {
                throw "Downloaded browser video was too small"
            }
            if (-not ($bytes[0] -eq 0x1A -and $bytes[1] -eq 0x45 -and $bytes[2] -eq 0xDF -and $bytes[3] -eq 0xA3)) {
                throw "Downloaded browser video did not start with a WebM EBML header"
            }
        } finally {
            Remove-Item -LiteralPath $tempVideo.FullName -ErrorAction SilentlyContinue
        }
    } | Out-Null

    if (@($objectUris | Where-Object { $_.StartsWith($ExpectedObjectUriPrefix) }).Count -gt 0) {
        Test-Step "Verify browser artifact objects exist in backend storage" {
            foreach ($objectUri in $objectUris) {
                if (-not $objectUri.StartsWith($ExpectedObjectUriPrefix)) {
                    continue
                }
                $key = $objectUri.Substring($ExpectedObjectUriPrefix.Length)
                $path = "$StorageRoot/sandbox-artifacts/$key"
                $raw = & docker exec $BackendContainer test -f $path
                if ($LASTEXITCODE -ne 0) {
                    throw "Expected backend storage file not found: $path"
                }
            }
        } | Out-Null
    }

    Test-Step "Verify no managed sandbox containers remain" {
        $names = @(& docker ps -a --filter "name=seahorse-sandbox-" --format "{{.Names}}" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
        if ($LASTEXITCODE -ne 0) {
            throw "docker ps failed with $LASTEXITCODE"
        }
        if ($names.Count -ne 0) {
            throw "Expected no leftover managed sandbox containers, found: $($names -join ', ')"
        }
    } | Out-Null

    Test-Step "Verify sandbox_browser did not add non-terminal sessions" {
        $count = Invoke-PostgresScalar "SELECT count(*) FROM sa_sandbox_session WHERE status NOT IN ('SUCCEEDED','FAILED','CANCELLED','TIMED_OUT');"
        if ([int]$count -gt $baselineNonTerminalSandboxSessions) {
            throw "Expected sandbox_browser smoke not to add non-terminal sandbox sessions; baseline=$baselineNonTerminalSandboxSessions current=$count"
        }
    } | Out-Null

    Write-Host "`nSummary: $passed / $total passed, $failed failed" -ForegroundColor Cyan
    Write-Host "Backend: $BaseUrl"
    Write-Host "Tool: sandbox_browser"
    Write-Host "Session: $sessionId"
    Write-Host "JSON Artifact: $jsonArtifactId"
    Write-Host "Screenshot Artifact: $pngArtifactId"
    Write-Host "HAR Artifact: $harArtifactId"
    Write-Host "Video Artifact: $videoArtifactId"
    Write-Host "URL Session: $urlSessionId"
    Write-Host "URL Result Artifact: $urlJsonArtifactId"
    Write-Host "URL Session Summary Artifact: $urlSessionSummaryArtifactId"
    Write-Host "URL Session State Artifact: $urlSessionStateArtifactId"
    Write-Host "URL HAR Artifact: $urlHarArtifactId"
    Write-Host "Replay URL Session: $replaySessionId"
    Write-Host "Replay URL Result Artifact: $replayJsonArtifactId"
    Write-Host "Replay URL HAR Artifact: $replayHarArtifactId"
    Write-Host "Artifact Replay URL Session: $artifactReplaySessionId"
    Write-Host "Artifact Replay URL Result Artifact: $artifactReplayJsonArtifactId"
    Write-Host "Artifact Replay URL HAR Artifact: $artifactReplayHarArtifactId"
} catch {
    Write-Host "`nSummary: $passed / $total passed, $failed failed" -ForegroundColor Cyan
    Write-Error $_.Exception.Message
    exit 1
} finally {
    if ($browserProfileNetworkEnabled -and $null -ne $headers) {
        try {
            $restore = Invoke-Json -Method POST -Path "/api/sandbox/runtime/profile-policies" -Headers $headers -Body @{
                tenantId = "default"
                runtimeType = "BROWSER_AUTOMATION"
                profileId = "browser-readonly"
                status = "ACTIVE"
                sessionTtlSeconds = 3600
                networkAllowed = $false
            }
            if ($null -ne $restore) {
                Write-Host "  Restored browser runtime profile networkAllowed=false" -ForegroundColor DarkGray
            }
        } catch {
            Write-Host "  WARN: failed to restore browser runtime profile network policy: $($_.Exception.Message)" -ForegroundColor Yellow
        }
    }
    Stop-ExternalHttpFixture -Name $externalHttpContainerName -Root $externalHttpRoot
    Stop-ExternalHttpFixture -Name $assetHttpContainerName -Root $assetHttpRoot
}

if ($failed -gt 0) {
    exit 1
}
