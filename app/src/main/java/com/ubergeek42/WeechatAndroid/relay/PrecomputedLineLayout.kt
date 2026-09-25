package com.ubergeek42.WeechatAndroid.relay

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Rect
import android.text.Layout
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import androidx.annotation.AnyThread
import androidx.core.graphics.withTranslation
import androidx.core.text.PrecomputedTextCompat
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.WeechatAndroid.service.P.Alignment
import com.ubergeek42.WeechatAndroid.upload.f
import com.ubergeek42.WeechatAndroid.utils.SHOULD_EMOJIFY
import com.ubergeek42.WeechatAndroid.views.H_BLOOM
import com.ubergeek42.WeechatAndroid.views.drawRoundedBackground
import com.ubergeek42.weechat.ColorScheme


// Some font metrics and copies of paint that *only* depend on `P.textPaint`.
private class Cached private constructor(val textPaint: TextPaint) {
    val drawingPaint = TextPaint(textPaint)
    val timestampPaint = TextPaint(textPaint).apply { fontFeatureSettings += ", \"tnum\" 1" }

    val spaceWidth: Float = textPaint.measureText(" ")
    val aWidth: Float = textPaint.measureText("+")
    val lessThanWidth: Float = textPaint.measureText("<")
    val moreThanWidth: Float = textPaint.measureText(">")
    val plusWidth: Float
    val plusShiftForBloom: Float

    // When drawing the "+", it is positioned directly between two letters,
    // both of which can have background with some bloom, or extra padding on its sides.
    // The below code positions the "+" so that the adjacent background is allowed
    // to encroach on the bearings of the glyph (its intrinsic padding), but not glyph itself.
    init {
        val plusBounds = Rect()
        textPaint.getTextBounds("+", 0, 1, plusBounds)
        val plusAdvanceWidth = textPaint.measureText("+")
        val plusBearingLeft = plusBounds.left.f
        val plusBearingRight = plusAdvanceWidth - plusBounds.right
        plusWidth = maxOf(plusBearingLeft, H_BLOOM) + maxOf(plusBearingRight, H_BLOOM) + plusBounds.width()
        plusShiftForBloom = maxOf(0f, H_BLOOM - plusBearingLeft)
    }

    companion object {
        @Volatile private var cached: Cached? = null

        @AnyThread fun get(textPaint: TextPaint): Cached {
            cached?.let { if (it.textPaint == textPaint) return it }
            return Cached(textPaint).also { cached = it }
        }
    }
}


// Information for drawing a line. Does NOT depend on the width of the view;
// when drawing in a view, `obtainMessageLayout` should be called with the width of the view.
// Can be constructed on any thread.
class PrecomputedLineLayout {
    val drawingPaint: TextPaint // Only changed when drawing, i.e. on the main thread
    val timestampPaint: TextPaint // Never changed

    val nickPrefixOffset: Float
    val prefixOffset: Float
    val nickSuffixOffset: Float
    val moreOffset: Float
    val messageOffset: Float

    val leadingMessageIndent: Float

    val timestamp: String?
    val prefixLayout: StaticLayout
    val messagePrecomputedText: PrecomputedTextCompat

    @AnyThread constructor(line: Line) : this(
         timestamp = P.dateFormat?.print(line.timestamp),
         prefix = line.getPrefixSpanned(),
         message = line.getMessageSpanned(),
         enclosePrefix = P.encloseNick && line.displayAs == LineSpec.DisplayAs.Say,
         maxPrefixWidthInChars = P.maxWidth,
         alignment = P.align,
         textPaint = P.textPaint
    )

    @AnyThread constructor(
        timestamp: String?,
        prefix: Spanned,
        message: Spanned,
        enclosePrefix: Boolean,
        maxPrefixWidthInChars: Int,
        alignment: Alignment,
        textPaint: TextPaint,
    ) {
        val cached = Cached.get(textPaint)
        drawingPaint = cached.drawingPaint
        timestampPaint = cached.timestampPaint

        this.timestamp = timestamp

        val timestampAndSpaceWidth = when {
            timestamp != null -> timestampPaint.measureText(timestamp) + cached.spaceWidth
            else -> 0f
        }

        val maxPrefixWidth = cached.aWidth * maxPrefixWidthInChars

        val maxPrefixWithWithALittleExtra = when {
            alignment == Alignment.PrefixRight && timestamp != null -> maxPrefixWidth + 0.33f * cached.spaceWidth
            else -> maxPrefixWidth
        }

        val nickPrefixWidth = if (enclosePrefix) cached.lessThanWidth else 0f
        val nickSuffixWidth = if (enclosePrefix) cached.moreThanWidth else 0f

        val (fittingPrefixChars, consumedPrefixWidth) = if (SHOULD_EMOJIFY) {
            prefix.cutToFitWidthUsingLayout(textPaint, maxPrefixWithWithALittleExtra - nickPrefixWidth - nickSuffixWidth)
        } else {
            prefix.cutToFitWidthUsingPaint(textPaint, maxPrefixWithWithALittleExtra - nickPrefixWidth - nickSuffixWidth)
        }

        prefixOffset = when {
            alignment == Alignment.PrefixRight -> timestampAndSpaceWidth + (maxPrefixWidth - consumedPrefixWidth) - nickSuffixWidth
            else -> timestampAndSpaceWidth + nickPrefixWidth
        }

        nickPrefixOffset = if (enclosePrefix) prefixOffset - nickPrefixWidth else -1f
        nickSuffixOffset = if (enclosePrefix) prefixOffset + consumedPrefixWidth else -1f

        moreOffset = if (fittingPrefixChars < prefix.length) prefixOffset + consumedPrefixWidth + nickSuffixWidth + cached.plusShiftForBloom else -1f

        messageOffset = when (alignment) {
            Alignment.Left      -> 0f
            Alignment.Timestamp -> timestampAndSpaceWidth
            else                -> prefixOffset + consumedPrefixWidth + nickSuffixWidth + cached.plusWidth
        }

        leadingMessageIndent = prefixOffset + consumedPrefixWidth + nickSuffixWidth + cached.plusWidth - messageOffset

        prefixLayout = StaticLayout.Builder.obtain(prefix, 0, fittingPrefixChars, textPaint, Int.MAX_VALUE)
                .build()

        val messagePrecomputedTextParams = PrecomputedTextCompat.Params.Builder(textPaint)
                .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
                .build()
        messagePrecomputedText = PrecomputedTextCompat.create(message, messagePrecomputedTextParams)
    }

    @SuppressLint("WrongConstant") // Layout.BREAK_STRATEGY_HIGH_QUALITY
    fun obtainMessageLayout(viewWidth: Int, textPaint: TextPaint = P.textPaint): StaticLayout {
        return StaticLayout.Builder.obtain(messagePrecomputedText, 0, messagePrecomputedText.length, textPaint, viewWidth - messageOffset.toInt())
                .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
                .apply { if (leadingMessageIndent > 0) setIndents(intArrayOf(leadingMessageIndent.toInt(), 0), null) }
                .build()
    }

    fun draw(canvas: Canvas, messageLayout: StaticLayout, colorScheme: ColorScheme = ColorScheme.get()) {
        fun getTimestampColor() = colorScheme.chat_time[0] or -0x1000000
        fun getChatNickPrefixColor() = colorScheme.chat_nick_prefix[0] or -0x1000000
        fun getChatNickSuffixColor() = colorScheme.chat_nick_prefix[0] or -0x1000000
        fun getMoreColor() = colorScheme.chat_prefix_more[0] or -0x1000000

        val textY = -drawingPaint.fontMetrics.top

        if (timestamp != null) {
            canvas.drawText(timestamp, 0f, textY, timestampPaint.apply { color = getTimestampColor() })
        }

        if (nickPrefixOffset != -1f) {
            canvas.drawText("<", nickPrefixOffset, textY, drawingPaint.apply { color = getChatNickPrefixColor() })
        }

        canvas.withTranslation(prefixOffset, 0f) {
            prefixLayout.drawRoundedBackground(canvas, drawingPaint, 0f)
            prefixLayout.draw(canvas)
        }

        if (nickSuffixOffset != -1f) {
            canvas.drawText(">", nickSuffixOffset, textY, drawingPaint.apply { color = getChatNickSuffixColor() })
        }

        if (moreOffset != -1f) {
            canvas.drawText("+", moreOffset, textY, drawingPaint.apply { color = getMoreColor() })
        }

        canvas.withTranslation(messageOffset, 0f) {
            messageLayout.drawRoundedBackground(canvas, drawingPaint, leadingMessageIndent)
            messageLayout.draw(canvas)
        }
    }
}


data class FittingCharsAndWidth(val fittingChars: Int, val consumedWidth: Float)

fun Spanned.cutToFitWidthUsingPaint(textPaint: TextPaint, maxWidth: Float): FittingCharsAndWidth {
    val charWidths = FloatArray(this.length)

    textPaint.getTextWidths(this, 0, this.length, charWidths)

    var fittingChars = 0
    var consumedWidth = 0f

    for (charWidth in charWidths) {
        if (consumedWidth + charWidth > maxWidth) {
            break
        } else {
            consumedWidth += charWidth
            fittingChars++
        }
    }

    return FittingCharsAndWidth(fittingChars, consumedWidth)
}

fun Spanned.cutToFitWidthUsingLayout(textPaint: TextPaint, maxWidth: Float): FittingCharsAndWidth {
    val tempLayout = StaticLayout.Builder.obtain(this, 0, this.length, textPaint, Int.MAX_VALUE)
            .build()

    var fittingChars = 0
    var consumedWidth = 0f

    for (i in this.indices) {
        val offsetForChar = tempLayout.getPrimaryHorizontal(i + 1)

        if (offsetForChar > maxWidth) {
            break
        } else {
            consumedWidth = offsetForChar
            fittingChars++
        }
    }

    return FittingCharsAndWidth(fittingChars, consumedWidth)
}