param(
    [switch]$SkipBuild,
    [switch]$SkipGameTests
)

$ErrorActionPreference = 'Stop'

if ($SkipBuild -and $SkipGameTests) {
    throw 'Nothing to run: both -SkipBuild and -SkipGameTests were specified.'
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$gradleWrapper = Join-Path $repoRoot 'gradlew.bat'
if (-not (Test-Path -LiteralPath $gradleWrapper -PathType Leaf)) {
    throw "Gradle wrapper was not found: $gradleWrapper"
}

$runId = Get-Date -Format 'yyyyMMdd-HHmmss'
$reportDirectory = Join-Path $repoRoot "build/reports/empires-automation/$runId"
New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null

$results = @()

function Invoke-GradleCheck {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [Parameter(Mandatory = $true)][string]$SuccessPattern
    )

    $logPath = Join-Path $reportDirectory "$Name.log"
    Write-Host ''
    Write-Host "========== $Name ==========" -ForegroundColor Cyan
    Write-Host "Log: $logPath"

    Push-Location $repoRoot
    try {
        & $gradleWrapper @Arguments 2>&1 | Tee-Object -FilePath $logPath
        $gradleExitCode = $LASTEXITCODE
    }
    finally {
        Pop-Location
    }

    $logText = Get-Content -LiteralPath $logPath -Raw
    $hasSuccessMarker = $logText -match $SuccessPattern
    $passed = ($gradleExitCode -eq 0) -and $hasSuccessMarker

    $results += [pscustomobject]@{
        Name = $Name
        Passed = $passed
        ExitCode = $gradleExitCode
        Log = $logPath
    }

    if ($passed) {
        Write-Host "[PASS] $Name" -ForegroundColor Green
    }
    else {
        Write-Host "[FAIL] $Name (Gradle exit=$gradleExitCode, success marker=$hasSuccessMarker)" -ForegroundColor Red
    }

    return $passed
}

if (-not $SkipBuild) {
    $null = Invoke-GradleCheck -Name 'build' -Arguments @('build', '--console=plain') -SuccessPattern 'BUILD SUCCESSFUL'
}

if (-not $SkipGameTests) {
    $null = Invoke-GradleCheck -Name 'gametests' -Arguments @('runGameTestServer', '--console=plain') -SuccessPattern 'All\s+\d+\s+required tests passed'
}

$summaryPath = Join-Path $reportDirectory 'summary.txt'
$summaryLines = @(
    'Minecraft Empires automated checks',
    "Run: $runId",
    "Repository: $repoRoot",
    ''
)

foreach ($result in $results) {
    $status = if ($result.Passed) { 'PASS' } else { 'FAIL' }
    $summaryLines += "$status $($result.Name) (exit=$($result.ExitCode))"
    $summaryLines += "  Log: $($result.Log)"
}

$allPassed = ($results.Count -gt 0) -and (@($results | Where-Object { -not $_.Passed }).Count -eq 0)
$summaryLines += ''
$summaryLines += if ($allPassed) { 'RESULT: PASS' } else { 'RESULT: FAIL' }
Set-Content -LiteralPath $summaryPath -Value $summaryLines -Encoding UTF8

Write-Host ''
Write-Host '========== SUMMARY ==========' -ForegroundColor Cyan
foreach ($result in $results) {
    $status = if ($result.Passed) { 'PASS' } else { 'FAIL' }
    $color = if ($result.Passed) { 'Green' } else { 'Red' }
    Write-Host "[$status] $($result.Name)" -ForegroundColor $color
}
Write-Host "Report: $summaryPath"

if (-not $allPassed) {
    exit 1
}

Write-Host 'All selected checks passed.' -ForegroundColor Green
exit 0
