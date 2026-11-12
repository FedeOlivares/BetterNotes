package app.betternotes.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.betternotes.R
import app.betternotes.fonts.FontLibrary
import app.betternotes.fonts.rememberFontPicker
import app.betternotes.ui.theme.LocalSettingsColors
import app.betternotes.ui.theme.Spacing
import app.betternotes.ui.theme.TextSize

/**
 * The fonts that were added, each written in itself, and the button that adds one from a .ttf or .otf file, and the one that draws a font.
 * No screenshot shows this; it follows the other cards. A note that uses a removed font shows the app's own.
 */
@Composable
internal fun FontsCard(drawFont: (String?) -> Unit) {
    val colors = LocalSettingsColors.current
    val addFont = rememberFontPicker()
    SettingsCard {
        CardTitle(stringResource(R.string.settings_fonts))
        SettingRow(stringResource(R.string.font_add), detail = stringResource(R.string.settings_fonts_add_detail), onClick = addFont) {
            Text(
                stringResource(R.string.settings_fonts_choose),
                color = colors.accent,
                fontSize = TextSize.S19_5,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(Spacing.S8),
            )
        }
        SettingRow(
            stringResource(R.string.settings_fonts_create),
            detail = stringResource(R.string.settings_fonts_create_detail),
            onClick = { drawFont(null) },
        ) {
            Text(
                stringResource(R.string.settings_fonts_draw),
                color = colors.accent,
                fontSize = TextSize.S19_5,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(Spacing.S8),
            )
        }
        Text(stringResource(R.string.settings_fonts_included), color = colors.secondary, fontSize = TextSize.S15, modifier = Modifier.padding(bottom = Spacing.S10))
        val names = FontLibrary.names.filter { !FontLibrary.isBuiltIn(it) }
        if (names.isEmpty()) {
            Text(
                stringResource(R.string.settings_fonts_none),
                color = colors.secondary,
                fontSize = TextSize.S15,
                modifier = Modifier.padding(bottom = Spacing.S14),
            )
        }
        for (name in names) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(60.dp)) {
                Text(
                    name,
                    color = colors.text,
                    fontSize = TextSize.S21,
                    fontFamily = FontLibrary.family(name, bold = false, italic = false) ?: FontFamily.Default,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (FontLibrary.isHandmade(name)) {
                    Text(
                        stringResource(R.string.settings_fonts_edit),
                        color = colors.accent,
                        fontSize = TextSize.S19_5,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { drawFont(name) }.padding(Spacing.S8),
                    )
                }
                Text(
                    stringResource(R.string.settings_fonts_remove),
                    color = colors.accent,
                    fontSize = TextSize.S19_5,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { FontLibrary.remove(name) }.padding(Spacing.S8),
                )
            }
        }
    }
}
