// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package com.ubergeek42.WeechatAndroid.utils

import android.content.Context
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.view.animation.AlphaAnimation
import androidx.annotation.UiThread
import androidx.recyclerview.widget.RecyclerView
import com.ubergeek42.WeechatAndroid.views.OnJumpedUpWhileScrollingListener
import com.ubergeek42.WeechatAndroid.views.jumpThenSmoothScrollCentering


private const val DELAY = 10L
private const val DURATION = 300L
private const val SCROLL_DELAY = 100L


class AnimatedRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : RecyclerView(context, attrs), OnJumpedUpWhileScrollingListener {

    // used to temporarily disable animator to avoid premature fade-in of views
    // note: setItemAnimator(null) can change the number of children
    private val cachedItemAnimator: ItemAnimator? = itemAnimator

    // number of lines already in adapter — these don't need to be animated
    // value of -1 indicates that no animation is requested
    private var linesAlreadyDisplayed = -1

    // line to scroll to after animations complete
    // -1 indicates that no scroll has been suggested
    private var scrollToPosition = -1

    private val manager = object : LinearLayoutManagerFix(getContext(), VERTICAL, false) {
        // there probably should be a simpler way of doing this, but so far this method
        // is the only one i've found that is called reliably on laying out children
        override fun onLayoutCompleted(state: State) {
            super.onLayoutCompleted(state)
            if (childCount > 1) animateIfNeeded()
        }
    }

    init {
        manager.stackFromEnd = true
        layoutManager = manager
        setHasFixedSize(true)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // this is called only when the number of items in adapter is > 1
    // this animates all children of the RecyclerView except the last `linesAlreadyDisplayed`
    @UiThread private fun animateIfNeeded() {
        if (linesAlreadyDisplayed < 0) return

        if (scrollState != SCROLL_STATE_IDLE) {
            onAnimationsCancelled.run()
            return
        }

        val adapterItemCount = (adapter ?: return).itemCount

        var found = false
        var step = 0

        val lastAnimatablePosition = adapterItemCount - linesAlreadyDisplayed - 1
        val lastAnimatableViewHolder = if (lastAnimatablePosition > 0)
                findViewHolderForAdapterPosition(lastAnimatablePosition) else null

        linesAlreadyDisplayed = -1

        if (lastAnimatableViewHolder != null) {
            val lastAnimatableView = lastAnimatableViewHolder.itemView

            itemAnimator = null
            for (i in childCount downTo 0) {
                val view = getChildAt(i)
                if (!found && view === lastAnimatableView) found = true
                if (found) view.animateAlpha(step++)
            }
        }

        if (found) {
            postDelayed(onAnimationsCancelled, (step - 1) * DELAY + DURATION)
        } else {
            onAnimationsCancelled.run()
        }
    }

    private val onAnimationsCancelled = Runnable {
        if (itemAnimator == null) itemAnimator = cachedItemAnimator

        scrollToPosition.let { position ->
            if (position != -1) {
                postDelayed({
                    smoothScrollToPositionFix(position)
                    scrollToPosition = -1
                }, SCROLL_DELAY)
            }
        }
    }

    @UiThread fun requestAnimation() = ulet(adapter) { adapter ->
        linesAlreadyDisplayed = adapter.itemCount - 1
    }

    // this is needed because awakenScrollbars is protected
    @UiThread fun flashScrollbar() {
        awakenScrollBars()
    }

    // todo remove this once #492 is resolved
    override fun setItemAnimator(animator: ItemAnimator?) {
        Assert.assertThat(Looper.myLooper()).isEqualTo(Looper.getMainLooper())
        super.setItemAnimator(animator)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////// smooth scroll to hot item
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @UiThread fun smoothScrollToPositionAfterAnimation(position: Int) {
        if (linesAlreadyDisplayed < 0) {
            smoothScrollToPositionFix(position)
        } else {
            scrollToPosition = position
        }
    }

    @UiThread private fun smoothScrollToPositionFix(position: Int) = ulet(layoutManager) {
        jumpThenSmoothScrollCentering(position)
    }

    var onJumpedUpWhileScrollingListener: OnJumpedUpWhileScrollingListener? = null

    override fun onJumpedUpWhileScrolling() {
        onJumpedUpWhileScrollingListener?.onJumpedUpWhileScrolling()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////// disabling animation for some updates
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @UiThread fun disableAnimationForNextUpdate() {
        itemAnimator = null
    }

    @UiThread fun scheduleAnimationRestoring() {
        if (itemAnimator == null) post {
            if (itemAnimator == null) itemAnimator = cachedItemAnimator
        }
    }

    // todo is this used?
    override fun smoothScrollToPosition(position: Int) {
        if (itemAnimator != null) {
            super.smoothScrollToPosition(position)
        } else {
            super.scrollToPosition(position)
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////// bottom/top detection
    ////////////////////////////////////////////////////////////////////////////////////////////////

    var onBottom = true
        private set

    var onTop = false
        private set

    // todo is this useful?
    override fun onScrolled(dx: Int, dy: Int) {
        if (dy == 0 || adapter == null) return

        val lastVisible = manager.findLastVisibleItemPosition() == adapter!!.itemCount - 1
        val firstVisible = manager.findFirstVisibleItemPosition() == 0

        if (dy < 0 && !lastVisible) {
            onBottom = false
        } else if (dy > 0 && lastVisible) {
            onBottom = true
        }

        if (dy > 0 && !firstVisible) {
            onTop = false
        } else if (dy < 0 && firstVisible) {
            onTop = true
        }
    }

    fun recheckTopBottom() {
        if (adapter == null) return
        val firstCompletelyVisible = manager.findFirstCompletelyVisibleItemPosition()
        if (firstCompletelyVisible == NO_POSITION) return
        val lastVisible = manager.findLastVisibleItemPosition()
        onTop = firstCompletelyVisible == 0
        onBottom = lastVisible == adapter!!.itemCount - 1
    }
}


// use an external ObjectAnimator instead of view.animate() because
// the latter leaves some residue in the ViewPropertyAnimator attached to the view
@UiThread private fun View.animateAlpha(position: Int) {
    AlphaAnimation(0f, 1f).run {
        duration = DURATION
        startOffset = (position * DELAY)
        startAnimation(this)
    }
}