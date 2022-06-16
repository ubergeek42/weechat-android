package com.ubergeek42.WeechatAndroid.views

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.text.Layout
import android.text.Spannable
import android.text.StaticLayout
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.WeechatAndroid.upload.f
import com.ubergeek42.WeechatAndroid.upload.i
import com.ubergeek42.WeechatAndroid.utils.invalidatableLazy


// a class that mimics Layout but one that can draw with alpha by drawing into a Bitmap first.
internal class AlphaLayout private constructor(
    private val layout: Layout
) {
    private val bitmapDelegate = invalidatableLazy {
        Bitmap.createBitmap(layout.width, layout.height, Bitmap.Config.ARGB_8888).also {
            layout.draw(Canvas(it))
        }
    }
    private val bitmap by bitmapDelegate

    fun draw(canvas: Canvas, alpha: Float) {
        canvas.drawBitmap(bitmap, 0f, 0f, _alphaPaint.also { it.alpha = (alpha * 255).i })
    }

    fun clearBitmap() = bitmapDelegate.invalidate()

    fun usesCurrentPaint() = layout.paint === P.textPaint

    ////////////////////////////////////////////////////////////////////////////////////////////////

    val paint: Paint get() = layout.paint
    val height: Int get() = layout.height
    fun draw(canvas: Canvas) = layout.draw(canvas)
    fun getLineForVertical(vertical: Int) = layout.getLineForVertical(vertical)
    fun getOffsetForHorizontal(line: Int, horiz: Float) = layout.getOffsetForHorizontal(line, horiz)

    fun getHorizontalTextCoordinatesForLine(line: Int)
            = layout.getParagraphLeft(line).f..layout.getLineMax(line)

    companion object {
        private val ALIGNMENT = Layout.Alignment.ALIGN_NORMAL
        private const val SPACING_MULTIPLIER = 1f
        private const val SPACING_ADDITION = 0f
        private const val INCLUDE_PADDING = false

        // in the super rare case where thumbnail width > screen width
        // text width can be calculated to be less than 0, so set it to a positive value else crash
        @SuppressLint("WrongConstant")
        @Suppress("DEPRECATION")
        @JvmStatic
        fun make(text: Spannable, anyWidth: Int): AlphaLayout {
            val width = if (anyWidth > 0) anyWidth else 100
            val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(text, 0, text.length, P.textPaint, width)
                        .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                        .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
                        .build()
            } else {
                StaticLayout(text, P.textPaint, width,
                        ALIGNMENT, SPACING_MULTIPLIER, SPACING_ADDITION, INCLUDE_PADDING)
            }
            return AlphaLayout(layout)
        }
    }
}

// this is only used in a single method on the main thread
@Suppress("ObjectPropertyName") private val _alphaPaint = Paint()
