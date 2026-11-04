# Handoff: library and integration

## Where things are
- `main` is at `0f00133`, plus the commit adding this file. It holds the foundation, `:core`, canvas, PDF (`ws4-pdf-ws7` 99e5aad), settings (`ws7-settings` 733033f), library (`ws5-library` 5cd890f, then `integration`) and the icon (d3a7160).
- `ws6-editor` and `ws3-canvas` (637b609) are not merged.
- `integration` is checked out in the worktree `D:\code\better-notes-ws5`. The Editor thread was merging onto an earlier commit of it, 5595bb3.

## Done
- Home screen: tiles, thumbnails, folders, stars, tags, bin, title search, selecting, grid and list.
- "+" panel from `add new note.jpg`, without "Link". Text makes a typed notebook (`DocumentKind.TEXT`, `TextNoteActivity`). Canvas makes a paged drawing note.
- The circled "..." menu from `... on home.jpg`, plus a Settings row.
- A house and the folder's name replace "All" inside a folder. The greeting asks for a name once.
- Language (English, es-419), theme and name are in Settings > General. Library and settings text is translated.
- `ui/settings` now uses `ReferenceScale`.

## Tested
- Full device suite on a 2136x3200, density 440, API 37 emulator at e8cdd0c's code before its last text-note change: 99 tests, 0 failed, 4 skipped (`LibraryDemoSeed`, 3 in `HandwritingSearchTest`).
- After that change and the merge of `main`: unit tests, lint (0 errors, 1 warning), debug and release builds, and the 19 library and thumbnail device tests all pass. The rest of the device suite was not rerun.
- Not tested: anything on the Pad 7, library speed, the real camera and photo picker, a real language switch through the settings row (only `cmd locale set-app-locales`), and files from real-world sources.

## Left to do
1. Merge `ws6-editor` once the Editor's merge onto `integration` is done. Expect conflicts in `MainActivity`, `CanvasView` and `PagePainter`.
2. Merge `ws3-canvas`. It touches `MainActivity`, `PerfProbe`, `CanvasBenchmark`, `CanvasViewTest` and `measure-device.ps1`.
3. After ws6: make `MainActivity` honor `EXTRA_START_ACTION` (`START_RECORDING`, from the Audio tile), give `ThumbnailRenderer` the editor's item painting through a `PaintSource`, and translate the editor's strings.
4. Ask the owner where an infinite canvas should be created. The "Canvas" tile no longer makes one, and nothing else in the library does, yet the requirements still list it.
5. Run the library on the Pad 7 (`f9ea56c5`; check first that nobody is measuring) and measure opening and scrolling.
6. Screenshots still missing for: tiles' "..." menus, the search field, the bin, the typed notebook and selecting. These are plain stand-ins.

## Gotchas
- A popup or dialog starts a new composition with the device's density, not `ReferenceScale`'s. See how `OptionsMenu` hands the scale in.
- Every new string needs a copy in `values-b+es+419`. Lint fails the build otherwise.
- `ReferenceScale`'s floor must stay below the Pad 7's scale. At 0.75 it enlarged everything by 3% at density 440.
- Test at density 440: density 360 hid that bug.
- Don't run an emulator from a command piped into `tail`: the pipe stays open and the command hangs. My AVD `Ws5_Pad7` is deleted; each thread needs its own (see the `parallel-thread-setup` memory).
- `gradlew connected...` runs on every connected device, so set `ANDROID_SERIAL`.
- Typed text goes through `ItemsEdit`, applied once per save, so undo doesn't grow per letter.
- `reference/notewise/` is git-ignored, and clones don't have it. Read it from `D:\code\better-notes`.
