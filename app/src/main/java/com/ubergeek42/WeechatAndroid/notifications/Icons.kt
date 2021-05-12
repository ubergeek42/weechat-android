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
import org.apache.commons.codec.DecoderException
import org.apache.commons.codec.binary.Hex
import java.io.File
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
    two(0x38592a, 0xa4ba62)
    two(0xdaa03d, 0x616247)
    two(0xdf6589, 0x3c1053)
    two(0x0063b2, 0x9cc3d5)
    two(0x808080, 0xd6ed17)
    two(0x101820, 0xfee715)
    two(0x00405f, 0xa0dbbf)

    one(0x5b8491, 0xfcfc8a)
    one(0x9e1030, 0xdd4132)
    one(0xffde7c, 0x8e7a45)
    one(0x5f5b8b, 0xe69cfc)

    two(0xa95559, 0xffc288)
    two(0x703a67, 0x998fb5)
    two(0x005142, 0x20ad87)
}.toList()


@OptIn(ExperimentalUnsignedTypes::class)
private fun String.djb2Remainder(divisor: Int): Int {
    var hash = 0U
    forEach { char ->
        hash = char.code.toUInt() + ((hash shl 5) - hash)
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


fun obtainIcon(text: String, colorKey: String, allowUriIcons: Boolean): IconCompat {
    val cutText = when {
        text.isBlank() -> "?"
        text.startsWith("##") -> text.subSequence(1, if (text.length >= 3) 3 else 2)
        text.length >= 2 -> text.subSequence(0, 2)
        else -> text.subSequence(0, 1)
    }.toString()

    val key = DiskIconCache.Key(cutText, colorKey)

    if (allowUriIcons) {
        DiskIconCache.retrieve(key)?.let { cachedIcon ->
            return cachedIcon
        }
    }

    val colorIndex = colorKey.djb2Remainder(colorPairs.size)
    val bitmap = generateIconBitmap(ICON_SIDE_LENGTH,
        ICON_SIDE_LENGTH,
        cutText,
        ICON_TEXT_SIZE,
        colorPairs[colorIndex])

    val icon = IconCompat.createWithAdaptiveBitmap(bitmap)
    DiskIconCache.store(key, bitmap, icon)
    return icon
}


// see IconCompat createWithAdaptiveBitmap & AdaptiveIconDrawable
private val ICON_SIDE_LENGTH = 108.dp_to_px
private val ICON_VIEWPORT_SIDE_LENGTH = 72.dp_to_px
private val ICON_TEXT_SIZE = ICON_VIEWPORT_SIDE_LENGTH / 2f

private val defaultBoldTypeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)


////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////// cache
////////////////////////////////////////////////////////////////////////////////////////////////////


object DiskIconCache {
    private const val DIRECTORY_NAME = "icons"

    private val directory = File(applicationContext.cacheDir, DIRECTORY_NAME).apply { mkdirs() }

    // don't use disk cache unless at least 100 MB is free;
    // the app will survive it, but if some icons get removed while we are running
    // the system will be displaying black space instead of icon
    @SuppressLint("UsableSpace")
    private val enabled = try {
                              directory.usableSpace > 100 * 1000 * 1000   // 100 MB
                          } catch (e: Exception) {
                              Toaster.ErrorToast.show(e)
                              false
                          }

    data class Key(val text: String, val colorKey: String) {
        fun toFileName() = "$text$SEPARATOR$colorKey".encodeToSafeString()

        companion object {
            const val SEPARATOR = " :: "

            @Throws(DecoderException::class, IndexOutOfBoundsException::class)
            fun fromFileName(fileName: String): Key {
                val (text, colorKey) = fileName.decodeFromSafeString().split(SEPARATOR, limit = 2)
                return Key(text, colorKey)
            }
        }
    }

    private val keyToIcon = ConcurrentHashMap<Key, IconCompat>()

    @JvmStatic fun initialize() {
        if (!enabled) return

        statisticsHandler.post {
            directory.listFiles()?.forEach { file ->
                suppress<Exception>(showToast = true) {
                    Key.fromFileName(file.name).let { key ->
                        keyToIcon[key] = IconCompat.createWithAdaptiveBitmapContentUri(file.toContentUri())
                    }
                }
            }
        }
    }

    // store currently generated memory-heavy icon in cache,
    // but schedule replacing it with a much lighter version of itself
    fun store(key: Key, bitmap: Bitmap, provisionalIcon: IconCompat) {
        if (!enabled) return

        keyToIcon[key] = provisionalIcon

        statisticsHandler.post {
            suppress<Exception>(showToast = true) {
                val file = File(directory, key.toFileName())

                file.outputStream().use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }

                keyToIcon[key] = IconCompat.createWithAdaptiveBitmapContentUri(file.toContentUri())
            }
        }
    }

    fun retrieve(key: Key) = keyToIcon[key]

    private fun File.toContentUri(): Uri {
        return FileProvider.getUriForFile(applicationContext, AUTHORITY, this)
    }
}


private val AUTHORITY = applicationContext.packageName + ContentUriFetcher.FILE_PROVIDER_SUFFIX


fun String.encodeToSafeString(): String = String(Hex.encodeHex(this.toByteArray()))
fun String.decodeFromSafeString(): String = String(Hex.decodeHex(this.toCharArray()))