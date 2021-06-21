package androidx.preference

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.WeechatAndroid.upload.Suri
import com.ubergeek42.WeechatAndroid.upload.applicationContext
import com.ubergeek42.WeechatAndroid.upload.resolver
import com.ubergeek42.WeechatAndroid.upload.suppress
import com.ubergeek42.WeechatAndroid.utils.Constants
import com.ubergeek42.WeechatAndroid.utils.Toaster
import com.ubergeek42.WeechatAndroid.utils.getUris
import com.ubergeek42.WeechatAndroid.utils.saveUriToFile
import com.ubergeek42.weechat.ColorScheme
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.File
import java.io.FileInputStream
import java.util.*


object ThemeManager {
    fun getThemeSearchDirectories(context: Context): List<String> {
        val internalFontFolder = context.getExternalFilesDir(CUSTOM_THEMES_DIRECTORY)
        return if (internalFontFolder != null) listOf(internalFontFolder.toString()) else listOf()
    }

    @JvmStatic fun loadColorSchemeFromPreferences(context: Context) {
        val path = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(if (P.darkThemeActive) Constants.PREF_COLOR_SCHEME_NIGHT else Constants.PREF_COLOR_SCHEME_DAY,
                           if (P.darkThemeActive) Constants.PREF_COLOR_SCHEME_NIGHT_D else Constants.PREF_COLOR_SCHEME_DAY_D)
        suppress<Exception>(showToast = true) {
            val themeInfo = ThemeInfo.fromPath(path!!)
            ColorScheme.set(ColorScheme(themeInfo.properties))
        }
    }

    private fun Context.getAssetThemes() = sequence {
        assets.list("")?.forEach { assetName ->
            if (assetName.endsWith("theme.properties", true)) {
                yield(ThemeInfo.fromPath(assetName))
            }
        }
    }

    private fun Context.getExternalThemes() = sequence {
        getThemeSearchDirectories(this@getExternalThemes).forEach { directoryName ->
            val directory = File(directoryName)
            if (directory.exists()) {
                directory.listFiles()?.forEach { file ->
                    suppress<Exception>(showToast = true) {
                        yield(ThemeInfo.fromPath(file.absolutePath))
                    }
                }
            }
        }
    }

    fun enumerateThemes(context: Context) = context.getAssetThemes() + context.getExternalThemes()

    ////////////////////////////////////////////////////////////////////////////////////////////////

    fun requestThemeImport(activity: Activity) {
        activity.startActivityForResult(importThemesIntent, IMPORT_THEMES_REQUEST_CODE, null)
    }

    @JvmStatic fun importThemesFromResultIntent(context: Context, intent: Intent?) {
        val imported = sequence {
            intent?.getUris()?.forEach { uri ->
                suppress<Exception>(showToast = true) {
                    val mediaType = resolver.getType(uri)?.toMediaTypeOrNull()
                    val fileName = Suri.makeFileNameWithExtension(uri, mediaType)
                    val folder = context.getExternalFilesDir(CUSTOM_THEMES_DIRECTORY)
                    val file = File(folder, fileName)

                    val themeName = try {
                        context.saveUriToFile(uri, file)
                        ThemeInfo.fromPath(file.path).name
                    } catch (e: Exception) {
                        file.delete()
                        throw e
                    }

                    yield(themeName)
                }
            }
        }

        Toaster.SuccessToast.show(context.getString(R.string.pref__ThemePreference__imported,
                                                    imported.joinToString(", ")))
    }
}


class ThemeInfo(
    val name: String,
    val path: String,
    val properties: Properties,
) {
    companion object {
        fun fromPath(path: String): ThemeInfo {
            val properties = Properties()
            val name = if (path.startsWith("/")) {
                properties.load(FileInputStream(path))
                properties.getProperty("name") ?: File(path).name
            } else {
                properties.load(applicationContext.assets.open(path))
                properties.getProperty("name") ?: path
            }
            return ThemeInfo(name, path, properties)
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