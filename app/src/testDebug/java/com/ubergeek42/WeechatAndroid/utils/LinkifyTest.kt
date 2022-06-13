package com.ubergeek42.WeechatAndroid.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

internal class LinkifyTest {
    @ParameterizedTest
    @MethodSource
    fun invalidUrlDetection(string: String) {
        assertNull(Linkify.getFirstUrlFromString(string))
    }

    @ParameterizedTest
    @MethodSource
    fun validUrlDetection(strings: Pair<String, String>) {
        val (string, url) = strings
        assertEquals(url, Linkify.getFirstUrlFromString(string))
    }

    @Suppress("unused")
    companion object {
        @JvmStatic fun invalidUrlDetection() = listOf(
            "foo",
            "http://",
            "http://#",
            "http:// fail.com",
            "http://.www..foo.bar/",
            "http://url.co1/,",
            "http://2.2.2.256/foo ",
            "http://[3210:123z::]:80/bye#",
            "www.mail-.lv",
            "www.ma.-il.lv",
            "www.127.0.0.1",
            "www.[2607:f8b0:4009:810::200e]",
            "www.123",
            "http://ser\$ver.com",
            "http://ser_ver.com",
            "http://www.abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijkl/",
            "h[://a.com/",
            "http://…",
        )


        @JvmStatic fun validUrlDetection() = listOf(
            "http://url.com",
            "http://funny.url.com",
            "http://url.com./",
            "http://url.com.:123",
            "https://hp--community.force.com/",
            "http://xn--d1abbgf6aiiy.xn--p1ai/",
            "https://xn----8sbfxoeboc6b7i.xn--p1ai/",
            "www.ma-il.lv/\$_",

            "WWW.URL.COM",
            "HTTP://URL.COM",

            "http://url",
            "http://url.r",

            "protocol://foo?parameter=value",
            "protocol+secure://foo.bar",

            " http://url.com " to "http://url.com",
            "http://url.com." to "http://url.com",
            "http://url.com”" to "http://url.com",
            "http://url.com…" to "http://url.com",
            "http://url.com/foo”" to "http://url.com/foo",
            "http://url.com/foo…" to "http://url.com/foo",
            "  https://url\u0001" to "https://url",
            "\u0003www.url.com" to "www.url.com",
            "http://url.co\u00a0m/," to "http://url.co",
            "\"https://en.wikipedia.org/wiki/Bap_(food)\"" to "https://en.wikipedia.org/wiki/Bap_(food)",
            "(http://url.com)" to "http://url.com",
            "http://foo.com/blah_blah_(wikipedia))" to "http://foo.com/blah_blah_(wikipedia)",
            "(http://foo.com/blah_blah_(wikipedia)_(again))" to "http://foo.com/blah_blah_(wikipedia)_(again)",

            "http://127.0.0.1/foo",
            "http://[3ffe:2a00:100:7031::1]",
            "http://[1080::8:800:200C:417A]/foo",
            "http://[FEDC:BA98:7654:3210:FEDC:BA98:7654:3210]:80/index.html",
            "http://[::3210]:80/hi",
            "http://[3210:123::]:80/bye#",

            "http://server.com/www.server.com",
            "http://badutf8pcokay.com/%FF?%FE#%FF",
            "http://www.abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijk.com/",
            "http://abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcde.abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijk.abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijk.abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijk.com",

            "http://➡.ws/䨹",
            "http://i❤️.ws/",
            "HTTP://ПРЕЗИДЕНТ.РФ",
            "https://моя-молитва.рф/",
            "(https://ru.wikipedia.org/wiki/Мыло_(значения))" to "https://ru.wikipedia.org/wiki/Мыло_(значения)",
            "http://➡.ws/♥/pa%2Fth;par%2Fams?que%2Fry=a&b=c",
            "http://website.com/path/is%2fslash/!$&'()*+,;=:@/path?query=!$&'()*+,;=:@?query#fragment!$&'()*+,;=:@#fragment",
            "http://10.0.0.1?a=1",
        ).map {
            if (it is String) it to it else it as Pair<*, *>
        }
    }
}