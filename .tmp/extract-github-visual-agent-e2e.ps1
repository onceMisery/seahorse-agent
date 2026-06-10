param(
    [Parameter(Mandatory = $true)]
    [string]$RunDir
)

$ErrorActionPreference = 'Stop'

$rawFile = Join-Path $RunDir 'raw.sse.txt'
if (-not (Test-Path $rawFile)) {
    throw "Raw SSE file not found: $rawFile"
}

function Save-Utf8File {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$Content
    )
    Set-Content -Path $Path -Value $Content -Encoding UTF8
}

function Try-ReadJson {
    param([AllowEmptyString()][string]$Text)
    if ([string]::IsNullOrWhiteSpace($Text)) {
        return $null
    }
    try {
        return $Text | ConvertFrom-Json -ErrorAction Stop
    } catch {
        return $null
    }
}

function Normalize-ArtifactTitle {
    param(
        [AllowEmptyString()][string]$Title,
        [Parameter(Mandatory = $true)][string]$DefaultName
    )
    if ([string]::IsNullOrWhiteSpace($Title)) {
        $safe = $DefaultName
    } else {
        $safe = $Title.Trim()
    }
    $safe = $safe -replace '[\\/:*?"<>|]', '-'
    if (-not ($safe -match '\.[A-Za-z0-9]+$')) {
        return $DefaultName
    }
    return $safe
}

function First-NonBlank {
    param([string[]]$Values)
    foreach ($value in $Values) {
        if (-not [string]::IsNullOrWhiteSpace($value)) {
            return $value
        }
    }
    return ''
}

$lines = Get-Content -Path $rawFile -Encoding UTF8
$response = New-Object System.Text.StringBuilder
$toolEvents = New-Object System.Collections.Generic.List[string]
$toolArtifacts = New-Object System.Collections.Generic.List[object]

foreach ($line in $lines) {
    if (-not $line.StartsWith('data:')) {
        continue
    }

    $jsonText = $line.Substring(5)
    if ([string]::IsNullOrWhiteSpace($jsonText) -or $jsonText.Trim() -eq '[DONE]') {
        continue
    }

    $payload = Try-ReadJson $jsonText
    if ($null -eq $payload) {
        continue
    }

    $typed = $payload.typedPayload
    $eventType = [string]$payload.eventType
    if ($eventType -and $eventType.StartsWith('TOOL_CALL')) {
        $toolId = First-NonBlank @([string]$typed.toolId, [string]$typed.toolName)
        if ($toolId) {
            $toolEvents.Add("$($payload.eventSeq):${eventType}:$toolId")
        }

        if ($eventType -eq 'TOOL_CALL_FINISHED') {
            $toolResultText = if ($typed.summary) { [string]$typed.summary } else { [string]$typed.message }
            if ($toolResultText) {
                $toolResult = Try-ReadJson $toolResultText
                if ($toolResult -and $toolResult.artifactType -and $toolResult.content) {
                    $toolArtifacts.Add([pscustomobject]@{
                        toolId = $toolId
                        artifactType = [string]$toolResult.artifactType
                        format = [string]$toolResult.format
                        content = [string]$toolResult.content
                        eventSeq = $payload.eventSeq
                        toolCallId = [string]$typed.toolCallId
                    })
                }
            }
        }
    }

    if ($payload.type -and $payload.delta) {
        if ([string]$payload.type -eq 'response') {
            [void]$response.Append([string]$payload.delta)
        }
    }
}

$body = $response.ToString()
$bodyPath = Join-Path $RunDir 'final-response-with-artifacts.md'
Save-Utf8File -Path $bodyPath -Content $body

$markdown = $body
$inlineArtifacts = New-Object System.Collections.Generic.List[object]
$artifactPattern = '(?s)<artifact\s+language="([^"]+)"\s+title="([^"]+)">\s*(.*?)\s*</artifact>'
$artifactMatches = [regex]::Matches($body, $artifactPattern)
for ($i = $artifactMatches.Count - 1; $i -ge 0; $i--) {
    $match = $artifactMatches[$i]
    $inlineArtifacts.Add([pscustomobject]@{
        language = $match.Groups[1].Value
        title = $match.Groups[2].Value
        content = $match.Groups[3].Value
        index = $match.Index
        length = $match.Length
    })
    $markdown = $markdown.Remove($match.Index, $match.Length).Trim()
}

$mdPath = Join-Path $RunDir 'final-document.md'
Save-Utf8File -Path $mdPath -Content $markdown

$htmlArtifact = @($inlineArtifacts | Where-Object {
    $_.language -eq 'html' -or $_.title -match '\.html?$'
} | Sort-Object index | Select-Object -Last 1)
$htmlPath = Join-Path $RunDir 'project-intro-web-preview.html'
if ($htmlArtifact) {
    Save-Utf8File -Path $htmlPath -Content $htmlArtifact.content
}

$newsletter = @($toolArtifacts | Where-Object {
    $_.toolId -eq 'newsletter_generation' -or $_.artifactType -eq 'newsletter'
} | Sort-Object eventSeq | Select-Object -Last 1)
$presentation = @($toolArtifacts | Where-Object {
    $_.toolId -eq 'ppt_generation' -or $_.artifactType -eq 'presentation'
} | Sort-Object eventSeq | Select-Object -Last 1)
$frontend = @($toolArtifacts | Where-Object {
    $_.toolId -eq 'frontend_design' -or $_.artifactType -eq 'frontend_design'
} | Sort-Object eventSeq | Select-Object -Last 1)

$newsletterPath = Join-Path $RunDir 'newsletter.md'
if ($newsletter) {
    Save-Utf8File -Path $newsletterPath -Content $newsletter.content
}

$presentationPath = Join-Path $RunDir 'presentation.md'
if ($presentation) {
    Save-Utf8File -Path $presentationPath -Content $presentation.content
}

$frontendToolPath = Join-Path $RunDir 'frontend-design-tool-output.html'
if ($frontend) {
    Save-Utf8File -Path $frontendToolPath -Content $frontend.content
}

$allImageRefs = New-Object System.Collections.Generic.List[string]
foreach ($content in @($body, $newsletter.content, $presentation.content, $frontend.content, $htmlArtifact.content)) {
    if ([string]::IsNullOrWhiteSpace($content)) {
        continue
    }
    foreach ($match in [regex]::Matches($content, '!\[[^\]]*\]\(([^)]+)\)')) {
        $allImageRefs.Add($match.Groups[1].Value)
    }
    foreach ($match in [regex]::Matches($content, '<img[^>]+src=["'']([^"'']+)["'']', 'IgnoreCase')) {
        $allImageRefs.Add($match.Groups[1].Value)
    }
}
$badImageRefs = @($allImageRefs | Where-Object {
    $_ -and -not ($_.StartsWith('https://') -or $_.StartsWith('http://') -or $_.StartsWith('data:image/'))
})
$internalImageRefs = @($allImageRefs | Where-Object {
    $_ -match 'localhost|127\.0\.0\.1|file://|minio|assets/'
})

$section21 = ''
$section21Match = [regex]::Match($markdown, '(?s)2\.1[^\n]*\n(.*?)(?=\n#{1,4}\s*(2\.2|3\.|III|Three)|\n##|\z)')
if ($section21Match.Success) {
    $section21 = $section21Match.Groups[1].Value
}
$section21HasMermaid = $section21 -match '```mermaid\s+flowchart'
$section21HasAsciiBox = $section21 -match '[\u2500-\u257F]'

$chapter9 = ''
$chapter9Start = [regex]::Match($markdown, '(?m)^\s*#+\s*#?\s*(九|9\.?|IX)[^\n]*(生成|稿件|版式|产物|artifact|Artifact)')
if ($chapter9Start.Success) {
    $remaining = $markdown.Substring($chapter9Start.Index)
    $chapter9End = [regex]::Match($remaining.Substring([Math]::Min($remaining.Length, 1)), '(?m)^\s*#+\s*#?\s*(十|10\.?|X)[^\n]*')
    if ($chapter9End.Success) {
        $chapter9 = $remaining.Substring(0, $chapter9End.Index + 1)
    } else {
        $chapter9 = $remaining
    }
} else {
    $idx = $markdown.IndexOf('newsletter_generation')
    if ($idx -lt 0) { $idx = $markdown.IndexOf('ppt_generation') }
    if ($idx -lt 0) { $idx = $markdown.IndexOf('frontend_design') }
    if ($idx -ge 0) {
        $start = [Math]::Max(0, $idx - 1200)
        $len = [Math]::Min($markdown.Length - $start, 5000)
        $chapter9 = $markdown.Substring($start, $len)
    }
}
$chapter9HasNewsletter = $chapter9 -match '9\.1|长文稿件摘要|newsletter_generation|长文.*Markdown'
$chapter9HasPpt = $chapter9 -match '9\.2|演示文稿摘要|ppt_generation|幻灯片|演示文稿'
$chapter9HasWebLayout = $chapter9 -match '9\.3|Web\s*版式预览摘要|frontend_design|HTML/CSS|版式'

$html = if ($htmlArtifact) { $htmlArtifact.content } else { $null }
$htmlCoversWholeDoc = $false
if ($html) {
    $htmlHeadingCount = [regex]::Matches($html, '<h2\b', 'IgnoreCase').Count
    $htmlCoversWholeDoc = (
        ($html -match 'Redis') -and
        ($htmlHeadingCount -ge 9) -and
        ($html -match '<img\b') -and
        ($html -match 'summary-box|newsletter_generation|生成稿件|版式产物') -and
        ($html -match '<table\b|README|src/')
    )
}
$htmlPreservesCssHashes = -not $html -or ($html -notmatch '#\s+[0-9A-Fa-f]{3,8}\b')

$requiredTools = @(
    'github_repository_reader',
    'web_fetch',
    'chart_visualization',
    'image_generation',
    'newsletter_generation',
    'ppt_generation',
    'frontend_design'
)
$calledTools = @($toolEvents | ForEach-Object {
    ($_ -split ':')[-1]
} | Sort-Object -Unique)
$finishedTools = @($toolEvents | Where-Object {
    $_ -match ':TOOL_CALL_FINISHED:'
} | ForEach-Object {
    ($_ -split ':')[-1]
} | Sort-Object -Unique)
$missingTools = @($requiredTools | Where-Object { $calledTools -notcontains $_ })
$missingFinishedTools = @($requiredTools | Where-Object { $finishedTools -notcontains $_ })

$newsletterContentLooksReal = $newsletter -and $newsletter.content.Length -gt 200 -and $newsletter.content -match '#|Redis|项目|架构'
$presentationContentLooksReal = $presentation -and $presentation.content.Length -gt 200 -and $presentation.content -match 'slide|Slide|幻灯片|演示|讲稿|Speaker|要点'
$frontendContentLooksReal = $frontend -and $frontend.content.Length -gt 200 -and $frontend.content -match '<[a-zA-Z][^>]*>|class=|style='

$manifestPath = Join-Path $RunDir 'artifact-manifest.json'
$manifest = [ordered]@{
    finalMarkdown = $mdPath
    wholeDocumentHtml = if ($htmlArtifact) { $htmlPath } else { $null }
    newsletterMarkdown = if ($newsletter) { $newsletterPath } else { $null }
    presentationMarkdown = if ($presentation) { $presentationPath } else { $null }
    frontendDesignHtml = if ($frontend) { $frontendToolPath } else { $null }
    source = 'tool_call_finished events plus final response artifacts'
}
$manifestJson = $manifest | ConvertTo-Json -Depth 4
Save-Utf8File -Path $manifestPath -Content $manifestJson

$checks = [ordered]@{
    rawSseExists = (Test-Path $rawFile)
    finalMarkdownExists = (Test-Path $mdPath)
    htmlArtifactExists = [bool]$htmlArtifact
    htmlArtifactCoversWholeDocument = [bool]$htmlCoversWholeDoc
    htmlArtifactPreservesCssHashes = [bool]$htmlPreservesCssHashes
    newsletterArtifactExists = (Test-Path $newsletterPath)
    newsletterArtifactHasRealContent = [bool]$newsletterContentLooksReal
    presentationArtifactExists = (Test-Path $presentationPath)
    presentationArtifactHasRealContent = [bool]$presentationContentLooksReal
    frontendDesignArtifactExists = (Test-Path $frontendToolPath)
    frontendDesignArtifactHasRealContent = [bool]$frontendContentLooksReal
    artifactManifestExists = (Test-Path $manifestPath)
    section21HasMermaidFlowchart = [bool]$section21HasMermaid
    section21HasNoAsciiBoxDiagram = -not [bool]$section21HasAsciiBox
    imageReferencesAreWebSafe = ($badImageRefs.Count -eq 0 -and $internalImageRefs.Count -eq 0 -and $allImageRefs.Count -gt 0)
    chapter9HasNewsletterSummary = [bool]$chapter9HasNewsletter
    chapter9HasPresentationSummary = [bool]$chapter9HasPpt
    chapter9HasWebLayoutSummary = [bool]$chapter9HasWebLayout
    requiredToolsWereCalled = ($missingTools.Count -eq 0)
    requiredToolsFinished = ($missingFinishedTools.Count -eq 0)
}

$reportLines = New-Object System.Collections.Generic.List[string]
$reportLines.Add('# GitHub Visual Agent E2E Check Report')
$reportLines.Add('')
$reportLines.Add("Generated at: $((Get-Date).ToString('yyyy-MM-dd HH:mm:ss'))")
$reportLines.Add("Raw SSE: $rawFile")
$reportLines.Add("Final Markdown: $mdPath")
$reportLines.Add("Artifact manifest: $manifestPath")
$reportLines.Add("Newsletter artifact: $(if ($newsletter) { $newsletterPath } else { 'not generated' })")
$reportLines.Add("Presentation artifact: $(if ($presentation) { $presentationPath } else { 'not generated' })")
$reportLines.Add("Frontend design artifact: $(if ($frontend) { $frontendToolPath } else { 'not generated' })")
$reportLines.Add("Whole-document HTML preview: $(if ($htmlArtifact) { $htmlPath } else { 'not generated' })")
$reportLines.Add('')
$reportLines.Add('## Checks')
foreach ($key in $checks.Keys) {
    $status = if ($checks[$key]) { 'PASS' } else { 'FAIL' }
    $reportLines.Add("- $status - $key")
}
$reportLines.Add('')
$reportLines.Add('## Tool Calls')
$reportLines.Add("Called tools: $($calledTools -join ', ')")
$reportLines.Add("Finished tools: $($finishedTools -join ', ')")
if ($missingTools.Count -gt 0) {
    $reportLines.Add("Missing called tools: $($missingTools -join ', ')")
} else {
    $reportLines.Add('Missing called tools: none')
}
if ($missingFinishedTools.Count -gt 0) {
    $reportLines.Add("Missing finished tools: $($missingFinishedTools -join ', ')")
} else {
    $reportLines.Add('Missing finished tools: none')
}
$reportLines.Add('')
$reportLines.Add('## Extracted Artifacts')
foreach ($artifact in $toolArtifacts) {
    $reportLines.Add("- $($artifact.toolId): $($artifact.artifactType) / $($artifact.format) / $($artifact.content.Length) chars")
}
$reportLines.Add('')
$reportLines.Add('## Image References')
if ($allImageRefs.Count -eq 0) {
    $reportLines.Add('No image references found.')
} else {
    foreach ($ref in ($allImageRefs | Sort-Object -Unique)) {
        $reportLines.Add("- $ref")
    }
}
if ($badImageRefs.Count -gt 0 -or $internalImageRefs.Count -gt 0) {
    $reportLines.Add('')
    $reportLines.Add('Invalid image references:')
    foreach ($ref in (($badImageRefs + $internalImageRefs) | Sort-Object -Unique)) {
        $reportLines.Add("- $ref")
    }
}
$reportLines.Add('')
$reportLines.Add('## Result')
$allPassed = $true
foreach ($key in $checks.Keys) {
    if (-not $checks[$key]) {
        $allPassed = $false
    }
}
if ($allPassed) {
    $reportLines.Add('This E2E output satisfies the strict artifact-based acceptance requirements.')
} else {
    $reportLines.Add('This E2E output still has failing acceptance items. See FAIL rows above.')
}

$reportPath = Join-Path $RunDir 'e2e-check-report.md'
Save-Utf8File -Path $reportPath -Content ($reportLines -join [Environment]::NewLine)

$summary = [ordered]@{
    runDir = $RunDir
    rawFile = $rawFile
    markdownFile = $mdPath
    htmlFile = if ($htmlArtifact) { $htmlPath } else { $null }
    newsletterFile = if ($newsletter) { $newsletterPath } else { $null }
    presentationFile = if ($presentation) { $presentationPath } else { $null }
    frontendDesignFile = if ($frontend) { $frontendToolPath } else { $null }
    manifestFile = $manifestPath
    reportFile = $reportPath
    checks = $checks
    calledTools = $calledTools
    finishedTools = $finishedTools
    missingTools = $missingTools
    missingFinishedTools = $missingFinishedTools
    imageRefs = @($allImageRefs | Sort-Object -Unique)
}
$summaryJson = $summary | ConvertTo-Json -Depth 6
Save-Utf8File -Path (Join-Path $RunDir 'e2e-summary.json') -Content $summaryJson

$summaryJson
