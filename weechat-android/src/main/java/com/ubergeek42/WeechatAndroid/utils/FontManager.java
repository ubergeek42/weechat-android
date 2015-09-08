package com.ubergeek42.WeechatAndroid.utils;
/*
 * Copyright (C) 2011 George Yunaev @ Ulduzsoft
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

import android.graphics.Typeface;
import android.os.Environment;

import java.io.File;
import java.util.HashMap;

public class FontManager
{
    static public String[] fontdirs = { Environment.getExternalStorageDirectory().toString() + "/weechat", "/system/fonts" };
    // This function enumerates all fonts on Android system and returns the HashMap with the font
    // absolute file name as key, and the font literal name (embedded into the font) as value.
    static public HashMap< String, String > enumerateFonts()
    {
        HashMap< String, String > fonts = new HashMap<>();

        for ( String fontdir : fontdirs )
        {
            File dir = new File( fontdir );

            if ( !dir.exists() )
                continue;

            File[] files = dir.listFiles();

            if ( files == null )
                continue;

            for ( File file : files )
            {
                // Only try ttf or otf files
                if (file.getName().toLowerCase().endsWith(".ttf") || file.getName().toLowerCase().endsWith(".otf")) {
                    // See if it is a valid typeface
                    try {
                        Typeface.createFromFile(file.getAbsolutePath());
                        fonts.put(file.getAbsolutePath(), file.getName());
                    } catch (RuntimeException r) {
                        // Invalid font
                    }
                }
            }
        }

        return fonts.isEmpty() ? null : fonts;
    }
}