package com.ubergeek42.WeechatAndroid.upload

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.text.Spanned
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import com.ubergeek42.WeechatAndroid.utils.ActionEditText
import com.ubergeek42.WeechatAndroid.utils.Toaster.Companion.ErrorToast
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root


class MediaAcceptingEditText : ActionEditText {
    @Root private val kitty = Kitty.make()

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun onCreateInputConnection(editorInfo: EditorInfo): InputConnection? {
        val inputConnection = super.onCreateInputConnection(editorInfo) ?: return null
        EditorInfoCompat.setContentMimeTypes(editorInfo, arrayOf("*/*", "image/*", "image/png", "image/gif", "image/jpeg"))
        return InputConnectionCompat.createWrapper(inputConnection, editorInfo, callback)
    }

    private val callback = InputConnectionCompat.OnCommitContentListener { inputContentInfo, flags, _ ->
        val lacksPermission = (flags and InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0
        val shouldRequestPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1 && lacksPermission

        if (shouldRequestPermission) {
            try {
                // todo release the permission at some point?
                inputContentInfo.requestPermission()
            } catch (e: Exception) {
                kitty.error("Failed to acquire permission for %s", inputContentInfo.description, e)
                return@OnCommitContentListener false
            }
        }

        try {
            UrisShareObject.fromUris(listOf(inputContentInfo.contentUri)).insert(this, InsertAt.CURRENT_POSITION)
            true
        } catch(e:Exception) {
            kitty.error("Error while accessing uri", e)
            ErrorToast.show(e)
            false
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
                    it.replace(it.getSpanStart(span), it.getSpanEnd(span), httpUri)
                    it.removeSpan(span)
                }
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////// save & restore
    ////////////////////////////////////////////////////////////////////////////////////////////////

    class ShareSpanInfo(val uri: Uri, val start: Int, val end: Int)

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
            state.shareSpans.forEach {
                suppress<Exception>(showToast = true) {
                    val suri = Suri.fromUri(it.uri)
                    getThumbnailAndThen(context, it.uri) { bitmap ->
                        val span = if (bitmap != NO_BITMAP)
                            BitmapShareSpan(suri, bitmap) else NonBitmapShareSpan(suri)
                        suppress<Exception>(showToast = true) {
                            text?.setSpan(span, it.start, it.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
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
