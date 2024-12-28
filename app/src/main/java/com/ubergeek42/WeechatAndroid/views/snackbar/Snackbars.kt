package com.ubergeek42.WeechatAndroid.views.snackbar

import android.app.Activity
import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.snackbar.Snackbar
import com.ubergeek42.WeechatAndroid.BuildConfig
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.utils.Toaster
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root


@Root private val kitty = Kitty.make("Snackbars")


// Create snackbars by calling `showSnackbar` on either an activity or a view.
// As `CoordinatorLayout` is responsible for proper placement and animation of snackbars,
//
//   * if calling on an activity, the activity **MUST** have a `CoordinatorLayout`
//     with id `coordinator_layout`;
//
//   * if calling on a view, the view **MUST** be either a `CoordinatorLayout`,
//     or a (possibly indirect) child of `CoordinatorLayout`.
//
// Additional configuration can be done in the configuration block, e.g.
//
//     showSnackbar(text) {
//         addCallback(callback)
//     }
//
// If an activity wants to alter behavior for all snackbars,
// it can extend `BaseSnackbarBuilderProvider`.


typealias SnackbarBuilder = Snackbar.() -> Unit

interface BaseSnackbarBuilderProvider {
    val baseSnackbarBuilder: SnackbarBuilder
}


fun Activity.showSnackbar(
    @StringRes textResource: Int,
    duration: Int = Snackbar.LENGTH_LONG,
    snackbarBuilder: SnackbarBuilder? = null,
) {
    val text = getText(textResource)
    showSnackbar(text, duration, snackbarBuilder)
}


fun Activity.showSnackbar(
    text: CharSequence,
    duration: Int = Snackbar.LENGTH_LONG,
    snackbarBuilder: SnackbarBuilder? = null,
) {
    val view: View? = findViewById(R.id.coordinator_layout) as? CoordinatorLayout

    if (view != null) {
        val baseSnackbarBuilder = (this as? BaseSnackbarBuilderProvider)?.baseSnackbarBuilder
        view.showSnackbar(text, duration) {
            baseSnackbarBuilder?.invoke(this)
            snackbarBuilder?.invoke(this)
        }
    } else {
        val errorMessage = "While trying to show a snackbar, could not find a view with id coordinator_layout in $this"

        if (BuildConfig.DEBUG) {
            throw IllegalArgumentException(errorMessage)
        } else {
            kitty.error(errorMessage)
            Toaster.InfoToast.show(errorMessage)
        }
    }
}


fun View.showSnackbar(
    @StringRes textResource: Int,
    duration: Int = Snackbar.LENGTH_LONG,
    snackbarBuilder: SnackbarBuilder? = null,
) {
    val text = resources.getText(textResource)
    showSnackbar(text, duration, snackbarBuilder)
}


fun View.showSnackbar(
    text: CharSequence,
    duration: Int = Snackbar.LENGTH_LONG,
    snackbarBuilder: SnackbarBuilder? = null,
) {
    val snackbar = Snackbar.make(this, text, duration)
    snackbar.setMaxLines(4)
    snackbar.behavior = SwipeDismissBehaviorFix()

    if (snackbarBuilder != null) {
        snackbar.snackbarBuilder()
    }

    kitty.info("Showing a snackbar: '%s'", text)

    snackbar.show()
}


fun Snackbar.setMaxLines(maxLines: Int) {
    view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.maxLines = maxLines
}
