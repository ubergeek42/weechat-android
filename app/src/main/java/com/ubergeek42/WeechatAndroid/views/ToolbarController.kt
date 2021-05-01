package com.ubergeek42.WeechatAndroid.views

import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.ubergeek42.WeechatAndroid.WeechatActivity
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.WeechatAndroid.upload.f
import kotlin.math.sign


class ToolbarController(val activity: WeechatActivity) : DefaultLifecycleObserver, SystemAreaHeightObserver {
    fun observeLifecycle() {
        activity.lifecycle.addObserver(this)
        SystemAreaHeightExaminer.obtain(activity).also { it.observer = this }.observeLifecycle()
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        autoHideEnabled = P.autoHideActionbar
    }

    private var autoHideEnabled = true
        set(enabled) {
            if (field != enabled) {
                field = enabled
                activity.ui.toolbarContainer.post {
                    activity.ui.pager.updateMargins(
                        top = if (enabled) 0 else activity.ui.toolbarContainer.height
                    )
                }
            }
        }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private var toolbarShown = true
    private var keyboardVisible = false

    private fun show() {
        if (!toolbarShown) {
            toolbarShown = true
            activity.ui.toolbarContainer.animate()
                    .translationY(0f)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
        }
    }

    private fun hide() {
        if (!autoHideEnabled) return

        if (toolbarShown) {
            toolbarShown = false
            activity.ui.toolbarContainer.animate()
                    .translationY(-activity.ui.toolbarContainer.bottom.f)
                    .setInterpolator(AccelerateInterpolator())
                    .start()
        }

    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private var cumDy = 0

    fun onChatLinesScrolled(dy: Int, touchingTop: Boolean, touchingBottom: Boolean) {
        if (!autoHideEnabled || keyboardVisible || dy == 0) return

        if (cumDy.sign != dy.sign) cumDy = 0
        cumDy += dy

        if (cumDy < -hideToolbarScrollThreshold || cumDy < 0 && touchingTop) hide()
        if (cumDy > showToolbarScrollThreshold || cumDy > 0 && touchingBottom) show()
    }

    fun onPageChangedOrSelected() {
        cumDy = 0
        show()
    }

    private fun onSoftwareKeyboardStateChanged(visible: Boolean) {
        if (keyboardVisible != visible) {
            keyboardVisible = visible

            if (autoHideEnabled && activity.isChatInputOrSearchInputFocused) {
                if (visible) hide() else show()
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private var initialSystemAreaHeight = -1

    override fun onSystemAreaHeightChanged(systemAreaHeight: Int) {
        // note the initial system area (assuming keyboard closed) and return. we should be getting
        // a few more calls to this method without any changes to the height numbers

        // note the initial system area (assuming keyboard closed) and return. we should be getting
        // a few more calls to this method without any changes to the height numbers
        if (initialSystemAreaHeight == -1) {
            initialSystemAreaHeight = systemAreaHeight
            return
        }

        // weed out some insanity that's happening when the window is in split screen mode. it seems
        // that while resizing some elements can temporarily have the height 0.
        if (systemAreaHeight < initialSystemAreaHeight) return

        val keyboardVisible = systemAreaHeight - initialSystemAreaHeight >
                sensibleMinimumSoftwareKeyboardHeight

        onSoftwareKeyboardStateChanged(keyboardVisible)
    }
}


private val sensibleMinimumSoftwareKeyboardHeight = 50 * P._1dp
private val hideToolbarScrollThreshold = P._200dp
private val showToolbarScrollThreshold = P._200dp