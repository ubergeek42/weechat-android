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
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.utils.Constants
import java.io.File


class FontPreference(context: Context, attrs: AttributeSet?) : DialogPreference(context, attrs), DialogFragmentGetter {
    private var fontPath: String
        get() = sharedPreferences!!.getString(key, Constants.PREF_BUFFER_FONT_D) ?: ""
        set(path) {
            sharedPreferences!!.edit().putString(key, path).apply()
            notifyChanged()
        }

    override fun getSummary(): CharSequence {
        val path = fontPath
        return if (path.isEmpty()) {
            context.getString(R.string.pref__FontPreference__default)
        } else {
            File(path).name
        }
    }

    override fun getDialogFragment(): DialogFragment {
        return FontPreferenceFragment()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    class FontPreferenceFragment : PreferenceDialogFragmentCompat(), DialogInterface.OnClickListener {
        private lateinit var fonts: List<FontInfo>
        private lateinit var inflater: LayoutInflater

        @OptIn(ExperimentalStdlibApi::class)
        override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
            super.onPrepareDialogBuilder(builder)
            inflater = LayoutInflater.from(context)

            val fakeDefaultFontName = getString(R.string.pref__FontPreference__default)
            val fakeDefaultFont = FontInfo(fakeDefaultFontName, "", Typeface.MONOSPACE)
            val managerFonts = FontManager.enumerateFonts(requireContext())
            fonts = listOf(fakeDefaultFont) + managerFonts.sortedBy { it.name.lowercase() }

            val currentPath = (preference as FontPreference).fontPath
            val currentIndex = fonts.indexOfFirst { it.path == currentPath }  // -1 is ok

            builder.setSingleChoiceItems(FontAdapter(), currentIndex, this)
            builder.setPositiveButton(getString(R.string.pref__FontPreference__import_button)) { _, _ ->
                FontManager.requestFontImport(requireActivity())
                dismiss()
            }
        }

        override fun onClick(dialog: DialogInterface, which: Int) {
            if (which >= 0) (preference as FontPreference).fontPath = fonts[which].path
            dialog.dismiss()
        }

        override fun onDialogClosed(b: Boolean) {}

        ////////////////////////////////////////////////////////////////////////////////////////////

        private inner class FontAdapter : BaseAdapter() {
            override fun getCount() = fonts.size
            override fun getItem(position: Int) = fonts[position]
            override fun getItemId(position: Int) = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: inflater.inflate(androidx.appcompat.R.layout.select_dialog_singlechoice_material, parent, false)
                val textView = view.findViewById<CheckedTextView>(android.R.id.text1)

                val fontInfo = getItem(position)
                textView.apply {
                    ellipsize = TextUtils.TruncateAt.END
                    setSingleLine()
                    typeface = fontInfo.typeface
                    text = fontInfo.name
                }

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