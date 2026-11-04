# UI reference (Notewise, dark mode)

Three owner screenshots exist in `reference/notewise/` (1.jpg, 2.jpg, 3.jpg). They contain personal notes and a profile photo. `reference/` is git-ignored so they stay out of every repository; a fresh clone will not have them.

Colors below are descriptions by eye; none have been measured. Icon meanings marked "(?)" are unconfirmed.

Also available: Play Store marketing images and the help site at help.notewise.dev. Treat the marketing images as approximate.

## Library
- Near-black background with a subtle blue dotted glow along the top.
- Large serif greeting ("Good evening, [name]") with a smaller sans-serif subtitle.
- Top right: a cloud-status button, and a pill holding search and the avatar. (Cloud button and avatar are omitted in Better Notes; the app is offline.)
- Filter row: a blue "All" chip, then star, tag, people and bin icons; a circled "..." at far right. (The people filter is omitted.)
- Portrait layout is a 3-column grid of rounded cards with a thin light border.
- Document card: dark header (date, title up to 2 lines, "..."), with the first-page thumbnail filling the rest.
- Folder card: solid blue folder illustration, "18 items" at top, "..." and the folder name at bottom.
- Bottom left: floating pill with home (selected, blue), an asterisk (AI?) and a grid icon (?).
- Bottom right: round blue "+" floating button.

## Editor toolbars
Dark floating pills sit over the canvas, with no full-width bar.
- **Left pill:** back, sidebar panel, page grid, page stack (?), up/down chevrons (?).
- **Center pill:** undo, redo, finger mode (hand with arrows), then a horizontally scrolling tool strip, then a divider and a collapse chevron.
  - Visible in the strip: table, an unidentified tray icon, sticky/add (?), loupe (?), ruler, microphone, laser pointer (?), settings.
  - A partly hidden icon at the left edge suggests more tools are scrolled out of view, likely including the pen.
- **Right pill:** eye (view-only, shown in a lighter circle) and "...".
- **Second-row pill (active tool):** line type, 3 widths, 5 color circles, add color, edit color, sliders button. Dividers separate the groups. A small blue dot marks the selected width and color.

## Pen panel
A dark rounded panel with these parts, top to bottom:
- Title "Pen" and a circular close button.
- Stroke preview on a dotted background.
- Three type cards: Natural, Ball, Calligraphy. The selected card has a blue fill and border.
- Thickness: value pill, then a slider with minus and plus buttons.
- Line type selector.
- Toggles: Hold to draw shape (with a "..." for options), Scribble to erase, Circle to lasso.
- Stabilization value.
- Color: add button, list button, a row of swatches with the selected one ringed, and a color wheel.

## Editor canvas
- The screenshots show a cream dotted page.

## Screenshots still needed
Without these, those screens can't be matched. Ask the owner rather than inventing them.
- Eraser, lasso, shape, image, sticky note, table, ruler, loupe, recorder and laser pointer states
- Page grid and quick page switcher
- Sidebar, "..." menus and the add-page flow
- Search results
- New note/import flow
- All settings screens
- The template picker and custom layout builder
- Lasso selection handles
- Toolbar scrolled to show the pen, pencil, highlighter and eraser
