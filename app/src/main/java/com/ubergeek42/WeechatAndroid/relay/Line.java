// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package com.ubergeek42.WeechatAndroid.relay;

import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.ubergeek42.WeechatAndroid.service.P;
import com.ubergeek42.WeechatAndroid.utils.Linkify;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;
import com.ubergeek42.weechat.Color;
import com.ubergeek42.weechat.ColorScheme;
import com.ubergeek42.weechat.relay.connection.RelayConnection;
import com.ubergeek42.weechat.relay.protocol.HdataEntry;
import com.ubergeek42.weechat.relay.protocol.RelayObject;

import org.joda.time.format.DateTimeFormatter;

public class Line {
    final private static @Root Kitty kitty = Kitty.make();

    public enum Type {
        OTHER,
        INCOMING_MESSAGE,
        OUTCOMING_MESSAGE
    }

    public enum DisplayAs {
        UNSPECIFIED,    // not any of:
        SAY,            // can be written as <nick> message
        ACTION,         // can be written as * nick message
    }

    // notify levels, as specified by the `notify_xxx` tags and the `notify_level` bit, is the best
    // thing that we can use to determine if we should issue notifications. the algorithm is a tad
    // complicated, however.

    // * get notify level from the `notify_level` bit. it's better to use this as “its value is also
    //   affected by the max hotlist level you can set for some nicks (so not only tags in line)”
    //   * if not present, get notify level from tags (we are using the last tag)
    //     * if no `notify_*` tags are present, use low. see footnote at
    //       https://weechat.org/files/doc/devel/weechat_user.en.html#lines_format
    // * if weechat >= 3.0:
    //   * if either tag `notify_none` is present, or `notify_level` is none, make sure that our
    //     notify level is none. this can be not so in some weird situations
    // * if notify level wasn't coerced to none in the previous step, and if highlight bit is set,
    //   set notify level to highlight. highlights coming from irc can have notify levels message or
    //   private. see discussion at https://github.com/weechat/weechat/issues/1529

    // fun commands:
    //   /wait 1 /print -tags notify_none,notify_highlight foo\t bar
    //   /wait 1 /print -tags notify_none,irc_privmsg foo\t your_nick
    //     (irc_privmsg is needed for weechat to highlight your_nick)
    //   /buffer set hotlist_max_level_nicks_add someones_nick:0
    //     (see https://blog.weechat.org/post/2010/12/02/Max-hotlist-level-for-some-nicks)

    // our final `notify` field represents the “final product”. it is the level with which the line
    // was added to hotlist and if it's private or highlight we know we should issue a notification.
    public enum Notify {
        NONE,
        LOW,
        MESSAGE,
        PRIVATE,
        HIGHLIGHT;

        // see table at https://weechat.org/files/doc/stable/weechat_plugin_api.en.html#_hook_line
        static Notify fromNotifyLevelBit(byte notify_level) {
            switch (notify_level) {
                case -1: return NONE;
                default:
                case 0: return LOW;
                case 1: return MESSAGE;
                case 2: return PRIVATE;
                case 3: return HIGHLIGHT;
            }
        }
    }

    final private static byte NOTIFY_LEVEL_BIT_NOT_PRESENT = -123;

    final public long pointer;
    final public Type type;
    final public boolean isHighlighted;

    final public DisplayAs displayAs;
    final public Notify notify;

    final long timestamp;
    final boolean isVisible;

    final private String rawPrefix;
    final private String rawMessage;
    final private @Nullable String nick;

    Line(long pointer, Type type, long timestamp, @NonNull String rawPrefix, @NonNull String rawMessage,
         @Nullable String nick, boolean visible, boolean isHighlighted, DisplayAs displayAs, Notify notify) {
        this.pointer = pointer;
        this.type = type;
        this.timestamp = timestamp;
        this.rawPrefix = rawPrefix;
        this.rawMessage = rawMessage;
        this.nick = nick;
        this.isVisible = visible;
        this.isHighlighted = isHighlighted;
        this.displayAs = displayAs;
        this.notify = notify;
    }

    @WorkerThread public static @NonNull Line make(@NonNull HdataEntry entry) {
        long pointer = entry.getPointerLong();
        String message = entry.getItem("message").asString();
        String prefix = entry.getItem("prefix").asString();
        boolean visible = entry.getItem("displayed").asChar() == 0x01;
        long timestamp = entry.getItem("date").asTime().getTime();

        RelayObject high = entry.getItem("highlight");
        boolean highlighted = high != null && high.asChar() == 0x01;

        RelayObject notifyLevelObj = entry.getItem("notify_level");
        byte notifyLevelBit = notifyLevelObj == null ? NOTIFY_LEVEL_BIT_NOT_PRESENT : notifyLevelObj.asByte();

        RelayObject tagsItem = entry.getItem("tags_array");
        String[] tags = tagsItem != null && tagsItem.getType() == RelayObject.WType.ARR ?
                tagsItem.asArray().asStringArray() : null;

        message = message == null ? "" : message;
        prefix = prefix == null ? "" : prefix;
        String nick = null;

        Type type = Type.OTHER;
        DisplayAs displayAs = DisplayAs.UNSPECIFIED;
        Notify notify = Notify.LOW;

        if (tags == null) {
            // there are no tags, it's probably an old version of weechat, so we err
            // on the safe side and treat it as from human
            type = Type.INCOMING_MESSAGE;
        } else {
            boolean log1 = false, self_msg = false, irc_action = false, irc_privmsg = false,
                    notify_none = false;

            for (String tag : tags) {
                switch (tag) {
                    case "log1":        log1 = true; break;
                    case "self_msg":    self_msg = true; break;
                    case "irc_privmsg": irc_privmsg = true; break;
                    case "irc_action":  irc_action = true; break;
                    case "notify_none":      notify = Notify.NONE; notify_none = true; break;
                    case "notify_message":   notify = Notify.MESSAGE; break;
                    case "notify_private":   notify = Notify.PRIVATE; break;
                    case "notify_highlight": notify = Notify.HIGHLIGHT; break;
                    default:
                        if (tag.startsWith("nick_")) nick = tag.substring(5);
                }
            }

            // notify_level bit supersedes tags
            if (notifyLevelBit != NOTIFY_LEVEL_BIT_NOT_PRESENT)
                notify = Notify.fromNotifyLevelBit(notifyLevelBit);

            // starting with roughly 3.0 (2b16036), if a line has the tag notify_none, or if
            // notify_level is -1, it is not added to the hotlist nor it beeps—even if highlighted.
            boolean notifyNoneForced = RelayConnection.weechatVersion >= 0x3000000 &&
                    (notify == Notify.NONE || notify_none);

            if (notifyNoneForced) {
                notify = Notify.NONE;
            } else if (highlighted) {
                // highlights from irc will have the level message or private upon inspection. since
                // we are thinking of notify as the level with which the message was added to
                // hotlist, we have to “fix” this by looking at the highlight bit.
                notify = Notify.HIGHLIGHT;
            }

            // log1 should be reliable enough method of telling if a line is a message from user,
            // our own or someone else's. see `/help logger`: log levels 2+ are nick changes, etc.
            // ignoring `prefix_nick_...`, `nick_...` and `host_...` tags:
            //   * join: `irc_join`, `log4`
            //   * user message:  `irc_privmsg`, `notify_message`, `log1`
            //   * own message:   `irc_privmsg`, `notify_none`, `self_msg`, `no_highlight`, `log1`
            // note: some messages such as those produced by `/help` itself won't have tags at all.
            boolean isMessageFromSelfOrUser = log1 || irc_privmsg || irc_action;

            if (tags.length > 0 && isMessageFromSelfOrUser) {
                boolean isMessageFromSelf = self_msg ||
                        RelayConnection.weechatVersion < 0x1070000 && notify == Notify.NONE;
                type = isMessageFromSelf ? Type.OUTCOMING_MESSAGE : Type.INCOMING_MESSAGE;
            }

            if (irc_action) displayAs = DisplayAs.ACTION;
            else if (irc_privmsg) displayAs = DisplayAs.SAY;
        }

        return new Line(pointer, type, timestamp, prefix, message, nick, visible, highlighted,
                displayAs, notify);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private String prefixString = null;
    private String messageString = null;
    volatile private Spannable spannable = null;

    @AnyThread void invalidateSpannable() {
        spannable = null;
        prefixString = messageString = null;
    }

    @AnyThread void ensureSpannable() {
        if (spannable != null) return;

        StringBuilder timestamp = null;
        DateTimeFormatter dateFormat = P.dateFormat;
        if (dateFormat != null) {
            timestamp = new StringBuilder();
            dateFormat.printTo(timestamp, this.timestamp);
        }

        boolean encloseNick = P.encloseNick && displayAs == DisplayAs.SAY;
        Color color = new Color(timestamp, rawPrefix, rawMessage, encloseNick, isHighlighted, P.maxWidth, P.align);
        Spannable spannable = new SpannableString(color.lineString);

        if (type == Type.OTHER && P.dimDownNonHumanLines) {
            spannable.setSpan(new ForegroundColorSpan(ColorScheme.get().chat_inactive_buffer[0] |
                    0xFF000000), 0, spannable.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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

        Linkify.linkify(spannable, color.messageString);

        this.prefixString = color.prefixString;
        this.messageString = color.messageString;
        this.spannable = spannable;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public @Nullable String getNick() {
        return nick;
    }

    // can't simply do ensureSpannable() here as this can be called for a highlights when there's no
    // activity (after OOM kill). this would parse the spannable using incorrect colors, and this
    // spannable wouldn't get reset by P if the buffer's not open.
    public @NonNull String getPrefixString() {
        return prefixString != null ? prefixString : new Color().parseColors(rawPrefix).toString();
    }

    public @NonNull String getMessageString() {
        return messageString != null ? messageString : new Color().parseColors(rawMessage).toString();
    }

    public @NonNull String getIrcLikeString() {
        return String.format(displayAs == DisplayAs.SAY ? "<%s> %s" : "%s %s",
                getPrefixString(),
                getMessageString());
    }

    public @NonNull Spannable getSpannable() {
        ensureSpannable();
        return spannable;
    }

    @NonNull @Override public String toString() {
        return "Line(0x" + Long.toHexString(pointer) + ": " + getIrcLikeString() + ")";
    }
}
