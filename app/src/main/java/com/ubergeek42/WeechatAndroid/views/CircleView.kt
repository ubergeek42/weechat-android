package com.ubergeek42.WeechatAndroid.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.ubergeek42.WeechatAndroid.upload.f


class CircleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    fun setColor(color: Int) {
        if (color != paint.color) {
            paint.color = color
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val x = width.f / 2
        val y = height.f / 2

        canvas.drawCircle(x, y, x, paint)
    }
}