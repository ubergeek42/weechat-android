package com.ubergeek42.WeechatAndroid.upload

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.ubergeek42.WeechatAndroid.utils.Constants.*

object Config {
    var uploadUri = PREF_UPLOADING_URI_D
    var uploadFormFieldName = PREF_UPLOADING_FORM_FIELD_NAME
    var httpUriGetter = HttpUriGetter.simple
    var requestModifiers = emptyList<RequestModifier>()
}

fun initPreferences() {
    val p = PreferenceManager.getDefaultSharedPreferences(applicationContext)
    for (key in listOf(PREF_UPLOADING_URI,
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

fun onSharedPreferenceChanged(p: SharedPreferences, key: String) {
    when (key) {
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
