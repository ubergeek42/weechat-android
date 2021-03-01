package com.ubergeek42.WeechatAndroid.search

import com.ubergeek42.WeechatAndroid.relay.Line

typealias MatchList = List<Long>


class Search(
    private val matcher: Matcher,
    private val searchListener: Listener,
) {
    private var lastMatches: MatchList? = null

    fun onLinesChanged(lines: List<Line>) {
        val matches = lines.filter { matcher.matches(it) }
                           .map { it.pointer }
                           .toList()

        if (lastMatches != matches) {
            lastMatches = matches
            searchListener.onSearchResultsChanged(matches)
        }
    }

    fun interface Listener {
        fun onSearchResultsChanged(matches: MatchList)
    }

    fun interface Matcher {
        fun matches(line: Line): Boolean

        companion object {
            @JvmStatic fun fromString(text: String): Matcher {
                return if (text.isEmpty()) {
                    Matcher { line -> line.isHighlighted }
                } else {
                    Matcher { line -> line.ircLikeString.contains(text) }
                }
            }
        }
    }
}
