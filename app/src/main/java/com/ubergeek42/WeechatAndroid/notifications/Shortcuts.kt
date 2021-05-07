package com.ubergeek42.WeechatAndroid.notifications

import android.content.Context
import android.content.Intent
import android.content.LocusId
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import com.ubergeek42.WeechatAndroid.WeechatActivity
import com.ubergeek42.WeechatAndroid.relay.Buffer
import com.ubergeek42.WeechatAndroid.relay.BufferList
import com.ubergeek42.WeechatAndroid.upload.applicationContext
import com.ubergeek42.WeechatAndroid.utils.Constants
import com.ubergeek42.WeechatAndroid.utils.Utils


fun interface ShortcutReporter {
    fun reportBufferFocused(pointer: Long)
}


val shortcuts: ShortcutReporter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                      Shortcuts(applicationContext)
                                  } else {
                                      ShortcutReporter { /* no op */ }
                                  }


@RequiresApi(30)
class Shortcuts(val context: Context): ShortcutReporter {
    private val shortcutManager = context.getSystemService(ShortcutManager::class.java)!!

    private fun makeShortcutForBuffer(buffer: Buffer): ShortcutInfo {
        val iconBitmap = generateIcon(text = buffer.shortName, colorKey = buffer.fullName)
        val icon = Icon.createWithBitmap(iconBitmap)

        val intent = Intent(applicationContext, WeechatActivity::class.java).apply {
            putExtra(Constants.EXTRA_BUFFER_FULL_NAME, buffer.fullName)
            action = Utils.pointerToString(buffer.pointer)
        }

        return ShortcutInfo.Builder(context, buffer.fullName)
            .setShortLabel(buffer.shortName)
            .setLongLabel(buffer.shortName)
            .setIcon(icon)
            .setIntent(intent)
            .setLongLived(true)
            .setLocusId(LocusId(buffer.fullName))
            .build()
    }

    private val pushedShortcuts = mutableSetOf<String>()

    override fun reportBufferFocused(pointer: Long) {
        BufferList.findByPointer(pointer)?.let { buffer ->
            val key = buffer.fullName

            if (key !in pushedShortcuts) {
                shortcutManager.pushDynamicShortcut(makeShortcutForBuffer(buffer))
                pushedShortcuts.add(key)
            }

            shortcutManager.reportShortcutUsed(key)
        }
    }
}
