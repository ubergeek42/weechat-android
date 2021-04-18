package androidx.preference

import android.content.Context
import android.util.AttributeSet
import androidx.core.text.HtmlCompat
import androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY


class FontPreferenceHelp(context: Context?, attrs: AttributeSet?) : HelpPreference(context, attrs) {
    override fun getSummary(): CharSequence {
        var message = "Non-monospace fonts will not work well with alignment. " +
                      "Import fonts from the Buffer font dialog, " +
                      "or put them into one of the following locations:"

        FontManager.getFontSearchDirectories(context).forEach { path ->
            message += "<br>&nbsp;&nbsp;&nbsp;&nbsp;$path"
        }

        return HtmlCompat.fromHtml(message, FROM_HTML_MODE_LEGACY)
    }
}
