package androidx.preference

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.File


private fun List<File>.createTypeface0(): Typeface? {
    return Typeface.createFromFile(first())
}


@RequiresApi(Build.VERSION_CODES.Q)
private fun List<File>.createTypefaceOrNull29(): Typeface? {
    val fonts = sorted().mapNotNull { file ->
        try {
            Font.Builder(file).build()
        } catch (e: Exception) {
            println("Failed to parse font from file $file: $e")
            null
        }
    }

    if (fonts.isEmpty()) return null

    val fontIterator = fonts.iterator()
    val family: FontFamily = FontFamily.Builder(fontIterator.next())
            .apply {
                fontIterator.forEach { font ->
                    try {
                        addFont(font)
                    } catch (e: Exception) {
                        println("Failed to add font $font to family: $e")
                    }
                }
            }
            .build()

    return Typeface.CustomFallbackBuilder(family).build()
}


fun List<File>.createTypefaceOrNull(): Typeface? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        createTypefaceOrNull29()
    } else {
        createTypeface0()
    }
}


////////////////////////////////////////////////////////////////////////////////////////////////////


data class TypefaceInfo(
    val familyName: String,
    val filePaths: List<String>,
    val typeface: Typeface,
)


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
        TypefaceInfo(file.name, listOf(file.absolutePath), typeface)
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
                val typeface = files.createTypefaceOrNull29()
                if (typeface != null) {
                    val name = files.first().getFontFamilyOrNull() ?: files.first().name
                    TypefaceInfo(name, files.map { it.absolutePath }, typeface)
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
