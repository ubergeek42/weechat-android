package androidx.preference

import android.content.Context
import android.content.DialogInterface
import android.content.res.TypedArray
import android.util.AttributeSet
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.preference.ThemeManager.enumerateThemes
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.utils.Toaster


class ThemePreference(context: Context, attrs: AttributeSet?) : DialogPreference(context, attrs), DialogFragmentGetter {
    private lateinit var defaultValue: String

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
        defaultValue = a.getString(index) ?: ""
        return defaultValue
    }

    private var themePath: String
        get() = sharedPreferences!!.getString(key, defaultValue) ?: ""
        set(path) {
            sharedPreferences!!.edit().putString(key, path).apply()
            notifyChanged()
        }

    override fun getSummary(): CharSequence {
        val path = themePath
        return if (path.isEmpty()) {
            context.getString(R.string.pref__ThemePreference__not_set)
        } else {
            try {
                ThemeInfo.fromPath(path).name
            } catch (e: Exception) {
                Toaster.ErrorToast.show(e)
                context.getString(R.string.pref__ThemePreference__error)
            }
        }
    }

    override fun getDialogFragment(): DialogFragment {
        return ThemePreferenceFragment()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    class ThemePreferenceFragment : PreferenceDialogFragmentCompat(), DialogInterface.OnClickListener {
        private lateinit var themes: List<ThemeInfo>

        @OptIn(ExperimentalStdlibApi::class)
        override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
            super.onPrepareDialogBuilder(builder)

            themes = enumerateThemes(requireContext()).sortedBy { it.name.lowercase() }.toList()

            val themeNames = themes.map { it.name }.toTypedArray()
            val currentPath = (preference as ThemePreference).themePath
            val currentIndex = themes.indexOfFirst { it.path == currentPath }   // -1 is ok

            builder.setSingleChoiceItems(themeNames, currentIndex, this)
            builder.setPositiveButton(getString(R.string.pref__ThemePreference__import_button)) { _, _ ->
                ThemeManager.requestThemeImport(requireActivity())
                dismiss()
            }
        }

        override fun onClick(dialog: DialogInterface, which: Int) {
            if (which >= 0) (preference as ThemePreference).themePath = (themes[which].path)
            dialog.dismiss()
        }

        override fun onDialogClosed(b: Boolean) {}
    }
}