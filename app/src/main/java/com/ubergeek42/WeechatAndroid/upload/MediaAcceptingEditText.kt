package com.ubergeek42.WeechatAndroid.upload

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ImageSpan
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import com.ubergeek42.WeechatAndroid.Weechat
import com.ubergeek42.WeechatAndroid.utils.ActionEditText
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root
import kotlin.concurrent.thread


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
        val shouldRequestPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1 && lacksPermission;

        if (shouldRequestPermission) {
            try {
                inputContentInfo.requestPermission()
            } catch (e: Exception) {
                return@OnCommitContentListener false
            }
        }

        // https://stackoverflow.com/a/58008340/1449683
        thread {
            val uri = inputContentInfo.contentUri
            val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            val imageSpan = ImageSpan(context, bitmap)

            Weechat.runOnMainThread {
                val newText = SpannableString(TextUtils.concat(text, "?"))
                newText.setSpan(imageSpan, newText.length - 1, newText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setText(newText)
                if (shouldRequestPermission) inputContentInfo.releasePermission()
            }
        }

        true
    }
}