# Dev environment (Windows 11)

- Android Studio: `D:\Android\Android Studio`, build 262.10968.63 (the owner reports Rabbit 2).
- Bundled JDK: OpenJDK 25.0.4 (`D:\Android\Android Studio\jbr`). `java` is not on PATH.
- SDK: `%LOCALAPPDATA%\Android\Sdk`. Installed (checked 2026-11-03): platform `android-37.0`, build-tools 36.0.0, platform-tools. No `cmdline-tools` and no NDK.
- `compileSdk` and `targetSdk` are 37. `minSdk` is 35.
- `local.properties` is git-ignored and holds the SDK path.
- Android docs list Rabbit 2 as supporting AGP up to 9.5; AGP 9.5 needs Gradle 9.6+.

## Toolchain in use
Gradle 9.6.0 with a pinned checksum, AGP 9.5.0, Kotlin 2.4.21, JDK 25 (JBR) targeting Java 17.

## Latest stable versions found
- AGP 9.5.0
- Kotlin 2.4.21
- Compose BOM 2026.09.00
- graphics-core 1.0.4
- input-motionprediction 1.0.0
- androidx.ink 1.0.0 (newest alpha: 1.1.0-alpha10, which adds a partial stroke eraser and the public custom brush API)
- ML Kit digital-ink-recognition 19.0.0
