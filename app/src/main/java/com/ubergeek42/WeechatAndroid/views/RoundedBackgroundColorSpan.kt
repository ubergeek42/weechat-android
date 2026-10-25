package com.ubergeek42.WeechatAndroid.views

import android.graphics.Canvas
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.text.Spanned
import android.text.StaticLayout
import com.ubergeek42.WeechatAndroid.upload.dp_to_pxf
import com.ubergeek42.WeechatAndroid.upload.f
import com.ubergeek42.WeechatAndroid.upload.i


class RoundedBackgroundColorSpan(val color: Int)


/**
 * Draw the background for [RoundedBackgroundColorSpan]s.
 * If any span spans multiple lines, the background for those is drawn
 * as a single flowing shape with rounded corners.
 *
 * This is a separate function instead of a method of one of the spans,
 * as there are simply no spans that have proper access to the layout itself.
 *
 * This logic works like this:
 *
 *   * We gather the rects enclosing each span, taking into account the direction of text.
 *     For text with line height < 1.0, we ignore line spacing when calculating the rects.
 *     This ensures that the letters don't “stick out” of the background.
 *     For text with line height > 1.0, we *do* take line height into account,
 *     so that the rects from consecutive lines can be merged into one path.
 *
 *   * We slightly enlarge all rects in all directions, staying within the canvas.
 *     This is mainly done for aesthetic reasons.
 *     Note that while the text is most often drawn between the accent and descent of the font,
 *     in some cases glyphs may extend beyond this range.
 *     Enlarging the rects also helps with this somewhat.
 *
 *   * We merge consecutive sets of rects of the same color.
 *
 *   * If there's a rect of one color directly followed by a rect of another color on the same line,
 *     we slightly enlarge the first rect in the direction of text. By overlapping the rects,
 *     we make sure that appear like `(one (two)` instead of `(one)(two)`,
 *     with no gaps on the edges of the rects.
 *
 *   * Finally, the rects are joined into a single path,
 *     and drawn with an effect that automagically rounds the corners.
 *
 * TODO Support bidirectional text if there is demand.
 *   This is, for example, a run of Arabic text embedded in an English sentece.
 *   Or, add an option to use the regular `BackgroundColorSpan`.
 *
 *   Consider the following text, with `[]` indicating a background span:
 *
 *       Forward-flowing text, back[ward-flowing] text, forward again
 *
 *   It should get rendered as follows:
 *
 *       Forward-flowing text [-draw]kcab
 *       txet [gniwolf], forward again
 *
 *   This is currently **NOT** supported,
 *
 *   Note aslo that when gathering the rects
 *   we assume that the direction of text is the same as the direction of the paragraph.
 *   This **MIGHT NOT** be the case even if the are no changes in the directions of the text.
 *
 * TODO The above steps are expect that the spans that we get from `getSpans`
 *   are in the order they were added to the text.
 *   This appears to be the case for us, but the method does not guarantee it.
 *   See if this is something that may need to be fixed.
 *
 * TODO Decide if it's worth caching the drawing information.
 *   Finding and merging the rects might be expensive,
 *   and it can be easily done when constructing the layout.
 */
fun StaticLayout.drawRoundedBackground(canvas: Canvas, paint: Paint, firstLineIndent: Float) {
    data class RectsToColor(val rects: MutableList<RectF>, val color: Int)

    val spanned = text as? Spanned ?: return
    val textStart = getLineStart(0)
    val textEnd = getLineEnd(lineCount - 1)

    val listOfRectsToColor = spanned.getSpans(textStart, textEnd, RoundedBackgroundColorSpan::class.java)
            .ifEmpty { return }
            .mapNotNullTo(mutableListOf()) { span ->
                val start = maxOf(textStart, spanned.getSpanStart(span))
                val end = minOf(textEnd, spanned.getSpanEnd(span))
                val rects = collectRects(start, end, firstLineIndent)
                if (rects.isNotEmpty()) RectsToColor(rects, span.color) else null
            }

    val drawingBounds = RectF(canvas.getClipBounds())
    listOfRectsToColor.forEach { (rects, _) -> rects.forEach { it.addBloom(drawingBounds) } }

    listOfRectsToColor.zipWithNextInPlace { a, b, removeB ->
        if (a.color == b.color) {
            a.rects.addAll(b.rects)
            removeB()
        }
    }

    listOfRectsToColor.zipWithNextInPlace { a, b ->
        val lastRect = a.rects.last()
        val nextRect = b.rects.first()
        if (lastRect.top == nextRect.top) {
            when {
                lastRect.left < nextRect.left && lastRect.right > nextRect.left -> lastRect.right += RADIUS
                lastRect.left > nextRect.left && lastRect.left < nextRect.right -> lastRect.left -= RADIUS
            }
        }
    }

    listOfRectsToColor.forEach { (rects, color) ->
        paintRects(canvas, paint, color, rects)
    }
}


private fun StaticLayout.collectRects(start: Int, end: Int, firstLineIndent: Float): MutableList<RectF> {
    val startingLineIndex = getLineForOffset(start)
    val endingLineIndex = getLineForOffset(end)

    val rects = (startingLineIndex..endingLineIndex).mapNotNullTo(mutableListOf()) { lineIndex ->
        val isLtr = getParagraphDirection(lineIndex) > 0

        val left: Float
        val right: Float

        if (isLtr) {
            left = when (lineIndex) {
                startingLineIndex -> getPrimaryHorizontal(start)
                else              -> 0f // = getLineLeft(lineIndex). Can't be the first line
            }

            right = when (lineIndex) {
                endingLineIndex -> getPrimaryHorizontal(end)
                0               -> getLineMax(lineIndex) + firstLineIndent
                else            -> getLineMax(lineIndex) // = getLineRight(lineIndex)
            }
        } else {
            right = when (lineIndex) {
                startingLineIndex -> getPrimaryHorizontal(start)
                else              -> width.f
            }

            left = when (lineIndex) {
                endingLineIndex -> getPrimaryHorizontal(end)
                else            -> width - getLineMax(lineIndex) // = getLineLeft(lineIndex)
            }
        }

        val top = when (lineIndex) {
            0    -> getLineTop(lineIndex) - topPadding // top padding is negative
            else -> getLineTop(lineIndex)
        }

        val bottom = when {
            lineIndex == lineCount - 1 -> getLineBottom(lineIndex) - bottomPadding
            spacingMultiplier < 1.0f   -> getLineBottomIgnoringLineSpacing(lineIndex)
            else                       -> getLineBottom(lineIndex)
        }

        when {
            left < right -> RectF(left, top.f, right, bottom.f)
            left > right -> RectF(right, top.f, left, bottom.f)
            else -> null // there was null! but why? using backticks
        }
    }

    return rects
}


private fun paintRects(canvas: Canvas, paint: Paint, color: Int, rects: List<RectF>) {
    if (rects.isEmpty()) return

    val originalColor = paint.color
    paint.color = color

    if (rects.size == 1) {
        canvas.drawRoundRect(rects.first(), RADIUS, RADIUS, paint)
    } else {
        val originalPathEffect = paint.pathEffect
        paint.pathEffect = CORNER_PATH_EFFECT
        canvas.drawPath(rects.toUnionPath(), paint)
        paint.pathEffect = originalPathEffect
    }

    paint.color = originalColor
}


private fun RectF.toPath() = Path().apply { addRect(this@toPath, Path.Direction.CW) }

private fun List<RectF>.toUnionPath() = first().toPath()
        .also {
            for (i in 1 until size) {
                it.op(this[i].toPath(), Path.Op.UNION)
            }
        }


private fun RectF.addBloom(bounds: RectF) {
    left = maxOf(bounds.left, left - H_BLOOM)
    top = maxOf(bounds.top, top - V_BLOOM)
    right = minOf(bounds.right, right + H_BLOOM)
    bottom = minOf(bounds.bottom, bottom + V_BLOOM)
}


private fun StaticLayout.getLineBottomIgnoringLineSpacing(lineIndex: Int): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        getLineBottom(lineIndex, false)
    } else {
        val lineTop = getLineTop(lineIndex)
        val lineBottom = getLineBottom(lineIndex)
        (lineTop + (lineBottom - lineTop) / spacingMultiplier - spacingAdd).i
    }
}


private val V_BLOOM = 1.dp_to_pxf * 0.5f
val H_BLOOM = 1.dp_to_pxf * 1.33f

private val RADIUS = 5.dp_to_pxf
private val CORNER_PATH_EFFECT = CornerPathEffect(RADIUS)


private fun <T> MutableCollection<T>.zipWithNextInPlace(process: (a: T, b: T) -> Unit) {
    if (size < 2) return
    val iterator = iterator()

    var current = iterator.next()
    while (iterator.hasNext()) {
        val next = iterator.next()
        process(current, next)
        current = next
    }
}

private fun <T> MutableCollection<T>.zipWithNextInPlace(process: (a: T, b: T, removeB: () -> Unit) -> Unit) {
    if (size < 2) return
    val iterator = iterator()

    var current = iterator.next()

    while (iterator.hasNext()) {
        val next = iterator.next()
        var nextRemoved = false

        process(current, next) {
            iterator.remove()
            nextRemoved = true
        }

        if (!nextRemoved) current = next
    }
}