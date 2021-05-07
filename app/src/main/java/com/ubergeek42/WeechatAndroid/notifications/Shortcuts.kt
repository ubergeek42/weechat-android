package com.ubergeek42.WeechatAndroid.notifications

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.graphics.drawable.IconCompat
import com.ubergeek42.WeechatAndroid.WeechatActivity
import com.ubergeek42.WeechatAndroid.relay.Buffer
import com.ubergeek42.WeechatAndroid.relay.BufferList
import com.ubergeek42.WeechatAndroid.relay.BufferSpec
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

    private fun makeShortcutForBuffer(buffer: Buffer): ShortcutInfoCompat {
        val iconBitmap = generateIcon(text = buffer.shortName, colorKey = buffer.fullName)
        val icon = IconCompat.createWithBitmap(iconBitmap)

        val intent = Intent(applicationContext, WeechatActivity::class.java).apply {
            putExtra(Constants.EXTRA_BUFFER_FULL_NAME, buffer.fullName)
            action = Utils.pointerToString(buffer.pointer)
        }

        val builder = ShortcutInfoCompat.Builder(context, buffer.fullName)
            .setShortLabel(buffer.shortName)
            .setLongLabel(buffer.shortName)
            .setIcon(icon)
            .setIntent(intent)
            .setLongLived(true)
            .setLocusId(LocusIdCompat(buffer.fullName))

        if (buffer.type == BufferSpec.Type.Private) {
            val person = getPerson(key = buffer.fullName, nick = buffer.shortName)
            builder.setPerson(person)
        }

        return builder.build()
    }

    private val pushedShortcuts = mutableSetOf<String>()

    override fun reportBufferFocused(pointer: Long) {
        BufferList.findByPointer(pointer)?.let { buffer ->
            val key = buffer.fullName

            if (key !in pushedShortcuts) {
                shortcutManager.pushDynamicShortcut(makeShortcutForBuffer(buffer).toShortcutInfo())
                pushedShortcuts.add(key)
            }

            shortcutManager.reportShortcutUsed(key)
        }
    }
}
