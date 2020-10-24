package com.ubergeek42.WeechatAndroid.upload

import android.content.Intent
import android.content.Intent.EXTRA_ALLOW_MULTIPLE
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.ubergeek42.WeechatAndroid.media.ContentUriFetcher.FILE_PROVIDER_SUFFIX
import java.io.File
import java.io.IOException
import java.lang.IllegalArgumentException
import java.text.SimpleDateFormat
import java.util.*


private val context = applicationContext


private interface Target {
    val intent: Intent
    val requestCode: Int
}


// the intent to pick images only, as opposed to images and videos, is much more user friendly
enum class Targets(override val requestCode: Int) : Target {
    Anything(1010) {
        override val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                  type = "*/*"
                                  addCategory(Intent.CATEGORY_OPENABLE)
                                  putExtra(EXTRA_ALLOW_MULTIPLE, true)
                              }
    },
    Images(1009) {
        override val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                  type = "images/*"
                                  addCategory(Intent.CATEGORY_OPENABLE)
                                  putExtra(EXTRA_ALLOW_MULTIPLE, true)
                              }
    },
    ImagesAndVideos(1008) {
        override val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                  type = "*/*"
                                  addCategory(Intent.CATEGORY_OPENABLE)
                                  putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                                  putExtra(EXTRA_ALLOW_MULTIPLE, true)
                              }
    },
    Camera(1007) {
        override val intent get() = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                                        putExtra(MediaStore.EXTRA_OUTPUT, createTemporaryFile())
                                    }
    },
}


fun chooseFiles(fragment: Fragment, longClick: Boolean) {
    val target = if (longClick) Targets.Camera else Targets.ImagesAndVideos
    fragment.startActivityForResult(target.intent, target.requestCode)
}


fun getShareObjectFromIntent(requestCode: Int, intent: Intent?): ShareObject {
    when (requestCode) {
        Targets.Anything.requestCode,
        Targets.ImagesAndVideos.requestCode -> {

            // multiple files selected
            intent!!.clipData?.let { clipData ->
                val uris = (0 until clipData.itemCount).map { clipData.getItemAt(it).uri }
                return UrisShareObject.fromUris(uris)
            }

            // a single file selected
            intent.data!!.let {
                return UrisShareObject.fromUris(listOf(it))
            }
        }

        Targets.Camera.requestCode -> {
            val uri = Uri.fromFile(currentPhotoPath)

            // the bitmap that the intent contains is supposed to show a smaller image
            // suitable for displaying to the user. in practice, however, the intent is null.
            // in both cases the url that we provided to the picker will point at the full image
            // see https://stackoverflow.com/a/12564910/1449683
            intent?.extras?.get("data")?.also {
                return UrisShareObject.fromCamera(it as Bitmap, uri)
            }

            return UrisShareObject.fromUris(listOf(uri))
        }

        else -> throw IllegalArgumentException("Unknown request code: $requestCode")
    }
}


// we must provide a writeable path for the camera to write to. it can be:
// * our private dir: getExternalFilesDir() -- no permissions required
// * external dir : getExternalStoragePublicDirectory() -- permissions required
// * external dir MediaStore.Images table -- no permissions required, api 29+

// this pass is not returned back to us, so we simply save it here
lateinit var currentPhotoPath: File

@Throws(IOException::class)
private fun createTemporaryFile(): Uri {
    val timestamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val directory: File = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: throw IOException("Storage directory not available")

    currentPhotoPath = File.createTempFile("WeechatAndroid_${timestamp}_", ".jpeg", directory)

    return FileProvider.getUriForFile(context,
            context.packageName + FILE_PROVIDER_SUFFIX,
            currentPhotoPath)
}
