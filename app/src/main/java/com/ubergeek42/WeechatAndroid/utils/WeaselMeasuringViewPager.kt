package com.ubergeek42.WeechatAndroid.utils

import android.content.Context
import android.util.AttributeSet
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.WeechatAndroid.views.windowInsets

class WeaselMeasuringViewPager : ViewPagerFix {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        measuredWidth.let {
            if (it > 0) weaselWidth = it - windowInsets.left - windowInsets.right
        }
    }

    var weaselWidth = 0
}