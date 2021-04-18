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
import com.ubergeek42.weechat.ColorScheme
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
                "<a href='import'>Tap here to import themes</a> " +
                "or put them into the following location:"

        ThemeManager.getThemeSearchDirectories(context).forEach { path ->
            message += "<br>&nbsp;&nbsp;&nbsp;&nbsp;$path"
        }

        return HtmlCompat.fromHtml(message, HtmlCompat.FROM_HTML_MODE_LEGACY).toSpannable().apply {
            replaceLinksWithCustomActions(mapOf("import" to {
                val activity = context as AppCompatActivity
                activity.startActivityForResult(importThemesIntent, IMPORT_THEMES_REQUEST_CODE, null)
            }))
        }
    }

    companion object {
        @JvmStatic fun onActivityResult(context: Context, intent: Intent?) {
            val imported = mutableListOf<String>()

            intent?.getUris()?.forEach { uri ->
                suppress<Exception>(showToast = true) {
                    val mediaType = resolver.getType(uri)?.toMediaTypeOrNull()
                    val fileName = Suri.makeFileNameWithExtension(uri, mediaType)
                    val folder = context.getExternalFilesDir(CUSTOM_THEMES_DIRECTORY)
                    val file = File(folder, fileName)

                    val themeName = try {
                        context.saveUriToFile(uri, file)
                        getThemeName(context, file.path)
                    } catch (e: Exception) {
                        file.delete()
                        throw e
                    }

                    imported.add(themeName)
                }
            }

            if (imported.isNotEmpty()) {
                Toaster.SuccessToast.show("Imported: " + imported.joinToString(", "))
            }
        }

        @JvmStatic fun getThemeName(context: Context, path: String): String {
            val properties = Properties()
            return if (path.startsWith("/")) {
                properties.load(FileInputStream(path))
                properties.getProperty("name") ?: File(path).name
            } else {
                properties.load(context.assets.open(path))
                properties.getProperty("name") ?: path
            }
        }
    }
}


const val CUSTOM_THEMES_DIRECTORY = "themes"
const val IMPORT_THEMES_REQUEST_CODE = 1235

// neither text/plain nor text/x-java-properties pick properties
private val importThemesIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
    type = "*/*"
    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
    addCategory(Intent.CATEGORY_OPENABLE)
    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
}