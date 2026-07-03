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
    [string]$StorageRoot = "/app/seahorse-agent-storage",
    [string]$ExpectedObjectUriPrefix = "local://sandbox-artifacts/",
    [switch]$SkipBrowserImageBuild,
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

try {
    if (-not $SkipHealth) {
        Test-Step "Wait for backend health" {
            Wait-ForHealth
        } | Out-Null
    }

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
    $runId = "sandbox-browser-run-$suffix"
    $toolCallId = "sandbox-browser-call-$suffix"
    $html = "<!doctype html><html><head><title>Browser $Marker</title></head><body><main><h1>$Marker</h1><p>Seahorse browser sandbox smoke.</p></main></body></html>"

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
        $response = Invoke-Json -Method POST -Path "/api/tools/sandbox_browser/invoke" -Headers $headers -Body @{
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
            }
            resourceRefs = @{}
            idempotencyKey = "${runId}:${toolCallId}"
            allowedToolIds = @("sandbox_browser")
        }
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
        $artifacts = @($parsed.artifacts)
        if ($artifacts.Count -ne 2) {
            throw "Expected browser result JSON and screenshot artifacts: $content"
        }
        $jsonArtifact = @($artifacts | Where-Object { "$($_.mediaType)" -eq "application/json" })
        $pngArtifact = @($artifacts | Where-Object { "$($_.mediaType)" -eq "image/png" })
        if ($jsonArtifact.Count -ne 1 -or $pngArtifact.Count -ne 1) {
            throw "Expected application/json and image/png artifacts: $content"
        }
        if ($jsonArtifact[0].promptVisible -ne $true -or $pngArtifact[0].promptVisible -ne $true) {
            throw "Expected prompt-visible browser artifacts: $content"
        }
        $parsed
    }
    if (-not $observation) { exit 1 }

    $sessionId = "$($observation.sessionId)"
    $jsonArtifactId = "$(@($observation.artifacts | Where-Object { "$($_.mediaType)" -eq "application/json" })[0].artifactId)"
    $pngArtifactId = "$(@($observation.artifacts | Where-Object { "$($_.mediaType)" -eq "image/png" })[0].artifactId)"

    $objectUris = Test-Step "Verify persisted BROWSER_AUTOMATION session and artifacts" {
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
        @($jsonParts[0], $pngParts[0])
    }
    if (-not $objectUris) { exit 1 }

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

    if ($objectUris[0].StartsWith($ExpectedObjectUriPrefix) -or $objectUris[1].StartsWith($ExpectedObjectUriPrefix)) {
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

    Test-Step "Verify no non-terminal sandbox sessions remain" {
        $count = Invoke-PostgresScalar "SELECT count(*) FROM sa_sandbox_session WHERE status NOT IN ('SUCCEEDED','FAILED','CANCELLED','TIMED_OUT');"
        if ([int]$count -ne 0) {
            throw "Expected zero non-terminal sandbox sessions but got $count"
        }
    } | Out-Null

    Write-Host "`nSummary: $passed / $total passed, $failed failed" -ForegroundColor Cyan
    Write-Host "Backend: $BaseUrl"
    Write-Host "Tool: sandbox_browser"
    Write-Host "Session: $sessionId"
    Write-Host "JSON Artifact: $jsonArtifactId"
    Write-Host "Screenshot Artifact: $pngArtifactId"
} catch {
    Write-Host "`nSummary: $passed / $total passed, $failed failed" -ForegroundColor Cyan
    Write-Error $_.Exception.Message
    exit 1
}

if ($failed -gt 0) {
    exit 1
}
