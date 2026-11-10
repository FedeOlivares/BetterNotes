package app.betternotes.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The editor's icons, drawn for this app as line art on a 24-unit grid. None come from Notewise. */
internal object EditorIcons {
    val Back by lazy { line("back", "M20 12H4.5 M11 5l-7 7 7 7") }
    val Sidebar by lazy { line("sidebar", rrect(3f, 5f, 18f, 14f, 2.5f) + "M9.5 5v14") }
    val PageGrid by lazy {
        line(
            "page-grid",
            rrect(3.5f, 3.5f, 7f, 7f, 1.5f) + rrect(13.5f, 3.5f, 7f, 7f, 1.5f) +
                rrect(3.5f, 13.5f, 7f, 7f, 1.5f) + rrect(13.5f, 13.5f, 7f, 7f, 1.5f),
        )
    }
    val PageStack by lazy { line("page-stack", rrect(4f, 8f, 16f, 12.5f, 2.5f) + "M6 5.5h12 M8.5 3h7") }
    val ChevronsUpDown by lazy { line("chevrons", "M7 9.5l5-5 5 5 M7 14.5l5 5 5-5") }
    val ChevronUp by lazy { line("chevron-up", "M7 14.5l5-5 5 5") }
    val ChevronDown by lazy { line("chevron-down", "M7 9.5l5 5 5-5") }
    val ChevronLeft by lazy { line("chevron-left", "M14.5 7l-5 5 5 5") }
    val ChevronRight by lazy { line("chevron-right", "M9.5 7l5 5-5 5") }
    val Undo by lazy { line("undo", "M8.5 5L4.5 9l4 4 M4.5 9h10a5 5 0 0 1 0 10h-3") }
    val Redo by lazy { line("redo", "M15.5 5l4 4-4 4 M19.5 9h-10a5 5 0 0 0 0 10h3") }
    val FingerMode by lazy {
        line(
            "finger-mode",
            "M11 11V6a1.5 1.5 0 0 1 3 0v6l4.2.9a2 2 0 0 1 1.6 2.3l-.6 3.4a2.5 2.5 0 0 1-2.5 2.1h-3.6" +
                "a3 3 0 0 1-2.3-1.1L7 16a1.4 1.4 0 0 1 2.1-1.8L11 16z " +
                "M5 10V3 M3 5l2-2 2 2 M3 8l2 2 2-2",
        )
    }
    /** The finger drawing a line: the same hand as [FingerMode], with a wavy stroke where the arrows were. */
    val FingerDraw by lazy {
        line(
            "finger-draw",
            "M11 11V6a1.5 1.5 0 0 1 3 0v6l4.2.9a2 2 0 0 1 1.6 2.3l-.6 3.4a2.5 2.5 0 0 1-2.5 2.1h-3.6" +
                "a3 3 0 0 1-2.3-1.1L7 16a1.4 1.4 0 0 1 2.1-1.8L11 16z " +
                "M2.5 7c1.2-3 2.4-3 3.3 0s2.1 3 3.2 0",
        )
    }
    val Pen by lazy { line("pen", "M4 20l1.2-4.6L16.6 4a2 2 0 0 1 2.8 0l.6.6a2 2 0 0 1 0 2.8L8.6 18.8z") }
    val Pencil by lazy {
        line("pencil", "M12 11.5L18.2 4a1.8 1.8 0 0 1 2.6 2.6L13.5 13 M12 11.5l1.5 1.5c.4 3-2.4 6.5-9.5 7 1.6-1.6 2-3.2 2.2-4.6.3-2.3 3.2-4.6 5.8-3.9z")
    }
    val Highlighter by lazy {
        line("highlighter", "M16.81 2.09L21.91 7.19 13.43 15.67 8.33 10.57z M9.04 11.28L12.72 14.96 10.95 16.73 7.27 13.05z M8.05 13.83L10.17 15.95 8.54 17.58 6.42 15.46z")
    }
    val Eraser by lazy {
        line("eraser", "M14.48 3.16L20.84 9.52 9.52 20.84 3.16 14.48z M5.99 11.65L12.35 18")
    }
    val Lasso by lazy {
        line("lasso", dashedArc() + "M8.5 15.5a2.6 2.6 0 1 1-2.7-2.4c1 0 2.2.5 2.7 2.4zM8.5 15.5c.3 1.9-.6 3.8-2.5 5")
    }
    val Shapes by lazy {
        line("shapes", rrect(3.5f, 8f, 9.5f, 9.5f, 1.5f) + "M12.5 3a5 5 0 0 1 5 5 M13 16.5L17 9.5l4.5 11h-9z")
    }
    /** The tagged strip between the eraser and the shapes. What it does is unconfirmed. */
    val Tape by lazy { line("tape", "M4.2 13.8L13.8 4.2l2 2 1.5-1 1.5 1.5-1 1.5 2 2-9.6 9.6-2-2-1.5 1-1.5-1.5 1-1.5z") }
    val Text by lazy { line("text", rrect(3.5f, 3.5f, 17f, 17f, 2.5f) + "M8 8.5h8 M12 8.5v8 M10.3 16.5h3.4") }
    val Sticker by lazy {
        line("sticker", "M12 3a9 9 0 1 0 9 9c-1 3-3 5-5.5 6.5 M8 14.5c1 2.2 5 3 7.5 0 M9 9.5v.2 M15 9.5v.2 M21 12c-2.5-.5-5 .5-5.5 6.5")
    }
    val AddElement by lazy { line("add-element", rrect(3.5f, 4f, 9f, 7f, 1.5f) + rrect(3.5f, 14f, 17f, 6f, 1.5f) + "M18 4.5v6 M15 7.5h6") }
    val EraseMode by lazy { line("erase-mode", "M9 4.2A9 9 0 1 0 19.8 15 M14.5 3.5l6 6-5 5-6-6z M8 13.5v.2 M5.5 9.5v.2") }
    val Broom by lazy { line("broom", "M20.5 3.5L13 11 M9.5 9l5.5 5.5c-1.5 3-4 5.5-8 6.5l-1-3-1.5 2L3 19c1.5-1 3-3 2-4l-1-.5c2.500-.5 4-2.500 5.500-5.500z") }
    val LassoFreeform by lazy { line("lasso-freeform", dashedCircle() + "M16.5 6.500l1.500 1.500-6 6-2 .5.5-2z") }
    val LassoRectangle by lazy { line("lasso-rectangle", "M3 7V4.500A1.500 1.500 0 0 1 4.500 3H7 M11 3h3 M21 11v3 M21 18v1.500a1.500 1.500 0 0 1-1.500 1.500H17 M13 21h-3 M6 21H4.500A1.500 1.500 0 0 1 3 19.500V18 M3 14v-3 M17.500 5.500l2 2-7 7-2.500.5.500-2.500z") }
    val Rotate by lazy { line("rotate", "M19.500 12a7.500 7.500 0 1 1-2.200-5.300 M19.500 4.500v4h-4") }
    val Lock by lazy { line("lock", "M6.500 11h11v8.500h-11z M8.500 11V8a3.500 3.500 0 0 1 7 0v3") }
    val Unlock by lazy { line("unlock", "M6.500 11h11v8.500h-11z M8.500 11V8a3.500 3.500 0 0 1 6.600-1.600") }
    val Translucent by lazy { line("translucent", "M12 3.500C9 7.500 6.500 10 6.500 13.500a5.500 5.500 0 0 0 11 0C17.500 10 15 7.500 12 3.500z M12 7v10") }
    val LassoLocked by lazy { line("lasso-locked", dashedCircle() + "M8.500 11h7v5.500h-7z M10 11V9.500a2 2 0 0 1 4 0V11") }
    val LassoScreenshot by lazy { line("lasso-screenshot", dashedCircle() + "M8 10h1.500l1-1.500h3l1 1.500H16v6H8z M12 11a1.700 1.700 0 1 0 .01 0") }
    val Bold by lazy { line("bold", "M8 5h5a3.500 3.500 0 0 1 0 7H8z M8 12h6a3.500 3.500 0 0 1 0 7H8z", width = 2.4f) }
    val Italic by lazy { line("italic", "M10 5h7 M7 19h7 M14 5l-4 14") }
    val Underline by lazy { line("underline", "M7 4v8a5 5 0 0 0 10 0V4 M6 20h12") }
    val Strike by lazy { line("strike", "M17 7c-1-1.800-3-2.500-5-2.500-2.800 0-5 1.300-5 3.800 0 1.700 1 2.900 3 3.700 M4 12h16 M15.500 15c.5.600.8 1.300.8 2 0 2.300-2 3.500-4.800 3.500-2.300 0-4.200-.8-5.200-2.500") }
    val AlignStart by lazy { line("align-start", "M4 6h16 M4 12h10 M4 18h13") }
    val AlignCenter by lazy { line("align-center", "M4 6h16 M7 12h10 M5.500 18h13") }
    val AlignEnd by lazy { line("align-end", "M4 6h16 M10 12h10 M7 18h13") }
    val AlignTop by lazy { line("align-top", "M5 4h14 M12 20V8 M7.500 12.500L12 8l4.500 4.500") }
    val AlignMiddle by lazy { line("align-middle", "M5 12h14 M12 3v5 M9.500 6L12 8.500 14.500 6 M12 21v-5 M9.500 18l2.500-2.500 2.500 2.500") }
    val AlignBottom by lazy { line("align-bottom", "M5 20h14 M12 4v12 M7.500 11.500L12 16l4.500-4.500") }
    val AddImage by lazy { line("add-image", "M13 4H6a2.500 2.500 0 0 0-2.500 2.500v11A2.500 2.500 0 0 0 6 20h12a2.500 2.500 0 0 0 2.500-2.500V11 M3.500 16l5-5 5 5 2-2 5 4 M18 2.500v6 M15 5.500h6") }
    val Folder by lazy { line("folder", "M3.500 7A2 2 0 0 1 5.500 5H10l2 2.500h6.500a2 2 0 0 1 2 2V17a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2z") }
    val Scan by lazy { line("scan", "M3.500 8V5.500a2 2 0 0 1 2-2H8 M16 3.500h2.500a2 2 0 0 1 2 2V8 M20.500 16v2.500a2 2 0 0 1-2 2H16 M8 20.500H5.500a2 2 0 0 1-2-2V16 M8.500 8h5l2 2v6h-7z") }
    val ImageRound by lazy { line("image-round", "M12 3a9 9 0 1 0 .01 0") }
    val ImageSquare by lazy { line("image-square", rrect(3.5f, 3.5f, 17f, 17f, 2f)) }
    val Download by lazy { line("download", "M12 4v11 M7.500 11L12 15.500 16.500 11 M5 20h14") }
    val PlayCircle by lazy { line("play-circle", "M12 3a9 9 0 1 0 .01 0 M10 8.500v7l5.500-3.500z") }
    val Play by lazy { filled("play", "M7 4.500v15l13-7.500z") }
    val Pause by lazy { filled("pause", "M7 4.500h3.500v15H7z M13.500 4.500H17v15h-3.500z") }
    val StopCircle by lazy { filled("stop-circle", "M12 2.500a9.500 9.500 0 1 0 .01 0z M9 9h6v6H9z") }
    val CheckCircle by lazy { line("check-circle", "M12 3a9 9 0 1 0 .01 0 M7.500 12.500l3 3 6-6.500") }
    val Crosshair by lazy { line("crosshair", "M12 5a7 7 0 1 0 .01 0 M12 2.500V8 M12 16v5.500 M2.500 12H8 M16 12h5.500") }
    val Bucket by lazy {
        line("bucket", "M4.500 12.500L11 6l7.500 7.500-6.500 6.500a2 2 0 0 1-2.800 0L4.500 15.300a2 2 0 0 1 0-2.800z M11 6L9 4 M4.500 13h14 M20 16.500c1.200 1.700 1.700 2.600 1.700 3.200a1.700 1.700 0 0 1-3.400 0c0-.6.500-1.500 1.700-3.200z")
    }
    val Eyedropper by lazy {
        line("eyedropper", "M15 4.500l4.500 4.500 M17 3a2.100 2.100 0 0 1 3 3l-2.500 2.500-3-3z M14.500 6.500l-9 9a2 2 0 0 0-.5 1.100V19h2.400a2 2 0 0 0 1.100-.5l9-9 M4.500 19.500l.5-.5")
    }
    val GridCross by lazy { line("grid-cross", "M12 3v18 M3 12h18") }
    val GridThirds by lazy { line("grid-thirds", "M9 3v18 M15 3v18 M3 9h18 M3 15h18") }
    val Forward by lazy { line("forward", "M4 12h15.500 M13 5l7 7-7 7") }
    val Search by lazy { line("search", "M10.5 4a6.5 6.5 0 1 0 .01 0 M15.5 15.5L21 21") }
    val Image by lazy { line("image", rrect(3f, 4.5f, 18f, 15f, 2.5f) + "M3.5 17l5-5 4 4 2.5-2.5 5 5 M15.5 9.5a1.2 1.2 0 1 0 .01 0") }
    val Camera by lazy { line("camera", "M4 7.5h3.5L9 5h6l1.5 2.5H20a1.5 1.5 0 0 1 1.5 1.5v9a1.5 1.5 0 0 1-1.5 1.5H4A1.5 1.5 0 0 1 2.5 18V9A1.5 1.5 0 0 1 4 7.5z M12 9.8a3.5 3.5 0 1 0 .01 0") }
    val StickyNote by lazy { line("sticky", "M6 3.5h12A2.5 2.5 0 0 1 20.5 6v8l-6.5 6.5H6A2.5 2.5 0 0 1 3.5 18V6A2.5 2.5 0 0 1 6 3.5z M20.5 14H16a2 2 0 0 0-2 2v4.5") }
    val Table by lazy { line("table", rrect(3f, 5f, 18f, 14f, 2.5f) + "M3 10h18 M3 14.5h18 M12 10v9") }
    val Tray by lazy { line("tray", rrect(3f, 6.5f, 18f, 13.5f, 3f) + "M6 6.5V5a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v1.5 M8 13c1.5-3 2.5-3 3 0s2 3 3 0 1.5-2 2-1") }
    val Loupe by lazy { line("loupe", rrect(3f, 4f, 17f, 15.5f, 2f) + "M11.5 7.5a3.8 3.8 0 1 0 .01 0 M11.5 9.6v3.4 M9.8 11.3h3.4 M14.3 14l6 6.5") }
    val Ruler by lazy { line("ruler", "M3.7 16L16 3.7a1.5 1.5 0 0 1 2.1 0l2.2 2.2a1.5 1.5 0 0 1 0 2.1L8 20.3a1.5 1.5 0 0 1-2.1 0l-2.2-2.2a1.5 1.5 0 0 1 0-2.1z M8 11.7l2 2 M11 8.7l2 2 M14 5.7l2 2") }
    val Mic by lazy { line("mic", rrect(9f, 3f, 6f, 11f, 3f) + "M5.5 11a6.5 6.5 0 0 0 13 0 M12 17.5V21 M9 21h6") }
    val Laser by lazy { line("laser", "M3.5 20.5L11 13 M15 3v3 M15 12v3 M21 9h-3 M12 9H9 M19.2 4.8l-2 2 M12.8 11.2l-1 1 M19.2 13.2l-2-2 M12.8 6.8l-2-2") }
    val Settings by lazy { line("settings", gear() + "M12 9a3 3 0 1 0 .01 0") }
    val Eye by lazy { line("eye", "M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z M12 9.2a2.8 2.8 0 1 0 .01 0") }
    /** A crossed eye with the corners of a square and a circle behind it, for the tools that are hidden. */
    val HiddenTools by lazy {
        line("hidden-tools", "M3 8V4.500A1.500 1.500 0 0 1 4.500 3H8 M16 3a4 4 0 0 1 4 4 M3.500 14.500s3-4.500 8.500-4.500 8.500 4.500 8.500 4.500-3 4.500-8.500 4.500-8.500-4.500-8.500-4.500z M12 13a1.800 1.800 0 1 0 .01 0 M4.500 20.500l15-15")
    }
    val EyeOff by lazy { line("eye-off", "M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z M12 9.2a2.8 2.8 0 1 0 .01 0 M4 4l16 16") }
    val More by lazy { filled("more", "M5.5 10.2a1.8 1.8 0 1 0 .01 0z M12 10.2a1.8 1.8 0 1 0 .01 0z M18.5 10.2a1.8 1.8 0 1 0 .01 0z") }
    val AddColor by lazy { line("add-color", dashedCircle() + "M12 8v8 M8 12h8") }
    val EditColor by lazy { line("edit-color", "M14.5 4.5l3.5 3.5L8.5 17.5H5v-3.5z M5 21h14") }
    val Grip by lazy { filled("grip", "M8.5 5a1.4 1.4 0 1 0 .01 0z M15.5 5a1.4 1.4 0 1 0 .01 0z M8.5 10.6a1.4 1.4 0 1 0 .01 0z M15.5 10.6a1.4 1.4 0 1 0 .01 0z M8.5 16.2a1.4 1.4 0 1 0 .01 0z M15.5 16.2a1.4 1.4 0 1 0 .01 0z") }
    val Brush by lazy {
        line("brush", "M18.02 3.3L20.7 5.98 15.4 11.28 12.72 8.6z M12.7 8.7C9.6 8 6.7 10 6.5 12.8 6.4 14.6 5 15.8 3.4 16.4c-.5.3-.4 1 .2 1.2 4.4 1.3 9.6-.2 10.8-4.6.3-1.1.5-1.5 1-1.8")
    }
    val Export by lazy { line("export", "M12 15V4 M8 8l4-4 4 4 M5 14v5a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-5") }
    val GoToPage by lazy { line("go-to-page", "M12 3a9 9 0 1 0 .01 0 M8 12h8 M13 9l3 3-3 3") }
    val Template by lazy { line("template", rrect(4f, 4f, 16f, 6f, 1.5f) + "M4 14h6 M4 18h6 M14 14h6 M14 18h6") }
    val Cover by lazy { line("cover", rrect(5f, 3f, 14f, 18f, 2f) + "M9 3v18 M12 8h4") }
    val ScrollLayout by lazy { line("scroll-layout", "M8 5v14 M4.5 15.5L8 19l3.5-3.5 M16 19V5 M12.5 8.5L16 5l3.5 3.5") }
    val Split by lazy { line("split", rrect(3f, 5f, 18f, 14f, 2f) + "M12 5v14") }
    val StylusFinger by lazy { line("stylus-finger", "M3.5 17c3-9 5-9 5-4s2 3 4 0 3-1 4 2 M17 15l3.5-4") }
    val Trash by lazy { line("trash", "M4 7h16 M9 7V4h6v3 M6 7l1 13h10l1-13 M10 11v6 M14 11v6") }
    val Sun by lazy {
        line(
            "sun",
            "M12 8a4 4 0 1 0 .01 0 M12 2v2.5 M12 19.5V22 M2 12h2.5 M19.5 12H22 M4.9 4.9l1.8 1.8 " +
                "M17.3 17.3l1.8 1.8 M4.9 19.1l1.8-1.8 M17.3 6.7l1.8-1.8",
        )
    }
    val Sliders by lazy { line("sliders", "M3 8h10 M19 8h2 M3 16h2 M11 16h10 M16 5a3 3 0 1 0 .01 0 M8 13a3 3 0 1 0 .01 0") }
    val Close by lazy { line("close", "M6 6l12 12 M18 6L6 18") }
    // What the tape is printed with, and how it is laid down. Each tape icon is a strip with its mark inside.
    val TapePlain by lazy { line("tape-plain", "M3 8h18v8H3z") }
    val TapeDots by lazy { line("tape-dots", "M3 8h18v8H3z M8 12v.01 M12 12v.01 M16 12v.01 M10 10v.01 M14 10v.01 M10 14v.01 M14 14v.01") }
    val TapeLines by lazy { line("tape-lines", "M3 8h18v8H3z M7 16l3-8 M12 16l3-8 M17 16l3-8") }
    val TapeHatch by lazy { line("tape-hatch", "M3 8h18v8H3z M7 16l3-8 M12 16l3-8 M7 8l3 8 M12 8l3 8") }
    val LineAlways by lazy { line("line-always", "M4 19L20 5") }
    val LineHold by lazy { line("line-hold", "M4 19L14 9 M18 4a3 3 0 1 0 .01 0 M18 2.5V4l1 1") }
    val LineNever by lazy { line("line-never", "M3 16c3-9 5-9 7-2s5 7 8-4") }
    val Check by lazy { line("check", "M5 12.5l4.5 4.5L19 7.5") }
    val Minus by lazy { line("minus", "M5 12h14") }
    val Plus by lazy { line("plus", "M12 5v14 M5 12h14") }
    /** The simplified solid version of a tool's icon, small enough to sit three to a button as a group's preview. */
    fun mini(key: String): ImageVector = minis.getOrPut(key) {
        filled("mini-$key", MINI_PATHS[key] ?: "M4 4h16v16H4z")
    }

    private val minis = HashMap<String, ImageVector>()
    private val MINI_PATHS = mapOf(
        "lasso" to "M12 4C7 4 3 7 3 11s4 7 9 7c1 0 2-.1 3-.4l3 2.4-.4-3.500c1.600-1.200 2.400-3 2.400-5.500 0-4-4-7-9-7z",
        "pen" to "M15.500 3.500l5 5L9 20l-6 1 1-6z",
        "pencil" to "M20 3l1 1-9 9-1.500-1.500z M9 13a3.500 3.500 0 1 1-3.500 3.500C5.500 15 7 13 9 13z",
        "highlighter" to "M14 3l7 7-8 8-7-7z M4 14l6 6-4 1-3-3z",
        "eraser" to "M13 3l8 8-9 9H7l-5-5z",
        "tape" to "M3 8l3-3h12l3 3v8l-3 3H6l-3-3z",
        "shapes" to "M3 4h9v9H3z M14 11l7 10h-14z",
        "image" to "M3 5h18v14H3z",
        "text" to "M4 4h16v16H4z",
        "sticky" to "M4 4h16v11l-5 5H4z",
        "sticker" to "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z",
        "table" to "M3 4h8v7H3z M13 4h8v7h-8z M3 13h8v7H3z M13 13h8v7h-8z",
        "clipboard" to "M4 8h16v12H4z M8 3h8v3H8z",
        "add_element" to "M3 4h10v7H3z M3 13h18v7H3z M17 3h2v2h2v2h-2v2h-2V7h-2V5h2z",
        "loupe" to "M10 3a7 7 0 1 0 0 14 7 7 0 0 0 0-14z M15.500 15l5.500 5.500-1.500 1.500-5.500-5.500z",
        "ruler" to "M3 15L15 3l6 6L9 21z",
        "recorder" to "M9 3h6v11a3 3 0 0 1-6 0z M11 17h2v4h-2z",
        "laser" to "M12 2l2.500 7.500L22 12l-7.500 2.500L12 22l-2.500-7.500L2 12l7.500-2.500z",
        "settings" to "M12 2l2 3.500 4-.5.500 4 3.500 2-3.500 2-.5 4-4-.5L12 22l-2-3.500-4 .5-.5-4L2 12l3.500-2 .5-4 4 .5z",
        "fill" to "M11 3l9 9-7.500 7.500a2 2 0 0 1-2.800 0L3.500 13.300a2 2 0 0 1 0-2.800z M20 15.500c1.300 1.800 2 3 2 4a2 2 0 1 1-4 0c0-1 .7-2.200 2-4z",
    )
    val Info by lazy { line("info", "M12 3a9 9 0 1 0 .01 0 M12 11v5.5 M12 7.6v.2") }
    val List by lazy { line("list", "M4 5.5h16 M4 10h16 M4 14.5h16 M4 19h16", width = 2.2f) }
    val NaturalNib by lazy { line("natural", "M4 20.2L13.6 3.8 20.2 10.2z M11.9 7.2l5.3 5.3 M4 20.2l2.6-.7") }
    /** The ball tip: a flared barrel ending in a rounded tip, like the reference's, pointing down and left. */
    val BallNib by lazy { line("ball", "M13.6 3.6l7.2 6.2-8.4 5.4 M13.6 3.6L7.6 11.4 M7.6 11.4l4.8 3.8 M7.6 11.4C3.8 12.4 2.8 16.4 5 18.8 7.2 21 10.8 18.6 12.4 15.2") }
    val CalligraphyNib by lazy {
        line("calligraphy", "M4.2 11.2l6.6-2.2 5.8 5.8-2.9 4.4H4.2z M4.2 11.2v7.8 M9.6 14l-5.2 5.2 M12 7.8L17.8 13.6A1.7 1.7 0 0 0 20.2 11.2L14.4 5.4A1.7 1.7 0 0 0 12 7.8z")
    }

    private fun line(name: String, path: String, width: Float = STROKE_WIDTH) = ImageVector.Builder(
        name,
        ICON_DP.dp,
        ICON_DP.dp,
        ICON_DP,
        ICON_DP,
    ).addPath(
        PathParser().parsePathString(path).toNodes(),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()

    private fun filled(name: String, path: String) = ImageVector.Builder(
        name,
        ICON_DP.dp,
        ICON_DP.dp,
        ICON_DP,
        ICON_DP,
    ).addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black)).build()

    /**
     * Three squares overlapping from the top left to the bottom right, read as a stack seen from above: the top one is the front and
     * the bottom one the back. The one at [solid] (0 top, 1 middle, 2 bottom) is filled.
     */
    private fun stack(name: String, solid: Int): ImageVector {
        val squares = listOf(
            "M3 3h9v9H3z",
            "M7.5 7.5h9v9h-9z",
            "M12 12h9v9h-9z",
        )
        val builder = ImageVector.Builder(name, ICON_DP.dp, ICON_DP.dp, ICON_DP, ICON_DP)
        // The bottom one first and the top one last, so the top one covers the lines of the ones under it.
        for (k in squares.indices.reversed()) {
            val nodes = PathParser().parsePathString(squares[k]).toNodes()
            if (k == solid) {
                builder.addPath(nodes, fill = SolidColor(Color.Black), stroke = SolidColor(Color.Black), strokeLineWidth = STROKE_WIDTH, strokeLineJoin = StrokeJoin.Round)
            } else {
                builder.addPath(nodes, stroke = SolidColor(Color.Black), strokeLineWidth = STROKE_WIDTH, strokeLineJoin = StrokeJoin.Round)
            }
        }
        return builder.build()
    }

    val BringToFront by lazy { stack("bring-to-front", 0) }
    val MoveToMiddle by lazy { stack("move-to-middle", 1) }
    val SendToBack by lazy { stack("send-to-back", 2) }

    private fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float): String {
        val innerW = w - 2 * r
        val innerH = h - 2 * r
        return "M${x + r} ${y}h${innerW}a$r $r 0 0 1 $r ${r}v${innerH}a$r $r 0 0 1 -$r ${r}h-${innerW}" +
            "a$r $r 0 0 1 -$r -${r}v-${innerH}a$r $r 0 0 1 $r -${r}z "
    }

    private fun dashedCircle(): String = buildString {
        for (k in 0 until DASHES) {
            val from = (k * 360f / DASHES + DASH_GAP_DEGREES) * DEGREES_TO_RADIANS
            val to = ((k + 1) * 360f / DASHES - DASH_GAP_DEGREES) * DEGREES_TO_RADIANS
            append("M${12f + 9f * cos(from)} ${12f + 9f * sin(from)}")
            append("A9 9 0 0 1 ${12f + 9f * cos(to)} ${12f + 9f * sin(to)} ")
        }
    }

    /** A circle of dashes, open at the lower left for the lasso's loop. */
    private fun dashedArc(): String = buildString {
        for (k in 0 until LASSO_DASHES) {
            val from = (LASSO_START_DEGREES + k * LASSO_STEP_DEGREES) * DEGREES_TO_RADIANS
            val to = (LASSO_START_DEGREES + (k + 1) * LASSO_STEP_DEGREES - DASH_GAP_DEGREES) * DEGREES_TO_RADIANS
            append("M${13f + 8f * cos(from)} ${10.5f + 8f * sin(from)}")
            append("A8 8 0 0 1 ${13f + 8f * cos(to)} ${10.5f + 8f * sin(to)} ")
        }
    }

    private fun gear(): String = buildString {
        val step = 360f / GEAR_TEETH
        for (k in 0 until GEAR_TEETH) {
            val a = k * step
            point(if (k == 0) 'M' else 'L', GEAR_INNER, a - step * 0.28f)
            point('L', GEAR_OUTER, a - step * 0.14f)
            point('L', GEAR_OUTER, a + step * 0.14f)
            point('L', GEAR_INNER, a + step * 0.28f)
        }
        append("z ")
    }

    private fun StringBuilder.point(command: Char, radius: Float, degrees: Float) {
        val a = degrees * DEGREES_TO_RADIANS
        append("$command${12f + radius * cos(a)} ${12f + radius * sin(a)}")
    }

    private const val ICON_DP = 24f
    private const val STROKE_WIDTH = 1.7f
    private const val DASHES = 8
    private const val DASH_GAP_DEGREES = 8f
    private const val LASSO_DASHES = 5
    private const val LASSO_START_DEGREES = 170f
    private const val LASSO_STEP_DEGREES = 58f
    private const val GEAR_TEETH = 8
    private const val GEAR_INNER = 7.2f
    private const val GEAR_OUTER = 9.4f
    private const val DEGREES_TO_RADIANS = (PI / 180.0).toFloat()
}
