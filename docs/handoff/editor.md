# Editor UI and tools: handoff

Branch `ws6-editor` in `D:\code\better-notes-ws6` (a clone, not a worktree). It has main (e051d17) and integration merged in. It is not merged into `main`. Reference screenshots are read from `D:\code\better-notes\reference\notewise\`.

## State
Builds, `:core:test` and lint pass (lint: `DataExtractionRules`, `DrawAllocation`, `MissingApplicationIcon` fixed on main since). On an emulator at the Pad 7's size and density, 134 of 137 on-device tests passed before the last merge: all of `CanvasViewTest` (31). The 3 failures are in `LibraryScreenTest` (not re-checked: no emulator or `adb` on PATH this round). A trial merge of `main` (09f0863) into this branch has no conflicts (`git merge-tree`), but I did not build the merged result (text input in a wiped emulator), not in editor code, but I did not rule out the merge. Nothing has run on the Pad 7: no latency or frame numbers.

## Done (all in `app/.../ui/editor`, `ui/theme`, `ui/icons`, `canvas/`, `:core tools/`)
Toolbars and tool rows from the screenshots, dark and light. Pen, pencil, highlighter with panel, laser (tail, no ink), ruler (two fingers, strokes follow its edge), eraser, lasso (Word-style handles, move, resize, snap to align), shapes (two pages), tape, emoji, sticky note, text, table, images (camera, gallery, files, paste), video, recorder, loupe, all pages with drag to reorder, page and zoom readout. Strings in `values` and `values-b+es+419`. Theme comes from `LibraryPreferences`.

## Left
- Insert: the sidebar's Insert entry now selects the picture tool and opens its panel (`insert screenshot or image.png` shows that same panel: camera, gallery, files, scan, recent pictures). Assumption: Notewise's Insert is that panel; the owner has not confirmed. The fourth button (scan or screenshot) still says "not built". Search has no screenshot.
- Quick switch (stack button) is built from `quick navigation (between notes).jpg`: recents list, tap opens the note. Pinning and the per-row "..." menu are not built (no screenshot of them); the info icon and "..." say "not built". Not run on a device.
- `EXTRA_START_ACTION` (`START_TYPING`, `START_RECORDING`) is not read in `MainActivity`.
- `canvas.onToolAction`: stylus buttons and shortcuts send tool actions that the toolbars should act on (PEN, ERASER, LASSO...). Not wired.
- Language: the picker is the library's; check the editor follows it.
- Laser panel (`LaserPanel.kt`) is built from `laser options.png`: Dot/Line/Delay, length 1-5, colors. Not persisted across restarts, the swatch list icon is absent, the length slider ticks are evenly spaced (the screenshot's are not), and it has not been seen on screen.
- Saved but inert: scribble to erase, circle to lasso, stabilization, locked-object switches, auto deselect, take screenshot.
- Scan, left-pill chevrons, selecting several pages, the swatch list. Tape, emoji, video, shapes page 2 and the lasso handles were never looked at on screen (emulator froze); video playback and photo capture never run.
- Unverified: ruler with two real fingers; speed of the overlay views on the tablet.

## Gotchas
- `ui/editor` sizes are in screenshot pixels (2.5 per unit); `EditorChrome` rescales by 2.5/2.75. The library has its own scale.
- Wet ink draws above every view: each control calls `masksInk`, or ink shows through it.
- Format: new shapes and `TAG_STYLED_TEXT`, `TAG_STYLED_TABLE`, `TAG_VIDEO` were added without bumping `FORMAT_VERSION`; new enum values go at the end of their lists.
- Recordings and videos live in the document's `assets/`. A recording has no mark on the page.
- `ItemPainter` handles every item but ink. PDF and export use `PagePainter` through `PaintSource`; anything new it needs goes on `PaintSource` (`image()` is there).
- Emulator: my AVD `Ws6_Pad7` (port 5580) froze once with other emulators running; only one at a time is safe on this 16 GB PC. Drive it with `adb -s`.
- Don't use the Pad 7 (`f9ea56c5`) without asking the coordinator.
