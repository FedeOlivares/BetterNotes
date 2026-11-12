package app.betternotes.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Every corner radius the screens use, smallest first. The values were measured from the reference screenshots, so
 * there are more steps than a designed scale would have; a redesign can map them onto fewer.
 */
internal object Radius {
    val R2 = 2.dp
    val R3 = 3.dp
    val R4 = 4.dp
    val R6 = 6.dp
    val R8 = 8.dp
    val R9 = 9.dp
    val R10 = 10.dp
    val R12 = 12.dp
    val R14 = 14.dp
    val R16 = 16.dp
    val R18 = 18.dp
    val R20 = 20.dp
    val R22 = 22.dp
    val R24 = 24.dp
    val R25 = 25.dp
    val R26 = 26.dp
    val R27 = 27.dp
    val R28 = 28.dp
    val R32 = 32.dp
    val R33 = 33.dp
}

/**
 * The app's shapes. The screens take every rounded corner from here, so changing a radius above or a shape below
 * restyles everything that uses it. Shapes are shared instances, so they are not rebuilt on each composition.
 */
internal object AppShapes {
    val Circle = CircleShape

    val Rounded2 = RoundedCornerShape(Radius.R2)
    val Rounded3 = RoundedCornerShape(Radius.R3)
    val Rounded4 = RoundedCornerShape(Radius.R4)
    val Rounded6 = RoundedCornerShape(Radius.R6)
    val Rounded8 = RoundedCornerShape(Radius.R8)
    val Rounded9 = RoundedCornerShape(Radius.R9)
    val Rounded10 = RoundedCornerShape(Radius.R10)
    val Rounded12 = RoundedCornerShape(Radius.R12)
    val Rounded14 = RoundedCornerShape(Radius.R14)
    val Rounded16 = RoundedCornerShape(Radius.R16)
    val Rounded18 = RoundedCornerShape(Radius.R18)
    val Rounded20 = RoundedCornerShape(Radius.R20)
    val Rounded22 = RoundedCornerShape(Radius.R22)
    val Rounded24 = RoundedCornerShape(Radius.R24)
    val Rounded25 = RoundedCornerShape(Radius.R25)
    val Rounded26 = RoundedCornerShape(Radius.R26)
    val Rounded27 = RoundedCornerShape(Radius.R27)
    val Rounded28 = RoundedCornerShape(Radius.R28)
    val Rounded32 = RoundedCornerShape(Radius.R32)
    val Rounded33 = RoundedCornerShape(Radius.R33)

    /** Shapes with only some corners rounded. */
    val TopRounded6 = RoundedCornerShape(topStart = Radius.R6, topEnd = Radius.R6)
    val TopRounded8 = RoundedCornerShape(topStart = Radius.R8, topEnd = Radius.R8)
    val TopRounded20 = RoundedCornerShape(topStart = Radius.R20, topEnd = Radius.R20)
    val TopRounded28 = RoundedCornerShape(topStart = Radius.R28, topEnd = Radius.R28)
    val EndRounded28 = RoundedCornerShape(topEnd = Radius.R28, bottomEnd = Radius.R28)

    /** Named shapes of particular parts. */
    val EditorPanel = Rounded22
    val LibraryCard = Rounded25
    val LibraryRow = Rounded18
    val LibraryMenu = Rounded24
    val FolderPreview = TopRounded8
    val NewItemPanel = Rounded28
    val NewItemTile = Rounded22
    val NewNotebookTile = Rounded12
    val NewNotebookDialog = Rounded28
}
