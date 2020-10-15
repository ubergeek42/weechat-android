package com.ubergeek42.WeechatAndroid.utils

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.Weechat
import java.lang.Exception

private val context = Weechat.applicationContext


class Toaster(
    private val duration: Int,
    private val colorResource: Int?,
) {
    fun show(message: String) {
        Weechat.runOnMainThread {
            Toast.makeText(context, message, duration).apply {
                colorResource?.let {
                    val color = ContextCompat.getColor(context, it)
                    view.background.setTint(color)
                }
                show()
            }
        }
    }

    fun show(message: String, vararg args: Any) {
        show(String.format(message, *args))
    }

    fun show(@StringRes id: Int) {
        show(context.resources.getString(id))
    }

    fun show(@StringRes id: Int, vararg args: Any) {
        show(context.resources.getString(id, *args))
    }

    fun show(e: Exception) {
        show(R.string.error, FriendlyExceptions(context).getFriendlyException(e).message)
    }

    // long and short toasts are insignificant messages that routinely appear during the use
    // of the app. info toasts are important messages that should be more noticeable to the user
    companion object {
        @JvmField val ErrorToast = Toaster(Toast.LENGTH_LONG, R.color.toastError)
        @JvmField val SuccessToast = Toaster(Toast.LENGTH_LONG, R.color.toastSuccess)
        @JvmField val InfoToast = Toaster(Toast.LENGTH_LONG, R.color.toastInfo)
        @JvmField val LongToast = Toaster(Toast.LENGTH_LONG, null)
        @JvmField val ShortToast = Toaster(Toast.LENGTH_SHORT, null)
    }
}
