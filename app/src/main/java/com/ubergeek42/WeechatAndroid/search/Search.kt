package com.ubergeek42.WeechatAndroid.search

import com.ubergeek42.WeechatAndroid.relay.Line
import java.util.*

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
                    val source = SearchConfig.source
                    val caseSensitive = SearchConfig.caseSensitive
                    val regex = SearchConfig.regex

                    val getSource: ((Line) -> String) = when (source) {
                        SearchConfig.Source.Prefix -> Line::getPrefixString
                        SearchConfig.Source.Message -> Line::getMessageString
                        SearchConfig.Source.PrefixAndMessage -> Line::getIrcLikeString
                    }

                    val sourceMatchesSearch: ((String) -> Boolean) = if (regex) {
                        val flags = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                        text.toRegex(flags)::matches
                    } else {
                        if (caseSensitive) {
                            { string: String -> string.contains(text) }
                        } else {
                            val locale = Locale.getDefault()
                            val textUpper = text.toUpperCase(locale)
                            val textLower = text.toLowerCase(locale);

                            { string: String ->
                                string.toUpperCase(locale).contains(textUpper) ||
                                        string.toLowerCase(locale).contains(textLower)
                            }
                        }
                    }

                    Matcher { line -> sourceMatchesSearch(getSource(line)) }
                }
            }
        }
    }
}

object SearchConfig {
    @JvmField var caseSensitive = false
    @JvmField var regex = false
    @JvmField var source = Source.PrefixAndMessage

    enum class Source {
        Prefix,
        Message,
        PrefixAndMessage
    }
}