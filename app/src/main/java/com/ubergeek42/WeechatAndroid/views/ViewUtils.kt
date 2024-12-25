@file:Suppress("NOTHING_TO_INLINE")

package com.ubergeek42.WeechatAndroid.views

import android.app.Activity
import android.content.Context
import android.content.Context.INPUT_METHOD_SERVICE
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.annotation.StringRes
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.view.GravityCompat
import androidx.core.view.updateLayoutParams
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.WeechatActivity
import com.ubergeek42.WeechatAndroid.upload.applicationContext
import com.ubergeek42.WeechatAndroid.upload.dp_to_px
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


// A bit hacky, but should be safe. Assumes that height of all children is constant.
fun RecyclerView.scrollCenteringWithoutAnimation(position: Int) {
    val layoutManager = layoutManager as? LinearLayoutManager ?: return
    val childHeight = getChildAt(0)?.height ?: 0
    val originalItemAnimator = itemAnimator
    itemAnimator = null
    layoutManager.scrollToPositionWithOffset(position, height / 2 - childHeight / 2 )
    post { itemAnimator = originalItemAnimator }
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


////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////////////////////////////////////////////////////////////////////////// keyboard
////////////////////////////////////////////////////////////////////////////////////////////////////


private val imm = applicationContext.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager

fun View.showSoftwareKeyboard() {
    imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
}

fun View.hideSoftwareKeyboard() {
    imm.hideSoftInputFromWindow(this.windowToken, 0)
}


////////////////////////////////////////////////////////////////////////////////////////////////////


// historical note from the old comment:
// > this method is called from onStart() instead of onCreate() as onCreate() is called when the
// > activities get recreated due to theme/battery state change. for some reason, the activities
// > get recreated even though the user is using another app; if it happens in the wrong screen
// > orientation, the value is wrong.

// gets width of weasel (effectively the recycler view) for LineView. this is a workaround
// necessary in order to circumvent a bug (?) in ViewPager: sometimes, when measuring views, the
// RecyclerView will have a width of 0 (esp. when paging through buffers fast) and hence
// LineView will receive a suggested maximum width of 0 in its onMeasure().
//      note: other views in RecyclerView don't seem to have this problem. they either receive
//      correct values or somehow recover from width 0. the difference seems to lie in the fact
//      that they are inflated, and not created programmatically.
// todo: switch to ViewPager2 and get rid of this nonsense
fun Activity.calculateApproximateWeaselWidth(): Int {
    if (this is WeechatActivity) {
        ui.pager.weaselWidth.let { if (it > 0) return it }
    }

    val windowWidthIsh = window.decorView.width.let {
        if (it > 0) it else resources.displayMetrics.widthPixels
    }

    if (this is WeechatActivity) {
        val drawerAlwaysVisible = !resources.getBoolean(R.bool.slidy)
        if (drawerAlwaysVisible) return windowWidthIsh - 280.dp_to_px
    }

    return windowWidthIsh
}
