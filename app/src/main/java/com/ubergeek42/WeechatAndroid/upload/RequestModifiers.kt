package com.ubergeek42.WeechatAndroid.upload

import okhttp3.Credentials
import okhttp3.Request

fun interface RequestModifier {
    fun modify(requestBuilder: Request.Builder)

    companion object {
        fun basicAuthentication(user: String, password: String): RequestModifier {
            return RequestModifier { requestBuilder ->
                requestBuilder.addHeader("Authorization", Credentials.basic(user, password))
            }
        }

        @Throws(IndexOutOfBoundsException::class)
        fun additionalHeaders(string: String): RequestModifier {
            val headers = string.lineSequence()
                    .filter { line -> line.isNotBlank() }
                    .map { line -> line.split(": ", limit = 2) }
                    .filter { list -> if (list.size == 2) true else throw ParseException("Line ${list[0]} doesn’t contain ': '") }
            return RequestModifier { requestBuilder ->
                for ((name, value) in headers) requestBuilder.addHeader(name, value)
            }
        }
    }

    class ParseException(message: String): Exception(message = message)
}