// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
package com.ubergeek42.WeechatAndroid.relay

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.net.Uri
import android.text.SpannableString
import android.text.style.StyleSpan
import com.ubergeek42.WeechatAndroid.media.ContentUriFetcher
import com.ubergeek42.WeechatAndroid.media.Engine
import com.ubergeek42.WeechatAndroid.notifications.HotNotification
import com.ubergeek42.WeechatAndroid.relay.BufferList.findByPointer
import com.ubergeek42.cats.Cat
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root
import java.util.*
import java.util.concurrent.atomic.AtomicInteger


@Root private val kitty = Kitty.make("Hotlist") as Kitty


val totalHotCount = AtomicInteger(0)


enum class NotifyReason {
    HotSync, HotAsync, Redraw
}


////////////////////////////////////////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////////////////// message
////////////////////////////////////////////////////////////////////////////////////////////////////


class HotlistMessage(
    val hotBuffer: HotlistBuffer,
    val timestamp: Long,
    val nick: CharSequence,
    val message: CharSequence,
    val isAction: Boolean,
    var image: Uri? = null,
) {
    companion object {
        fun fromLine(line: Line, hotBuffer: HotlistBuffer): HotlistMessage {
            val isAction = line.displayAs === LineSpec.DisplayAs.Action
            val message = line.messageString.let { if (isAction) it.toItalicizedSpannable() else it }
            val nick = line.nick ?: line.prefixString

            return HotlistMessage(hotBuffer = hotBuffer,
                                  timestamp = line.timestamp,
                                  nick = nick,
                                  message = message,
                                  isAction = isAction)
        }
    }
}


////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////// buffer
////////////////////////////////////////////////////////////////////////////////////////////////////

class HotlistBuffer (
    val fullName: String,
    var shortName: String,
    var isPrivate: Boolean,
) {
    var messages = mutableListOf<HotlistMessage>()
    var lastMessageTimestamp = System.currentTimeMillis()  // todo

    var hotCount: Int = 0
        set(value) {
            lastMessageTimestamp = System.currentTimeMillis()
            totalHotCount.addAndGet(value - field)
            field = value
        }

    companion object {
        fun fromBuffer(buffer: Buffer): HotlistBuffer {
            return HotlistBuffer(
                fullName = buffer.fullName,
                shortName = buffer.shortName,
                isPrivate = buffer.type === BufferSpec.Type.Private
            )
        }
    }

    // if hot count has changed — either:
    //  * the buffer was closed or read (hot count == 0)
    //  * (hotlist) brought us new numbers (See Buffer.updateHotlist()). that would happen if:
    //      * the buffer was read in weechat (new number is lower or higher (only if not
    //        syncing). if we kept syncing (invalidateMessages == false), new number is going to
    //        be lower and the messages will still be valid, so we can simply truncate them. if
    //        we weren't syncing, invalidateMessages will be true.
    //      * the buffer wasn't read in weechat, and the new number is higher. while the
    //        messages are valid, the list now looks something like this:
    //            ? ? ? <message> <message> ?
    //        instead of displaying it like this, let's clear it for the sake of simplicity
    fun updateHotCount(buffer: Buffer, invalidateMessages: Boolean) {
        val newHotCount = buffer.hotCount
        val newShortName = buffer.shortName
        val updateHotCount = hotCount != newHotCount
        val updateShortName = shortName != newShortName

        if (updateHotCount || updateShortName || (invalidateMessages && messages.size > 0)) {
            kitty.info(
                "updateHotCount(%s): %s -> %s (invalidate=%s)",
                buffer, hotCount, newHotCount, invalidateMessages
            )

            if (invalidateMessages) {
                messages.clear()
            } else if (updateHotCount) {
                if (newHotCount > hotCount) {
                    messages.clear()
                } else {
                    val toRemove = messages.size - newHotCount
                    if (toRemove >= 0) messages.subList(0, toRemove).clear()
                }
            }

            isPrivate = buffer.type === BufferSpec.Type.Private
            shortName = newShortName
            if (updateHotCount) hotCount = newHotCount

            Hotlist.notifyHotlistChanged(this, NotifyReason.HotAsync)
        }
    }

    fun onNewHotLine(buffer: Buffer, line: Line) {
        hotCount++
        shortName = buffer.shortName
        isPrivate = buffer.type === BufferSpec.Type.Private
        val message = HotlistMessage.fromLine(line, this)
        messages.add(message)

        Hotlist.notifyHotlistChanged(this, NotifyReason.HotSync)

        if (Engine.isEnabledAtAll() && Engine.isEnabledForLocation(Engine.Location.NOTIFICATION) &&
                Engine.isEnabledForLine(line)) {
            ContentUriFetcher.loadFirstUrlFromText(message.message) { imageUri: Uri ->
                message.image = imageUri
                Hotlist.notifyHotlistChanged(this, NotifyReason.Redraw)
            }
        }
    }

    fun clear() {
        if (hotCount != 0) {
            hotCount = 0
            messages.clear()
            Hotlist.notifyHotlistChanged(this, NotifyReason.Redraw)
        }
    }
}


object Hotlist {
    @SuppressLint("UseSparseArrays")
    private val hotList = HashMap<Long, HotlistBuffer>()
    private val totalHotCount = AtomicInteger(0)

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////// public methods
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // the only purpose of this is to show/hide the action button when connecting/disconnecting
    var connected = false

    @JvmStatic @Cat @Synchronized fun redraw(connected: Boolean) {
        if (Hotlist.connected == connected) return
        Hotlist.connected = connected
        for (buffer in hotList.values) notifyHotlistChanged(buffer, NotifyReason.Redraw)
    }

    @Cat @Synchronized fun onNewHotLine(buffer: Buffer, line: Line) {
        getHotBuffer(buffer).onNewHotLine(buffer, line)
    }

    @Synchronized fun adjustHotListForBuffer(buffer: Buffer, invalidateMessages: Boolean) {
        getHotBuffer(buffer).updateHotCount(buffer, invalidateMessages)
    }

    @Cat @Synchronized fun makeSureHotlistDoesNotContainInvalidBuffers() {
        val it: MutableIterator<Map.Entry<Long, HotlistBuffer>> = hotList.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            if (findByPointer(entry.key) == null) {
                entry.value.clear()
                it.remove()
            }
        }
    }

    val hotCount get() = totalHotCount.get()

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // creates hot buffer if not present. note that buffers that lose hot messages aren't removed!
    // todo ???
    private fun getHotBuffer(buffer: Buffer): HotlistBuffer {
        return hotList.getOrPut(buffer.pointer) { HotlistBuffer.fromBuffer(buffer) }
    }

    fun notifyHotlistChanged(buffer: HotlistBuffer, reason: NotifyReason) {
        val allMessages = ArrayList<HotlistMessage>()
        var hotBufferCount = 0
        var lastMessageTimestamp: Long = 0

        synchronized(Hotlist::class.java) {
            for (b in hotList.values) {
                if (b.hotCount == 0) continue
                hotBufferCount++
                allMessages.addAll(b.messages)
                if (b.lastMessageTimestamp > lastMessageTimestamp) lastMessageTimestamp =
                    b.lastMessageTimestamp
            }
        }

        // older messages come first
        allMessages.sortWith { m1: HotlistMessage, m2: HotlistMessage ->
            m1.timestamp.compareTo(m2.timestamp)
        }

        HotNotification(
            connected,
            totalHotCount.get(),
            hotBufferCount,
            allMessages,
            buffer,
            reason,
            lastMessageTimestamp
        ).show()
    }
}


fun CharSequence.toItalicizedSpannable(): CharSequence {
    return SpannableString(this).also {
        it.setSpan(StyleSpan(Typeface.ITALIC), 0, length, 0)
    }
}
