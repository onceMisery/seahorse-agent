param(
    [string]$SmokeScript = "scripts/e2e-backend-smoke.ps1"
)

$ErrorActionPreference = "Stop"

function Import-SmokeFunction {
    param(
        [string]$ScriptPath,
        [string[]]$FunctionNames
    )

    $tokens = $null
    $parseErrors = $null
    $ast = [System.Management.Automation.Language.Parser]::ParseFile(
        (Resolve-Path -LiteralPath $ScriptPath),
        [ref]$tokens,
        [ref]$parseErrors)
    if ($parseErrors -and $parseErrors.Count -gt 0) {
        throw "PowerShell parse errors in ${ScriptPath}: $($parseErrors[0].Message)"
    }

    foreach ($name in $FunctionNames) {
        $functionAst = $ast.Find({
                param($node)
                $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and
                $node.Name -eq $name
            }, $true)
        if ($null -eq $functionAst) {
            throw "Function not found in ${ScriptPath}: $name"
        }
        Invoke-Expression "function global:$name $($functionAst.Body.Extent.Text)"
    }
}

function Assert-Throws {
    param(
        [scriptblock]$ScriptBlock,
        [string]$Name
    )

    try {
        & $ScriptBlock
    } catch {
        return
    }
    throw "$Name did not throw"
}

$scriptPath = Join-Path (Get-Location) $SmokeScript
Import-SmokeFunction -ScriptPath $scriptPath -FunctionNames @(
    "Assert-Code",
    "Assert-NonEmptyDataArray",
    "Get-RagTraceHitText",
    "Assert-RagTraceNodesContainHit"
)

$metadataOnlyTrace = [PSCustomObject]@{
    code = "0"
    data = @(
        [PSCustomObject]@{
            nodeType = "RETRIEVAL_CHANNEL"
            nodeName = "search-channel:VectorGlobalSearch"
            extraData = (@{
                    hitCount = 1
                    metadata = @{ embeddingModel = "nomic-embed-text" }
                    hits = @(
                        @{ textPreview = "unrelated content"; docId = "doc-1" }
                    )
                } | ConvertTo-Json -Depth 20 -Compress)
        }
    )
}

Assert-Throws -Name "metadata-only RAG hit evidence" -ScriptBlock {
    Assert-RagTraceNodesContainHit -Response $metadataOnlyTrace -ExpectedText "nomic-embed-text"
}

$hitTextTrace = [PSCustomObject]@{
    code = "0"
    data = @(
        [PSCustomObject]@{
            nodeType = "RETRIEVAL_CHANNEL"
            nodeName = "search-channel:VectorGlobalSearch"
            extraData = (@{
                    hitCount = 1
                    metadata = @{ embeddingModel = "nomic-embed-text" }
                    hits = @(
                        @{ textPreview = "smoke uses nomic-embed-text with 768 dimensions"; docId = "doc-1" }
                    )
                } | ConvertTo-Json -Depth 20 -Compress)
        }
    )
}

Assert-RagTraceNodesContainHit -Response $hitTextTrace -ExpectedText "nomic-embed-text"

$smokeContent = Get-Content -LiteralPath $scriptPath -Raw
if ($smokeContent.Contains("Assert-TextContains") -or $smokeContent.Contains("Convert-SseResponseText")) {
    throw "Smoke script must rely on trace hit evidence instead of LLM response text assertions"
}

Write-Host "Smoke contract behavior tests passed"
