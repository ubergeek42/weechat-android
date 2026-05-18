package androidx.preference

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.graphics.fonts.FontStyle.FONT_SLANT_ITALIC
import android.os.Build
import androidx.annotation.RequiresApi
import com.ubergeek42.WeechatAndroid.upload.Suri
import com.ubergeek42.WeechatAndroid.upload.resolver
import com.ubergeek42.WeechatAndroid.upload.suppress
import com.ubergeek42.WeechatAndroid.utils.getUris
import com.ubergeek42.WeechatAndroid.utils.saveUriToFile
import com.ubergeek42.WeechatAndroid.views.snackbar.showSnackbar
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.File


/**
 * For API < 29.
 * Returns null if file not found.
 * Otherwise, does not check if the file is a valid font.
 */
private fun File.createTypefaceOrNull0(): Typeface? {
    return try {
        Typeface.createFromFile(this)
    } catch (e: Exception) {
        println("Failed to load typeface from file ${this}: $e")
        null
    }
}


/**
 * For API ≥ 29.
 * Returns a list of added fonts, and the resulting typeface or null if no font files are valid.
 * In the case where several fonts have the same style (weight & slant), only one will be added.
 */
@RequiresApi(Build.VERSION_CODES.Q)
private fun Collection<File>.createFontFamilyOrNull29(): FontFamily? {
    val fonts = mapNotNull { file ->
            try {
                Font.Builder(file).build()
            } catch (e: Exception) {
                println("Failed to parse font from file $file: $e")
                null
            }
        }

    if (fonts.isEmpty()) return null

    val fontIterator = fonts.iterator()
    return FontFamily.Builder(fontIterator.next())
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
}


@RequiresApi(Build.VERSION_CODES.Q)
private fun FontFamily.createTypeface29() = Typeface.CustomFallbackBuilder(this).build()



fun List<File>.createTypefaceOrNull(): Typeface? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        createFontFamilyOrNull29()?.createTypeface29()
    } else {
        firstOrNull()?.createTypefaceOrNull0()
    }
}


////////////////////////////////////////////////////////////////////////////////////////////////////


/**
 * [fontFilePaths] can be empty if it's a default font.
 *
 * [fonts] *might* be empty below API 29. If present:
 *   * [fontFilePaths] correspond to [fonts], and
 *   * all fonts are actually available.
 * If not:
 *   * the path might not be a valid font.
 */
data class TypefaceInfo(
    val name: String,
    val fontFilePaths: List<String>,
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

    override fun toString(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "$name (${getStylesDescription().joinToString(", ")})"
        } else {
            name
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
    return files.mapNotNull { file ->
        val typeface = file.createTypefaceOrNull0() ?: return@mapNotNull null
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
                val fontFamily = files.createFontFamilyOrNull29()
                if (fontFamily != null) {
                    val fonts = fontFamily.fonts
                    val firstFile = fonts.first().file!!
                    TypefaceInfo(
                        name = firstFile.getFontFamilyOrNull() ?: firstFile.name,
                        fontFilePaths = fonts.map { it.file!!.absolutePath },
                        fonts = fonts,
                        typeface = fontFamily.createTypeface29()
                    )
                } else {
                    null
                }
            }
}


private fun List<File>.enumerateTypefaces(): List<TypefaceInfo> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        enumerateTypefaces29(this) // TODO ext
    } else {
        enumerateTypefaces0(this)
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

    return files.enumerateTypefaces()
}


private val FontFamily.fonts: List<Font>
    @RequiresApi(Build.VERSION_CODES.Q) get() = (0..<size).map { index -> getFont(index) }


////////////////////////////////////////////////////////////////////////////////////////////////////


fun importFontsFromResultIntent(context: Activity, intent: Intent?) {
    val exceptions = mutableListOf<Exception>()
    val importedFiles = mutableListOf<File>()

    val fontsFolder = context.getExternalFilesDir(CUSTOM_FONTS_DIRECTORY)

    intent?.getUris()?.forEach { uri ->
        try {
            val mediaType = resolver.getType(uri)?.toMediaTypeOrNull()
            val fileName = Suri.makeFileNameWithExtension(uri, mediaType)
            val outputFile = File(fontsFolder, fileName)

            try {
                context.saveUriToFile(uri, outputFile)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Font.Builder(outputFile).build() // validate the file, if possible
                }
            } catch (e: Exception) {
                suppress<Exception> { outputFile.delete() }
                throw e
            }

            importedFiles.add(outputFile)
        } catch (e: Exception) {
            exceptions.add(e)
        }
    }

    val importedTypefacesDescription = importedFiles.enumerateTypefaces().joinToString()

    when {
        importedFiles.isNotEmpty() && exceptions.isEmpty() -> {
            context.showSnackbar("Imported: $importedTypefacesDescription")
        }
        importedFiles.isNotEmpty() && exceptions.isNotEmpty() -> {
            context.showSnackbar("Imported: $importedTypefacesDescription. There were errors while importing some of the fonts", exceptions.combineIntoOne())
        }
        importedFiles.isEmpty() && exceptions.isNotEmpty() -> {
            context.showSnackbar("Failed to import fonts", exceptions.combineIntoOne())
        }
        else -> {
            context.showSnackbar("Failed to import fonts", Exception("Failed to import fonts from intent $intent"))
        }
    }
}


private fun List<Exception>.combineIntoOne() =
    if (size == 1) {
        first()
    } else {
        Exception("Could not import fonts").apply { forEach { addSuppressed(it) } }
    }