package com.ubergeek42.WeechatAndroid.utils

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.viewpager.widget.ViewPager

// this is a workaround for a very rare crash
// https://stackoverflow.com/questions/16459196

open class ViewPagerFix : ViewPager {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    override fun onInterceptTouchEvent(ev: MotionEvent?) =
            try {
                super.onInterceptTouchEvent(ev)
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
                false
            }
}