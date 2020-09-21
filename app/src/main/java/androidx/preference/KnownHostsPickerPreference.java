package androidx.preference;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.weechat.relay.connection.SSHConnection;

public class KnownHostsPickerPreference extends FilePreference {
    public KnownHostsPickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override protected String saveData(@Nullable byte[] bytes) throws Exception {
        if (bytes != null) SSHConnection.parseKnownHosts(bytes);
        super.saveData(bytes);
        return getContext().getString(bytes == null ?
                R.string.pref_known_hosts_cleared :
                R.string.pref_known_hosts_set);
    }
}
