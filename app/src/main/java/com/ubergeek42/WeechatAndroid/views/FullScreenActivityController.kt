package com.ubergeek42.WeechatAndroid.views

import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.WeechatActivity
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.WeechatAndroid.utils.ThemeFix
import com.ubergeek42.WeechatAndroid.utils.WeaselMeasuringViewPager


// this can technically work on earlier Android versions,
// e.g. on api 24 (7.0) it works perfectly in dark mode,
// but in light mode the status bar icons remain light
val FULL_SCREEN_DRAWER_ENABLED = Build.VERSION.SDK_INT >= 26    // 8.0, Oreo


object SystemWindowInsets {
    var top = 0
    var bottom = 0
}


fun interface InsetListener {
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

    private lateinit var navigationPadding: View

    override fun onCreate(owner: LifecycleOwner) {
        if (!FULL_SCREEN_DRAWER_ENABLED) return

        val toolbarContainer = activity.findViewById<View>(R.id.toolbar_container)
        val viewPager = activity.findViewById<WeaselMeasuringViewPager>(R.id.main_viewpager)
        val rootView = viewPager.rootView

        navigationPadding = activity.findViewById(R.id.navigation_padding)
        navigationPadding.visibility = View.VISIBLE

        // todo use WindowCompat.setDecorFitsSystemWindows(window, false)
        // todo needs api 30+? and/or androidx.core:core-ktx:1.5.0-beta02
        rootView.systemUiVisibility = rootView.systemUiVisibility or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        rootView.setOnApplyWindowInsetsListener listener@{ _, insets ->
            SystemWindowInsets.top = insets.systemWindowInsetTop
            SystemWindowInsets.bottom = insets.systemWindowInsetBottom

            insetListeners.forEach { it.onInsetsChanged() }

            insets
        }

        val weechatActivityInsetsListener = InsetListener {
            toolbarContainer.updatePadding(top = SystemWindowInsets.top)
            navigationPadding.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                height = SystemWindowInsets.bottom }
            viewPager.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = SystemWindowInsets.bottom }
        }

        insetListeners.add(weechatActivityInsetsListener)
    }

    // status bar can be colored since api 21 and have dark icons since api 23
    // navigation bar can be colored since api 21 and can have dark icons since api 26 via
    // SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR, which the theming engine seems to be setting
    // automatically, and since api 27 via android:navigationBarColor
    override fun onStart(owner: LifecycleOwner) {
        if (FULL_SCREEN_DRAWER_ENABLED) {
            navigationPadding.setBackgroundColor(P.colorPrimaryDark)
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
}

////////////////////////////////////////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////////////// buffer list
////////////////////////////////////////////////////////////////////////////////////////////////////

class BufferListFragmentFullScreenController(val fragment: Fragment) : DefaultLifecycleObserver {
    fun observeLifecycle() {
        fragment.lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        if (!FULL_SCREEN_DRAWER_ENABLED) return

        val bufferListView = fragment.requireView()
        val navigationPadding = bufferListView.findViewById<View>(R.id.navigation_padding)
        val layoutManager = bufferListView.findViewById<RecyclerView>(R.id.recycler)
                .layoutManager as FullScreenDrawerLinearLayoutManager

        navigationPadding.setBackgroundColor(P.colorPrimaryDark)

        val filterBar = bufferListView.findViewById<View>(R.id.filter_bar)

        fun applyInsets() {
            if (P.showBufferFilter) {
                navigationPadding.visibility = View.VISIBLE
                navigationPadding.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    height = SystemWindowInsets.bottom }
                filterBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    bottomMargin = SystemWindowInsets.bottom }
                layoutManager.setInsets(SystemWindowInsets.top, 0)
            } else {
                navigationPadding.visibility = View.GONE
                layoutManager.setInsets(SystemWindowInsets.top, SystemWindowInsets.bottom)
            }
        }

        insetListeners.add(InsetListener { applyInsets() })

        applyInsets()
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////////// height observer
////////////////////////////////////////////////////////////////////////////////////////////////////


fun interface SystemAreaHeightObserver {
    fun onSystemAreaHeightChanged(size: Int)
}


abstract class SystemAreaHeightExaminer {
    var observer: SystemAreaHeightObserver? = null

    abstract fun onActivityCreated(activity: WeechatActivity)
    abstract fun onActivityDestroyed(activity: WeechatActivity)

    companion object {
        @JvmStatic fun obtain() = if (FULL_SCREEN_DRAWER_ENABLED)
            NewSystemAreaHeightExaminer() else OldSystemAreaHeightExaminer()
    }
}


class OldSystemAreaHeightExaminer : SystemAreaHeightExaminer() {
    private lateinit var activity: AppCompatActivity
    private lateinit var content: View
    private lateinit var rootView: View

    override fun onActivityCreated(activity: WeechatActivity) {
        this.activity = activity
        content = activity.findViewById(android.R.id.content)
        rootView = content.rootView
        content.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
    }

    override fun onActivityDestroyed(activity: WeechatActivity) {
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


private class NewSystemAreaHeightExaminer : SystemAreaHeightExaminer() {
    override fun onActivityCreated(activity: WeechatActivity) {
        insetListeners.add(insetListener)
    }

    override fun onActivityDestroyed(activity: WeechatActivity) {
        insetListeners.remove(insetListener)
    }

    private val insetListener = InsetListener {
        observer?.onSystemAreaHeightChanged(SystemWindowInsets.bottom)
    }
}