package app.betternotes.core

import app.betternotes.core.edit.EditTarget
import app.betternotes.core.model.Document
import app.betternotes.core.model.IdSource
import app.betternotes.core.model.InkStyle
import app.betternotes.core.model.PageContent
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.StrokeItem
import app.betternotes.core.model.StrokePointsBuilder

/** Predictable ids that count up from a value well above the ones tests give their own items. */
class CountingIds(private var next: Long = 1000) : IdSource {
    override fun next() = next++
}

/** A stroke through the given x,y pairs. Time and pressure rise along it. */
fun stroke(id: Long, vararg xy: Float, width: Float = 2f): StrokeItem {
    val builder = StrokePointsBuilder(xy.size / 2)
    for (i in 0 until xy.size / 2) {
        builder.add(xy[i * 2], xy[i * 2 + 1], pressure = i * 0.1f, tilt = 0.5f, orientation = 1f, timeMs = i * 10f)
    }
    return StrokeItem(id, InkStyle(width = width), builder.build())
}

class FakeTarget(override val document: Document) : EditTarget {
    private val contents = HashMap<Long, PageContent>()
    override fun content(pageId: Long) = contents.getOrPut(pageId) { PageContent() }
}

fun PageContent.ids(): List<Long> = toList().map(PageItem::id)
