package androidx.preference;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.service.P;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;
import com.ubergeek42.weechat.ColorScheme;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_COLOR_SCHEME_DAY;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_COLOR_SCHEME_DAY_D;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_COLOR_SCHEME_NIGHT;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_COLOR_SCHEME_NIGHT_D;
import static com.ubergeek42.WeechatAndroid.utils.Toaster.ErrorToast;

public class ThemeManager {

    final private static @Root Kitty kitty = Kitty.make();

    static List<String> getThemeSearchDirectories(Context context) {
        List<String> out = new ArrayList<>(Collections.singletonList(
                Environment.getExternalStorageDirectory().toString() + "/weechat"));
        File appSpecificFontFolder = context.getExternalFilesDir("themes");
        if (appSpecificFontFolder != null)
            out.add(appSpecificFontFolder.toString());
        return out;
    }

    public static void loadColorSchemeFromPreferences(@NonNull Context context) {
        String path = android.preference.PreferenceManager.getDefaultSharedPreferences(context)
                .getString(P.darkThemeActive ? PREF_COLOR_SCHEME_NIGHT : PREF_COLOR_SCHEME_DAY,
                        P.darkThemeActive ? PREF_COLOR_SCHEME_NIGHT_D : PREF_COLOR_SCHEME_DAY_D);
        Properties p = loadColorScheme(path, context.getAssets());
        if (p == null)
            ErrorToast.show(R.string.pref__ThemeManager__error_loading_color_scheme, path);
        else
            ColorScheme.set(new ColorScheme(p));
    }

    static @NonNull LinkedList<ThemeInfo> enumerateThemes(@NonNull Context context) {
        LinkedList<ThemeInfo> themes = new LinkedList<>();
        AssetManager manager = context.getAssets();

        // load themes from assets
        try {
            String[] builtin_themes = manager.list("");
            if (builtin_themes != null) {
                for (String theme : builtin_themes) {
                    if (!theme.toLowerCase().endsWith("theme.properties"))
                        continue;
                    Properties p = loadColorScheme(theme, manager);
                    if (p != null)
                        themes.add(new ThemeInfo(p.getProperty("name", theme), theme));
                }
            }
        } catch (IOException e) {e.printStackTrace();}

        // load themes from disk
        for (String directory : getThemeSearchDirectories(context)) {
            File dir = new File(directory);
            if (!dir.exists()) continue;
            File[] files = dir.listFiles();
            if (files == null) continue;

            for (File file : files) {
                 if (!file.getName().toLowerCase().endsWith("theme.properties"))
                     continue;
                 Properties p = loadColorScheme(file.getAbsolutePath(), null);
                 if (p != null)
                     themes.add(new ThemeInfo(p.getProperty("name", file.getName()), file.getAbsolutePath()));
            }
        }
        return themes;
    }

    private static @Nullable Properties loadColorScheme(String path, AssetManager manager) {
        Properties p = new Properties();
        try {
            p.load(path.startsWith("/") ? new FileInputStream(path) : manager.open(path));
            return p;
        } catch (IOException e) {
            kitty.error("Failed to load file " + path, e);
            return null;
        }
    }

    static public class ThemeInfo implements Comparable<ThemeInfo> {
        public @NonNull String name;
        public @NonNull String path;

        ThemeInfo(@NonNull String name, @NonNull String path) {
            this.name = name;
            this.path = path;
        }

        @Override
        public int compareTo(@NonNull ThemeInfo another) {
            return this.name.compareTo(another.name);
        }
    }
}
