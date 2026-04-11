package androidx.preference

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.graphics.fonts.FontStyle.FONT_SLANT_ITALIC
import android.graphics.fonts.FontStyle.FONT_SLANT_UPRIGHT
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.File


private fun List<File>.createTypeface0(): Typeface? {
    return Typeface.createFromFile(first())
}


@RequiresApi(Build.VERSION_CODES.Q)
private fun List<File>.createTypeface29(): Pair<List<Font>, Typeface?> {
    val fonts = sorted().mapNotNull { file ->
            try {
                Font.Builder(file).build()
            } catch (e: Exception) {
                println("Failed to parse font from file $file: $e")
                null
            }
        }

    if (fonts.isEmpty()) return emptyList<Font>() to null

    val addedFonts = mutableListOf<Font>()

    val fontIterator = fonts.iterator()
    val firstFont = fontIterator.next()
    addedFonts.add(firstFont)
    val family: FontFamily = FontFamily.Builder(firstFont)
            .apply {
                fontIterator.forEach { font ->
                    try {
                        addFont(font)
                        addedFonts.add(font)
                    } catch (e: Exception) {
                        println("Failed to add font $font to family: $e")
                    }
                }
            }
            .build()

    return addedFonts to Typeface.CustomFallbackBuilder(family).build()
}


fun List<File>.createTypefaceOrNull(): Typeface? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        createTypeface29().second
    } else {
        createTypeface0()
    }
}


////////////////////////////////////////////////////////////////////////////////////////////////////


/**
 * [filePaths] can be empty if it's a default font.
 *
 * [fonts] *might* be empty below API 29. If present:
 *   * [filePaths] correspond to [fonts], and
 *   * all fonts are actually available.
 * If not:
 *   * the path might not be a valid font.
 */
data class TypefaceInfo(
    val familyName: String,
    val filePaths: List<String>,
    val fonts: List<Font>,
    val typeface: Typeface
) {
    @RequiresApi(Build.VERSION_CODES.Q)
    fun getStylesDescription(): List<String> {
        return fonts.map {
                val weight = it.style.weight
                val italic = if (it.style.slant == FONT_SLANT_ITALIC) "i" else ""
                "$weight$italic"
            }
    }

    companion object {
        fun getDefault(name: String) = TypefaceInfo(name, emptyList(), emptyList(), Typeface.MONOSPACE)
    }
}


private fun getFontSearchDirectories(context: Context): List<File> {
    val internalFontFolder = context.getExternalFilesDir(CUSTOM_FONTS_DIRECTORY)
    return if (internalFontFolder != null) {
        listOf(File("/system/fonts"), internalFontFolder)
    } else {
        listOf(File("/system/fonts"))
    }
}


private fun enumerateTypefaces0(files: List<File>): List<TypefaceInfo> {
    return files.map { file ->
        val typeface = Typeface.createFromFile(file)
        TypefaceInfo(file.name, listOf(file.absolutePath), emptyList(), typeface)
    }
}


@RequiresApi(Build.VERSION_CODES.Q)
private fun enumerateTypefaces29(files: List<File>): List<TypefaceInfo> {
    val firstWordRegex = "^\\w+".toRegex()

    // /path/to/file-regular.ttf -> file
    fun File.getFontFamilyOrNull() = firstWordRegex.find(name)?.groupValues?.firstOrNull()

    // /path/to/file-regular.ttf -> /path/to/file
    fun File.getFontFamilyGroupKey() = (parent ?: "") + (getFontFamilyOrNull() ?: name)

    return files
            .groupBy { file -> file.getFontFamilyGroupKey() }
            .mapNotNull { (_, files) ->
                val (addedFonts, typeface) = files.createTypeface29()
                if (typeface != null) {
                    val firstFile = addedFonts.first().file!!
                    val name = firstFile.getFontFamilyOrNull() ?: firstFile.name
                    TypefaceInfo(name, addedFonts.map { it.file!!.absolutePath }, addedFonts, typeface)
                } else {
                    null
                }
            }
}


fun enumerateTypefaces(context: Context): List<TypefaceInfo> {
    val directories = getFontSearchDirectories(context)

    val files = directories
            .flatMap { directory ->
                try {
                    directory.listFiles()?.asIterable() ?: emptyList()
                } catch (e: Exception) {
                    println("Could not list files in directory $directory: $e")
                    emptyList()
                }
            }

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        enumerateTypefaces29(files)
    } else {
        enumerateTypefaces0(files)
    }
}
