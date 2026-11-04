# Runs the same checks a CI job would: unit tests, lint and the debug and release builds.
# Usage: .\scripts\check.ps1 [extra Gradle arguments]

$ErrorActionPreference = 'Stop'

if (-not $env:JAVA_HOME) {
    # java is not on PATH on the dev machine, so fall back to the JDK bundled with Android Studio.
    $env:JAVA_HOME = 'D:\Android\Android Studio\jbr'
}

$root = Split-Path -Parent $PSScriptRoot
& (Join-Path $root 'gradlew.bat') -p $root test lint assembleDebug assembleRelease @args
exit $LASTEXITCODE
