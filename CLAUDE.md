# Better Notes
Stylus-first note-taking and PDF annotation app for Android tablets, modelled on Notewise for Android.
Primary device: Xiaomi Pad 7 (Android 16, API 36) with stylus. iPhone is a possible future target.
Native Kotlin: Compose for the UI, a custom View for the canvas. Pure-Kotlin `:core` (JVM tests) plus Android `:app`.
Fully offline. The ML Kit Digital Ink models (English, Spanish) ship inside the APK, so nothing is downloaded (`docs/handwriting-models.md`).

## Priorities
1. Clean, efficient code.
2. Speed, especially loading PDFs and registering input.

## Commands (Windows 11)
`java` is not on PATH. Set `JAVA_HOME=D:\Android\Android Studio\jbr` before calling `gradlew` directly; `scripts\check.ps1` does it for you.
- All checks (tests, lint, debug and release builds): `.\scripts\check.ps1`
- Core unit tests: `.\gradlew.bat :core:test`
- Debug APK: `.\gradlew.bat :app:assembleDebug`
- Canvas tests on a device or emulator: `.\gradlew.bat :app:connectedDebugAndroidTest`
- Measure on a device: `.\scripts\measure-device.ps1 [-Serial <serial>] [-Strokes 3000] [-HandSeconds 60]`
- Time things on the `profile` build type, never debug.

## Hard rules
- Never assume a refresh, touch or stylus rate (maximums: 144 / 360 / 240 Hz). Time by the clock and by event timestamps, not by counting frames or samples. Check the rate the app actually gets; HyperOS may limit it.
- Verify performance on the real Pad 7. Report measured numbers, and say plainly what was not compiled, tested, run or measured.
- UI must match Notewise. If a screen's reference screenshot is missing, ask the owner; do not invent it.
- Redraw icons and artwork; never extract or copy Notewise assets.
- Tiles must never cut anything. Any new or changed display item, tool or painter must paint only inside its `paintBounds`, and a tile that is touched by even one pixel of it must be drawn and redrawn. `paintBounds` takes in everything painted: text that overflows its frame (the page does not clip it), glyph overhang, italic slant, spans, stroke width and miter joins, arrow tips, shadows, antialiasing. Page-local regions turn with the page before they reach the tiles (`DocumentLayout.pageBoundsToWorld`). Add a core test in `PaintBoundsTest` or `TileGridTest` for each new item.
- All page and item changes go through edits (`ItemsEdit`, `UndoHistory`). Undo is strictly last-in, first-out.
- To change the file format, bump `FORMAT_VERSION` and add a migration. Never edit the expected bytes in `CodecTest`.
- Nothing online. No cloud, sync, sharing, AI or account.

## Where to look (read only when relevant)
- Product requirements and owner decisions: `docs/requirements.md`
- UI reference and missing screenshots: `docs/ui-reference.md` (images in `reference/notewise/`, git-ignored)
- Device facts and real-size math: `docs/device.md`
- Dev environment and tool versions: `docs/environment.md`
- Current status, known gaps, open choices, workstreams: `STATUS.md`

Source files are an unverified starting point; replace them if a better structure emerges.
