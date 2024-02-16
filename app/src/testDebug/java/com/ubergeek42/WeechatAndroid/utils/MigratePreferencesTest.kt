package com.ubergeek42.WeechatAndroid.utils

import android.content.SharedPreferences
import android.util.Base64
import androidx.core.content.edit
import androidx.preference.PrivateKeyPickerPreference
import androidx.test.core.app.ApplicationProvider
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ubergeek42.weechat.relay.connection.SSHConnection
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config


@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
internal class MigratePreferencesTest {
    private lateinit var defaultSharedPreferences: SharedPreferences
    private lateinit var encryptedSharedPreferences: SharedPreferences
    private lateinit var multiSharedPreferences: SharedPreferences

    @Before
    fun initPreferences() {
        defaultSharedPreferences = SPMockBuilder().createSharedPreferences()
        encryptedSharedPreferences = SPMockBuilder().createSharedPreferences()

        multiSharedPreferences = object : MultiSharedPreferences() {
            override val allActualSharedPreferences = listOf(defaultSharedPreferences, encryptedSharedPreferences)

            override fun getActualSharedPreferences(key: String?) = when {
                key == null -> defaultSharedPreferences
                key.startsWith("encrypted") -> encryptedSharedPreferences
                else -> defaultSharedPreferences
            }
        }
    }

    @Test
    fun `Test relay password migration`() {
        defaultSharedPreferences.edit { putString(Constants.Deprecated.PREF_PASSWORD, "foo") }
        MigratePreferences(multiSharedPreferences).migrate()
        val value = encryptedSharedPreferences.getString(Constants.PREF_PASSWORD, "missing")
        Assert.assertEquals("foo", value)
    }

    @Test
    fun `Test upload basic authentication password migration`() {
        defaultSharedPreferences.edit { putString(Constants.Deprecated.PREF_UPLOAD_AUTHENTICATION_BASIC_PASSWORD, "foo") }
        MigratePreferences(multiSharedPreferences).migrate()
        val value = encryptedSharedPreferences.getString(Constants.PREF_UPLOAD_AUTHENTICATION_BASIC_PASSWORD, "missing")
        Assert.assertEquals("foo", value)
    }

    @Test
    fun `Test SSH key migration`() {
        applicationContext = ApplicationProvider.getApplicationContext()

        @Suppress("SpellCheckingInspection")
        val base64EncodedPemWithOpenSsh25519Key = """
            -----BEGIN OPENSSH PRIVATE KEY-----
            b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtzc2gtZW
            QyNTUxOQAAACBlA9NtYa1wnZT0e3rPepp3Ea8GXbqDy3dJPHfByfBwygAAAJhT15i+U9eY
            vgAAAAtzc2gtZWQyNTUxOQAAACBlA9NtYa1wnZT0e3rPepp3Ea8GXbqDy3dJPHfByfBwyg
            AAAEAl/41KTVxCz7P25x0RcXqmwAWiNHQNJiMEpUMaYzdz2WUD021hrXCdlPR7es96mncR
            rwZduoPLd0k8d8HJ8HDKAAAAEXNAREVTS1RPUC01TUlIVlFJAQIDBA==
            -----END OPENSSH PRIVATE KEY-----
        """
            .trimIndent()
            .toByteArray()
            .let { Base64.encodeToString(it, Base64.NO_WRAP) }

        defaultSharedPreferences.edit {
            putString(Constants.Deprecated.PREF_SSH_PASS, "needs to be present for migration")
            putString(Constants.Deprecated.PREF_SSH_KEY, base64EncodedPemWithOpenSsh25519Key)
        }

        MigratePreferences(multiSharedPreferences).migrate()

        val prefSshKey = encryptedSharedPreferences.getString(Constants.PREF_SSH_KEY_FILE, "missing")
        val keyBytes = PrivateKeyPickerPreference.getData(prefSshKey)
        val keyPair = SSHConnection.deserializeKeyPair(keyBytes)

        Assert.assertEquals("EdDSA", keyPair.public.algorithm)
    }
}
