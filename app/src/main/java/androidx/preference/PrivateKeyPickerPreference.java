package androidx.preference;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ubergeek42.weechat.relay.connection.SSHConnection;

import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_SSH_KEY_FILE;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_SSH_KEY_PASSPHRASE;

class PrivateKeyPickerPreference extends PasswordedFilePickerPreference {
    public PrivateKeyPickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override protected void saveData(@Nullable byte[] bytes, @NonNull String password) throws Exception {
        String key;
        if (bytes != null) {
            SSHConnection.getKeyPair(bytes, password);
            key = Base64.encodeToString(bytes, Base64.NO_WRAP);
        } else {
            key = password = null;
        }
        getSharedPreferences().edit()
                .putString(PREF_SSH_KEY_FILE, key)
                .putString(PREF_SSH_KEY_PASSPHRASE, password).apply();
        notifyChanged();
    }
}
