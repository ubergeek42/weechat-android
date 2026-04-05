// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
package androidx.preference

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.util.Base64
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.copypaste.getClipboardText
import com.ubergeek42.WeechatAndroid.utils.Utils
import com.ubergeek42.WeechatAndroid.views.snackbar.showSnackbar
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root


open class FilePreference(context: Context, attrs: AttributeSet?)
        : DialogPreference(context, attrs), DialogFragmentGetter {

    override fun getSummary(): CharSequence? {
        val bytes = getData(getPersistedString(null))
        val setNotSetStringResourceId = if (Utils.isEmpty(bytes)) {
            R.string.pref__FilePreference__summary_status_not_set
        } else {
            R.string.pref__FilePreference__summary_status_set
        }
        return context.getString(
            R.string.pref__FilePreference__summary_adapter,
            super.getSummary(),
            context.getText(setNotSetStringResourceId)
        )
    }

    // validate, if needed, and save data. throw anything on error—it will get printed.
    // returned string, if not null, will be displayed as a long toast
    @Throws(Exception::class) protected open fun saveData(bytes: ByteArray?): String? {
        if (callChangeListener(bytes)) {
            persistString(if (bytes == null) null else Base64.encodeToString(bytes, Base64.NO_WRAP))
            notifyChanged()
        }
        return null
    }

    private fun saveDataAndShowSnackbar(bytesGetter: ThrowingGetter<ByteArray?>) {
        try {
            val bytes = bytesGetter.get()
            val message = saveData(bytes)
            if (message != null) showSnackbar(message)
        } catch (e: Exception) {
            kitty.error("error", e)
            showSnackbar(e)
        }
    }

    /**********************************************************************************************/

    // this gets called when a file has been picked
    fun onActivityResult(intent: Intent) {
        saveDataAndShowSnackbar { Utils.readFromUri(context, intent.data) }
    }

    override fun getDialogFragment(): DialogFragment = FilePreferenceFragment()

    /**********************************************************************************************/

    open class FilePreferenceFragment : PreferenceDialogFragmentCompat() {
        override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
            val preference = preference as FilePreference

            builder.setNeutralButton(R.string.pref__FilePreference__button_clear) { _, _ ->
                preference.saveDataAndShowSnackbar { null }
            }

            builder.setNegativeButton(R.string.pref__FilePreference__button_paste) { _, _ ->
                val clipboardText = requireContext().getClipboardText()
                if (clipboardText.isNotEmpty()) {
                    preference.saveDataAndShowSnackbar { clipboardText.toString().toByteArray() }
                } else {
                    showSnackbar(R.string.error__pref__clipboard_empty)
                }
            }

            builder.setPositiveButton(R.string.pref__FilePreference__button_choose_file) { _, _ ->
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.setType("*/*")
                targetFragment!!.startActivityForResult(intent, requireArguments().getInt("code"))
            }
        }

        override fun onDialogClosed(b: Boolean) {}
    }

    companion object {
        @Root private val kitty = Kitty.make()

        // a helper method that gets the original bytes from the strings
        fun getData(data: String?): ByteArray? {
            return try {
                Base64.decode(data!!.toByteArray(), Base64.NO_WRAP)
            } catch (ignored: IllegalArgumentException) {
                null
            } catch (ignored: NullPointerException) {
                null
            }
        }
    }
}


// this slightly simplifies code by allowing onActivityResult not to deal with exceptions
private fun interface ThrowingGetter<T> {
    @Throws(Exception::class) fun get(): T
}
