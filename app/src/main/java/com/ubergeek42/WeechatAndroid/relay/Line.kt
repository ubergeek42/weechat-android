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
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.WeechatAndroid.upload.i
import com.ubergeek42.WeechatAndroid.utils.Linkify.linkify
import com.ubergeek42.WeechatAndroid.utils.SHOULD_EMOJIFY
import com.ubergeek42.WeechatAndroid.utils.emojify
import com.ubergeek42.weechat.Color
import com.ubergeek42.weechat.ColorScheme


open class Line(
    @JvmField val pointer: Long,
    @JvmField val type: LineSpec.Type,
    @JvmField val timestamp: Long,
    private val rawPrefix: String,
    private val rawMessage: String,
    @JvmField val nick: String?,
    @JvmField val isVisible: Boolean,
    @JvmField val isHighlighted: Boolean,
    @JvmField val displayAs: LineSpec.DisplayAs,
    @JvmField val notifyLevel: LineSpec.NotifyLevel
) {
    @AnyThread fun ensureSpannable() {
        if (_spannable != null) return

        val encloseNick = P.encloseNick && displayAs == LineSpec.DisplayAs.Say
        val color = Color(timestampString, rawPrefix, rawMessage, encloseNick, isHighlighted, P.maxWidth, P.align)
        val spannable: Spannable = SpannableString(color.lineString)

        if (type == LineSpec.Type.Other && P.dimDownNonHumanLines) {
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

        if (SHOULD_EMOJIFY) emojify(spannable)

        _prefixString = color.prefixString
        _messageString = color.messageString
        _spannable = spannable
    }

    @AnyThread fun invalidateSpannable() {
        _spannable = null
        _messageString = null
        _prefixString = null
    }

    @Volatile private var _spannable: Spannable? = null
    open val spannable get(): Spannable {
        ensureSpannable()
        return _spannable!!
    }

    private val timestampString get() = P.dateFormat?.let { dateFormat ->
        StringBuilder().also { builder -> dateFormat.printTo(builder, this.timestamp) }
    }

    // can't simply do ensureSpannable() here as this can be called for a highlights when there's no
    // activity (after OOM kill). this would parse the spannable using incorrect colors, and this
    // spannable wouldn't get reset by P if the buffer's not open.
    private var _prefixString: String? = null
    open val prefixString get() = _prefixString ?: Color().parseColors(rawPrefix).toString()

    private var _messageString: String? = null
    open val messageString get() = _messageString ?: Color().parseColors(rawMessage).toString()

    // caching this method (for the purpose of speeding up search)
    // yields about 5ms for searches of 4096 lines, despite what the flame chart shows
    val ircLikeString get() = if (displayAs == LineSpec.DisplayAs.Say)
            "<$prefixString> $messageString" else "$prefixString $messageString"

    val timestampedIrcLikeString: String get() =
            timestampString?.let { timestamp -> "$timestamp $ircLikeString" } ?: ircLikeString

    fun visuallyEqualsTo(other: Line) =
        type == other.type &&
        timestamp == other.timestamp &&
        rawPrefix == other.rawPrefix &&
        rawMessage == other.rawMessage &&
        isHighlighted == other.isHighlighted &&
        displayAs == other.displayAs

    override fun toString() = "Line(${pointer.as0x}): $ircLikeString)"
}
