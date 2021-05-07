package com.ubergeek42.WeechatAndroid.notifications

import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat


private val persons = mutableMapOf<String, Person>()


fun getPerson(key: String, nick: String): Person {
    return persons.getOrPut(key) {
        val iconBitmap = generateIcon(text = nick, colorKey = key)
        val icon = IconCompat.createWithBitmap(iconBitmap)

        Person.Builder()
            .setKey(key)
            .setName(nick)
            .setIcon(icon)
            .build()
    }
}
