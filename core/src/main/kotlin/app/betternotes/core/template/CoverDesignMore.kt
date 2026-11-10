package app.betternotes.core.template

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Covers that need a little more drawing: the bright artwork, the letters, the checkbox. Redrawn from the reference. */
internal val moreCovers = listOf(
    CoverDesign("letters", CoverGroup.BLACK_AND_WHITE, "Letters") {
        background(0xFFFFFFFF.toInt())
        rect(0f, 0f, 2.2f * u, height, 0xFFBDBDBD.toInt())
        val word = "GOTSENOS"
        val gray = 0xFFC4C4C4.toInt()
        var row = 0
        var y = 7 * u
        while (y < height) {
            for (col in 0 until 4) {
                val letter = word[(row * 3 + col * 2) % word.length].toString()
                sink.text(letter, 9 * u + col * 25 * u + (row % 2) * 6 * u, y, 3.4f * u, gray, TextStyle.HEADING, TextAlign.CENTER)
            }
            y += 11 * u
            row++
        }
    },
    CoverDesign("brown-checkbox", CoverGroup.BROWN, "Checkbox") {
        background(0xFFC9B99C.toInt())
        val ink = 0xFF2F2A22.toInt()
        val cell = 20 * u
        var x = 0f
        while (x <= width) {
            line(x, 0f, x, height, 0.12f * u, ink)
            x += cell
        }
        var y = 0f
        while (y <= height) {
            line(0f, y, width, y, 0.12f * u, ink)
            y += cell
        }
        x = 0f
        while (x < width) {
            y = 0f
            while (y < height) {
                line(x, y, x + cell, y + cell, 0.1f * u, ink)
                line(x + cell, y, x, y + cell, 0.1f * u, ink)
                if (((x / cell).toInt() + (y / cell).toInt()) % 2 == 0) dot(x, y, 0.9f * u, ink)
                y += cell
            }
            x += cell
        }
    },
    CoverDesign("fluid", CoverGroup.BRIGHT, "Fluid") {
        background(0xFFFBE6F1.toInt())
        val soft = 0xFFFAD3E6.toInt()
        val pale = 0xFFFEF1F8.toInt()
        dot(width * 0.2f, height * 0.3f, 40 * u, soft)
        dot(width * 0.75f, height * 0.5f, 46 * u, pale)
        dot(width * 0.3f, height * 0.8f, 52 * u, soft)
        dot(width * 0.9f, height * 0.9f, 30 * u, pale)
        // Fine vertical lines across the top, as in the reference.
        var x = 0f
        while (x < width) {
            line(x, 0f, x, height * 0.32f, 0.12f * u, 0xFFF4BBD8.toInt())
            x += 1.4f * u
        }
    },
    CoverDesign("goood", CoverGroup.BRIGHT, "GOOOD") {
        background(0xFF1B1F44.toInt())
        for (i in 0 until 18) wave(height * 0.03f + i * 7.2f * u, 4 * u, 38 * u, i * 0.55f, 0.28f * u, 0xFF23BCE0.toInt())
        val white = 0xFFFFFFFF.toInt()
        val spots = listOf(
            "G" to (0.22f to 0.16f), "O" to (0.72f to 0.1f), "D" to (0.9f to 0.22f), "O" to (0.55f to 0.3f),
            "O" to (0.05f to 0.4f), "O" to (0.78f to 0.52f), "O" to (0.55f to 0.7f), "D" to (0.9f to 0.74f),
        )
        for ((letter, at) in spots) sink.text(letter, width * at.first, height * at.second, 11 * u, white, TextStyle.HEADING, TextAlign.CENTER)
    },
    CoverDesign("liquid", CoverGroup.BRIGHT, "Liquid") {
        background(0xFFF6C4DE.toInt())
        dot(width * 0.35f, height * 0.25f, 38 * u, 0xFFFCE2EC.toInt())
        dot(width * 0.7f, height * 0.5f, 34 * u, 0xFFFFD8C8.toInt())
        dot(width * 0.3f, height * 0.7f, 30 * u, 0xFFF9D2E4.toInt())
        dot(width * 0.8f, height * 0.88f, 26 * u, 0xFFFCE2EC.toInt())
        for (i in 0 until 14) {
            val a = i * 2f * PI.toFloat() / 14f
            dot(width * 0.55f + cos(a) * 18 * u, height * 0.5f + sin(a) * 18 * u, 0.6f * u, 0xFFF4A98A.toInt())
        }
    },
    CoverDesign("lotus", CoverGroup.BRIGHT, "Lotus") {
        background(0xFFE2E3DE.toInt())
        val cell = 50 * u
        var row = 0
        var y = -cell / 4
        while (y < height) {
            var x = if (row % 2 == 0) -cell / 4 else cell / 4
            while (x < width) {
                // A tile of two greens meeting a red one, like the cut stones of the reference.
                rect(x, y, cell * 0.5f, cell * 0.5f, 0xFF2EBE8D.toInt())
                rect(x + cell * 0.5f, y, cell * 0.25f, cell * 0.5f, 0xFF2D7A47.toInt())
                rect(x, y - cell * 0.35f, cell * 0.5f, cell * 0.35f, 0xFFDC4C57.toInt())
                dot(x + cell * 0.35f, y + cell * 0.7f, 1.5f * u, 0xFFEDF0A0.toInt())
                x += cell * 1.5f
            }
            y += cell * 0.9f
            row++
        }
    },
)
