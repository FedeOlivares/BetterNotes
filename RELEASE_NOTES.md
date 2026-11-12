# Better Notes 2.5.0 — Release Notes

Text colors, one color picker everywhere, steadier gestures around text and post-its, and a cleaned-up code base.

- Text colors: the text row has color circles (black, blue, white, and a + for the color wheel). The color follows the selection: single letters, the whole box, or the next text you type. A text box's background color is in the text tool's settings.
- Writing in a text box: scribbling over typed letters rubs out only those letters. Tapping away in the middle of writing lets the last words land, then closes the box, and the tool in use before (or the pen) comes back. Double tapping opens a box for editing without its handles, and typing again drops the handles. While a box is open the pen always writes, including in the strip around the box that used to be dead.
- Undo: each block of handwriting you write is one undo step (after a 1.5 second pause), and keyboard typing is grouped the same way. Undo and redo no longer stop at a post-it opening or closing.
- Color pickers: every color wheel is one sheet with a hex code field and Cancel and Done. Every fixed set of colors ends in a + circle that opens it: shape and tape colors, post-it colors, folders, covers, planner colors, paper colors, the layout builder and the template sheet.
- Selecting: a loop with the lasso round the hanging part of an item that sticks out of a post-it grabs the item. A cover's background is selected only by holding on it, never by a loop. Circle to lasso works over post-its. The post-it's close corner is 60 percent bigger.
- Finger in scroll mode never draws: with something selected and unlocked it moves or resizes the selection, and otherwise it moves the page (a locked selection moves the page).
- Tape returns to the previous tool (on, 1 second by default), and every tool that gives the earlier tool back goes to the pen when there is none.
- Code: the colors, shapes, text sizes and spacing of the app are now defined in one place. This was meant to change nothing you see.
- Notebooks that use a text box background color are saved at page format 16, and those with per-letter colors at 17. A page uses them only when it needs them, other pages keep their bytes, and older notebooks open as before. Older versions of the app cannot open pages saved at 16 or 17.
- Known limits: handwriting written from right to left is not recognized as text (a limit of the recognizer), and closing a post-it that folds items into it still reopens when undone.
- Tried on the Pad 7 for this release: the fix for text drawn twice behind an open box, and holding to select a cover's background. Both worked.
- Not hand-tested on the Pad 7: holding to select a locked item, a tiny loop over an overhanging item, moving an image with a finger, a finger tap away from a selection, the return to the pen for the highlighter, eraser, lasso, text and sticker tools one by one, and opening a notebook with tape as the first tool. The rest of the text work was tried on the Pad 7 while it was made, and I did not repeat those checks for this release. The code cleanup has no visual check on the Pad. See the 2.4.0 notes below for what was and was not tested there.
- Still true from earlier: three connected tests fail on the Pad 7 and are not confirmed with a real pen (aStylusStrokeKeepsPressure, aSavedStrokeMeshesTheSameAsTheOneJustDrawn and holdingThePenStillStraightens), and the lint error at PlannerMenus.kt line 112 remains.

---

# Better Notes 2.4.1 — Release Notes

A privacy notice, and nothing else.

- Settings has a new Privacy Notice page at the end, in English and Spanish. It says that the app works on your device without an account, with no ads, analytics, crash reporting or tracking, and shares no data with anyone; that notes, PDFs, images, recordings and settings stay in the app's private storage and are never uploaded or backed up; what the camera, microphone, notifications, exact alarm and photo access are used for; that the only use of the internet is the Download button under Handwriting, which fetches the handwriting models from Google through ML Kit and sends none of your notes; and a contact address for privacy questions.
- The handwriting models already come inside the app (since 2.2.0), so that Download button is only needed if the models are missing from the device.
- The normal build still holds the internet permission, through the ML Kit library. The `offline` build type does not.
- This is the 2.4.0 build that was tested and approved, with only the privacy page added. Everything in the 2.4.0 notes below, including what was not hand-tested, still applies.
- The privacy page was compiled (the release build passes) but not opened on a device. The existing lint error at PlannerMenus.kt line 112 is still there.

---

# Better Notes 2.4.0 — Release Notes

Groups, layers, patterns, lists, planner recolor and a calmer way back to your tool.

- Highlighter: holding the pen still at the end of a highlighter line straightens a gentle bend (as along a line of text) into a straight line. A clearer curve, circle or arrow keeps the highlighter's own opacity, width and color.
- Groups: select items and tap the group button in the selection menu to move, resize and rotate them as one. Ungroup splits them again. Lassoing one member selects the whole group, and a copy of a group is its own group. A group saved as a sticker is placed as a group.
- Sticker sets: hold a sticker and drop it on another to make a set with a heading (the sticker panel stays open while you drag). Drop stickers on a set's heading to add them, or on "Not in a set" to take them out. Imported sticker packs can be rearranged the same way. Exporting a sticker as a PNG opens the save picker on Pictures (afterwards where the last one went), and the notice names the file and folder.
- Layers: a Layers button in the toolbar gives each page several layers. Only the working layer can be drawn on, selected or erased. Hide (the working layer too; the eye is open while a layer is visible), rename, reorder (hold and drag), merge and delete layers, or move a selection to another layer from the menu. Hidden layers are left out of exports and thumbnails. Groups stay on one layer.
- Return to the previous tool: after a shape, post-it, table, sticker or picture is made, the tool you had before comes back (a switch and a delay in the tool's panel). The new item is not left selected. The highlighter, eraser, lasso, text and sticker tools have the same switch and delay (on at 0 seconds, except the highlighter and the eraser, which start off). New pens, brushes and highlighters start with circle to lasso, pressure and scribble to erase on, and a saved pen, brush or highlighter that had all three off gets them on once.
- Post-its: tapping one with the post-it tool selects it without opening its text; "Edit note text" in its menu opens it. Their open and close tap areas are half as large again. Opacity is set in the post-it tool panel (the old row in Settings is gone), and a selected post-it's color and opacity are in the second toolbar. A post-it paints everything in it, up to and past its edge. A folding post-it takes along what is over 80 percent inside it, and tape (straight or drawn) that is over 5 percent inside it. Lasso and circle-to-lasso inside an open post-it work on its own items in place, and select the post-it only when the loop crosses its edges; scribble erases inside it. These are the same gestures as on the canvas. Home previews show what open post-its hold. Holding on an item selects it.
- Tables: choose a header color when making a table, and change the line, fill and header colors of an existing one, with a cross to go back to the usual look. Shapes can be mirrored.
- Text lists: a text box can start its lines with a dash, star, bullet, numbers, letters, roman numerals, an arrow or a colored dot.
- Paint bucket patterns: a Patterns switch in the bucket's options fills with dots, lines, diagonal lines or crosshatch over the fill color, with a pattern color, density and size.
- Planner recolor: every part a planner draws has a role. With the lasso (or a circle), pick a button, heading, list rule or other element of a planner page and change its text, lines, background, shading or font from a compact bar, with a tab for the rest. "Apply to similar items" spreads the look, also when pressed afterwards, Reset puts it back, and Done gives back the tool the lasso was lent from. A heading's lines and its rows are picked and reset together. A planner's generated parts are still locked content: they cannot be moved or deleted.
- Covers: the lasso never picks a cover's locked background, alone or through a group, and new cover backgrounds come locked. Home tiles now show post-its (with their ink and pictures), tables, tape and videos placed on a cover.
- Tapping away from a selection gives back the tool you had before. A sticker with a pattern fill placed in a post-it could fail to save; it now saves (at page format 15) instead.
- Notebooks that use groups, layers, lists, table colors, mirrored shapes or pattern fills are saved at newer page formats (9 to 15) that older versions cannot open. Notebooks that use none of them are saved as before.
- What was tried: the owner hand-tested the highlighter and layers on the Pad 7, and cover thumbnails were checked over adb. Everything else was not hand-tested on the tablet, including the last batch of fixes: planner recolor with the lasso and Similar, the paint bucket's pattern picker and sliders, selecting by holding, the return-to-tool switches and the tool defaults, the reworked post-it folding and gestures, dragging stickers into sets, and sticker export. Groups, tables, mirroring and lists have written device test steps that were not run.
- Known test failures: three connected tests fail on the Pad 7 and are not confirmed with a real pen: aStylusStrokeKeepsPressure, aSavedStrokeMeshesTheSameAsTheOneJustDrawn and holdingThePenStillStraightens. They use injected input, so the app itself may still be fine. Done giving back the lent tool in the planner recolor has no automated test.

---
# Better Notes 2.3.0 — Release Notes

Planner colors and shared dates.

- The planner's colors can be changed, like those of any other cover: when making a planner, four color dots under its cover (cover, spine, stripe, pages) open the color grid; on an existing planner, tap the years on the cover and use the Colors row. The pages' color is the blue of the rules, boxes and headings, and every page follows it. One undo step brings the old colors back.
- Handwriting in a semester or quarter start or end date blank on the index or on the Semester / Quarter planning page now shows on the other page too. The eraser removes it from both, and Clear in the date picker clears the typed date and the handwriting of that date. Each blank keeps up to 40 strokes, drawn at a constant width. The start and end blanks on the Semester overview page are still separate.
- Planners with colors or handwritten dates use a newer notebook file format (document version 7), which older versions of the app cannot open. Planners that use neither are saved as before, and older planners open as they were.
- Checked on the PC only (unit tests, lint, debug and release builds). The color choices, the handwriting in the date blanks and the eraser were not tried on the Pad 7 or on an emulator.

---

# Better Notes 2.2.0 — Release Notes

Draw your own font, a layout for phones, and handwriting models that come inside the app.

- In Settings, the Fonts card has a new "Create your own font". Draw each letter on a pad with guide lines (small letters, capitals, digits, punctuation and the Spanish letters), name the font and save it. It is saved in the app's fonts folder, joins the list of fonts for text boxes next to the five that come with the app and the ones you add, and can be removed there.
- A letter you do not draw shows in the default font. A drawn font can be reopened with Edit in the Fonts card, to redraw letters or rename it. The file is an ordinary .ttf with the drawings kept inside.
- Fonts are not stored in a notebook, so a notebook shared to another device needs the font file added there.
- Phones: on screens under 600 dp wide the toolbars run along the bottom, the home screen has two columns of tiles, and sheets are full width.
- Anything painted, even by one pixel, is now drawn and redrawn in every tile it touches.
- The English and Spanish handwriting models now come inside the app (the app is about 24 MB larger). Nothing is downloaded, so a fresh install or a reinstall can search handwriting straight away.
- A new planner starts as the full calendar year (January to December) instead of mid-year. From December 1st, a new planner is the next year.

---

# Better Notes 2.1 — Release Notes

Fonts of your own.

- In Settings, the new Fonts card adds a font from a .ttf or .otf file, chosen in the system file explorer. The font is copied into the app, so it stays after the original file is moved. Added fonts are listed in their own typeface, and can be removed there.
- In a text box's options, the new Font choice opens a list like the brush type's, each font shown in its own typeface. Its first item, "Add font", opens the file explorer. Select letters first and the font changes just those letters, so one text box can mix fonts.
- Five fonts come with the app, free to use and shared under the SIL Open Font License: Roboto, Open Sans, Lora, Caveat and Roboto Mono. Their license notices are in the app's files.
- Open with: a PDF or a Better Notes file (.bnote) opened from a file manager or another app can open in Better Notes. Opening a PDF this way was tried on the Pad 7; opening a .bnote from outside the app and opening a password-protected PDF this way were not tried.
- Text boxes: a tap with the pen or a finger selects a box and leaves no dot, a finger on a selected box moves it, the pen writes inside an open box, a box is as tall as its text, and its handles change the box and not the text size. Tapping away from an open box, or lassoing from it, goes back to the previous tool.
- A note that uses a font that has been removed shows its text in the app's own font, and shows the font again if it is added back with the same file name. Notebooks saved with this update use page format 8, which older versions of the app cannot open. Fonts are not stored inside the notebook, so a notebook shared to another device needs the same font files added there.

---

# Better Notes 2.0.1 — Release Notes

A small update to text boxes.

- Tap a text box to select it: it shows its handles, so you can move it, resize it or rotate it, and the size, bold, italic, underline, strike and alignment buttons in the toolbar change its text. Tap it twice to type in it again.
- While you type in a text box, select some letters and the size, bold, italic, underline and strike buttons change just those letters, so one text box can hold several looks. With nothing selected they change the whole text. Notebooks saved with this update use page format 7, which older versions of the app cannot open.
- A text box no longer slides away from its place while the page is still moving after a flick.
- If the handwriting models are not on the device, opening a text box now says so and where to download them, instead of silently ignoring handwriting.

---

# Better Notes 2.0 — Release Notes

Better Notes 2.0 brings a major update to the way you create, organize and work with your notes. The update introduces customizable notebook covers, a complete built-in planner, improved source management, split-screen editing and easier ways to share notebooks between devices.

Everything remains fully offline, and notebooks created with previous versions are still supported.

## What's new in 2.0

### Customizable notebooks and covers

Creating a new notebook is now more flexible. Our redesigned New Notebook dialog lets you choose a title, cover design, paper style, page orientation (portrait or landscape) and template language before creating your notebook.

- Choose from approximately 50 cover designs across several styles and color palettes, or start with a completely blank custom cover.
- Change the main colors of a cover before applying it, with a live preview of your choices.
- Every cover is editable. You can select individual elements, change their colors, move them around or delete them to create your own design.
- You can add or edit a cover at any time, even after creating your notebook.
- Covers support handwriting and drawing just like regular pages, while remaining fixed in the notebook's page structure.

Your notebooks also look better on the home screen, with larger cover previews, titles, menus and creation dates.

### Built-in planner

You can now create a complete planner directly from the New Notebook dialog. No need to build your own layouts or configure individual pages.

The planner includes:

- A customizable cover and interactive index.
- Yearly, monthly, weekly and daily calendars.
- Semester overviews and course organization.
- Exam preparation and group project pages.
- Meal planning, routines and habit tracking.
- Additional study and everyday planning templates.

Navigation is built directly into the pages. Use the tabs along the edges or tap calendar entries and dashboard links to jump straight to the section you need.

You can also adjust the calendar's starting weekday, choose between an academic year and a full calendar year, and enter your semester dates once to have them reflected throughout the planner.

### Improved source management

The Sources panel now works more like a notebook binder.

Instead of adding everything directly to your current canvas, the + button lets you add complete resources to your notebook, including:

- Other canvases.
- Images.
- PDF documents.
- Audio files.
- Other notebooks from your library.

Tap any source to open it. Your source list stays consistent across all entries, making it easier to move between related materials without losing track of them.

You can still add images, audio and other content directly to your current canvas using the toolbar.

### Split-screen editing

You can now work with two editor panes simultaneously.

Open a second pane beside or below your current one, depending on your preferred layout. A draggable divider lets you adjust the space allocated to each pane or close one by moving the divider to the edge.

You can also copy selected elements from one pane directly into the other.

This is particularly useful when referencing another notebook, comparing notes or working with source material while writing.

### Share complete notebooks

You can now export an entire notebook into a single `.bnote` file.

The export preserves the notebook's editable content, including:

- Drawings, text and other canvas elements.
- Images, PDFs and audio files.
- Sources and linked notebooks.
- Custom covers and notebook language.

To import a notebook on another installation of Better Notes, open the home screen's + menu and select File & Doc.

Imported notebooks are added as separate entries in your library. You can even import the same file more than once without overwriting existing notebooks.

### English and Spanish support

Better Notes is now available in both English and Spanish.

You can change the application language from Settings. Additionally, each notebook has its own template language, allowing you to keep individual notebooks in a different language from the application itself.

Changing your app language will not automatically change existing notebooks. You can change a notebook's template language at any time through its ... menu.

### Improved layers and object selection

Managing overlapping elements is now easier.

The selection menu includes new layer controls that let you bring an object to the front, move it to the middle or send it to the back.

Selection controls have also been improved:

- Action buttons remain visible and accessible, even when selecting objects near the edges of a page.
- Smaller icons provide more room for the selected content.
- Selection boundaries and handles now follow the page more accurately while scrolling.
- Post-it notes remain below other elements by default, but can be brought forward when working on covers.

### Improved page navigation

Several changes make navigating large notebooks more convenient.

- Additional space above the first page makes it easier to reach the beginning of a notebook.
- Covers are now counted as page 0 and appear consistently in the page counter, page navigation, search results and exports.
- Two-finger rotation is more predictable. Rotation begins after a deliberate 20-degree twist and supports free angles without snapping.
- The rotation indicator has been repositioned to remain accessible with different toolbar layouts.

### Writing and drawing improvements

We've also made several improvements to everyday writing and drawing tools.

- Handwriting in text boxes: You can now write by hand while editing a text box, and your handwriting is converted into typed text word by word.
- Improved text positioning: Text boxes follow the page when zooming or panning.
- More precise tool controls: Numeric settings now include minus and plus buttons alongside a slider for easier adjustments.
- Pen stabilization: Stabilization now offers 11 levels, from 0 to 10.
- Improved drawing tiles: Drawing tiles are now rendered with additional margins to prevent strokes near their edges from being clipped or displayed incorrectly at high zoom levels.

## Improvements carried over from previous versions

Better Notes 2.0 also retains and builds upon features introduced in earlier releases.

- Home screen organization: Move notebooks between folders, reorder them or drag them out of folders. Customize folder colors, opacity and optional file previews.
- Customizable toolbar: Position the toolbar along any screen edge, enable a grid overlay, organize up to ten tools into groups, hide unused tools or restore the default layout.
- Tables: Select tables with a double tap, adjust dividers using handles and add or remove rows and columns.
- Stickers: Save selections as stickers and reuse them from the Stickers tab, either by tapping or dragging them onto a page.
- Paint bucket: Fill enclosed areas in drawings, stickers and post-it notes. Adjust opacity and configure whether the tool returns to your previous selection.
- Shape recognition: Hold the pen at the end of a stroke to automatically convert it into a recognizable shape, including ellipses, rectangles, polygons, arrows and smooth curves. Shapes remain editable using handles.
- Pressure-sensitive pens: Configure pressure sensitivity individually for each pen and brush. Calligraphy tools have also received improvements.
- Post-it notes: Create notes without an initial text box, fold them into your notebook structure and use filling and erasing tools directly inside them.

## Known limitations

A few features are not included in this release:

- `.bnote` files cannot yet be opened directly from Android file managers. Use Home → + → File & Doc instead.
- There is currently no configurable handwriting pause setting.

All features were tested by the in-house dev team, but due to a limited set of tablets, it may behave unexpectedly in yours. Please let us know by opening up an issue here in GitHub if that's the case.

Some improvements, including cover menus and selection behavior during scrolling, have not yet been fully tested across all supported tablets.

Better Notes 2.0 is a substantial step forward in notebook customization, organization and workflow flexibility.

Whether you're using it for handwritten notes, studying, planning or managing reference materials, the goal is to give you more control over how your notebooks look and work, without sacrificing the offline-first experience.
