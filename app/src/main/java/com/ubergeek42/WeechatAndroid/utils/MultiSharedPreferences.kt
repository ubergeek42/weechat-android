package com.ubergeek42.WeechatAndroid.utils

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ubergeek42.WeechatAndroid.upload.applicationContext


private val masterKey = MasterKey.Builder(applicationContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

private val defaultSharedPreferences =
    PreferenceManager.getDefaultSharedPreferences(applicationContext)

private val encryptedSharedPreferences = EncryptedSharedPreferences.create(
    applicationContext,
    "encrypted_shared_preferences",
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)

@JvmField val multiSharedPreferences = object : MultiSharedPreferences() {
    override val allActualSharedPreferences = listOf(defaultSharedPreferences, encryptedSharedPreferences)

    override fun getActualSharedPreferences(key: String?) = when {
        key == null -> defaultSharedPreferences
        key.startsWith("encrypted") -> encryptedSharedPreferences
        else -> defaultSharedPreferences
    }
}


/**
 * A [SharedPreferences] that can dispatch calls to other `SharedPreferences` based on the key.
 * [getAll] assumes that no two delegate `SharedPreferences` have the same key.
 */
abstract class MultiSharedPreferences : SharedPreferences {
    abstract fun getActualSharedPreferences(key: String?): SharedPreferences
    abstract val allActualSharedPreferences: Collection<SharedPreferences>

    override fun getAll(): Map<String, *> = mutableMapOf<String, Any?>()
        .also { result -> allActualSharedPreferences.forEach { result.putAll(it.all) } }

    override fun getString(key: String, defValue: String?): String? =
        getActualSharedPreferences(key).getString(key, defValue)

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        getActualSharedPreferences(key).getStringSet(key, defValues)

    override fun getInt(key: String, defValue: Int): Int =
        getActualSharedPreferences(key).getInt(key, defValue)

    override fun getLong(key: String, defValue: Long): Long =
        getActualSharedPreferences(key).getLong(key, defValue)

    override fun getFloat(key: String, defValue: Float): Float =
        getActualSharedPreferences(key).getFloat(key, defValue)

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        getActualSharedPreferences(key).getBoolean(key, defValue)

    override fun contains(key: String): Boolean =
        getActualSharedPreferences(key).contains(key)

    override fun edit(): SharedPreferences.Editor =
        Editor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        allActualSharedPreferences.forEach { it.registerOnSharedPreferenceChangeListener(listener) }
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        allActualSharedPreferences.forEach { it.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    inner class Editor : SharedPreferences.Editor {
        private val preferencesToEditor = mutableMapOf<SharedPreferences, SharedPreferences.Editor>()

        private fun getEditor(key: String?): SharedPreferences.Editor {
            val preferences = getActualSharedPreferences(key)
            return preferencesToEditor.getOrPut(preferences) { preferences.edit() }
        }

        private fun getAllOpenEditors() = preferencesToEditor.values

        private fun getEditorsForAllPreferences() = allActualSharedPreferences
            .map { preferencesToEditor.getOrPut(it) { it.edit() } }

        override fun putString(key: String?, value: String?) = this
            .also { getEditor(key).putString(key, value) }

        override fun putStringSet(key: String?, values: MutableSet<String>?) = this
            .also { getEditor(key).putStringSet(key, values) }

        override fun putInt(key: String?, value: Int) = this
            .also { getEditor(key).putInt(key, value) }

        override fun putLong(key: String?, value: Long) = this
            .also { getEditor(key).putLong(key, value) }

        override fun putFloat(key: String?, value: Float) = this
            .also { getEditor(key).putFloat(key, value) }

        override fun putBoolean(key: String?, value: Boolean) = this
            .also { getEditor(key).putBoolean(key, value) }

        override fun remove(key: String?) = this
            .also { getEditor(key).remove(key) }

        override fun commit(): Boolean {
            return getAllOpenEditors().all { it.commit() }
        }

        override fun apply() {
            getAllOpenEditors().forEach { it.apply() }
        }

        override fun clear() = this
            .also { getEditorsForAllPreferences().forEach { it.clear() } }
    }
}