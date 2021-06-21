package androidx.preference

import android.content.Context
import android.util.AttributeSet
import androidx.core.text.HtmlCompat
import com.ubergeek42.WeechatAndroid.R


class ThemePreferenceHelp(context: Context?, attrs: AttributeSet?) : HelpPreference(context, attrs) {
    override fun getSummary(): CharSequence {
        val indent = "<br>&nbsp;&nbsp;&nbsp;&nbsp;"
        val directories = ThemeManager.getThemeSearchDirectories(context)
        val message = context.getString(R.string.pref__ThemePreferenceHelp__summary,
                indent + directories.joinToString(indent))

        return HtmlCompat.fromHtml(message, HtmlCompat.FROM_HTML_MODE_LEGACY)
    }
}
