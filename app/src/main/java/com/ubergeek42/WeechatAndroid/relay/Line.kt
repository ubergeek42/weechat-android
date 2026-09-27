// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
package com.ubergeek42.WeechatAndroid.relay

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import androidx.annotation.AnyThread
import com.ubergeek42.WeechatAndroid.service.P
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
    @AnyThread fun ensurePrecomputedLayout() {
        getPrecomputedLayout()
        if (prefixSpanned == null) prefixString = getPrefixSpanned().toString()
        if (messageSpanned == null) messageString = getMessageSpanned().toString()
    }

    @AnyThread fun clearPrecomputerLayoutEtc() {
        precomputedLineLayout = null
        prefixSpanned = null
        messageSpanned = null
        prefixString = null
        messageString = null
    }

    // can't simply do ensureSpannable() here as this can be called for a highlights when there's no
    // activity (after OOM kill). this would parse the spannable using incorrect colors, and this
    // spannable wouldn't get reset by P if the buffer's not open.
    //
    // TODO Optimize color stripping and don't store this here at all
    private var prefixString: String? = null

    fun getPrefixString() = prefixString
            ?: rawPrefix.toStringWithWeechatColorsStripped()
                    .also { prefixString = it }

    private var messageString: String? = null

    fun getMessageString() = messageString
            ?: rawMessage.toStringWithWeechatColorsStripped()
                    .also { messageString = it }

    private var prefixSpanned: Spanned? = null

    fun getPrefixSpanned() = prefixSpanned
            ?: rawPrefix.toSpannableWithWeechatColorsParsed(isHighlighted, type == LineSpec.Type.Other && P.dimDownNonHumanLines)
                    .also {
                        if (SHOULD_EMOJIFY) emojify(it)
                        prefixSpanned = it
                    }

    private var messageSpanned: Spanned? = null

    fun getMessageSpanned() = messageSpanned
            ?: rawMessage.toSpannableWithWeechatColorsParsed(false, type == LineSpec.Type.Other && P.dimDownNonHumanLines)
                    .also {
                        if (SHOULD_EMOJIFY) emojify(it)
                        linkify(it)
                        messageSpanned = it
                    }

    private var precomputedLineLayout: PrecomputedLineLayout? = null

    fun getPrecomputedLayout() = precomputedLineLayout
            ?: PrecomputedLineLayout(this)
                    .also { precomputedLineLayout = it }

    // caching this method (for the purpose of speeding up search)
    // yields about 5ms for searches of 4096 lines, despite what the flame chart shows
    fun getIrcLikeString() = if (displayAs == LineSpec.DisplayAs.Say)
            "<${getPrefixString()}> ${getMessageString()}" else "${getPrefixString()} ${getMessageString()}"

    fun getTimestampedIrcLikeString(): String =
            P.dateFormat?.let { "${it.print(timestamp)} ${getIrcLikeString()}" } ?: getIrcLikeString()

    fun visuallyEqualsTo(other: Line) =
        type == other.type &&
        timestamp == other.timestamp &&
        rawPrefix == other.rawPrefix &&
        rawMessage == other.rawMessage &&
        isHighlighted == other.isHighlighted &&
        displayAs == other.displayAs

    override fun toString() = "Line(${pointer.as0x}): ${getIrcLikeString()})"
}


fun String.toStringWithWeechatColorsStripped(): String {
    return Color().parseColors(this).toString()
}


// TODO Parse Weechat colors in a more direct way
fun String.toSpannableWithWeechatColorsParsed(highlight: Boolean, dim: Boolean): Spannable {
    val color = Color()
    color.parseColors(this)
    val spannable: Spannable = SpannableString(color.out)

    if (dim) {
        val dimColor = ColorScheme.get().chat_inactive_buffer[0] or -0x1000000
        spannable.setSpan(ForegroundColorSpan(dimColor), 0, spannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    } else if (highlight) {
        val highlightForegroundColor = ColorScheme.get().chat_highlight[0]
        val highlightBackgroundColor = ColorScheme.get().chat_highlight[1]
        if (highlightForegroundColor != -1) {
            spannable.setSpan(ForegroundColorSpan(highlightForegroundColor or -0x1000000), 0, spannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (highlightBackgroundColor != -1) {
            spannable.setSpan(BackgroundColorSpan(highlightBackgroundColor or -0x1000000), 0, spannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    } else {
        for (span in color.spanList) {
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

    return spannable
}


fun Spanned.getUrls() = getSpans(0, length, URLSpan::class.java).map { it.url }
