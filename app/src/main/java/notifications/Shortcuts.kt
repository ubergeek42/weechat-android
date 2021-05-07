package com.ubergeek42.WeechatAndroid.service

import android.content.Context
import android.content.Intent
import android.content.LocusId
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import com.ubergeek42.WeechatAndroid.WeechatActivity
import com.ubergeek42.WeechatAndroid.relay.Buffer
import com.ubergeek42.WeechatAndroid.relay.BufferList
import com.ubergeek42.WeechatAndroid.upload.applicationContext
import com.ubergeek42.WeechatAndroid.upload.dp_to_px
import com.ubergeek42.WeechatAndroid.upload.f
import com.ubergeek42.WeechatAndroid.utils.Constants
import com.ubergeek42.WeechatAndroid.utils.Utils


fun interface ShortcutReporter {
    fun reportBufferFocused(pointer: Long)
}


val shortcuts: ShortcutReporter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                      Shortcuts(applicationContext)
                                  } else {
                                      ShortcutReporter { /* no op */ }
                                  }


@RequiresApi(30)
class Shortcuts(val context: Context): ShortcutReporter {
    private val shortcutManager = context.getSystemService(ShortcutManager::class.java)!!

    private fun makeShortcutForBuffer(buffer: Buffer): ShortcutInfo {
        val iconBitmap = generateIcon(48.dp_to_px /* shortcutManager.iconMaxWidth */,
                                      48.dp_to_px /* shortcutManager.iconMaxHeight */,
                                      buffer.shortName,
                                      buffer.fullName)

        val intent = Intent(applicationContext, WeechatActivity::class.java).apply {
            putExtra(Constants.EXTRA_BUFFER_POINTER, buffer.pointer)
            action = Utils.pointerToString(buffer.pointer)
        }

        return ShortcutInfo.Builder(context, buffer.fullName)
            .setShortLabel(buffer.shortName)
            .setLongLabel(buffer.shortName)
            .setIcon(Icon.createWithBitmap(iconBitmap))
            .setIntent(intent)
            .setLongLived(true)
            .setLocusId(LocusId(buffer.fullName))
            .build()
    }

    private val pushedShortcuts = mutableSetOf<Long>()

    override fun reportBufferFocused(pointer: Long) {
        val buffer = BufferList.findByPointer(pointer)
        if (buffer != null) {
            if (pointer !in pushedShortcuts) {
                shortcutManager.pushDynamicShortcut(makeShortcutForBuffer(buffer))
                pushedShortcuts.add(pointer)
            }
            shortcutManager.reportShortcutUsed(buffer.fullName)
        }
    }
}


////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////////////////////////////////////////////////////////////////////////// icon gen
////////////////////////////////////////////////////////////////////////////////////////////////////


private val defaultBoldTypeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)


data class Colors(val foreground: Int, val background: Int)


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
fun String.djb2Remainder(divisor: Int): Int {
    var hash: UInt = 0U
    forEach { char ->
        hash = char.toInt().toUInt() + ((hash shl 5) - hash)
    }
    return (hash % divisor.toUInt()).toInt()
}


fun generateIcon(width: Int, height: Int, text: String, colors: Colors): Bitmap {
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
