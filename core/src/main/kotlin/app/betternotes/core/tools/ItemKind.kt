package app.betternotes.core.tools

import app.betternotes.core.model.AudioItem
import app.betternotes.core.model.ImageItem
import app.betternotes.core.model.InkTool
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.ShapeItem
import app.betternotes.core.model.StickyNoteItem
import app.betternotes.core.model.StrokeItem
import app.betternotes.core.model.TableItem
import app.betternotes.core.model.TextItem

/** The groups of items that the eraser and the lasso can each be told to touch or leave alone. */
enum class ItemKind {
    PEN,
    PENCIL,
    HIGHLIGHTER,
    SHAPE,
    IMAGE,
    TABLE,
    TEXT,
    STICKY_NOTE,
    AUDIO,
}

val PageItem.kind: ItemKind
    get() = when (this) {
        is StrokeItem -> when (style.tool) {
            InkTool.PEN -> ItemKind.PEN
            InkTool.PENCIL -> ItemKind.PENCIL
            InkTool.HIGHLIGHTER -> ItemKind.HIGHLIGHTER
        }
        is ShapeItem -> ItemKind.SHAPE
        is ImageItem -> ItemKind.IMAGE
        is TableItem -> ItemKind.TABLE
        is TextItem -> ItemKind.TEXT
        is StickyNoteItem -> ItemKind.STICKY_NOTE
        is AudioItem -> ItemKind.AUDIO
    }

/** Ink and shapes, since a stray eraser pass should not destroy anything harder to make again. */
val DEFAULT_ERASABLE: Set<ItemKind> = setOf(ItemKind.PEN, ItemKind.PENCIL, ItemKind.HIGHLIGHTER, ItemKind.SHAPE)

val ALL_ITEM_KINDS: Set<ItemKind> = ItemKind.entries.toSet()
