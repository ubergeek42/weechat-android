// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
package com.ubergeek42.WeechatAndroid.relay

import com.ubergeek42.WeechatAndroid.utils.removeChars
import com.ubergeek42.WeechatAndroid.utils.removeFirst
import com.ubergeek42.WeechatAndroid.utils.replaceFirstWith
import java.util.Collections
import java.util.LinkedList
import java.util.Locale


// this class is supposed to be synchronized by Buffer
internal class Nicks {
    enum class Status {
        Init,
        Ready
    }

    var status = Status.Init

    // sorted in last-spoke-comes-first order
    private val nicks = LinkedList<Nick>()

    fun getCopySortedByPrefixAndName(): ArrayList<Nick> {
        return ArrayList(nicks).also {
            Collections.sort(it, prefixAndNameComparator)
        }
    }

    fun addNick(nick: Nick) {
        nicks.add(nick)
    }

    fun removeNick(pointer: Long) {
        nicks.removeFirst { it.pointer == pointer }
    }

    fun updateNick(nick: Nick) {
        nicks.replaceFirstWith(nick) { it.pointer == nick.pointer }
    }

    fun replaceNicks(nicks: Collection<Nick>) {
        this.nicks.clear()
        this.nicks.addAll(nicks)
        status = Status.Ready
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    fun getMostRecentNicksMatching(prefix: String, ignoreChars: String): List<String> {
        val lowerCasePrefix = prefix.lowercase(Locale.ROOT)
        val ignoreCharsSansPrefixChars = ignoreChars.removeChars(lowerCasePrefix)

        return nicks
                .map { it.name }
                .filter { name ->
                    val lowerCaseNick = name.lowercase(Locale.ROOT)
                    val lowerCaseNickSansIgnoreChars = lowerCaseNick.removeChars(ignoreCharsSansPrefixChars)
                    lowerCaseNickSansIgnoreChars.startsWith(lowerCasePrefix)
                }
    }

    fun bumpNickToTop(name: String) {
        val nick = nicks.removeFirst { it.name == name }
        if (nick != null) nicks.addFirst(nick)
    }

    fun sortNicksByNamesThatSpokeLast(namesThatSpokeLast: Iterator<String>) {
        val nameToPosition = mutableMapOf<String, Int>()

        namesThatSpokeLast.forEach { name ->
            if (!nameToPosition.containsKey(name)) {
                nameToPosition[name] = nameToPosition.size
            }
        }

        nicks.sortWith { left: Nick, right: Nick ->
            val l = nameToPosition[left.name] ?: Int.MAX_VALUE
            val r = nameToPosition[right.name] ?: Int.MAX_VALUE
            l - r
        }
    }
}


private val prefixAndNameComparator = Comparator { left: Nick, right: Nick ->
    val diff = prioritizePrefix(left.prefix) - prioritizePrefix(right.prefix)
    if (diff != 0) diff else left.name.compareTo(right.name, ignoreCase = true)
}

// lower values = higher priority
private fun prioritizePrefix(p: String): Int {
    if (p.isEmpty()) return 100

    return when (p[0]) {
        '~' -> 1    // Owners
        '&' -> 2    // Admins
        '@' -> 3    // Ops
        '%' -> 4    // Half-Ops
        '+' -> 5    // Voiced
        else -> 100 // Other
    }
}

