package androidx.preference;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

import com.ubergeek42.weechat.relay.connection.SSHConnection;

public class KnownHostsPickerPreference extends FilePreference {
    public KnownHostsPickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override protected String saveData(@Nullable byte[] bytes) throws Exception {
        if (bytes != null) SSHConnection.validateKnownHosts(bytes);
        super.saveData(bytes);
        return bytes == null ? DEFAULT_SUCCESSFULLY_CLEARED : "Known hosts set; the syntax is valid";
    }
}
