package androidx.preference

import android.content.Context
import android.util.AttributeSet
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.service.SSLHandler
import com.ubergeek42.WeechatAndroid.utils.Toaster.Companion.ErrorToast
import com.ubergeek42.WeechatAndroid.utils.Toaster.Companion.SuccessToast

class ClearCertPreference(context: Context, attrs: AttributeSet) : ClearPreference(context, attrs) {
    override val message = R.string.pref__ClearCertPreference__prompt
    override val negativeButton = R.string.pref__ClearCertPreference__button_cancel
    override val positiveButton = R.string.pref__ClearCertPreference__button_clear

    override fun update() {
        val count = SSLHandler.getInstance(context).getUserCertificateCount()
        isEnabled = count > 0
        summary = when (count) {
            0 -> context.getString(R.string.pref__ClearCertPreference__0_entries)
            1 -> context.getString(R.string.pref__ClearCertPreference__1_entries)
            else -> context.resources.getQuantityString(R.plurals.pref__ClearCertPreference__n_entries, count, count)
        }
    }

    override fun clear() {
        val removed = SSLHandler.getInstance(context).removeUserKeystore()
        if (removed) {
            SuccessToast.show(R.string.pref__ClearCertPreference__success_cleared)
        } else {
            ErrorToast.show(R.string.pref__ClearCertPreference__error_could_not_clear)
        }
    }
}