package com.ubergeek42.WeechatAndroid.views

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.KeyEvent.ACTION_UP
import androidx.appcompat.widget.AppCompatEditText


fun interface OnBackGestureListener {
    fun onBackGesture(): Boolean
}


// the idea here is to listen to back gesture events,
// as opposed to back button presses (3 button navigation, etc).
// this is useful because with gestures there's 2 ways to minimize the keyboard,
// via the gesture and also via the gesture minimization button.
// this allows closing the search instantly on back press
// and leaving it open on keyboard minimization.
class BackGestureAwareEditText @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
) : AppCompatEditText(context, attrs) {

    var onBackGestureListener: OnBackGestureListener? = null

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        if (event.isBackGesture && event.action == ACTION_UP) {
            if (onBackGestureListener?.onBackGesture() == true) return true
        }

        return super.onKeyPreIme(keyCode, event)
    }
}


// this is super hacky but probably safe.
// with the back gesture the display ID is -1, or INVALID_DISPLAY
// the field is hidden but present in toString()
private val KeyEvent.isBackGesture get() =
        keyCode == KeyEvent.KEYCODE_BACK && toString().contains("displayId=-1")
