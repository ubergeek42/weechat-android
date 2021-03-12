package com.ubergeek42.WeechatAndroid.views

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class FullScreenDrawerLinearLayoutManager(
    context: Context,
    var recyclerView : RecyclerView,
    var adapter: RecyclerView.Adapter<*>,
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

    override fun measureChildWithMargins(child: View, widthUsed: Int, heightUsed: Int) {
        when (getPosition(child)) {
            0 -> child.setPadding(0, insetTop, 0, 0)
            adapter.itemCount - 1 -> child.setPadding(0, 0, 0, insetBottom)
            else -> child.setPadding(0, 0, 0, 0)
        }

        super.measureChildWithMargins(child, widthUsed, heightUsed)
    }
}
