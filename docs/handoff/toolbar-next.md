# Next editor tasks: handoff

State as of 2026-11-04. Read this, then only the files named below. Rules are in `CLAUDE.md`: no AI attribution in commits, no public push, say plainly what was and wasn't compiled, tested or run on the Pad 7, and ask the owner when a reference screenshot is missing.

## Repo state
- `main` (9192e42) has everything the owner asked for up to the creative brushes. `.\scripts\check.ps1` passes on it (core tests, lint, debug and release builds). The same build is installed on the Pad 7 (`f9ea56c5`). Ask the owner before taking over the tablet.
- Branch `wip-creative-brushes` holds the unfinished brush work (see below). It does not compile yet.
- Handoff of the earlier editor work: `docs/handoff/editor.md` (its last section lists the previous feedback round). This file is only for what is left.
- Emulator: `Ws6_Pad7` on port 5580 runs on-device tests (`ANDROID_SERIAL=emulator-5580`). Another thread's emulator may be on 5590; the PC has 16 GB, so stop yours when done. The test `CanvasViewTest#touchingACircleDrawnWithCircleToLassoSelectsWhatItSurrounds` passes there.
- Format: page files are at `PAGE_FORMAT_VERSION = 2` on `main` (shape tips). Other files stay at `FORMAT_VERSION = 1`. `CodecTest` golden bytes must not be edited; `PageCodec.encode(items, version = 1)` still writes the old layout for that test.

## Task 1: creative brushes (partly built, on `wip-creative-brushes`)
The owner's screenshots are `reference\notewise\brushes1.jpg` and `brushes2.png`. The pencil slot becomes "Creative brush" with a Brush type dropdown: Pencil, Oil, Spill, Watercolor, Comics, Scratches, Dots, Leaves, Cross, Glow. The pen and highlighter are unchanged.

Done on the branch (uncommitted-to-main, uncompiled):
- `BrushKind` enum and `InkStyle.brush` in `core/.../model/PageItem.kt`; `InkPreset.brush`; saved in `ToolStore` (key `brush`).
- Page format version 3: `BRUSH_KINDS` wire enum and the brush byte in `inkStyle()` in `PageCodec.kt` (read and written only when `version >= 3`); `PAGE_FORMAT_VERSION = 3` in `BinaryIO.kt`.
- `InkBrushes.kt`: `creativeFamily(kind)` builds one `BrushFamily` per kind from particle tips (`setParticleGapDistanceScale`), noise behaviors (`NoiseNode` into `TargetNode`), and for Glow two coats. No textures.
- `ui/editor/BrushTypes.kt`: the dropdown with a sample stroke per brush; `InkPanel.kt` shows it for the pencil tool; strings in English and es-419; `EditorIcons.Brush` replaces the pencil icon in the toolbar strip.

Known problems to fix first:
1. `InkBrushes.kt` is missing `import androidx.ink.brush.BrushBehavior` (the compile error).
2. `:core:test` reported 1 failure after the format bump. It was not read yet. It is most likely a test that checks the format version or the wire enums in `CodecTest` (for example the "newer version is refused" test, which now needs version 4, or the "every enum value has a wire code" test, which should also list `BrushKind`/`BRUSH_KINDS`). Change the test, never the golden bytes.
3. The `TargetNode`/`NoiseNode`/`SourceNode` constructor signatures were taken from `javap` output and are untried. The ink library's jar is under `~/.gradle/caches/9.6.0/transforms/*/transformed/ink-brush-api.jar`.
4. The look of each brush is a guess and must be tuned on the tablet with the owner. Say so plainly; they can compare against the screenshots.
5. Old pencil strokes (tool PENCIL, no brush) read as `BrushKind.PENCIL`, which is a new look from the stock marker they used before. Accept that, or keep a legacy path.

## Task 2: File & Doc accepts PDF and MP3
"Add to Note" (`ui/editor/SourcesColumn.kt`) has a File & Doc card that calls `sources.pickFile`, which today picks pictures only. The owner wants PDF and MP3 too.
- MP3 (audio): reuse the recorder's import, `state.importAudio(uri)` (`RecorderState`/`CanvasView.importAudio`), which already makes an `AudioItem`.
- PDF: the library already imports PDFs as documents (`PdfImporter` via `LibraryRepository.importPdf`). Decide with the owner whether a PDF added to an open note becomes extra pages in it or a new document linked as a source. Ask before building.
- The card's text ("Pictures from your files") and es-419 string need updating.

## Task 3: draggable recording bar on the canvas
Recordings stay in the microphone menu list (correct as built). The owner wants to tap and drag one from that list onto the canvas, where it becomes an audio bar (no Notewise reference; the owner described it):
- A small bar with a play/stop button and a stylized waveform histogram that fills as it plays. The playback point can be dragged along it.
- A "v" at its top right collapses it to only the play button. Pressing play expands it. When collapsed, a ">" next to the button opens it without playing.
- Offline only.
Model facts: `AudioItem(id, frame, assetId, durationMs)` already exists; recordings have a zero-size frame today, which means "not placed" (`CanvasView.recordings()`, `ItemPainter` paints nothing for `AudioItem`). To avoid another format change, the collapsed state can come from the frame width (for example open when `width > 2 * height`). Needs: a drag from `RecordingRow` in `Recorder.kt` to a drop point, a waveform computed from the audio file (decode with `MediaExtractor`/`MediaCodec` into about 100 amplitude buckets, cached per asset), a seekable `MediaPlayer` (`RecorderState.play` has no seek), and drawing the bar over the canvas (a Compose overlay positioned from the viewport like the video player, or `ItemPainter`). Hit test and erase for `AudioItem` already use its frame.

## Open owner confirmations
- Eraser "Auto deselect": it does nothing, because changing tool already clears the selection. I proposed removing the switch; unanswered. Leave it.
- The icon "next to the eraser, a line with a square through it": I guessed it is the tape; unanswered.
- Tabs in the sources column, and the Canvas card in "Add to Note", say "not built": linking a second canvas needs multi-document tabs. Not requested yet.
- Scroll and layout and Settings in the "..." menu both open the settings screen.

## Gotchas
- Wet ink draws above every Compose control: wrap new controls with `masksInk(state, key)`.
- `ui/editor` sizes are in screenshot pixels; `EditorChrome` rescales by 2.5/2.75.
- Shell: Python heredocs with quotes can break the Bash tool. Write patch scripts to a file and run them.
- The Pad 7 shows an install prompt: the owner must tap Install.
