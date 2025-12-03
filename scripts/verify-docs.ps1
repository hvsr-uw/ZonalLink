param(
    [string]$Root = (Resolve-Path "$PSScriptRoot\..").Path
)

$ErrorActionPreference = "Stop"
$failed = $false

$requiredDocs = @(
    "README.md",
    "docs/USE_CASES.md",
    "docs/HOW_IT_WORKS.md",
    "docs/ARCHITECTURE_OVERVIEW.md",
    "docs/DATA_FLOW.md",
    "docs/TESTING.md",
    "docs/REVIEWER_GUIDE.md",
    "docs/EXTENSION_POINTS.md"
)

foreach ($relative in $requiredDocs) {
    if (-not (Test-Path -LiteralPath (Join-Path $Root $relative))) {
        Write-Error "Missing required doc: $relative"
        $failed = $true
    }
}

$scanFiles = @(
    Get-Item -LiteralPath (Join-Path $Root "README.md")
    Get-ChildItem -LiteralPath (Join-Path $Root "docs") -Filter "*.md"
)

$forbidden = "TODO|FIXME|PLACEHOLDER|PASTE DESCRIPTION|lorem|console\.log|println\("
foreach ($file in $scanFiles) {
    $matches = Select-String -LiteralPath $file.FullName -Pattern $forbidden -AllMatches
    if ($matches) {
        $matches | ForEach-Object { Write-Error "Forbidden text in $($_.Path):$($_.LineNumber): $($_.Line)" }
        $failed = $true
    }
}

$linkPattern = '\[[^\]]+\]\(([^)#][^)]+)\)'
foreach ($file in $scanFiles) {
    $content = Get-Content -LiteralPath $file.FullName -Raw
    foreach ($match in [regex]::Matches($content, $linkPattern)) {
        $target = $match.Groups[1].Value
        if ($target -match '^[a-z]+:') {
            continue
        }
        $targetPath = $target -replace '#.*$', ''
        $resolved = Join-Path (Split-Path -Parent $file.FullName) $targetPath
        if (-not (Test-Path -LiteralPath $resolved)) {
            Write-Error "Broken link in $($file.FullName): $target"
            $failed = $true
        }
    }
}

if ($failed) {
    exit 1
}

Write-Host "Documentation checks passed."
