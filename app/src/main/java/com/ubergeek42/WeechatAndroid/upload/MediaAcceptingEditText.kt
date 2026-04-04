package com.ubergeek42.WeechatAndroid.upload

import android.content.Context
import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.view.ContentInfoCompat
import androidx.core.view.OnReceiveContentListener
import androidx.core.view.ViewCompat
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.utils.ActionEditText
import com.ubergeek42.WeechatAndroid.views.snackbar.showSnackbar
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root
import kotlinx.coroutines.launch


// Can not start with "*"
private val MIME_TYPES = arrayOf(
    "text/*",
    "image/*",
    "video/*",
    "audio/*",
    "application/pdf",
    "application/octet-stream" // Generic binary data fallback
)


class MediaAcceptingEditText : ActionEditText {
    @Root private val kitty = Kitty.make()

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun onCreateInputConnection(editorInfo: EditorInfo): InputConnection? {
        editorInfo.contentMimeTypes = MIME_TYPES
        setOnReceiveContentListener()
        return super.onCreateInputConnection(editorInfo)
    }

    /** As per the [OnReceiveContentListener] documentation,
      * we must either keep permissions for the content via intents,
      * or keep a reference to the payload object until we are done. */
    private val payloadsBeingProcessed = mutableSetOf<ContentInfoCompat>()

    // TODO call setPendingInputForParallelFragments()?
    //   See ef86b793811041df06d6f270a75deb3f546822e4
    private fun setOnReceiveContentListener() {
        ViewCompat.setOnReceiveContentListener(this, MIME_TYPES) { _, payload ->
            val split = payload.partition { clipDataItem -> clipDataItem.uri != null }
            val uriContent: ContentInfoCompat? = split.first
            val remaining: ContentInfoCompat? = split.second

            uriContent?.clip?.let { clipData ->
                val uris = (0..<clipData.itemCount).mapNotNull { clipData.getItemAt(it).uri }

                if (uris.isNotEmpty()) {
                    payloadsBeingProcessed.add(payload)

                    findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                        try {
                            UrisShareObject.fromUris(uris)
                                .insertAsync(this@MediaAcceptingEditText, InsertAt.CURRENT_POSITION)
                        } catch (e: Exception) {
                            showSnackbar(R.string.error__etc__could_not_import_data, e)
                        }
                    }
                }
            }

            remaining
        }
    }

    private fun getSuris() : List<Suri> {
        val spans = text?.let { it.getSpans(0, it.length, ShareSpan::class.java) }
        return spans?.map { it.suri }?.toList() ?: emptyList()
    }

    fun getNotReadySuris() : List<Suri> {
        return getSuris().filter { !it.ready }
    }

    fun textifyReadySuris() {
        text?.let {
            for (span in it.getSpans(0, it.length, ShareSpan::class.java)) {
                span.suri.httpUri?.let { httpUri ->
                    val pos = it.getSpanStart(span)
                    it.replace(pos, it.getSpanEnd(span), "")
                    it.removeSpan(span)
                    insertAddingSpacesAsNeeded(pos, httpUri)
                }
            }
        }

        if (!hasShareSpans()) payloadsBeingProcessed.clear()
    }

    private fun hasShareSpans(): Boolean {
        return text?.run { getSpans(0, length, ShareSpan::class.java).isNotEmpty() } == true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val oldLayout = layout
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (oldLayout == null && layout != null) {
            hasLayoutListener?.onHasLayout()
            hasLayoutListener = null
        }
    }

    fun interface HasLayoutListener { fun onHasLayout() }

    var hasLayoutListener: HasLayoutListener? = null

    // text width in pixels. if has more than 1 line, returns Float.MAX_VALUE;
    // if text hasn't been laid out yet, returns -1f
    fun getTextWidth(): Float {
        if (length() == 0) return 0f

        return layout?.let {
            if (it.lineCount < 1) return 0f
            if (it.lineCount > 1) return Float.MAX_VALUE
            return it.getLineWidth(0)
        } ?: -1f
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////// save & restore
    ////////////////////////////////////////////////////////////////////////////////////////////////

    data class ShareSpanInfo(val uri: Uri, val start: Int, val end: Int)

    override fun onSaveInstanceState(): Parcelable? {
        return SavedState(super.onSaveInstanceState()).apply {
            text?.run {
                shareSpans = getSpans(0, length, ShareSpan::class.java).map {
                    ShareSpanInfo(it.suri.uri, getSpanStart(it), getSpanEnd(it))
                }
            }
        }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is SavedState) {
            super.onRestoreInstanceState(state.superState)

            // most of the time TextView will have restored our spans from memory already
            // if so, make sure we don't create them twice
            if (state.shareSpans.isEmpty() || text == null || hasShareSpans()) return

            findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                state.shareSpans.forEach { (uri, start, end) ->
                    launch {
                        try {
                            val suri = Suri.fromUri(uri)
                            val thumbnailSpannable = makeThumbnailSpannable(context, suri)
                            text?.replace(start, end, thumbnailSpannable)
                        } catch (e: Exception) {
                            showSnackbar(R.string.error__etc__while_accessing_uri, e)
                        }
                    }
                }
            }
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    class SavedState : BaseSavedState {
        var shareSpans = listOf<ShareSpanInfo>()

        constructor(source: Parcel) : super(source) {
            shareSpans = (0 until source.readInt()).map {
                ShareSpanInfo(Uri.CREATOR.createFromParcel(source), source.readInt(), source.readInt())
            }
        }

        constructor(superState: Parcelable?) : super(superState)

        override fun writeToParcel(dest: Parcel, flags: Int) {
            super.writeToParcel(dest, flags)
            dest.writeInt(shareSpans.size)
            shareSpans.forEach {
                it.uri.writeToParcel(dest, flags)
                dest.writeInt(it.start)
                dest.writeInt(it.end)
            }
        }

        companion object {
            @Suppress("unused")
            @JvmField val CREATOR = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(source: Parcel) = SavedState(source)
                override fun newArray(size: Int) = arrayOfNulls<SavedState>(size)
            }
        }
    }
}
