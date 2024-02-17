package com.ubergeek42.WeechatAndroid.utils

import android.content.SharedPreferences
import androidx.core.content.edit
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
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
}
