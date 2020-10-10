package androidx.preference

import android.content.Context
import android.util.AttributeSet
import android.widget.Toast
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.service.P

class ClearKnownHostsPreference(context: Context, attrs: AttributeSet) : ClearPreference(context, attrs) {
    override val message = R.string.pref_clear_known_hosts_confirmation
    override val negativeButton = R.string.pref_clear_known_hosts_cancel
    override val positiveButton = R.string.pref_clear_known_hosts_clear

    override fun update() {
        P.loadServerKeyVerifier()
        val count = P.sshServerKeyVerifier.numberOfRecords
        isEnabled = count > 0

        summary = if (count == 0) context.getString(R.string.pref_clear_known_hosts_entries_0) else
                context.resources.getQuantityString(R.plurals.pref_clear_known_hosts_entries, count, count)
    }

    override fun clear() {
        P.sshServerKeyVerifier.clear()
        Toast.makeText(context, R.string.pref_clear_known_hosts_cleared, Toast.LENGTH_LONG).show()
    }
}