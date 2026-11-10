package app.betternotes.core.tools

/**
 * How the toolbar's tools are arranged: a list of entries, each a list of tool keys. An entry of one key is a tool by itself, and one of
 * two or more is a group, which shows as one icon and holds those tools.
 */
typealias ToolbarEntries = List<List<String>>

object ToolbarGroups {
    /** Where a dragged tool goes. Places count in the entries as they are with the tool taken out. */
    sealed interface Drop {
        /** By itself, as the entry at this place in the list. */
        data class Alone(val at: Int) : Drop

        /** Into the group that is the entry at [entry], at this place in it. */
        data class InGroup(val entry: Int, val at: Int) : Drop

        /** Together with the tool that is by itself at [entry]: the two become a group. */
        data class With(val entry: Int) : Drop
    }

    /** [saved] cleaned up: only tools of [all], each once, no empty entry, and any that were missing added at the end by themselves. */
    fun complete(saved: ToolbarEntries, all: List<String>): ToolbarEntries {
        val seen = HashSet<String>()
        val entries = ArrayList<List<String>>()
        for (entry in saved) {
            val keys = entry.filter { it in all && seen.add(it) }
            if (keys.isNotEmpty()) entries += keys
        }
        for (key in all) if (seen.add(key)) entries += listOf(key)
        return entries
    }

    /** The entries without [key]. A group that is left with one tool becomes that tool by itself, and an empty one goes. */
    fun without(entries: ToolbarEntries, key: String): ToolbarEntries = entries.mapNotNull { entry ->
        if (key !in entry) entry else entry.filter { it != key }.takeIf { it.isNotEmpty() }
    }

    /** [entries] with [key] moved to where [drop] says. */
    fun move(entries: ToolbarEntries, key: String, drop: Drop): ToolbarEntries {
        val rest = without(entries, key).toMutableList()
        when (drop) {
            is Drop.Alone -> rest.add(drop.at.coerceIn(0, rest.size), listOf(key))
            is Drop.InGroup -> {
                if (drop.entry !in rest.indices) {
                    rest += listOf(key)
                } else {
                    val group = rest[drop.entry].toMutableList()
                    group.add(drop.at.coerceIn(0, group.size), key)
                    rest[drop.entry] = group
                }
            }
            is Drop.With -> {
                if (drop.entry !in rest.indices) rest += listOf(key) else rest[drop.entry] = rest[drop.entry] + key
            }
        }
        return rest
    }

    /** Written as keys joined by commas, with the tools of a group joined by plus signs. A list of single keys is what was saved before groups. */
    fun encode(entries: ToolbarEntries): String = entries.joinToString(",") { it.joinToString("+") }

    fun decode(text: String?): ToolbarEntries =
        text.orEmpty().split(',').map { entry -> entry.split('+').filter { it.isNotEmpty() } }.filter { it.isNotEmpty() }
}
