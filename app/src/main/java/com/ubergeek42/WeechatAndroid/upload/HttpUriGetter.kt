package com.ubergeek42.WeechatAndroid.upload

import java.util.regex.PatternSyntaxException

fun interface HttpUriGetter {
    class UriLookupException(text: String) : Exception(text)

    @Throws(UriLookupException::class)
    fun getUri(body: String): String

    companion object {
        // this getter simply returns the whole body
        val simple = HttpUriGetter { body -> body }

        // this getter will find the uri using provided regex.
        // if that regex has capture groups, the first one is used
        // if there are no capture groups, the whole match is used
        @JvmStatic @Throws(PatternSyntaxException::class)
        fun fromRegex(string: String): HttpUriGetter {
            if (string.isBlank()) throw PatternSyntaxException("Blank regex", string, 0)
            val regex = Regex(string)

            return HttpUriGetter { body ->
                regex.find(body)?.let {
                    val httpUri = it.groupValues[if (it.groupValues.size > 1) 1 else 0 ]
                    if (httpUri.isNotBlank())
                        return@HttpUriGetter httpUri
                }
                val tinyBody = if (body.length > 30) body.substring(0..30) + "..." else body
                throw UriLookupException("Could not determine HTTP URI using regex $string\n\nBody: $tinyBody")
            }
        }
    }
}