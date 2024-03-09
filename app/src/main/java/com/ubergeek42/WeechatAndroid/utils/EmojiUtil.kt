package com.ubergeek42.WeechatAndroid.utils

import android.annotation.SuppressLint
import android.os.Build
import android.text.Spannable
import androidx.emoji2.text.DefaultEmojiCompatConfig
import androidx.emoji2.text.EmojiCompat
import androidx.emoji2.text.EmojiCompat.InitCallback
import androidx.emoji2.text.EmojiCompat.LOAD_STRATEGY_MANUAL
import androidx.emoji2.text.FontRequestEmojiCompatConfig.ExponentialBackoffRetryPolicy
import com.ubergeek42.WeechatAndroid.upload.applicationContext
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root
import kotlin.concurrent.thread

@Root private val kitty = Kitty.make("EmojiUtil")


// Android 12 and above can update emoji font at any time, while 11 and below
// only receives emoji updates along with system updates.
// AppCompat 1.4+ automatically adds support for new emoji for its views,
// but custom views should process the text manually.
@JvmField val SHOULD_EMOJIFY = Build.VERSION.SDK_INT < Build.VERSION_CODES.S


// See https://developer.android.com/develop/ui/views/text-and-emoji/emoji2#optional-features
fun initEmojiCompat() {
    val config = DefaultEmojiCompatConfig.create(applicationContext)

    if (config == null) {
        kitty.error("Could not create DefaultEmojiCompatConfig: no font provider found")
        return
    }

    var emojiCompat: EmojiCompat? = null

    config.setReplaceAll(true)
    config.setRetryPolicy(ExponentialBackoffRetryPolicy(5 * 60 * 1000))
    config.setMetadataLoadStrategy(LOAD_STRATEGY_MANUAL)
    config.registerInitCallback(object : InitCallback() {
        override fun onInitialized() {
            kitty.info("EmojiCompat initialized")
            emojiCompatOrNull = emojiCompat
        }

        override fun onFailed(throwable: Throwable?) {
            kitty.error("EmojiCompat initialization failed", throwable)
        }
    })

    emojiCompat = EmojiCompat.init(config)

    thread { emojiCompat.load() }
}


// Fully initialized and loaded EmojiCompat,
// or null if still initializing or if no suitable font providers found.
@Volatile private var emojiCompatOrNull: EmojiCompat? = null


// Replace old and missing emojis with modern emojis
@SuppressLint("CheckResult") // Calling process() returns the given input if it is Spannable.
fun emojify(text: Spannable) {
    emojiCompatOrNull?.process(text)
}
