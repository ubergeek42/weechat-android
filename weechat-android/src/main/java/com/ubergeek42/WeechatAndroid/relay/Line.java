/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.ubergeek42.WeechatAndroid.relay;

import android.graphics.Typeface;
import android.support.annotation.Nullable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;

import com.ubergeek42.WeechatAndroid.service.P;
import com.ubergeek42.WeechatAndroid.utils.Linkify;
import com.ubergeek42.weechat.Color;
import com.ubergeek42.weechat.ColorScheme;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Line {
    private static Logger logger = LoggerFactory.getLogger("Buffer");
    final private static boolean DEBUG = false;

    // core message data
    final public long pointer;
    final public Date date;
    final public String prefix;
    final public String message;

    // additional data
    final public int type;
    public @Nullable String speakingNick;
    public boolean privmsg, action, visible, highlighted;

    // sole purpose of this is to prevent onClick event on inner URLSpans to be fired
    // when user long-presses on the screen and a context menu is shown
    public boolean clickDisabled = false;

    // processed line ready to be displayed
    volatile public @Nullable Spannable spannable = null;

    public Line(long pointer, Date date, String prefix, @Nullable String message,
                boolean visible, boolean highlighted, @Nullable String[] tags) {
        this.pointer = pointer;
        this.date = date;
        this.prefix = prefix;
        this.message = (message == null) ? "" : message;
        this.visible = visible;
        this.highlighted = highlighted;

        if (tags != null) {
            boolean log1 = false;
            boolean notifyNone = false;

            for (String tag : tags) {
                if (tag.equals("log1"))
                    log1 = true;
                else if (tag.equals("notify_none"))
                    notifyNone = true;
                else if (tag.startsWith("nick_"))
                    this.speakingNick = tag.substring(5);
                else if (tag.endsWith("_privmsg"))
                    this.privmsg = true;
                else if (tag.endsWith("_action"))
                    this.action = true;
            }

            if (tags.length == 0 || !log1) {
                this.type = LINE_OTHER;
            } else {
                // Every "message" to user should have one or more of these tags
                // notifyNone, notify_highlight or notify_message
                this.type = notifyNone ? LINE_OWN : LINE_MESSAGE;
            }
        } else {
            // there are no tags, it's probably an old version of weechat, so we err
            // on the safe side and treat it as from human
            this.type = LINE_MESSAGE;
        }

        if (type != LINE_MESSAGE) speakingNick = null;
    }

    final public static int LINE_OTHER = 0;
    final public static int LINE_OWN = 1;
    final public static int LINE_MESSAGE = 2;

    //////////////////////////////////////////////////////////////////////////////////////////// processing stuff

    public void eraseProcessedMessage() {
        if (DEBUG) logger.warn("eraseProcessedMessage()");
        spannable = null;
    }

    public void processMessageIfNeeded() {
        if (DEBUG) logger.warn("processMessageIfNeeded()");
        if (spannable == null) processMessage();
    }

    /**
     * process the message and create a spannable object according to settings
     * * TODO: reuse span objects (how? would that do any good?)
     * * the problem is that one has to use distinct spans on the same string
     * * TODO: allow variable width font (should be simple enough
     */
    public void processMessage() {
        if (DEBUG) logger.warn("processMessage()");
        String timestamp = (P.dateFormat == null) ? null : P.dateFormat.format(date);
        boolean encloseNick = P.encloseNick && privmsg && !action;
        Color.parse(timestamp, prefix, message, encloseNick, highlighted, P.maxWidth, P.align);
        Spannable spannable = new SpannableString(Color.cleanMessage);

        if (this.type == LINE_OTHER && P.dimDownNonHumanLines) {
            spannable.setSpan(new ForegroundColorSpan(ColorScheme.get().chat_inactive_buffer[0] | 0xFF000000), 0, spannable.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            CharacterStyle droidSpan;
            for (Color.Span span : Color.finalSpanList) {
                switch (span.type) {
                    case Color.Span.FGCOLOR:   droidSpan = new ForegroundColorSpan(span.color | 0xFF000000); break;
                    case Color.Span.BGCOLOR:   droidSpan = new BackgroundColorSpan(span.color | 0xFF000000); break;
                    case Color.Span.ITALIC:    droidSpan = new StyleSpan(Typeface.ITALIC); break;
                    case Color.Span.BOLD:      droidSpan = new StyleSpan(Typeface.BOLD); break;
                    case Color.Span.UNDERLINE: droidSpan = new UnderlineSpan(); break;
                    default: continue;
                }
                spannable.setSpan(droidSpan, span.start, span.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        if (P.align != Color.ALIGN_NONE) {
            LeadingMarginSpan margin_span = new LeadingMarginSpan.Standard(0, (int) (P.letterWidth * Color.margin));
            spannable.setSpan(margin_span, 0, spannable.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        }

        // what a nice little custom linkifier we've got us here
        Linkify.linkify(spannable);

        this.spannable = spannable;
    }

    // is to be run rarely—only when we need to display a notification
    public String getNotificationString() {
        return String.format((!privmsg || action) ? "%s %s" : "<%s> %s",
                Color.stripEverything(prefix),
                Color.stripEverything(message));
    }
}
