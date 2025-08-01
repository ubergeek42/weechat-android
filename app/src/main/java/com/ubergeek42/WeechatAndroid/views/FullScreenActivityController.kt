package com.ubergeek42.WeechatAndroid.views

import android.content.Context
import android.os.Build
import android.util.TypedValue
import android.view.WindowInsets
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.ubergeek42.WeechatAndroid.WeechatActivity
import com.ubergeek42.WeechatAndroid.fragments.BufferFragment
import com.ubergeek42.WeechatAndroid.fragments.BufferListFragment
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.WeechatAndroid.upload.i


data class Insets(
    val top: Int,
    val bottom: Int,
    val left: Int,
    val right: Int,
)

var windowInsets = Insets(0, 0, 0, 0)


private fun interface InsetListener {
    fun onInsetsChanged()
}


private val insetListeners = mutableListOf<InsetListener>()


////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////////////////////////////////////////////////////////////////////////// activity
////////////////////////////////////////////////////////////////////////////////////////////////////

class WeechatActivityFullScreenController(val activity: WeechatActivity) : DefaultLifecycleObserver {
    fun observeLifecycle() {
        activity.lifecycle.addObserver(this)
    }

    // only used to weed out changes we don't care about
    private var oldWindowInsets = Insets(-1, -1, -1, -1)

    override fun onCreate(owner: LifecycleOwner) {
        val rootView = activity.ui.pager.rootView

        WindowCompat.setDecorFitsSystemWindows(activity.window, false)

        rootView.setOnApplyWindowInsetsListener listener@{ _, libraryInsets ->
            val newWindowInsets = if (Build.VERSION.SDK_INT >= 30) {
                val libraryWindowInsets = libraryInsets.getInsets(
                        WindowInsets.Type.systemBars() or
                        WindowInsets.Type.navigationBars() or
                        WindowInsets.Type.ime())
                Insets(libraryWindowInsets.top,
                       libraryWindowInsets.bottom,
                       libraryWindowInsets.left,
                       libraryWindowInsets.right)
            } else {
                @Suppress("DEPRECATION")
                Insets(libraryInsets.systemWindowInsetTop,
                       libraryInsets.systemWindowInsetBottom,
                       libraryInsets.systemWindowInsetLeft,
                       libraryInsets.systemWindowInsetRight)
            }

            if (oldWindowInsets != newWindowInsets) {
                oldWindowInsets = newWindowInsets
                windowInsets = newWindowInsets
                insetListeners.forEach { it.onInsetsChanged() }
            }

            libraryInsets
        }

        val weechatActivityInsetsListener = InsetListener {
            activity.ui.toolbarContainer.updatePadding(top = windowInsets.top,
                                                       left = windowInsets.left,
                                                       right = windowInsets.right)
            activity.ui.navigationPadding.updateDimensions(height = windowInsets.bottom)
            activity.ui.pager.updateMargins(bottom = windowInsets.bottom)
        }

        insetListeners.add(weechatActivityInsetsListener)
    }

    override fun onStart(owner: LifecycleOwner) {
        activity.ui.navigationPadding.setBackgroundColor(P.colorPrimaryDark)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        insetListeners.clear()
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////////////// buffer list
////////////////////////////////////////////////////////////////////////////////////////////////////

class BufferListFragmentFullScreenController(val fragment: BufferListFragment) : DefaultLifecycleObserver {
    fun observeLifecycle() {
        fragment.lifecycle.addObserver(this)
    }

    private var filterBarHeight = 0

    override fun onStart(owner: LifecycleOwner) {
        if (filterBarHeight == 0) filterBarHeight = fragment.requireContext().getActionBarHeight()

        insetListeners.add(insetListener)
        insetListener.onInsetsChanged()
    }

    override fun onStop(owner: LifecycleOwner) {
        insetListeners.remove(insetListener)
        filterBarHeight = 0
    }

    private val insetListener = InsetListener {
        val ui = fragment.ui
        val layoutManager = ui.bufferList.layoutManager as? FullScreenDrawerLinearLayoutManager

        ui.arrowUp.updateMargins(top = windowInsets.top)

        if (P.showBufferFilter) {
            ui.bufferList.updateMargins(bottom = filterBarHeight + windowInsets.bottom)
            ui.arrowDown.updateMargins(bottom = filterBarHeight + windowInsets.bottom)

            ui.filterInput.updateMargins(bottom = windowInsets.bottom)
            ui.filterInput.updatePadding(left = windowInsets.left)

            ui.filterClear.updateMargins(bottom = windowInsets.bottom)

            layoutManager?.setInsets(windowInsets.top,
                                     0,
                                     windowInsets.left)
        } else {
            ui.bufferList.updateMargins(bottom = 0)
            ui.arrowDown.updateMargins(bottom = windowInsets.bottom)

            layoutManager?.setInsets(windowInsets.top,
                                     windowInsets.bottom,
                                     windowInsets.left)
        }
    }
}


////////////////////////////////////////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////////// buffer fragment
////////////////////////////////////////////////////////////////////////////////////////////////////


class BufferFragmentFullScreenController(val fragment: BufferFragment) : DefaultLifecycleObserver {
    fun observeLifecycle() {
        fragment.lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        insetListeners.add(insetListener)
        insetListener.onInsetsChanged()
    }

    override fun onStop(owner: LifecycleOwner) {
        insetListeners.remove(insetListener)
    }

    private val insetListener = InsetListener {
        val ui = fragment.ui ?: return@InsetListener

        val linesTopPadding = if (fragment.activity is WeechatActivity && P.autoHideActionbar)
                windowInsets.top else 0
        val fabRightMargin = windowInsets.right + (P._1dp * 12).i

        ui.chatLines.updatePadding(top = linesTopPadding,
                                   left = windowInsets.left,
                                   right = windowInsets.right)

        ui.bottomBar.updatePadding(left = windowInsets.left,
                                   right = windowInsets.right)

        ui.scrollToBottomFab.updateMargins(right = fabRightMargin)
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////////// height observer
////////////////////////////////////////////////////////////////////////////////////////////////////


fun interface SystemAreaHeightObserver {
    fun onSystemAreaHeightChanged(systemAreaHeight: Int)
}


abstract class SystemAreaHeightExaminer(
        val activity: AppCompatActivity,
) : DefaultLifecycleObserver {
    fun observeLifecycle() {
        activity.lifecycle.addObserver(this)
    }

    var observer: SystemAreaHeightObserver? = null

    companion object {
        @JvmStatic fun obtain(activity: AppCompatActivity): SystemAreaHeightExaminer =
            NewSystemAreaHeightExaminer(activity)
    }
}


private class NewSystemAreaHeightExaminer(
        activity: AppCompatActivity,
) : SystemAreaHeightExaminer(activity) {
    override fun onCreate(owner: LifecycleOwner) {
        insetListeners.add(insetListener)
    }

    private val insetListener = InsetListener {
        observer?.onSystemAreaHeightChanged(windowInsets.bottom)
    }
}


////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////


fun Context.getActionBarHeight(): Int {
    val typedValue = TypedValue()
    return if (theme.resolveAttribute(android.R.attr.actionBarSize, typedValue, true)) {
        TypedValue.complexToDimensionPixelSize(typedValue.data, resources.displayMetrics)
    } else {
        0
    }
}
