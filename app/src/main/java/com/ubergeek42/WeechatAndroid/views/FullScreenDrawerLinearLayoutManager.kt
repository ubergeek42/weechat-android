package com.ubergeek42.WeechatAndroid.views

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class FullScreenDrawerLinearLayoutManager(
    context: Context,
    private val recyclerView: RecyclerView,
    private val adapter: RecyclerView.Adapter<*>,
) : LinearLayoutManager(context) {

    private var insetTop = 0
    private var insetBottom = 0

    fun setInsets(top: Int, bottom: Int) {
        if (insetTop != top || insetBottom != bottom) {
            insetTop = top
            insetBottom = bottom
            recyclerView.invalidate()
        }
    }

    private var normalChildHeight = 0

    private inline val canFitAllChildrenOutsideInsets: Boolean get() {
        val childrenHeight = normalChildHeight * adapter.itemCount
        val heightSansInsets = height - insetTop - insetBottom
        return heightSansInsets >= childrenHeight
    }

    override fun measureChildWithMargins(child: View, widthUsed: Int, heightUsed: Int) {
        val position = getPosition(child)

        when (position) {
            0 -> child.setPadding(0, insetTop, 0, 0)
            adapter.itemCount - 1 -> child.setPadding(0, 0, 0,
                    if (canFitAllChildrenOutsideInsets) 0 else insetBottom)
            else -> child.setPadding(0, 0, 0, 0)
        }

        super.measureChildWithMargins(child, widthUsed, heightUsed)

        if (position == 0) {
            val childHeight = child.measuredHeight
            if (childHeight != 0) {
                normalChildHeight = childHeight - insetTop
            }
        }
    }
}
