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
import com.ubergeek42.WeechatAndroid.BuildConfig
import com.ubergeek42.WeechatAndroid.service.P
import kotlin.math.sin
import kotlin.random.Random


private val DEBUG = BuildConfig.DEBUG

private const val SHORT_ANIMATION_DURATION = 120L  // ms
private const val LONG_ANIMATION_DURATION = 250L   // ms

private val MOVE_TO_NOWHERE_TRANSLATION = P._1dp * 30


private typealias VH = RecyclerView.ViewHolder


class CustomAdditionItemAnimator : DefaultItemAnimator() {
    var animationProvider: AnimationProvider = DefaultAnimationProvider
        set(value) {
            field = value
            value.setupItemAnimator(this)
        }

    private val pendingMoves = mutableListOf<VH>()
    private val pendingAdditions = mutableListOf<VH>()

    private val runningAnimations = mutableListOf<VH>()

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

        val animationDurations = AnimationDurations(
            if (hasPendingRemoves) removeDuration else 0,
            if (hasPendingChanges) changeDuration else 0,
            if (hasPendingMoves) moveDuration else 0,
        )

        pendingConsecutiveTopDisappearingMoves.forEach { holder ->
            animateMoveToNowhere(holder)    // uses removeDuration
        }

        pendingRegularMoves.forEach { holder ->
            animateMoveImpl(holder, animationDurations)
        }

        pendingAdditions.forEach { holder ->
            animateAddImpl(holder, animationDurations)
        }

        pendingMoves.clear()
        pendingAdditions.clear()
        this.hasPendingChanges = false
        this.hasPendingRemoves = false
    }

    private fun animateMoveToNowhere(holder: VH) {
        holder.startAnimation {
            alpha(0f)
            translationY(holder.itemView.translationY - MOVE_TO_NOWHERE_TRANSLATION)
            duration = removeDuration
        }
    }

    private fun animateMoveImpl(holder: VH, animationDurations: AnimationDurations) {
        holder.startAnimation {
            translationY(0f)
            duration = moveDuration
            startDelay = animationDurations.removeDuration
        }
    }

    private fun animateAddImpl(holder: VH, animationDurations: AnimationDurations) {
        val animationProvider = this.animationProvider
        holder.startAnimation {
            animationProvider.setupAddAnimation(this, animationDurations)
        }
    }

    private inline fun VH.startAnimation(setup: ViewPropertyAnimator.() -> Unit) {
        runningAnimations.add(this)

        itemView.animate().run {
            setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) {
                    itemView.resetAnimatedProperties()
                }

                override fun onAnimationEnd(animation: Animator) {
                    reset()
                    itemView.resetAnimatedProperties()
                    runningAnimations.remove(this@startAnimation)
                    dispatchAnimationFinished(this@startAnimation)
                    if (!isRunning) dispatchAnimationsFinished()
                }
            })

            setup()
            start()
        }
    }

    override fun isRunning() = super.isRunning() ||
            pendingAdditions.isNotEmpty() ||
            pendingMoves.isNotEmpty() ||
            runningAnimations.isNotEmpty()

    override fun endAnimation(holder: RecyclerView.ViewHolder) {
        super.endAnimation(holder)    // this calls animation cancel

        val removedFromPendingAdditions = pendingAdditions.remove(holder)
        val removedFromPendingMoves = pendingMoves.remove(holder)

        if (removedFromPendingAdditions || removedFromPendingMoves) {
            holder.itemView.resetAnimatedProperties()
            dispatchAnimationFinished(holder)
        }

        if (DEBUG) {
            require(!runningAnimations.contains(holder))
            require(!(removedFromPendingMoves && removedFromPendingAdditions))
        }
    }

    override fun endAnimations() {
        (pendingAdditions + pendingMoves).forEach { holder ->
            holder.itemView.resetAnimatedProperties()
            dispatchAnimationFinished(holder)
        }

        runningAnimations.toList().forEach { holder ->
            holder.itemView.animate().cancel()
            dispatchAnimationFinished(holder)
        }

        pendingAdditions.clear()
        pendingMoves.clear()

        if (DEBUG) require(runningAnimations.isEmpty())

        super.endAnimations()   // calls dispatchAnimationsFinished()
    }
}


////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////


interface AnimationProvider {
    fun setupItemAnimator(itemAnimator: DefaultItemAnimator) {}
    fun setupViewBeforeAnimation(view: View) {}
    fun setupAddAnimation(animator: ViewPropertyAnimator, animationDurations: AnimationDurations) {}
}


object DefaultAnimationProvider : AnimationProvider {
    override fun setupItemAnimator(itemAnimator: DefaultItemAnimator) {
        itemAnimator.moveDuration = LONG_ANIMATION_DURATION
    }
}


object FlickeringAnimationProvider : AnimationProvider {
    override fun setupItemAnimator(itemAnimator: DefaultItemAnimator) {
        itemAnimator.moveDuration = LONG_ANIMATION_DURATION
    }

    override fun setupViewBeforeAnimation(view: View) {
        view.alpha = 0f
    }

    override fun setupAddAnimation(animator: ViewPropertyAnimator, animationDurations: AnimationDurations) {
        animator.alpha(1f)
        animator.interpolator = FlickeringInterpolator
        animator.duration = LONG_ANIMATION_DURATION
        val otherAnimationsDelay = animationDurations.removeDuration +
                maxOf(animationDurations.changeDuration, animationDurations.moveDuration)
        animator.startDelay = otherAnimationsDelay + Random.nextLong(LONG_ANIMATION_DURATION / 6)
    }
}


// this assumes there's only one view added at the bottom, else this won't work well
// this applies sliding animation to the added item~~s~~
// using default interpolator and the same duration that is used to move other items up
// the duration is shorter here; it looks more smooth this way
object SlidingFromBottomAnimationProvider : AnimationProvider {
    override fun setupItemAnimator(itemAnimator: DefaultItemAnimator) {
        itemAnimator.moveDuration = SHORT_ANIMATION_DURATION
    }

    override fun setupViewBeforeAnimation(view: View) {
        view.translationY = view.height.toFloat()
    }

    override fun setupAddAnimation(animator: ViewPropertyAnimator, animationDurations: AnimationDurations) {
        animator.translationY(0f)
        animator.duration = SHORT_ANIMATION_DURATION
        animator.startDelay = animationDurations.removeDuration
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
    cancel()
    startDelay = 0
    interpolator = defaultInterpolator
}

private fun View.resetAnimatedProperties() {
    translationY = 0f
    alpha = 1f
}


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


private inline val View.topIncludingMargin get() = top - marginTop
private inline val View.bottomIncludingMargin get() = bottom + marginBottom


class AnimationDurations(
    val removeDuration: Long,
    val changeDuration: Long,
    val moveDuration: Long,
)