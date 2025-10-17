package com.ubergeek42.WeechatAndroid.views

import android.content.Context
import android.util.TypedValue
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner


// Using `getInsetsIgnoringVisibility` for system bars as those can be temporarily hidden at times,
// particularly when clicking on the paperclip button and getting the “Complete action using” dialog.
fun View.onSystemBarsInsetsChanged(listener: (insets: androidx.core.graphics.Insets) -> Unit) {
    var oldInsets = androidx.core.graphics.Insets.NONE

    ViewCompat.setOnApplyWindowInsetsListener(this) { _, windowInsets ->
        val newInsets = windowInsets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())

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
            windowInsets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars()),
            windowInsets.getInsets(WindowInsetsCompat.Type.ime()),
        )

        if (oldInsets != newInsets) {
            listener(newInsets)
            oldInsets = newInsets
        }

        windowInsets
    }
}


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
