package com.ubergeek42.WeechatAndroid.utils;


import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.text.Spannable;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.URLSpan;
import android.view.View;
import android.widget.Toast;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.relay.Line;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Our own Linkifier
 * Rationale: allow custom custom URLs as well URLs starting with www. with less fiddling;
 * also use non-colored URLSpans so that we don't have to recreate them every time
 */
public class Linkify {

    // pattern will always find urls starting with protocol, the only exception being "www."
    // in this case, prepend "http://" to url.
    public static void linkify (@NonNull Spannable s) {
        Matcher m = URL.matcher(s);
        while (m.find()) {
            String url = m.group(0);
            if (url.startsWith("www."))
                url = "http://" + url;
            s.setSpan(new URLSpan2(url), m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    public static @Nullable String getFirstUrlFromString(CharSequence s) {
        Matcher m = URL.matcher(s);
        return m.find() ? m.group(0) : null;
    }

    // an url span that doesn't change the color of the link
    // also it checks if the text line associated with it has clickDisable set by
    // onItemLongClick from ChatLinesAdapter, which prevents unwanted clicks on long clicks
    // also make sure we don't crash if nothing can handle our intent
    private static class URLSpan2 extends URLSpan {

        URLSpan2(@NonNull String url) {super(url);}

        @Override public void updateDrawState(@NonNull TextPaint ds) {
            ds.setUnderlineText(true);
        }

        @Override public void onClick(@NonNull View widget) {
            Line line = (Line) widget.getTag();
            if (line.clickDisabled)
                line.clickDisabled = false;
            else {
                // don't call super because super will open urls in the same tab
                Context context = widget.getContext();
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getURL()));
                try {
                    context.startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    CharSequence text = context.getString(R.string.linkify_error_no_activity, getURL());
                    Toast.makeText(widget.getContext(), text, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    final private static String IRIC = "[a-zA-Z0-9\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF]";
    final private static String GLTDC = "[a-zA-Z\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF]";

    final private static Pattern URL = Pattern.compile(
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
              "(?:" + IRIC + "+(?:-" + IRIC + "+)*\\.)+" +  // (\w+(-\w+)*\.)+      a. a-b. a-b.a-b.
              GLTDC + "{2,63}" +                            // (\w){2,63}           com ninja
        ")" +
        // port?
        "(?::\\d{2,5})?" +
        // & the rest
        "(?:" +
              "\\.?/" +
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
        , Pattern.CASE_INSENSITIVE | Pattern.COMMENTS);
}
