package com.ubergeek42.WeechatAndroid.notifications

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.ubergeek42.WeechatAndroid.upload.dp_to_px
import com.ubergeek42.WeechatAndroid.views.solidColor


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


fun generateIcon(text: String, colorKey: String): Bitmap {
    val cutText = when {
        text.isBlank() -> "?"
        text.startsWith("##") -> text.subSequence(1, if (text.length >= 3) 3 else 2)
        text.length >= 2 -> text.subSequence(0, 2)
        else -> text.subSequence(0, 1)
    }

    val colorIndex = colorKey.djb2Remainder(colorPairs.size)

    return generateIconBitmap(ICON_SIDE_LENGTH,
                        ICON_SIDE_LENGTH,
                        cutText.toString(),
                        ICON_TEXT_SIZE,
                        colorPairs[colorIndex])
}


// see IconCompat createWithAdaptiveBitmap & AdaptiveIconDrawable
private val ICON_SIDE_LENGTH = 108.dp_to_px
private val ICON_VIEWPORT_SIDE_LENGTH = 72.dp_to_px
private val ICON_TEXT_SIZE = ICON_VIEWPORT_SIDE_LENGTH / 2f

private val defaultBoldTypeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
