# Runs the heavy benchmark (PDF plus every brush) on a connected device and prints the numbers.
# Usage: .\scripts\bench-heavy.ps1 [-Serial <serial>] [-Skip build]
param([string]$Serial = 'f9ea56c5', [switch]$NoBuild)
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
& $adb -s $Serial shell am start -n "$package/app.betternotes.ui.MainActivity" --ez benchmark_heavy true | Out-Null
do {
    Start-Sleep -Seconds 3
    $done = & $adb -s $Serial logcat -d -s BetterNotesPerf:I AndroidRuntime:E | Select-String -Pattern 'benchmark finished|FATAL EXCEPTION'
} while (-not $done)
& $adb -s $Serial logcat -d -s BetterNotesPerf:I AndroidRuntime:E | Where-Object { $_ -notmatch 'beginning of' } |
    ForEach-Object { ($_ -replace '^.*BetterNotesPerf: ', '') }
