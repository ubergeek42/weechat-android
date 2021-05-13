// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
package com.ubergeek42.WeechatAndroid.relay

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.StyleSpan
import androidx.annotation.MainThread
import androidx.core.app.RemoteInput
import com.ubergeek42.WeechatAndroid.media.ContentUriFetcher
import com.ubergeek42.WeechatAndroid.media.Engine
import com.ubergeek42.WeechatAndroid.notifications.HotNotification
import com.ubergeek42.WeechatAndroid.notifications.KEY_TEXT_REPLY
import com.ubergeek42.WeechatAndroid.relay.BufferList.findByPointer
import com.ubergeek42.WeechatAndroid.service.Events
import com.ubergeek42.WeechatAndroid.utils.Toaster
import com.ubergeek42.WeechatAndroid.utils.Utils
import com.ubergeek42.cats.Cat
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root
import java.util.*
import java.util.concurrent.atomic.AtomicInteger


class HotlistMessage(
    val hotBuffer: Hotlist.HotBuffer,
    val timestamp: Long,
    val nick: CharSequence,
    val message: CharSequence,
    val isAction: Boolean,
    var image: Uri? = null,
) {
    companion object {
        fun fromLine(line: Line, hotBuffer: Hotlist.HotBuffer): HotlistMessage {
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


object Hotlist {
    @Root private val kitty = Kitty.make() as Kitty

    @SuppressLint("UseSparseArrays")
    private val hotList = HashMap<Long, HotBuffer>()
    private val totalHotCount = AtomicInteger(0)

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////// public methods
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // the only purpose of this is to show/hide the action button when connecting/disconnecting
    private var connected = false

    @JvmStatic @Cat @Synchronized fun redraw(connected: Boolean) {
        if (Hotlist.connected == connected) return
        Hotlist.connected = connected
        for (buffer in hotList.values) notifyHotlistChanged(buffer, NotifyReason.REDRAW)
    }

    @Cat @Synchronized fun onNewHotLine(buffer: Buffer, line: Line) {
        getHotBuffer(buffer).onNewHotLine(buffer, line)
    }

    @Synchronized fun adjustHotListForBuffer(buffer: Buffer, invalidateMessages: Boolean) {
        getHotBuffer(buffer).updateHotCount(buffer, invalidateMessages)
    }

    @Cat @Synchronized fun makeSureHotlistDoesNotContainInvalidBuffers() {
        val it: MutableIterator<Map.Entry<Long, HotBuffer>> = hotList.entries.iterator()
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
    private fun getHotBuffer(buffer: Buffer): HotBuffer {
        return hotList.getOrPut(buffer.pointer) { HotBuffer(buffer) }
    }

    enum class NotifyReason {
        HOT_SYNC, HOT_ASYNC, REDRAW
    }

    private fun notifyHotlistChanged(buffer: HotBuffer, reason: NotifyReason) {
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

    private fun getMessageText(intent: Intent): CharSequence? {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        return remoteInput?.getCharSequence(KEY_TEXT_REPLY)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////// classes
    ////////////////////////////////////////////////////////////////////////////////////////////////

    class HotBuffer internal constructor(buffer: Buffer) {
        var isPrivate: Boolean
        val pointer: Long
        var shortName: String
        val fullName: String
        val messages = ArrayList<HotlistMessage>()
        @JvmField var hotCount = 0
        var lastMessageTimestamp = System.currentTimeMillis()

        init {
            isPrivate = buffer.type === BufferSpec.Type.Private
            pointer = buffer.pointer
            shortName = buffer.shortName
            fullName = buffer.fullName
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
            val updatingHotCount = hotCount != newHotCount
            val updatingShortName = shortName != buffer.shortName
            if (!updatingHotCount && !updatingShortName && !(invalidateMessages && messages.size > 0)) return
            kitty.info("updateHotCount(%s): %s -> %s (invalidate=%s)", buffer, hotCount, newHotCount, invalidateMessages)
            if (invalidateMessages) {
                messages.clear()
            } else if (updatingHotCount) {
                if (newHotCount > hotCount) {
                    messages.clear()
                } else {
                    val toRemove = messages.size - newHotCount
                    if (toRemove >= 0) messages.subList(0, toRemove).clear()
                }
            }
            isPrivate = buffer.type === BufferSpec.Type.Private
            if (updatingHotCount) setHotCount(newHotCount)
            if (updatingShortName) shortName = buffer.shortName
            notifyHotlistChanged(this, NotifyReason.HOT_ASYNC)
        }

        fun onNewHotLine(buffer: Buffer, line: Line) {
            setHotCount(hotCount + 1)
            shortName = buffer.shortName
            isPrivate = buffer.type === BufferSpec.Type.Private
            val message = HotlistMessage.fromLine(line, this)
            messages.add(message)
            notifyHotlistChanged(this, NotifyReason.HOT_SYNC)

            if (Engine.isEnabledAtAll() && Engine.isEnabledForLocation(Engine.Location.NOTIFICATION) && Engine.isEnabledForLine(line)) {
                ContentUriFetcher.loadFirstUrlFromText(message.message) { imageUri: Uri? ->
                    message.image = imageUri
                    notifyHotlistChanged(this, NotifyReason.REDRAW)
                }
            }
        }

        fun clear() {
            if (hotCount == 0) return
            setHotCount(0)
            messages.clear()
            notifyHotlistChanged(this, NotifyReason.REDRAW)
        }

        private fun setHotCount(newHotCount: Int) {
            lastMessageTimestamp = System.currentTimeMillis()
            totalHotCount.addAndGet(newHotCount - hotCount)
            hotCount = newHotCount
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////




    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////// remote input receiver
    ////////////////////////////////////////////////////////////////////////////////////////////////

    class InlineReplyReceiver : BroadcastReceiver() {
        @MainThread override fun onReceive(context: Context, intent: Intent) {
            val strPointer = intent.action
            val pointer = Utils.pointerFromString(intent.action)
            val input = getMessageText(intent)
            val buffer = findByPointer(pointer)
            if (TextUtils.isEmpty(input) || buffer == null || !connected) {
                kitty.error("error while receiving remote input: pointer=%s, input=%s, " +
                            "buffer=%s, connected=%s", strPointer, input, buffer, connected)
                Toaster.ErrorToast.show("Error while receiving remote input")
                return
            }
            Events.SendMessageEvent.fireInput(buffer, input.toString())
            buffer.flagResetHotMessagesOnNewOwnLine = true
        }
    }
}


fun CharSequence.toItalicizedSpannable(): CharSequence {
    return SpannableString(this).also {
        it.setSpan(StyleSpan(Typeface.ITALIC), 0, length, 0)
    }
}