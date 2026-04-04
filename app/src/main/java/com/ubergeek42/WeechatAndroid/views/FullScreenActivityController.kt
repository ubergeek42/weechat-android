package com.ubergeek42.WeechatAndroid.views

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.util.TypedValue
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils.setAlphaComponent
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ubergeek42.WeechatAndroid.R


// Sometimes the system bars are temporarily hidden, in particular:
//   * when the window is in a split screen mode, and
//   * when clicking on the paperclip button and getting the “Complete action using” dialog.
// For the second case we may prefer using `getInsetsIgnoringVisibility`
// so that we don't get any jitter in the background.
// However, the split screen situation is more common, so we don't ignore the visibility.
// Are there other cases where the system bars may get temporarily hidden?
fun View.onSystemBarsInsetsChanged(listener: (insets: androidx.core.graphics.Insets) -> Unit) {
    var oldInsets = androidx.core.graphics.Insets.NONE

    ViewCompat.setOnApplyWindowInsetsListener(this) { _, windowInsets ->
        val newInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

        if (oldInsets != newInsets) {
            listener(newInsets)
            oldInsets = newInsets
        }

        windowInsets
    }
}

// IME insets can't be queried ignoring visibility, yielding “Unable to query the maximum insets for IME”.
fun View.onSystemBarsAndImeInsetsChanged(listener: (insets: androidx.core.graphics.Insets) -> Unit) {
    var oldInsets = androidx.core.graphics.Insets.NONE

    ViewCompat.setOnApplyWindowInsetsListener(this) { _, windowInsets ->
        val newInsets = androidx.core.graphics.Insets.max(
            windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()),
            windowInsets.getInsets(WindowInsetsCompat.Type.ime()),
        )

        if (oldInsets != newInsets) {
            listener(newInsets)
            oldInsets = newInsets
        }

        windowInsets
    }
}


fun Context.getActionBarHeight(): Int {
    val typedValue = TypedValue()
    return if (theme.resolveAttribute(android.R.attr.actionBarSize, typedValue, true)) {
        TypedValue.complexToDimensionPixelSize(typedValue.data, resources.displayMetrics)
    } else {
        0
    }
}


// API ≥ 35:
//   Setting a solid navigation bar color will *only* apply it to the buttoned navigation,
//   resulting in semi-transparent scrim with system-default transparency of 80%.
//   Gesture hint scrim is *not* affected by it.
//
// API < 35:
//   The color is set as is for every navigation mode.
@Suppress("DEPRECATION") // Works for now
fun Activity.applyNavigationBarScrim(@ColorInt opaqueColor: Int) {
    if (Build.VERSION.SDK_INT >= 35) {
        window.navigationBarColor = opaqueColor
    } else {
        window.navigationBarColor = setAlphaComponent(opaqueColor, 0xCC) // 80%
    }
}


// As of API 35 `isStatusBarContrastEnforced` is deprecated but still has an effect. What?
//
// Note that similar effect can be achieved
// by applying a no-op `setOnApplyWindowInsetsListener` to the `rootView` of the layout.
//
// See https://developer.android.com/about/versions/15/behavior-changes-15#edge-to-edge
@Suppress("DEPRECATION")
fun Activity.makeSystemBarsTransparent() {
    if (Build.VERSION.SDK_INT >= 29) {
        window.isNavigationBarContrastEnforced = false
        window.isStatusBarContrastEnforced = false
    } else {
        window.navigationBarColor = Color.TRANSPARENT
        window.statusBarColor = Color.TRANSPARENT
    }
}
