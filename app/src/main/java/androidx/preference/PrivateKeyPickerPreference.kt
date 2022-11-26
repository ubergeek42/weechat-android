package androidx.preference

import android.content.Context
import android.util.AttributeSet
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.utils.AndroidKeyStoreUtils
import com.ubergeek42.WeechatAndroid.utils.AndroidKeyStoreUtils.InsideSecurityHardware
import com.ubergeek42.WeechatAndroid.utils.Constants
import com.ubergeek42.WeechatAndroid.utils.TinyMap
import com.ubergeek42.WeechatAndroid.utils.Utils
import com.ubergeek42.WeechatAndroid.utils.makeKeyPair
import com.ubergeek42.WeechatAndroid.utils.toReader
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root
import com.ubergeek42.weechat.relay.connection.SSHConnection
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.KeyPair

class PrivateKeyPickerPreference(context: Context?, attrs: AttributeSet?) :
        PasswordedFilePickerPreference(context, attrs) {
    @Throws(Exception::class) override fun saveData(bytes: ByteArray?,
                                                    passphrase: String): String {
        val context = context
        var key: String?
        var message: String
        if (bytes != null) {
            val keyPair: KeyPair
            keyPair = try {
                SSHConnection.makeKeyPair(bytes, passphrase)
            } catch (sshlibException: Exception) {
                try {
                    makeKeyPair(
                        bytes.toReader(), passphrase.toCharArray())
                } catch (bouncyCastleException: Exception) {
                    throw if (String(bytes,
                                     StandardCharsets.UTF_8).contains("OPENSSH")
                    ) sshlibException else bouncyCastleException
                }
            }
            val algorithm = keyPair.private.algorithm
            try {
                AndroidKeyStoreUtils.putKeyPairIntoAndroidKeyStore(keyPair,
                                                                   SSHConnection.KEYSTORE_ALIAS)
                key = STORED_IN_KEYSTORE
                message = getInsideSecurityHardwareString(algorithm)
            } catch (e: Exception) {
                kitty.warn("Error while putting %s key into AndroidKeyStore",
                           algorithm,
                           e)
                key = Utils.serialize(keyPair)
                message =
                    context.getString(R.string.pref__PrivateKeyPickerPreference__success_stored_outside_key_store,
                                      algorithm,
                                      e.message)
            }
        } else {
            key = null
            message =
                context.getString(R.string.pref__PrivateKeyPickerPreference__success_key_forgotten)
            try {
                AndroidKeyStoreUtils.deleteAndroidKeyStoreEntry(SSHConnection.KEYSTORE_ALIAS)
            } catch (e: Exception) {
                kitty.warn("Error while deleting key from AndroidKeyStore", e)
            }
        }
        sharedPreferences!!.edit()
                .putString(Constants.PREF_SSH_KEY_FILE, key)
                .apply()
        notifyChanged()
        return message
    }

    @Throws(GeneralSecurityException::class, IOException::class)
    fun getInsideSecurityHardwareString(algorithm: String?): String {
        val inside =
            AndroidKeyStoreUtils.isInsideSecurityHardware(SSHConnection.KEYSTORE_ALIAS)
        val resId = TinyMap.of(
            InsideSecurityHardware.YES,
            R.string.pref__PrivateKeyPickerPreference__success_stored_inside_security_hardware_yes,
            InsideSecurityHardware.NO,
            R.string.pref__PrivateKeyPickerPreference__success_stored_inside_security_hardware_cant_tell,
            InsideSecurityHardware.CANT_TELL,
            R.string.pref__PrivateKeyPickerPreference__success_stored_inside_security_hardware_cant_tell
        )[inside]
        return context.getString(resId, algorithm)
    }

    companion object {
        @Root
        private val kitty: Kitty = Kitty.make()
        const val STORED_IN_KEYSTORE = "woo hoo the key is stored in keystore!"
        @JvmStatic fun getData(data: String): ByteArray? {
            return if (STORED_IN_KEYSTORE == data) SSHConnection.STORED_IN_KEYSTORE_MARKER else FilePreference.getData(
                data)
        }
    }
}
