package com.ubergeek42.WeechatAndroid.notifications

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
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
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root


private val USE_SHORTCUTS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1


@Root private val kitty: Kitty = Kitty.make("Shortcuts")


interface Shortcuts {
    fun reportBufferWasManuallyFocused(focusedKey: String, statistics: StatisticsImpl)
    fun reportBufferWasSharedTo(focusedKey: String, statistics: StatisticsImpl)
    fun ensureShortcutExists(key: String)
    fun updateShortcutNameIfNeeded(buffer: Buffer)
    fun updateDirectShareCount()
    fun removeAllShortcuts()
}


val shortcuts = if (USE_SHORTCUTS) {
                    ShortcutsImpl(applicationContext)
                } else {
                    object : Shortcuts {
                        override fun reportBufferWasManuallyFocused(focusedKey: String, statistics: StatisticsImpl) {}
                        override fun reportBufferWasSharedTo(focusedKey: String, statistics: StatisticsImpl) {}
                        override fun ensureShortcutExists(key: String) {}
                        override fun updateShortcutNameIfNeeded(buffer: Buffer) {}
                        override fun updateDirectShareCount() {}
                        override fun removeAllShortcuts() {}
                    }
                }


@RequiresApi(Build.VERSION_CODES.N_MR1)
private class ShortcutsImpl(val context: Context): Shortcuts {
    private val shortcutManager = context.getSystemService(ShortcutManager::class.java)!!

    // maxShortcutCountPerActivity returns 15 on my device,
    // while the launcher only shows 4 shortcuts.
    // having many shortcuts means we have to reorder them frequently;
    // to avoid that, limit the number to the maximum number from the documentation
    // note that this doesn't limit the *total* number of shortcuts;
    // a launcher can still show more than 5, but these would be in launcher's preferred order
    val launcherShortcutLimit = minOf(shortcutManager.maxShortcutCountPerActivity, 5)

    val directShareShortcutLimit get() = minOf(launcherShortcutLimit, Config.noOfDirectShareTargets)

    private fun makeShortcutForBuffer(buffer: Buffer, rank: Int?, shareTarget: Boolean): ShortcutInfoCompat {
        // note: pushDynamicShortcut doesn't support data type icons, throwing
        // IllegalArgumentException: Unsupported icon type: only the bitmap and resource types are supported
        // it also doesn't do URIs as these require passing permissions to the launcher,
        // which we can't do with icons
        val icon = obtainAdaptiveIcon(text = buffer.shortName,
                                      colorKey = buffer.fullName,
                                      allowUriIcons = false)

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
            builder.setPerson(getPersonByPrivateBuffer(buffer))
        }

        return builder.build()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @SuppressLint("WrongConstant")
    fun fetchShortcuts() = ShortcutManagerCompat
            .getShortcuts(applicationContext, 0xffffff)
            .map { it.id to it }
            .toMap()

    fun Map<String, ShortcutInfoCompat>.getLauncherShortcutKeys() = entries
            .sortedBy { it.value.rank }
            .take(launcherShortcutLimit)
            .map { it.key }

    fun Map<String, ShortcutInfoCompat>.getDirectShareShortcutKeys() =
            filter { it.value.categories?.isNotEmpty() == true }
            .map { it.key }

    var shortcuts = fetchShortcuts()
    var launcherShortcuts = shortcuts.getLauncherShortcutKeys()
    var directShareShortcuts = shortcuts.getDirectShareShortcutKeys()

    fun updateShortcut(key: String, rank: Int? = null, shareTarget: Boolean? = null) {
        BufferList.findByFullName(key)?.let { buffer ->
            val shortcut = if (key !in shortcuts) {
                makeShortcutForBuffer(buffer, rank, shareTarget ?: false)
            } else {
                val shortcutInfo = shortcuts[key]!!
                makeShortcutForBuffer(buffer,
                    rank ?: shortcutInfo.rank,
                    shareTarget ?: shortcutInfo.categories?.isNotEmpty() == true)
            }
            ShortcutManagerCompat.pushDynamicShortcut(applicationContext, shortcut)
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    fun updateDirectShareShortcuts(statistics: StatisticsImpl) {
        val old = directShareShortcuts.toSet()
        val new = statistics.getMostFrequentlySharedToBuffers(directShareShortcutLimit).toSet()
        if (old != new) {
            kitty.trace("updating direct share shortcuts: %s → %s", old, new)
            (old - new).forEach { key -> updateShortcut(key, shareTarget = false) }
            (new - old).forEach { key -> updateShortcut(key, shareTarget = true) }
            shortcuts = fetchShortcuts()
            directShareShortcuts = shortcuts.getDirectShareShortcutKeys()
        }
    }

    override fun reportBufferWasSharedTo(focusedKey: String, statistics: StatisticsImpl) {
        updateDirectShareShortcuts(statistics)
        shortcutManager.reportShortcutUsed(focusedKey)
    }

    override fun reportBufferWasManuallyFocused(focusedKey: String, statistics: StatisticsImpl) {
        val old = launcherShortcuts
        val new = statistics.getMostFrequentlyManuallyFocusedBuffers(launcherShortcutLimit)
        if (old != new) {
            kitty.trace("updating launcher shortcuts: %s → %s", old, new)
            val oldSansNew = old - new
            val oldSansOldSansNew = old - oldSansNew
            oldSansNew.forEach { key -> updateShortcut(key, rank = 10000) }
            if (new != oldSansOldSansNew) {
                new.forEachIndexed { index, key -> updateShortcut(key, rank = index) }
            }
            shortcuts = fetchShortcuts()
            launcherShortcuts = new     // getLauncherShortcutKeys() might yield more items than this
        }
        shortcutManager.reportShortcutUsed(focusedKey)
    }

    override fun ensureShortcutExists(key: String) {
        if (key !in shortcuts) {
            BufferList.findByFullName(key)?.let { buffer ->
                val shortcut = makeShortcutForBuffer(buffer, rank = 10000, shareTarget = false)
                ShortcutManagerCompat.pushDynamicShortcut(applicationContext, shortcut)
                shortcuts = fetchShortcuts()
            }
        }
    }

    override fun updateShortcutNameIfNeeded(buffer: Buffer) {
        shortcuts[buffer.fullName]?.let {
            if (it.shortLabel != buffer.shortName) {
                updateShortcut(buffer.fullName)
                shortcuts = fetchShortcuts()
            }
        }
    }

    override fun updateDirectShareCount() {
        if (BufferList.buffers.isEmpty()) return    // we need a buffer object to make a shortcut
        (statistics as? StatisticsImpl)?.let { updateDirectShareShortcuts(it) }
    }

    @SuppressLint("WrongConstant")
    override fun removeAllShortcuts() {
        ShortcutManagerCompat.removeAllDynamicShortcuts(applicationContext)
        val allShortcuts = ShortcutManagerCompat.getShortcuts(applicationContext, 0xffffff)
        ShortcutManagerCompat.removeLongLivedShortcuts(applicationContext, allShortcuts.map { it.id })
    }
}
