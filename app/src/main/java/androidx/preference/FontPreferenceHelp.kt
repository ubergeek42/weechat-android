package androidx.preference

import android.content.Context
import android.util.AttributeSet
import androidx.core.text.HtmlCompat
import androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
import com.ubergeek42.WeechatAndroid.R


class FontPreferenceHelp(context: Context?, attrs: AttributeSet?) : HelpPreference(context, attrs) {
    override fun getSummary(): CharSequence {
        val indent = "<br>&nbsp;&nbsp;&nbsp;&nbsp;"
        val directories = FontManager.getFontSearchDirectories(context)
        val message = context.getString(R.string.pref__FontPreferenceHelp__summary,
                indent + directories.joinToString(indent))

        return HtmlCompat.fromHtml(message, FROM_HTML_MODE_LEGACY)
    }
}
