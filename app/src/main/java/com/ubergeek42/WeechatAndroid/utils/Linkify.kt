package com.ubergeek42.WeechatAndroid.utils

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.text.Spannable
import android.text.Spanned
import android.text.TextPaint
import android.text.style.URLSpan
import android.view.View
import com.ubergeek42.WeechatAndroid.R
import java.util.regex.Pattern


object Linkify {
    @JvmStatic var messageFilter: Pattern? = null

    // pattern will always find urls starting with a scheme, the only exception being "www."
    // in this case, prepend "http://" to the url
    @JvmStatic
    fun linkify(spannable: Spannable) {
        findUrls(spannable)?.forEach { match ->
            val url = match.value.let { if (it.startsWith("www.")) "http://$it" else it }
            spannable.setSpan(URLSpan2(url),
                    match.range.first, match.range.last + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    @JvmStatic
    fun linkify(spannable: Spannable, message: CharSequence) {
        val filteredMessage = messageFilter?.let {
            Utils.replaceWithSpaces(message, it)
        } ?: message

        val offset = spannable.length - message.length

        findUrls(filteredMessage)?.forEach { match ->
            val url = match.value.let { if (it.startsWith("www.")) "http://$it" else it }
            spannable.setSpan(URLSpan2(url),
                    match.range.first + offset, match.range.last + offset + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    @JvmStatic
    fun getFirstUrlFromString(s: CharSequence) = URL.find(s)?.value
}


// an url span that doesn't change the color of the link
private class URLSpan2(url: String) : URLSpan(url) {
    override fun updateDrawState(ds: TextPaint) {
        ds.isUnderlineText = true
    }

    // super will open urls in the same tab via Browser.EXTRA_APPLICATION_ID
    // this implementation doesn't set that extra and opens urls in separate tabs
    override fun onClick(widget: View) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            widget.context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toaster.ErrorToast.show(R.string.error__etc__activity_not_found_for_url, url)
        }
    }
}


private fun findUrls(input: CharSequence): Sequence<MatchResult>? {
    return if (input.contains("://") || input.contains("www", ignoreCase = true)) {
        URL.findAll(input)
    } else {
        null
    }
}


// 00-1f     c0 control chars
// 20        space
// 21-2f     !"#$%&'()*+,-./
// 30-39         0123456789
// 3a-40     :;<=>?@
// 41-5a         ABCDEFGHIJKLMNOPQRSTUVWXYZ
// 5b-60     [\]^_`
// 61-7a         abcdefghijklmnopqrstuvwxyz
// 7b-7e     {|}~
// 7f        del
// 80-9f     c1 control chars
// a0        nbsp
@Suppress("RegExpRepeatedSpace", "SpellCheckingInspection", "RegExpRedundantEscape")
private val URL = run {
    val purePunycodeChar = """[a-z0-9]"""   // not mixed with ascii
    val badCharRange = """\x00-\x20\x7f-\xa0\ufff0-\uffff\s"""
    val goodChar = """[^$badCharRange]"""
    val goodHostChar = """[^\x00-\x2f\x3a-\x40\x5b-\x60\x7b-\xa0\ufff0-\uffff…]"""
    val goodTldChar = """[^\x00-\x40\x5b-\x60\x7b-\xa0\ufff0-\uffff…]"""

    val ipv4Segment = """(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)"""
    val ipv4 = """(?:$ipv4Segment\.){3} $ipv4Segment"""

    val ipv6Segment = """[0-9A-Fa-f]{1,4}"""
    val ipv6 = """
        \[
        (?:
                                                         (?:$ipv6Segment:){7} $ipv6Segment
            |                                         :: (?:$ipv6Segment:){6} $ipv6Segment
            | (?:                      $ipv6Segment)? :: (?:$ipv6Segment:){5} $ipv6Segment
            | (?:(?:$ipv6Segment:)?    $ipv6Segment)? :: (?:$ipv6Segment:){4} $ipv6Segment
            | (?:(?:$ipv6Segment:){0,2}$ipv6Segment)? :: (?:$ipv6Segment:){3} $ipv6Segment
            | (?:(?:$ipv6Segment:){0,3}$ipv6Segment)? :: (?:$ipv6Segment:){2} $ipv6Segment
            | (?:(?:$ipv6Segment:){0,4}$ipv6Segment)? :: (?:$ipv6Segment:)    $ipv6Segment
            | (?:(?:$ipv6Segment:){0,5}$ipv6Segment)? ::                      $ipv6Segment
            | (?:(?:$ipv6Segment:){0,6}$ipv6Segment)? ::
        )
        \]
    """

    // domain name includes non-standard single-letter top level domains and signle label domains;
    // also, the fqdn dot, but only if followed by url-ish things: / or :123
    val hostSegment = """$goodHostChar+(?:-+$goodHostChar+)*"""
    val tld = """(?:$goodTldChar{1,63}?|xn--$purePunycodeChar+)"""
    val domainName = """(?:$hostSegment\.)*$tld (?:\.(?=/|:\d))?"""
    val optionalUserInfo = """(?:[^$badCharRange@]*@)?"""

    """
    # url must be preceded by a word boundary
    \b
    
    (?:
        [A-Za-z+]+://
        $optionalUserInfo
        (?:$domainName|$ipv4|$ipv6)
    |
        [Ww]{3}\.
        $domainName
    )

    # optional port
    (?::\d{1,5})?

    # / or ? and the rest
    (?:
        [/?]
        
        # hello<world> in "hello<world>>", but parentheses
        (?:
            [^$badCharRange(]*
            \(
            [^$badCharRange)]+
            \)
        )*
        
        # any string, non-greedy!
        $goodChar*?
    )?

    # url must be directly followed by:
    (?=
        # some possible punctuation
        [\]>,.…)!?:'"”’@]*
        
        # and the end of string, or a space or another non-url character
        (?:$|[$badCharRange])
    )
    """.toRegex(RegexOption.COMMENTS)
}