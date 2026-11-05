# Release 1.0: what the last round added, and what to know

Version 1.0 (`versionCode 100`). Later requests count as 1.1. Rules are in `CLAUDE.md`.

## Added in the final round (this thread)
- **Brushes** (Pencil, Oil, Spill, Watercolor, Comics, Scratches, Stripes, Dots, Single dots, Leaves, Crosses, Glow): `canvas/InkBrushes.kt`.
  Pencil, Watercolor, Oil and Leaves are stamped pictures cut from the reference strokes (`app/src/main/assets/brush_textures`, made by
  `tools/extract_brush_textures.py` from `reference/notewise`, which is not in git). Stamps turn with the pen but can only be drawn on a hardware
  canvas, so thumbnails and exports draw a plain stand-in (`InkBrushes.draw`). The ink library allows at most 10 layers per brush.
  `BrushGalleryTest` renders every brush to a PNG on a device: `adb pull /sdcard/Android/data/app.betternotes/files/brushes.png`.
- **Recording bar**: drag a recording from the microphone menu onto the page. Hold it to move it. Waveform in `audio/Waveforms.kt`.
- **Lasso**: circle to lasso uses the lasso options ("Full selection only", "Select anything touched"), animates as marching ants, and hands the
  tool back when the selection ends. A finger tap works on the circle and on the selection's buttons.
- **Undo** by two-finger double tap fires on the second tap, with no wait.
- **Color bar**: more favorites, remove, rearrange, accept and discard (up to 12 colors).
- **Tape**: patterns (dots, lines, crosshatch), a shadow, thickness and opacity, always/hold/never straight, and tape that follows the pen
  (`TapePathItem`, stripes bend with the path). Straight lines (pen hold-to-line, line and arrow shapes, straight tape) show an angle tool
  and snap within 1.5 degrees of 0, 45, 90 and on (`core/tools/AngleGuide.kt`, `canvas/AngleGuidePainter.kt`).
- **Bubbles**: the tail of a speech or thought bubble can go all the way around.
- Page files are at format version 4. `CodecTest` golden bytes are unchanged.

## Known gaps
- Nothing here was run on the Pad 7 by the author of this note; checks ran on the emulator (`check.ps1`, `connectedDebugAndroidTest`).
- The `twoFingerTapsUndoAndRedo` canvas test may still assume that undo waits (the perf branch was fixing it).
- Exports and thumbnails draw plain stand-ins for the stamped brushes.
- Recording bars and tape that follows the pen are not drawn into thumbnails.
