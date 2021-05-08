package com.ubergeek42.WeechatAndroid.notifications

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
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
import com.ubergeek42.WeechatAndroid.upload.Config
import com.ubergeek42.WeechatAndroid.upload.applicationContext
import com.ubergeek42.WeechatAndroid.utils.Constants
import com.ubergeek42.WeechatAndroid.utils.Constants.PREF_UPLOAD_ACCEPT_TEXT_AND_MEDIA
import com.ubergeek42.WeechatAndroid.utils.Constants.PREF_UPLOAD_ACCEPT_TEXT_ONLY
import com.ubergeek42.WeechatAndroid.utils.Utils


interface Shortcuts {
    fun reportBufferWasManuallyFocused(statistics: StatisticsImpl)
    fun reportBufferWasSharedTo(statistics: StatisticsImpl)
    fun ensureShortcutExists(key: String)
}


val shortcuts = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ShortcutsImpl(applicationContext)
                } else {
                    object : Shortcuts {
                        override fun reportBufferWasManuallyFocused(statistics: StatisticsImpl) {}
                        override fun reportBufferWasSharedTo(statistics: StatisticsImpl) {}
                        override fun ensureShortcutExists(key: String) {}
                    }
                }


@RequiresApi(30)
private class ShortcutsImpl(val context: Context): Shortcuts {
    private val shortcutManager = context.getSystemService(ShortcutManager::class.java)!!
    val limit = shortcutManager.maxShortcutCountPerActivity

    private fun makeShortcutForBuffer(buffer: Buffer, rank: Int?, shareTarget: Boolean): ShortcutInfoCompat {
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
            .setIsConversation()

        rank?.let {
            builder.setRank(it)
        }

        if (shareTarget) {
            val category = when (Config.uploadAcceptShared) {
                PREF_UPLOAD_ACCEPT_TEXT_ONLY -> "com.ubergeek42.WeechatAndroid.category.BUFFER_TARGET_TEXT"
                PREF_UPLOAD_ACCEPT_TEXT_AND_MEDIA -> "com.ubergeek42.WeechatAndroid.category.BUFFER_TARGET_MEDIA"
                else -> "com.ubergeek42.WeechatAndroid.category.BUFFER_TARGET_EVERYTHING"
            }

            builder.setCategories(setOf(category))
        }

        if (buffer.type == BufferSpec.Type.Private) {
            val person = getPerson(key = buffer.fullName, nick = buffer.shortName)
            builder.setPerson(person)
        }

        return builder.build()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    var shortcuts: Map<String, ShortcutInfo> = fetchShortcuts()

    var launcherShortcuts = shortcuts
        .entries
        .sortedBy { it.value.rank }
        .take(limit)
        .map { it.key }
        .toSet()

    var directShareShortcuts = shortcuts
        .filter { it.value.categories?.isNotEmpty() == true }
        .map { it.key }
        .toSet()

    @SuppressLint("WrongConstant")
    fun fetchShortcuts() =
            shortcutManager.getShortcuts(0xffffff)
                .map { it.id to it }
                .toMap()

    fun updateShortcut(key: String, rank: Int? = null, shareTarget: Boolean? = null) {
        if (key !in shortcuts) {
            BufferList.findByFullName(key)?.let { buffer ->
                val shortcut = makeShortcutForBuffer(buffer, rank, shareTarget ?: false)
                shortcutManager.pushDynamicShortcut(shortcut.toShortcutInfo())
            }
        } else {
            BufferList.findByFullName(key)?.let { buffer ->
                val shortcutInfo = shortcuts[key]!!
                val shortcut = makeShortcutForBuffer(buffer, shortcutInfo.rank, shortcutInfo.categories?.isNotEmpty() == true)
                shortcutManager.pushDynamicShortcut(shortcut.toShortcutInfo())
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun reportBufferWasSharedTo(statistics: StatisticsImpl) {
        val old = directShareShortcuts
        val new = statistics.getMostFrequentlySharedToBuffers(limit).toSet()
        directShareShortcuts = new
        if (old != new) {
            (old - new).forEach { key -> updateShortcut(key, shareTarget = false) }
            (new - old).forEach { key -> updateShortcut(key, shareTarget = true) }
            shortcuts = fetchShortcuts()
        }
    }

    override fun reportBufferWasManuallyFocused(statistics: StatisticsImpl) {
        val old = launcherShortcuts
        val new = statistics.getMostFrequentlyManuallyFocusedBuffers(limit).toSet()
        launcherShortcuts = new
        if (old != new) {
            (old - new).forEach { key -> updateShortcut(key, rank = 1000) }
            (new - old).forEach { key -> updateShortcut(key, rank = 0) }
            shortcuts = fetchShortcuts()
        }
    }

    override fun ensureShortcutExists(key: String) {
        if (key !in shortcuts) {
            BufferList.findByFullName(key)?.let { buffer ->
                val shortcut = makeShortcutForBuffer(buffer, rank = 1000, shareTarget = false)
                shortcutManager.pushDynamicShortcut(shortcut.toShortcutInfo())
                shortcuts = fetchShortcuts()
            }
        }
    }
}

// todo thread
// todo no set()
