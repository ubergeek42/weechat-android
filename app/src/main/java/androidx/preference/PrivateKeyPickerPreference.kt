package androidx.preference

import android.content.Context
import android.util.AttributeSet
import androidx.core.content.edit
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.utils.AndroidKeyStoreUtils
import com.ubergeek42.WeechatAndroid.utils.AndroidKeyStoreUtils.InsideSecurityHardware
import com.ubergeek42.WeechatAndroid.utils.Constants
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


// Regarding the accidental override suppressing annotation, see:
//   https://youtrack.jetbrains.com/issue/KT-12993
//   https://youtrack.jetbrains.com/issue/KT-46621
@Suppress("ACCIDENTAL_OVERRIDE")
class PrivateKeyPickerPreference(context: Context?, attrs: AttributeSet?) :
        PasswordedFilePickerPreference(context, attrs) {

    @Throws(Exception::class)
    override fun saveData(bytes: ByteArray?, passphrase: String): String {
        var valueToStore: String?
        var successMessage: String

        if (bytes != null) {
            val keyPair: KeyPair = try {
                SSHConnection.makeKeyPair(bytes, passphrase)
            } catch (sshlibException: Exception) {
                try {
                    makeKeyPair(bytes.toReader(), passphrase.toCharArray())
                } catch (bouncyCastleException: Exception) {
                    val wasOpenSshKey = String(bytes, StandardCharsets.UTF_8).contains("OPENSSH")
                    throw if (wasOpenSshKey) sshlibException else bouncyCastleException
                }
            }

            val algorithmName = keyPair.private.algorithm

            try {
                AndroidKeyStoreUtils.putKeyPairIntoAndroidKeyStore(keyPair, SSHConnection.KEYSTORE_ALIAS)
                valueToStore = STORED_IN_KEYSTORE
                successMessage = getInsideSecurityHardwareString(algorithmName)
            } catch (e: Exception) {
                kitty.warn("Error while putting %s key into AndroidKeyStore", algorithmName, e)
                valueToStore = Utils.serialize(keyPair)
                successMessage = context.getString(
                    R.string.pref__PrivateKeyPickerPreference__success_stored_outside_key_store,
                    algorithmName, e.message
                )
            }
        } else {
            try {
                AndroidKeyStoreUtils.deleteAndroidKeyStoreEntry(SSHConnection.KEYSTORE_ALIAS)
            } catch (e: Exception) {
                kitty.warn("Error while deleting key from AndroidKeyStore", e)
            }
            valueToStore = null
            successMessage = context.getString(R.string.pref__PrivateKeyPickerPreference__success_key_forgotten)
        }

        sharedPreferences!!.edit { putString(Constants.PREF_SSH_KEY_FILE, valueToStore) }
        notifyChanged()

        return successMessage
    }

    @Throws(GeneralSecurityException::class, IOException::class)
    private fun getInsideSecurityHardwareString(algorithm: String?): String {
        val insideSecurityHardware = AndroidKeyStoreUtils
                .isInsideSecurityHardware(SSHConnection.KEYSTORE_ALIAS)

        val stringId = when (insideSecurityHardware) {
            InsideSecurityHardware.YES ->
                R.string.pref__PrivateKeyPickerPreference__success_stored_inside_security_hardware_yes
            InsideSecurityHardware.NO ->
                R.string.pref__PrivateKeyPickerPreference__success_stored_inside_security_hardware_no
            InsideSecurityHardware.CANT_TELL ->
                R.string.pref__PrivateKeyPickerPreference__success_stored_inside_security_hardware_cant_tell
        }

        return context.getString(stringId, algorithm)
    }

    companion object {
        @Root private val kitty: Kitty = Kitty.make()

        const val STORED_IN_KEYSTORE = "woo hoo the key is stored in keystore!"

        @JvmStatic fun getData(data: String?): ByteArray? {
            return when (data) {
                STORED_IN_KEYSTORE -> SSHConnection.STORED_IN_KEYSTORE_MARKER
                else -> FilePreference.getData(data)
            }
        }
    }
}