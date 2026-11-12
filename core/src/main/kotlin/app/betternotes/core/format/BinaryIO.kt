package app.betternotes.core.format

import java.io.IOException
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/** The file is damaged or truncated, or it is not one of ours. */
class CorruptFileException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** The file was written by a newer version of the app, so reading it could lose data. */
class UnsupportedVersionException(val version: Int) : IOException("Unsupported file version $version")

internal const val FORMAT_VERSION = 1

/** The library file is at version 2 (release 1.4): a folder gained its own color, opacity and file-preview switch. Version 1 files still open. */
internal const val LIBRARY_FORMAT_VERSION = 2

/**
 * The document and settings files are at version 2, because they hold custom layouts: a layout gained a paper color,
 * a base pattern and book style (2), and the positions of a frame's movable lines (3), and a page the angle it is shown at (4), and the cover page's design (5, release 1.4.1), and the planner: a document kind and a page template that holds its page and dates (6, release 1.6), and the planner's colors and the handwriting in its date blanks (7, release 2.3.0). Older files still open, with the defaults.
 * The other files are still at [FORMAT_VERSION].
 */
internal const val LAYOUT_FORMAT_VERSION = 7

/**
 * Version 14 of a document file holds a planner whose elements the owner restyled (a planner page template with its styles by role and by element).
 * Only a document that has one is written at 14; every other keeps [LAYOUT_FORMAT_VERSION], byte for byte. 8 to 13 are left to other changes.
 * The settings and library files never use it. This is the newest version a document file can be read at.
 */
internal const val PLANNER_ROLES_FORMAT_VERSION = 14

/**
 * The page files are at version 3: shapes gained a tip length and width (2), and ink a creative brush kind (3).
 * Version 4 added the tape and its shadow; 5 (release 1.3) added a locked flag on every item, a sticky note's keep-open, an image's opacity and the highlighter's no-overlap.
 * Version 6 (release 1.4) added the paint bucket's fill; 7 (release 2.0.1) the parts of a text box that look different from the rest; 8 (release 2.1) the added font of a text box and of its parts; 9 the group an item belongs to; 10 a text box that is a list. Older pages still open, with the defaults. The other files are still at [FORMAT_VERSION].
 */
internal const val PAGE_FORMAT_VERSION = 10

/**
 * Version 11 (layers) adds, after the items, the page's layer table and how many items each layer holds. Only a page that has more than one layer, or a
 * layer that is hidden or named, is written at this version; every other page is written at [PAGE_FORMAT_VERSION], exactly as before.
 * Versions 9 and 10 belong to other changes. This is the newest version a page file can be read at.
 */
internal const val PAGE_LAYERS_FORMAT_VERSION = 11

/**
 * Version 12 adds a table's header color and a shape's mirror, as item tags of their own. Only a page that holds one of them is written at this
 * version (with the layer table, as every version from 11 has); every other page keeps its bytes. This is the newest version a page file can be read at.
 */
internal const val PAGE_TOOLS_FORMAT_VERSION = 12

/**
 * Version 15 adds the pattern of a paint bucket fill (marks over its color), as an item tag of its own. Only a page that holds one is written at
 * this version (with the layer table, as every version from 11 has); every other page keeps its bytes. Versions 13 and 14 belong to other
 * changes. This is the newest version a page file can be read at.
 */
internal const val PAGE_PATTERN_FILL_FORMAT_VERSION = 15

/**
 * Version 16 adds the background color of a text box, as an item tag of its own. Only a page that holds a text box with one is written at this version (with the layer table, as every version from 11 has); every other page keeps its bytes. This is the newest version
 * a page file can be read at.
 */
internal const val PAGE_TEXT_COLORS_FORMAT_VERSION = 16

/**
 * Version 17 adds the color of single letters of a text box, as an item tag of its own. Only a page that holds a text box with such letters is
 * written at this version (with the layer table, as every version from 11 has); every other page keeps its bytes. This is the newest version a
 * page file can be read at.
 */
internal const val PAGE_SPAN_COLORS_FORMAT_VERSION = 17

private const val MAGIC_SIZE = 4
private const val CRC_SIZE = 4

/**
 * The container shared by every file: 4 magic bytes, the format version as a varint, the body, and
 * a CRC-32 of everything before it. All numbers are little-endian, which is what ARM runs natively.
 */
internal fun encodeFile(magic: String, body: ByteWriter.() -> Unit): ByteArray = encodeFile(magic, FORMAT_VERSION, body)

internal fun encodeFile(magic: String, version: Int, body: ByteWriter.() -> Unit): ByteArray {
    val writer = ByteWriter()
    writer.version = version
    writer.bytes(magic.toByteArray(Charsets.US_ASCII))
    writer.varint(version)
    writer.body()
    val crc = CRC32().apply { update(writer.array, 0, writer.size) }.value.toInt()
    writer.int(crc)
    return writer.toByteArray()
}

internal fun <T> decodeFile(bytes: ByteArray, magic: String, body: ByteReader.() -> T): T =
    decodeFile(bytes, magic, FORMAT_VERSION, body)

internal fun <T> decodeFile(bytes: ByteArray, magic: String, newest: Int, body: ByteReader.() -> T): T {
    if (bytes.size < MAGIC_SIZE + 1 + CRC_SIZE) throw CorruptFileException("Only ${bytes.size} bytes")
    val expected = magic.toByteArray(Charsets.US_ASCII)
    for (i in 0 until MAGIC_SIZE) {
        if (bytes[i] != expected[i]) throw CorruptFileException("Not a $magic file")
    }
    val bodyEnd = bytes.size - CRC_SIZE
    val reader = ByteReader(bytes, MAGIC_SIZE, bodyEnd)
    // The version comes before the CRC check because a future format may checksum differently.
    val version = reader.guarded { varint() }
    if (version < 1 || version > newest) throw UnsupportedVersionException(version)
    reader.version = version

    val stored = ByteBuffer.wrap(bytes, bodyEnd, CRC_SIZE).order(ByteOrder.LITTLE_ENDIAN).int
    val actual = CRC32().apply { update(bytes, 0, bodyEnd) }.value.toInt()
    if (stored != actual) throw CorruptFileException("Checksum mismatch")

    return reader.guarded {
        val result = body()
        if (remaining != 0) throw CorruptFileException("$remaining bytes left over")
        result
    }
}

internal class ByteWriter(capacity: Int = 16 * 1024) {
    /** The format version being written, for records that changed between versions. */
    var version = FORMAT_VERSION

    private var buffer = ByteBuffer.allocate(capacity).order(ByteOrder.LITTLE_ENDIAN)

    val array: ByteArray get() = buffer.array()
    val size: Int get() = buffer.position()

    fun toByteArray(): ByteArray = array.copyOf(size)

    fun byte(value: Int) {
        ensure(1)
        buffer.put(value.toByte())
    }

    fun bool(value: Boolean) = byte(if (value) 1 else 0)

    fun int(value: Int) {
        ensure(Int.SIZE_BYTES)
        buffer.putInt(value)
    }

    fun long(value: Long) {
        ensure(Long.SIZE_BYTES)
        buffer.putLong(value)
    }

    fun float(value: Float) {
        ensure(Float.SIZE_BYTES)
        buffer.putFloat(value)
    }

    fun varint(value: Int) {
        require(value >= 0) { "Negative varint $value" }
        var rest = value
        while (rest >= 0x80) {
            byte(rest and 0x7F or 0x80)
            rest = rest ushr 7
        }
        byte(rest)
    }

    fun string(value: String) {
        val utf8 = value.toByteArray(Charsets.UTF_8)
        varint(utf8.size)
        bytes(utf8)
    }

    fun bytes(value: ByteArray) {
        ensure(value.size)
        buffer.put(value)
    }

    /** Writes the array as one block, the fast path for stroke samples. */
    fun floats(value: FloatArray) {
        val byteCount = value.size * Float.SIZE_BYTES
        ensure(byteCount)
        buffer.asFloatBuffer().put(value)
        buffer.position(buffer.position() + byteCount)
    }

    private fun ensure(extra: Int) {
        if (buffer.remaining() >= extra) return
        val grown = ByteBuffer
            .allocate(maxOf(buffer.capacity() * 2, buffer.position() + extra))
            .order(ByteOrder.LITTLE_ENDIAN)
        grown.put(buffer.array(), 0, buffer.position())
        buffer = grown
    }
}

internal class ByteReader(bytes: ByteArray, offset: Int, end: Int) {
    private val buffer = ByteBuffer.wrap(bytes, offset, end - offset).order(ByteOrder.LITTLE_ENDIAN)

    /** The format version of the file being read, for records that changed between versions. */
    var version = FORMAT_VERSION

    /** The group ids of the items being read, innermost last: each item queues its own after its flags and takes it back when finished. */
    val groups = ArrayDeque<Long>()

    val remaining: Int get() = buffer.remaining()

    fun byte(): Int = buffer.get().toInt() and 0xFF

    fun bool(): Boolean = when (val value = byte()) {
        0 -> false
        1 -> true
        else -> throw CorruptFileException("Bad boolean $value")
    }

    fun int(): Int = buffer.getInt()
    fun long(): Long = buffer.getLong()
    fun float(): Float = buffer.getFloat()

    fun varint(): Int {
        var result = 0
        var shift = 0
        while (shift < Int.SIZE_BITS) {
            val next = byte()
            result = result or ((next and 0x7F) shl shift)
            if (next < 0x80) {
                if (result < 0) throw CorruptFileException("Varint too large")
                return result
            }
            shift += 7
        }
        throw CorruptFileException("Varint too long")
    }

    /**
     * A count of entries that take at least [minEntryBytes] each. Checking it against the bytes left
     * stops a damaged count from asking for a huge allocation.
     */
    fun count(minEntryBytes: Int): Int {
        val count = varint()
        if (count.toLong() * minEntryBytes > remaining) throw CorruptFileException("Count $count too large")
        return count
    }

    fun string(): String {
        val size = count(1)
        val value = String(buffer.array(), buffer.arrayOffset() + buffer.position(), size, Charsets.UTF_8)
        buffer.position(buffer.position() + size)
        return value
    }

    /** Skips [size] bytes, for a section this version of the app does not know. */
    fun skip(size: Int) {
        if (size > remaining) throw CorruptFileException("Skipping $size bytes, $remaining left")
        buffer.position(buffer.position() + size)
    }

    fun floats(count: Int): FloatArray {
        val byteCount = count.toLong() * Float.SIZE_BYTES
        if (byteCount > remaining) throw CorruptFileException("$count floats, $remaining bytes left")
        val out = FloatArray(count)
        buffer.asFloatBuffer().get(out)
        buffer.position(buffer.position() + byteCount.toInt())
        return out
    }

    /** Turns what bad bytes cause while decoding, including a model constructor refusing them, into one error. */
    fun <T> guarded(block: ByteReader.() -> T): T = try {
        block()
    } catch (e: BufferUnderflowException) {
        throw CorruptFileException("Unexpected end of file", e)
    } catch (e: IllegalArgumentException) {
        throw CorruptFileException("Invalid value: ${e.message}", e)
    } catch (e: IndexOutOfBoundsException) {
        throw CorruptFileException("Invalid value: ${e.message}", e)
    }
}

/**
 * Stores an enum as its position in an explicit list, so reordering or renaming the enum in the
 * model cannot silently change what old files mean. New values go at the end of the list.
 */
internal class WireEnum<E : Enum<E>>(private vararg val values: E) {
    fun write(writer: ByteWriter, value: E) {
        val code = values.indexOf(value)
        require(code >= 0) { "$value has no wire code" }
        writer.byte(code)
    }

    fun read(reader: ByteReader): E = values[reader.byte()]

    val coded: List<E> get() = values.toList()
}
