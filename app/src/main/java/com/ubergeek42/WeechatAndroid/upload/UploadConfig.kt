package com.ubergeek42.WeechatAndroid.upload

import android.content.ComponentName
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.preference.PreferenceManager
import com.ubergeek42.WeechatAndroid.BuildConfig
import com.ubergeek42.WeechatAndroid.notifications.shortcuts
import com.ubergeek42.WeechatAndroid.utils.Constants.*


object Config {
    var uploadAcceptShared = PREF_UPLOAD_ACCEPT_D
    var noOfDirectShareTargets = 2
    var uploadUri = PREF_UPLOAD_URI_D
    var uploadFormFieldName = PREF_UPLOAD_FORM_FIELD_NAME
    var httpUriGetter = HttpUriGetter.simple
    var requestModifiers = emptyList<RequestModifier>()
    var requestBodyModifiers = emptyList<RequestBodyModifier>()
    var rememberUploadsFor = PREF_UPLOAD_REMEMBER_UPLOADS_FOR_D.hours_to_ms
    @JvmField var paperclipAction1: Target = Target.MediaStoreImages
    @JvmField var paperclipAction2: Target? = Target.Camera
}

fun initPreferences() {
    val p = PreferenceManager.getDefaultSharedPreferences(applicationContext)
    for (key in listOf(
            PREF_UPLOAD_ACCEPT,
            PREF_UPLOAD_NO_OF_DIRECT_SHARE_TARGETS,
            PREF_UPLOAD_URI,
            PREF_UPLOAD_FORM_FIELD_NAME,
            PREF_UPLOAD_REGEX,
            PREF_UPLOAD_ADDITIONAL_HEADERS,
            PREF_UPLOAD_ADDITIONAL_FIELDS,
            PREF_UPLOAD_AUTHENTICATION,
            PREF_UPLOAD_AUTHENTICATION_BASIC_USER,
            PREF_UPLOAD_AUTHENTICATION_BASIC_PASSWORD,
            PREF_UPLOAD_REMEMBER_UPLOADS_FOR,
            PREF_PAPERCLIP_ACTION_1,
            PREF_PAPERCLIP_ACTION_2,
    )) {
        onSharedPreferenceChanged(p, key)
    }
}

@Suppress("BooleanLiteralArgument")
fun onSharedPreferenceChanged(p: SharedPreferences, key: String) {
    when (key) {
        PREF_UPLOAD_ACCEPT -> {
            val uploadAcceptShared = p.getString(key, PREF_UPLOAD_ACCEPT_D)!!
            Config.uploadAcceptShared = uploadAcceptShared
            val (text, media, everything) = when (uploadAcceptShared) {
                PREF_UPLOAD_ACCEPT_TEXT_ONLY -> true to false to false
                PREF_UPLOAD_ACCEPT_TEXT_AND_MEDIA -> false to true to false
                PREF_UPLOAD_ACCEPT_EVERYTHING -> false to false to true
                else -> true to false to false
            }
            enableDisableComponent(ShareActivityAliases.TEXT_ONLY.alias, text)
            enableDisableComponent(ShareActivityAliases.TEXT_AND_MEDIA.alias, media)
            enableDisableComponent(ShareActivityAliases.EVERYTHING.alias, everything)
        }

        PREF_UPLOAD_NO_OF_DIRECT_SHARE_TARGETS -> {
            val stringValue = p.getString(key, PREF_UPLOAD_NO_OF_DIRECT_SHARE_TARGETS_D)!!
            Config.noOfDirectShareTargets = stringValue.toInt()
            shortcuts.updateDirectShareCount()
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

        PREF_UPLOAD_ADDITIONAL_FIELDS -> {
            val additionalFields = RequestBodyModifier.additionalFields(
                    p.getString(key, PREF_UPLOAD_ADDITIONAL_FIELDS_D)!!)
            Config.requestBodyModifiers = if (additionalFields == null)
                emptyList() else listOf(additionalFields)
        }

        PREF_UPLOAD_REMEMBER_UPLOADS_FOR -> {
            suppress<NumberFormatException> {
                Config.rememberUploadsFor = p.getString(key, PREF_UPLOAD_REMEMBER_UPLOADS_FOR_D)!!.hours_to_ms
            }
        }

        PREF_PAPERCLIP_ACTION_1 -> {
            val value = p.getString(key, PREF_PAPERCLIP_ACTION_1_D)
            Config.paperclipAction1 = Target.fromPreferenceValue(value) ?: Target.ContentMedia
        }

        PREF_PAPERCLIP_ACTION_2 -> {
            val value = p.getString(key, PREF_PAPERCLIP_ACTION_2_D)
            Config.paperclipAction2 = Target.fromPreferenceValue(value)
        }
    }
}


fun validateUploadConfig() {
    if (Config.uploadUri.isBlank()) throw UploadConfigValidationError("Upload URL not set")
}

class UploadConfigValidationError(message: String) : Exception(message)


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