package com.ubergeek42.WeechatAndroid.upload

import android.content.ContentValues
import android.content.Intent
import android.content.Intent.EXTRA_ALLOW_MULTIPLE
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.ubergeek42.WeechatAndroid.media.ContentUriFetcher.FILE_PROVIDER_SUFFIX
import com.ubergeek42.WeechatAndroid.utils.Toaster.Companion.ErrorToast
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


private val context = applicationContext


private const val DEFAULT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or
        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION


private interface Target {
    val intent: Intent
    val requestCode: Int
}


enum class Targets(override val requestCode: Int) : Target {
    // these three open files using the default file manager
    // the intent to pick images only, as opposed to images and videos, is a bit more cute
    Images(1091) {
        override val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                  type = "image/*"
                                  flags = DEFAULT_FLAGS
                                  addCategory(Intent.CATEGORY_OPENABLE)
                                  putExtra(EXTRA_ALLOW_MULTIPLE, true)
                              }
    },
    ImagesAndVideos(1093) {
        override val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                  type = "*/*"
                                  flags = DEFAULT_FLAGS
                                  addCategory(Intent.CATEGORY_OPENABLE)
                                  putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                                  putExtra(EXTRA_ALLOW_MULTIPLE, true)
                              }
    },
    Anything(1097) {
        override val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                  type = "*/*"
                                  flags = DEFAULT_FLAGS
                                  addCategory(Intent.CATEGORY_OPENABLE)
                                  putExtra(EXTRA_ALLOW_MULTIPLE, true)
                              }
    },

    // these two will offer to pick images using apps such as Photos or Simple Gallery.
    // the images & videos intent doesn't work with Simple Gallery;
    // while the app opens, it doesn't offer to actually pick anything
    MediaStoreImages(1080) {
        override val intent = Intent(Intent.ACTION_PICK,
                                     MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                                  flags = DEFAULT_FLAGS
                                  putExtra(EXTRA_ALLOW_MULTIPLE, true)
                              }
    },
    MediaStoreImagesAndVideos(1083) {
        override val intent = Intent(Intent.ACTION_PICK,
                                     MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                                  type = "*/*"
                                  flags = DEFAULT_FLAGS
                                  putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                                  putExtra(EXTRA_ALLOW_MULTIPLE, true)
                              }
    },

    Camera(1060) {
        override val intent get(): Intent {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                createMediaStoreFile() else createExternalFile()

            return Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, uri)
            }
        }
    },
}


fun chooseFiles(fragment: Fragment, longClick: Boolean) {
    val target = if (longClick) Targets.Camera else Targets.MediaStoreImagesAndVideos
    try {
        fragment.startActivityForResult(target.intent, target.requestCode)
    } catch (e: Exception) {
        ErrorToast.show(e)
    }
}


fun getShareObjectFromIntent(requestCode: Int, intent: Intent?): ShareObject {
    when (requestCode) {
        Targets.Images.requestCode,
        Targets.ImagesAndVideos.requestCode,
        Targets.Anything.requestCode,
        Targets.MediaStoreImages.requestCode,
        Targets.MediaStoreImagesAndVideos.requestCode -> {
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
            // the bitmap that the intent contains is supposed to show a smaller image
            // suitable for displaying to the user. in practice, however, the intent is null.
            // in both cases the url that we provided to the picker will point at the full image
            // see https://stackoverflow.com/a/12564910/1449683
            intent?.extras?.get("data")?.also {
                return UrisShareObject.fromCamera(it as Bitmap, currentPhotoUri)
            }

            return UrisShareObject.fromUris(listOf(currentPhotoUri))
        }

        else -> throw IllegalArgumentException("Unknown request code: $requestCode")
    }
}


// we must provide a writeable path for the camera to write to. it can be:
// * our private dir: getExternalFilesDir() -- no permissions required
// * external dir : getExternalStoragePublicDirectory() -- permissions required
// * external dir MediaStore.Images table -- no permissions required on api 29+.
//   also on api < 29 we can't put files in a folder of our choosing
//   and i'm not sure if we can pick the name

// the uri is not returned back to us, so we simply save it here
lateinit var currentPhotoUri: Uri


private fun createExternalFile(): Uri {
    val directory: File = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: throw IOException("Storage directory not available")
    val file = File.createTempFile("IMG_${timestamp()}_", ".jpeg", directory)
    currentPhotoUri = Uri.fromFile(file)
    return FileProvider.getUriForFile(context, context.packageName + FILE_PROVIDER_SUFFIX, file)
}


private fun createMediaStoreFile(): Uri {
    val contentValues = ContentValues()
    contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_${timestamp()}.jpeg")
    contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES +  "/WeechatAndroid")
    }

    return (resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw IOException("Content provider returned null")).also {
        currentPhotoUri = it
    }
}

private fun timestamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
