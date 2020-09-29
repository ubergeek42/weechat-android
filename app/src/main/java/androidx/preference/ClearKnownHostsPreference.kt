package androidx.preference

import android.content.Context
import android.util.AttributeSet
import android.widget.Toast
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.service.P

class ClearKnownHostsPreference(context: Context, attrs: AttributeSet) : ClearPreference(context, attrs) {
    override val message = R.string.pref_clear_certs_confirmation
    override val negativeButton = R.string.pref_clear_certs_negative
    override val positiveButton = R.string.pref_clear_certs_positive

    override fun update() {
        P.loadServerKeyVerifier()
        val count = P.sshServerKeyVerifier.numberOfRecords
        isEnabled = count > 0
        summary = "$count known hosts"
    }

    override fun clear() {
        P.sshServerKeyVerifier.clear()
        Toast.makeText(context, "Known hosts cleared", Toast.LENGTH_LONG).show()
    }
}