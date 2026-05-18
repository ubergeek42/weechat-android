package androidx.preference

import android.content.Context
import android.content.DialogInterface
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
import androidx.core.content.edit


class FontPreference(context: Context, attrs: AttributeSet?) : DialogPreference(context, attrs), DialogFragmentGetter {
    private var fontPaths: Set<String>?
        get() = sharedPreferences!!.getStringSet(Constants.PREF_BUFFER_FONTS, Constants.PREF_BUFFER_FONTS_D)
        set(paths) {
            sharedPreferences!!.edit { putStringSet(Constants.PREF_BUFFER_FONTS, paths) }
            notifyChanged()
        }

    override fun getSummary(): CharSequence {
        val paths = fontPaths
        return if (paths.isNullOrEmpty()) {
            context.getString(R.string.pref__FontPreference__default)
        } else {
            paths.joinToString(", ") { File(it).name }
        }
    }

    override fun getDialogFragment(): DialogFragment {
        return FontPreferenceFragment()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    class FontPreferenceFragment : PreferenceDialogFragmentCompat(), DialogInterface.OnClickListener {
        private lateinit var typefaces: List<TypefaceInfo>
        private lateinit var inflater: LayoutInflater

        @OptIn(ExperimentalStdlibApi::class)
        override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
            super.onPrepareDialogBuilder(builder)
            inflater = LayoutInflater.from(context)

            val defaultTypeface = TypefaceInfo.getDefault(requireContext().getString(R.string.pref__FontPreference__default))
            val availableTypefaces = enumerateTypefaces(requireContext())
            typefaces = listOf(defaultTypeface) + availableTypefaces.sortedBy { it.name.lowercase() }

            typefaces.forEach {
                println("Typeface: ${it.name} ${it.fonts}")
            }

            val currentPaths = (preference as FontPreference).fontPaths
            val currentIndex = typefaces.indexOfFirst { currentPaths == it.fontFilePaths.toSet() } // -1 is ok

            builder.setSingleChoiceItems(FontAdapter(), currentIndex, this)
            builder.setPositiveButton(getString(R.string.pref__FontPreference__import_button)) { _, _ ->
                FontManager.requestFontImport(requireActivity())
                dismiss()
            }
        }

        override fun onClick(dialog: DialogInterface, which: Int) {
            if (which >= 0) (preference as FontPreference).fontPaths = typefaces[which].fontFilePaths.toSet()
            dialog.dismiss()
        }

        override fun onDialogClosed(b: Boolean) {}

        ////////////////////////////////////////////////////////////////////////////////////////////

        private inner class FontAdapter : BaseAdapter() {
            override fun getCount() = typefaces.size
            override fun getItem(position: Int) = typefaces[position]
            override fun getItemId(position: Int) = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: inflater.inflate(androidx.appcompat.R.layout.select_dialog_singlechoice_material, parent, false)
                val textView = view.findViewById<CheckedTextView>(android.R.id.text1)

                val fontInfo = getItem(position)
                textView.apply {
                    ellipsize = TextUtils.TruncateAt.END
                    //setSingleLine()
                    typeface = fontInfo.typeface
                    text = fontInfo.name + " (${fontInfo.getStylesDescription().joinToString(", ")})"
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