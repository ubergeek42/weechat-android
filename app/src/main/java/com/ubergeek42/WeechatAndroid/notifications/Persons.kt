package com.ubergeek42.WeechatAndroid.notifications

import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import com.ubergeek42.WeechatAndroid.relay.Buffer
import com.ubergeek42.WeechatAndroid.relay.Hotlist


private val persons = mutableMapOf<String, Person>()


fun getPerson(key: String,
              colorKey: String,
              nick: String,
              missing: Boolean
): Person {
    val storageKey = if (missing) "missing_$key" else key
    val iconText = if (missing) "?" else nick

    return persons.getOrPut(storageKey) {
        val iconBitmap = generateIcon(text = iconText, colorKey = colorKey)
        val icon = IconCompat.createWithBitmap(iconBitmap)

        Person.Builder()
            .setKey(storageKey)
            .setName(nick)
            .setIcon(icon)
            .build()
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
