package com.ubergeek42.WeechatAndroid.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.view.View
import android.view.ViewPropertyAnimator
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import com.ubergeek42.cats.Cat
import kotlin.math.sin
import kotlin.random.Random

class CustomAdditionItemAnimator : DefaultItemAnimator() {
    var animationProvider: AnimationProvider = DefaultAnimationProvider
        set(value) {
            field = value
            value.setupItemAnimator(this)
        }

    private val pendingAdditions = mutableListOf<RecyclerView.ViewHolder>()
    private val runningAdditions = mutableListOf<RecyclerView.ViewHolder>()

    private var hasPendingChanges = false
    private var hasPendingMoves = false
    private var hasPendingRemoves = false

    override fun animateChange(oldHolder: RecyclerView.ViewHolder,
                               newHolder: RecyclerView.ViewHolder,
                               fromX: Int, fromY: Int, toX: Int, toY: Int): Boolean {
        hasPendingChanges = true
        return super.animateChange(oldHolder, newHolder, fromX, fromY, toX, toY)
    }

    override fun animateMove(holder: RecyclerView.ViewHolder,
                             fromX: Int, fromY: Int, toX: Int, toY: Int): Boolean {
        hasPendingMoves = true
        return super.animateMove(holder, fromX, fromY, toX, toY)
    }

    override fun animateRemove(holder: RecyclerView.ViewHolder): Boolean {
        hasPendingRemoves = true
        return super.animateRemove(holder)
    }

    @Cat override fun animateAdd(holder: RecyclerView.ViewHolder): Boolean {
        return if (animationProvider is DefaultAnimationProvider) {
            super.animateAdd(holder)
        } else {
            endAnimation(holder)
            pendingAdditions.add(holder)
            animationProvider.setupViewBeforeAnimation(holder.itemView)
            true
        }
    }

    @Cat override fun runPendingAnimations() {
        super.runPendingAnimations()

        val removeDuration = if (hasPendingRemoves) removeDuration else 0
        val moveDuration = if (hasPendingMoves) moveDuration else 0
        val changeDuration = if (hasPendingChanges) changeDuration else 0
        val otherAnimationDelay = removeDuration + maxOf(moveDuration, changeDuration)

        pendingAdditions.forEach { holder ->
            animateAddImpl(holder, otherAnimationDelay)
        }

        pendingAdditions.clear()
        hasPendingChanges = false
        hasPendingMoves = false
        hasPendingRemoves = false
    }

    @Cat private fun animateAddImpl(holder: RecyclerView.ViewHolder, otherAnimationDelay: Long) {
        runningAdditions.add(holder)
        val view = holder.itemView
        val animator = view.animate()

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
            runningAdditions.isNotEmpty()

    private fun dispatchFinishedWhenDone() {
        if (!isRunning) dispatchAnimationsFinished()
    }

    override fun endAnimations() {
        pendingAdditions.forEach { holder ->
            FlickeringAnimationProvider.fixupViewOnAnimationCancel(holder.itemView)
            SlidingFromBottomAnimationProvider.fixupViewOnAnimationCancel(holder.itemView)
            dispatchAddFinished(holder)
        }

        runningAdditions.toList().forEach { holder ->
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
    fun setupItemAnimator(itemAnimator: DefaultItemAnimator)
    fun setupViewBeforeAnimation(view: View)
    fun fixupViewOnAnimationCancel(view: View)
    fun setupAnimation(animator: ViewPropertyAnimator, otherAnimationsDelay: Long)
}


object DefaultAnimationProvider : AnimationProvider {
    override fun setupItemAnimator(itemAnimator: DefaultItemAnimator) {
        itemAnimator.moveDuration = LONG
    }

    override fun setupViewBeforeAnimation(view: View) {}
    override fun fixupViewOnAnimationCancel(view: View) {}
    override fun setupAnimation(animator: ViewPropertyAnimator, otherAnimationsDelay: Long) {}
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
    @Cat override fun setupItemAnimator(itemAnimator: DefaultItemAnimator) {
        itemAnimator.moveDuration = SHORT
    }

    @Cat override fun setupViewBeforeAnimation(view: View) {
        view.translationY = view.height.toFloat()
    }

    @Cat override fun fixupViewOnAnimationCancel(view: View) {
        view.translationY = 0f
    }

    @Cat override fun setupAnimation(animator: ViewPropertyAnimator, otherAnimationsDelay: Long) {
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