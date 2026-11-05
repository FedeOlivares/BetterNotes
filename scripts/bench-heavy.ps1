# Runs the heavy benchmark (PDF plus every brush) on a connected device and prints the numbers.
# Usage: .\scripts\bench-heavy.ps1 [-Serial <serial>] [-NoBuild] [-Pdf <path on the PC>] [-NoInk]
# Installs a separate app (app.betternotes.profile). -Pdf copies the file to that app's folder on the device.
param([string]$Serial = 'f9ea56c5', [switch]$NoBuild, [string]$Pdf = '', [switch]$NoInk)
$ErrorActionPreference = 'Stop'
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'D:\Android\Android Studio\jbr' }
$root = Split-Path -Parent $PSScriptRoot
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$package = 'app.betternotes.profile'
if (-not $NoBuild) {
    & (Join-Path $root 'gradlew.bat') -p $root :app:assembleProfile --console=plain -q
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
& $adb -s $Serial install -r (Join-Path $root 'app\build\outputs\apk\profile\app-profile.apk') | Out-Null
& $adb -s $Serial shell am force-stop $package
& $adb -s $Serial logcat -c
$extras = @('--ez', 'benchmark_heavy', 'true')
if ($NoInk) { $extras += @('--ez', 'benchmark_ink', 'false') }
if ($Pdf) {
    $onDevice = "/sdcard/Android/data/$package/files/bench.pdf"
    & $adb -s $Serial shell mkdir -p "/sdcard/Android/data/$package/files"
    & $adb -s $Serial push $Pdf $onDevice | Out-Null
    $extras += @('--es', 'benchmark_pdf', $onDevice)
}
& $adb -s $Serial shell am start -n "$package/app.betternotes.ui.MainActivity" @extras | Out-Null
do {
    Start-Sleep -Seconds 3
    $done = & $adb -s $Serial logcat -d -s BetterNotesPerf:I AndroidRuntime:E | Select-String -Pattern 'benchmark finished|FATAL EXCEPTION'
} while (-not $done)
& $adb -s $Serial logcat -d -s BetterNotesPerf:I AndroidRuntime:E | Where-Object { $_ -notmatch 'beginning of' } |
    ForEach-Object { ($_ -replace '^.*BetterNotesPerf: ', '') }
