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
import com.ubergeek42.WeechatAndroid.R
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
 * Returns null if the file is not found.
 * Otherwise, does not check if the file is a valid font.
 */
private fun File.createTypefaceOrNull0(): Typeface? {
    return try {
        Typeface.createFromFile(this)
    } catch (e: Exception) {
        println("Failed to load typeface from file $this: $e")
        null
    }
}


/**
 * For API ≥ 29.
 * Returns null if no font files are valid.
 * In the case where several fonts have the same style (weight & slant), only one will be added.
 * Does NOT check whether the files actually belong to the same family.
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


// TODO determine if we can, or should, have some actual fallbacks here.
//   These may be available with Typeface.sSystemFontMap ("monospace", "fantasy"), however,
//   given a user-chosen font family, it may be hard to tell which one would be a good fit.
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


interface TypefaceInfo {
    val name: String
    val typeface: Typeface
    val fontFilePaths: Set<String> // Empty if it's a default font.
    fun getDescription(): String

    companion object {
        fun getDefault(context: Context) =
            TypefaceInfo0(context.getString(R.string.pref__FontPreference__default), Typeface.MONOSPACE, emptySet())
    }
}


data class TypefaceInfo0(
    override val name: String,
    override val typeface: Typeface,
    override val fontFilePaths: Set<String>,
) : TypefaceInfo {
    override fun getDescription() = name
}


@RequiresApi(Build.VERSION_CODES.Q)
data class TypefaceInfo29(
    override val name: String,
    override val typeface: Typeface,
    override val fontFilePaths: Set<String>,
    private val fonts: List<Font>,
) : TypefaceInfo {
    override fun getDescription(): String {
        val styles = fonts.map { font ->
            val weight = font.style.weight
            val italic = font.style.slant == FONT_SLANT_ITALIC
            if (italic) "${weight}i" else "$weight"
        }

        return "$name (${styles.joinToString(", ")})"
    }
}

/**
 * For API < 29.
 * Returns a separate TypefaceInfo for every existing file.
 */
private fun enumerateTypefaces0(files: List<File>): List<TypefaceInfo> {
    return files.mapNotNull { file ->
        val typeface = file.createTypefaceOrNull0() ?: return@mapNotNull null
        TypefaceInfo0(file.name, typeface, setOf(file.absolutePath))
    }
}


/**
 * For API ≥ 29.
 * Tries to group files by location and then by font family name,
 * which is stupidly derived from the first word of the file name.
 * This should work for the fonts that follow the usual naming convention, e.g.
 *
 *     CascadiaMono-Regular.ttf
 *     CascadiaMono-Bold.ttf
 *     CascadiaMono-BoldItalic.ttf
 *     ...
 *
 * While it is possible to get the family from the file metadata,
 * Android doesn't provide a ready way to do so and, and doing it manually would be too complex.
 *
 * TODO See if it's feasible to enumerate the system fonts better,
 *   e.g. using Typeface.systemFontFamilyName
 */
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
                files.createFontFamilyOrNull29()?.let { fontFamily ->
                    val fonts = fontFamily.fonts
                    val firstFile = fonts.first().file!!
                    TypefaceInfo29(
                        name = firstFile.getFontFamilyOrNull() ?: firstFile.name,
                        typeface = fontFamily.createTypeface29(),
                        fontFilePaths = fonts.mapTo(mutableSetOf()) { it.file!!.absolutePath },
                        fonts = fonts,
                    )
                }
            }
}


fun enumerateTypefaces(files: List<File>): List<TypefaceInfo> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        enumerateTypefaces29(files)
    } else {
        enumerateTypefaces0(files)
    }
}


fun getFontSearchDirectories(context: Context): List<File> {
    val internalFontFolder = context.getExternalFilesDir(CUSTOM_FONTS_DIRECTORY)
    return if (internalFontFolder != null) {
        listOf(File("/system/fonts"), internalFontFolder)
    } else {
        listOf(File("/system/fonts"))
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

    return enumerateTypefaces(files)
}


private val FontFamily.fonts: List<Font>
    @RequiresApi(Build.VERSION_CODES.Q) get() = (0..<size).map { index -> getFont(index) }


////////////////////////////////////////////////////////////////////////////////////////////////////


fun Activity.requestFontImport() {
    val importFontsIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
        type = "font/*"
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        addCategory(Intent.CATEGORY_OPENABLE)
        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
    }

    startActivityForResult(importFontsIntent, IMPORT_FONTS_REQUEST_CODE, null)
}


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

    val importedTypefacesDescription = enumerateTypefaces(importedFiles).joinToString { it.getDescription() }

    @Suppress("KotlinConstantConditions")
    when {
        importedFiles.isNotEmpty() && exceptions.isEmpty() ->
            context.showSnackbar("Imported: $importedTypefacesDescription")
        importedFiles.isNotEmpty() && exceptions.isNotEmpty() ->
            context.showSnackbar("Imported: $importedTypefacesDescription. There were errors while importing some of the fonts", exceptions.combineIntoOne())
        importedFiles.isEmpty() && exceptions.isNotEmpty() ->
            context.showSnackbar("Failed to import fonts", exceptions.combineIntoOne())
        else ->
            context.showSnackbar("Failed to import fonts", Exception("Failed to import fonts from intent $intent"))
    }
}


const val CUSTOM_FONTS_DIRECTORY = "fonts"
const val IMPORT_FONTS_REQUEST_CODE = 1234


private fun List<Exception>.combineIntoOne() =
    if (size == 1) {
        first()
    } else {
        Exception("Could not import fonts").apply { forEach { addSuppressed(it) } }
    }