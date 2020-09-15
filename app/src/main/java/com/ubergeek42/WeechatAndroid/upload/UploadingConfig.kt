package com.ubergeek42.WeechatAndroid.upload

import android.content.ComponentName
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.preference.PreferenceManager
import com.ubergeek42.WeechatAndroid.BuildConfig
import com.ubergeek42.WeechatAndroid.utils.Constants.*


object Config {
    var uploadUri = PREF_UPLOADING_URI_D
    var uploadFormFieldName = PREF_UPLOADING_FORM_FIELD_NAME
    var httpUriGetter = HttpUriGetter.simple
    var requestModifiers = emptyList<RequestModifier>()
}

fun initPreferences() {
    val p = PreferenceManager.getDefaultSharedPreferences(applicationContext)
    for (key in listOf(PREF_UPLOADING_ENABLED,
            PREF_UPLOADING_URI,
            PREF_UPLOADING_FORM_FIELD_NAME,
            PREF_UPLOADING_REGEX,
            PREF_UPLOADING_ADDITIONAL_HEADERS,
            PREF_UPLOADING_AUTHENTICATION,
            PREF_UPLOADING_AUTHENTICATION_BASIC_USER,
            PREF_UPLOADING_AUTHENTICATION_BASIC_PASSWORD
    )) {
        onSharedPreferenceChanged(p, key)
    }
}

@Suppress("BooleanLiteralArgument")
fun onSharedPreferenceChanged(p: SharedPreferences, key: String) {
    when (key) {
        PREF_UPLOADING_ENABLED -> {
            val (imagesVideos, everything) = when(p.getString(key, PREF_UPLOADING_ENABLED_D)) {
                PREF_UPLOADING_ENABLED_TEXT_ONLY ->          Pair(false, false)
                PREF_UPLOADING_ENABLED_TEXT_IMAGES_VIDEOS -> Pair(true,  false)
                PREF_UPLOADING_ENABLED_EVERYTHING ->         Pair(false, true)
                else -> Pair(false, false)
            }
            enableDisableComponent(IMAGES_VIDEOS, imagesVideos)
            enableDisableComponent(EVERYTHING, everything)
        }

        PREF_UPLOADING_URI -> {
            Config.uploadUri = p.getString(key, PREF_UPLOADING_URI_D)!!
        }

        PREF_UPLOADING_FORM_FIELD_NAME -> {
            Config.uploadFormFieldName = p.getString(key, PREF_UPLOADING_FORM_FIELD_NAME_D)!!
        }

        PREF_UPLOADING_REGEX -> {
            val regex = p.getString(key, PREF_UPLOADING_REGEX_D)!!
            Config.httpUriGetter = if (regex.isEmpty()) {
                HttpUriGetter.simple
            } else {
                HttpUriGetter.fromRegex(regex)
            }
        }

        PREF_UPLOADING_ADDITIONAL_HEADERS,
        PREF_UPLOADING_AUTHENTICATION,
        PREF_UPLOADING_AUTHENTICATION_BASIC_USER,
        PREF_UPLOADING_AUTHENTICATION_BASIC_PASSWORD -> {
            val requestModifiers = mutableListOf<RequestModifier>()

            val additionalHeaders = RequestModifier.additionalHeaders(
                    p.getString(PREF_UPLOADING_ADDITIONAL_HEADERS, PREF_UPLOADING_ADDITIONAL_HEADERS_D)!!)
            if (additionalHeaders != null)
                requestModifiers.add(additionalHeaders)

            if (p.getString(PREF_UPLOADING_AUTHENTICATION, PREF_UPLOADING_AUTHENTICATION_D) == PREF_UPLOADING_AUTHENTICATION_BASIC) {
                val user = p.getString(PREF_UPLOADING_AUTHENTICATION_BASIC_USER, PREF_UPLOADING_AUTHENTICATION_BASIC_USER_D)!!
                val password = p.getString(PREF_UPLOADING_AUTHENTICATION_BASIC_PASSWORD, PREF_UPLOADING_AUTHENTICATION_BASIC_PASSWORD_D)!!
                requestModifiers.add(RequestModifier.basicAuthentication(user, password))
            }

            Config.requestModifiers = requestModifiers
        }
    }
}

private const val IMAGES_VIDEOS = "ShareTextActivityImagesVideosOnly"
private const val EVERYTHING = "ShareTextActivityEverything"

private fun enableDisableComponent(name: String, enabled: Boolean) {
    val manager = applicationContext.packageManager
    val componentName = ComponentName(BuildConfig.APPLICATION_ID, "com.ubergeek42.WeechatAndroid.$name")
    val enabledDisabled = if (enabled)
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    manager.setComponentEnabledSetting(componentName, enabledDisabled, PackageManager.DONT_KILL_APP)
}