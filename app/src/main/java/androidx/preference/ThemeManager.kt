package androidx.preference

import android.content.Context
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.WeechatAndroid.upload.applicationContext
import com.ubergeek42.WeechatAndroid.upload.suppress
import com.ubergeek42.WeechatAndroid.utils.Constants
import com.ubergeek42.weechat.ColorScheme
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
