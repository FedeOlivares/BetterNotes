package app.betternotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.betternotes.canvas.CanvasView
import app.betternotes.canvas.NoteEditor
import app.betternotes.core.search.TextMatch

/**
 * A plain way to try PDF import and text search until the real screens exist. There is no reference
 * for the import flow or the search results yet, so this deliberately matches nothing.
 */
@Composable
internal fun PdfDevChrome(canvas: CanvasView, editor: () -> NoteEditor?, status: String, importPdf: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf(emptyList<TextMatch>()) }
    var current by remember { mutableIntStateOf(0) }

    fun select(index: Int) {
        current = index
        val match = matches.getOrNull(index)
        canvas.showMatches(matches, match)
        match?.let(canvas::reveal)
    }

    fun search(text: String) {
        query = text
        matches = emptyList()
        current = 0
        canvas.showMatches(emptyList())
        val open = editor() ?: return
        if (text.isBlank()) {
            open.cancelPdfSearch()
            return
        }
        open.searchPdfText(
            text,
            onMatches = { found ->
                val first = matches.isEmpty()
                matches = matches + found
                if (first) select(0) else canvas.showMatches(matches, matches.getOrNull(current))
            },
            onFinished = {},
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .padding(top = 6.dp)
            .background(Color(0xE01C1E22), RoundedCornerShape(24.dp))
            .padding(horizontal = 8.dp),
    ) {
        PdfButton("Import PDF", importPdf)
        BasicTextField(
            value = query,
            onValueChange = ::search,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
            cursorBrush = SolidColor(Color.White),
            modifier = Modifier
                .width(160.dp)
                .background(Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                .padding(8.dp),
        )
        if (matches.isNotEmpty()) {
            PdfButton("<") { select((current - 1).mod(matches.size)) }
            Text("${current + 1}/${matches.size}", color = Color.White, fontSize = 14.sp)
            PdfButton(">") { select((current + 1).mod(matches.size)) }
        }
        if (status.isNotEmpty()) Text(status, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
private fun PdfButton(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Color.White,
        fontSize = 14.sp,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
    )
}
