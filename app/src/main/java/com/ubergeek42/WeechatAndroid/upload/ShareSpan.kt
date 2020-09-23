package com.ubergeek42.WeechatAndroid.upload

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ReplacementSpan
import com.ubergeek42.WeechatAndroid.media.Config
import com.ubergeek42.WeechatAndroid.service.P


private val CAN_SHOW_ICON = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
private val ICON_SIZE = if (CAN_SHOW_ICON) 30.dp_to_px else 0

val THUMBNAIL_MAX_WIDTH = 80.dp_to_px
val THUMBNAIL_MAX_HEIGHT = 80.dp_to_px
private val PADDING = 3.dp_to_px
private val LAYOUT_MAX_WIDTH = THUMBNAIL_MAX_WIDTH - (PADDING * 2)
private val LAYOUT_MAX_HEIGHT = THUMBNAIL_MAX_HEIGHT - (PADDING * 2) - ICON_SIZE
private val THUMBNAIL_CORNER_RADIUS = Config.THUMBNAIL_CORNER_RADIUS.f
private val TEXT_SIZE = 13.dp_to_px.f


abstract class ShareSpan(
    val suri: Suri,
) : ReplacementSpan() {
    protected abstract val width: Int
    protected abstract val height: Int

    // see https://stackoverflow.com/a/63948243/1449683
    override fun getSize(paint: Paint, text: CharSequence?,
                         start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        fm?.apply {
            top = -height + bottom
            ascent = top
            descent = bottom
        }
        return width
    }
}


class BitmapShareSpan(suri: Suri, val bitmap: Bitmap) : ShareSpan(suri) {
    override val width = bitmap.width
    override val height = bitmap.height

    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int,
                      x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        canvas.drawBitmap(bitmap, x, (bottom - height).f, null)
    }
}


class NonBitmapShareSpan(suri: Suri) : ShareSpan(suri) {
    override val width = THUMBNAIL_MAX_WIDTH
    override val height = THUMBNAIL_MAX_HEIGHT

    private val layout = StaticLayout(suri.fileName,
            textPaint, LAYOUT_MAX_WIDTH, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
    private val layoutLeft = if (layout.maxLineWidth > LAYOUT_MAX_WIDTH)
            PADDING.f else (width - layout.maxLineWidth) fdiv 2
    private val layoutTop: Float = if (CAN_SHOW_ICON) {
                ICON_SIZE + if (layout.height > LAYOUT_MAX_HEIGHT)
                        0f else (LAYOUT_MAX_HEIGHT - layout.height) fdiv 2
            } else {
                if (layout.height > LAYOUT_MAX_HEIGHT)
                        PADDING.f else (height - layout.height) fdiv 2
            }

    @SuppressLint("NewApi")
    private val iconDrawable = if (!CAN_SHOW_ICON) null else
            resolver.getTypeInfo(suri.mediaType.toString()).icon.loadDrawable(applicationContext)
                    .apply { setBounds(
                            (THUMBNAIL_MAX_WIDTH - ICON_SIZE) / 2,
                            0,
                            (THUMBNAIL_MAX_WIDTH + ICON_SIZE) / 2,
                            ICON_SIZE) }

    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int,
                      x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        backgroundPaint.color = P.toolbarIconColor
        textPaint.color = P.colorPrimary

        canvas.save()
        canvas.translate(x, (bottom - height).f)
        canvas.drawRoundRect(rect, THUMBNAIL_CORNER_RADIUS, THUMBNAIL_CORNER_RADIUS, backgroundPaint)

        iconDrawable?.setTint(0)    // this fixes tinting not changing for some icons
        iconDrawable?.setTint(P.colorPrimary)
        iconDrawable?.draw(canvas)

        canvas.translate(layoutLeft, layoutTop)
        layout.draw(canvas)
        canvas.restore()
    }

    companion object {
        private val rect = RectF(0f, 0f, THUMBNAIL_MAX_WIDTH.f, THUMBNAIL_MAX_HEIGHT.f)
        private val backgroundPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = TEXT_SIZE }
    }
}


val Layout.maxLineWidth : Int get() {
    return (0 until lineCount).maxOfOrNull { getLineWidth(it) }?.toInt() ?: width
}
