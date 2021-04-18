// Copyright (C) 2011 George Yunaev @ Ulduzsoft
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
package androidx.preference

import android.content.Context
import android.graphics.Typeface
import com.ubergeek42.WeechatAndroid.upload.suppress
import java.io.File
import java.util.*

internal object FontManager {
    fun getFontSearchDirectories(context: Context): List<String> {
        val internalFontFolder = context.getExternalFilesDir(CUSTOM_FONTS_DIRECTORY)
        return if (internalFontFolder != null) {
            listOf("/system/fonts", internalFontFolder.toString())
        } else {
            listOf("/system/fonts")
        }
    }

    fun enumerateFonts(context: Context): List<FontInfo> {
        val fonts = mutableListOf<FontInfo>()

        getFontSearchDirectories(context).forEach { directoryName ->
            val directory = File(directoryName)
            if (directory.exists()) {
                directory.listFiles()?.forEach { file ->
                    suppress<Exception>(showToast = true) {
                        val typeface = Typeface.createFromFile(file.absolutePath)
                        fonts.add(FontInfo(file.name, file.absolutePath, typeface))
                    }
                }
            }
        }

        return fonts
    }
}

internal class FontInfo (
    var name: String,
    var path: String,
    var typeface: Typeface
)