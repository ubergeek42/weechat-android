package android.support.v7.preference;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.widget.Toast;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.weechat.ColorScheme;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Properties;

import static com.ubergeek42.WeechatAndroid.utils.Constants.*;

public class ThemeManager {

    private final static Logger logger = LoggerFactory.getLogger("ThemeManager");
    public final static String SEARCH_DIR = Environment.getExternalStorageDirectory().toString() + "/weechat";

    public static void loadColorSchemeFromPreferences(@NonNull Context context) {
        String path = android.preference.PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PREF_COLOR_SCHEME, PREF_COLOR_SCHEME_D);
        Properties p = loadColorScheme(path, context.getAssets());
        if (p == null)
            Toast.makeText(context, context.getString(R.string.pref_theme_loading_error, path), Toast.LENGTH_SHORT).show();
        else
            ColorScheme.set(new ColorScheme(p));
    }

    public static @NonNull LinkedList<ThemeInfo> enumerateThemes(@NonNull Context context) {
        LinkedList<ThemeInfo> themes = new LinkedList<>();
        AssetManager manager = context.getAssets();

        // load themes from assets
        try {
            String[] builtin_themes = manager.list("");
            for (String theme : builtin_themes) {
                if (!theme.toLowerCase().endsWith("theme.properties"))
                    continue;
                Properties p = loadColorScheme(theme, manager);
                if (p != null)
                    themes.add(new ThemeInfo(p.getProperty("NAME", theme), theme));
            }
        } catch (IOException e) {e.printStackTrace();}

        // load themes from disk
        File dir = new File(SEARCH_DIR);
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!file.getName().toLowerCase().endsWith("theme.properties"))
                        continue;
                    Properties p = loadColorScheme(file.getAbsolutePath(), null);
                    if (p != null)
                        themes.add(new ThemeInfo(p.getProperty("NAME", file.getName()), file.getAbsolutePath()));
                }
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
            logger.error("Failed to load file " + path, e);
            return null;
        }
    }

    static public class ThemeInfo implements Comparable<ThemeInfo> {
        public @NonNull String name;
        public @NonNull String path;

        public ThemeInfo(@NonNull String name, @NonNull String path) {
            this.name = name;
            this.path = path;
        }

        @Override
        public int compareTo(@NonNull ThemeInfo another) {
            return this.name.compareTo(another.name);
        }
    }
}
