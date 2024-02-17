package com.ubergeek42.WeechatAndroid.utils

import android.content.SharedPreferences
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import org.junit.Before
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
}
