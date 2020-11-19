package com.ubergeek42.WeechatAndroid.upload

import okhttp3.Credentials
import okhttp3.Headers
import okhttp3.MultipartBody
import okhttp3.Request

fun interface RequestModifier {
    fun modify(requestBuilder: Request.Builder)

    companion object {
        @JvmStatic fun basicAuthentication(user: String, password: String): RequestModifier {
            return RequestModifier { requestBuilder ->
                requestBuilder.addHeader("Authorization", Credentials.basic(user, password))
            }
        }

        @JvmStatic @Throws(ParseException::class, IllegalArgumentException::class)
        fun additionalHeaders(string: String): RequestModifier? {
            val headers = string.lineSequence()
                    .filter { line -> line.isNotBlank() }
                    .map { line -> line.splitIntoTwoBy(": ") }
                    .onEach { (name, value) -> Headers.Builder().add(name, value) }
                    .toList()
            if (headers.isEmpty())
                return null
            return RequestModifier { requestBuilder ->
                for ((name, value) in headers) requestBuilder.addHeader(name, value)
            }
        }
    }

    class ParseException(message: String): Exception(message)
}


fun interface RequestBodyModifier {
    fun modify(requestBodyBuilder: MultipartBody.Builder)

    companion object {
        @JvmStatic fun additionalFields(string: String): RequestBodyModifier? {
            val fields = string.lineSequence()
                    .filter { line -> line.isNotBlank() }
                    .map { line -> line.splitIntoTwoBy("=") }
                    .onEach { (name, value) -> MultipartBody.Builder().addFormDataPart(name, value) }
                    .toList()
            if (fields.isEmpty())
                return null
            return RequestBodyModifier { requestBodyBuilder ->
                for ((name, value) in fields) requestBodyBuilder.addFormDataPart(name, value)
            }
        }
    }
}


private fun String.splitIntoTwoBy(delimiter: String) : Pair<String, String> {
    val parts = this.split(delimiter, limit = 2)
    if (parts.size != 2) throw RequestModifier.ParseException("Line doesn’t contain '$delimiter': $this")
    return parts[0] to parts[1]
}
