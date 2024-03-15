package com.ubergeek42.WeechatAndroid.views

import android.content.Context
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowInsets
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updatePadding
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.ubergeek42.WeechatAndroid.WeechatActivity
import com.ubergeek42.WeechatAndroid.fragments.BufferFragment
import com.ubergeek42.WeechatAndroid.fragments.BufferListFragment
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.WeechatAndroid.upload.i
import com.ubergeek42.WeechatAndroid.utils.ThemeFix


// this can technically work on earlier Android versions,
// e.g. on api 24 (7.0) it works perfectly in dark mode,
// but in light mode the status bar icons remain light
val FULL_SCREEN_DRAWER_ENABLED = Build.VERSION.SDK_INT >= 26    // 8.0, Oreo


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
        if (!FULL_SCREEN_DRAWER_ENABLED) return

        val rootView = activity.ui.pager.rootView

        activity.ui.navigationPadding.visibility = View.VISIBLE

        // todo use WindowCompat.setDecorFitsSystemWindows(window, false)
        // todo needs api 30+? and/or androidx.core:core-ktx:1.5.0-beta02
        rootView.systemUiVisibility = rootView.systemUiVisibility or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

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

    // status bar can be colored since api 21 and have dark icons since api 23
    // navigation bar can be colored since api 21 and can have dark icons since api 26 via
    // SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR, which the theming engine seems to be setting
    // automatically, and since api 27 via android:navigationBarColor
    override fun onStart(owner: LifecycleOwner) {
        if (FULL_SCREEN_DRAWER_ENABLED) {
            activity.ui.navigationPadding.setBackgroundColor(P.colorPrimaryDark)
        } else {
            val systemAreaBackgroundColorIsDark = !ThemeFix.isColorLight(P.colorPrimaryDark)
            val statusBarIconCanBeDark = Build.VERSION.SDK_INT >= 23
            val navigationBarIconsCanBeDark = Build.VERSION.SDK_INT >= 26

            if (systemAreaBackgroundColorIsDark || statusBarIconCanBeDark)
                activity.window.statusBarColor = P.colorPrimaryDark

            if (systemAreaBackgroundColorIsDark || navigationBarIconsCanBeDark)
                activity.window.navigationBarColor = P.colorPrimaryDark
        }
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

        if (!FULL_SCREEN_DRAWER_ENABLED) {
            fragment.ui.bufferList.updateMargins(
                    bottom = if (P.showBufferFilter) filterBarHeight else 0)
            fragment.ui.arrowDown.updateMargins(
                    bottom = if (P.showBufferFilter) filterBarHeight else 0)
        } else {
            insetListeners.add(insetListener)
            insetListener.onInsetsChanged()
        }
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
        if (!FULL_SCREEN_DRAWER_ENABLED) return
        insetListeners.add(insetListener)
        insetListener.onInsetsChanged()
    }

    override fun onStop(owner: LifecycleOwner) {
        if (!FULL_SCREEN_DRAWER_ENABLED) return
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
        @JvmStatic fun obtain(activity: AppCompatActivity) = if (FULL_SCREEN_DRAWER_ENABLED)
            NewSystemAreaHeightExaminer(activity) else OldSystemAreaHeightExaminer(activity)
    }
}


private class OldSystemAreaHeightExaminer(
        activity: AppCompatActivity,
) : SystemAreaHeightExaminer(activity) {
    private lateinit var content: View
    private lateinit var rootView: View

    override fun onCreate(owner: LifecycleOwner) {
        content = activity.findViewById(android.R.id.content)
        rootView = content.rootView
        content.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
    }

    // windowHeight is the height of the activity that includes the height of the status bar and the
    // navigation bar. if the activity is split, this height seems to be only including the system
    // bar that the activity is “touching”. this height doesn't include the keyboard height per se,
    // but if the activity changes size due to the keyboard, this number remains the same.
    // activityHeight is the height of the activity not including any of the system stuff.
    private val globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener listener@{
        // on android 7, if changing the day/night theme in settings, the activity can be recreated
        // right away but with a wrong window height. so we wait until it's actually resumed
        if (activity.lifecycle.currentState != Lifecycle.State.RESUMED) return@listener

        val windowHeight = rootView.height
        val activityHeight = content.height
        val systemAreaHeight = windowHeight - activityHeight

        // weed out some insanity that's happening when the window is in split screen mode.
        // it seems that while resizing some elements can temporarily have the height 0.
        if (windowHeight <= 0 || activityHeight <= 0 || systemAreaHeight <= 0) return@listener

        observer?.onSystemAreaHeightChanged(systemAreaHeight)
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
