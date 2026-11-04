# Measures the app on a connected device and prints what the device reports about itself.
# Usage: .\scripts\measure-device.ps1 [-Serial <adb serial>] [-Strokes 3000] [-HandSeconds 0]
#
# The scripted benchmark needs nobody at the device. -HandSeconds N also opens the scratch note for N
# seconds, for someone to write with the stylus and then with a finger.

param(
    [string]$Serial = '',
    [int]$Strokes = 3000,
    [int]$HandSeconds = 0
)

$ErrorActionPreference = 'Stop'

if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'D:\Android\Android Studio\jbr' }
$root = Split-Path -Parent $PSScriptRoot
$adbPath = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$package = 'app.betternotes.profile'
$activity = "$package/app.betternotes.ui.MainActivity"

function Adb {
    if ($Serial) { & $adbPath -s $Serial @args } else { & $adbPath @args }
}

Write-Host '--- Device ---'
Write-Host ('model:   ' + (Adb shell getprop ro.product.model))
Write-Host ('android: ' + (Adb shell getprop ro.build.version.release) + ' (API ' + (Adb shell getprop ro.build.version.sdk) + ')')
Write-Host ('hyperos: ' + (Adb shell getprop ro.mi.os.version.incremental))
Write-Host ('size:    ' + (Adb shell wm size))
Write-Host ('density: ' + (Adb shell wm density))
Adb shell dumpsys display | Select-String -Pattern 'mActiveModeId|supportedModes|xDpi|mBaseDisplayInfo' | Select-Object -First 6 |
    ForEach-Object { Write-Host $_.Line.Trim().Substring(0, [Math]::Min(300, $_.Line.Trim().Length)) }

$api = [int](Adb shell getprop ro.build.version.sdk)
if ($api -lt 35) {
    Write-Host "This device has API $api, and the app needs 35 (Android 15). Stopping before the install fails."
    exit 1
}

& (Join-Path $root 'gradlew.bat') -p $root :app:assembleProfile --console=plain -q
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Adb install -r (Join-Path $root 'app\build\outputs\apk\profile\app-profile.apk') | Out-Null

Write-Host "`n--- Scripted benchmark, $Strokes strokes on a page ---"
Adb shell am force-stop $package
Adb logcat -c
Adb shell am start -n $activity --ei benchmark_strokes $Strokes | Out-Null
do {
    Start-Sleep -Seconds 3
    $done = Adb logcat -d -s BetterNotesPerf:I AndroidRuntime:E | Select-String -Pattern 'benchmark finished|FATAL EXCEPTION'
} while (-not $done)
Adb logcat -d -s BetterNotesPerf:I AndroidRuntime:E | Where-Object { $_ -notmatch 'beginning of' } |
    ForEach-Object { ($_ -replace '^.*BetterNotesPerf: ', '') }

if ($HandSeconds -gt 0) {
    Write-Host "`n--- Write on the device for $HandSeconds seconds ---"
    Adb logcat -c
    Adb shell am start -n $activity | Out-Null
    Start-Sleep -Seconds $HandSeconds
    Adb logcat -d -s BetterNotesPerf:I AndroidRuntime:E | Where-Object { $_ -notmatch 'beginning of' } |
        ForEach-Object { ($_ -replace '^.*BetterNotesPerf: ', '') }
}

Write-Host "`n--- What the display did ---"
Adb shell dumpsys SurfaceFlinger | Select-String -Pattern 'refresh-rate|VsyncConfiguration|Active mode|activeMode' | Select-Object -First 6 |
    ForEach-Object { Write-Host $_.Line.Trim() }
