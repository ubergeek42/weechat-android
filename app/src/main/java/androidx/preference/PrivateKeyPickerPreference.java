package androidx.preference;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.utils.TinyMap;
import com.ubergeek42.WeechatAndroid.utils.Utils;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;
import com.ubergeek42.weechat.relay.connection.SSHConnection;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;

import static com.ubergeek42.WeechatAndroid.utils.AndroidKeyStoreUtils.deleteAndroidKeyStoreEntry;
import static com.ubergeek42.WeechatAndroid.utils.AndroidKeyStoreUtils.isInsideSecurityHardware;
import static com.ubergeek42.WeechatAndroid.utils.AndroidKeyStoreUtils.putKeyPairIntoAndroidKeyStore;
import static com.ubergeek42.WeechatAndroid.utils.AndroidKeyStoreUtils.InsideSecurityHardware;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_SSH_KEY_FILE;

public class PrivateKeyPickerPreference extends PasswordedFilePickerPreference {
    final private static @Root Kitty kitty = Kitty.make();
    final public static String STORED_IN_KEYSTORE = "woo hoo the key is stored in keystore!";

    public PrivateKeyPickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override protected String saveData(@Nullable byte[] bytes, @NonNull String passphrase) throws Exception {
        Context context = getContext();
        String key, message;

        if (bytes != null) {
            KeyPair keyPair = SSHConnection.makeKeyPair(bytes, passphrase);
            String algorithm = keyPair.getPrivate().getAlgorithm();

            try {
                putKeyPairIntoAndroidKeyStore(keyPair, SSHConnection.KEYSTORE_ALIAS);
                key = STORED_IN_KEYSTORE;
                message = getInsideSecurityHardwareString(algorithm);
            } catch (Exception e) {
                kitty.warn("Error while putting %s key into AndroidKeyStore", algorithm, e);
                key = Utils.serialize(keyPair);
                message = context.getString(R.string.pref_ssh_key_stored_outside_key_store, algorithm, e.getMessage());
            }
        } else {
            key = null;
            message = context.getString(R.string.pref_ssh_key_forgotten);
            try {
                deleteAndroidKeyStoreEntry(SSHConnection.KEYSTORE_ALIAS);
            } catch (Exception e) {
                kitty.warn("Error while deleting key from AndroidKeyStore", e);
            }
        }

        getSharedPreferences().edit()
                .putString(PREF_SSH_KEY_FILE, key)
                .apply();
        notifyChanged();
        return message;
    }

    public static @Nullable byte[] getData(String data) {
        return STORED_IN_KEYSTORE.equals(data) ?
                SSHConnection.STORED_IN_KEYSTORE_MARKER : FilePreference.getData(data);
    }

    public String getInsideSecurityHardwareString(String algorithm) throws GeneralSecurityException, IOException {
        InsideSecurityHardware inside = isInsideSecurityHardware(SSHConnection.KEYSTORE_ALIAS);
        int resId = TinyMap.of(
                InsideSecurityHardware.YES, R.string.pref_ssh_key_stored_inside_security_hardware_yes,
                InsideSecurityHardware.NO, R.string.pref_ssh_key_stored_inside_security_hardware_cant_tell,
                InsideSecurityHardware.CANT_TELL, R.string.pref_ssh_key_stored_inside_security_hardware_cant_tell
        ).get(inside);
        return getContext().getString(resId, algorithm);
    }
}
