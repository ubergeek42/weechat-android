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
        val matcher = URL.matcher(spannable)

        while (matcher.find()) {
            var url = matcher.group(0)!!
            if (url.startsWith("www.")) url = "http://$url"
            spannable.setSpan(URLSpan2(url),
                              matcher.start(), matcher.end(),
                              Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    @JvmStatic
    fun linkify(spannable: Spannable, message: CharSequence) {
        val filteredMessage = if (messageFilter != null) {
                                  Utils.replaceWithSpaces(message, messageFilter)
                              } else {
                                  message
                              }

        val offset = spannable.length - filteredMessage.length
        val matcher = URL.matcher(filteredMessage)

        while (matcher.find()) {
            var url = matcher.group(0)!!
            if (url.startsWith("www.")) url = "http://$url"
            spannable.setSpan(URLSpan2(url),
                              offset + matcher.start(), offset + matcher.end(),
                              Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    @JvmStatic
    fun getFirstUrlFromString(s: CharSequence): String? {
        val matcher = URL.matcher(s)
        return if (matcher.find()) matcher.group(0) else null
    }
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


private const val IRIC = "[a-zA-Z0-9\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF]"
private const val GLTDC = "[a-zA-Z\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF]"

private val URL = Pattern.compile(
        // url must be preceded by a word boundary
        "\\b" +
        // protocol:// or www.
        "(?:[A-z]+://|www\\.)" +
        // optional user:pass at
        "(?:\\S+(?::\\S*)?@)?" +
        "(?:" +
              // ip address (+ some exceptions)
              "(?!10(?:\\.\\d{1,3}){3})" +
              "(?!127(?:\\.\\d{1,3}){3})" +
              "(?!169\\.254(?:\\.\\d{1,3}){2})" +
              "(?!192\\.168(?:\\.\\d{1,3}){2})" +
              "(?!172\\.(?:1[6-9]|2\\d|3[0-1])(?:\\.\\d{1,3}){2})" +
              "(?:[1-9]\\d?|1\\d\\d|2[01]\\d|22[0-3])" +
              "(?:\\.(?:1?\\d{1,2}|2[0-4]\\d|25[0-5])){2}" +
              "(?:\\.(?:[1-9]\\d?|1\\d\\d|2[0-4]\\d|25[0-4]))" +
        "|" +
              // domain name (a.b.c.com)
              "(?:" + IRIC + "+(?:-" + IRIC + "+)*\\.)*" +  // (\w+(-\w+)*\.)*      a. a-b. a-b.a-b.
              GLTDC + "{1,63}" +                            // (\w){1,63}           com ninja r
        ")" +
        // port?
        "(?::\\d{2,5})?" +
        // & the rest
        "(?:" +
              "\\.?[/?]" +
              "(?:" +
                    // hello(world) in hello(world))
                    "(?:" +
                         "[^\\s(]*" +
                         "\\(" +
                         "[^\\s)]+" +
                         "\\)" +
                    ")+" +
                    "[^\\s)]*?" +
              "|" +
                    // any string (non-greedy!)
                    "\\S*?" +
              ")" +
        ")?" +
        // url must be directly followed by
        "(?=" +
              // some possible punctuation
              // AND space or end of string
              "[])>,.!?:\"”]*" +
              "(?:\\s|$)" +
        ")"
        , Pattern.CASE_INSENSITIVE or Pattern.COMMENTS)