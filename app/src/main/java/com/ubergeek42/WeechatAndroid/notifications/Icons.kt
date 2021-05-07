package com.ubergeek42.WeechatAndroid.notifications

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.ubergeek42.WeechatAndroid.upload.f


private data class Colors(val foreground: Int, val background: Int)


private val colorPairs: List<Colors> = sequence {
    listOf(
        0xffcbce91 to 0xffea738d,
        0xff101820 to 0xfffee715,
        0xffa07855 to 0xffd4b996,
        0xff2d2926 to 0xffe94b3c,
        0xffdaa03d to 0xff616247,
    ).forEach { (left, right) ->
        yield(Colors(left.toInt(), right.toInt()))
        yield(Colors(right.toInt(), left.toInt()))
    }
}.toList()


@OptIn(ExperimentalUnsignedTypes::class)
private fun String.djb2Remainder(divisor: Int): Int {
    var hash = 0U
    forEach { char ->
        hash = char.toInt().toUInt() + ((hash shl 5) - hash)
    }
    return (hash % divisor.toUInt()).toInt()
}


private fun generateIcon(width: Int, height: Int, text: String, colors: Colors): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint()

    val x = width.f / 2
    val y = height.f / 2

    paint.apply {
        flags = Paint.ANTI_ALIAS_FLAG
        color = colors.background
    }

    canvas.drawCircle(x, y, minOf(x, y), paint)

    paint.apply {
        textSize = height / 2f
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


fun generateIcon(width: Int, height: Int, shortName: String, fullName: String): Bitmap {
    val text = when {
        shortName.isBlank() -> "?"
        shortName.startsWith("##") -> shortName.subSequence(1, if (shortName.length >= 3) 3 else 2)
        shortName.length >= 2 -> shortName.subSequence(0, 2)
        else -> shortName.subSequence(0, 1)
    }

    val colorIndex = fullName.djb2Remainder(colorPairs.size)
    return generateIcon(width, height, text.toString(), colorPairs[colorIndex])
}


private val defaultBoldTypeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
