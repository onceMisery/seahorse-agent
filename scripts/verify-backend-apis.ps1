# Seahorse Agent 后端 API 全面验证脚本
# 用法: .\scripts\verify-backend-apis.ps1
# 前提: Docker 运行中, 后端容器已启动 (http://localhost:9090)

param(
    [string]$BaseUrl = "http://localhost:9090",
    [switch]$Verbose
)

$ErrorActionPreference = "Continue"
$global:passed = 0
$global:failed = 0
$global:skipped = 0
$global:results = @()

function Test-Api {
    param(
        [string]$Name,
        [string]$Method = "GET",
        [string]$Url,
        [string]$Body = $null,
        [string]$ContentType = "application/json",
        [hashtable]$Headers = @{},
        [string]$ExpectCode = "0",
        [int]$ExpectStatus = 200,
        [string]$Group = ""
    )
    $fullUrl = "$BaseUrl$Url"
    try {
        $params = @{
            Uri = $fullUrl
            Method = $Method
            Headers = $Headers
            ContentType = $ContentType
            ErrorAction = "Stop"
        }
        if ($Body) { $params.Body = $Body }
        $response = Invoke-RestMethod @params
        $code = $response.code
        $hasData = $null -ne $response.data
        if ($code -eq $ExpectCode) {
            $global:passed++
            $status = "PASS"
            Write-Host "  [PASS] $Name (code=$code, hasData=$hasData)" -ForegroundColor Green
        } else {
            $global:failed++
            $status = "FAIL"
            $msg = $response.message
            Write-Host "  [FAIL] $Name - code=$code, expected=$ExpectCode, message=$msg" -ForegroundColor Red
        }
        $global:results += [PSCustomObject]@{ Group=$Group; Name=$Name; Status=$status; Code=$code; HasData=$hasData }
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        if ($statusCode -eq $ExpectStatus -and $ExpectStatus -ne 200) {
            $global:passed++
            Write-Host "  [PASS] $Name (expected error $statusCode)" -ForegroundColor Green
            $global:results += [PSCustomObject]@{ Group=$Group; Name=$Name; Status="PASS"; Code=$statusCode; HasData=$false }
        } else {
            $global:failed++
            Write-Host "  [FAIL] $Name - HTTP $statusCode: $($_.Exception.Message)" -ForegroundColor Red
            $global:results += [PSCustomObject]@{ Group=$Group; Name=$Name; Status="FAIL"; Code=$statusCode; HasData=$false }
        }
    }
}

# ═══════════════════════════════════════════════════
# 1. 认证接口
# ═══════════════════════════════════════════════════
Write-Host "`n═══════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  1. 认证接口 (Auth/User)" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════" -ForegroundColor Cyan

# 1.1 登录
$loginBody = '{"username":"admin","password":"admin"}'
try {
    $loginResp = Invoke-RestMethod -Uri "$BaseUrl/auth/login" -Method POST -ContentType "application/json" -Body $loginBody -ErrorAction Stop
    $token = $loginResp.data.token
    $userId = $loginResp.data.userId
    $role = $loginResp.data.role
    if ($token) {
        $global:passed++
        Write-Host "  [PASS] POST /auth/login (token=$($token.Substring(0,8))..., role=$role)" -ForegroundColor Green
        $global:results += [PSCustomObject]@{ Group="Auth"; Name="POST /auth/login"; Status="PASS"; Code="0"; HasData=$true }
    } else {
        $global:failed++
        Write-Host "  [FAIL] POST /auth/login - no token returned" -ForegroundColor Red
    }
} catch {
    $global:failed++
    Write-Host "  [FAIL] POST /auth/login - $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "  无法继续测试，退出。" -ForegroundColor Yellow
    exit 1
}

$authHeaders = @{ Authorization = $token }

# 1.2 错误登录
Test-Api -Name "POST /auth/login (wrong password)" -Method POST -Url "/auth/login" `
    -Body '{"username":"admin","password":"wrong"}' -ExpectStatus 500 -Group "Auth"

# 1.3 登出
Test-Api -Name "POST /auth/logout" -Method POST -Url "/auth/logout" -Headers $authHeaders -Group "Auth"

# 1.4 当前用户
Test-Api -Name "GET /user/me" -Url "/user/me" -Headers $authHeaders -Group "Auth"

# 1.5 未认证访问
Test-Api -Name "GET /user/me (no auth)" -Url "/user/me" -ExpectStatus 401 -Group "Auth"

# ═══════════════════════════════════════════════════
# 2. 用户管理接口
# ═══════════════════════════════════════════════════
Write-Host "`n═══════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  2. 用户管理接口 (User CRUD)" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════" -ForegroundColor Cyan

Test-Api -Name "GET /users?page" -Url "/users?current=1&size=10" -Headers $authHeaders -Group "User"
Test-Api -Name "GET /users?keyword" -Url "/users?current=1&size=10&keyword=admin" -Headers $authHeaders -Group "User"

# ═══════════════════════════════════════════════════
# 3. 仪表板接口
# ═══════════════════════════════════════════════════
Write-Host "`n═══════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  3. 仪表板接口 (Dashboard)" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════" -ForegroundColor Cyan

Test-Api -Name "GET /admin/dashboard/overview" -Url "/admin/dashboard/overview" -Headers $authHeaders -Group "Dashboard"
Test-Api -Name "GET /admin/dashboard/overview?window=7d" -Url "/admin/dashboard/overview?window=7d" -Headers $authHeaders -Group "Dashboard"
Test-Api -Name "GET /admin/dashboard/performance" -Url "/admin/dashboard/performance" -Headers $authHeaders -Group "Dashboard"
Test-Api -Name "GET /admin/dashboard/trends?metric=conversations" -Url "/admin/dashboard/trends?metric=conversations" -Headers $authHeaders -Group "Dashboard"
Test-Api -Name "GET /admin/dashboard/trends (missing metric)" -Url "/admin/dashboard/trends" -Headers $authHeaders -ExpectStatus 400 -Group "Dashboard"

# ═══════════════════════════════════════════════════
# 4. 系统配置接口
# ═══════════════════════════════════════════════════
Write-Host "`n═══════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  4. 系统配置接口 (RAG Settings / AI Config)" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════" -ForegroundColor Cyan

Test-Api -Name "GET /rag/settings" -Url "/rag/settings" -Headers $authHeaders -Group "RagSettings"
Test-Api -Name "GET /admin/ai-config" -Url "/admin/ai-config" -Headers $authHeaders -Group "AiConfig"
Test-Api -Name "GET /admin/ai-config/nonexistent" -Url "/admin/ai-config/nonexistent" -Headers $authHeaders -Group "AiConfig"

# ═══════════════════════════════════════════════════
# 5. 插件管理接口
# ═══════════════════════════════════════════════════
Write-Host "`n═══════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  5. 插件管理接口 (Plugin)" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════" -ForegroundColor Cyan

Test-Api -Name "GET /agent/plugins/health" -Url "/agent/plugins/health" -Headers $authHeaders -Group "Plugin"
Test-Api -Name "GET /agent/plugins/status" -Url "/agent/plugins/status" -Headers $authHeaders -Group "Plugin"
Test-Api -Name "GET /agent/plugins/registry" -Url "/agent/plugins/registry" -Headers $authHeaders -Group "Plugin"

# ═══════════════════════════════════════════════════
# 6. 知识库接口
# ═══════════════════════════════════════════════════
Write-Host "`n═══════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  6. 知识库接口 (KB / Doc / Chunk)" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════" -ForegroundColor Cyan

Test-Api -Name "GET /knowledge-base?page" -Url "/knowledge-base?current=1&size=10" -Headers $authHeaders -Group "KnowledgeBase"
Test-Api -Name "GET /knowledge-base/chunk-strategies" -Url "/knowledge-base/chunk-strategies" -Headers $authHeaders -Group "KnowledgeBase"

# 创建测试知识库
$kbCreateBody = '{"name":"api-test-kb","embeddingModel":"default","collectionName":"apitestkb"}'
try {
    $kbResp = Invoke-RestMethod -Uri "$BaseUrl/knowledge-base" -Method POST -ContentType "application/json" -Body $kbCreateBody -Headers $authHeaders -ErrorAction Stop
    $testKbId = $kbResp.data
    if ($testKbId) {
        $global:passed++
        Write-Host "  [PASS] POST /knowledge-base (id=$testKbId)" -ForegroundColor Green
        $global:results += [PSCustomObject]@{ Group="KnowledgeBase"; Name="POST /knowledge-base"; Status="PASS"; Code="0"; HasData=$true }
    }
} catch {
    # 可能已存在，尝试查询
    Write-Host "  [WARN] POST /knowledge-base failed, trying to find existing..." -ForegroundColor Yellow
    $kbList = Invoke-RestMethod -Uri "$BaseUrl/knowledge-base?current=1&size=10" -Headers $authHeaders
    $testKbId = ($kbList.data.records | Where-Object { $_.collectionName -eq "apitestkb" } | Select-Object -First 1).id
    if ($testKbId) {
        Write-Host "  [INFO] Found existing KB id=$testKbId" -ForegroundColor Yellow
    }
}

if ($testKbId) {
    Test-Api -Name "GET /knowledge-base/$testKbId" -Url "/knowledge-base/$testKbId" -Headers $authHeaders -Group "KnowledgeBase"
    Test-Api -Name "GET /knowledge-base/$testKbId/docs" -Url "/knowledge-base/$testKbId/docs?current=1&size=10" -Headers $authHeaders -Group "KnowledgeDoc"

    # 上传测试文档
    $tempFile = [System.IO.Path]::GetTempFileName() + ".md"
    Set-Content -Path $tempFile -Value "---`nname: test`ndescription: test doc`n---`n# Test`nTest content"
    try {
        $uploadResult = curl.exe -s -X POST "$BaseUrl/knowledge-base/$testKbId/docs/upload" `
            -H "Authorization: $token" -F "file=@$tempFile" | ConvertFrom-Json
        if ($uploadResult.code -eq "0") {
            $testDocId = $uploadResult.data.id
            $global:passed++
            Write-Host "  [PASS] POST /knowledge-base/$testKbId/docs/upload (docId=$testDocId)" -ForegroundColor Green
            $global:results += [PSCustomObject]@{ Group="KnowledgeDoc"; Name="POST docs/upload"; Status="PASS"; Code="0"; HasData=$true }

            # 查询文档详情
            Test-Api -Name "GET /knowledge-base/docs/$testDocId" -Url "/knowledge-base/$testKbId/docs?current=1&size=10" -Headers $authHeaders -Group "KnowledgeDoc"

            # 查询文档块
            Test-Api -Name "GET /knowledge-base/$testKbId/chunks" -Url "/knowledge-base/$testKbId/chunks?docId=$testDocId&current=1&size=10" -Headers $authHeaders -Group "KnowledgeChunk"
        } else {
            $global:failed++
            Write-Host "  [FAIL] POST docs/upload - $($uploadResult.message)" -ForegroundColor Red
        }
    } catch {
        $global:failed++
        Write-Host "  [FAIL] POST docs/upload - $($_.Exception.Message)" -ForegroundColor Red
    } finally {
        Remove-Item $tempFile -ErrorAction SilentlyContinue
    }
}

# ═══════════════════════════════════════════════════
# 7. Agent 定义接口
# ═══════════════════════════════════════════════════
Write-Host "`n═══════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  7. Agent 定义接口 (Definition / ToolCatalog)" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════" -ForegroundColor Cyan

Test-Api -Name "GET /api/agents?page" -Url "/api/agents?current=1&size=10" -Headers $authHeaders -Group "Agent"
Test-Api -Name "GET /agents?page (alias)" -Url "/agents?current=1&size=10" -Headers $authHeaders -Group "Agent"
Test-Api -Name "GET /api/tools?page" -Url "/api/tools?current=1&size=10" -Headers $authHeaders -Group "ToolCatalog"

# ═══════════════════════════════════════════════════
# 8. 技能管理接口
# ═══════════════════════════════════════════════════
Write-Host "`n═══════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  8. 技能管理接口 (Skill)" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════" -ForegroundColor Cyan

Test-Api -Name "GET /api/skills?page" -Url "/api/skills?current=1&size=10" -Headers $authHeaders -Group "Skill"
Test-Api -Name "GET /api/skills?keyword" -Url "/api/skills?current=1&size=10&keyword=research" -Headers $authHeaders -Group "Skill"

# 创建自定义技能
$skillBody = '{"content":"---\nname: api-test-skill\ndescription: Test skill for API verification\n---\n# Test Skill\n## Method\n1. Test step"}'
try {
    $skillResp = Invoke-RestMethod -Uri "$BaseUrl/api/skills/custom" -Method POST -ContentType "application/json" -Body $skillBody -Headers $authHeaders -ErrorAction Stop
    if ($skillResp.code -eq "0") {
        $global:passed++
        Write-Host "  [PASS] POST /api/skills/custom (name=api-test-skill)" -ForegroundColor Green
        $global:results += [PSCustomObject]@{ Group="Skill"; Name="POST /api/skills/custom"; Status="PASS"; Code="0"; HasData=$true }

        # 查询技能详情
        Test-Api -Name "GET /api/skills/api-test-skill" -Url "/api/skills/api-test-skill" -Headers $authHeaders -Group "Skill"

        # 启用技能
        Test-Api -Name "POST /api/skills/api-test-skill/enable" -Method POST -Url "/api/skills/api-test-skill/enable" -Headers $authHeaders -Group "Skill"

        # 禁用技能
        Test-Api -Name "POST /api/skills/api-test-skill/disable" -Method POST -Url "/api/skills/api-test-skill/disable" -Headers $authHeaders -Group "Skill"

        # 历史版本
        Test-Api -Name "GET /api/skills/custom/api-test-skill/history" -Url "/api/skills/custom/api-test-skill/history" -Headers $authHeaders -Group "Skill"

        # 更新技能
        $updateBody = '{"content":"---\nname: api-test-skill\ndescription: Updated test skill\n---\n# Updated\n## Method\n1. Updated step"}'
        Test-Api -Name "PUT /api/skills/custom/api-test-skill" -Method PUT -Url "/api/skills/custom/api-test-skill" -Body $updateBody -Headers $authHeaders -Group "Skill"

        # 删除技能
        Test-Api -Name "DELETE /api/skills/custom/api-test-skill" -Method DELETE -Url "/api/skills/custom/api-test-skill" -Headers $authHeaders -Group "Skill"
    }
} catch {
    $global:failed++
    Write-Host "  [FAIL] POST /api/skills/custom - $($_.Exception.Message)" -ForegroundColor Red
}

# 测试安装技能
$installBody = '{"content":"---\nname: install-test\ndescription: Install test skill\n---\n# Install Test\n## Method\n1. Install step"}'
Test-Api -Name "POST /api/skills/install" -Method POST -Url "/api/skills/install" -Body $installBody -Headers $authHeaders -Group "Skill"

# ═══════════════════════════════════════════════════
# 9. Feature 接口
# ═══════════════════════════════════════════════════
Write-Host "`n═══════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  9. Feature 能力接口" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════" -ForegroundColor Cyan

Test-Api -Name "GET /api/features" -Url "/api/features" -Headers $authHeaders -Group "Feature"

# ═══════════════════════════════════════════════════
# 汇总报告
# ═══════════════════════════════════════════════════
Write-Host "`n═══════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  验证报告" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  总计:  $($global:passed + $global:failed)" -ForegroundColor White
Write-Host "  通过:  $($global:passed)" -ForegroundColor Green
Write-Host "  失败:  $($global:failed)" -ForegroundColor Red

if ($global:failed -gt 0) {
    Write-Host "`n  失败项:" -ForegroundColor Red
    $global:results | Where-Object { $_.Status -eq "FAIL" } | Format-Table Group, Name, Code -AutoSize
}

Write-Host "`n  按分组统计:" -ForegroundColor White
$global:results | Group-Object Group | ForEach-Object {
    $p = ($_.Group | Where-Object { $_.Status -eq "PASS" }).Count
    $f = ($_.Group | Where-Object { $_.Status -eq "FAIL" }).Count
    $color = if ($f -gt 0) { "Red" } else { "Green" }
    Write-Host "    $($_.Name): $p passed, $f failed" -ForegroundColor $color
}
