package com.ubergeek42.WeechatAndroid.search

import com.ubergeek42.WeechatAndroid.relay.Line
import com.ubergeek42.WeechatAndroid.relay.HeaderLine
import com.ubergeek42.WeechatAndroid.search.Search.Matcher
import com.ubergeek42.WeechatAndroid.upload.applicationContext
import java.util.regex.PatternSyntaxException


typealias MatchList = List<Long>


class Search(
    private val matcher: Matcher,
    private val searchListener: Listener,
) {
    private var lastMatches: MatchList? = null

    fun onLinesChanged(lines: List<Line>) {
        val matches = lines
                .filter { it::class == Line::class || it is HeaderLine }
                .filter { matcher.matches(it) }
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
            @Throws(PatternSyntaxException::class)
            @JvmStatic fun fromString(text: String, config: SearchConfig): Matcher {
                return if (text.isEmpty()) {
                    Matcher { line -> line.isHighlighted }
                } else {
                    val (caseSensitive, regex, source) = config

                    val getSource = when (source) {
                        SearchConfig.Source.Prefix -> Line::prefixString
                        SearchConfig.Source.Message -> Line::messageString
                        SearchConfig.Source.PrefixAndMessage -> Line::ircLikeString
                    }

                    val sourceMatchesSearch: ((String) -> Boolean) = if (regex) {
                        val flags = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                        text.toRegex(flags)::containsMatchIn
                    } else {
                        if (caseSensitive) {
                            { string -> string.contains(text) }
                        } else {
                            val locale = applicationContext.resources.configuration.locale;
                            val textUpper = text.toUpperCase(locale)
                            val textLower = text.toLowerCase(locale);

                            { string ->
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


data class SearchConfig(
    @JvmField val caseSensitive: Boolean,
    @JvmField val regex: Boolean,
    @JvmField val source: Source,
) {
    enum class Source {
        Prefix,
        Message,
        PrefixAndMessage
    }

    companion object {
        @JvmStatic val default = SearchConfig(caseSensitive = false,
                                              regex = false,
                                              source = Source.PrefixAndMessage)
    }
}
