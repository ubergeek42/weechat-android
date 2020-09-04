package com.ubergeek42.WeechatAndroid.upload

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.text.*
import android.text.style.ImageSpan
import android.widget.EditText
import androidx.annotation.MainThread
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import com.ubergeek42.WeechatAndroid.Weechat
import kotlin.concurrent.thread

enum class InsertAt {
    CURRENT_POSITION, END
}

interface ShareObject {
    fun insert(editText: EditText, insertAt: InsertAt)
}


data class TextShareObject(val text: CharSequence) : ShareObject {
    @MainThread override fun insert(editText: EditText, insertAt: InsertAt) {
        editText.insertAddingSpacesAsNeeded(insertAt, text)
    }
}


@Suppress("ArrayInDataClass")
data class UrisShareObject(val type: String?, val uris: List<Uri>) : ShareObject {
    private val bitmaps: Array<Bitmap?> = arrayOfNulls(uris.size)

    constructor(type: String?, uri: Uri) : this(type, listOf(uri))

    override fun insert(editText: EditText, insertAt: InsertAt) {
        insert(editText, insertAt, null)
    }

    fun insert(editText: EditText, insertAt: InsertAt, then: (() -> Unit)?) {
        val context = editText.context
        getAllImagesAndRunOnMainThread(context) {
            for (i in uris.indices) {
                editText.insertAddingSpacesAsNeeded(insertAt, makeImageSpanned(context, i))
            }
            if (then != null) then()
        }
    }

    private fun getAllImagesAndRunOnMainThread(context: Context, then: () -> Unit) {
        uris.forEachIndexed { i, uri ->
            thread {
                getThumbnailAndRunOnMainThread(context, uri) { bitmap ->
                    bitmaps[i] = bitmap
                    if (bitmaps.all { it != null }) then()
                }
            }
        }
    }

    private fun makeImageSpanned(context: Context, i: Int) : Spanned {
        val spanned = SpannableString(PLACEHOLDER_TEXT)
        val imageSpan = ImageSpan(context, bitmaps[i]!!)
        spanned.setSpan(imageSpan, 0, PLACEHOLDER_TEXT.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return spanned
    }
}


////////////////////////////////////////////////////////////////////////////////////////////////////


fun EditText.insertAddingSpacesAsNeeded(insertAt: InsertAt, word: CharSequence) {
    val pos = if (insertAt == InsertAt.CURRENT_POSITION) selectionEnd else text.length
    val shouldPrependSpace = pos > 0 && text[pos - 1] != ' '
    val shouldAppendSpace = pos < text.length && text[pos + 1] != ' '

    val wordWithSurroundingSpaces = SpannableStringBuilder()
    if (shouldPrependSpace) wordWithSurroundingSpaces.append(' ')
    wordWithSurroundingSpaces.append(word)
    if (shouldAppendSpace) wordWithSurroundingSpaces.append(' ')

    text.insert(pos, wordWithSurroundingSpaces)
}


const val THUMBNAIL_MAX_WIDTH = 250
const val THUMBNAIL_MAX_HEIGHT = 250


fun getThumbnailAndRunOnMainThread(context: Context, uri: Uri, then: (bitmap: Bitmap) -> Unit) {
    Glide.with(context)
            .asBitmap()
            .load(uri)
            .into(object : SimpleTarget<Bitmap>(THUMBNAIL_MAX_WIDTH, THUMBNAIL_MAX_HEIGHT) {
                override fun onResourceReady(bitmap: Bitmap, transition: Transition<in Bitmap>?) {
                    Weechat.runOnMainThread { then(bitmap) }
                }
            })
}