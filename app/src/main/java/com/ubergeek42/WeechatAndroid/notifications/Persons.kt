package com.ubergeek42.WeechatAndroid.notifications

import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import com.ubergeek42.WeechatAndroid.relay.Buffer
import com.ubergeek42.WeechatAndroid.relay.Hotlist


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
        val iconBitmap = generateIcon(text = iconText, colorKey = colorKey)
        val icon = IconCompat.createWithBitmap(iconBitmap)

        val person = Person.Builder()
            .setKey(storageKey)
            .setName(nick)
            .setIcon(icon)
            .build()

        cachedPersons[storageKey] = CachedPerson(colorKey = colorKey, nick = nick, person = person)

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


fun getPersonByPrivateBuffer(buffer: Hotlist.HotBuffer): Person {
    return getPerson(
        key = buffer.fullName,
        colorKey = buffer.fullName,
        nick = buffer.shortName,
        missing = false
    )
}
