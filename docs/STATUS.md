# Project status

Location: `D:\code\better-notes`, a local git repository with no remote.

**The foundation, `:core` and the canvas build and pass their tests on the PC and on an x86_64 phone emulator (API 37). Nothing has run on the Pad 7 yet, so no performance number below comes from it.**

## Known gaps
- Lint reports 2 warnings: no app icon, and `allowBackup` without `dataExtractionRules`.
- The release build is not signed, so it cannot be installed as is. The debug build can.
- All real UI is still missing. `DevChrome` is a plain stand-in for the toolbars, and `MainActivity` opens a scratch note or canvas until the library exists.
- The canvas paints only strokes and the page background. Shapes, images, text, sticky notes, tables, recordings and PDF pages are in the model but are not drawn yet.
- The pencil and dotted lines have no brush of their own: the pencil uses the ball pen's brush, and dotted lines use the dashed brush, which ignores dash and gap lengths.
- Redrawing after a zoom settles happens in one frame, on the main thread. On the emulator, with 3000 strokes all in view at 30%, that frame took about 430 ms. Check on the tablet and spread the work over frames if it shows.
- `InProgressStrokesView`'s latency data is restricted API, used only by `PerfProbe`, which release builds drop.
- Library folders are not in `:core` yet; the library workstream adds them.

## Architecture notes (for orientation; the code is the source of truth)
- **Coordinates:** world units in PDF points (1/72 inch), so PDFs and notes share one space. Zoom 1.0 equals physical size via screen DPI.
- **Storage:** one binary file per page, so long documents only load the pages on screen. Stroke samples are packed floats (x, y, pressure, tilt, orientation, elapsed ms), stored uncompressed (24 bytes per sample) to favor load speed.
- **Ink:** androidx.ink `InProgressStrokesView` with `MotionEventPredictor` and `requestUnbufferedDispatch`.
- **PDF:** platform `PdfRenderer`; `minSdk` 35 adds text extraction.
- **Gestures:** `GestureRecognizer` has no clock. The app schedules `onDeadline` at `nextDeadlineMs`, which is how undo waits out the triple-tap window.
- **Rendering:** `TileLayerView` draws finished content as 512 px `RenderNode` tiles with GPU layers. `PagePainter.paintItems` is where new item types go.
- **Templates:** emit lines and dots to a `TemplateSink`, shared by the canvas and PDF export.
- **Perf:** `app/.../perf/` holds `PerfProbe` (logcat tag `BetterNotesPerf`, debug and profile builds) and `CanvasBenchmark`.

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
4. **PDF:** import, fast tiled rendering, annotation, text search.
5. **Library:** tiles, thumbnails, folders.
6. **Editor UI and tools:** toolbars, panels, shapes, images and camera, sticky notes, tables, clipboard, loupe, ruler, recorder, laser pointer, view-only mode.
7. **Settings and output:** gestures, keyboard shortcuts, scroll layouts, templates, custom layout builder, PDF and image export, handwriting search.
