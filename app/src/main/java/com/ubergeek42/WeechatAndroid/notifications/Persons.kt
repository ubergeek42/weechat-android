package com.ubergeek42.WeechatAndroid.notifications

import android.content.Intent
import android.net.Uri
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import com.ubergeek42.WeechatAndroid.relay.Buffer
import com.ubergeek42.WeechatAndroid.upload.applicationContext


data class CachedPerson(
    val colorKey: String,
    val nick: String,
    val person: Person,
)


private val cachedPersons = mutableMapOf<String, CachedPerson>()


fun getPerson(key: String,
              colorKey: String,
              nick: String,
              missing: Boolean
): Person {
    val storageKey = if (missing) "missing_$key" else key

    val cachedPerson = cachedPersons[storageKey]

    return if (cachedPerson != null && cachedPerson.colorKey == colorKey && cachedPerson.nick == nick) {
        cachedPerson.person
    } else {
        val iconText = if (missing) "?" else nick
        val icon = obtainAdaptiveIcon(text = iconText, colorKey = colorKey, allowUriIcons = true)

        val person = Person.Builder()
            .setKey(storageKey)
            .setName(nick)
            .setIcon(icon)
            .build()

        if (icon.type == IconCompat.TYPE_URI_ADAPTIVE_BITMAP) {
            cachedPersons[storageKey] =
                    CachedPerson(colorKey = colorKey, nick = nick, person = person)
            icon.uri.grantReadPermissionToSystem()
        }

        return person
    }
}


fun getPersonByPrivateBuffer(buffer: Buffer): Person {
    return getPerson(
        key = buffer.fullName,
        colorKey = buffer.fullName,
        nick = buffer.shortName,
        missing = false
    )
}


fun getPersonByPrivateBuffer(buffer: HotlistBuffer): Person {
    return getPerson(
        key = buffer.fullName,
        colorKey = buffer.fullName,
        nick = buffer.shortName,
        missing = false
    )
}


fun Uri.grantReadPermissionToSystem() {
    applicationContext.grantUriPermission("com.android.systemui", this,
            Intent.FLAG_GRANT_READ_URI_PERMISSION)
}