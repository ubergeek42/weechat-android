package com.ubergeek42.WeechatAndroid.views

import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.ubergeek42.WeechatAndroid.upload.f
import com.ubergeek42.WeechatAndroid.upload.i
import kotlin.math.absoluteValue
import kotlin.math.pow


fun interface OnJumpedUpWhileScrollingListener {
    fun onJumpedUpWhileScrolling()
}


fun RecyclerView.jumpThenSmoothScroll(position: Int) {
    jumpThenSmoothScroll(LinearSmoothScroller(context), position)
}


fun RecyclerView.jumpThenSmoothScrollCentering(position: Int) {
    jumpThenSmoothScroll(CenteringSmoothScroller(this.context), position)
}


private class CenteringSmoothScroller(context: Context) : LinearSmoothScroller(context) {
    override fun calculateDtToFit(viewStart: Int, viewEnd: Int,
                                  boxStart: Int, boxEnd: Int,
                                  snapPreference: Int): Int {
        return boxStart + (boxEnd - boxStart) / 2 - (viewStart + (viewEnd - viewStart) / 2)
    }
}

// this was adapted from this Stack Overflow comment by Vlad:
// https://stackoverflow.com/a/63643036
private fun RecyclerView.jumpThenSmoothScroll(smoothScroller: RecyclerView.SmoothScroller, position: Int) {
    val layoutManager = layoutManager as? LinearLayoutManager ?: return

    val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
    val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()

    val (edge, direction) = when {
        position < firstVisiblePosition -> firstVisiblePosition to -1
        position > lastVisiblePosition -> lastVisiblePosition to 1
        else -> position to -1
    }

    val positionDifference = (position - edge).absoluteValue

    if (positionDifference > JUMP_THRESHOLD) {
        val positionsOverThreshold = positionDifference - JUMP_THRESHOLD
        val positionsToScrollPlus = positionsOverThreshold.f.pow(0.58f).i
        val positionsToScroll = JUMP_THRESHOLD + positionsToScrollPlus
        val positionsToJump = positionDifference - positionsToScroll

        layoutManager.scrollToPositionWithOffset(edge + direction * positionsToJump, 0)

        if (direction == -1 && this is OnJumpedUpWhileScrollingListener) {
            post { onJumpedUpWhileScrolling() }
        }
    }

    smoothScroller.targetPosition = position
    layoutManager.startSmoothScroll(smoothScroller)
}

private const val JUMP_THRESHOLD = 30

