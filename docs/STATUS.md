# Project status

Location: `D:\code\better-notes`, a local git repository with no remote.

**PDF, settings, the library and the launcher icon are merged with the foundation, `:core` and the canvas. They build and pass their tests on the PC and on an x86_64 emulator at the Pad 7's resolution and density (API 37). The library has not run on the Pad 7, so none of its speed has been measured.**

## Known gaps
- Lint reports 1 warning: `allowBackup` without `dataExtractionRules`.
- The release build is not signed, so it cannot be installed as is. The debug build can.
- The editor's toolbars and tools are on branch `ws6-editor`, not yet merged. See `docs/handoff/editor.md`. `MainActivity` is the editor, and opens a scratch note or canvas when started without a document, as the tests and measuring scripts do.
- The pencil and dotted lines have no brush of their own: the pencil uses the ball pen's brush, and dotted lines use the dashed brush, which ignores dash and gap lengths.
- Redrawing after a zoom settles happens in one frame, on the main thread. On the emulator, with 3000 strokes all in view at 30%, that frame took about 430 ms. Check on the tablet and spread the work over frames if it shows.
- `InProgressStrokesView`'s latency data is restricted API, used only by `PerfProbe`, which release builds drop.
- Settings, export and search reach the app through `DevChrome` and the home screen's menu until the real toolbars take over. Whoever owns those shows them through `SettingsOverlayHost`.

## Architecture notes (for orientation; the code is the source of truth)
- **Coordinates:** world units in PDF points (1/72 inch), so PDFs and notes share one space. Zoom 1.0 equals physical size via screen DPI.
- **Storage:** one binary file per page, so long documents only load the pages on screen. Stroke samples are packed floats (x, y, pressure, tilt, orientation, elapsed ms), stored uncompressed (24 bytes per sample) to favor load speed.
- **Ink:** androidx.ink `InProgressStrokesView` with `MotionEventPredictor` and `requestUnbufferedDispatch`.
- **PDF:** platform `PdfRenderer`; `minSdk` 35 adds text extraction.
- **Gestures:** `GestureRecognizer` has no clock. The app schedules `onDeadline` at `nextDeadlineMs`, which is how undo waits out the triple-tap window.
- **Rendering:** `TileLayerView` draws finished content as 512 px `RenderNode` tiles with GPU layers. `PagePainter.paintItems` is where new item types go.
- **Templates:** emit lines and dots to a `TemplateSink`, shared by the canvas and PDF export.
- **Perf:** `app/.../perf/` holds `PerfProbe` (logcat tag `BetterNotesPerf`, debug and profile builds) and `CanvasBenchmark`.

## Subsystems

### Library
The app opens on `ui/library/LibraryActivity`, which starts `MainActivity` with `EXTRA_DOCUMENT_ID`.
- `core/.../library`: `Library`, immutable, holds folders and documents as one `LibraryEntry` type. `LibraryStore` lays out `library.bnl` and `documents/<hex id>/` in `filesDir`. A document's own folder is the truth about it: the library file caches its name and dates, and `LibraryRepository.refresh()` compares file times to correct them, so nothing has to tell the library about a save. `LibraryStoreTest` pins the file's bytes.
- `app/.../library`: `LibraryRepository` (one per process, changes apply on the main thread and write in the background), `ThumbnailStore` and `ThumbnailRenderer` (720x540 WebP in the cache folder, named by save time, decoded to hardware bitmaps), and `LibraryPreferences` (theme, sort and the owner's name, in SharedPreferences `settings`).
- `ui/theme`: `AppTheme` and `AppColors`. `ReferenceScale` makes 1 dp equal 1 pixel of the 1068 px wide reference, so its measurements copy over directly. The dark colors are measured from the screenshot; the light ones have no reference.
- To compare with the reference: seed made-up content with `LibraryDemoSeed` (see its comment), then take a screenshot. `reference/notewise/app home.jpg` is at half the Pad 7's resolution, and `add new note.jpg` at its full resolution.

Library decisions and references:
- The owner chose: "Text" makes a typed notebook (`DocumentKind.TEXT`, `TextNoteActivity`), "Canvas" a drawing note with pages, not an infinite one. Nothing in the library creates an infinite canvas any more, and where it should be offered is still to ask.
- Inside a folder the screen is the same, with a house and the folder's name in place of "All".
- The bottom pill keeps only home, since its other icons were online features.
- The "+" panel matches `add new note.jpg` without "Link", and the circled "..." matches `... on home.jpg` plus a Settings row, since the avatar that led there is gone.
- UI text is English or Latin American Spanish (`values-b+es+419`), chosen under Settings > General with the theme and the name. New strings need both, which lint enforces.
- A popup or dialog starts a new composition with the device's density. Menus measured from a reference hand in the scale, as `OptionsMenu` does. The plain dialogs and the tiles' menus stay at real dp.

Library choices with no reference:
- The tiles' "..." menus, all dialogs, the search field, selecting, the list layout's rows and the typed notebook's screen.
- "Scan" is the camera with no edge detection, because the offline-capable scanner needs Google Play services. "File & Doc" takes PDFs, sound and pictures, not PPT, which nothing here can open offline.
- `EXTRA_START_ACTION` asks the editor to start recording in a new note. Nothing reads it until the editor has that tool.
- Morning and afternoon use the evening's subtitle, and "Hey" lasts until 5 AM. The screenshots only show 8:21 PM and 2:05 AM.
- Dates for items older than this year, the tag filter's list, and landscape's 4 columns.
- The serif is the system's (Noto Serif), not the reference's, which only looks close.
- `ThumbnailRenderer` draws ink, pictures and typed text with its own code. Once `PagePainter` paints those, it should go through a `PaintSource`.

### Settings, export and search
- `core/.../settings`: `AppSettings` (two-finger tap timings, scroll direction, new page defaults, custom layouts, shortcuts, screen calibration), `SettingsCodec` (tagged sections, so a setting can be added without a format version) and `SettingsStore`. The theme is not here: the library keeps it in SharedPreferences. `app/.../settings/SettingsRepository` holds the current value and saves in the background. `CanvasView.applySettings` applies them.
- Shortcuts: `KeyBindings` maps `EditorAction` to `KeyChord`. `MainActivity.onKeyDown` gets only keys no view took, and passes them to `CanvasView.perform`. That handles undo, redo, zoom, page moves, finger mode and view-only, and returns false for the rest, which the toolbars must handle.
- `core/.../template`: `PageAppearance` (template kinds, density and line weight, undoable background and custom layout edits) and `CustomLayoutBuilder`.
- Export: `core/.../export/ExportPlan` holds the math and file names. `app/.../export/DocumentExporter` writes PDFs (vector, one point per point) and PNG or JPEG images (300 dpi by default) from the saved files on a background thread. `NoteEditor.exporter()` saves first.
- `PagePainter` draws from a `PaintSource`, so that export can use it off the main thread. New item types added there reach exports too, if what they need is on `PaintSource`.
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

### PDF
In `app/.../pdf/`, plus `SearchableText` in `:core`'s `search` package:
- `PdfImporter`: copies a PDF into a new document folder, `documentsDir/<id hex>`, built in a temporary folder and renamed at the end. An encrypted file is stored decrypted, so the password is never kept. Off the main thread.
- `PdfWorker`: the one thread that talks to `PdfRenderer`, because the platform serializes PDF work anyway. It keeps the last page open. Previews go first, then tiles nearest the middle of the view, then one search page at a time.
- `PdfPages`: paints a PDF page into a tile without ever waiting. A tile uses its own raster if ready, otherwise the zoom before stretched, otherwise a whole-page preview (512 px on the long side), and is redrawn when its raster arrives. Rasters sit on the tile's own pixels, so text stays as sharp as PDFium drew it. A pinch asks for no sharp tiles until it ends.
- `PdfThumbnails`: a one-off render of a page, for the library and the page switcher.
- Search has two parts. `PdfPrintedText` feeds PDF lines into `SearchIndexer` as `TextSource.PRINTED`, so `DocumentSearch` finds PDF text next to handwriting, with a whole line as the hit's rectangle. `NoteEditor.searchPdfText` streams `TextMatch`es with the exact words' rectangles, and `CanvasView.showMatches` and `reveal` mark and scroll to them. The search screen should list hits from `DocumentSearch` and can mark words with `searchPdfText`, or the two could become one if line rectangles prove good enough.
- Export: `DocumentExporter` draws the PDF page under the ink with `PdfThumbnails`, because `PaintSource.paintPdfTile` is only for tiles.

`PdfTest` runs on a device or emulator and checks real pixels. `PdfSpeedTest` times a generated 300-page PDF and logs under `BetterNotesPerf`. With more than one device attached, set `ANDROID_SERIAL` or Gradle runs on all of them.

Measured on the x86_64 phone emulator only, so these say little about the Pad 7: import 1.1 to 1.6 s, opening the saved document 8 to 14 ms, first pixels of page 1 190 to 390 ms, jumping to page 201 240 to 410 ms, first search 1.9 to 3 s, a repeat search 19 ms.

Known gaps:
- Tested only on PDFs generated with Android's `PdfDocument` and with pypdf, never on a real-world file such as a scanned or very heavy book.
- `PdfRenderer` reports page sizes in whole points, so a real A4 page (595.28 x 841.89) can lose under 1 pt at its right and bottom edges. That is about 0.05% off true size.
- Text search reads text only, so a scanned PDF finds nothing. There is no OCR.
- Previews and tiles are not cached on disk. `searchPdfText` reads text once per page and keeps it only in memory.
- The bitmap caches have fixed budgets (96 MB previews, 128 MB tiles) and do not react to `onTrimMemory`.
- Links and forms in a PDF do nothing.
- An exported PDF holds the pages of an imported PDF as 300 dpi pixels, so they lose their selectable text. The platform offers no way to copy their vectors at `minSdk` 35.

Choices with no reference that may need the owner's input:
- A PDF page is always on white paper. Its page background and template are ignored, while blank pages added to a PDF keep theirs.
- Search matches ignore case, accents, ligatures, line wraps and all spaces, so "a si" also finds "así".
- Highlight colors for search results.

## Choices made without a reference (may need owner input)

### In `:core`
- The eraser skips images, text, tables, sticky notes and recordings. Only the lasso and "clear page" remove them.
- On an infinite canvas, "clear page" removes items inside the view, cuts strokes at its edge and keeps other items that cross it.
- A stroke is lassoed if at least half of it is inside. Other items are lassoed by their center.
- The guitar layout is 6-line tablature. Cornell and legal proportions, template colors and gesture timings are defaults chosen without a reference.

### In the canvas
- A stroke that starts off a page, in the gap or margin, is ignored. Ink past the page's edge is clipped.
- Page gap 12 pt, margin around the document 24 dp, backdrop color near-black, tile redraw at stretches past 0.6x and 1.6x.
- A note at 100% wider than the screen opens at its top-left corner.
- The infinite canvas stops about 18 m from the origin in each direction, to keep float coordinates accurate at 1500%.
- The stylus's eraser end erases partially, with a 6 pt radius.
- Calligraphy is a flat 45-degree nib, and a square tip ignores pressure.

## Suggested workstreams
Dependencies run top to bottom; 4 to 7 can run in parallel once 3 is stable.
1. **Foundation:** done.
2. **Core:** done (model, geometry, undo, eraser, lasso, gestures, templates, file format, with tests).
3. **Canvas and ink:** built and tested on emulator only. **Next: test on the real Pad 7** (latency, refresh rate actually granted, real-size accuracy, the 430 ms redraw frame).
4. **PDF:** merged.
5. **Library:** merged.
6. **Editor UI and tools:** toolbars, panels, shapes, images and camera, sticky notes, tables, clipboard, loupe, ruler, recorder, laser pointer, view-only mode.
7. **Settings and output:** merged.
