package com.ubergeek42.WeechatAndroid.utils

import android.annotation.SuppressLint
import android.os.Build
import android.text.Spannable
import androidx.emoji2.text.EmojiCompat
import com.ubergeek42.WeechatAndroid.upload.suppress
import java.lang.IllegalStateException


// Android 12 and above can update emoji font at any time, while 11 and below
// only receives emoji updates along with system updates.
// AppCompat 1.4+ automatically adds support for new emoji for its views,
// but custom views should process the text manually.
val SHOULD_EMOJIFY = Build.VERSION.SDK_INT < Build.VERSION_CODES.S


// Must be called after EmojiCompat.init(), which is normally called by
// EmojiCompatInitializer around application OnCreate.
private val emojiCompat = EmojiCompat.get()


// Add spans with missing emojis.
// Calling process() returns the given input if it is Spannable.
//
// While we can always get an EmojiCompat instance after initializing it,
// it may take some time to load the font, and in some rare cases
// the app will want to emojify before the font has loaded. While in such a state,
// calling process() will throw IllegalStateException("Not initialized yet").
//
// This can be triggered by:
//   * opening a buffer
//   * minimizing and killing the app
//   * starting the app by tapping on its icon
//
// There are workarounds, but the above state occurs rarely,
// and the impact of not having the latest emojis is rather minimal.
// So, simply suppress the exception.
@SuppressLint("CheckResult")
fun emojify(text: Spannable) {
    suppress<IllegalStateException> {
        emojiCompat.process(text)
    }
}
