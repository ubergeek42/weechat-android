package android.support.v7.preference;

import android.content.res.AssetManager;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Properties;

public class ThemeManager {

    public final static String SEARCH_DIR = Environment.getExternalStorageDirectory().toString() + "/weechat";

    public static @NonNull LinkedList<ThemeInfo> enumerateThemes(AssetManager assetManager) {
        LinkedList<ThemeInfo> themes = new LinkedList<>();

        // load themes from assets
        try {
            String[] builtin_themes = assetManager.list("");
            for (String theme : builtin_themes) {
                if (!theme.toLowerCase().endsWith("theme.properties"))
                    continue;
                Properties p = new Properties();
                try {
                    p.load(assetManager.open(theme));
                } catch (IOException e) {
                    Log.w("ThemeManager", "Failed to load file from assets " + theme);
                    continue;
                }
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
                    Properties p = new Properties();
                    try {
                        p.load(new FileInputStream(file));
                    } catch (IOException e) {
                        Log.w("ThemeManager", "Failed to load file " + file.getAbsolutePath());
                        continue;
                    }
                    themes.add(new ThemeInfo(p.getProperty("NAME", file.getName()), file.getAbsolutePath()));
                }
            }
        }
        return themes;
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
