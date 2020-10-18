package androidx.preference

import android.content.Context
import android.util.AttributeSet
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.WeechatAndroid.utils.Toaster.Companion.SuccessToast

class ClearKnownHostsPreference(context: Context, attrs: AttributeSet) : ClearPreference(context, attrs) {
    override val message = R.string.pref__ClearKnownHostsPreference__prompt
    override val negativeButton = R.string.pref__ClearKnownHostsPreference__button_cancel
    override val positiveButton = R.string.pref__ClearKnownHostsPreference__button_clear

    override fun update() {
        P.loadServerKeyVerifier()
        val count = P.sshServerKeyVerifier.numberOfRecords
        isEnabled = count > 0

        summary = if (count == 0) context.getString(R.string.pref__ClearKnownHostsPreference__0_entries) else
                context.resources.getQuantityString(R.plurals.pref__ClearKnownHostsPreference__n_entries, count, count)
    }

    override fun clear() {
        P.sshServerKeyVerifier.clear()
        SuccessToast.show(R.string.pref__ClearKnownHostsPreference__success_cleared)
    }
}