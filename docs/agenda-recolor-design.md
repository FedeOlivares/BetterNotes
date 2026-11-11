# Agenda recolor: design

Goal: lasso any generated element of a planner page and recolor it (text, lines, background, shading, font), alone or for every similar element. Planner pages stay locked: no move, no text edit, no delete.

## How planner pages are made today
- A planner page is not made of items. Its `PageTemplate.Planner` (page kind, setup, semesters, colors, dateInk) is drawn on every paint by `PlannerDrawing.draw` through a `TemplateSink` (`Layer` helpers: `button`, `tiles`, `heading`, `title`, `rows`, `box`, `tabs`, `rightText`, `c.text`, `c.fill`).
- The planner colors (cover, spine, stripe, accent) are already stored on the template of every page. Date handwriting (`DateStroke`) is replicated on the pages that show it.
- Tap targets come from the same drawing (`LinkOut`), so geometry and links cannot drift. The lasso reuses that trick: a probe pass of the same drawing code records each element.
- The whole page is emitted into the sink and the canvas clips per tile, so nothing is culled by region today. That is the tile rule to keep.

## Similarity roles
`PlannerRole` enum (append-only, ordinal is what the file keeps):
BUTTON (every `button()` and every `tiles()` cell: Yearly Overview, Routines, Grade Tracker, VIEW WEEK, Smstr/Qtr, and the large Dotted tile), PAGE_TITLE (`title()`: Planner Dashboard, 03 | March, Week 22, General Planning), SUBTITLE (`rightText`: the year, the week range), DAY_DATE (the day page's "Friday, May 1, 2026" and its weekday underline), SECTION_HEADING (`heading()` and the bold labels: SCHEDULE, TOP PRIORITIES, STUDENT TASKS, MONTHLY PLANNING), ROW_LABEL (`labelRows` labels), ROWS (ruled task rows with their numbers and slashes), TABLE (`table()` headers and cells), BOX (`box()` frames), CELL_TEXT (calendar day numbers, hour numbers), WEEKDAY_HEADER (MON TUE...), SHADE_BAND (week-number column, out-of-month cells, hour column), TAB (side tabs and the current-tab label), COVER_TITLE, DATE_BLANK (the "Start Date:" labels and rules; the handwriting itself is never recolored).
Each primitive drawn inside a `c.element(role)` scope carries that role. Outside any scope the role is NONE and nothing changes (date handwriting, paper).

Examples from the brief: Yearly Overview, Routines, Grade Tracker and the Dotted tile are all BUTTON. Month and week titles are PAGE_TITLE, the day's long date is DAY_DATE, so it is skipped. SCHEDULE, TOP PRIORITIES and STUDENT TASKS (week, day and month pages) are all SECTION_HEADING.

## What can be changed
Per role (or per single element) a `RoleStyle`: text color, line color, background color (a solid fill behind the element, rounded for buttons and tiles), shading color, font id (from the 2.1 font library). Every field is nullable: null means default. A primitive's slot comes from what it is: text is TEXT, line/dot/polyline is LINE, a see-through fill is SHADING, an opaque fill is BACKGROUND.

## Storage (additive, only when used)
- `PlannerRoleStyles`: `byRole: Map<PlannerRole, RoleStyle>` and `byElement: Map<ElementKey, RoleStyle>`, where `ElementKey` = planner page + role + the element's top-left in the 1000x1414 reference units, rounded. Position keys survive any change of date or language because the drawing is deterministic.
- Held in `PageTemplate.Planner.roles` (default empty). Role styles are copied onto every page like `colors`; element styles live on their own page. Empty means the template encodes exactly as before, so old planners and old files are unchanged byte for byte.
- New template tag `TEMPLATE_PLANNER_ROLES` written only when `roles` is not empty, and one constant `PLANNER_ROLES_FORMAT_VERSION = 14`. The document is written at 14 only if some page uses roles, else at 7 as now; the decoder accepts up to 14. Never touch the expected bytes in `CodecTest`; a new test covers roles.
- All changes are `SetPlannerPages` edits (undoable, last in first out), like `PlannerPages.recolor`. `PlannerPages.change` carries the roles over to rebuilt pages.

## Drawing
`PlannerStyleSink` wraps the sink like `RecolorSink` does, looking up (page element or role, slot) per primitive. `TemplateSink.text` gains an optional `font` argument so text can switch typeface; `CanvasTemplateSink` resolves it with `FontLibrary`. Backgrounds are drawn with the same sink calls (fills and dots), so they render in every tile they touch.

## Lasso
- Planner pages are locked: the lasso never moves, copies, cuts or deletes generated elements and cannot edit their text.
- Lasso arbitration: if the loop catches the user's own ink items, the lasso behaves as it does today. If it catches no ink and the page is a planner page, it selects planner elements (a probe pass records role, bounds and anchor of each element; an element is picked if the loop covers its center or a majority of its area).
- The selection bar for planner elements offers only: Text, Lines, Background, Shading, Font, Apply to similar items (toggle, off by default), Reset. With the toggle on, the edit writes `byRole` (all pages); off, it writes `byElement` for the selected elements only. Date handwriting replication is untouched: `dateInk` and `semesters` stay on their own fields, and the date strokes are drawn under role NONE.

## Known risks to confirm
1. Version 14: planner templates live in the document file (now 7), page files are at 12. 14 is used as one constant for both, with 13 left unused.
2. `rightText` estimates its width, so a wider font can shift right-aligned text. Plan: make those texts end-aligned in the sink, a small drawing change.
3. Buttons have no fill today. A background fill is new drawing under them; the default (no override) draws exactly as before.
