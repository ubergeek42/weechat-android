package com.ubergeek42.WeechatAndroid.upload

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream


// Suri stands for “share uri” which i hated for no solid reason
// it's also how Suri alpacas are called, and they are gorgeous; i've wanted to touch one for years
class Suri private constructor(
    var uri: Uri, var mediaType: MediaType?, var fileName: String, var fileSize: Long
) : java.io.Serializable, Parcelable {
    var httpUri: String? = null

    val ready get() = httpUri != null

    constructor(parcel: Parcel) : this(
        parcel.readParcelable(Uri::class.java.classLoader)!!,
        parcel.readString()!!.toMediaTypeOrNull(),
        parcel.readString()!!,
        parcel.readLong()
    ) {
        httpUri = parcel.readString()
    }

    @Throws(FileNotFoundException::class, IOException::class, SecurityException::class)
    fun getInputStream(): InputStream {
        return resolver.openInputStream(uri) ?: throw IOException("Null input stream for $uri")
    }

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<Suri> {
            override fun createFromParcel(parcel: Parcel): Suri {
                return Suri(parcel)
            }

            override fun newArray(size: Int): Array<Suri?> {
                return arrayOfNulls(size)
            }
        }

        @Throws(FileNotFoundException::class, IOException::class, SecurityException::class)
        fun fromUri(uri: Uri): Suri {
            val mediaType = resolver.getType(uri)?.toMediaTypeOrNull()
            val fileSize = resolver.openFileDescriptor(uri, "r")?.statSize ?: -1L
            val fileName = makeFileNameWithExtension(uri, mediaType)
            return Suri(uri, mediaType, fileName, fileSize)
        }

        // adapted from the following answer by Stefan Haustein
        // https://stackoverflow.com/a/25005243/1449683
        private fun getFileName(uri: Uri): String? {
            if (uri.scheme == "content") {
                resolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val name = cursor.getString(columnIndex)
                        if (name != null) return name
                    }
                }
            }
            return uri.lastPathSegment
        }

        fun makeFileNameWithExtension(uri: Uri, mediaType: MediaType?): String {
            val fileName = getFileName(uri)
            fileName?.let { if ("." in it) return it }

            val base = if (fileName.isNullOrEmpty()) fileName else "unknown"
            val extension = if (mediaType == null)
                "bin" else MimeTypeMap.getSingleton().getExtensionFromMimeType(mediaType.toString())
            return "$base.$extension"
        }
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelable(uri, flags)
        parcel.writeString(fileName)
        parcel.writeLong(fileSize)
        parcel.writeString(httpUri)
    }

    override fun describeContents(): Int {
        return 0
    }
}