package androidx.preference

import android.content.Context
import android.util.AttributeSet
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.service.SSLHandler
import com.ubergeek42.WeechatAndroid.utils.Toaster.Companion.ErrorToast
import com.ubergeek42.WeechatAndroid.utils.Toaster.Companion.SuccessToast
import java.text.MessageFormat

class ClearCertPreference(context: Context, attrs: AttributeSet) : ClearPreference(context, attrs) {
    override val message = R.string.pref_clear_certs_confirmation
    override val negativeButton = R.string.pref_clear_certs_negative
    override val positiveButton = R.string.pref_clear_certs_positive

    override fun update() {
        val count = SSLHandler.getInstance(context).userCertificateCount
        isEnabled = count > 0
        summary = MessageFormat.format(context.getString(R.string.pref_clear_certs_summary), count)
    }

    override fun clear() {
        val removed = SSLHandler.getInstance(context).removeKeystore()
        if (removed) {
            SuccessToast.show(R.string.pref_clear_certs_success)
        } else {
            ErrorToast.show(R.string.pref_clear_certs_failure)
        }
    }
}