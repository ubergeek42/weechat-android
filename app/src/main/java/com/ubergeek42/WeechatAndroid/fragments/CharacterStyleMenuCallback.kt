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
import java.util.*


private var menuIdSource = 2123


private enum class StyleMenuItem(
   title: CharSequence,
   val span: CharacterStyle? = null,
) {
    Style("Style"),

    Bold("B", StyleSpan(Typeface.BOLD)),
    Italic("I", StyleSpan(Typeface.ITALIC)),
    Underline("U", UnderlineSpan()),
    Color("Color"),
    Reset("Reset"),

    Red("Red", ForegroundColorSpan(0xff0000.solidColor)),

    BackgroundBlue("Blue", BackgroundColorSpan(0x0000ff.solidColor));

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
                menu.add(StyleMenuItem.Red, StyleMenuItem.BackgroundBlue)
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
        is UnderlineSpan -> UnderlineSpan()
        is StyleSpan -> StyleSpan(this.style)
        is ForegroundColorSpan -> ForegroundColorSpan(this.foregroundColor)
        is BackgroundColorSpan -> BackgroundColorSpan(this.backgroundColor)
        else -> this
    }
}


private fun interface CharacterStyleMatcher<T : CharacterStyle> {
    fun matches(span: T): Boolean
}


private fun <T: CharacterStyle> T.getMatcher(): CharacterStyleMatcher<T> {
    return if (this is StyleSpan) {
        val style = this.style
        CharacterStyleMatcher { other -> other is StyleSpan && other.style and style != 0 }
    } else {
        CharacterStyleMatcher { other -> other::class.isInstance(this) }
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


@Suppress("IfThenToElvis")
private fun EditText.applySelectionStyle(span: CharacterStyle?) {
    val text = this.text!!
    val selectionStart = this.selectionStart
    val selectionEnd = this.selectionEnd

    val matcher = if (span != null) span.getMatcher() else CharacterStyleMatcher { true }

    var addSpan = false
    if (span != null) {
        val selectionSize = selectionEnd - selectionStart
        val selectionStyledCharacterCount = text.getStyledCharacterCount(selectionStart, selectionEnd, matcher)
        val selectionUnstyledCharacterCount = selectionSize - selectionStyledCharacterCount
        addSpan = selectionUnstyledCharacterCount > selectionStyledCharacterCount
    }

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
        styledBits.set(spanInRegionStart - regionStart, spanInRegionEnd - regionStart + 1, true)
    }

    return styledBits.cardinality()
}


private inline fun <reified T : CharacterStyle> Editable.forEachSpan(regionStart: Int, regionEnd: Int,
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