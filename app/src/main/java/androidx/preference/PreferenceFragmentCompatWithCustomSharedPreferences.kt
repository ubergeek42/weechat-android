package androidx.preference

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import androidx.annotation.CallSuper
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty


inline fun <reified T, V> privateFieldMadeAccessible(fieldName: String) =
    object : ReadWriteProperty<T, V> {
        private val field = T::class.java
            .getDeclaredField(fieldName)
            .apply { isAccessible = true }

        override fun getValue(thisRef: T, property: KProperty<*>): V {
            return field.get(thisRef) as V
        }

        override fun setValue(thisRef: T, property: KProperty<*>, value: V) {
            field.set(thisRef, value)
        }
    }


/**
 * A somewhat hacky way of having custom shared preferences in a preference fragment.
 * In [onCreate], `PreferenceFragmentCompat` assigns to `mPreferenceManager`,
 * and then calls `onCreatePreferences`. We intercept this call and replace the manager.
 *
 * Overriding [PreferenceManager.getSharedPreferences] should be safe,
 * as [PreferenceManager.mSharedPreferences] seems not be used elsewhere.
 */
@SuppressLint("RestrictedApi")
abstract class PreferenceFragmentCompatWithCustomSharedPreferences : PreferenceFragmentCompat() {
    abstract fun getCustomSharedPreferences(): SharedPreferences

    private var mPreferenceManager: PreferenceManager
        by privateFieldMadeAccessible<PreferenceFragmentCompat, _>("mPreferenceManager")

    @CallSuper override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val customSharedPreferences = getCustomSharedPreferences()

        val customPreferenceManager = object : PreferenceManager(requireContext()) {
            override fun getSharedPreferences() = customSharedPreferences
        }

        mPreferenceManager = customPreferenceManager
        mPreferenceManager.onNavigateToScreenListener = this
    }
}
