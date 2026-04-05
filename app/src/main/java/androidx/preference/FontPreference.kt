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
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class FontPreference(context: Context, attrs: AttributeSet?) : DialogPreference(context, attrs), DialogFragmentGetter {
    private var fontPaths: Set<String>?
        get() = sharedPreferences!!.getStringSet(Constants.PREF_BUFFER_FONTS, Constants.PREF_BUFFER_FONTS_D)
        set(paths) {
            sharedPreferences!!.edit { putStringSet(Constants.PREF_BUFFER_FONTS, paths) }
            setSummary()
        }

    override fun onAttached() {
        super.onAttached()
        setSummary()
    }

    fun setSummary() {
        val paths = fontPaths
        if (paths.isNullOrEmpty()) {
            setSummary(R.string.pref__FontPreference__default)
        } else {
            (context as? LifecycleOwner)?.lifecycleScope
                    ?.launch(Dispatchers.IO) {
                        enumerateTypefaces(paths.map(::File))
                               .firstOrNull()
                               ?.getDescription()
                               ?.let { withContext(Dispatchers.Main) { setSummary(it) } }
                    }
        }
    }

    override fun getDialogFragment(): DialogFragment {
        return FontPreferenceFragment()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    class FontPreferenceFragment : PreferenceDialogFragmentCompat(), DialogInterface.OnClickListener {
        private lateinit var typefaceInfos: List<TypefaceInfo>
        private lateinit var inflater: LayoutInflater

        @OptIn(ExperimentalStdlibApi::class)
        override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
            super.onPrepareDialogBuilder(builder)
            inflater = LayoutInflater.from(context)

            val defaultTypefaceInfo = TypefaceInfo.getDefault(requireContext())
            val availableTypefaceInfos = enumerateTypefaces(requireContext())
            typefaceInfos = listOf(defaultTypefaceInfo) + availableTypefaceInfos.sortedBy { it.name.lowercase() }

            val currentPaths = (preference as FontPreference).fontPaths
            val currentIndex = typefaceInfos.indexOfFirst { currentPaths == it.fontFilePaths } // -1 is ok

            builder.setSingleChoiceItems(FontAdapter(), currentIndex, this)
            builder.setPositiveButton(getString(R.string.pref__FontPreference__import_button)) { _, _ ->
                requireActivity().requestFontImport()
                dismiss()
            }
        }

        override fun onClick(dialog: DialogInterface, which: Int) {
            if (which >= 0) (preference as FontPreference).fontPaths = typefaceInfos[which].fontFilePaths
            dialog.dismiss()
        }

        override fun onDialogClosed(b: Boolean) {}

        ////////////////////////////////////////////////////////////////////////////////////////////

        private inner class FontAdapter : BaseAdapter() {
            override fun getCount() = typefaceInfos.size
            override fun getItem(position: Int) = typefaceInfos[position]
            override fun getItemId(position: Int) = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: inflater.inflate(androidx.appcompat.R.layout.select_dialog_singlechoice_material, parent, false)
                val textView = view.findViewById<CheckedTextView>(android.R.id.text1)

                val fontInfo = getItem(position)
                textView.apply {
                    ellipsize = TextUtils.TruncateAt.END
                    typeface = fontInfo.typeface
                    text = fontInfo.getDescription()
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