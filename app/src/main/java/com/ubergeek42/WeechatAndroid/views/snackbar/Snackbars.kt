package com.ubergeek42.WeechatAndroid.views.snackbar

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import com.google.android.material.snackbar.Snackbar
import com.ubergeek42.WeechatAndroid.BuildConfig
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.copypaste.setClipboardText
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.WeechatAndroid.utils.FriendlyExceptions
import com.ubergeek42.WeechatAndroid.utils.Toaster
import com.ubergeek42.WeechatAndroid.utils.Utils
import com.ubergeek42.WeechatAndroid.views.EditTextActivity
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
// If passing a throwable, text or string resource ID is optional.
// If it is not present, a message will be constructed from the throwable itself.
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


/**************************************************************************************************/


fun Preference.showSnackbar(t: Throwable) {
    (context as Activity).showSnackbar(t)
}

fun Preference.showSnackbar(@StringRes textResource: Int, t: Throwable) {
    (context as Activity).showSnackbar(textResource, t)
}

fun Preference.showSnackbar(text: CharSequence, t: Throwable) {
    (context as Activity).showSnackbar(text, t)
}

@JvmOverloads
fun Preference.showSnackbar(@StringRes textResource: Int, snackbarBuilder: SnackbarBuilder? = null) {
    (context as Activity).showSnackbar(textResource, snackbarBuilder)
}

@JvmOverloads
fun Preference.showSnackbar(text: CharSequence, snackbarBuilder: SnackbarBuilder? = null) {
    (context as Activity).showSnackbar(text, snackbarBuilder)
}


/**************************************************************************************************/


fun Fragment.showSnackbar(t: Throwable) {
    requireActivity().showSnackbar(t)
}

fun Fragment.showSnackbar(@StringRes textResource: Int, t: Throwable) {
    requireActivity().showSnackbar(textResource, t)
}

fun Fragment.showSnackbar(text: CharSequence, t: Throwable) {
    requireActivity().showSnackbar(text, t)
}

@JvmOverloads
fun Fragment.showSnackbar(@StringRes textResource: Int, snackbarBuilder: SnackbarBuilder? = null) {
    requireActivity().showSnackbar(textResource, snackbarBuilder)
}

@JvmOverloads
fun Fragment.showSnackbar(text: CharSequence, snackbarBuilder: SnackbarBuilder? = null) {
    requireActivity().showSnackbar(text, snackbarBuilder)
}


/**************************************************************************************************/


fun Activity.showSnackbar(t: Throwable) {
    getCoordinatorLayoutOrNull()?.showSnackbar(t)
}

fun Activity.showSnackbar(@StringRes textResource: Int, t: Throwable) {
    getCoordinatorLayoutOrNull()?.showSnackbar(textResource, t)
}

fun Activity.showSnackbar(text: CharSequence, t: Throwable) {
    getCoordinatorLayoutOrNull()?.showSnackbar(text, t)
}

@JvmOverloads
fun Activity.showSnackbar(@StringRes textResource: Int, snackbarBuilder: SnackbarBuilder? = null) {
    getCoordinatorLayoutOrNull()?.showSnackbar(textResource, snackbarBuilder)
}

@JvmOverloads
fun Activity.showSnackbar(text: CharSequence, snackbarBuilder: SnackbarBuilder? = null) {
    getCoordinatorLayoutOrNull()?.showSnackbar(text, snackbarBuilder)
}

private fun Activity.getCoordinatorLayoutOrNull(): CoordinatorLayout? {
    val coordinatorLayout = findViewById(R.id.coordinator_layout) as? CoordinatorLayout

    return if (coordinatorLayout != null) {
        coordinatorLayout
    } else {
        val errorMessage = "While trying to show a snackbar, could not find a view with id coordinator_layout in $this"

        if (BuildConfig.DEBUG) {
            throw IllegalArgumentException(errorMessage)
        } else {
            kitty.error(errorMessage)
            Toaster.ErrorToast.show(errorMessage)
        }

        null
    }
}


/**************************************************************************************************/


fun View.showSnackbar(t: Throwable) {
    showSnackbar(context.getSnackbarTextForThrowable(t), t)
}

fun View.showSnackbar(@StringRes textResource: Int, t: Throwable) {
    showSnackbar(resources.getText(textResource), t)
}

fun View.showSnackbar(text: CharSequence, t: Throwable) {
    showSnackbar(text, getSnackbarBuilderForThrowable(t))
}

@JvmOverloads
fun View.showSnackbar(@StringRes textResource: Int, snackbarBuilder: SnackbarBuilder? = null) {
    showSnackbar(resources.getText(textResource), snackbarBuilder)
}

@JvmOverloads
fun View.showSnackbar(text: CharSequence, snackbarBuilder: SnackbarBuilder? = null) {
    val activity = Utils.getActivity(this)
    val baseSnackbarBuilder = (activity as? BaseSnackbarBuilderProvider)?.baseSnackbarBuilder

    val snackbar = Snackbar.make(this, text, Snackbar.LENGTH_LONG)
    snackbar.setMaxLines(4)
    snackbar.reactToInsetChangesProperly()
    snackbar.behavior = SwipeDismissBehaviorFix()

    baseSnackbarBuilder?.invoke(snackbar)
    snackbarBuilder?.invoke(snackbar)

    kitty.info("Showing a snackbar: '%s'", text)

    snackbar.show()
}


/**************************************************************************************************
 **************************************************************************************************
 **************************************************************************************************/


private fun Snackbar.setMaxLines(maxLines: Int) {
    view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.maxLines = maxLines
}


private fun Context.getSnackbarTextForThrowable(t: Throwable): CharSequence {
    val exceptionMessage = FriendlyExceptions(this).getFriendlyException(t).message
    return resources.getString(R.string.error__etc__prefix, exceptionMessage)
}

private fun getSnackbarBuilderForThrowable(t: Throwable): SnackbarBuilder = {
    setAction(R.string.ui__snackbars__action__error_details) {
        val intent = Intent(context, ErrorDetailsActivity::class.java).apply {
            putExtra(EXTRA_ERROR_TEXT, t.stackTraceToString())
        }
        context.startActivity(intent)
    }
}


private const val EXTRA_ERROR_TEXT = "error_text"

class ErrorDetailsActivity : EditTextActivity(allowSoftKeyboard = false) {
    private lateinit var errorText: CharSequence

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        P.applyThemeAfterActivityCreation(this)
        P.storeThemeOrColorSchemeColors(this)
        window.decorView.setBackgroundColor(P.colorPrimary)

        errorText = intent.getCharSequenceExtra(EXTRA_ERROR_TEXT) ?: "huh"

        ui.toolbar.apply {
            setTitle(R.string.dialog__error_details__title)
            inflateMenu(R.menu.error_details_activity)
            setOnMenuItemClickListener { item: MenuItem ->
                when (item.itemId) {
                    R.id.copy      -> {
                        setClipboardText(errorText)
                        finish()
                    }

                    R.id.wrap_text -> {
                        item.isChecked = !item.isChecked
                        ui.text.setHorizontallyScrolling(!item.isChecked)
                    }
                }
                true
            }
        }

        // Allow word breaking after "." or "$"
        // and before opening parentheses--for stuff like "method(File.kt:69)"
        val breakableErrorText = errorText
            .replace("([.$])(?!\\s)".toRegex(), "$1\u200b")
            .replace("(?<!\\s)\\(".toRegex(), "\u200b(")

        ui.text.setText(breakableErrorText)
        ui.text.setTextIsSelectable(true)
    }
}
