package com.ubergeek42.WeechatAndroid.utils

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.preference.PrivateKeyPickerPreference
import com.ubergeek42.WeechatAndroid.service.P.VolumeRole
import com.ubergeek42.WeechatAndroid.upload.applicationContext
import com.ubergeek42.WeechatAndroid.utils.AndroidKeyStoreUtils.InsideSecureHardware
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root
import com.ubergeek42.weechat.relay.connection.SSHConnection


class MigratePreferences(val context: Context) {
    companion object {
        @Root private val kitty = Kitty.make() as Kitty
    }

    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private var migrators = mutableListOf<Migrator>()

    private inner class Migrator(
        val oldVersion: Int,
        val newVersion: Int,
        val action: Migrator.() -> Unit
    ) {
        fun migrate() {
            kitty.info("Migrating preferences from version %s to %s", oldVersion, newVersion)
            if (oldVersion != preferences.getInt(VERSION_KEY, 0)) throw RuntimeException("Could not migrate")
            this.action()
            preferences.edit().putInt(VERSION_KEY, newVersion).apply()
        }
    }

    fun migrate() {
        kitty.info("Preferences version: %s", preferences.getInt(VERSION_KEY, 0))

        outer@ while (true) {
            val version = preferences.getInt(VERSION_KEY, 0)
            for (migrator in migrators) {
                if (migrator.oldVersion == version) {
                    migrator.migrate()
                    continue@outer
                }
            }
            return
        }
    }

    init {
        fun add(oldVersion: Int, newVersion: Int, action: Migrator.() -> Unit) {
            migrators.add(Migrator(oldVersion, newVersion, action))
        }

        add(0, 1) {
            if (!preferences.contains(Constants.Deprecated.PREF_SSH_PASS)) return@add

            val sshPass = preferences.getString(Constants.Deprecated.PREF_SSH_PASS,
                                                Constants.Deprecated.PREF_SSH_PASS_D)
            val sshKey = preferences.getString(Constants.Deprecated.PREF_SSH_KEY,
                                               Constants.Deprecated.PREF_SSH_KEY_D)
            val authenticationMethod: String
            val sshPassword: String?
            val sshKeyFile: String?
            val sshPassphrase: String?

            if (sshKey != null) {
                authenticationMethod = Constants.PREF_SSH_AUTHENTICATION_METHOD_KEY
                sshPassword = null
                sshPassphrase = sshPass
                sshKeyFile = sshKey
            } else {
                authenticationMethod = Constants.PREF_SSH_AUTHENTICATION_METHOD_PASSWORD
                sshPassword = sshPass
                sshPassphrase = null
                sshKeyFile = null
            }

            preferences.edit()
                    .putString(Constants.PREF_SSH_AUTHENTICATION_METHOD, authenticationMethod)
                    .putString(Constants.PREF_SSH_PASSWORD, sshPassword)
                    .putString(Constants.PREF_SSH_KEY_FILE, sshKeyFile)
                    .putString(Constants.Deprecated.PREF_SSH_KEY_PASSPHRASE, sshPassphrase)
                    .remove(Constants.Deprecated.PREF_SSH_PASS)
                    .remove(Constants.Deprecated.PREF_SSH_KEY)
                    .apply()
        }

        add(1, 2) {
            val sshKeyFile = preferences.getString(Constants.PREF_SSH_KEY_FILE,
                                                   Constants.PREF_SSH_KEY_FILE_D) ?: return@add

            val sshPassphrase = preferences.getString(Constants.Deprecated.PREF_SSH_KEY_PASSPHRASE,
                                                      Constants.Deprecated.PREF_SSH_KEY_PASSPHRASE_D)

            if (PrivateKeyPickerPreference.STORED_IN_KEYSTORE != sshKeyFile) {
                val sshKeyFileBytes = PrivateKeyPickerPreference.getData(sshKeyFile)
                try {
                    val keyPair = SSHConnection.makeKeyPair(sshKeyFileBytes, sshPassphrase)
                    AndroidKeyStoreUtils.putKeyPairIntoAndroidKeyStore(keyPair, SSHConnection.KEYSTORE_ALIAS)
                    preferences.edit()
                            .putString(Constants.PREF_SSH_KEY_FILE, PrivateKeyPickerPreference.STORED_IN_KEYSTORE)
                            .putString(Constants.Deprecated.PREF_SSH_KEY_PASSPHRASE, null)
                            .apply()
                    val message = mapOf(
                        InsideSecureHardware.YES to "secure hardware",
                        InsideSecureHardware.NO to "software key store",
                        InsideSecureHardware.CANT_TELL to "key store"
                    )[AndroidKeyStoreUtils.isInsideSecurityHardware(SSHConnection.KEYSTORE_ALIAS)]
                    showInfo("While migrating preferences, " +
                             "private SSH key was moved into $message")
                } catch (e: Exception) {
                    e.printStackTrace()
                    showError("While migrating preferences, " +
                              "attempted to move SSH private key into AndroidKeyStore. " +
                              "This was unsuccessful. Reason: ", e)
                }
            }
        }

        add(2, 3) {
            val sshKeyFile = preferences.getString(Constants.PREF_SSH_KEY_FILE,
                                                   Constants.PREF_SSH_KEY_FILE_D) ?: return@add

            val sshPassphrase = preferences.getString(Constants.Deprecated.PREF_SSH_KEY_PASSPHRASE,
                                                      Constants.Deprecated.PREF_SSH_KEY_PASSPHRASE_D)

            if (PrivateKeyPickerPreference.STORED_IN_KEYSTORE != sshKeyFile) {
                try {
                    val sshKeyFileBytes = PrivateKeyPickerPreference.getData(sshKeyFile)
                    val keyPair = SSHConnection.makeKeyPair(sshKeyFileBytes, sshPassphrase)
                    preferences.edit()
                            .putString(Constants.PREF_SSH_KEY_FILE, Utils.serialize(keyPair))
                            .apply()
                } catch (e: Exception) {
                    showError("Failed to migrate SSH key: ", e)
                    preferences.edit()
                            .putString(Constants.PREF_SSH_KEY_FILE, Constants.PREF_SSH_KEY_FILE_D)
                            .apply()
                }
            }

            preferences.edit()
                    .remove(Constants.Deprecated.PREF_SSH_KEY_PASSPHRASE)
                    .apply()
        }

        add(3, 4) {
            val knownHosts = preferences.getString(Constants.Deprecated.PREF_SSH_KNOWN_HOSTS,
                                                   Constants.Deprecated.PREF_SSH_KNOWN_HOSTS_D)
            if (knownHosts == Constants.Deprecated.PREF_SSH_KNOWN_HOSTS_D) return@add

            showInfo("While migrating preferences, the SSH known hosts preference " +
                     "was removed. You will be prompted to accept SSH host key the next time you " +
                     "connect using SSH.")
            preferences.edit()
                    .remove(Constants.Deprecated.PREF_SSH_KNOWN_HOSTS)
                    .apply()
        }

        add(4, 5) {
            val font = preferences.getString(Constants.PREF_BUFFER_FONT,
                                             Constants.PREF_BUFFER_FONT_D)
            val fontSet = font != Constants.PREF_BUFFER_FONT_D

            val colorSchemeDay = preferences.getString(Constants.PREF_COLOR_SCHEME_DAY,
                                                       Constants.PREF_COLOR_SCHEME_DAY_D) ?: ""
            val colorSchemeDaySet = colorSchemeDay.startsWith("/")

            val colorSchemeNight = preferences.getString(Constants.PREF_COLOR_SCHEME_NIGHT,
                                                         Constants.PREF_COLOR_SCHEME_NIGHT_D) ?: ""
            val colorSchemeNightSet = colorSchemeNight.startsWith("/")

            if (fontSet || colorSchemeDaySet || colorSchemeNightSet) {
                showInfo("The app no longer requests external storage permission. " +
                         "If you are using custom fonts or themes, " +
                         "you may have to import them in settings.")
            }
        }

        add(5, 6) {
            val volumeChangesSize = preferences.getBoolean(Constants.Deprecated.PREF_VOLUME_BTN_SIZE, Constants.Deprecated.PREF_VOLUME_BTN_SIZE_D)
            preferences.edit {
                this.remove(Constants.Deprecated.PREF_VOLUME_BTN_SIZE)
                this.putString(
                    Constants.PREF_VOLUME_ROLE,
                    (if (volumeChangesSize) VolumeRole.ChangeTextSize else VolumeRole.DoNothing).value
                )
            }
        }
    }
}


private const val VERSION_KEY = "preferences-version-key"


private fun showError(message: String, e: Exception) {
    val error = FriendlyExceptions(applicationContext).getFriendlyException(e).message
    Toaster.InfoToast.show(message + error)
    Toaster.InfoToast.show(message + error)
}

private fun showInfo(message: String) {
    Toaster.InfoToast.show(message)
    Toaster.InfoToast.show(message)
}