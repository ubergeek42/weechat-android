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
import com.ubergeek42.WeechatAndroid.upload.applicationContext
import com.ubergeek42.WeechatAndroid.upload.f
import com.ubergeek42.WeechatAndroid.utils.onAnimationEnd


class CircularImageButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AppCompatImageButton(context, attrs) {
    init {
        background = null
        outlineProvider = pillOutlineProvider
        clipToOutline = true
    }

    fun show() {
        if (visibility != VISIBLE) {
            visibility = VISIBLE
            startAnimation(showAnimation)
        }
    }

    fun hide() {
        if (visibility != INVISIBLE) {
            val hideAnimation = makeHideAnimation().apply {
                onAnimationEnd { visibility = INVISIBLE }
            }
            startAnimation(hideAnimation)
        }
    }

    override fun setBackgroundColor(color: Int) {
        if (backgroundPaint.color != color) {
            backgroundPaint.color = color
            invalidate()
        }
    }

    private val backgroundPaint = Paint().apply { style = Paint.Style.FILL }

    override fun onDraw(canvas: Canvas?) {
        canvas?.drawPaint(backgroundPaint)
        super.onDraw(canvas)
    }
}


val pillOutlineProvider = object : ViewOutlineProvider() {
    override fun getOutline(view: View, outline: Outline) {
        outline.setRoundRect(0, 0, view.width, view.height, view.height.f / 2)
    }
}


private val animationDuration = applicationContext
        .resources.getInteger(android.R.integer.config_shortAnimTime).toLong()

val showAnimation = ScaleAnimation(
        0f, 1f, 0f, 1f,
        Animation.RELATIVE_TO_SELF, 0.5f,
        Animation.RELATIVE_TO_SELF, 0.5f)
        .apply { duration = animationDuration }

fun makeHideAnimation() = ScaleAnimation(
        1f, 0f, 1f, 0f,
        Animation.RELATIVE_TO_SELF, 0.5f,
        Animation.RELATIVE_TO_SELF, 0.5f)
        .apply { duration = animationDuration }


