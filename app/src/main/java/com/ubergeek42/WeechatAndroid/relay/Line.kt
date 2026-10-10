// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
package com.ubergeek42.WeechatAndroid.relay

import android.graphics.Paint
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.LineHeightSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import androidx.annotation.AnyThread
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.WeechatAndroid.upload.i
import com.ubergeek42.WeechatAndroid.utils.Linkify.linkify
import com.ubergeek42.WeechatAndroid.utils.SHOULD_EMOJIFY
import com.ubergeek42.WeechatAndroid.utils.emojify
import com.ubergeek42.WeechatAndroid.views.RoundedBackgroundColorSpan
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
    @JvmField val notifyLevel: LineSpec.NotifyLevel,
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
                        it.applyParagraphSpansToDoubleNewLine()
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
    open fun getIrcLikeString() = if (displayAs == LineSpec.DisplayAs.Say)
            "<${getPrefixString()}> ${getMessageString()}" else "${getPrefixString()} ${getMessageString()}"

    open fun getTimestampedIrcLikeString(): String =
            P.dateFormat?.let { "${it.print(timestamp)} ${getIrcLikeString()}" } ?: getIrcLikeString()

    open fun visuallyEqualsTo(other: Line) =
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
            spannable.setSpan(RoundedBackgroundColorSpan(highlightBackgroundColor or -0x1000000), 0, spannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    } else {
        for (span in color.spanList) {
            val droidSpan = when (span.type) {
                Color.Span.FGCOLOR -> ForegroundColorSpan(span.color or -0x1000000)
                Color.Span.BGCOLOR -> RoundedBackgroundColorSpan(span.color or -0x1000000)
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

/**
 * This makes the paragraph the height of `fontMetrics.intrinsicPadding` and `P.paragraphSpacing`,
 * effectively the same distance as the distance between individual messages.
 * The `chooseHeight` method runs *before* `StaticLayout` applies any line height changes.
 * It is supposed to be preemptively undoing those changes,
 * as we don't want the line height setting to affect the spacing between messages.
 */
class ParagraphSpan : LineHeightSpan {
    override fun chooseHeight(text: CharSequence, start: Int, end: Int, spanstartv: Int, lineHeight: Int, fm: Paint.FontMetricsInt) {
        val extra = (fm.descent - fm.ascent + P.paragraphSpacing * 2) * (P.lineHeight - 1) // Ctrl-F "spacingmult - 1" in StaticLayout.java
        fm.ascent = fm.top - fm.ascent // Same as -intrinsicTopPadding
        fm.descent = fm.bottom - fm.descent // Same as intrinsicBottomPadding
        fm.descent -= extra.i
        fm.descent = (fm.ascent + (fm.descent - fm.ascent + P.paragraphSpacing * 2) / P.lineHeight).i
    }
}

/**
 * We can have new lines in a message. WeeChat doesn't exactly define the meaning of a new line,
 * or of multiple new lines. Neither does the `draft/multiline` spec.
 *
 * Here we make an assumption that a new line should behave like `<br>`,
 * perhaps for the purpose of pasting code or poetry.
 * Also, we make an assumption that a double new line should behave like a `<p>`, as in Markdown.
 *
 * The below is a simple solution that applies message spacing to the second of every `\n\n`.
 *
 * Thought: With these assumptions, it may make sense to display new paragraphs as separate messages.
 * This would allow for a more efficient display in the `RecyclerView`,
 * however we should bear in mind the following questions:
 *   * If there's a highlight in a message, or a search term,
 *     should it semantically apply to the whole message, or only one paragraph?
 *   * If there's a media preview, do we display one per paragraph, or one per message?
 *   * What should we offer in the copy dialog?
 *
 * TODO Decide if we want to specially treat:
 *   * More than two `\n` in a row,
 *   * Lines only consisting of whitespace,
 *   * Leading and ending empty lines.
 */
fun Spannable.applyParagraphSpansToDoubleNewLine() {
    var searchIndex = 0
    while (true) {
        val index = indexOf("\n\n", startIndex = searchIndex)
        if (index == -1) return
        setSpan(ParagraphSpan(), index + 1, index + 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        searchIndex = index + 1
    }
}


fun Spanned.getUrls() = getSpans(0, length, URLSpan::class.java).map { it.url }
