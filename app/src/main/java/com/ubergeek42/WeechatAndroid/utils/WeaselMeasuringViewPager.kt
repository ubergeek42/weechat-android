package com.ubergeek42.WeechatAndroid.utils

import android.content.Context
import android.util.AttributeSet
import androidx.core.graphics.Insets
import com.ubergeek42.WeechatAndroid.views.onSystemBarsInsetsChanged

class WeaselMeasuringViewPager : ViewPagerFix {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    private var insets = Insets.NONE

    init { onSystemBarsInsetsChanged { insets = it } }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        measuredWidth.let {
            if (it > 0) weaselWidth = it - insets.left - insets.right
        }
    }

    var weaselWidth = 0
}