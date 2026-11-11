# Handwriting models in the APK

ML Kit Digital Ink has no API for bundled models: `DigitalInkRecognitionModel` is always fetched by `RemoteModelManager`.
So the app restores the files into ML Kit's own storage before ML Kit's first use. Measured on the Pad 7 (2026-11-10,
debug build, fresh `pm clear`): `missing: []`, recognition reads the test words, and 0 bytes sent or received.

## What ships
- `ink-models/*.zip` in the APK: five zips from `dl.google.com/handwriting/models/` (en-US and es-ES, about 24 MB
  together, the TFLite network is shared). They are not in git. The `fetchInkModels` Gradle task downloads them once
  into `~/.better-notes/ink-models`, checks the SHA-1 in `files.txt` and adds them to the assets. Needs the internet on
  the first build only.
- `src/main/assets/ink-models/files.txt`: where each file goes, its unzipped size and the zip's SHA-1.
- `src/main/assets/ink-models/state/*.xml`: copies of ML Kit's `shared_prefs/gms_icing_mdd_*mlkit_digital_ink_recognition.xml`
  and `gms_icing_mdd_migrations.xml`, taken after a real download. They record which files exist.

## How it works
`InkModels.install` (called from `BetterNotesApp`) runs `InkModelSeeder` on a thread. If ML Kit has no model files, it
unzips the five files to `files/mlkit_digital_ink_recognition/shared/datadownload/public/<id>/` and then copies the
preference files, which go last. `InkModels.missing` and `download` wait for it. Models ML Kit downloaded itself are left alone.

## Checking for newer models
ML Kit cannot be asked for newer models: it has no version API, and the list of model files, addresses and checksums is
inside the library. The models only change when `mlkitInk` in `libs.versions.toml` is raised. So there is no in-app
update check. `.\scripts\check-ink-models.ps1` compares `files.txt` (with its `# mlkit-version:` line) with the list in the
ML Kit library in the Gradle cache. It exits 1 and names what differs: a changed ML Kit version, a changed checksum, or a
model that has a newer dated file. Run it after raising `mlkitInk`.

## When ML Kit is updated (`mlkitInk` in `libs.versions.toml`)
The copied state is tied to ML Kit 19.0.0 (the model files from 2019-12-08 and `datadownloadfile_*` ids). After a bump,
re-capture it: install a debug build, run `HandwritingCheck` with `handwriting_download`, pull `files/mlkit_digital_ink_recognition`
and the `gms_icing_mdd_*` preference files with `run-as`, and update `files.txt` (zip names, sizes, SHA-1s from the library's list, and its `mlkit-version` line) and `state/`. Then run `check-ink-models.ps1` until it says up to date. If ML Kit does not accept the
old state, it just downloads (debug and profile builds) or reports the models missing (offline build).

## Not done or unknown
- Google's terms on redistributing these model files in our own APK were not checked. This is a private app.
- ML Kit might delete the files on its own (its maintenance has expiry rules). Not seen. If it happens the models are
  restored on the next start, because the seeder checks for the files.
- No automated test: the seeder needs a device, and connected tests must not run on the Pad 7.
