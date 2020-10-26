package com.ubergeek42.WeechatAndroid.upload

import android.content.ComponentName
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.preference.PreferenceManager
import com.ubergeek42.WeechatAndroid.BuildConfig
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.utils.Constants.*


object Config {
    var uploadUri = PREF_UPLOAD_URI_D
    var uploadFormFieldName = PREF_UPLOAD_FORM_FIELD_NAME
    var httpUriGetter = HttpUriGetter.simple
    var requestModifiers = emptyList<RequestModifier>()
    var rememberUploadsFor = PREF_UPLOAD_REMEMBER_UPLOADS_FOR_D.hours_to_ms
    @JvmField var filePickerAction1: Targets = Targets.MediaStoreImages
    @JvmField var filePickerAction2: Targets? = Targets.Camera
}

fun initPreferences() {
    val p = PreferenceManager.getDefaultSharedPreferences(applicationContext)
    for (key in listOf(
            PREF_UPLOAD_ACCEPT,
            PREF_UPLOAD_URI,
            PREF_UPLOAD_FORM_FIELD_NAME,
            PREF_UPLOAD_REGEX,
            PREF_UPLOAD_ADDITIONAL_HEADERS,
            PREF_UPLOAD_AUTHENTICATION,
            PREF_UPLOAD_AUTHENTICATION_BASIC_USER,
            PREF_UPLOAD_AUTHENTICATION_BASIC_PASSWORD,
            PREF_UPLOAD_REMEMBER_UPLOADS_FOR,
            PREF_SHOW_PAPERCLIP_ACTION_1,
            PREF_SHOW_PAPERCLIP_ACTION_2,
    )) {
        onSharedPreferenceChanged(p, key)
    }
}

@Suppress("BooleanLiteralArgument")
fun onSharedPreferenceChanged(p: SharedPreferences, key: String) {
    when (key) {
        PREF_UPLOAD_ACCEPT -> {
            val (text, media, everything) = when (p.getString(key, PREF_UPLOAD_ACCEPT_D)) {
                PREF_UPLOAD_ACCEPT_TEXT_ONLY -> true to false to false
                PREF_UPLOAD_ACCEPT_TEXT_AND_MEDIA -> false to true to false
                PREF_UPLOAD_ACCEPT_EVERYTHING -> false to false to true
                else -> true to false to false
            }
            enableDisableComponent(ShareActivityAliases.TEXT_ONLY.alias, text)
            enableDisableComponent(ShareActivityAliases.TEXT_AND_MEDIA.alias, media)
            enableDisableComponent(ShareActivityAliases.EVERYTHING.alias, everything)
        }

        PREF_UPLOAD_URI -> {
            Config.uploadUri = p.getString(key, PREF_UPLOAD_URI_D)!!
        }

        PREF_UPLOAD_FORM_FIELD_NAME -> {
            Config.uploadFormFieldName = p.getString(key, PREF_UPLOAD_FORM_FIELD_NAME_D)!!
        }

        PREF_UPLOAD_REGEX -> {
            val regex = p.getString(key, PREF_UPLOAD_REGEX_D)!!
            Config.httpUriGetter = if (regex.isEmpty()) {
                HttpUriGetter.simple
            } else {
                HttpUriGetter.fromRegex(regex)
            }
        }

        PREF_UPLOAD_ADDITIONAL_HEADERS,
        PREF_UPLOAD_AUTHENTICATION,
        PREF_UPLOAD_AUTHENTICATION_BASIC_USER,
        PREF_UPLOAD_AUTHENTICATION_BASIC_PASSWORD -> {
            val requestModifiers = mutableListOf<RequestModifier>()

            val additionalHeaders = RequestModifier.additionalHeaders(
                    p.getString(PREF_UPLOAD_ADDITIONAL_HEADERS, PREF_UPLOAD_ADDITIONAL_HEADERS_D)!!)
            if (additionalHeaders != null)
                requestModifiers.add(additionalHeaders)

            if (p.getString(PREF_UPLOAD_AUTHENTICATION, PREF_UPLOAD_AUTHENTICATION_D) == PREF_UPLOAD_AUTHENTICATION_BASIC) {
                val user = p.getString(PREF_UPLOAD_AUTHENTICATION_BASIC_USER, PREF_UPLOAD_AUTHENTICATION_BASIC_USER_D)!!
                val password = p.getString(PREF_UPLOAD_AUTHENTICATION_BASIC_PASSWORD, PREF_UPLOAD_AUTHENTICATION_BASIC_PASSWORD_D)!!
                requestModifiers.add(RequestModifier.basicAuthentication(user, password))
            }

            Config.requestModifiers = requestModifiers
        }

        PREF_UPLOAD_REMEMBER_UPLOADS_FOR -> {
            suppress<NumberFormatException> {
                Config.rememberUploadsFor = p.getString(key, PREF_UPLOAD_REMEMBER_UPLOADS_FOR_D)!!.hours_to_ms
            }
        }

        PREF_SHOW_PAPERCLIP_ACTION_1 -> {
            val value = p.getString(key, PREF_SHOW_PAPERCLIP_ACTION_1_D)
            Config.filePickerAction1 = value?.toTarget() ?: Targets.ImagesAndVideos
        }

        PREF_SHOW_PAPERCLIP_ACTION_2 -> {
            val value = p.getString(key, PREF_SHOW_PAPERCLIP_ACTION_2_D)
            Config.filePickerAction2 = value?.toTarget()
        }
    }
}


fun validateUploadConfig() {
    if (Config.uploadUri.isBlank()) throw UploadConfigValidationError("Upload URL not set")
}

class UploadConfigValidationError(message: String) : Exception(message)


private fun String.toTarget(): Targets? {
    return when (this) {
        PREF_SHOW_PAPERCLIP_ACTION_NONE -> null
        PREF_SHOW_PAPERCLIP_ACTION_CONTENT_IMAGES -> Targets.Images
        PREF_SHOW_PAPERCLIP_ACTION_CONTENT_MEDIA -> Targets.ImagesAndVideos
        PREF_SHOW_PAPERCLIP_ACTION_CONTENT_ANYTHING -> Targets.Anything
        PREF_SHOW_PAPERCLIP_ACTION_MEDIASTORE_IMAGES -> Targets.MediaStoreImages
        PREF_SHOW_PAPERCLIP_ACTION_MEDIASTORE_MEDIA -> Targets.MediaStoreImagesAndVideos
        PREF_SHOW_PAPERCLIP_ACTION_MEDIASTORE_CAMERA -> Targets.Camera
        else -> null
    }
}

fun getUploadMenuTitleResId(target: Targets): Int {
    return when (target) {
        Targets.Images -> R.string.menu__upload_actions__content_images
        Targets.ImagesAndVideos -> R.string.menu__upload_actions__content_media
        Targets.Anything -> R.string.menu__upload_actions__content_anything
        Targets.MediaStoreImages -> R.string.menu__upload_actions__mediastore_images
        Targets.MediaStoreImagesAndVideos -> R.string.menu__upload_actions__mediastore_media
        Targets.Camera ->  R.string.menu__upload_actions__camera
    }
}

private enum class ShareActivityAliases(val alias: String) {
    TEXT_ONLY("ShareActivityText"),
    TEXT_AND_MEDIA("ShareActivityMedia"),
    EVERYTHING("ShareActivityEverything"),
}

private fun enableDisableComponent(name: String, enabled: Boolean) {
    val manager = applicationContext.packageManager
    val componentName = ComponentName(BuildConfig.APPLICATION_ID, "com.ubergeek42.WeechatAndroid.$name")
    val enabledDisabled = if (enabled)
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    manager.setComponentEnabledSetting(componentName, enabledDisabled, PackageManager.DONT_KILL_APP)
}

private val String.hours_to_ms get() = (this.toFloat() * 60 * 60 * 1000).toInt()