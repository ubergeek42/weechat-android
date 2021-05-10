package com.ubergeek42.WeechatAndroid.notifications

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.IconCompat
import com.ubergeek42.WeechatAndroid.media.ContentUriFetcher
import com.ubergeek42.WeechatAndroid.upload.applicationContext
import com.ubergeek42.WeechatAndroid.upload.dp_to_px
import com.ubergeek42.WeechatAndroid.upload.suppress
import com.ubergeek42.WeechatAndroid.utils.Toaster
import com.ubergeek42.WeechatAndroid.views.solidColor
import org.apache.commons.codec.binary.Hex
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap


private data class Colors(val background: Int, val foreground: Int)


private val colorPairs: List<Colors> = sequence {
    suspend fun SequenceScope<Colors>.one(a: Int, b: Int) {
        yield(Colors(a.solidColor, b.solidColor))
    }

    suspend fun SequenceScope<Colors>.two(a: Int, b: Int) {
        one(a, b)
        one(b, a)
    }

    two(0xa07855, 0xd4b996)
    two(0x3d3936, 0xe94b3c)
    two(0x2c5f2d, 0x97bc62)
    two(0xdaa03d, 0x616247)
    two(0xdf6589, 0x3c1053)
    two(0x0063b2, 0x9cc3d5)
    two(0x606060, 0xd6ed17)
    two(0x5f4b8b, 0xe69a8d)
    two(0x101820, 0xfee715)
    two(0x00203f, 0xadefd1)

    one(0x5b84b1, 0xfc766a)
    one(0x9e1030, 0xdd4132)
    one(0xffe77a, 0x2c5f2d)
}.toList()


@OptIn(ExperimentalUnsignedTypes::class)
private fun String.djb2Remainder(divisor: Int): Int {
    var hash = 0U
    forEach { char ->
        hash = char.toInt().toUInt() + ((hash shl 5) - hash)
    }
    return (hash % divisor.toUInt()).toInt()
}


@Suppress("SameParameterValue")
private fun generateIconBitmap(
    width: Int,
    height: Int,
    text: String,
    textSize: Float,
    colors: Colors
): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint()

    paint.apply {
        paint.style = Paint.Style.FILL
        flags = Paint.ANTI_ALIAS_FLAG
        color = colors.background
    }

    canvas.drawPaint(paint)

    paint.apply {
        this.textSize = textSize
        color = colors.foreground
        typeface = defaultBoldTypeface
    }

    val fontMetrics = paint.fontMetrics
    val ascent = fontMetrics.ascent
    val descent = fontMetrics.descent

    val textWidth = paint.measureText(text)

    canvas.drawText(
        text,
        (width - textWidth) / 2f,
        (height + (descent - ascent)) / 2f - descent,
        paint
    )

    return bitmap
}


fun obtainIcon(text: String, colorKey: String, supportsUri: Boolean): IconCompat {
    val cutText = when {
        text.isBlank() -> "?"
        text.startsWith("##") -> text.subSequence(1, if (text.length >= 3) 3 else 2)
        text.length >= 2 -> text.subSequence(0, 2)
        else -> text.subSequence(0, 1)
    }.toString()

    if (supportsUri) {
        val key = DiscIconCache.Key(cutText, colorKey)
        val cachedIcon = DiscIconCache.get(key)
        if (cachedIcon != null) return cachedIcon
    } else {
        val key = MemoryIconCache.Key(cutText, colorKey)
        val cachedIcon = MemoryIconCache.get(key)
        if (cachedIcon != null) return cachedIcon
    }

    val colorIndex = colorKey.djb2Remainder(colorPairs.size)
    val bitmap = generateIconBitmap(ICON_SIDE_LENGTH,
        ICON_SIDE_LENGTH,
        cutText,
        ICON_TEXT_SIZE,
        colorPairs[colorIndex])

    if (supportsUri) {
        DiscIconCache.store(DiscIconCache.Key(cutText, colorKey), bitmap)
    } else {
        MemoryIconCache.store(MemoryIconCache.Key(cutText, colorKey), bitmap)
    }

    return IconCompat.createWithAdaptiveBitmap(bitmap)
}


// see IconCompat createWithAdaptiveBitmap & AdaptiveIconDrawable
private val ICON_SIDE_LENGTH = 108.dp_to_px
private val ICON_VIEWPORT_SIDE_LENGTH = 72.dp_to_px
private val ICON_TEXT_SIZE = ICON_VIEWPORT_SIDE_LENGTH / 2f

private val defaultBoldTypeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)


////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////


object MemoryIconCache {
    data class Key(private val text: String, private val colorKey: String)

    private val keyToIcon = ConcurrentHashMap<Key, IconCompat>()

    fun store(key: Key, bitmap: Bitmap) {
        statisticsHandler.post {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            keyToIcon[key] = IconCompat.createWithData(stream.toByteArray(), 0, stream.size())
        }
    }

    fun get(key: Key) = keyToIcon[key]
}


object DiscIconCache {
    private const val DIRECTORY_NAME = "icons"

    private val directory = File(applicationContext.cacheDir, DIRECTORY_NAME).apply { mkdirs() }

    @SuppressLint("UsableSpace")
    private val enabled = try {
                              directory.usableSpace > 100 * 1000 * 1000   // 100 MB
                          } catch (e: Exception) {
                              Toaster.ErrorToast.show(e)
                              false
                          }

    data class Key(private val text: String, private val colorKey: String) {
        fun toFileName() = String(Hex.encodeHex("$text$separator$colorKey".toByteArray()))

        companion object {
            const val separator = " :: "

            fun fromFile(file: File): Key? {
                return try {
                    val (text, colorKey) = String(Hex.decodeHex(file.name))
                            .split(separator, limit = 2)
                    Key(text, colorKey)
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
        }
    }

    private val keyToUri = ConcurrentHashMap<Key, Uri>()

    fun initialize() {
        if (!enabled) return

        statisticsHandler.post {
            directory.listFiles()?.forEach { file ->
                Key.fromFile(file)?.let { key ->
                    keyToUri[key] = file.toContentUri()
                }
            }
        }
    }

    fun store(key: Key, bitmap: Bitmap) {
        if (!enabled) return

        statisticsHandler.post {
            suppress<IOException>(showToast = true) {
                val file = File(directory, key.toFileName())

                File(directory, key.toFileName()).outputStream().use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }

                keyToUri[key] = file.toContentUri()
            }
        }
    }

    fun get(key: Key): IconCompat? {
        return keyToUri[key]?.let { uri ->
            IconCompat.createWithAdaptiveBitmapContentUri(uri)
        }
    }

    private fun File.toContentUri(): Uri {
        return try {
            FileProvider.getUriForFile(
                applicationContext,
                applicationContext.packageName + ContentUriFetcher.FILE_PROVIDER_SUFFIX,
                this)
        } catch (e: Exception) {
            // todo
            throw e
        }
    }
}