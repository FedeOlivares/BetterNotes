# Better Notes

Better Notes is a stylus-first note-taking and PDF annotation app for Android tablets. Write, draw, annotate PDFs, plan your year, and keep your study material together in notebooks, all fully offline: no account, no cloud, no sync and no sharing service. The handwriting-recognition models (English and Spanish) come inside the app, so nothing is ever downloaded.

The app is available in **English and Spanish**. It is built for a Xiaomi Pad 7 (Android 16) and written in Kotlin: Jetpack Compose for the screens, a custom `View` for the canvas, and a pure-Kotlin `core` module with unit tests. It needs **Android 15 (API 35) or newer**.

See [RELEASE_NOTES.md](RELEASE_NOTES.md) for the changes in each release.

## Contents
- [Getting started](#getting-started)
- [Writing and drawing](#writing-and-drawing)
- [Working with pages](#working-with-pages)
- [Adding content](#adding-content)
- [Selecting and arranging](#selecting-and-arranging)
- [Covers](#covers)
- [PDFs](#pdfs)
- [Search and handwriting recognition](#search-and-handwriting-recognition)
- [Page templates and custom layouts](#page-templates-and-custom-layouts)
- [The planner](#the-planner)
- [Sources: a binder for each notebook](#sources-a-binder-for-each-notebook)
- [Split-screen editing](#split-screen-editing)
- [Exporting and sharing](#exporting-and-sharing)
- [Languages](#languages)
- [Customizing the app](#customizing-the-app)
- [Performance and design](#performance-and-design)
- [Privacy](#privacy)
- [Known limitations](#known-limitations)
- [Building](#building)
- [Project layout](#project-layout)

## Getting started

### Your library
Opening the app shows your notebooks and PDFs as tiles: the name and a menu on top, a picture of the first page or the cover, and the date underneath. Switch between a grid and a list, sort, search, and choose a light or dark theme (dark by default). The home screen greets you by name.

- **Folders** can be given a color, an opacity and an optional preview of the files inside. Drag a tile into a folder, between tiles to reorder them, or onto the strip at the top to take it out of a folder.
- Select several tiles to move, tag or delete them. Deleted items go to a bin.
- The **+ button** starts anything: Create Notebook, Text (a typed notebook), Canvas, Audio, File & Doc (PDFs, audio, pictures and notebook files), Take Photo, Scan, Photos and Folder.

### Creating a notebook
The New Notebook dialog, shown over a blurred home, lets you choose:
- a **title**,
- a **cover** (about fifty designs, a blank custom cover, or the planner),
- the **paper**: a template and a paper color,
- **portrait or landscape**,
- the **language** of the words printed on templates.

A notebook is either paged, with resizable pages (A4, A5, Letter, Legal or your own size in px, cm or inches), or an infinite canvas with no edges.

### The basics of writing
- The stylus draws and one finger scrolls. Two fingers zoom (up to 1500%) and pan. A finger-draw mode lets your finger draw instead.
- Zoom starts at real size: 1 cm drawn on an A4 page is 1 cm on the screen.
- A two-finger double tap undoes and a two-finger triple tap redoes. A one-finger double tap zooms to 200% or back to real size.
- The stylus's eraser end erases, and its buttons can be given tool actions.

## Writing and drawing
- **Pen, pencil and highlighter.** Each has five colors that you can change, a custom color and a color wheel, three line widths with fine-tuning, opacity, a round or square tip, and a hold-to-draw-a-straight-line timer (on the highlighter, a gentle bend becomes a straight line).
- **Pen types and brushes.** Natural, ball point and calligraphy (a flat nib) pens, and creative brushes with their own textures: pencil, oil, spill, watercolor, comics, scratches, stripes, dots, leaves, cross, glow and single dots. Highlighter strokes of one color do not darken where they cross when "no overlap" is on.
- **Pressure sensitivity** for every pen and brush, with a curve and a per-tool switch, and **stabilization** with 11 levels (0 to 10).
- **Eraser.** Partial or whole-stroke erasing, a tip size slider, an option to include locked items, and "clear page" (on an infinite canvas it clears only what is showing).
- **Shapes.** Lines, arrows, rectangles, ellipses, triangles, diamonds, polygons, stars, speech bubbles, solids and more, with fill, line width, dotted or dashed lines, corner radius and tips, and mirroring.
- **Shape recognition.** Hold the pen still at the end of a stroke and it becomes the closest ellipse, rectangle, polygon (3 to 8 corners), arrow or smooth curve, with handles to edit it. A plain straight stroke straightens, and complex curves are smoothed.
- **Tape.** A translucent tape with patterns and a shadow, laid straight or along a path.
- **Paint bucket.** Fills a closed area of drawing, a sticker or a post-it with a color, at screen resolution, with opacity and an option to return to the previous tool after a delay.
- **Helpers.** A laser pointer, a ruler over the canvas, a loupe to write small in a magnified window, and a color picker.
- **Every number in a tool panel** has a minus, a slider and a plus.

## Working with pages
- **All pages.** See every page in a grid, rearrange them by dragging, add or delete a page, and jump with a quick page switcher that lists thumbnails or by typing a page number.
- **Scroll layout.** Vertical, left to right, or right to left. A button after the last page adds a new one.
- **Room to work.** Half a page of extra space before the first page makes the top of a page easy to reach.
- **Rotating a page.** Turn a page with two fingers. It starts after a deliberate twist of more than 20 degrees and keeps whatever angle you leave it at; the rotation badge resets it upright.
- **Cover as page 0.** A notebook with a cover opens on its first page, and the cover counts as page 0 in the page counter, page navigation, search results and exports.
- **View only** mode, from a corner button, disables editing and hides the tools.
- **Infinite canvas** with a map, and a setting to continue from the last page you used.
- **Typed notebooks** (Text) hold typed text.

## Adding content
- **Pictures** from the gallery, the camera (a dedicated photo button) or a file, with a border, rounded corners and shapes. **Scan** a document by taking a photo and setting its corners. **Videos** too.
- **Text boxes**, including **handwriting while typing**: write by hand in a text box and it turns into typed text after each word, with the ink flying to its place. Text boxes follow the page when you zoom or pan. Tap a box to select it (move, resize, rotate); tap it twice to type. Lists can start lines with a dash, star, bullet, numbers, letters, roman numerals, an arrow or a colored dot. Select some letters and the size, bold, italic, underline and strike buttons change just those letters.
- **Fonts.** Five fonts come with the app (Roboto, Open Sans, Lora, Caveat and Roboto Mono, under the SIL Open Font License). In Settings, add your own from a .ttf or .otf file, or **draw your own font**: draw each letter on a pad, name the font and save it as a .ttf you can reopen and edit. A text box's Font choice lists every font in its own typeface and can change just the selected letters. Fonts are not stored in a notebook, so a notebook shared to another device needs the same font files added there.
- **Tables** with dividers you can drag (double tap a table), buttons to add or remove rows and columns, and colors for lines, fill and header.
- **Sticky notes (post-its).** Smaller when closed; whatever is typed or drawn in one stays in it and folds away with it. Fills and erasing work inside them.
- **Stickers and emoji.** Save any selection as a sticker, pick from a Stickers tab next to Emoji, import packs from folders of PNGs, and place them by tapping or by holding and dragging. Drop one sticker on another to make a set.
- **Audio recordings.** Record in the app or add a file; a bar on the page has a play button and a waveform you can drag.
- **A clipboard list** that also takes photos copied from other apps.

## Selecting and arranging
- **Lasso** (freeform or rectangle) with filters for which kinds of items to catch, handles to resize and rotate, and snapping to align.
- **Buttons under the selection:** delete, copy, save as a sticker, and a "..." bar. The buttons always stay on the screen, even when the selection is near the edge of a page.
- **The "..." bar** locks or unlocks items (locked items form a layer below the rest), recolors post-its, sets a picture's opacity, and puts the selection at the **front, middle or back** of the page's items.
- **Layers.** Post-its are always at the bottom of a page, except on a cover, where they can be raised above other items. Drawing, stickers, post-its and the canvas are layered for the paint bucket.
- **Groups.** Group a selection to move, resize and rotate it as one; Ungroup splits it again.
- **Layers.** Each page can have several layers: only the working layer is editable, and layers can be hidden, renamed, reordered, merged, deleted, and items moved between them.
- **Back to the previous tool.** After a shape, post-it, table or picture is made, tapping away brings back the tool you had before (switch and delay in each tool's panel).
- **Undo and redo** go step by step through everything you do.

## Covers
- About fifty designs in seven groups (Simple, Solid, Enclosed, Black and white, Brown, Plain and Bright), all drawn in the app, and a blank **Custom** cover.
- **Change the colors** of a design from the circles under the preview, with the result shown at once, before the notebook is made.
- **Edit a cover** on a canvas of its own: every fill, line, dot and piece of text is an item you can select, recolor, move and delete. **Edit design** does the same to the cover of an existing notebook.
- **Add or edit a cover at any time** from a notebook's "..." menu, or with the blue **Add cover** button that slides out from under the toolbar when you scroll above the first page.
- The cover is page 0: you can draw and write on it like any page, but it is locked into the notebook's page structure (it cannot be deleted, moved or resized). Choose portrait or landscape for it.

## PDFs
- Import a PDF as a notebook, or open one from a file manager or another app with "Open with". It is copied into the app, and an encrypted file is stored decrypted (the password is never kept). Page sizes are the PDF's own.
- Annotate with every tool. Pages open without waiting for the renderer: a preview first, then sharp tiles nearest the view.
- Search the PDF's printed text next to your handwriting. Blank pages can be added to a PDF, and a PDF can have a cover.

## Search and handwriting recognition
- Search finds handwriting and typed text, ignoring case and accents and tolerating a letter or two of difference. Results are listed by page and marked on the page.
- Handwriting is recognized on the device with ML Kit Digital Ink, in **English and Spanish**. The models come inside the app (about 24 MB), so handwriting search works right after install. Only the lines that changed are re-read, after each save.

## Page templates and custom layouts
- **Lined, dotted, grid and hexagonal** pages with adjustable density and line width, and a paper color.
- **Layouts:** Cornell (two kinds), legal, engineering, music score, guitar tab and guitar score, and printed pages: semester overview, mind map and week planner, in English and Spanish.
- **Custom layouts:** set the size in px, cm or inches, then draw lines, rectangles, tables and ruled areas, choose a paper color and a base pattern, and use book-style (mirrored) pages.
- Apply a template to the current page or to all pages, and change the page size (a PDF page's size cannot change).

## The planner
Create a complete planner from the New Notebook dialog, below the covers: nothing else to decide, as it brings its own cover, paper and size.

- **Pages:** an interactive index dashboard, a year calendar, monthly, weekly and daily pages, semester overviews and course pages, exam preparation, a group project planner, a grade tracker, study sessions, assignments, a yearly overview, a meal planner, routines, a habit tracker, goals, a to-do list, and blank, dotted, lined and grid pages, all in one visual style.
- **Navigation** is built into the pages: tabs along the edge (shaded and joined to the page of their month) and links on the dashboards and calendars take you straight to a section.
- **A calendar you can move.** Change a date's weekday from a menu and every date before and after shifts with it. The cover chooses an academic year (for example July 2026 to July 2027) or a full calendar year and sets the weekday of every date. A new planner starts as the full calendar year, or as the next year from December 1st. Enter your semester dates once, typed or handwritten, and they appear on the index and the semester page. The cover's title is editable by tapping it.
- **Colors.** Choose the cover, spine, stripe and page colors when you make a planner, or change them later by tapping the years on its cover. Every page follows the page color, and one undo brings the old colors back.
- Available in English and Spanish ("Agenda"). Pages are drawn as you approach them, so a planner of hundreds of pages opens fast.

## Sources: a binder for each notebook
The Sources panel works like a binder. Its + button adds complete resources to the notebook instead of items to the canvas:
- other canvases,
- images (each on a canvas sized to it),
- PDF documents,
- audio files,
- other notebooks from your library.

Tap a source to open it. The list is the same from every source, so you can always get back. Adding images, audio and so on from the toolbar still puts them on the current canvas.

## Split-screen editing
Work in two editor panes at once, side by side or stacked. A draggable divider sets the space for each pane, and dragging it to an edge closes a pane. Copy a selection from one pane into the other. It is useful for referencing another notebook, comparing notes or working with source material while you write.

## Exporting and sharing
- **PDF.** Vector export with a scale, an invert option and a "rasterize" option.
- **Images.** PNG or JPEG, 300 dpi by default.
- Choose the pages by tapping thumbnails or typing a range such as "2-5, 8". Imported PDF pages become 300 dpi images in an exported PDF, so their selectable text is lost.
- **Whole notebooks.** Export a notebook as a single `.bnote` file with its editable drawings, text and other items, its images, PDFs and audio, its sources and linked notebooks, its cover and its language. To open it on another installation, open the file from a file manager or another app (it opens in Better Notes), or use the home **+** menu and **File & Doc**. It is added as a new notebook, even if you import the same file twice, and a file that is not valid or comes from a newer version is refused.

## Languages
- The interface is in **English or Latin American Spanish**, chosen at the top of Settings.
- Each notebook has its own **template language** for the words printed on its built-in templates. It starts as the app's language, and changing the app's language does not change existing notebooks. Change one notebook from its "..." menu, or choose it when you create the notebook.

## Customizing the app
- **Toolbar.** Move it to any edge (top, bottom, left or right) by holding it, show a grid overlay with a center cross and thirds, keep up to ten tools visible, group, reorder and hide tools, or restore the defaults.
- **Phones.** On screens under 600 dp wide the toolbars run along the bottom, the home screen has two columns of tiles and sheets are full width.
- **Stylus and fingers.** Tap timings, two-finger gestures, stylus button actions, and the two-finger rotation.
- **Keyboard shortcuts** for every tool and action, all changeable.
- **Canvas.** Continue from the last page, keep the screen on, hide the system bars, snap to align, and screen calibration so zoom 100% is true size.
- **Notes.** How new notes are named, and auto naming. A Pomodoro timer. Fonts, and the storage the handwriting models use.

## Performance and design
- Built for the Pad 7 and its stylus: the app asks for the highest refresh and touch rates the device offers (up to 144 Hz screen, 360 Hz touch, 240 Hz stylus) and never assumes one. Everything is timed by the clock and by event timestamps. Ink uses `androidx.ink` with motion prediction and unbuffered input.
- Finished content is drawn as 512 px GPU-backed tiles, redrawn with a margin so a line near a tile edge is never clipped. Each page is its own file, so a long notebook only loads the pages near the view.
- Files are versioned: an older notebook always opens in a newer app, and a newer one is refused rather than damaged.

## Privacy
Everything stays on the device. There is no account, analytics, cloud, sync or AI, and the app does not use the network: the handwriting models are inside it. An `offline` build type without the internet permission is also available. Notebooks live in the app's private storage, and files you export go where you choose.

## Known limitations
- Opening a `.bnote` file or a password-protected PDF from outside the app (Open with) has not been tried on a tablet; Home → + → File & Doc always works.
- There is no configurable handwriting pause setting.
- Scanned PDFs cannot be searched, as there is no OCR, and links and forms in a PDF do nothing.
- The Celebration group of covers is missing, and the other covers are close redraws rather than exact copies.

All features were tested by the in-house dev team, but because of a limited set of tablets, the app may behave unexpectedly on yours. Please let us know by opening an issue here on GitHub if that happens. Some improvements, including cover menus and selection behavior during scrolling, have not yet been fully tested across all supported tablets.

## Building
Windows, Android Studio's bundled JDK and the Android SDK. `java` is not on `PATH`, so set `JAVA_HOME` (for example to the Android Studio `jbr` folder) before calling `gradlew` directly.

```bash
# all checks: tests, lint, debug and release builds
./scripts/check.ps1

# unit tests of the pure-Kotlin core
./gradlew :core:test

# a debug APK
./gradlew :app:assembleDebug

# tests that need a device or emulator
./gradlew :app:connectedDebugAndroidTest
```

- Time anything on the `profile` build type, never debug. `scripts/measure-device.ps1` measures ink latency and drawing speed on a device.
- A signed release build reads its key from a git-ignored `keystore.properties`.
- With more than one device attached, set `ANDROID_SERIAL` so Gradle does not install on all of them.

## Project layout
- `core/` is pure Kotlin with JVM tests: the document model, geometry, undo and edits, tools, gestures, templates, covers, the planner calendar, search, export and the file formats.
- `app/` is the Android app: the canvas and ink, the editor, the library, settings, PDF, export and search.
- `docs/` holds the product requirements and the device, environment and UI reference notes.
