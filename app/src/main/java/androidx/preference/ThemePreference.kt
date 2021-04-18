package androidx.preference

import android.content.Context
import android.content.DialogInterface
import android.content.res.TypedArray
import android.text.TextUtils
import android.util.AttributeSet
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.preference.ThemeManager.ThemeInfo
import androidx.preference.ThemeManager.enumerateThemes
import androidx.preference.ThemePreferenceHelp.Companion.getThemeName
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.utils.Toaster
import java.util.*

class ThemePreference(context: Context?, attrs: AttributeSet?) : DialogPreference(context, attrs), DialogFragmentGetter {
    ////////////////////////////////////////////////////////////////////////////////////////////////
    private var defaultValue: String? = null
    private val themePath: String?
        private get() = sharedPreferences.getString(key, defaultValue)

    private fun setThemePath(path: String) {
        sharedPreferences.edit().putString(key, path).apply()
        notifyChanged()
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
        return a.getString(index).also { defaultValue = it }!!
    }

    override fun getSummary(): CharSequence {
        val path = themePath
        return if (TextUtils.isEmpty(path)) {
            context.getString(R.string.pref__ThemePreference__not_set)
        } else {
            try {
                getThemeName(context, path!!)
            } catch (e: Exception) {
                Toaster.ErrorToast.show(e)
                "Error"
            }
        }
    }

    override fun getDialogFragment(): DialogFragment {
        return ThemePreferenceFragment()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    class ThemePreferenceFragment : PreferenceDialogFragmentCompat(), DialogInterface.OnClickListener {
        private var themes: LinkedList<ThemeInfo>? = null
        override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
            super.onPrepareDialogBuilder(builder)
            themes = enumerateThemes(requireContext())
            Collections.sort(themes)

            // find index of the current theme, and while we are at it
            // create a CharSequence[] copy of the theme name list
            val list = arrayOfNulls<CharSequence>(themes!!.size)
            val currentPath = (preference as ThemePreference).themePath
            var idx = 0
            var checked_item = 0
            for (theme in themes!!) {
                if (theme.path == currentPath) checked_item = idx
                list[idx] = theme.name
                idx++
            }
            builder.setSingleChoiceItems(list, checked_item, this)
            builder.setPositiveButton(null, null)
        }

        override fun onClick(dialog: DialogInterface, which: Int) {
            if (which >= 0) (preference as ThemePreference).setThemePath(themes!![which].path)
            dialog.dismiss()
        }

        override fun onDialogClosed(b: Boolean) {}
    }
}