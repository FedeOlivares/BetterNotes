package app.betternotes.ui.editor

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.betternotes.R
import app.betternotes.fonts.FontLibrary
import app.betternotes.fonts.rememberFontPicker
import app.betternotes.ui.icons.EditorIcons
import app.betternotes.ui.theme.LocalEditorColors

/**
 * The text's font, in the way of the brush type: the one in use, each name in its own typeface, and a list of the
 * others that opens under it. The list starts with "Add font", which opens the file explorer, then the app's own font.
 */
@Composable
internal fun FontSelector(current: String, onSelect: (String) -> Unit) {
    val colors = LocalEditorColors.current
    var open by remember { mutableStateOf(false) }
    val addFont = rememberFontPicker { name ->
        onSelect(name)
        open = false
    }
    val names = FontLibrary.names
    Text(
        stringResource(R.string.panel_font),
        color = colors.label,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = PanelPadding, top = 6.dp, bottom = 8.dp),
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = PanelPadding)) {
        FontRow(current.ifEmpty { null }, selected = false, chevron = true) { open = !open }
        if (open) {
            Column(
                Modifier
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, colors.panelDivider, RoundedCornerShape(16.dp))
                    .heightIn(max = LIST_MAX_HEIGHT)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().height(ROW_HEIGHT).clickable(onClick = addFont).padding(horizontal = 20.dp),
                ) {
                    Icon(EditorIcons.Plus, null, tint = colors.accent, modifier = Modifier.padding(end = 12.dp).size(22.dp))
                    Text(stringResource(R.string.font_add), color = colors.accent, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
                FontRow(null, selected = current.isEmpty(), chevron = false) {
                    open = false
                    onSelect("")
                }
                for (name in FontLibrary.BUILT_IN + names.filter { !FontLibrary.isBuiltIn(it) }) {
                    FontRow(name, selected = name == current, chevron = false) {
                        open = false
                        onSelect(name)
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}

/** A font's name written in that font; null is the app's own font. A name whose file has gone is written in the app's own. */
@Composable
private fun FontRow(name: String?, selected: Boolean, chevron: Boolean, onClick: () -> Unit) {
    val colors = LocalEditorColors.current
    // Reading the names makes the row follow a font that is added or removed.
    val family = name?.takeIf { FontLibrary.isBuiltIn(it) || it in FontLibrary.names }?.let { FontLibrary.family(it, bold = false, italic = false) } ?: FontFamily.Default
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .then(if (chevron) Modifier.clip(RoundedCornerShape(27.dp)).border(1.dp, colors.panelDivider, RoundedCornerShape(27.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
    ) {
        Text(
            name ?: stringResource(R.string.font_default),
            color = if (selected) colors.accent else colors.title,
            fontSize = 18.sp,
            fontFamily = family,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (chevron) Icon(EditorIcons.ChevronDown, null, tint = colors.label, modifier = Modifier.padding(start = 10.dp).size(22.dp))
    }
}

private val ROW_HEIGHT = 54.dp
private val LIST_MAX_HEIGHT = 324.dp
