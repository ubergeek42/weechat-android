package com.ubergeek42.WeechatAndroid.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;

import java.util.ArrayList;
import java.util.List;

import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_SSH_AUTHENTICATION_METHOD;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_SSH_AUTHENTICATION_METHOD_KEY;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_SSH_AUTHENTICATION_METHOD_PASSWORD;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_SSH_KEY_FILE;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_SSH_KEY_PASSPHRASE;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_SSH_PASSWORD;

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
        while (true) {
            int version = preferences.getInt(VERSION_KEY, 0);
            for (Migrator migrator : migrators) {
                if (migrator.oldVersion == version) {
                    migrator.migrate();
                    break;
                }
                return;
            }
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
                    .putString(PREF_SSH_KEY_PASSPHRASE, sshPassphrase)

                    .remove(Constants.Deprecated.PREF_SSH_PASS)
                    .remove(Constants.Deprecated.PREF_SSH_KEY)
                    .apply();
        }));
    }
}
