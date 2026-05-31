# Seahorse Agent local Docker redeploy script (Windows)
# Usage:
#   .\redeploy.ps1 frontend [minimal|full]
#   .\redeploy.ps1 backend  [minimal|full]
#   .\redeploy.ps1 all      [minimal|full]
# Examples:
#   .\redeploy.ps1 frontend
#   .\redeploy.ps1 backend full
#   .\redeploy.ps1 all minimal -Logs

param(
    [ValidateSet("frontend", "backend", "all")]
    [string]$Target = "all",

    [ValidateSet("minimal", "min", "m", "full", "f")]
    [string]$Mode = "full",

    [switch]$NoBuild,
    [switch]$Logs
)

$ErrorActionPreference = "Stop"

function Resolve-ComposeFile {
    param([string]$Mode)

    if ($Mode -in @("minimal", "min", "m")) {
        return "docker-compose.yml"
    }

    return "docker-compose.full.yml"
}

function Resolve-Services {
    param([string]$Target)

    switch ($Target) {
        "frontend" { return @("frontend") }
        "backend" { return @("backend") }
        "all" { return @("backend", "frontend") }
    }
}

function Assert-Command {
    param([string]$CommandName)

    if (-not (Get-Command $CommandName -ErrorAction SilentlyContinue)) {
        Write-Host "ERROR: $CommandName not found. Please install and start Docker Desktop." -ForegroundColor Red
        exit 1
    }
}

function Wait-ContainerRunning {
    param([string[]]$ContainerNames)

    foreach ($container in $ContainerNames) {
        Write-Host -NoNewline "  Waiting for $container to run..."
        for ($i = 0; $i -lt 30; $i++) {
            $state = docker inspect --format="{{.State.Status}}" $container 2>$null
            if ($state -eq "running") {
                Write-Host " OK" -ForegroundColor Green
                break
            }
            Start-Sleep -Seconds 2
        }

        if ($state -ne "running") {
            Write-Host " not running" -ForegroundColor Yellow
        }
    }
}

function Invoke-DockerCompose {
    param([string[]]$Arguments)

    & docker @Arguments
}

Assert-Command "docker"
docker compose version *> $null

$ComposeFile = Resolve-ComposeFile $Mode
$Services = Resolve-Services $Target
$ContainerNames = $Services | ForEach-Object { "seahorse-$_" }

if (-not (Test-Path $ComposeFile)) {
    Write-Host "ERROR: $ComposeFile not found." -ForegroundColor Red
    exit 1
}

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  Seahorse Agent local redeploy" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "Compose: $ComposeFile"
Write-Host "Target:  $Target ($($Services -join ', '))"
Write-Host ""

if (-not $NoBuild) {
    Write-Host "Building image(s)..." -ForegroundColor Green
    Invoke-DockerCompose (@("compose", "-f", $ComposeFile, "build") + $Services)
    Write-Host ""
}

Write-Host "Recreating container(s)..." -ForegroundColor Green
Invoke-DockerCompose (@("compose", "-f", $ComposeFile, "up", "-d", "--no-deps", "--force-recreate") + $Services)

Write-Host ""
Wait-ContainerRunning $ContainerNames

Write-Host ""
Write-Host "Current status:" -ForegroundColor Green
Invoke-DockerCompose (@("compose", "-f", $ComposeFile, "ps") + $Services)

if ($Logs) {
    Write-Host ""
    Write-Host "Recent logs:" -ForegroundColor Green
    Invoke-DockerCompose (@("compose", "-f", $ComposeFile, "logs", "--tail=80") + $Services)
}

Write-Host ""
Write-Host "Done." -ForegroundColor Green
Write-Host "Frontend: http://localhost"
Write-Host "Backend:  http://localhost:9090"
