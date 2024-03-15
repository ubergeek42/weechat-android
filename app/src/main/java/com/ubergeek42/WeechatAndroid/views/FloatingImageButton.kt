package com.ubergeek42.WeechatAndroid.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import androidx.appcompat.widget.AppCompatImageButton
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.WeechatAndroid.upload.applicationContext
import com.ubergeek42.WeechatAndroid.upload.f


open class FloatingImageButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AppCompatImageButton(context, attrs) {
    fun show(animate: Boolean = true) {
        if (visibility != VISIBLE) {
            visibility = VISIBLE
            if (animate) startAnimation(makeShowAnimation())
        }
    }

    fun hide(animate: Boolean = true) {
        if (visibility != INVISIBLE) {
            visibility = INVISIBLE
            if (animate) startAnimation(makeHideAnimation())
        }
    }

    private fun makeShowAnimation() = ScaleAnimation(
        0f, 1f, 0f, 1f,
        Animation.RELATIVE_TO_SELF, 0.5f,
        Animation.RELATIVE_TO_SELF, 0.5f)
            .apply { duration = shortAnimTime }

    private fun makeHideAnimation() = ScaleAnimation(
        1f, .5f, 1f, 0.5f,
        Animation.RELATIVE_TO_SELF, 0.5f,
        Animation.RELATIVE_TO_SELF, 0.5f)
            .apply { duration = shortAnimTime / 2 }
}


class CircularImageButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FloatingImageButton(context, attrs) {
    init {
        background = null
        outlineProvider = pillOutlineProvider
        clipToOutline = true
    }

    override fun setBackgroundColor(color: Int) {
        if (backgroundPaint.color != color) {
            backgroundPaint.color = color
            invalidate()
        }
    }

    private val backgroundPaint = Paint().apply { style = Paint.Style.FILL }

    override fun onDraw(canvas: Canvas) {
        canvas.drawPaint(backgroundPaint)
        super.onDraw(canvas)
    }
}


class RectangularImageButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FloatingImageButton(context, attrs) {
    init {
        background = null
        outlineProvider = RoundedRectangleOutlineProvider(10 * P._1dp)
        clipToOutline = true
    }
}


private val pillOutlineProvider = object : ViewOutlineProvider() {
    override fun getOutline(view: View, outline: Outline) {
        outline.setRoundRect(0, 0, view.width, view.height, view.height.f / 2)
    }
}


class RoundedRectangleOutlineProvider(private val cornerRadius: Float) : ViewOutlineProvider() {
    override fun getOutline(view: View, outline: Outline) {
        outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
    }
}


private val shortAnimTime = applicationContext
        .resources.getInteger(android.R.integer.config_shortAnimTime).toLong()
