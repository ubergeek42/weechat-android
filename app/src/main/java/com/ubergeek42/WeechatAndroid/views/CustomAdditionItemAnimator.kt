package com.ubergeek42.WeechatAndroid.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.view.View
import android.view.ViewPropertyAnimator
import androidx.core.view.marginBottom
import androidx.core.view.marginTop
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import com.ubergeek42.WeechatAndroid.service.P
import kotlin.math.sin
import kotlin.random.Random


private typealias VH = RecyclerView.ViewHolder


class CustomAdditionItemAnimator : DefaultItemAnimator() {
    var animationProvider: AnimationProvider = DefaultAnimationProvider
        set(value) {
            field = value
            value.setupItemAnimator(this)
        }

    private val pendingMoves = mutableListOf<VH>()
    private val pendingAdditions = mutableListOf<VH>()

    private val runningMoves = mutableListOf<VH>()
    private val runningMovesToNowhere = mutableListOf<VH>()
    private val runningAdditions = mutableListOf<VH>()

    private var hasPendingChanges = false
    private var hasPendingRemoves = false

    override fun animateRemove(holder: VH): Boolean {
        hasPendingRemoves = true
        return super.animateRemove(holder)
    }

    override fun animateChange(oldHolder: VH, newHolder: VH, fromX: Int, fromY: Int, toX: Int, toY: Int): Boolean {
        hasPendingChanges = true
        return super.animateChange(oldHolder, newHolder, fromX, fromY, toX, toY)
    }

    override fun animateMove(holder: VH, fromX: Int, fromY: Int, toX: Int, toY: Int): Boolean {
        return if (animationProvider !is FlickeringAnimationProvider) {
            super.animateMove(holder, fromX, fromY, toX, toY)
        } else {
            endAnimation(holder)
            val view = holder.itemView
            val actualFromY = fromY + view.translationY
            val deltaY = toY - actualFromY

            if (deltaY == 0f) {
                dispatchMoveFinished(holder)
                false
            } else {
                view.translationY = -deltaY
                pendingMoves.add(holder)
                true
            }
        }
    }

    override fun animateAdd(holder: VH): Boolean {
        return if (animationProvider is DefaultAnimationProvider) {
            super.animateAdd(holder)
        } else {
            endAnimation(holder)
            pendingAdditions.add(holder)
            animationProvider.setupViewBeforeAnimation(holder.itemView)
            true
        }
    }

    override fun runPendingAnimations() {
        super.runPendingAnimations()

        val (pendingConsecutiveTopDisappearingMoves, pendingRegularMoves) =
                separateViewHoldersIntoConsecutiveTopDisappearingAndTheRest(pendingMoves)

        val hasPendingRemoves = this.hasPendingRemoves ||
                pendingConsecutiveTopDisappearingMoves.isNotEmpty()
        val hasPendingMoves = pendingRegularMoves.isNotEmpty()

        val removeDuration = if (hasPendingRemoves) removeDuration else 0
        val changeDuration = if (hasPendingChanges) changeDuration else 0
        val moveDuration = if (hasPendingMoves) moveDuration else 0
        val additionAnimationDelay = removeDuration + maxOf(moveDuration, changeDuration)

        pendingConsecutiveTopDisappearingMoves.forEach { holder ->
            animateMoveToNowhere(holder)
        }

        pendingRegularMoves.forEach { holder ->
            animateMoveImpl(holder, removeDuration)
        }

        pendingAdditions.forEach { holder ->
            animateAddImpl(holder, additionAnimationDelay)
        }

        pendingMoves.clear()
        pendingAdditions.clear()
        this.hasPendingChanges = false
        this.hasPendingRemoves = false
    }

    private fun animateMoveToNowhere(holder: VH) {
        runningMovesToNowhere.add(holder)
        val view = holder.itemView
        val animator = view.animate()

        animator.alpha(0f)
        animator.translationY(view.translationY - P._1dp * 30)
        animator.duration = removeDuration

        animator.setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationCancel(animation: Animator) {
                view.translationY = 0f
                view.alpha = 1f
            }

            override fun onAnimationEnd(animation: Animator) {
                view.translationY = 0f
                view.alpha = 1f
                runningMovesToNowhere.remove(holder)
                animator.cancel()
                animator.reset()
                dispatchMoveFinished(holder)
                dispatchFinishedWhenDone()
            }
        }).start()
    }

    private fun animateMoveImpl(holder: VH, removeAnimationDelay: Long) {
        runningMoves.add(holder)
        val view = holder.itemView
        val animator = view.animate()

        animator.translationY(0f)
        animator.duration = moveDuration
        animator.startDelay = removeAnimationDelay

        animator.setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationCancel(animation: Animator) {
                view.translationY = 0f
            }

            override fun onAnimationEnd(animation: Animator) {
                runningMoves.remove(holder)
                animator.cancel()
                animator.reset()
                dispatchMoveFinished(holder)
                dispatchFinishedWhenDone()
            }
        }).start()
    }

    private fun animateAddImpl(holder: VH, otherAnimationDelay: Long) {
        runningAdditions.add(holder)
        val view = holder.itemView
        val animator = view.animate()

        val animationProvider = this.animationProvider
        animationProvider.setupAnimation(animator, otherAnimationDelay)

        animator.setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationCancel(animation: Animator) {
                animationProvider.fixupViewOnAnimationCancel(view)
            }

            override fun onAnimationEnd(animation: Animator) {
                runningAdditions.remove(holder)
                animator.cancel()
                animator.reset()
                dispatchAddFinished(holder)
                dispatchFinishedWhenDone()
            }
        }).start()
    }

    override fun isRunning() = super.isRunning() ||
            pendingAdditions.isNotEmpty() ||
            runningAdditions.isNotEmpty() ||
            pendingMoves.isNotEmpty() ||
            runningMoves.isNotEmpty() ||
            runningMovesToNowhere.isNotEmpty()

    private fun dispatchFinishedWhenDone() {
        if (!isRunning) dispatchAnimationsFinished()
    }

    override fun endAnimations() {
        (pendingAdditions + pendingMoves).forEach { holder ->
            holder.itemView.translationY = 0f
            holder.itemView.alpha = 1f
            dispatchAddFinished(holder)
        }

        (runningAdditions + runningMoves + runningMovesToNowhere).forEach { holder ->
            holder.itemView.animate().cancel()
            dispatchAddFinished(holder)
        }

        pendingAdditions.clear()
        runningAdditions.clear()

        super.endAnimations()
    }
}


////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////


interface AnimationProvider {
    fun setupItemAnimator(itemAnimator: DefaultItemAnimator) {}
    fun setupViewBeforeAnimation(view: View) {}
    fun fixupViewOnAnimationCancel(view: View) {}
    fun setupAnimation(animator: ViewPropertyAnimator, otherAnimationsDelay: Long) {}
}


object DefaultAnimationProvider : AnimationProvider {
    override fun setupItemAnimator(itemAnimator: DefaultItemAnimator) {
        itemAnimator.moveDuration = LONG
    }
}


object FlickeringAnimationProvider : AnimationProvider {
    override fun setupItemAnimator(itemAnimator: DefaultItemAnimator) {
        itemAnimator.moveDuration = LONG
    }

    override fun setupViewBeforeAnimation(view: View) {
        view.alpha = 0f
    }

    override fun fixupViewOnAnimationCancel(view: View) {
        view.alpha = 1f
    }

    override fun setupAnimation(animator: ViewPropertyAnimator, otherAnimationsDelay: Long) {
        animator.alpha(1f)
        animator.interpolator = FlickeringInterpolator
        animator.duration = LONG
        animator.startDelay = otherAnimationsDelay + Random.nextLong(LONG / 6)
    }
}


// this assumes there's only one view added at the bottom, else this won't work well
// this applies sliding animation to the added item~~s~~
// using default interpolator and the same duration that is used to move other items up
// the duration is shorter here; it looks more smooth this way
object SlidingFromBottomAnimationProvider : AnimationProvider {
    override fun setupItemAnimator(itemAnimator: DefaultItemAnimator) {
        itemAnimator.moveDuration = SHORT
    }

    override fun setupViewBeforeAnimation(view: View) {
        view.translationY = view.height.toFloat()
    }

    override fun fixupViewOnAnimationCancel(view: View) {
        view.translationY = 0f
    }

    override fun setupAnimation(animator: ViewPropertyAnimator, otherAnimationsDelay: Long) {
        animator.translationY(0f)
        animator.duration = SHORT
        animator.startDelay = 0
    }
}


// https://www.desmos.com/calculator/29szyffa3p
private object FlickeringInterpolator : TimeInterpolator {
    override fun getInterpolation(input: Float): Float {
        return (input + sin(input * Math.PI * 6).toFloat() / 3).coerceIn(0f, 1f)
    }
}


private val defaultInterpolator = ValueAnimator().interpolator


private fun ViewPropertyAnimator.reset() {
    startDelay = 0
    interpolator = defaultInterpolator
}


private const val SHORT = 120L  // ms
private const val LONG = 250L   // ms


private fun separateViewHoldersIntoConsecutiveTopDisappearingAndTheRest(source: Collection<VH>):
        Pair<Collection<VH>, Collection<VH>> {
    var previousView: View? = null
    val consecutiveViews = source
            .sortedBy { it.itemView.bottom }
            .takeWhile { holder ->
                val nextView = holder.itemView
                val consecutive = previousView == null ||
                        nextView.topIncludingMargin == previousView!!.bottomIncludingMargin
                previousView = nextView
                consecutive
            }
    return if (consecutiveViews.isNotEmpty() && consecutiveViews.all { it.itemView.bottom < 0 }) {
        Pair(consecutiveViews, source - consecutiveViews)
    } else {
        Pair(emptyList(), source)
    }
}


inline private val View.topIncludingMargin get() = top - marginTop
inline private val View.bottomIncludingMargin get() = bottom + marginBottom
