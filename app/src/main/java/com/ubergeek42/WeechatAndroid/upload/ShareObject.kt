package com.ubergeek42.WeechatAndroid.upload

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.*
import android.widget.EditText
import androidx.annotation.MainThread
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.ubergeek42.WeechatAndroid.media.Config
import com.ubergeek42.WeechatAndroid.media.WAGlideModule.isContextValidForGlide
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.coroutines.resume


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

open class UrisShareObject(
    private val suris: List<Suri>
) : ShareObject {
    override fun insert(editText: EditText, insertAt: InsertAt) {
        editText.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
            insertAsync(editText, insertAt)
        }
    }

    suspend fun insertAsync(editText: EditText, insertAt: InsertAt) {
        val thumbnailSpannables = coroutineScope {
            suris.map { async { makeThumbnailSpannable(editText.context, it) } }.awaitAll()
        }

        thumbnailSpannables.forEach { thumbnailSpannable ->
            editText.insertAddingSpacesAsNeeded(insertAt, thumbnailSpannable)
        }
    }

    companion object {
        @JvmStatic @Throws(FileNotFoundException::class, IOException::class, SecurityException::class)
        fun fromUris(uris: List<Uri>): UrisShareObject {
            return UrisShareObject(uris.map { Suri.fromUri(it) })
        }
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////

suspend fun makeThumbnailSpannable(context: Context, suri: Suri): Spannable {
    val bitmapOrNull = getBitmapOrNull(context, suri.uri)
    val span = if (bitmapOrNull != null) BitmapShareSpan(suri, bitmapOrNull) else NonBitmapShareSpan(suri)

    return SpannableString(PLACEHOLDER_TEXT).apply {
        setSpan(span, 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

// this starts the upload in a worker thread and exits immediately.
// target callbacks will be called on the main thread
suspend fun getBitmapOrNull(context: Context, uri: Uri): Bitmap? =
    suspendCancellableCoroutine { continuation ->
        if (isContextValidForGlide(context)) {
            Glide.with(context)
                .asBitmap()
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .transform(MultiTransformation(
                        NotQuiteCenterCrop(),
                        RoundedCorners(Config.THUMBNAIL_CORNER_RADIUS),
                ))
                .load(uri)
                .into(object : CustomTarget<Bitmap>(THUMBNAIL_MAX_WIDTH, THUMBNAIL_MAX_HEIGHT) {
                    @MainThread override fun onResourceReady(bitmap: Bitmap, transition: Transition<in Bitmap>?) {
                        continuation.resume(bitmap)
                    }

                    // The request seems to be attempted once again on minimizing/restoring the app.
                    // To avoid that, clear target soon, but not on current thread--the library doesn't allow it.
                    // See https://github.com/bumptech/glide/issues/4125
                    @MainThread override fun onLoadFailed(errorDrawable: Drawable?) {
                        continuation.resume(null)
                        main { Glide.with(context).clear(this) }
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                        // this shouldn't happen
                    }
                })
        }
    }

////////////////////////////////////////////////////////////////////////////////////////////////////

@MainThread fun EditText.insertAddingSpacesAsNeeded(insertAt: InsertAt, word: CharSequence) {
    val pos = if (insertAt == InsertAt.CURRENT_POSITION) selectionEnd else text.length
    insertAddingSpacesAsNeeded(pos, word)
}

@MainThread fun EditText.insertAddingSpacesAsNeeded(pos: Int, word: CharSequence) {
    val wordStartsWithSpace = word.firstOrNull() == ' '
    val wordEndsWithSpace = word.lastOrNull() == ' '
    val    spaceBeforeInsertLocation = pos > 0 && text[pos - 1] == ' '
    val nonSpaceBeforeInsertLocation = pos > 0 && text[pos - 1] != ' '
    val    spaceAfterInsertLocation = pos < text.length && text[pos] == ' '
    val nonSpaceAfterInsertLocation = pos < text.length && text[pos] != ' '

    val shouldPrependSpace = nonSpaceBeforeInsertLocation && !wordStartsWithSpace
    val shouldAppendSpace = nonSpaceAfterInsertLocation && !wordEndsWithSpace
    val shouldCollapseSpacesBefore = spaceBeforeInsertLocation && wordStartsWithSpace
    val shouldCollapseSpacesAfter = spaceAfterInsertLocation && wordEndsWithSpace

    val wordWithSurroundingSpaces = SpannableStringBuilder()
    if (shouldPrependSpace) wordWithSurroundingSpaces.append(' ')
    wordWithSurroundingSpaces.append(word)
    if (shouldAppendSpace) wordWithSurroundingSpaces.append(' ')

    val replaceFrom = if (shouldCollapseSpacesBefore) pos - 1 else pos
    val replaceTo = if (shouldCollapseSpacesAfter) pos + 1 else pos
    text.replace(replaceFrom, replaceTo, wordWithSurroundingSpaces)

    if (pos < replaceTo) setSelection(replaceFrom + wordWithSurroundingSpaces.length)
}

////////////////////////////////////////////////////////////////////////////////////////////////////


// this simply loads all images for an intent so that they are cached in glide
fun preloadThumbnailsForIntent(intent: Intent) {
    val uris = when (intent.action) {
        Intent.ACTION_SEND -> {
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (uri == null) null else listOf(uri)
        }
        Intent.ACTION_SEND_MULTIPLE -> {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        }
        else -> null
    }

    uris?.forEach { uri ->
        GlobalScope.launch { getBitmapOrNull(applicationContext, uri) }
    }
}