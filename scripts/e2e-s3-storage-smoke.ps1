param(
    [string]$BaseUrl = "http://127.0.0.1:19092",
    [string]$Username = "admin",
    [string]$Password = "admin123",
    [string]$BackendImage = "seahorse-agent-backend:latest",
    [string]$BackendContainerName = "seahorse-s3-storage-smoke",
    [string]$DockerNetwork = "seahorse-agent_default",
    [int]$HostPort = 19092,
    [string]$PostgresHost = "postgres",
    [string]$PostgresContainer = "seahorse-postgres",
    [string]$PostgresDatabase = "seahorse",
    [string]$PostgresUsername = "seahorse",
    [string]$PostgresPassword = "seahorse",
    [string]$MinioContainer = "seahorse-minio",
    [string]$MinioEndpoint = "http://minio:9000",
    [string]$MinioInternalEndpoint = "http://127.0.0.1:9000",
    [string]$MinioAccessKey = "minioadmin",
    [string]$MinioSecretKey = "minioadmin",
    [string]$BackendJarPath = "",
    [switch]$KeepContainer
)

$ErrorActionPreference = "Stop"
$passed = 0
$failed = 0
$total = 0

$RepoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($BackendJarPath)) {
    $BackendJarPath = Join-Path $RepoRoot "seahorse-agent-bootstrap\target\seahorse-agent-bootstrap-0.0.1-SNAPSHOT-exec.jar"
}

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

function Remove-SmokeContainer {
    param([string]$Name)
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & docker.exe inspect $Name *> $null
        if ($LASTEXITCODE -eq 0) {
            & docker.exe rm -f $Name *> $null
        }
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
}

function Invoke-JsonAt {
    param(
        [string]$Url,
        [string]$Method,
        [object]$Body = $null,
        [hashtable]$Headers = @{},
        [int]$ExpectedStatus = 200
    )

    $bodyText = $null
    if ($null -ne $Body) {
        $bodyText = if ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Depth 30 -Compress }
    }

    $tempBodyFile = $null
    $args = @("-sS", "-w", "`n%{http_code}", "-X", $Method, $Url)
    if ($bodyText) {
        $tempBodyFile = New-TemporaryFile
        $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
        [System.IO.File]::WriteAllText($tempBodyFile.FullName, $bodyText, $utf8NoBom)
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
        throw "curl exited with $exitCode for $Method $Url"
    }

    $lines = @($raw)
    if ($lines.Count -eq 0) {
        throw "empty curl output for $Method $Url"
    }
    $status = [int]$lines[-1]
    $content = if ($lines.Count -gt 1) { ($lines[0..($lines.Count - 2)] -join "`n") } else { "" }
    if ($status -ne $ExpectedStatus) {
        throw "Expected HTTP $ExpectedStatus but got $status for $Method $Url body=$content"
    }
    if ([string]::IsNullOrWhiteSpace($content)) {
        return $null
    }
    return $content | ConvertFrom-Json
}

function Invoke-Json {
    param(
        [string]$Method,
        [string]$Path,
        [object]$Body = $null,
        [hashtable]$Headers = @{},
        [int]$ExpectedStatus = 200
    )
    return Invoke-JsonAt -Url "$BaseUrl$Path" -Method $Method -Body $Body -Headers $Headers -ExpectedStatus $ExpectedStatus
}

function Invoke-MultipartFile {
    param(
        [string]$Path,
        [string]$FilePath,
        [hashtable]$Headers = @{}
    )

    $args = @("-sS", "-w", "`n%{http_code}", "-X", "POST", "$BaseUrl$Path", "-F", "file=@$FilePath;type=text/plain")
    foreach ($key in $Headers.Keys) {
        $args += @("-H", "${key}: $($Headers[$key])")
    }

    $raw = & curl.exe @args
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        throw "curl exited with $exitCode for multipart POST $Path"
    }
    $lines = @($raw)
    if ($lines.Count -eq 0) {
        throw "empty curl output for multipart POST $Path"
    }
    $status = [int]$lines[-1]
    $content = if ($lines.Count -gt 1) { ($lines[0..($lines.Count - 2)] -join "`n") } else { "" }
    if ($status -ne 200) {
        throw "Expected HTTP 200 but got $status for multipart POST $Path body=$content"
    }
    return $content | ConvertFrom-Json
}

function Assert-ApiOk {
    param([object]$Response, [string]$Name)
    if ($null -eq $Response -or "$($Response.code)" -ne "0") {
        throw "$Name API error: $($Response | ConvertTo-Json -Depth 30 -Compress)"
    }
}

function Wait-ForHealth {
    param([string]$Url, [int]$Attempts = 90)
    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        try {
            $health = Invoke-JsonAt -Url $Url -Method GET
            if ($health.status -eq "UP") {
                return $health
            }
        } catch {
            if ($attempt -ge $Attempts) {
                throw
            }
        }
        Start-Sleep -Seconds 2
    }
    throw "Timed out waiting for health at $Url"
}

function Invoke-DbScalarRow {
    param([string]$Sql)
    $raw = & docker.exe exec $PostgresContainer psql -U $PostgresUsername -d $PostgresDatabase -t -A -F "|" -c $Sql
    if ($LASTEXITCODE -ne 0) {
        throw "psql failed with exit code $LASTEXITCODE"
    }
    $rows = @($raw | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($rows.Count -eq 0) {
        return $null
    }
    return $rows[0]
}

function Parse-S3Url {
    param([string]$Url)
    $match = [regex]::Match($Url, '^s3://([^/]+)/(.+)$')
    if (-not $match.Success) {
        throw "storage ref is not an s3 url: $Url"
    }
    return [PSCustomObject]@{
        Bucket = $match.Groups[1].Value
        Key = $match.Groups[2].Value
    }
}

function Invoke-Minio {
    param([string[]]$Arguments)
    $setup = "mc alias set seahorse $MinioInternalEndpoint $MinioAccessKey $MinioSecretKey >/dev/null"
    $command = "$setup && mc " + ($Arguments -join " ")
    $output = & docker.exe exec $MinioContainer sh -c $command
    if ($LASTEXITCODE -ne 0) {
        throw "mc command failed: mc $($Arguments -join ' ') output=$output"
    }
    return $output
}

try {
    Test-Step "Start temporary S3-enabled backend" {
        Remove-SmokeContainer -Name $BackendContainerName
        $args = @(
            "run", "-d",
            "--name", $BackendContainerName,
            "--network", $DockerNetwork,
            "-p", "${HostPort}:9090",
            "-e", "SERVER_PORT=9090",
            "-e", "MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,beans,conditions",
            "-e", "SEAHORSE_AGENT_PRODUCT_MODE=enterprise",
            "-e", "SPRING_DATASOURCE_URL=jdbc:postgresql://${PostgresHost}:5432/${PostgresDatabase}",
            "-e", "SPRING_DATASOURCE_USERNAME=$PostgresUsername",
            "-e", "SPRING_DATASOURCE_PASSWORD=$PostgresPassword",
            "-e", "SEAHORSE_AGENT_ADAPTERS_REPOSITORY_TYPE=jdbc",
            "-e", "SEAHORSE_AGENT_ADAPTERS_AI_TYPE=mock",
            "-e", "SEAHORSE_AGENT_ADAPTERS_VECTOR_TYPE=noop",
            "-e", "SEAHORSE_AGENT_ADAPTERS_CACHE_TYPE=local",
            "-e", "SEAHORSE_AGENT_ADAPTERS_CACHE_REDIS_HOST=redis",
            "-e", "SEAHORSE_AGENT_ADAPTERS_CACHE_REDIS_PORT=6379",
            "-e", "SPRING_DATA_REDIS_HOST=redis",
            "-e", "SPRING_DATA_REDIS_PORT=6379",
            "-e", "SEAHORSE_AGENT_ADAPTERS_MQ_TYPE=direct",
            "-e", "SEAHORSE_AGENT_ADAPTERS_OBSERVATION_TYPE=noop",
            "-e", "SEAHORSE_AGENT_ADAPTERS_STORAGE_TYPE=s3",
            "-e", "SEAHORSE_AGENT_ADAPTERS_STORAGE_S3_ENDPOINT=$MinioEndpoint",
            "-e", "SEAHORSE_AGENT_ADAPTERS_STORAGE_S3_ACCESS_KEY=$MinioAccessKey",
            "-e", "SEAHORSE_AGENT_ADAPTERS_STORAGE_S3_SECRET_KEY=$MinioSecretKey",
            "-e", "SEAHORSE_AGENT_ADAPTERS_STORAGE_S3_REGION=us-east-1",
            $BackendImage
        )
        if (Test-Path -LiteralPath $BackendJarPath) {
            $jarMount = "$($BackendJarPath):/app/app.jar:ro"
            $args = $args[0..($args.Count - 2)] + @("-v", $jarMount) + $args[-1]
        }
        $output = & docker.exe @args
        if ($LASTEXITCODE -ne 0) {
            throw "docker run failed: $output"
        }
        $output
    } | Out-Null

    Test-Step "Wait for S3 backend health" {
        Wait-ForHealth -Url "$BaseUrl/actuator/health"
    } | Out-Null
    if ($failed -gt 0) { exit 1 }

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

    $conversationId = Test-Step "Create conversation" {
        $response = Invoke-Json -Method POST -Path "/api/conversations" -Headers $headers
        Assert-ApiOk $response "Create conversation"
        if (-not $response.data) {
            throw "Create conversation response did not include id"
        }
        "$($response.data)"
    }
    if (-not $conversationId) { exit 1 }

    $tempFile = New-TemporaryFile
    $marker = "CODX_S3_STORAGE_$(Get-Date -Format yyyyMMddHHmmssfff)"
    Set-Content -LiteralPath $tempFile.FullName -Value "S3 storage smoke $marker" -Encoding UTF8 -NoNewline

    $attachment = Test-Step "Upload conversation attachment through S3 backend" {
        $response = Invoke-MultipartFile -Path "/api/conversations/$conversationId/attachments" -FilePath $tempFile.FullName -Headers $headers
        Assert-ApiOk $response "Upload attachment"
        if (-not $response.data.attachmentId) {
            throw "Upload response did not include attachmentId"
        }
        if ("$($response.data.storageRef)" -notlike "s3://*") {
            throw "Upload response storageRef was not s3: $($response.data | ConvertTo-Json -Depth 20 -Compress)"
        }
        $response.data | ConvertTo-Json -Depth 20 -Compress | Write-Host
        $response.data
    }
    if (-not $attachment) { exit 1 }

    $dbRow = Test-Step "Verify attachment DB row has s3 storage ref" {
        $sql = @"
select attachment_id,
       conversation_id,
       user_id,
       storage_ref,
       deleted
from sa_conversation_attachment
where attachment_id = '$($attachment.attachmentId)';
"@
        $row = Invoke-DbScalarRow $sql
        if (-not $row) {
            throw "No DB row found for attachment $($attachment.attachmentId)"
        }
        $parts = $row -split "\|", 5
        if ($parts.Count -lt 5) {
            throw "Unexpected DB row format: $row"
        }
        if ($parts[1] -ne $conversationId) {
            throw "DB conversation_id mismatch: $row"
        }
        if ($parts[3] -notlike "s3://*") {
            throw "DB storage_ref was not s3: $row"
        }
        if ($parts[4] -ne "0") {
            throw "DB deleted flag was not 0: $row"
        }
        $row | Write-Host
        $row
    }
    if (-not $dbRow) { exit 1 }

    $location = Parse-S3Url -Url "$($attachment.storageRef)"

    Test-Step "Verify object exists in MinIO" {
        $stat = Invoke-Minio -Arguments @("stat", "seahorse/$($location.Bucket)/$($location.Key)")
        $stat | Write-Host
    } | Out-Null

    Test-Step "List attachment through API" {
        $response = Invoke-Json -Method GET -Path "/api/conversations/$conversationId/attachments" -Headers $headers
        Assert-ApiOk $response "List attachments"
        $found = @($response.data | Where-Object { $_.attachmentId -eq $attachment.attachmentId }) | Select-Object -First 1
        if (-not $found) {
            throw "Uploaded attachment was not listed"
        }
        if ("$($found.storageRef)" -ne "$($attachment.storageRef)") {
            throw "Listed storageRef mismatch: $($found | ConvertTo-Json -Depth 20 -Compress)"
        }
    } | Out-Null

    Test-Step "Delete attachment through API" {
        $response = Invoke-Json -Method DELETE -Path "/api/conversations/$conversationId/attachments/$($attachment.attachmentId)" -Headers $headers
        Assert-ApiOk $response "Delete attachment"
        if ($response.data.deleted -ne $true) {
            throw "Delete response was not true: $($response | ConvertTo-Json -Depth 20 -Compress)"
        }
    } | Out-Null

    Test-Step "Verify DB soft delete and MinIO object removal" {
        $row = Invoke-DbScalarRow "select deleted from sa_conversation_attachment where attachment_id = '$($attachment.attachmentId)';"
        if ($row -ne "1") {
            throw "Attachment deleted flag expected 1 but got $row"
        }
        $previousErrorActionPreference = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        try {
            $setup = "mc alias set seahorse $MinioInternalEndpoint $MinioAccessKey $MinioSecretKey >/dev/null"
            $command = "$setup && mc stat seahorse/$($location.Bucket)/$($location.Key)"
            $output = & docker.exe exec $MinioContainer sh -c $command 2>&1
            $exitCode = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $previousErrorActionPreference
        }
        if ($exitCode -eq 0) {
            throw "MinIO object still exists after delete: $output"
        }
        "deleted=1; minio stat exit=$exitCode" | Write-Host
    } | Out-Null

    Write-Host "`nSummary: $passed / $total passed, $failed failed" -ForegroundColor Cyan
    Write-Host "Marker: $marker"
    Write-Host "Conversation ID: $conversationId"
    Write-Host "Attachment ID: $($attachment.attachmentId)"
    Write-Host "Storage ref: $($attachment.storageRef)"
    Write-Host "S3 bucket: $($location.Bucket)"
    Write-Host "S3 key: $($location.Key)"
} finally {
    if ($null -ne $tempFile -and (Test-Path -LiteralPath $tempFile.FullName)) {
        Remove-Item -LiteralPath $tempFile.FullName -ErrorAction SilentlyContinue
    }
    if (-not $KeepContainer) {
        Remove-SmokeContainer -Name $BackendContainerName
    }
}

if ($failed -gt 0) {
    exit 1
}
