package androidx.preference

import android.content.Context
import android.content.DialogInterface
import android.graphics.Typeface
import android.text.TextUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckedTextView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.preference.DialogFragmentGetter
import androidx.preference.FontPreference.FontPreferenceFragment
import androidx.preference.PreferenceDialogFragmentCompat
import androidx.preference.FontPreference
import androidx.preference.FontPreference.FontPreferenceFragment.FontAdapter
import androidx.preference.PreferenceViewHolder
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.utils.Constants
import java.io.File
import java.util.*

class FontPreference  ////////////////////////////////////////////////////////////////////////////////////////////////
(context: Context?, attrs: AttributeSet?) : DialogPreference(context, attrs), DialogFragmentGetter {
    private var fontPath: String
        private get() {
            var path = sharedPreferences.getString(key, Constants.PREF_BUFFER_FONT_D)
            if ("" != path) path = File(path).name
            return path!!
        }
        private set(path) {
            sharedPreferences.edit().putString(key, path).apply()
            notifyChanged()
        }

    override fun getSummary(): CharSequence {
        val path = fontPath
        return if ("" == path) context.getString(R.string.pref__FontPreference__default) else path
    }

    override fun getDialogFragment(): DialogFragment {
        return FontPreferenceFragment()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    class FontPreferenceFragment : PreferenceDialogFragmentCompat(), DialogInterface.OnClickListener {
        private var fonts: LinkedList<FontManager.FontInfo>? = null
        private var inflater: LayoutInflater? = null
        override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
            super.onPrepareDialogBuilder(builder)
            inflater = requireContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            fonts = FontManager.enumerateFonts(requireContext())
            Collections.sort(fonts)

            // add a "fake" default monospace font
            fonts!!.addFirst(FontManager.FontInfo(getString(R.string.pref__FontPreference__default), "", Typeface.MONOSPACE))

            // get index of currently selected font
            val currentPath = (preference as FontPreference).fontPath
            var idx = 0
            var checked_item = 0
            for (font in fonts!!) {
                if (font.path == currentPath) {
                    checked_item = idx
                    break
                }
                idx++
            }
            builder.setSingleChoiceItems(FontAdapter(), checked_item, this)
            builder.setPositiveButton(null, null)
        }

        override fun onClick(dialog: DialogInterface, which: Int) {
            if (which >= 0) (preference as FontPreference).fontPath = fonts!![which].path
            dialog.dismiss()
        }

        override fun onDialogClosed(b: Boolean) {}

        ////////////////////////////////////////////////////////////////////////////////////////////
        inner class FontAdapter : BaseAdapter() {
            override fun getCount(): Int {
                return fonts!!.size
            }

            override fun getItem(position: Int): Any {
                return fonts!![position]
            }

            override fun getItemId(position: Int): Long {
                return position.toLong()
            }

            override fun getView(position: Int, view: View, parent: ViewGroup): View {
                var view = view
                if (view == null) {
                    view = inflater!!.inflate(androidx.appcompat.R.layout.select_dialog_singlechoice_material, parent, false)
                    val tv = view.findViewById<CheckedTextView>(android.R.id.text1)
                    tv.ellipsize = TextUtils.TruncateAt.END
                    tv.setSingleLine()
                }
                val font = getItem(position) as FontManager.FontInfo
                val tv = view.findViewById<CheckedTextView>(android.R.id.text1)
                tv.typeface = font.typeface
                tv.text = font.name
                return view
            }
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val summary = holder.findViewById(android.R.id.summary) as TextView
        summary.maxHeight = Int.MAX_VALUE
    }
}