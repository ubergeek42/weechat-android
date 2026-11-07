package com.ubergeek42.WeechatAndroid.upload

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.annotation.TargetApi
import android.content.ContentValues
import android.content.Intent
import android.content.Intent.EXTRA_ALLOW_MULTIPLE
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.WeechatActivity
import com.ubergeek42.WeechatAndroid.dialogs.ScrollableDialog
import com.ubergeek42.WeechatAndroid.media.ContentUriFetcher.FILE_PROVIDER_SUFFIX
import com.ubergeek42.WeechatAndroid.utils.applicationContext
import com.ubergeek42.WeechatAndroid.utils.Constants
import com.ubergeek42.WeechatAndroid.views.snackbar.showSnackbar
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@Root private val kitty: Kitty = Kitty.make()


private val context = applicationContext


private const val DEFAULT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or
        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION


private interface TargetInfo {
    val intent: Intent
}


enum class Target(
    val requestCode: Int,
    val menuItemResId: Int,
    val preferenceValue: String,
) : TargetInfo {
    // these three open files using the default file manager
    // the intent to pick images only, as opposed to images and videos, is a bit more cute
    ContentImages(1091, R.string.menu__upload_actions__content_images,
                        Constants.PREF_PAPERCLIP_ACTION_CONTENT_IMAGES) {
        override val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                  type = "image/*"
                                  flags = DEFAULT_FLAGS
                                  addCategory(Intent.CATEGORY_OPENABLE)
                                  putExtra(EXTRA_ALLOW_MULTIPLE, true)
                              }
    },
    ContentMedia(1093, R.string.menu__upload_actions__content_media,
                       Constants.PREF_PAPERCLIP_ACTION_CONTENT_MEDIA) {
        override val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                  type = "*/*"
                                  flags = DEFAULT_FLAGS
                                  addCategory(Intent.CATEGORY_OPENABLE)
                                  putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                                  putExtra(EXTRA_ALLOW_MULTIPLE, true)
                              }
    },
    ContentAnything(1097, R.string.menu__upload_actions__content_anything,
                          Constants.PREF_PAPERCLIP_ACTION_CONTENT_ANYTHING) {
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
    MediaStoreImages(1080, R.string.menu__upload_actions__mediastore_images,
                           Constants.PREF_PAPERCLIP_ACTION_MEDIASTORE_IMAGES) {
        override val intent = Intent(Intent.ACTION_PICK,
                                     MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                                  flags = DEFAULT_FLAGS
                                  putExtra(EXTRA_ALLOW_MULTIPLE, true)
                              }
    },

    /**
     * A Gallery app normally has an `<intent-filter>` with the action `PICK`
     * and one or more supported MIME types. To request multiple types of media from such an app,
     * we want to have the intent `type` of `"*\*"` and the extra `Intent.EXTRA_MIME_TYPES`
     * with the array of the actual MIME types that we want.
     *
     * Note, this behavior might be *not* standard for `PICK`.
     * However, it is mentioned in a note on `OPEN_DOCUMENT`, which says,¹
     *
     *   > Your intent **must** specify a MIME type and must declare the `CATEGORY_OPENABLE` category.
     *   > If appropriate, you can specify more than one MIME type
     *   > by adding an array of MIME types with the `EXTRA_MIME_TYPES` extra—
     *   > if you do so, you must set the primary MIME type in `setType()` to `"*\*"`.
     *
     * Now, Android 17 (API 37) introduced a new intent, `PICK_CONTACTS`,
     * which opens the new privacy-focused `ContactsPickerActivity`. Also,²
     *
     *   > For apps targeting Android 17 (API level 37) and higher, the system automatically upgrades
     *   > the existing `Intent.ACTION_PICK` intent to use the new Contact Picker interface.
     *
     * What this really means is that this activity implements³ an `<intent-filter>`
     * with the action `PICK`, *and* a higher `android:priority`, which works like this:⁴
     *
     *   > When an intent can be handled by multiple activities with different priorities,
     *   > Android considers only those with higher priority values as potential targets for the intent.
     *
     * The end result is that on Android 17 `ContactsPickerActivity` hijacks all `PICK` intents
     * with the type `"*\*"`, as this type matches all MIME types.
     *
     * I couldn't think of a pretty workaround for this. Some of the options are:
     *
     *   * Have a `type` with multiple MIME types, separated by either a space, `|` or `,`.
     *     This is **highly** non-standard. However, it seems to work in some apps,
     *     such as the Samsung gallery. Other apps, such as Photos, seem to be processing
     *     only the first item in `type`, and ignoring `EXTRA_MIME_TYPES`.
     *
     *   * Create a chooser that excludes the contact picker.
     *     While it works, the result of this choice is not remembered by the system—
     *     there are no "Just once" or "Always" buttons.
     *     We *could* remember the choice with the 3-argument `createChooser`,
     *     but that requires a broadcast receiver, and a way of forgetting user's choice.
     *
     *       ```kt
     *       Intent.createChooser(intent, null).apply {
     *           val contactsComponent = ComponentName(
     *               "com.android.contactspicker",
     *               "com.android.contactspicker.ContactsPickerActivity"
     *           )
     *           putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(contactsComponent))
     *       }
     *       ```
     *
     *   * Check what app is used to only pick images, and use that.
     *     We can do that by calling `packageManager.resolveActivity(imageIntent, ...)`.
     *     However, this only works out of the box if the user has either a single app
     *     that can handle the intent, which is not a realistic scenario,
     *     or has made a choice to "Always" open a specific app.
     *     If not, the result of that call will be `com.android.internal.app.ResolverActivity`.
     *     Also, to get a meaningful result out of the `resolveActivity` call,
     *     we'd need a `<queries>` block in the manifest.
     *
     * Even if not effective in all cases, the first of the three options seems to be a safe choice.
     * As `ContentMedia` is a viable alternative and the recommended way to pick mixed media,
     * I feel like it's not worth it to implement further fixes.
     *
     * ¹ https://developer.android.com/guide/components/intents-common#OpenFile
     * ² https://developer.android.com/about/versions/17/features/contact-picker#backward-compatibility
     * ³ https://android.googlesource.com/platform/packages/apps/ContactsPicker/+/refs/heads/android17-release/AndroidManifest.xml
     * ⁴ https://developer.android.com/guide/topics/manifest/intent-filter-element
     */
    MediaStoreMedia(1083, R.string.menu__upload_actions__mediastore_media,
                          Constants.PREF_PAPERCLIP_ACTION_MEDIASTORE_MEDIA) {
        override val intent = Intent(Intent.ACTION_PICK).apply {
                                  type = if (Build.VERSION.SDK_INT >= 37) "image/*|video/*" else "*/*"
                                  flags = DEFAULT_FLAGS
                                  putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                                  putExtra(EXTRA_ALLOW_MULTIPLE, true)
                              }
    },

    Camera(1060, R.string.menu__upload_actions__camera,
                 Constants.PREF_PAPERCLIP_ACTION_CAMERA) {
        override val intent get(): Intent {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                createMediaStoreFile() else
                createFileInPublicExternalDirectory()

            return Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, uri)
            }
        }
    };

    companion object {
        fun fromPreferenceValue(string: String?): Target? {
            values().forEach { if (string == it.preferenceValue) return it }
            return null
        }
    }
}


@Suppress("DEPRECATION") // startActivityForResult, requestPermissions
fun chooseFiles(fragment: Fragment, target: Target) {
    try {
        validateUploadConfig()
        fragment.startActivityForResult(target.intent, target.requestCode)
    } catch (e: WritePermissionRequiredError) {
        val activity = fragment.activity as WeechatActivity
        val dialog = ScrollableDialog.buildWritePermissionRequiredDialog(activity) { _, _ ->
            fragment.requestPermissions(arrayOf(WRITE_EXTERNAL_STORAGE),
                    WRITE_PERMISSION_REQUEST_FOR_CAMERA)
        }
        dialog.show(activity.supportFragmentManager, "camera-write-permission-dialog")
    } catch (e: Exception) {
        kitty.warn("Error while starting file picker activity", e)
        fragment.showSnackbar(e)
    }
}


fun getShareObjectFromIntent(requestCode: Int, intent: Intent?): ShareObject? {
    when (requestCode) {
        Target.ContentImages.requestCode,
        Target.ContentMedia.requestCode,
        Target.ContentAnything.requestCode,
        Target.MediaStoreImages.requestCode,
        Target.MediaStoreMedia.requestCode -> {
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

        Target.Camera.requestCode -> {
            // the bitmap that the intent contains is supposed to show a smaller image
            // suitable for displaying to the user. in practice, however, the intent is null.
            // in both cases the url that we provided to the picker will point at the full image
            // see https://stackoverflow.com/a/12564910/1449683
            return UrisShareObject.fromUris(listOf(currentPhotoUri))
        }

        else -> return null
    }
}


////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////// files
////////////////////////////////////////////////////////////////////////////////////////////////////

// we must provide a writeable path for the camera to write to. it can be:
// * our private dir: getExternalFilesDir() -- no permissions required
// * external dir : getExternalStoragePublicDirectory() -- permissions required
// * external dir MediaStore.Images table -- no permissions required on api 29+.
//   also on api < 29 we can't put files in a folder of our choosing
//   and i'm not sure if we can pick the name

// the uri is not returned back to us, so we simply save it here
lateinit var currentPhotoUri: Uri


private fun createFile(directory: File): Uri {
    val file = File.createTempFile("IMG_${timestamp()}_", ".jpeg", directory)
    currentPhotoUri = Uri.fromFile(file)
    return FileProvider.getUriForFile(context, context.packageName + FILE_PROVIDER_SUFFIX, file)
}


private fun createFileInAppSpecificExternalDirectory(): Uri {
    val directory = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: throw IOException("App-specific storage directory not available")
    return createFile(directory)
}


@Suppress("DEPRECATION")
@TargetApi(28)
private fun createFileInPublicExternalDirectory(): Uri {
    if (checkSelfPermission(context, WRITE_EXTERNAL_STORAGE) != PERMISSION_GRANTED)
            throw WritePermissionRequiredError()
    val rootDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            ?: throw IOException("Public storage directory not available")
    val appDirectory = File(rootDirectory, APP_FOLDER)
    if (!appDirectory.exists()) appDirectory.mkdirs()
    return createFile(appDirectory)
}


private fun createMediaStoreFile(): Uri {
    val contentValues = ContentValues()
    contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_${timestamp()}.jpeg")
    contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + File.separator + APP_FOLDER)
    }

    return (resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw IOException("Content provider returned null")).also {
        currentPhotoUri = it
    }
}


const val WRITE_PERMISSION_REQUEST_FOR_CAMERA = 1060
private const val APP_FOLDER = "Weechat-Android"

private fun timestamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

private class WritePermissionRequiredError : Exception("Write permission required")