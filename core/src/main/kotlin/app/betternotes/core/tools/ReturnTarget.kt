package app.betternotes.core.tools

/** Where a tool that gives the tool in use before back goes when it is done. */
object ReturnTarget {
    /**
     * The tool to come back to: the one in use before [current] when there was one, and the [pen] when there was none, or when it is [current]
     * itself, as it is when a tool is the first one the notebook opens with.
     */
    fun <T> target(previous: T?, current: T, pen: T): T = if (previous == null || previous == current) pen else previous
}
