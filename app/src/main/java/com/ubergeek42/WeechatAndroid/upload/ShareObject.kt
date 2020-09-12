package com.ubergeek42.WeechatAndroid.upload

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.*
import android.text.style.ImageSpan
import android.widget.EditText
import androidx.annotation.MainThread
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.ubergeek42.WeechatAndroid.Weechat
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.concurrent.thread


data class ShareSpan(
        val context: Context,
        val suri: Suri,
        val bitmap: Bitmap
) : ImageSpan(context, bitmap)


////////////////////////////////////////////////////////////////////////////////////////////////////


enum class InsertAt {
    CURRENT_POSITION, END
}


interface ShareObject {
    fun insert(editText: EditText, insertAt: InsertAt)
}


data class TextShareObject(
    val text: CharSequence
) : ShareObject {
    @MainThread override fun insert(editText: EditText, insertAt: InsertAt) {
        editText.insertAddingSpacesAsNeeded(insertAt, text)
    }
}


// non-breaking space. regular spaces and characters can lead to some problems with some keyboards...
const val PLACEHOLDER_TEXT = "\u00a0"

@Suppress("ArrayInDataClass")
open class UrisShareObject(
    private val suris: List<Suri>
) : ShareObject {
    private val bitmaps: Array<Bitmap?> = arrayOfNulls(suris.size)

    override fun insert(editText: EditText, insertAt: InsertAt) {
        val context = editText.context
        getAllImagesAndRunOnMainThread(context) {
            for (i in suris.indices) {
                editText.insertAddingSpacesAsNeeded(insertAt, makeImageSpanned(context, i))
            }
        }
    }

    private fun getAllImagesAndRunOnMainThread(context: Context, then: () -> Unit) {
        suris.forEachIndexed { i, suri ->
            thread {
                getThumbnailAndRunOnMainThread(context, suri.uri) { bitmap ->
                    bitmaps[i] = bitmap
                    if (bitmaps.all { it != null }) then()
                }
            }
        }
    }

    private fun makeImageSpanned(context: Context, i: Int) : Spanned {
        val spanned = SpannableString(PLACEHOLDER_TEXT)
        val imageSpan = ShareSpan(context, suris[i], bitmaps[i]!!)
        spanned.setSpan(imageSpan, 0, PLACEHOLDER_TEXT.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return spanned
    }

    companion object {
        @JvmStatic @Throws(FileNotFoundException::class, IOException::class, SecurityException::class)
        fun fromUris(uris: List<Uri>): UrisShareObject {
            return UrisShareObject(uris.map { Suri.fromUri(it) })
        }
    }
}


////////////////////////////////////////////////////////////////////////////////////////////////////


const val THUMBNAIL_MAX_WIDTH = 250
const val THUMBNAIL_MAX_HEIGHT = 250

fun getThumbnailAndRunOnMainThread(context: Context, uri: Uri, then: (bitmap: Bitmap) -> Unit) {
    Glide.with(context)
            .asBitmap()
            .load(uri)
            .into(object : CustomTarget<Bitmap>(THUMBNAIL_MAX_WIDTH, THUMBNAIL_MAX_HEIGHT) {
                override fun onResourceReady(bitmap: Bitmap, transition: Transition<in Bitmap>?) {
                    Weechat.runOnMainThread { then(bitmap) }
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    val bitmap = makeThumbnailForUri(uri)
                    Weechat.runOnMainThread { then(bitmap) }
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    TODO("Not yet implemented")
                }
            })
}


const val TEXT_SIZE = 40f
const val PADDING = 10
const val LAYOUT_MAX_WIDTH = THUMBNAIL_MAX_WIDTH - (PADDING * 2)
const val LAYOUT_MAX_HEIGHT = THUMBNAIL_MAX_HEIGHT - (PADDING * 2)
const val TEXT_COLOR = Color.BLACK

fun makeThumbnailForUri(uri: Uri) : Bitmap {
    val text = uri.lastPathSegment ?: "file"

    val backgroundPaint = Paint()
    backgroundPaint.color = Color.WHITE
    backgroundPaint.style = Paint.Style.FILL

    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    textPaint.textSize = TEXT_SIZE
    textPaint.color = TEXT_COLOR

    val layout = StaticLayout(text, textPaint, LAYOUT_MAX_WIDTH,
            Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
    val left = if (layout.maxLineWidth > LAYOUT_MAX_WIDTH) PADDING else (THUMBNAIL_MAX_WIDTH - layout.maxLineWidth) / 2
    val top = if (layout.height > LAYOUT_MAX_HEIGHT) PADDING else (THUMBNAIL_MAX_HEIGHT - layout.height) / 2

    val image = Bitmap.createBitmap(THUMBNAIL_MAX_WIDTH, THUMBNAIL_MAX_HEIGHT, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(image)
    canvas.drawPaint(backgroundPaint)
    canvas.translate(left.toFloat(), top.toFloat())
    layout.draw(canvas)

    return image
}


////////////////////////////////////////////////////////////////////////////////////////////////////


@MainThread fun EditText.insertAddingSpacesAsNeeded(insertAt: InsertAt, word: CharSequence) {
    val pos = if (insertAt == InsertAt.CURRENT_POSITION) selectionEnd else text.length
    val shouldPrependSpace = pos > 0 && text[pos - 1] != ' '
    val shouldAppendSpace = pos < text.length && text[pos + 1] != ' '

    val wordWithSurroundingSpaces = SpannableStringBuilder()
    if (shouldPrependSpace) wordWithSurroundingSpaces.append(' ')
    wordWithSurroundingSpaces.append(word)
    if (shouldAppendSpace) wordWithSurroundingSpaces.append(' ')

    text.insert(pos, wordWithSurroundingSpaces)
}


val Layout.maxLineWidth : Int get() {
    return (0 until lineCount).maxOfOrNull { getLineWidth(it) }?.toInt() ?: width
}