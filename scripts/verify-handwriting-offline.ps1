# Checks on a connected device that handwriting recognition works with no network, and sets the device
# up for everyday use without one. Usage: .\scripts\verify-handwriting-offline.ps1 [-Serial <adb serial>]
#
# 1. Installs the debug build and downloads the en-US and es-ES models, about 40 MB. This is the one
#    download the owner accepted. Skipped if the models are already there.
# 2. Turns on airplane mode and recognizes test words.
# 3. Installs the offline build over it, which has no INTERNET permission, and recognizes them again.
#
# It leaves the offline build installed and airplane mode as it found it. The test words are drawn by
# program, so this shows the pipeline works without a network, not how well it reads real handwriting.

param([string]$Serial = '')

$ErrorActionPreference = 'Stop'

if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'D:\Android\Android Studio\jbr' }
$root = Split-Path -Parent $PSScriptRoot
$adbPath = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$package = 'app.betternotes'
$activity = "$package/app.betternotes.ui.MainActivity"
$tag = 'BetterNotesInk'

function Adb {
    if ($Serial) { & $adbPath -s $Serial @args } else { & $adbPath @args }
}

function Run-Check([string]$extra) {
    Adb shell am force-stop $package
    Adb logcat -c
    Adb shell am start -n $activity --ez $extra true | Out-Null
    $deadline = (Get-Date).AddMinutes(12)
    do {
        Start-Sleep -Seconds 2
        $log = Adb logcat -d -s "${tag}:*" AndroidRuntime:E
        $done = $log | Select-String -Pattern 'RESULT finished|FATAL EXCEPTION'
    } while (-not $done -and (Get-Date) -lt $deadline)
    $log | Where-Object { $_ -notmatch 'beginning of' } | ForEach-Object { Write-Host (($_ -replace "^.*${tag}\s*: ", '')) }
    if (-not $done) { Write-Host 'TIMED OUT waiting for the check to finish.' }
}

Write-Host ('model:   ' + (Adb shell getprop ro.product.model))
Write-Host ('android: ' + (Adb shell getprop ro.build.version.release) + ' (API ' + (Adb shell getprop ro.build.version.sdk) + ')')
$gms = Adb shell pm path com.google.android.gms
Write-Host ('google play services: ' + $(if ($gms) { 'present' } else { 'ABSENT' }))

$airplaneBefore = (Adb shell settings get global airplane_mode_on).Trim()

& (Join-Path $root 'gradlew.bat') -p $root :app:assembleDebug :app:assembleOffline --console=plain -q
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

try {
    Write-Host "`n--- Debug build, network on: downloading the models if they are missing ---"
    Adb shell cmd connectivity airplane-mode disable
    # -r keeps data, and a device left on the offline build can go back to debug the same way.
    Adb install -r -d (Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk') | Out-Null
    Run-Check 'handwriting_download'

    Write-Host "`n--- Debug build, airplane mode ---"
    Adb shell cmd connectivity airplane-mode enable
    Start-Sleep -Seconds 3
    Run-Check 'handwriting_check'

    Write-Host "`n--- Offline build (no INTERNET permission), airplane mode ---"
    Adb install -r -d (Join-Path $root 'app\build\outputs\apk\offline\app-offline.apk') | Out-Null
    $granted = Adb shell dumpsys package $package | Select-String -Pattern 'android.permission.INTERNET'
    Write-Host ('INTERNET permission: ' + $(if ($granted) { 'PRESENT' } else { 'absent' }))
    Run-Check 'handwriting_check'

    Write-Host "`n--- Offline build (no INTERNET permission), network on ---"
    Adb shell cmd connectivity airplane-mode disable
    Start-Sleep -Seconds 3
    Run-Check 'handwriting_check'
} finally {
    if ($airplaneBefore -eq '1') {
        Adb shell cmd connectivity airplane-mode enable
    } else {
        Adb shell cmd connectivity airplane-mode disable
    }
}
