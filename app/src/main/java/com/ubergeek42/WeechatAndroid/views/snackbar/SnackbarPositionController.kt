package com.ubergeek42.WeechatAndroid.views.snackbar

import android.view.View
import android.view.ViewTreeObserver
import androidx.core.view.marginBottom
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
    private var targetTranslationY = 0f

    fun setSnackbar(snackbar: Snackbar) {
        this.snackbar = snackbar
        targetTranslationY = 0f
        animateOnNextLayout = false
        recalculateAndUpdateTranslation(animate = false)
    }

    fun setAnchor(view: View?) {
        if (anchor == view) return

        anchor?.viewTreeObserver?.removeOnGlobalLayoutListener(onGlobalLayoutListener)
        view?.viewTreeObserver?.addOnGlobalLayoutListener(onGlobalLayoutListener)
        anchor = view

        if (view == null || view.isLaidOut) {
            recalculateAndUpdateTranslation(animate = true)
        } else {
            animateOnNextLayout = true
        }
    }

    fun setInsets(insets: Insets) {
        this.insets = insets
        recalculateAndUpdateTranslation(animate = false)
    }

    private val onGlobalLayoutListener =
        ViewTreeObserver.OnGlobalLayoutListener {
            recalculateAndUpdateTranslation(animate = animateOnNextLayout)
            animateOnNextLayout = false
        }

    private fun recalculateAndUpdateTranslation(animate: Boolean) = ulet (snackbar) { snackbar ->
        val snackbarView = snackbar.view
        val snackbarViewParent = snackbarView.parent

        if (!snackbar.isShown || snackbarViewParent !is View) return

        val extraSpaceBottom = anchor?.let { snackbarViewParent.height - it.top }
            ?: insets?.bottom
            ?: 0

        val translationY = snackbar.view.marginBottom - extraSpaceBottom.f

        if (targetTranslationY != translationY) {
            targetTranslationY = translationY

            if (animate) {
                snackbar.view.animate().translationY(translationY).setDuration(shortAnimTime).start()
            } else {
                snackbar.view.translationY = translationY
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
