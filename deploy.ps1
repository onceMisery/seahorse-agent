# Seahorse Agent 全量部署脚本 (Windows)
# 用法: .\deploy.ps1

$ErrorActionPreference = "Stop"
$ComposeFile = "docker-compose.full.yml"
$EnvFile = ".env"
$EnvExample = ".env.full.example"

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  Seahorse Agent 全量部署" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# 检查 Docker
try {
    $dockerVersion = docker --version 2>&1
    Write-Host "Docker: $dockerVersion"
} catch {
    Write-Host "错误: 未找到 Docker，请先安装 Docker Desktop" -ForegroundColor Red
    exit 1
}

# 检查 Docker Compose
try {
    $composeVersion = docker compose version 2>&1
    Write-Host "Compose: $composeVersion"
} catch {
    Write-Host "错误: 未找到 Docker Compose，请升级 Docker Desktop" -ForegroundColor Red
    exit 1
}

Write-Host ""

# 检查 .env 文件
if (-not (Test-Path $EnvFile)) {
    Write-Host "未找到 .env 文件，从 $EnvExample 复制..." -ForegroundColor Yellow
    if (-not (Test-Path $EnvExample)) {
        Write-Host "错误: 未找到 $EnvExample" -ForegroundColor Red
        exit 1
    }
    Copy-Item $EnvExample $EnvFile
    Write-Host "已创建 $EnvFile，请编辑填入你的 AI API 配置后重新运行此脚本" -ForegroundColor Green
    Write-Host ""
    Write-Host "  记事本 $EnvFile"
    Write-Host ""
    exit 0
}

# 检查必填项
$envContent = Get-Content $EnvFile -Raw
if ($envContent -match "sk-your-api-key-here") {
    Write-Host "错误: 请先编辑 $EnvFile，填入你的 AI API Key" -ForegroundColor Red
    exit 1
}

Write-Host "配置文件: $EnvFile"
Write-Host ""

# 构建并启动
Write-Host "正在构建并启动所有服务..." -ForegroundColor Cyan
Write-Host "（首次构建约 5-10 分钟，请耐心等待）"
Write-Host ""

docker compose -f $ComposeFile up -d --build

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  服务启动中..." -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# 等待关键服务健康
Write-Host "等待服务健康检查..."
$services = @(
    "seahorse-postgres",
    "seahorse-redis",
    "seahorse-elasticsearch",
    "seahorse-milvus",
    "seahorse-pulsar-broker",
    "seahorse-backend"
)

foreach ($svc in $services) {
    $found = $false
    Write-Host -NoNewline "  等待 $svc ..."
    for ($i = 0; $i -lt 60; $i++) {
        try {
            $status = docker inspect --format='{{.State.Health.Status}}' $svc 2>$null
            if ($status -eq "healthy") {
                Write-Host " OK" -ForegroundColor Green
                $found = $true
                break
            }
        } catch {}
        Start-Sleep -Seconds 5
    }
    if (-not $found) {
        Write-Host " 超时" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  部署完成！" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  前端:          http://localhost"
Write-Host "  后端 API:      http://localhost:9090"
Write-Host "  MinIO 控制台:  http://localhost:9001"
Write-Host "  Milvus Attu:   http://localhost:8000"
Write-Host "  Pulsar 管理台: http://localhost:8080"
Write-Host "  Elasticsearch:  http://localhost:9200"
Write-Host ""
Write-Host "  默认账号: admin / admin"
Write-Host ""
Write-Host "  管理命令:" -ForegroundColor Yellow
Write-Host "    查看状态:  docker compose -f $ComposeFile ps"
Write-Host "    查看日志:  docker compose -f $ComposeFile logs -f backend"
Write-Host "    停止服务:  docker compose -f $ComposeFile down"
Write-Host "    清理数据:  docker compose -f $ComposeFile down -v"
Write-Host ""
