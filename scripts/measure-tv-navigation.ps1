param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [Parameter(Mandatory = $true)][ValidateSet('Home', 'SettingsSidebar', 'SettingsPlayback', 'SettingsCatalogs')][string]$Scenario,
    [string]$Adb = 'adb',
    [string]$OutputDirectory = 'artifacts/navigation',
    [int]$PauseMs = 250
)

$ErrorActionPreference = 'Stop'
function Invoke-Adb {
    $result = & $Adb -s $Serial @args
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $args" }
    return $result
}

# Position focus before running: Home card, Accounts sidebar, or first content row.
# This driver uses direction keys only and does not change preferences.
$window = (Invoke-Adb shell dumpsys window) -join "`n"
if ($window -notmatch 'mCurrentFocus=.*com\.arvio\.tv') {
    throw 'ARVIO must be foreground before measuring.'
}
$keys = switch ($Scenario) {
    'Home' { (@(22,22,22,22,22,20,21,21,21,21,20) * 6) + (@(19) * 12) }
    'SettingsSidebar' { ((@(20) * 10) + (@(19) * 10)) * 3 }
    'SettingsPlayback' { ((@(20) * 11) + (@(19) * 11)) * 2 }
    'SettingsCatalogs' { ((@(20) * 24) + (@(19) * 24)) * 2 }
}
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$stem = Join-Path $OutputDirectory "$Scenario-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
Invoke-Adb shell dumpsys gfxinfo com.arvio.tv reset | Out-Null
$timer = [Diagnostics.Stopwatch]::StartNew()
foreach ($key in $keys) {
    Invoke-Adb shell input keyevent $key | Out-Null
    Start-Sleep -Milliseconds $PauseMs
}
$timer.Stop()
$raw = (Invoke-Adb shell dumpsys gfxinfo com.arvio.tv framestats) -join "`n"
$raw | Set-Content "$stem.txt"
function Metric([string]$Pattern) {
    $match = [regex]::Match($raw, $Pattern)
    if (!$match.Success) { throw "Missing gfxinfo metric: $Pattern" }
    return $match.Groups[1].Value
}
$summary = [ordered]@{
    scenario = $Scenario
    serial = $Serial
    inputs = $keys.Count
    seconds = [Math]::Round($timer.Elapsed.TotalSeconds, 2)
    frames = [int](Metric 'Total frames rendered: (\d+)')
    jankPercent = [double]::Parse((Metric 'Janky frames: \d+ \(([\d.]+)%\)'), [Globalization.CultureInfo]::InvariantCulture)
    medianMs = [int](Metric '50th percentile: (\d+)ms')
    p95Ms = [int](Metric '95th percentile: (\d+)ms')
    p99Ms = [int](Metric '99th percentile: (\d+)ms')
    rawReport = "$stem.txt"
}
$summary | ConvertTo-Json | Set-Content "$stem.json"
$summary | ConvertTo-Json
