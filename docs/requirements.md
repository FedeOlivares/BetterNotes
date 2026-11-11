# Requirements and owner decisions

## Product
- "Better Notes": stylus-first note-taking and PDF annotation app for Android tablets, modelled on Notewise for Android.
- All features are offline and local.
- Branding: the implementing agent decides, unless the owner specifies otherwise.
- UI must be identical to Notewise (see `docs/ui-reference.md`).

### Performance limits
Maximums, not guarantees (owner, 2026-11-04). The app asks for the most the device offers and must work well at lower rates, which the system can impose at any time.
- Up to 144 Hz screen refresh rate
- Up to 360 Hz general touch sampling
- Up to 240 Hz stylus sampling

Nothing may assume a rate. Time things by the clock and by event timestamps, never by counting frames or samples.

## Owner requirements

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

## Owner decisions

### 2026-11-03
- **Undo gesture:** two fingers. A two-finger double tap undoes and a two-finger triple tap redoes. Undo must wait out the triple-tap window before firing.
- **Handwriting languages:** English and Spanish.
- **Online features:** nothing online at all: no cloud, sync, sharing, AI or account. Leave out the cloud button, avatar and people filter.
- **Distribution:** personal use. Still do not copy Notewise's assets.
- **Themes:** light and dark, with dark as the default. No light-mode reference screenshots exist.

### 2026-11-04
- **Handwriting model download:** a one-time download of the ML Kit Digital Ink models for English and Spanish (about 20 MB each) is accepted. It is the only exception to "nothing online" and needs the internet permission, which the app does not request yet.
- **Greeting:** the app asks for a name only, and the library greeting uses it.
- **New items:** "Text" makes a typed notebook. "Canvas" makes a drawing canvas that is not infinite. "Link" is left out.
- **Bottom pill:** its asterisk and grid icons were online features (AI, layout market), so only home stays.
- **Inside a folder:** the same as the home screen, with a house and the folder's name in place of "All".
- **Language:** the UI is English or Latin American Spanish, selectable.
- **`minSdk`:** stays at 35. The owner's tablet runs Android 16 (API 36), which meets it.

### 2026-11-10
- **Handwriting models ship with the app:** the one-time download is replaced by bundling the English and Spanish models in the APK (about 24 MB). Nothing online is left. How it works: `docs/handwriting-models.md`.

## Still to verify
- ML Kit's docs imply recognition runs from the downloaded model, but don't explicitly say it works offline. Test recognition with no connection once the models are downloaded.

## Notewise behavior (help site, help.notewise.dev)
- By default, one finger scrolls and the stylus draws; a toolbar Finger Mode button changes this.
- Some Xiaomi devices intercept three-finger gestures at system level, so avoid relying on them.
- Pen settings include Stabilization and a Latency Optimization level.
