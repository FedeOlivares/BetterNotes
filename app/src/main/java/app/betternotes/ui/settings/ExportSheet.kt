package app.betternotes.ui.settings

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.betternotes.R
import app.betternotes.canvas.NoteEditor
import app.betternotes.core.export.ExportFrame
import app.betternotes.core.export.ImageExportOptions
import app.betternotes.core.export.ImageFormat
import app.betternotes.core.export.PAGE_SCALE_RANGE
import app.betternotes.core.export.PageSelection
import app.betternotes.core.format.NotebookArchive
import app.betternotes.core.export.PdfExportOptions
import app.betternotes.core.export.exportFileName
import app.betternotes.core.export.parsePageRange
import app.betternotes.core.geometry.Bounds
import app.betternotes.core.model.Page
import app.betternotes.export.DocumentExporter
import app.betternotes.export.exporter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import kotlin.math.roundToInt

private enum class ExportKind { PDF, IMAGE, NOTEBOOK }

private const val SCALE_STEP = 0.05f
private const val POINTS_PER_INCH = 72f
private const val MM_PER_INCH = 25.4f
private const val THUMBNAIL_PX = 190
private const val MIME_PDF = "application/pdf"

/**
 * The export sheet, matching the owner's screenshot. The Image options have no screenshot, so they
 * reuse what the PDF ones show. The reference's third kind is its own file format, which this app has
 * no use for yet, so the sheet offers two.
 */
@Composable
internal fun ExportSheet(editor: NoteEditor, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalSettingsColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The exporter is for one thread, so thumbnails and the export itself take turns on this one.
    val worker = remember { Dispatchers.IO.limitedParallelism(1) }
    val exporter = remember { editor.exporter() }
    val pages = remember { editor.document.pages.toList() }
    val title = remember { editor.document.title }

    var selected by remember { mutableStateOf(pages.indices.toSet()) }
    var rangeText by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(ExportKind.PDF) }
    var scale by remember { mutableFloatStateOf(1f) }
    var invert by remember { mutableStateOf(false) }
    var rasterize by remember { mutableStateOf(false) }
    var format by remember { mutableStateOf(ImageFormat.PNG) }
    var working by remember { mutableStateOf(false) }

    fun run(write: () -> Int, done: Int = R.string.export_done) {
        working = true
        scope.launch {
            val message = withContext(worker) {
                try {
                    if (write() > 0) done else R.string.export_nothing
                } catch (_: IOException) {
                    R.string.export_failed
                }
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            working = false
            if (message == done) onClose()
        }
    }

    val selection = PageSelection.Pages(selected.sorted())
    val pdfTarget = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MIME_PDF)) { uri ->
        if (uri != null) {
            run({ writeTo(context, uri) { exporter.writePdf(selection, it, PdfExportOptions(scale, invert, rasterize)) } })
        }
    }
    val notebookTarget = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            // The editor saved already, in `editor.exporter()` above.
            val root = editor.store.root
            val repository = app.betternotes.library.LibraryRepository.get(context)
            val linked = app.betternotes.core.format.Binder(editor.store).links().map { repository.documentStore(it).root }
            run({ writeTo(context, uri) { NotebookArchive.write(root, it, linked); 1 } })
        }
    }
    val imagesFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) {
            run({
                exporter.writeImages(selection, ImageExportOptions(format, scale = scale, invert = invert), { name ->
                    openInTree(context, tree, name, format)
                })
            })
        }
    }

    SettingsSheet(stringResource(R.string.export_title), onClose, modifier, headerHeight = 96.dp) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 28.dp).padding(bottom = 26.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                Text(stringResource(R.string.export_select_pages), color = colors.text, fontSize = 21.sp, modifier = Modifier.weight(1f))
                val allSelected = selected.size == pages.size
                Text(
                    stringResource(if (allSelected) R.string.export_deselect_all else R.string.export_select_all),
                    color = colors.text,
                    fontSize = 19.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable {
                        selected = if (allSelected) emptySet() else pages.indices.toSet()
                        rangeText = ""
                    },
                )
            }
            Spacer(Modifier.height(36.dp))
            // The cover, when there is one, is page 0, as in the editor.
            val firstNumber = if (pages.firstOrNull()?.isCover == true) 0 else 1
            PageChooser(pages, exporter, worker, selected, firstNumber) { index ->
                selected = if (index in selected) selected - index else selected + index
                rangeText = ""
            }
            Spacer(Modifier.height(20.dp))
            RangeField(rangeText) { text ->
                rangeText = text
                val parsed = parsePageRange(text, pages.size, firstNumber)
                if (parsed != null) selected = parsed.toSet()
            }
            Spacer(Modifier.height(36.dp))
            Text(stringResource(R.string.export_as), color = colors.text, fontSize = 21.sp)
            Spacer(Modifier.height(20.dp))
            Column(Modifier.fillMaxWidth().padding(horizontal = 55.dp), verticalArrangement = Arrangement.spacedBy(21.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    KindCard(ExportIcons.Pdf, stringResource(R.string.export_pdf), kind == ExportKind.PDF, Modifier.weight(1f)) { kind = ExportKind.PDF }
                    KindCard(ExportIcons.Image, stringResource(R.string.export_image), kind == ExportKind.IMAGE, Modifier.weight(1f)) { kind = ExportKind.IMAGE }
                    KindCard(ExportIcons.Notebook, stringResource(R.string.export_notebook), kind == ExportKind.NOTEBOOK, Modifier.weight(1f)) { kind = ExportKind.NOTEBOOK }
                }
                when (kind) {
                    ExportKind.PDF -> SettingsCard(horizontal = 27.dp, top = 16.dp, bottom = 16.dp) {
                        ScaleRow(scale, sizeLabel(pages, selected, scale, inPixels = false)) { scale = it }
                        ToggleRow(stringResource(R.string.export_invert), invert) { invert = it }
                        ToggleRow(stringResource(R.string.export_rasterize), rasterize, stringResource(R.string.export_rasterize_detail)) { rasterize = it }
                    }
                    ExportKind.NOTEBOOK -> SettingsCard(horizontal = 27.dp, top = 16.dp, bottom = 16.dp) {
                        // The whole notebook, whatever pages are chosen above: someone with this app can open it and edit everything.
                        Text(stringResource(R.string.export_notebook_detail), color = colors.secondary, fontSize = 17.sp, modifier = Modifier.padding(vertical = 10.dp))
                    }
                    ExportKind.IMAGE -> SettingsCard(horizontal = 27.dp, top = 16.dp, bottom = 16.dp) {
                        ScaleRow(scale, sizeLabel(pages, selected, scale, inPixels = true)) { scale = it }
                        ToggleRow(stringResource(R.string.export_invert), invert) { invert = it }
                        Row(horizontalArrangement = Arrangement.spacedBy(11.dp), modifier = Modifier.padding(vertical = 10.dp)) {
                            ChoiceChip(stringResource(R.string.export_png), format == ImageFormat.PNG) { format = ImageFormat.PNG }
                            ChoiceChip(stringResource(R.string.export_jpeg), format == ImageFormat.JPEG) { format = ImageFormat.JPEG }
                        }
                    }
                }
            }
            Spacer(Modifier.height(30.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(38.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                TextButton(stringResource(R.string.export_cancel), enabled = !working, onClick = onClose)
                val ready = !working && (selected.isNotEmpty() || kind == ExportKind.NOTEBOOK)
                TextButton(stringResource(if (working) R.string.export_working else R.string.export_action), enabled = ready) {
                    when (kind) {
                        ExportKind.PDF -> pdfTarget.launch(exportFileName(title, "pdf"))
                        ExportKind.IMAGE -> imagesFolder.launch(null)
                        ExportKind.NOTEBOOK -> notebookTarget.launch(exportFileName(title, NotebookArchive.EXTENSION))
                    }
                }
            }
        }
    }
}

/** Opens a new file for writing, and turns the system's "couldn't" into the same error as any other. */
private fun writeTo(context: Context, uri: Uri, write: (OutputStream) -> Int): Int {
    val stream = context.contentResolver.openOutputStream(uri) ?: throw IOException("No stream for $uri")
    return stream.use(write)
}

private fun openInTree(context: Context, tree: Uri, name: String, format: ImageFormat): OutputStream {
    val resolver = context.contentResolver
    val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    val mime = if (format == ImageFormat.PNG) "image/png" else "image/jpeg"
    val file = DocumentsContract.createDocument(resolver, parent, mime, name) ?: throw IOException("Could not create $name")
    return resolver.openOutputStream(file) ?: throw IOException("No stream for $name")
}

@Composable
private fun PageChooser(
    pages: List<Page>,
    exporter: DocumentExporter,
    worker: CoroutineDispatcher,
    selected: Set<Int>,
    firstNumber: Int,
    onToggle: (Int) -> Unit,
) {
    val thumbnails = remember { mutableStateMapOf<Int, Bitmap>() }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(pages.indices.toList(), key = { it }) { index ->
            LaunchedEffect(index) {
                if (index !in thumbnails) {
                    val made = withContext(worker) {
                        try {
                            exporter.thumbnail(index, THUMBNAIL_PX)
                        } catch (_: IOException) {
                            null
                        }
                    }
                    if (made != null) thumbnails[index] = made
                }
            }
            PageTile(index + firstNumber - 1, thumbnails[index], index in selected) { onToggle(index) }
        }
    }
}

@Composable
private fun PageTile(index: Int, thumbnail: Bitmap?, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalSettingsColors.current
    val shape = RoundedCornerShape(9.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(role = Role.Checkbox, onClick = onClick)) {
        Box(
            Modifier
                .size(95.dp)
                .clip(shape)
                .background(colors.chipIdle)
                .border(1.5.dp, colors.switchOff, shape),
        ) {
            if (thumbnail != null) {
                Image(thumbnail.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            if (selected) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 9.dp)
                        .size(28.dp)
                        .background(colors.accent, CircleShape),
                ) {
                    Icon(SettingsIcons.Check, null, tint = colors.onAccent, modifier = Modifier.size(22.dp))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text((index + 1).toString(), color = colors.secondary, fontSize = 19.5.sp)
    }
}

@Composable
private fun RangeField(value: String, onChange: (String) -> Unit) {
    val colors = LocalSettingsColors.current
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .background(colors.field, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)),
    ) {
        BasicTextField(
            value,
            onChange,
            singleLine = true,
            textStyle = TextStyle(color = colors.text, fontSize = 21.sp),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 18.dp),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(stringResource(R.string.export_range_hint), color = colors.secondary, fontSize = 21.sp)
                }
                inner()
            },
        )
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp).background(colors.secondary))
    }
}

@Composable
private fun KindCard(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalSettingsColors.current
    val shape = RoundedCornerShape(22.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .height(98.dp)
            .clip(shape)
            .background(if (selected) colors.kindSelected else colors.sheet)
            .border(if (selected) 2.5.dp else 1.dp, if (selected) colors.accent else colors.switchOff, shape)
            .clickable(role = Role.RadioButton, onClick = onClick),
    ) {
        Icon(icon, null, tint = if (selected) colors.accent else colors.text, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(8.dp))
        Text(label, color = if (selected) colors.accent else colors.text, fontSize = 17.sp)
    }
}

@Composable
private fun ScaleRow(scale: Float, size: String?, onChange: (Float) -> Unit) {
    val colors = LocalSettingsColors.current
    val range = PAGE_SCALE_RANGE
    fun fromFraction(fraction: Float): Float {
        val raw = range.start + fraction * (range.endInclusive - range.start)
        return ((raw / SCALE_STEP).roundToInt() * SCALE_STEP).coerceIn(range)
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(62.dp)) {
        Text(stringResource(R.string.export_page_scale), color = colors.text, fontSize = 19.5.sp)
        Spacer(Modifier.width(10.dp))
        PlainSlider(
            (scale - range.start) / (range.endInclusive - range.start),
            { onChange(fromFraction(it)) },
            Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(140.dp)) {
            Text(stringResource(R.string.export_percent, (scale * 100f).roundToInt()), color = colors.text, fontSize = 16.5.sp, fontWeight = FontWeight.Medium)
            if (size != null) Text(size, color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** What the first selected page comes out at. A canvas has no fixed size, so it shows none. */
@Composable
private fun sizeLabel(pages: List<Page>, selected: Set<Int>, scale: Float, inPixels: Boolean): String? {
    val first = selected.minOrNull()?.let(pages::getOrNull) ?: return null
    val page = first.size ?: return null
    val area = Bounds(0f, 0f, page.widthPt, page.heightPt)
    if (inPixels) {
        val frame = ExportFrame.forImage(area, ImageExportOptions.DEFAULT_DPI * scale)
        return stringResource(R.string.export_size_px, frame.outputWidth, frame.outputHeight)
    }
    val frame = ExportFrame.forPdf(area, scale)
    val toMm = MM_PER_INCH / POINTS_PER_INCH
    return stringResource(R.string.export_size_mm, (frame.outputWidth * toMm).roundToInt(), (frame.outputHeight * toMm).roundToInt())
}

@Composable
private fun TextButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalSettingsColors.current
    Text(
        label,
        color = if (enabled) colors.text else colors.secondary,
        fontSize = 19.5.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(8.dp),
    )
}

private object ExportIcons {
    val Pdf by lazy { lineIcon("pdf", "M6 3h8l4 4v14H6zM14 3v4h4M9 17c2-1 4-4 4.5-7c.3 3 2 5 4 5.5c-3 0-6 .5-8.5 1.5z") }
    val Notebook by lazy { lineIcon("notebook", "M6.5 3.5h11a1 1 0 0 1 1 1v15a1 1 0 0 1-1 1h-11a1 1 0 0 1-1-1v-15a1 1 0 0 1 1-1zM9.5 3.5v17M12.5 8h3.5") }
    val Image by lazy { lineIcon("image", "M4 5h16v14H4zM4 16l5-5l4 4l3-2.5l4 3.5M15.5 8.5a1.2 1.2 0 1 0 .01 0") }
}
