package com.ubergeek42.WeechatAndroid.upload

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ImageSpan
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import com.ubergeek42.WeechatAndroid.utils.ActionEditText
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root

const val PLACEHOLDER_TEXT = "📎"

class MediaAcceptingEditText : ActionEditText {
    @Root private val kitty = Kitty.make()

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun onCreateInputConnection(editorInfo: EditorInfo): InputConnection {
        val inputConnection: InputConnection = super.onCreateInputConnection(editorInfo)
        EditorInfoCompat.setContentMimeTypes(editorInfo, arrayOf("*/*"))
        return InputConnectionCompat.createWrapper(inputConnection, editorInfo, callback)
    }

    private val callback = InputConnectionCompat.OnCommitContentListener {
            inputContentInfo, flags, _ ->
        val lacksPermission = (flags and InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0
        val shouldRequestPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1 && lacksPermission

        if (shouldRequestPermission) {
            try {
                inputContentInfo.requestPermission()
            } catch (e: Exception) {
                return@OnCommitContentListener false
            }
        }

        val imageSpan = ImageSpan(context, inputContentInfo.contentUri)
        val newText = SpannableString(TextUtils.concat(text, PLACEHOLDER_TEXT))
        newText.setSpan(imageSpan,
                        newText.length - PLACEHOLDER_TEXT.length,
                        newText.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        setText(newText)

        if (shouldRequestPermission) inputContentInfo.releasePermission()

        true
    }
}

const val HEIGHT = 200

class SensibleImageSpan(context: Context, uri: Uri) : ImageSpan(context, uri) {
    override fun getDrawable(): Drawable {
        val drawable = super.getDrawable()
        val width = drawable.intrinsicWidth
        val height = drawable.intrinsicHeight
        val nw = HEIGHT.toFloat() / height * width
        drawable.setBounds(0, 0, nw.toInt(), HEIGHT)
        return drawable
    }
}

interface ShareObject {
    fun insertAt(editText: EditText, cursorPosition: Int)

    fun insertAtEnd(editText: EditText) {
        insertAt(editText, editText.text.length)
    }
}

data class TextShareObject(val text: CharSequence) : ShareObject {
    override fun insertAt(editText: EditText, cursorPosition: Int) {
        val originalText = editText.text
        editText.setText(TextUtils.concat(
                originalText.subSequence(0, cursorPosition),
                text,
                originalText.subSequence(cursorPosition, originalText.length)))
    }
}

@Suppress("ArrayInDataClass")
data class UrisShareObject(val type: String?, val uris: List<Uri>) : ShareObject {
    constructor(type: String?, uri: Uri) : this(type, listOf(uri))

    override fun insertAt(editText: EditText, cursorPosition: Int) {
        var text = editText.text as CharSequence
        var currentCursorPosition = cursorPosition
        for (uri in uris) {
            text = insertAt(editText.context, text, currentCursorPosition, uri)
            currentCursorPosition += PLACEHOLDER_TEXT.length
        }
        editText.setText(text)
    }

    private fun insertAt(context: Context, originalText: CharSequence,
                         cursorPosition: Int, uri: Uri) : CharSequence {
        val newText = SpannableString(TextUtils.concat(
                originalText.subSequence(0, cursorPosition),
                PLACEHOLDER_TEXT,
                originalText.subSequence(cursorPosition, originalText.length)))
        val imageSpan = SensibleImageSpan(context, uri)
        newText.setSpan(imageSpan,
                newText.length - PLACEHOLDER_TEXT.length,
                newText.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return newText
    }
}
