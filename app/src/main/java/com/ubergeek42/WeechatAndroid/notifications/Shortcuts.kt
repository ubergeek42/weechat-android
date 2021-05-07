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
import com.ubergeek42.WeechatAndroid.upload.dp_to_px
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
        val iconBitmap = generateIcon(48.dp_to_px /* shortcutManager.iconMaxWidth */,
                                      48.dp_to_px /* shortcutManager.iconMaxHeight */,
                                      buffer.shortName,
                                      buffer.fullName)

        val intent = Intent(applicationContext, WeechatActivity::class.java).apply {
            putExtra(Constants.EXTRA_BUFFER_POINTER, buffer.pointer)
            action = Utils.pointerToString(buffer.pointer)
        }

        return ShortcutInfo.Builder(context, buffer.fullName)
            .setShortLabel(buffer.shortName)
            .setLongLabel(buffer.shortName)
            .setIcon(Icon.createWithBitmap(iconBitmap))
            .setIntent(intent)
            .setLongLived(true)
            .setLocusId(LocusId(buffer.fullName))
            .build()
    }

    private val pushedShortcuts = mutableSetOf<Long>()

    override fun reportBufferFocused(pointer: Long) {
        val buffer = BufferList.findByPointer(pointer)
        if (buffer != null) {
            if (pointer !in pushedShortcuts) {
                shortcutManager.pushDynamicShortcut(makeShortcutForBuffer(buffer))
                pushedShortcuts.add(pointer)
            }
            shortcutManager.reportShortcutUsed(buffer.fullName)
        }
    }
}
