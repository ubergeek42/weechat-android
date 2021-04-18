// Copyright (C) 2011 George Yunaev @ Ulduzsoft
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
package androidx.preference

import android.content.Context
import android.graphics.Typeface
import java.io.File
import java.util.*

internal object FontManager {
    fun getFontSearchDirectories(context: Context): List<String> {
        val out: MutableList<String> = ArrayList(listOf("/system/fonts"))
        val appSpecificFontFolder = context.getExternalFilesDir(CUSTOM_FONTS_DIRECTORY)
        if (appSpecificFontFolder != null) out.add(appSpecificFontFolder.toString())
        return out
    }

    fun enumerateFonts(context: Context): LinkedList<FontInfo> {
        val fonts = LinkedList<FontInfo>()
        for (directory in getFontSearchDirectories(context)) {
            val dir = File(directory)
            if (!dir.exists()) continue
            val files = dir.listFiles() ?: continue
            for (file in files) {
                val fileName = file.name.toLowerCase()
                if (fileName.endsWith(".ttf") || fileName.endsWith(".otf")) {
                    try {
                        val typeface = Typeface.createFromFile(file.absolutePath)
                        fonts.add(FontInfo(file.name, file.absolutePath, typeface))
                    } catch (r: RuntimeException) {
                        // Invalid font
                    }
                }
            }
        }
        return fonts
    }

    class FontInfo internal constructor(var name: String, var path: String, var typeface: Typeface) : Comparable<FontInfo> {
        override fun compareTo(another: FontInfo): Int {
            return name.compareTo(another.name)
        }
    }
}