package androidx.preference

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
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
import java.io.IOException


class FontPreferenceHelp(context: Context?, attrs: AttributeSet?) : HelpPreference(context, attrs) {
    override fun getSummary(): CharSequence {
        var message = "Non-monospace fonts will not work well with alignment. " +
                      "<a href='import'>Tap here to import fonts</a> " +
                      "or put them into one of the following locations:"

        FontManager.getFontSearchDirectories(context).forEach { path ->
            message += "<br>&nbsp;&nbsp;&nbsp;&nbsp;$path"
        }

        return HtmlCompat.fromHtml(message, FROM_HTML_MODE_LEGACY).toSpannable().apply {
            replaceLinksWithCustomActions(mapOf("import" to {
                val activity = context as AppCompatActivity
                activity.startActivityForResult(importFontsIntent, IMPORT_FONTS_REQUEST_CODE, null)
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

                    if (!arrayOf(".ttf", ".otf").any { fileName.endsWith(it, true) }) {
                        throw IOException("Doesn't look like a font: $fileName")
                    }

                    val folder = context.getExternalFilesDir(CUSTOM_FONTS_DIRECTORY)
                    val file = File(folder, fileName)

                    try {
                        context.saveUriToFile(uri, file)
                    } catch (e: Exception) {
                        file.delete()
                        throw e
                    }

                    // Typeface.createFromFile() does not validate the font,
                    // so we are not doing any validation

                    imported.add(fileName)
                }
            }

            if (imported.isNotEmpty()) {
                Toaster.SuccessToast.show("Imported: " + imported.joinToString(", "))
            }
        }
    }
}


const val CUSTOM_FONTS_DIRECTORY = "fonts"
const val IMPORT_FONTS_REQUEST_CODE = 1234

private val importFontsIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
    type = "font/*"
    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
    addCategory(Intent.CATEGORY_OPENABLE)
    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
}
