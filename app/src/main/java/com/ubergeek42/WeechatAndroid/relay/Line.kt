// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
package com.ubergeek42.WeechatAndroid.relay

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.WeechatAndroid.upload.i
import com.ubergeek42.WeechatAndroid.utils.Linkify.linkify
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root
import com.ubergeek42.weechat.Color
import com.ubergeek42.weechat.ColorScheme
import com.ubergeek42.weechat.relay.connection.Handshake.Companion.weechatVersion
import com.ubergeek42.weechat.relay.protocol.HdataEntry
import com.ubergeek42.weechat.relay.protocol.RelayObject

open class Line internal constructor(
    @JvmField val pointer: Long,
    @JvmField val type: Type,
    @JvmField val timestamp: Long,
    private val rawPrefix: String,
    private val rawMessage: String,
    @JvmField val nick: String?,
    @JvmField val isVisible: Boolean,
    @JvmField val isHighlighted: Boolean,
    @JvmField val displayAs: DisplayAs,
    @JvmField val notify: Notify
) {
    enum class Type {
        OTHER,
        INCOMING_MESSAGE,
        OUTCOMING_MESSAGE,
    }

    enum class DisplayAs {
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

    // see table at https://weechat.org/files/doc/stable/weechat_plugin_api.en.html#_hook_line
    enum class Notify {
        NONE,
        LOW,
        MESSAGE,
        PRIVATE,
        HIGHLIGHT;

        companion object {
            fun fromByte(byte: Byte) = when (byte) {
                (-1).toByte() -> NONE
                0.toByte() -> LOW
                1.toByte() -> MESSAGE
                2.toByte() -> PRIVATE
                3.toByte()-> HIGHLIGHT
                else -> LOW
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private var prefixString: String? = null
    private var messageString: String? = null

    @Volatile private var spannable: Spannable? = null

    @AnyThread fun invalidateSpannable() {
        spannable = null
        messageString = null
        prefixString = messageString
    }

    @AnyThread fun ensureSpannable() {
        if (spannable != null) return

        val timestamp: CharSequence? = P.dateFormat?.let { dateFormat ->
            StringBuilder().also { builder -> dateFormat.printTo(builder, this.timestamp) }
        }

        val encloseNick = P.encloseNick && displayAs == DisplayAs.SAY
        val color = Color(timestamp, rawPrefix, rawMessage, encloseNick, isHighlighted, P.maxWidth, P.align)
        val spannable: Spannable = SpannableString(color.lineString)

        if (type == Type.OTHER && P.dimDownNonHumanLines) {
            spannable.setSpan(ForegroundColorSpan(ColorScheme.get().chat_inactive_buffer[0] or
                    -0x1000000), 0, spannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else {
            for (span in color.finalSpanList) {
                val droidSpan = when (span.type) {
                    Color.Span.FGCOLOR -> ForegroundColorSpan(span.color or -0x1000000)
                    Color.Span.BGCOLOR -> BackgroundColorSpan(span.color or -0x1000000)
                    Color.Span.ITALIC -> StyleSpan(Typeface.ITALIC)
                    Color.Span.BOLD -> StyleSpan(Typeface.BOLD)
                    Color.Span.UNDERLINE -> UnderlineSpan()
                    else -> continue
                }
                spannable.setSpan(droidSpan, span.start, span.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        if (P.align != Color.ALIGN_NONE) {
            val marginSpan = LeadingMarginSpan.Standard(0, (P.letterWidth * color.margin).i)
            spannable.setSpan(marginSpan, 0, spannable.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        }

        linkify(spannable, color.messageString)

        this.prefixString = color.prefixString
        this.messageString = color.messageString
        this.spannable = spannable
    }

    // can't simply do ensureSpannable() here as this can be called for a highlights when there's no
    // activity (after OOM kill). this would parse the spannable using incorrect colors, and this
    // spannable wouldn't get reset by P if the buffer's not open.
    open fun getPrefixString() = prefixString ?: Color().parseColors(rawPrefix).toString()
    open fun getMessageString() = messageString ?: Color().parseColors(rawMessage).toString()

    val ircLikeString: String
        get() {
            val prefix = getPrefixString()
            val message = getMessageString()
            return if (displayAs == DisplayAs.SAY) "<$prefix> $message" else "$prefix $message"
        }

    open fun getSpannable(): Spannable {
        ensureSpannable()
        return spannable!!
    }

    override fun toString(): String {
        return "Line(0x" + java.lang.Long.toHexString(pointer) + ": " + ircLikeString + ")"
    }

    companion object {
        @Root private val kitty: Kitty = Kitty.make()

        @JvmStatic @WorkerThread fun make(entry: HdataEntry): Line {
            val pointer = entry.pointerLong
            val message = entry.getItem("message").asString() ?: ""
            val prefix = entry.getItem("prefix").asString() ?: ""
            val visible = entry.getItem("displayed").asChar().toInt() == 0x01
            val timestamp = entry.getItem("date").asTime().time

            val high = entry.getItem("highlight")
            val highlighted = high != null && high.asChar().toInt() == 0x01

            val notifyLevelBit = entry.getItem("notify_level")?.asByte() ?: NOTIFY_LEVEL_BIT_NOT_PRESENT

            val tagsItem = entry.getItem("tags_array")
            val tags = if (tagsItem?.type == RelayObject.WType.ARR) tagsItem.asArray().asStringArray() else null

            var nick: String? = null
            var type = Type.OTHER
            var displayAs = DisplayAs.UNSPECIFIED
            var notify = Notify.LOW

            if (tags == null) {
                // there are no tags, it's probably an old version of weechat, so we err
                // on the safe side and treat it as from human
                type = Type.INCOMING_MESSAGE
            } else {
                var log1 = false
                var selfMsg = false
                var ircAction = false
                var ircPrivmsg = false
                var notifyNone = false

                for (tag in tags) {
                    when (tag) {
                        "log1" -> log1 = true
                        "self_msg" -> selfMsg = true
                        "irc_privmsg" -> ircPrivmsg = true
                        "irc_action" -> ircAction = true
                        "notify_none" -> { notify = Notify.NONE; notifyNone = true }
                        "notify_message" -> notify = Notify.MESSAGE
                        "notify_private" -> notify = Notify.PRIVATE
                        "notify_highlight" -> notify = Notify.HIGHLIGHT
                        else -> if (tag.startsWith("nick_")) nick = tag.substring(5)
                    }
                }

                // notify_level bit supersedes tags
                if (notifyLevelBit != NOTIFY_LEVEL_BIT_NOT_PRESENT) {
                    notify = Notify.fromByte(notifyLevelBit)
                }

                // starting with roughly 3.0 (2b16036), if a line has the tag notify_none, or if
                // notify_level is -1, it is not added to the hotlist nor it beeps—even if highlighted.
                val notifyNoneForced = weechatVersion >= 0x3000000 &&
                        (notify == Notify.NONE || notifyNone)

                if (notifyNoneForced) {
                    notify = Notify.NONE
                } else if (highlighted) {
                    // highlights from irc will have the level message or private upon inspection. since
                    // we are thinking of notify as the level with which the message was added to
                    // hotlist, we have to “fix” this by looking at the highlight bit.
                    notify = Notify.HIGHLIGHT
                }

                // log1 should be reliable enough method of telling if a line is a message from user,
                // our own or someone else's. see `/help logger`: log levels 2+ are nick changes, etc.
                // ignoring `prefix_nick_...`, `nick_...` and `host_...` tags:
                //   * join: `irc_join`, `log4`
                //   * user message:  `irc_privmsg`, `notify_message`, `log1`
                //   * own message:   `irc_privmsg`, `notify_none`, `self_msg`, `no_highlight`, `log1`
                // note: some messages such as those produced by `/help` itself won't have tags at all.
                val isMessageFromSelfOrUser = log1 || ircPrivmsg || ircAction

                if (tags.isNotEmpty() && isMessageFromSelfOrUser) {
                    val isMessageFromSelf = selfMsg ||
                            weechatVersion < 0x1070000 && notify == Notify.NONE
                    type = if (isMessageFromSelf) Type.OUTCOMING_MESSAGE else Type.INCOMING_MESSAGE
                }

                if (ircAction) {
                    displayAs = DisplayAs.ACTION
                } else if (ircPrivmsg) {
                    displayAs = DisplayAs.SAY
                }
            }

            return Line(pointer, type, timestamp, prefix, message, nick, visible, highlighted,
                    displayAs, notify)
        }
    }
}

private const val NOTIFY_LEVEL_BIT_NOT_PRESENT: Byte = -123
