# Checks that the handwriting models bundled in the app (app/src/main/assets/ink-models/files.txt) still match
# what the ML Kit library in use lists. ML Kit cannot be asked for newer models at run time: its list of model
# files, addresses and checksums is inside the library, so models only change with a new ML Kit version.
# Usage: .\scripts\check-ink-models.ps1      Exit code 0 = up to date, 1 = needs a refresh. Needs no device.
# After a Gradle sync the library is in the Gradle cache. Refreshing steps: docs/handwriting-models.md.

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$root = Split-Path -Parent $PSScriptRoot
$list = Get-Content (Join-Path $root 'app\src\main\assets\ink-models\files.txt')
$bundledVersion = ($list | Where-Object { $_ -match '^# mlkit-version: (\S+)' } | ForEach-Object { $Matches[1] })
$libVersion = (Select-String -Path (Join-Path $root 'gradle\libs.versions.toml') -Pattern '^mlkitInk\s*=\s*"([^"]+)"').Matches[0].Groups[1].Value
$problems = @()

if ($bundledVersion -ne $libVersion) {
    $problems += "ML Kit is $libVersion but the bundled models and saved state were captured with $bundledVersion."
}

$gradleHome = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
$aar = Get-ChildItem (Join-Path $gradleHome 'caches\modules-2\files-2.1\com.google.mlkit\digital-ink-recognition') -Recurse -Filter "digital-ink-recognition-$libVersion.aar" -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $aar) { Write-Host "ML Kit $libVersion is not in the Gradle cache. Run a build first."; exit 2 }

$zip = [IO.Compression.ZipFile]::OpenRead($aar.FullName)
try {
    $reader = New-Object IO.StreamReader($zip.GetEntry('assets/manifest.json').Open())
    $packs = ($reader.ReadToEnd() | ConvertFrom-Json).packs
    $reader.Close()
} finally { $zip.Dispose() }

# A model "stem" is its file name without the date, so a newer date of the same model shows up.
function Stem([string]$name) { $name -replace '\.?\d{8}\.?', '.' -replace '\.\.', '.' }

foreach ($line in $list | Where-Object { $_ -and -not $_.StartsWith('#') }) {
    $f = $line.Split('|'); $zipName = $f[1]; $sha1 = $f[3]
    $url = "https://dl.google.com/handwriting/models/$zipName"
    $pack = $packs | Where-Object { $_.download_urls[0] -eq $url }
    if ($pack) {
        if ($pack.sha1_checksum -ne $sha1) { $problems += "$zipName is listed by ML Kit with a different checksum." }
    } else {
        $newer = $packs | Where-Object { (Stem ($_.download_urls[0].Split('/')[-1])) -eq (Stem $zipName) }
        $problems += "$zipName is no longer listed by ML Kit." + $(if ($newer) { " Newer: $($newer.download_urls[0].Split('/')[-1])" } else { '' })
    }
}

if ($problems) {
    $problems | ForEach-Object { Write-Host "OUT OF DATE: $_" }
    Write-Host 'Refresh as described in docs/handwriting-models.md ("When ML Kit is updated").'
    exit 1
}
Write-Host "Up to date: the 5 bundled model files match the ML Kit $libVersion list."
