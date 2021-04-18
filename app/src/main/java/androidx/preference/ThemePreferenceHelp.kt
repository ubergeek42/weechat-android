package androidx.preference

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.core.text.toSpannable
import com.ubergeek42.WeechatAndroid.upload.Suri
import com.ubergeek42.WeechatAndroid.upload.resolver
import com.ubergeek42.WeechatAndroid.upload.suppress
import com.ubergeek42.WeechatAndroid.utils.Toaster
import com.ubergeek42.WeechatAndroid.utils.getUris
import com.ubergeek42.WeechatAndroid.utils.replaceLinksWithCustomActions
import com.ubergeek42.WeechatAndroid.utils.saveUriToFile
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.File
import java.io.FileInputStream
import java.util.*


class ThemePreferenceHelp(context: Context?, attrs: AttributeSet?) : HelpPreference(context, attrs) {
    override fun getSummary(): CharSequence {
        var message = "You can create custom color schemes for this application. " +
                "Use arbitrary colors for UI elements, " +
                "customize highlight and read marker color, " +
                "change the WeeChat color palette! " +
                "<a href=\"https://github.com/ubergeek42/weechat-android/wiki/Custom-Color-Schemes\">Learn more</a><br>" +
                "<br>" +
                "Import color schemes from the color scheme dialogs " +
                "or put them into the following location:"

        ThemeManager.getThemeSearchDirectories(context).forEach { path ->
            message += "<br>&nbsp;&nbsp;&nbsp;&nbsp;$path"
        }

        return HtmlCompat.fromHtml(message, HtmlCompat.FROM_HTML_MODE_LEGACY)
    }
}
