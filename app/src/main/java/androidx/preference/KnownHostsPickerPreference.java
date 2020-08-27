package androidx.preference;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

import com.ubergeek42.weechat.relay.connection.SSHConnection;

class KnownHostsPickerPreference extends FilePreference {
    public KnownHostsPickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override protected void saveData(@Nullable byte[] bytes) throws Exception {
        if (bytes != null) SSHConnection.validateKnownHosts(bytes);
        super.saveData(bytes);
    }
}
