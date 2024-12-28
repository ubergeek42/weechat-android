package com.ubergeek42.WeechatAndroid.views.snackbar

import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.view.ViewTreeObserver
import androidx.core.view.ViewCompat
import androidx.core.view.marginTop
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks
import com.google.android.material.snackbar.Snackbar
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.fragments.BufferFragment
import com.ubergeek42.WeechatAndroid.upload.applicationContext
import com.ubergeek42.WeechatAndroid.upload.f
import com.ubergeek42.WeechatAndroid.utils.ulet
import com.ubergeek42.WeechatAndroid.views.Insets


class SnackbarPositionController {
    private var snackbar: Snackbar? = null
    private var anchor: View? = null
    private var insets: Insets? = null

    private var animateOnNextLayout = false

    fun setSnackbar(snackbar: Snackbar) {
        this.snackbar = snackbar
        animateOnNextLayout = false
        ViewCompat.setOnApplyWindowInsetsListener(snackbar.view) { _, insets -> insets }
        recalculateAndUpdateBottomMargin(animate = false)
    }

    fun setAnchor(view: View?) {
        if (anchor == view) return

        anchor?.viewTreeObserver?.removeOnGlobalLayoutListener(onGlobalLayoutListener)
        view?.viewTreeObserver?.addOnGlobalLayoutListener(onGlobalLayoutListener)
        anchor = view

        if (view == null || view.isLaidOut) {
            recalculateAndUpdateBottomMargin(animate = true)
        } else {
            animateOnNextLayout = true
        }
    }

    fun setInsets(insets: Insets) {
        this.insets = insets
        recalculateAndUpdateBottomMargin(animate = false)
    }

    private val onGlobalLayoutListener =
        ViewTreeObserver.OnGlobalLayoutListener {
            recalculateAndUpdateBottomMargin(animate = animateOnNextLayout)
            animateOnNextLayout = false
        }

    private fun recalculateAndUpdateBottomMargin(animate: Boolean) = ulet (snackbar) { snackbar ->
        val snackbarView = snackbar.view
        val snackbarViewParent = snackbarView.parent
        val snackbarViewLayoutParams = snackbarView.layoutParams as MarginLayoutParams

        if (!snackbar.isShown || snackbarViewParent !is View) return

        val bottomMargin = anchor?.let { snackbarViewParent.height - it.top + snackbarView.marginTop }
            ?: insets?.bottom
            ?: 0

        val oldBottomMargin = snackbarViewLayoutParams.bottomMargin

        if (oldBottomMargin != bottomMargin) {
            snackbarViewLayoutParams.bottomMargin =  bottomMargin
            snackbarView.requestLayout()

            if (animate) {
                snackbarView.translationY = bottomMargin - oldBottomMargin.f
                snackbarView.animate().translationY(0f).setDuration(shortAnimTime).start()
            }
        }
    }
}


fun SnackbarPositionController.setOrScheduleSettingAnchorAfterPagerChange(
    pointer: Long,
    currentBufferFragment: BufferFragment?,
    fragmentManager: FragmentManager,
) {
    if (pointer == 0L) {
        setAnchor(null)
    } else {
        if (currentBufferFragment != null && currentBufferFragment.isVisible) {
            setAnchor(currentBufferFragment.requireView().findViewById(R.id.bottom_bar))
        } else {
            fragmentManager.registerFragmentLifecycleCallbacks(object : FragmentLifecycleCallbacks() {
                override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
                    if (f is BufferFragment && f.pointer == pointer) {
                        setAnchor(f.requireView().findViewById(R.id.bottom_bar))
                        fragmentManager.unregisterFragmentLifecycleCallbacks(this)
                    }
                }
            }, false)
        }
    }
}


private val shortAnimTime = applicationContext
        .resources.getInteger(android.R.integer.config_shortAnimTime).toLong()
