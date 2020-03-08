// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package com.ubergeek42.WeechatAndroid.relay;

import android.graphics.Typeface;
import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
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

import java.text.DateFormat;
import java.util.Date;

public class Line {
    // core message data
    final public long pointer;
    final @Nullable public String prefix;
    final @NonNull public String message;
    final Date date;

    // additional data
    final public int type;
    final public boolean visible;
    final public boolean highlighted;

    @Nullable String speakingNick;
    public boolean action;
    private boolean privmsg;

    // sole purpose of this is to prevent onClick event on inner URLSpans to be fired
    // when user long-presses on the screen and a context menu is shown
    public boolean clickDisabled = false;

    // processed line ready to be displayed
    volatile public @Nullable Spannable spannable = null;

    @WorkerThread public Line(long pointer, Date date, @Nullable String prefix, @Nullable String message,
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

    final static int LINE_OTHER = 0;
    final static int LINE_OWN = 1;
    final public static int LINE_MESSAGE = 2;

    //////////////////////////////////////////////////////////////////////////////////////////// processing stuff

    @AnyThread void eraseProcessedMessage() {
        spannable = null;
    }

    @AnyThread void processMessage() {
        if (spannable == null) forceProcessMessage();
    }

    /**
     * process the message and create a spannable object according to settings
     * * TODO: reuse span objects (how? would that do any good?)
     * * the problem is that one has to use distinct spans on the same string
     * * TODO: allow variable width font (should be simple enough
     */
    @AnyThread void forceProcessMessage() {
        DateFormat dateFormat = P.dateFormat;
        String timestamp = (dateFormat == null) ? null : dateFormat.format(date);
        boolean encloseNick = P.encloseNick && privmsg && !action;
        Color color = new Color(timestamp, prefix, message, encloseNick, highlighted, P.maxWidth, P.align);
        Spannable spannable = new SpannableString(color.cleanMessage);

        if (this.type == LINE_OTHER && P.dimDownNonHumanLines) {
            spannable.setSpan(new ForegroundColorSpan(ColorScheme.get().chat_inactive_buffer[0] | 0xFF000000), 0, spannable.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            CharacterStyle droidSpan;
            for (Color.Span span : color.finalSpanList) {
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
            LeadingMarginSpan margin_span = new LeadingMarginSpan.Standard(0, (int) (P.letterWidth * color.margin));
            spannable.setSpan(margin_span, 0, spannable.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        }

        // what a nice little custom linkifier we've got us here
        Linkify.linkify(spannable);

        this.spannable = spannable;
    }

    // is to be run rarely—only when we need to display a notification
    @AnyThread public String getNotificationString() {
        return String.format((!privmsg || action) ? "%s %s" : "<%s> %s",
                Color.stripEverything(prefix),
                Color.stripEverything(message));
    }

    @NonNull @Override public String toString() {
        return "Line(0x" + Long.toHexString(pointer) + ": " + getNotificationString() + ")";
    }
}
