package androidx.preference

import android.content.Context
import android.content.res.AssetManager
import android.preference.PreferenceManager
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.WeechatAndroid.utils.Constants
import com.ubergeek42.WeechatAndroid.utils.Toaster
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root
import com.ubergeek42.weechat.ColorScheme
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.*

object ThemeManager {
    @Root
    private val kitty: Kitty = Kitty.make()
    fun getThemeSearchDirectories(context: Context): List<String> {
        val out: MutableList<String> = ArrayList()
        val appSpecificFontFolder = context.getExternalFilesDir(CUSTOM_THEMES_DIRECTORY)
        if (appSpecificFontFolder != null) out.add(appSpecificFontFolder.toString())
        return out
    }

    @JvmStatic fun loadColorSchemeFromPreferences(context: Context) {
        val path = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(if (P.darkThemeActive) Constants.PREF_COLOR_SCHEME_NIGHT else Constants.PREF_COLOR_SCHEME_DAY,
                        if (P.darkThemeActive) Constants.PREF_COLOR_SCHEME_NIGHT_D else Constants.PREF_COLOR_SCHEME_DAY_D)
        val p = loadColorScheme(path, context.assets)
        if (p == null) Toaster.ErrorToast.show(R.string.pref__ThemeManager__error_loading_color_scheme, path!!) else ColorScheme.set(ColorScheme(p))
    }

    @JvmStatic fun enumerateThemes(context: Context): LinkedList<ThemeInfo> {
        val themes = LinkedList<ThemeInfo>()
        val manager = context.assets

        // load themes from assets
        try {
            val builtin_themes = manager.list("")
            if (builtin_themes != null) {
                for (theme in builtin_themes) {
                    if (!theme.toLowerCase().endsWith("theme.properties")) continue
                    val p = loadColorScheme(theme, manager)
                    if (p != null) themes.add(ThemeInfo(p.getProperty("name", theme), theme))
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        // load themes from disk
        for (directory in getThemeSearchDirectories(context)) {
            val dir = File(directory)
            if (!dir.exists()) continue
            val files = dir.listFiles() ?: continue
            for (file in files) {
                if (!file.name.toLowerCase().endsWith("theme.properties")) continue
                val p = loadColorScheme(file.absolutePath, null)
                if (p != null) themes.add(ThemeInfo(p.getProperty("name", file.name), file.absolutePath))
            }
        }
        return themes
    }

    private fun loadColorScheme(path: String?, manager: AssetManager?): Properties? {
        val p = Properties()
        return try {
            p.load(if (path!!.startsWith("/")) FileInputStream(path) else manager!!.open(path))
            p
        } catch (e: IOException) {
            kitty.error("Failed to load file $path", e)
            null
        }
    }

    class ThemeInfo internal constructor(var name: String, var path: String) : Comparable<ThemeInfo> {
        override fun compareTo(another: ThemeInfo): Int {
            return name.compareTo(another.name)
        }
    }
}