// Copyright (C) 2011 George Yunaev @ Ulduzsoft
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package androidx.preference;

import android.graphics.Typeface;
import android.os.Environment;
import androidx.annotation.NonNull;

import java.io.File;
import java.util.LinkedList;

public class FontManager {
    final static public String[] FONT_DIRS = {Environment.getExternalStorageDirectory().toString() + "/weechat", "/system/fonts"};

    static public @NonNull LinkedList<FontInfo> enumerateFonts() {
        LinkedList<FontInfo> fonts = new LinkedList<>();

        for (String fontdir : FONT_DIRS) {
            File dir = new File(fontdir);
            if (!dir.exists()) continue;
            File[] files = dir.listFiles();
            if (files == null) continue;

            for (File file : files) {
                String fileName = file.getName().toLowerCase();
                if (fileName.endsWith(".ttf") || fileName.endsWith(".otf")) {
                    try {
                        Typeface typeface = Typeface.createFromFile(file.getAbsolutePath());
                        fonts.add(new FontInfo(file.getName(), file.getAbsolutePath(), typeface));
                    } catch (RuntimeException r) {
                        // Invalid font
                    }
                }
            }
        }
        return fonts;
    }

    static public class FontInfo implements Comparable<FontInfo> {
        public FontInfo(@NonNull String name, @NonNull String path, @NonNull Typeface typeface) {
            this.name = name;
            this.path = path;
            this.typeface = typeface;
        }

        public @NonNull String name;
        public @NonNull String path;
        public @NonNull Typeface typeface;

        @Override public int compareTo(@NonNull FontInfo another) {
            return this.name.compareTo(another.name);
        }
    }
}