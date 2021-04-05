package com.ubergeek42.WeechatAndroid.fragments

import android.graphics.Typeface
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import com.ubergeek42.WeechatAndroid.views.solidColor
import com.ubergeek42.weechat.relay.connection.find
import java.lang.StringBuilder
import java.util.*


private var menuIdSource = 2123


// regular UnderlineSpan is used for suggestions, so we can't make use of it
private class UnderlineSpan2 : UnderlineSpan()


private enum class StyleMenuItem(
   title: CharSequence,
   val span: CharacterStyle? = null,
) {
    Style("Style"),

    Bold("B", StyleSpan(Typeface.BOLD)),
    Italic("I", StyleSpan(Typeface.ITALIC)),
    Underline("U", UnderlineSpan2()),
    Color("Color"),
    Reset("Reset"),

    White(MircColor.White.displayName, ForegroundColorSpan(MircColor.White.color.solidColor)),
    Black(MircColor.Black.displayName, ForegroundColorSpan(MircColor.Black.color.solidColor)),
    Navy(MircColor.Navy.displayName, ForegroundColorSpan(MircColor.Navy.color.solidColor)),
    Green(MircColor.Green.displayName, ForegroundColorSpan(MircColor.Green.color.solidColor)),
    Red(MircColor.Red.displayName, ForegroundColorSpan(MircColor.Red.color.solidColor)),
    Maroon(MircColor.Maroon.displayName, ForegroundColorSpan(MircColor.Maroon.color.solidColor)),
    Purple(MircColor.Purple.displayName, ForegroundColorSpan(MircColor.Purple.color.solidColor)),
    Orange(MircColor.Orange.displayName, ForegroundColorSpan(MircColor.Orange.color.solidColor)),
    Yellow(MircColor.Yellow.displayName, ForegroundColorSpan(MircColor.Yellow.color.solidColor)),
    LightGreen(MircColor.LightGreen.displayName, ForegroundColorSpan(MircColor.LightGreen.color.solidColor)),
    Teal(MircColor.Teal.displayName, ForegroundColorSpan(MircColor.Teal.color.solidColor)),
    Cyan(MircColor.Cyan.displayName, ForegroundColorSpan(MircColor.Cyan.color.solidColor)),
    RoyalBlue(MircColor.RoyalBlue.displayName, ForegroundColorSpan(MircColor.RoyalBlue.color.solidColor)),
    Magenta(MircColor.Magenta.displayName, ForegroundColorSpan(MircColor.Magenta.color.solidColor)),
    Gray(MircColor.Gray.displayName, ForegroundColorSpan(MircColor.Gray.color.solidColor)),
    LightGray(MircColor.LightGray.displayName, ForegroundColorSpan(MircColor.LightGray.color.solidColor)),

    BackgroundWhite(MircColor.White.displayName, BackgroundColorSpan(MircColor.White.color.solidColor)),
    BackgroundBlack(MircColor.Black.displayName, BackgroundColorSpan(MircColor.Black.color.solidColor)),
    BackgroundNavy(MircColor.Navy.displayName, BackgroundColorSpan(MircColor.Navy.color.solidColor)),
    BackgroundGreen(MircColor.Green.displayName, BackgroundColorSpan(MircColor.Green.color.solidColor)),
    BackgroundRed(MircColor.Red.displayName, BackgroundColorSpan(MircColor.Red.color.solidColor)),
    BackgroundMaroon(MircColor.Maroon.displayName, BackgroundColorSpan(MircColor.Maroon.color.solidColor)),
    BackgroundPurple(MircColor.Purple.displayName, BackgroundColorSpan(MircColor.Purple.color.solidColor)),
    BackgroundOrange(MircColor.Orange.displayName, BackgroundColorSpan(MircColor.Orange.color.solidColor)),
    BackgroundYellow(MircColor.Yellow.displayName, BackgroundColorSpan(MircColor.Yellow.color.solidColor)),
    BackgroundLightGreen(MircColor.LightGreen.displayName, BackgroundColorSpan(MircColor.LightGreen.color.solidColor)),
    BackgroundTeal(MircColor.Teal.displayName, BackgroundColorSpan(MircColor.Teal.color.solidColor)),
    BackgroundCyan(MircColor.Cyan.displayName, BackgroundColorSpan(MircColor.Cyan.color.solidColor)),
    BackgroundRoyalBlue(MircColor.RoyalBlue.displayName, BackgroundColorSpan(MircColor.RoyalBlue.color.solidColor)),
    BackgroundMagenta(MircColor.Magenta.displayName, BackgroundColorSpan(MircColor.Magenta.color.solidColor)),
    BackgroundGray(MircColor.Gray.displayName, BackgroundColorSpan(MircColor.Gray.color.solidColor)),
    BackgroundLightGray(MircColor.LightGray.displayName, BackgroundColorSpan(MircColor.LightGray.color.solidColor));

    val id = menuIdSource++

    val title = span?.let { span ->
        SpannableString(title).apply {
            setSpan(span, 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    } ?: title
}


class CharacterStyleMenuCallback(private val editText: EditText) : ActionMode.Callback {
    private var activeStyleMenuItem: StyleMenuItem? = null

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        menu.add(StyleMenuItem.Style)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        activeStyleMenuItem?.let {
            menu.clear()

            if (it == StyleMenuItem.Style) {
                menu.add(StyleMenuItem.Bold, StyleMenuItem.Italic, StyleMenuItem.Underline,
                         StyleMenuItem.Color, StyleMenuItem.Reset)
            } else if (it == StyleMenuItem.Color) {
                StyleMenuItem.values().forEach { item ->
                    if (item.span is ForegroundColorSpan || item.span is BackgroundColorSpan) {
                        menu.add(item)
                    }
                }
            }
            return true
        }
        return false
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        val styleMenuItem = StyleMenuItem::id.find(item.itemId) ?: return false

        when (styleMenuItem) {
            StyleMenuItem.Style, StyleMenuItem.Color -> {
                activeStyleMenuItem = styleMenuItem
                mode.invalidate()
            }
            else -> editText.applySelectionStyle(styleMenuItem.span)
        }

        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        activeStyleMenuItem = null
    }
}


private fun Menu.add(vararg items: StyleMenuItem) {
    items.forEach {
        this.add(0, it.id, 0, it.title)
    }
}


////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////


private fun CharacterStyle.copy(): CharacterStyle {
    return when (this) {
        is UnderlineSpan2 -> UnderlineSpan2()
        is StyleSpan -> StyleSpan(this.style)
        is ForegroundColorSpan -> ForegroundColorSpan(this.foregroundColor)
        is BackgroundColorSpan -> BackgroundColorSpan(this.backgroundColor)
        else -> this
    }
}


private fun interface CharacterStyleMatcher<T : CharacterStyle> {
    fun matches(span: T): Boolean
}


private fun <T: CharacterStyle> T.getMatcher(strictColorMatching: Boolean): CharacterStyleMatcher<T> {
    return if (this is StyleSpan) {
        val style = this.style
        CharacterStyleMatcher { other -> other is StyleSpan && other.style and style != 0 }
    } else {
        if (strictColorMatching) {
            CharacterStyleMatcher { other ->
                return@CharacterStyleMatcher when {
                    !other::class.isInstance(this) -> false
                    this is ForegroundColorSpan &&
                            this.foregroundColor != (other as ForegroundColorSpan).foregroundColor
                                    -> false
                    this is BackgroundColorSpan &&
                            this.backgroundColor != (other as BackgroundColorSpan).backgroundColor
                                    -> false
                    else -> true
                }
            }
        } else {
            CharacterStyleMatcher { other -> other::class.isInstance(this) }
        }
    }
}


private inline fun <reified T : CharacterStyle> Editable.clearSpans(regionStart: Int, regionEnd: Int, matcher: CharacterStyleMatcher<T>) {
    forEachSpan(regionStart, regionEnd, matcher) { span, start, end ->
        if (start <= regionStart) {
            setSpanEx(span, start, regionStart)
            if (end > regionEnd) {
                setSpanEx(span.copy(), regionEnd, end)
            }
        } else {
            if (end <= regionEnd) {
                removeSpan(span)
            } else {
                setSpanEx(span, regionEnd, end)
            }
        }
    }
}


private fun Editable.setSpanEx(span: CharacterStyle, start: Int, end: Int) {
    if (start < 0 || end > length) return
    if (start >= end) {
        removeSpan(span)
    } else {
        setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}


private fun EditText.applySelectionStyle(span: CharacterStyle?) {
    val text = this.text!!
    val selectionStart = this.selectionStart
    val selectionEnd = this.selectionEnd

    var addSpan = false
    if (span != null) {
        val selectionSize = selectionEnd - selectionStart
        val selectionStyledCharacterCount = text.getStyledCharacterCount(selectionStart, selectionEnd,
                span.getMatcher(strictColorMatching = true))
        addSpan = selectionStyledCharacterCount != selectionSize
    }

    val matcher = span?.getMatcher(strictColorMatching = false) ?: CharacterStyleMatcher { true }
    text.clearSpans(selectionStart, selectionEnd, matcher)
    if (addSpan) text.setSpan(span!!.copy(), selectionStart, selectionEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

}


private inline fun <reified T : CharacterStyle> Editable.getStyledCharacterCount(
        regionStart: Int, regionEnd: Int, matcher: CharacterStyleMatcher<T>): Int {
    if (regionStart >= regionEnd) return 0

    val styledBits = BitSet(regionEnd - regionStart)
    forEachSpan(regionStart, regionEnd, matcher) { _, start, end ->
        val spanInRegionStart = start.coerceAtLeast(regionStart)
        val spanInRegionEnd = end.coerceAtMost(regionEnd)
        styledBits.set(spanInRegionStart - regionStart, spanInRegionEnd - regionStart, true)
    }

    return styledBits.cardinality()
}


private inline fun <reified T : CharacterStyle> Spannable.forEachSpan(regionStart: Int, regionEnd: Int,
                                                             matcher: CharacterStyleMatcher<T>,
                                                             block: (T, Int, Int) -> Unit) {
    getSpans(regionStart, regionEnd, T::class.java).forEach { span ->
        if (matcher.matches(span)) {
            val start = getSpanStart(span)
            val end = getSpanEnd(span)
            block(span, start, end)
        }
    }
}


////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////


enum class MircColor(val code: String, val color: Int, val displayName: String) {
    White("00", 0xffffff, "White"),
    Black("01", 0x000000, "Black"),
    Navy("02", 0x00007f, "Navy"),
    Green("03", 0x009300, "Green"),
    Red("04", 0xff0000, "Red"),
    Maroon("05", 0x7f0000, "Maroon"),
    Purple("06", 0x9c009c, "Purple"),
    Orange("07", 0xfc7f00, "Orange"),
    Yellow("08", 0xffff00, "Yellow"),
    LightGreen("09", 0x00fc00, "Light Green"),
    Teal("10", 0x009393, "Teal"),
    Cyan("11", 0x00ffff, "Cyan"),
    RoyalBlue("12", 0x0000fc, "Royal blue"),
    Magenta("13", 0xff00ff, "Magenta"),
    Gray("14", 0x7f7f7f, "Gray"),
    LightGray("15", 0xd2d2d2, "Light Gray");

    companion object {
        fun byColor(color: Int) = MircColor::color.find(color and 0xffffff)
    }
}



private class MircCodedStringComposer(private val spannable: Spannable) {
    private sealed class SpanInfo(val span: CharacterStyle, val edge: Int) {
        class Start(span: CharacterStyle, start: Int) : SpanInfo(span, start)
        class End(span: CharacterStyle, end: Int) : SpanInfo(span, end)
    }

    private var currentForegroundColorCode: String? = null
    private var currentBackgroundColorCode: String? = null

    private fun CharacterStyle.toMircCode(spanOpening: Boolean): CharSequence {
        return when (this) {
            is UnderlineSpan2 -> "\u001f"
            is StyleSpan -> {
                when (style) {
                    Typeface.BOLD -> "\u0002"
                    Typeface.ITALIC -> "\u001d"
                    Typeface.BOLD_ITALIC -> "\u0002\u001d"
                    else -> ""
                }
            }
            is ForegroundColorSpan -> {
                if (spanOpening) {
                    val color = MircColor.byColor(this.foregroundColor)
                    if (color == null) "" else "\u0003${color.code}"
                } else {
                    if (currentBackgroundColorCode == null) "\u0003" else "\u0003\u0003,$currentBackgroundColorCode"
                }
            }
            is BackgroundColorSpan -> {
                if (spanOpening) {
                    val color = MircColor.byColor(this.backgroundColor)
                    if (color == null) "" else "\u0003,${color.code}"
                } else {
                    if (currentForegroundColorCode == null) "\u0003" else "\u0003\u0003$currentForegroundColorCode"
                }
            }
            else -> ""
        }
    }

    fun compose(): String {
        val string = spannable.toString()
        val spans = mutableListOf<SpanInfo>()

        spannable.forEachSpan(0, spannable.length, CharacterStyleMatcher { true }) { span, start, end ->
            spans.add(SpanInfo.Start(span, start))
            spans.add(SpanInfo.End(span, end))
        }

        // lower edges before higher, ends before starts
        spans.sortBy { it.edge * 2 + if (it is SpanInfo.Start) 1 else 0 }

        val out = StringBuilder()

        for (index in 0..spannable.length) {
            spans.filter { it.edge == index }.forEach {
                val spanOpening = it is SpanInfo.Start

                out.append(it.span.toMircCode(spanOpening = spanOpening))

                if (it.span is ForegroundColorSpan) currentForegroundColorCode =
                        if (spanOpening) MircColor.byColor(it.span.foregroundColor)?.code else null
                if (it.span is BackgroundColorSpan) currentBackgroundColorCode =
                        if (spanOpening) MircColor.byColor(it.span.backgroundColor)?.code else null
            }
            if (index < spannable.length) out.append(string[index])
        }

        return out.toString()
    }
}

fun CharSequence.toMircCodedString(): CharSequence {
    return if (this is Spannable) {
        MircCodedStringComposer(this).compose()
    } else {
        this
    }
}
