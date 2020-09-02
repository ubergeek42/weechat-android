package com.ubergeek42.WeechatAndroid.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.preference.PrivateKeyPickerPreference;

import com.ubergeek42.WeechatAndroid.Weechat;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;
import com.ubergeek42.weechat.relay.connection.SSHConnection;

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;

import static androidx.preference.PrivateKeyPickerPreference.STORED_IN_KEYSTORE;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_SSH_AUTHENTICATION_METHOD;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_SSH_AUTHENTICATION_METHOD_KEY;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_SSH_AUTHENTICATION_METHOD_PASSWORD;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_SSH_KEY_FILE;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_SSH_KEY_FILE_D;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_SSH_PASSWORD;
import static com.ubergeek42.WeechatAndroid.utils.AndroidKeyStoreUtils.isInsideSecurityHardware;
import static com.ubergeek42.WeechatAndroid.utils.AndroidKeyStoreUtils.putKeyPairIntoAndroidKeyStore;
import static com.ubergeek42.WeechatAndroid.utils.AndroidKeyStoreUtils.InsideSecurityHardware;

public class MigratePreferences {
    final private static @Root Kitty kitty = Kitty.make();

    final static String VERSION_KEY = "preferences-version-key";

    final SharedPreferences preferences;

    List<Migrator> migrators = new ArrayList<>();

    public MigratePreferences(Context context) {
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        initMigrators();
    }

    private class Migrator {
        public int oldVersion;
        public int newVersion;
        public Runnable runnable;

        public Migrator(int oldVersion, int newVersion, Runnable runnable) {
            this.oldVersion = oldVersion;
            this.newVersion = newVersion;
            this.runnable = runnable;
        }

        void migrate() {
            kitty.info("Migrating preferences from version %s to %s", oldVersion, newVersion);
            if (oldVersion != preferences.getInt(VERSION_KEY, 0))
                throw new RuntimeException("Could not migrate");
            runnable.run();
            preferences.edit().putInt(VERSION_KEY, newVersion).apply();
        }
    }


    public void migrate() {
        main: while (true) {
            int version = preferences.getInt(VERSION_KEY, 0);
            for (Migrator migrator : migrators) {
                if (migrator.oldVersion == version) {
                    migrator.migrate();
                    continue main;
                }
            }
            return;
        }
    }

    void initMigrators() {
        migrators.add(new Migrator(0, 1, () -> {
            if (!preferences.contains(Constants.Deprecated.PREF_SSH_PASS))
                return;
            String sshPass = preferences.getString(Constants.Deprecated.PREF_SSH_PASS,
                    Constants.Deprecated.PREF_SSH_PASS_D);
            String sshKey = preferences.getString(Constants.Deprecated.PREF_SSH_KEY,
                    Constants.Deprecated.PREF_SSH_KEY_D);

            String authenticationMethod, sshPassword, sshKeyFile, sshPassphrase;
            if (sshKey != null) {
                authenticationMethod = PREF_SSH_AUTHENTICATION_METHOD_KEY;
                sshPassword = null;
                sshPassphrase = sshPass;
                sshKeyFile = sshKey;
            } else {
                authenticationMethod = PREF_SSH_AUTHENTICATION_METHOD_PASSWORD;
                sshPassword = sshPass;
                sshPassphrase = null;
                sshKeyFile = null;
            }

            preferences.edit()
                    .putString(PREF_SSH_AUTHENTICATION_METHOD, authenticationMethod)
                    .putString(PREF_SSH_PASSWORD, sshPassword)
                    .putString(PREF_SSH_KEY_FILE, sshKeyFile)
                    .putString(Constants.Deprecated.PREF_SSH_KEY_PASSPHRASE, sshPassphrase)

                    .remove(Constants.Deprecated.PREF_SSH_PASS)
                    .remove(Constants.Deprecated.PREF_SSH_KEY)
                    .apply();
        }));

        migrators.add(new Migrator(1, 2, () -> {
            String sshKeyFile = preferences.getString(PREF_SSH_KEY_FILE, PREF_SSH_KEY_FILE_D);
            String sshPassphrase = preferences.getString(Constants.Deprecated.PREF_SSH_KEY_PASSPHRASE,
                    Constants.Deprecated.PREF_SSH_KEY_PASSPHRASE_D);
            if (sshKeyFile == null)
                return;

            if (!STORED_IN_KEYSTORE.equals(sshKeyFile)) {
                byte[] sshKeyFileBytes = PrivateKeyPickerPreference.getData(sshKeyFile);
                try {
                    KeyPair keyPair = SSHConnection.makeKeyPair(sshKeyFileBytes, sshPassphrase);
                    putKeyPairIntoAndroidKeyStore(keyPair, SSHConnection.KEYSTORE_ALIAS);
                    preferences.edit()
                            .putString(PREF_SSH_KEY_FILE, STORED_IN_KEYSTORE)
                            .putString(Constants.Deprecated.PREF_SSH_KEY_PASSPHRASE, null)
                            .apply();
                    String message = TinyMap.of(
                            InsideSecurityHardware.YES, "security hardware",
                            InsideSecurityHardware.NO, "software key store",
                            InsideSecurityHardware.CANT_TELL, "key store"
                    ).get(isInsideSecurityHardware(SSHConnection.KEYSTORE_ALIAS));
                    Weechat.showLongToast("While migrating preferences, private SSH key was moved " +
                            "into " + message);
                } catch (Exception e) {
                    Weechat.showLongToast("While migrating preferences, attempted to move SSH " +
                            "private key into AndroidKeyStore. This was unsuccessful. Reason: " +
                            e.getMessage());
                }
            }
        }));

        migrators.add(new Migrator(2, 3, () -> {
            String sshKeyFile = preferences.getString(PREF_SSH_KEY_FILE, PREF_SSH_KEY_FILE_D);
            String sshPassphrase = preferences.getString(Constants.Deprecated.PREF_SSH_KEY_PASSPHRASE,
                    Constants.Deprecated.PREF_SSH_KEY_PASSPHRASE_D);
            if (sshKeyFile == null)
                return;

            if (!STORED_IN_KEYSTORE.equals(sshKeyFile)) {
                try {
                    byte[] sshKeyFileBytes = PrivateKeyPickerPreference.getData(sshKeyFile);
                    KeyPair keyPair = SSHConnection.makeKeyPair(sshKeyFileBytes, sshPassphrase);
                    preferences.edit()
                            .putString(PREF_SSH_KEY_FILE, Utils.serialize(keyPair))
                            .apply();
                } catch (Exception e) {
                    Weechat.showLongToast("Failed to migrate SSH key: " + e);
                    preferences.edit()
                            .putString(PREF_SSH_KEY_FILE, PREF_SSH_KEY_FILE_D)
                            .apply();
                }
            }

            preferences.edit()
                    .remove(Constants.Deprecated.PREF_SSH_KEY_PASSPHRASE)
                    .apply();
        }));
    }
}
