# Better Notes: Project Brief

## 1. Product
"Better Notes" is a stylus-first note-taking and PDF annotation app for Android tablets, modelled on Notewise for Android.
- **Primary device:** Xiaomi Pad 7 with stylus. iPhone is a possible future target.
- **Connectivity:** all features are offline and local.
- **Branding:** the implementing agent decides, unless the owner specifies otherwise.
- **UI:** must be identical to Notewise (see section 4).

### Priorities
1. Clean, efficient code.
2. App speed, especially loading PDFs and registering input.

### Performance targets
- 144 Hz screen refresh rate
- 360 Hz general touch sampling
- 240 Hz stylus sampling

## 2. Owner requirements

### Library (home screen)
- Opening the app shows loaded PDFs and notes as tiles, each showing part of the first page.
- Folders can be created here.

### Notes and pages
- A note is either paged, with resizable pages, or an infinite canvas.
- Zoom goes up to 1500%.
- Default zoom is real size: 1 cm drawn on an A4 page equals 1 cm on the real page.
- A canvas that already exists can change size. A PDF must not.

### Inside a note or PDF
- See all pages and rearrange them.
- Quick page switcher: a list of all pages with thumbnails.
- Add a page.
- Search, which must attempt to recognize handwriting.

### Toolbar
**Input mode**
- Switch between stylus draw and finger draw.
- When stylus draw is active, fingers move the page.

**Pen, pencil and highlighter (each has all of these)**
- 5 colors, with the ability to add one custom color or change the defaults
- 3 line widths
- Opacity
- An expanded panel with: fine-tuning of line width, round or square tip, a hold-to-draw-straight-line timer, and a color wheel

**Lasso**
- Select items and resize them.

**Eraser**
- Partial erasing
- Tip size slider
- Choice of partial or entire-path erase
- "Clear page"; on an infinite canvas it clears only the displayed portion

**Shapes**
- Fill
- Line width
- Line continuity: dotted, dashed, dot/dash size
- Corner radius

**Images**
- Add an image.
- A dedicated "photo" button goes straight to the camera.
- Photo border, corner radius and shape.

**Sticky note**
- A real sticky note that is smaller when closed.
- Anything typed or drawn in it is contained in it.

**Other tools**
- Table
- Clipboard button, which must support pasting photos copied from outside the app
- Loupe
- Recorder
- Ruler displayed over the canvas to help draw lines
- Laser pointer
- "View only" toggle in a corner, which disables editing and removes the tools

### Settings
**Export**
- Export to PDF and to image.

**Stylus and fingers (defaults)**
- One finger scrolls.
- Two fingers zoom and pan.
- Double tap undoes, with a setting to change its timing.
- Triple tap with two fingers redoes.
- Keyboard shortcuts for all tools and options.

**Scroll and layout**
- Side to side, to the left or right, or vertical.

**Page appearance**
- Change the template and background color.
- Lined, dotted or grid, each with adjustable density and line width.
- Page layouts: hexagonal, Cornell, legal, music score, guitar.
- Build your own custom layout: set the size in px, cm or inches, and add lines, tables, etc.

## 3. Owner decisions and open questions

### Decided by the owner (2026-11-03)
- **Undo gesture:** two fingers, so a two-finger double tap undoes and a two-finger triple tap redoes. Undo must wait out the triple-tap window before firing.
- **Handwriting languages:** English and Spanish.
- **Online features:** nothing online at all: no cloud, sync, sharing, AI or account. Leave out the cloud button, avatar and people filter.
- **Distribution:** personal use. Still do not copy Notewise's assets.
- **Themes:** light and dark, with dark as the default. No light-mode reference screenshots exist.

### Decided by the owner (2026-11-04)
- **Handwriting model download:** a one-time download of the ML Kit Digital Ink models for English and Spanish (about 20 MB each) is accepted. It is the only exception to "nothing online". If the models ever go missing, the app reports it and does not download them again by itself.
- **Greeting:** the app asks for a name only, and the library greeting uses it.
- **`minSdk`:** stays at 35. The owner's tablet runs Android 16 (API 36), which meets it.

### Still to verify
- That recognition works on the Pad 7 with no network: run `.\scripts\verify-handwriting-offline.ps1 -Serial <serial>`. On an x86_64 emulator (API 37) it worked in airplane mode and with no `INTERNET` permission, with the models surviving that update and 0 bytes of the app's traffic while recognizing. `TrafficStats` sees only the app's own traffic, not Google Play services'.

## 4. UI reference
The owner's screenshots of Notewise in dark mode are in `D:\code\better-notes\reference\notewise\`, named for what they show.

They contain personal notes and a profile photo. `reference/` is git-ignored so they stay out of every repository; a fresh clone will not have them.

Colors below are descriptions by eye; none have been measured. Icon meanings marked "(?)" are unconfirmed.

### Library
- Near-black background with a subtle blue dotted glow along the top.
- Large serif greeting ("Good evening, [name]") with a smaller sans-serif subtitle.
- Top right: a cloud-status button, and a pill holding search and the avatar.
- Filter row: a blue "All" chip, then star, tag, people and bin icons; a circled "..." at far right.
- Portrait layout is a 3-column grid of rounded cards with a thin light border.
- Document card: dark header (date, title up to 2 lines, "..."), with the first-page thumbnail filling the rest.
- Folder card: solid blue folder illustration, "18 items" at top, "..." and the folder name at bottom.
- Bottom left: floating pill with home (selected, blue), an asterisk (AI?) and a grid icon (?).
- Bottom right: round blue "+" floating button.

### Editor toolbars
Dark floating pills sit over the canvas, with no full-width bar.
- **Left pill:** back, sidebar panel, page grid, page stack (?), up/down chevrons (?).
- **Center pill:** undo, redo, finger mode (hand with arrows), then a horizontally scrolling tool strip, then a divider and a collapse chevron.
  - Visible in the strip: table, an unidentified tray icon, sticky/add (?), loupe (?), ruler, microphone, laser pointer (?), settings.
  - A partly hidden icon at the left edge suggests more tools are scrolled out of view, likely including the pen.
- **Right pill:** eye (view-only, shown in a lighter circle) and "...".
- **Second-row pill (active tool):** line type, 3 widths, 5 color circles, add color, edit color, sliders button. Dividers separate the groups. A small blue dot marks the selected width and color.

### Pen panel
A dark rounded panel with these parts, top to bottom:
- Title "Pen" and a circular close button.
- Stroke preview on a dotted background.
- Three type cards: Natural, Ball, Calligraphy. The selected card has a blue fill and border.
- Thickness: value pill, then a slider with minus and plus buttons.
- Line type selector.
- Toggles: Hold to draw shape (with a "..." for options), Scribble to erase, Circle to lasso.
- Stabilization value.
- Color: add button, list button, a row of swatches with the selected one ringed, and a color wheel.

### Editor canvas
- The screenshots show a cream dotted page.

### Screenshots still needed
Without these, those screens can't be matched:
- Eraser, lasso, shape, image, sticky note, table, ruler, loupe, recorder and laser pointer states
- Page grid and quick page switcher
- Sidebar, "..." menus and the add-page flow
- Search results
- New note/import flow
- All settings screens
- The template picker and custom layout builder
- Lasso selection handles
- Toolbar scrolled to show the pen, pencil, highlighter and eraser

Also available: Play Store marketing images and the help site at help.notewise.dev. Treat the marketing images as approximate.

## 5. Verified facts
Checked on 2026-11-03.

### Device (Xiaomi spec page)
- 11.2 inch, 3200 x 2136, 345 ppi, up to 144 Hz.
- Touch sampling up to 360 Hz, 240 Hz with pen.
- The spec page lists HyperOS 2 and doesn't state the Android version. See the next section for what the tablet runs.

### Owner's tablet (read over adb, 2026-11-04)
- Xiaomi Pad 7, model 2410CRP4CG (`uke`), arm64-v8a.
- Android 16 (API 36), HyperOS `OS3.0.304.0.WOZMIXM`.
- Display 2136 x 3200, density bucket 440. It reports xdpi 332.441 and ydpi 340.511.
- It lists refresh rates of 30, 48, 50, 60, 90, 120 and 144 Hz. That list doesn't show what the app actually gets.
- The reported dpi average about 336.5, some 2.5% below the spec's 345 ppi. `CanvasView` uses that average, so real size may come out about 2.5% small. Nobody has measured this on the glass. `AppSettings.calibration` can correct it, but no screen sets it yet.

### Real-size implications at 345 ppi
- 1 cm is about 135.8 px, so A4 width (210 mm) is about 2852 px.
- The screen's short side is about 157 mm, so an A4 page at 100% does not fit the screen width in portrait, and shows only part of its height in landscape.
- Reported device DPI may be inaccurate, so a calibration setting is worth considering.

### Notewise behavior (help site)
- By default, one finger scrolls and the stylus draws; a toolbar Finger Mode button changes this.
- Some Xiaomi devices intercept three-finger gestures at system level, so avoid relying on them.
- Pen settings include Stabilization and a Latency Optimization level.

### Dev environment (Windows 11)
- Android Studio is at `D:\Android\Android Studio`, build 262.10968.63 (the owner reports Rabbit 2).
- The bundled JDK is OpenJDK 25.0.4; `java` is not on PATH.
- The SDK is at `%LOCALAPPDATA%\Android\Sdk`. Installed platform versions have not been checked.
- Android docs list Rabbit 2 as supporting AGP up to 9.5, and AGP 9.5 needs Gradle 9.6+.

### Latest stable versions found
- AGP 9.5.0
- Kotlin 2.4.21
- Compose BOM 2026.09.00
- graphics-core 1.0.4
- input-motionprediction 1.0.0
- androidx.ink 1.0.0 (newest alpha: 1.1.0-alpha10, which adds a partial stroke eraser and the public custom brush API)
- ML Kit digital-ink-recognition 19.0.0

## 6. Current project state
Location: `D:\code\better-notes`, a local git repository with no remote. **The foundation, `:core` and the canvas build and pass their tests on the PC and on an x86_64 phone emulator (API 37). Nothing has run on the Pad 7 yet, so no performance number below comes from it.**

### Build and test
`java` is not on PATH, so set `JAVA_HOME` to `D:\Android\Android Studio\jbr` before calling `gradlew` directly. `scripts\check.ps1` does this for you.
- All checks (unit tests, lint, debug and release builds): `.\scripts\check.ps1`
- Core unit tests: `.\gradlew.bat :core:test`
- Debug APK: `.\gradlew.bat :app:assembleDebug`
- Device tests on a connected device or emulator: `.\gradlew.bat :app:connectedDebugAndroidTest`. Tests that need the handwriting models skip without them, and the test run uninstalls the app, which deletes them. Add `-Pandroid.testInstrumentationRunnerArguments.downloadInk=true` to fetch them first.
- Handwriting with no network, and installing the everyday build: `.\scripts\verify-handwriting-offline.ps1 [-Serial <serial>]`
- Measure on a device: `.\scripts\measure-device.ps1 [-Serial <serial>] [-Strokes 3000] [-HandSeconds 60]`

Toolchain in use: Gradle 9.6.0 with a pinned checksum, AGP 9.5.0, Kotlin 2.4.21, JDK 25 (JBR) targeting Java 17. `local.properties` is git-ignored and holds the SDK path.

### Installed SDK (checked 2026-11-03)
Platform `android-37.0`, build-tools 36.0.0, platform-tools. There is no `cmdline-tools` or NDK. `compileSdk` and `targetSdk` are 37.

### Files that exist
- Gradle config and wrapper, `.gitignore`, `.gitattributes`, `scripts/check.ps1`
- `app/`: the canvas, the measuring tools, and a temporary way in. See "Canvas" below. Also settings, export and search. See "Settings, export and search" below.
- `core/` (`app.betternotes.core`), pure Kotlin with JVM tests. See "Core" below.
- `reference/notewise/`: the owner's screenshots (git-ignored)

### Known gaps
- Lint reports 2 warnings: no app icon, and `allowBackup` without `dataExtractionRules`.
- The release build is not signed, so it cannot be installed as is. The debug build can.
- All real UI is still missing. `DevChrome` is a plain stand-in for the toolbars, with no reference to match, and `MainActivity` opens a scratch note or canvas until the library exists.
- The canvas paints only strokes and the page background. Shapes, images, text, sticky notes, tables, recordings and PDF pages are in the model but are not drawn yet.
- The pencil and dotted lines have no brush of their own yet: the pencil uses the ball pen's brush, and dotted lines use the dashed brush, which ignores the dash and gap lengths.
- Redrawing after a zoom settles happens in one frame, on the main thread. On the emulator, with 3000 strokes all in view at 30%, that frame took about 430 ms. Check this on the tablet, and spread the work over frames if it shows.
- `InProgressStrokesView`'s latency data is restricted API, used only by `PerfProbe`, which release builds drop.
- Export and search have logic and tests but no screens, because no reference screenshots of them exist.
- The settings screens are reached from `DevChrome` until the real toolbars take over. Whoever owns those shows them through `SettingsOverlayHost`.
- Search does not cover PDF text yet.
- Export paints what `PagePainter` paints, so it has the same gap as the canvas: only strokes, paper and templates for now.
- ML Kit's native libraries make the APK large: about 73 MB for debug and 39 MB for offline, with every ABI included. Only arm64-v8a is needed on the tablet.

### Core
- `geometry`: bounds, affine, rotated `Frame`, convex clipping (`Capsule`, `RectClipper`), units, viewport.
- `model`: `Document` and `Page` (description), `PageContent` (items in paint order, with their bounds in one flat array), immutable `PageItem` types (stroke, shape, image, text, sticky note, table, audio). Stroke samples are packed floats: x, y, pressure, tilt, orientation, elapsed ms.
- `edit`: `ItemsEdit` and the page edits, `UndoHistory`. Edits store positions, which stay valid because undo is strictly last-in, first-out. All page and item changes go through edits.
- `tools`: `EraseSession` (partial or whole), `clearEdit`, `selectWithLasso`, `resizeTransform`, `topItemAt`.
- `gesture`: `GestureRecognizer`. It has no clock, so the app schedules `onDeadline` at `nextDeadlineMs`, which is how undo waits out the triple-tap window.
- `template`: templates emit lines and dots to a `TemplateSink`, so the canvas and PDF export can share them. The sink clips.
- `format`: `PageCodec`, `DocumentCodec`, `DocumentStore` (atomic writes, CRC-32), `DocumentSession` (lazy page loading, eviction, undo). `CodecTest` pins the bytes of format version 1: to change the format, bump `FORMAT_VERSION` and add a migration instead of editing the expected bytes.

Choices in `:core` that have no reference and may need the owner's input:
- The eraser skips images, text, tables, sticky notes and recordings. Only the lasso and "clear page" remove them.
- On an infinite canvas, "clear page" removes items inside the view, cuts strokes at its edge and keeps other items that cross it.
- A stroke is lassoed if at least half of it is inside. Other items are lassoed by their center.
- Guitar Tablature is 6-line staves, and Guitar Score pairs a 5-line staff with a 6-line one. Cornell and legal proportions, template colors and gesture timings are defaults chosen without a reference.
- Stroke samples are stored uncompressed, favoring load speed over disk use (24 bytes per sample).
- Library folders are not in `:core` yet; the library workstream adds them.

### Canvas
In `app/.../canvas/`:
- `CanvasView`: the drawing surface. Touches go to `GestureRecognizer`. The stroke in progress goes through androidx.ink's `InProgressStrokesView`, with motion prediction and unbuffered dispatch. Also handles pan, pinch, fling and the eraser.
- `TileLayerView`: finished content as 512 px tiles, each a `RenderNode` with a GPU layer, in a fixed square of slots. Moving the page only composites textures and rasterizes the tiles it uncovers. During a pinch the tiles stretch, and they redraw past a set stretch and when the fingers lift.
- `PagePainter`: paints paper, template and items into a tile. New item types go in `paintItems`.
- `NoteEditor`: the open document on the main thread, with disk work and meshing on one background thread. Meshing a page uses all cores. Saves 1 s after the last edit, and on `onStop`.
- `StrokeCache` and `InkBrushes`: ink meshes per `StrokeItem`, and `InkStyle` to `Brush`, with stock brushes pinned to a version.

`DocumentLayout` and `TileGrid` in `:core` hold the math. Stroke coordinates are the page's own points. `app/.../perf/` holds `PerfProbe` (logcat tag `BetterNotesPerf`, debug and profile builds) and `CanvasBenchmark`. Time things on the `profile` build type, which runs like a release.

Choices in the canvas that have no reference and may need the owner's input:
- A stroke that starts off a page, in the gap or margin, is ignored. Ink past the page's edge is clipped.
- Page gap 12 pt, margin around the document 24 dp, backdrop color near-black, tile redraw at stretches past 0.6x and 1.6x.
- A note at 100% wider than the screen opens at its top-left corner.
- The infinite canvas stops about 18 m from the origin in each direction, to keep float coordinates accurate at 1500%.
- The stylus's eraser end erases partially, with a 6 pt radius.
- Calligraphy is a flat 45-degree nib, and a square tip ignores pressure.

### Settings, export and search
- `core/.../settings`: `AppSettings` (two-finger tap timings, scroll direction, new page defaults, custom layouts, shortcuts, screen calibration), `SettingsCodec` (tagged sections, so a setting can be added without a format version) and `SettingsStore`. The theme is not here: the library keeps it in SharedPreferences. `app/.../settings/SettingsRepository` holds the current value and saves in the background. `CanvasView.applySettings` applies them.
- Shortcuts: `KeyBindings` maps `EditorAction` to `KeyChord`. `MainActivity.onKeyDown` gets only keys no view took, and passes them to `CanvasView.perform`. That handles undo, redo, zoom, page moves, finger mode and view-only, and returns false for the rest, which the toolbars must handle.
- `core/.../template`: `PageAppearance` (template kinds, density and line weight, undoable background and custom layout edits) and `CustomLayoutBuilder`.
- Export: `core/.../export/ExportPlan` holds the math and file names. `app/.../export/DocumentExporter` writes PDFs (vector, one point per point) and PNG or JPEG images (300 dpi by default) from the saved files on a background thread. `NoteEditor.exporter()` saves first.
- `PagePainter` draws from a `PaintSource`, so that export can use it off the main thread. New item types and PDF backgrounds added there reach exports too, if what they need is on `PaintSource`.
- Search: `core/.../search` has the `HandwritingRecognizer` interface, `groupInkLines`, `SearchIndex` and `SearchIndexer`, which saves `search.bni` in the document's folder and only recognizes lines that changed. `app/.../search` has `MlKitRecognizer`, `InkModels` and `DocumentSearch`, which refreshes after each save on a low-priority thread.
- Screens: `app/.../ui/settings/`. `SettingsScreen`, `StylusFingerSheet` and `TemplateSheet` follow the owner's screenshots `options.jpg`, `finger and stylus options.jpg` and `template view.jpg`, with colors and sizes measured from them. Scroll and layout, shortcuts, calibration, handwriting models and `CustomLayoutSheet` have no screenshot, so they only follow the same style. `SettingsTheme` scales one dp to one reference pixel.
- Stylus buttons: `StylusButtons` tells a click from a hold. `CanvasView.onToolAction` receives the tool choices, which the toolbars must act on.
- The `offline` build type has no `INTERNET` permission. It uses the same package and key as debug, so it installs over a debug build that has downloaded the models. Debug and profile keep the permission, and so does release, which is not used yet. ML Kit needs the keep rules in `app/proguard-rules.pro`, or minified builds crash at startup.
- `perf/HandwritingCheck` and `SyntheticInk` run the check script's recognition. The words are drawn by program, so they show the pipeline works, not how well it reads real writing.

Left out of the reference's screens, on purpose: notifications, password, Link, Snap to align, "Auto name notes and sources", "Learn more" and the info icon. They are online, unrequested or have nothing behind them.

Choices here that have no reference and may need the owner's input:
- "Fast" latency means motion prediction is on. Nothing else in the app differs between the two levels.
- Double tap with one finger zooms to 200%, and from any other zoom back to real size.
- Density runs 1 to 10, higher is denser, and 7 is each template's default. Line width runs 1 to 10, and 3 is the default.
- The size chooser offers A4, A5, Letter and Legal. The reference's options aren't visible.
- All default shortcuts. Single letters choose tools, and Ctrl combinations do the rest.
- Undo's tap window can be set from 100 to 1000 ms, and the default stays 300 ms. The slider sits under Redo on the stylus and finger sheet, past what the screenshot shows.
- A custom layout resizes the page to its own size, and can't go on a PDF page or a canvas. Changing the template of a PDF page does nothing.
- An exported canvas shows its items plus a 24 pt margin, and an empty one is left out. Images stop at 64 million pixels, and a canvas larger than a PDF page shrinks to fit.
- Search needs all the query's words on one line, ignores case and accents, and lets handwriting differ by one letter from 4 letters up, and two from 8 up. Those near matches are flagged `exact = false`.
- Highlighter strokes, and strokes over 5 times the median height, are not read as writing.

Treat the source files as an unverified starting point. Replace them if a better structure emerges.

## 7. Proposed approach (not owner decisions)
Open to challenge. These are one agent's suggestions, not validated by a build.
- **Stack:** native Kotlin. Compose for the UI, and a custom View for the canvas.
- **Ink:** androidx.ink `InProgressStrokesView` with `MotionEventPredictor` and `requestUnbufferedDispatch` for low-latency strokes. Its newer releases add iOS rendering work, which may help a future port.
- **Modules:** a pure Kotlin `:core` (model, geometry, erase, lasso, gestures, templates, file format) with JVM tests, plus an Android `:app`.
- **Coordinates:** world units in PDF points (1/72 inch), so PDFs and notes share one space. Zoom 1.0 equals physical size via screen DPI.
- **Storage:** one binary file per page, so long documents only load the pages on screen.
- **PDF:** platform `PdfRenderer`. `minSdk` 35 adds its text extraction, and the tablet's Android 16 meets that.
- **Undo:** immutable items with reversible edits.

## 8. Suggested workstreams
Dependencies run top to bottom; workstreams 4 to 7 can run in parallel once 3 is stable.
1. **Foundation:** get the Gradle project building, add the wrapper, initialize git, add CI-style test commands.
2. **Core:** document model, geometry, undo, eraser, lasso, gestures, templates and file format, with unit tests.
3. **Canvas and ink:** low-latency drawing, zoom and pan, real-size rendering, stylus and finger modes. Test early on the real tablet.
4. **PDF:** import, fast tiled rendering, annotation, text search.
5. **Library:** tiles, thumbnails, folders.
6. **Editor UI and tools:** toolbars, panels, shapes, images and camera, sticky notes, tables, clipboard, loupe, ruler, recorder, laser pointer, view-only mode.
7. **Settings and output:** gestures, keyboard shortcuts, scroll layouts, templates, custom layout builder, PDF and image export, handwriting search.

## 9. Working rules
- **Never add Claude or AI attribution to git commits (no `Co-Authored-By`).** All credit goes to the owner.
- Verify performance on the actual Pad 7. Report measured numbers, and say plainly when something could not be measured.
- HyperOS may limit an app's refresh rate; check the rate the app actually gets rather than assuming 144 Hz.
- Report honestly what was and wasn't compiled, tested or run on the device.
- Ask the owner when a screen's reference screenshot is missing rather than inventing it, since the UI must match.
- Redraw icons and artwork instead of extracting them from Notewise.
