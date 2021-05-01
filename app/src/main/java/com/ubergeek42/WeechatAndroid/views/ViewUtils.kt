@file:Suppress("NOTHING_TO_INLINE")

package com.ubergeek42.WeechatAndroid.views

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.view.GravityCompat
import androidx.core.view.updateLayoutParams
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.ubergeek42.WeechatAndroid.upload.f
import com.ubergeek42.WeechatAndroid.upload.i
import com.ubergeek42.WeechatAndroid.utils.u
import kotlin.math.absoluteValue
import kotlin.math.pow


val Int.solidColor get() = this or 0xff000000.u


inline fun View.updateMargins(top: Int? = null, bottom: Int? = null, left: Int? = null, right: Int? = null) {
    updateLayoutParams<ViewGroup.MarginLayoutParams> {
        if (top != null) topMargin = top
        if (bottom != null) bottomMargin = bottom
        if (left != null) leftMargin = left
        if (right != null) rightMargin = right
    }
}

inline fun View.updateDimensions(width: Int? = null, height: Int? = null) {
    updateLayoutParams<ViewGroup.MarginLayoutParams> {
        if (width != null) this.width = width
        if (height != null) this.height = height
    }
}


// LinearLayoutManager does not respect setStackFromEnd
// while performing scrollToPositionWithOffset. this is a workaround for this bug.
// see https://issuetracker.google.com/issues/148537196
fun RecyclerView.scrollToPositionWithOffsetFix(position: Int, desiredInvisiblePixels: Int) {
    val linearLayoutManager = layoutManager as LinearLayoutManager
    linearLayoutManager.scrollToPositionWithOffset(position, height - paddingTop - 1)
    post {
        val lastChild = getChildAt(childCount - 1) ?: return@post
        val currentInvisiblePixels = lastChild.bottom - height
        val correction = desiredInvisiblePixels - currentInvisiblePixels
        scrollBy(0, -correction)
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////// scrollers
////////////////////////////////////////////////////////////////////////////////////////////////////

fun interface OnJumpedUpWhileScrollingListener {
    fun onJumpedUpWhileScrolling()
}


////////////////////////////////////////////////////////////////////////////////////////////////////


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
        position < firstVisiblePosition -> firstVisiblePosition to UP
        position > lastVisiblePosition -> lastVisiblePosition to DOWN
        else -> position to UP
    }

    val positionDifference = (position - edge).absoluteValue

    if (positionDifference > JUMP_THRESHOLD) {
        val positionsOverThreshold = positionDifference - JUMP_THRESHOLD
        val positionsToScrollPlus = positionsOverThreshold.f.pow(0.58f).i
        val positionsToScroll = JUMP_THRESHOLD + positionsToScrollPlus
        val positionsToJump = positionDifference - positionsToScroll

        // setting offset to recycler view height is a not very precise but fast enough workaround
        // for the LinearLayoutManager issue mentioned above
        val offset = if (direction == UP) 0 else height
        layoutManager.scrollToPositionWithOffset(edge + direction * positionsToJump, offset)

        if (direction == UP && this is OnJumpedUpWhileScrollingListener) {
            post { onJumpedUpWhileScrolling() }
        }
    }

    smoothScroller.targetPosition = position
    layoutManager.startSmoothScroll(smoothScroller)
}


private const val DOWN = 1
private const val UP = -1
private const val JUMP_THRESHOLD = 30


////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////// drawer
////////////////////////////////////////////////////////////////////////////////////////////////////


// this simply makes sure that onDrawerSlide(Float) is called for all changes,
// including view restoration
abstract class DrawerToggleFix(
    activity: Activity,
    private val drawerLayout: DrawerLayout,
    @StringRes openDrawerContentDescRes: Int,
    @StringRes closeDrawerContentDescRes: Int,
) : ActionBarDrawerToggle(activity, drawerLayout, openDrawerContentDescRes, closeDrawerContentDescRes) {
    private var lastOffset = -1f

    abstract fun onDrawerSlide(offset: Float)

    private fun onDrawerSlideInternal(offset: Float) {
        if (lastOffset != offset) {
            lastOffset = offset
            onDrawerSlide(offset)
        }
    }

    override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
        super.onDrawerSlide(drawerView, slideOffset)
        onDrawerSlideInternal(slideOffset)
    }

    override fun onDrawerOpened(drawerView: View) {
        super.onDrawerOpened(drawerView)
        onDrawerSlideInternal(1f)
    }

    override fun onDrawerClosed(drawerView: View) {
        super.onDrawerClosed(drawerView)
        onDrawerSlideInternal(0f)
    }

    override fun syncState() {
        super.syncState()
        val open = drawerLayout.isDrawerOpen(GravityCompat.START)
        onDrawerSlideInternal(if (open) 1f else 0f)
    }
}