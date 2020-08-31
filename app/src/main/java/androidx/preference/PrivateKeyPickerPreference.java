package androidx.preference;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ubergeek42.WeechatAndroid.utils.Utils;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;
import com.ubergeek42.weechat.relay.connection.SSHConnection;

import java.security.KeyPair;

import static com.ubergeek42.WeechatAndroid.utils.AndroidKeyStoreUtils.deleteAndroidKeyStoreEntry;
import static com.ubergeek42.WeechatAndroid.utils.AndroidKeyStoreUtils.isInsideSecurityHardware;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_SSH_KEY_FILE;
import static com.ubergeek42.WeechatAndroid.utils.AndroidKeyStoreUtils.putKeyPairIntoAndroidKeyStore;

public class PrivateKeyPickerPreference extends PasswordedFilePickerPreference {
    final private static @Root Kitty kitty = Kitty.make();
    final public static String STORED_IN_KEYSTORE = "woo hoo the key is stored in keystore!";

    public PrivateKeyPickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override protected String saveData(@Nullable byte[] bytes, @NonNull String passphrase) throws Exception {
        String key, message;
        if (bytes != null) {
            KeyPair keyPair = SSHConnection.makeKeyPair(bytes, passphrase);

            try {
                putKeyPairIntoAndroidKeyStore(keyPair, SSHConnection.KEYSTORE_ALIAS);
                key = STORED_IN_KEYSTORE;

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    message = isInsideSecurityHardware(SSHConnection.KEYSTORE_ALIAS) ?
                            "%s key was stored inside security hardware" :
                            "%s key was stored in key store but not inside security hardware";
                } else {
                    message = "%s key was stored in key store";
                }
            } catch (Exception e) {
                key = Utils.serialize(keyPair);
                message = "%s key was stored inside the app.\n\n" +
                        "The key couldn't be stored in the key store: " + e.getMessage();
                kitty.warn(message, e);
            }
            message = String.format(message, keyPair.getPrivate().getAlgorithm());

        } else {
            key = null;
            message = "Key forgotten";
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
}
