package com.ubergeek42.WeechatAndroid.notifications

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.IconCompat
import com.ubergeek42.WeechatAndroid.media.ContentUriFetcher
import com.ubergeek42.WeechatAndroid.upload.applicationContext
import com.ubergeek42.WeechatAndroid.upload.dp_to_px
import com.ubergeek42.WeechatAndroid.upload.suppress
import com.ubergeek42.WeechatAndroid.utils.Toaster
import com.ubergeek42.WeechatAndroid.views.solidColor
import com.ubergeek42.weechat.fromHexStringToByteArray
import com.ubergeek42.weechat.toHexStringLowercase
import java.io.File
import java.lang.IllegalArgumentException
import java.util.concurrent.ConcurrentHashMap


// Icon.createWithAdaptiveBitmapContentUri() is an API level 30 method;
// on earlier API, the compat library will simply read the icon from Uri
// and reconstruct by calling Icon.createWithAdaptiveBitmap(BitmapFactory.decodeStream(...)).
//
// reading this Uri requires context which compat library doesn't provide;
// thus using Uri Icons leads to a crash on api 29-:
//     java.lang.IllegalArgumentException: Context is required to resolve the file uri of the icon: content://...
//        at androidx.core.graphics.drawable.IconCompat.toIcon(IconCompat.java:572)
//        at androidx.core.graphics.drawable.IconCompat.toIcon(IconCompat.java:529)
//        at androidx.core.app.Person.toAndroidPerson(Person.java:177)
//        at androidx.core.app.NotificationCompat$MessagingStyle$Message.toAndroidMessage(NotificationCompat.java:4002)
private val URI_NOTIFICATIONS_HAVE_BENEFIT = Build.VERSION.SDK_INT >= 30


private interface Icon {
    enum class Shape {
        Square,
        Round,
    }

    data class Key(
        val shape: Shape,
        val colors: Colors,
        val sideLength: Int,
        val textSize: Float,
        val text: String,
    )

    data class Colors(val background: Int, val foreground: Int) {
        companion object {
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

            fun forKey(key: String): Colors {
                val colorIndex = key.djb2Remainder(colorPairs.size)
                return colorPairs[colorIndex]
            }
        }
    }
}


private fun makeBitmap(key: Icon.Key): Bitmap {
    val (shape, colors, sideLength, textSize, text) = key

    val bitmap = Bitmap.createBitmap(sideLength, sideLength, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    paint.color = colors.background

    when (shape) {
        Icon.Shape.Square -> {
            paint.style = Paint.Style.FILL
            canvas.drawPaint(paint)
        }
        Icon.Shape.Round -> {
            val radius = sideLength / 2f
            canvas.drawCircle(radius, radius, radius, paint)
        }
    }

    paint.textSize = textSize
    paint.color = colors.foreground
    paint.typeface = defaultBoldTypeface

    val fontMetrics = paint.fontMetrics
    val ascent = fontMetrics.ascent
    val descent = fontMetrics.descent

    val textWidth = paint.measureText(key.text)
    val x = (sideLength - textWidth) / 2f                       // left x coordinate
    val y = (sideLength + (descent - ascent)) / 2f - descent    // baseline y coordinate

    canvas.drawText(text, x, y, paint)

    return bitmap
}

////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////


private fun CharSequence.getIconCharacters(): String {
    return when {
        isBlank() -> "?"
        startsWith("##") -> subSequence(1, if (length >= 3) 3 else 2)
        length >= 2 -> subSequence(0, 2)
        else -> subSequence(0, 1)
    }.toString()
}


fun obtainLegacyRoundIconBitmap(text: String, colorKey: String): Bitmap {
    val key = Icon.Key(
        shape = Icon.Shape.Round,
        colors = Icon.Colors.forKey(colorKey),
        sideLength = LEGACY_ICON_SIDE_LENGTH,
        textSize = LEGACY_ICON_TEXT_SIZE,
        text = text.getIconCharacters()
    )

    return memoryIconBitmapCache.getOrPut(key) { makeBitmap(key) }
}


fun obtainAdaptiveIcon(text: String, colorKey: String, allowUriIcons: Boolean): IconCompat {
    val key = Icon.Key(
        shape = Icon.Shape.Square,
        colors = Icon.Colors.forKey(colorKey),
        sideLength = ADAPTIVE_ICON_SIDE_LENGTH,
        textSize = ADAPTIVE_ICON_TEXT_SIZE,
        text = text.getIconCharacters()
    )

    return if (URI_NOTIFICATIONS_HAVE_BENEFIT && allowUriIcons) {
        DiskIconCache.retrieve(key)?.let { return it }

        val bitmap = makeBitmap(key)
        val icon = IconCompat.createWithAdaptiveBitmap(bitmap)
        DiskIconCache.store(key, bitmap, icon)
        icon
    } else {
        memoryIconCache.getOrPut(key) { IconCompat.createWithAdaptiveBitmap(makeBitmap(key)) }
    }
}


////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////// cache
////////////////////////////////////////////////////////////////////////////////////////////////////


private val memoryIconBitmapCache = ConcurrentHashMap<Icon.Key, Bitmap>()
private val memoryIconCache = ConcurrentHashMap<Icon.Key, IconCompat>()


fun clearMemoryIconCache() {
    memoryIconBitmapCache.clear()
    memoryIconCache.clear()
}


////////////////////////////////////////////////////////////////////////////////////////////////////


private fun Icon.Key.toFileName(): String {
    return "${shape.name}:${colors.background}:${colors.foreground}:$sideLength:$textSize:$text"
        .encodeToSafeString()
}


@Throws(IllegalArgumentException::class, NumberFormatException::class,
        IndexOutOfBoundsException::class)
private fun String.toIconKey(): Icon.Key {
    val (shape, background, foreground, sideLength, textSize, text) = this
        .decodeFromSafeString()
        .split(":", limit = 6)
    return Icon.Key(
        shape = Icon.Shape.valueOf(shape),
        sideLength = sideLength.toInt(),
        textSize = textSize.toFloat(),
        colors = Icon.Colors(background.toInt(), foreground.toInt()),
        text = text
    )
}


private object DiskIconCache {
    const val DIRECTORY_NAME = "icons"

    val directory = File(applicationContext.cacheDir, DIRECTORY_NAME).apply { mkdirs() }

    // don't use disk cache unless at least 100 MB is free;
    // the app will survive it, but if some icons get removed while we are running
    // the system will be displaying black space instead of icon
    @SuppressLint("UsableSpace")
    val enabled = try {
                      directory.usableSpace > 100 * 1000 * 1000   // 100 MB
                  } catch (e: Exception) {
                      Toaster.ErrorToast.show(e)
                      false
                  }

    val keyToIcon = ConcurrentHashMap<Icon.Key, IconCompat>()

    fun initialize() {
        if (!enabled) return

        notificationHandler.post {
            directory.listFiles()?.forEach { file ->
                suppress<Exception> {
                    val key = file.name.toIconKey()
                    keyToIcon[key] = IconCompat.createWithAdaptiveBitmapContentUri(file.toContentUri())
                }
            }
        }
    }

    // store currently generated memory-heavy icon in cache,
    // but schedule replacing it with a much lighter version of itself
    fun store(key: Icon.Key, bitmap: Bitmap, provisionalIcon: IconCompat) {
        if (!enabled) return

        keyToIcon[key] = provisionalIcon

        notificationHandler.post {
            suppress<Exception>(showToast = true) {
                val file = File(directory, key.toFileName())

                file.outputStream().use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }

                keyToIcon[key] = IconCompat.createWithAdaptiveBitmapContentUri(file.toContentUri())
            }
        }
    }

    fun retrieve(key: Icon.Key) = keyToIcon[key]

    fun File.toContentUri(): Uri {
        return FileProvider.getUriForFile(applicationContext, AUTHORITY, this)
    }
}


fun initializeIconCache() {
    DiskIconCache.initialize()
}


////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////


// see IconCompat createWithAdaptiveBitmap & AdaptiveIconDrawable
private val ADAPTIVE_ICON_SIDE_LENGTH = 108.dp_to_px
private val ICON_VIEWPORT_SIDE_LENGTH = 72.dp_to_px
private val ADAPTIVE_ICON_TEXT_SIZE = ICON_VIEWPORT_SIDE_LENGTH / 2f

private val LEGACY_ICON_SIDE_LENGTH = 48.dp_to_px
private val LEGACY_ICON_TEXT_SIZE = LEGACY_ICON_SIDE_LENGTH / 2f


private val defaultBoldTypeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)


private val AUTHORITY = applicationContext.packageName + ContentUriFetcher.FILE_PROVIDER_SUFFIX


private fun String.encodeToSafeString(): String = this.toByteArray().toHexStringLowercase()
private fun String.decodeFromSafeString(): String = this.fromHexStringToByteArray().decodeToString()


private operator fun <E> List<E>.component6() = this[5]
