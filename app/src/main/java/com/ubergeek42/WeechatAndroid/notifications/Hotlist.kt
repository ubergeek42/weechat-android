// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
package com.ubergeek42.WeechatAndroid.notifications

import android.graphics.Typeface
import android.net.Uri
import android.text.SpannableString
import android.text.style.StyleSpan
import com.ubergeek42.WeechatAndroid.media.ContentUriFetcher
import com.ubergeek42.WeechatAndroid.media.Engine
import com.ubergeek42.WeechatAndroid.relay.Buffer
import com.ubergeek42.WeechatAndroid.relay.BufferList
import com.ubergeek42.WeechatAndroid.relay.BufferSpec
import com.ubergeek42.WeechatAndroid.relay.Line
import com.ubergeek42.WeechatAndroid.relay.LineSpec
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root


@Root private val kitty = Kitty.make("Hotlist") as Kitty


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


data class HotlistBuffer(
    val pointer: Long,
    val fullName: String,
    val shortName: String,
    val isPrivate: Boolean,

    val hotCount: Int,                      // as per WeeChat's hotlist
    val messages: List<HotlistMessage>,     // messages we've seen. might be less than above
) {
    // timestamp of the last seen message, or 0 if we don't have this information
    val lastMessageTimestamp get() = messages.lastOrNull()?.timestamp ?: 0

    companion object {
        fun fromBuffer(buffer: Buffer): HotlistBuffer {
            return HotlistBuffer(
                pointer = buffer.pointer,
                fullName = buffer.fullName,
                shortName = buffer.shortName,
                isPrivate = buffer.type === BufferSpec.Type.Private,
                hotCount = 0,
                messages = emptyList(),
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
    fun updateByBuffer(buffer: Buffer, invalidateMessages: Boolean) {
        val newHotCount = buffer.hotCount
        val newFullName = buffer.fullName
        val newShortName = buffer.shortName

        val updateHotCount = hotCount != newHotCount
        val updateFullName = fullName != newFullName
        val updateShortName = shortName != newShortName

        if (updateHotCount || updateFullName || updateShortName ||
                (invalidateMessages && messages.isNotEmpty())) {
            val messages = when {
                invalidateMessages -> emptyList()
                newHotCount > hotCount -> emptyList()
                messages.size > newHotCount -> messages.subList(messages.size - newHotCount, messages.size)
                else -> messages
            }

            copy(
                fullName = newFullName,
                shortName = newShortName,
                isPrivate = buffer.type == BufferSpec.Type.Private,
                hotCount = newHotCount,
                messages = messages,
            ).pushUpdate(newMessageAsync = newHotCount > hotCount)
        }
    }

    fun updateOnHotLine(buffer: Buffer, line: Line) {
        val message = HotlistMessage.fromLine(line, this)

        copy(
            hotCount = hotCount + 1,
            messages = messages + message,
        ).pushUpdate(newMessage = true)

        if (Engine.isEnabledAtAll() && Engine.isEnabledForLocation(Engine.Location.NOTIFICATION) &&
            Engine.isEnabledForLine(line)) {
            ContentUriFetcher.loadFirstUrlFromText(message.message) { imageUri: Uri ->
                notificationHandler.post {
                    val hotBuffer = getHotBuffer(buffer)
                    if (hotBuffer.messages.lastOrNull() === message) {
                        message.image = imageUri
                        hotBuffer.pushUpdate()
                    }
                }
            }
        }
    }

    fun clear() {
        if (hotCount != 0) {
            copy(hotCount = 0, messages = emptyList()).pushUpdate()
        }
    }
}


////////////////////////////////////////////////////////////////////////////////////////////////////


internal var hotlistBuffers = mapOf<Long, HotlistBuffer>()


private fun HotlistBuffer.pushUpdate(
    newMessage: Boolean = false,
    newMessageAsync: Boolean = false
) {
    hotlistBuffers = (hotlistBuffers + (this.pointer to this))
        .filter { it.value.hotCount > 0 }
        .also {
            when {
                newMessage -> showHotNotification(it.values, this)
                newMessageAsync -> showHotAsyncNotification(it.values, this)
                this.hotCount > 0 -> updateHotNotification(this, it.values)
                else -> filterNotifications(it.values)
            }
        }
}


////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////// public methods
////////////////////////////////////////////////////////////////////////////////////////////////////


object Hotlist {
    fun reportNewHotLine(buffer: Buffer, line: Line) {
        notificationHandler.post {
            getHotBuffer(buffer).updateOnHotLine(buffer, line)
        }
    }

    fun adjustHotListForBuffer(buffer: Buffer, invalidateMessages: Boolean) {
        notificationHandler.post {
            getHotBuffer(buffer).updateByBuffer(buffer, invalidateMessages)
        }
    }

    fun makeSureHotlistDoesNotContainInvalidBuffers() {
        notificationHandler.post {
            hotlistBuffers.values.forEach {
                if (BufferList.findByFullName(it.fullName) == null) it.clear()
            }
        }
    }
}


////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////


private fun getHotBuffer(buffer: Buffer): HotlistBuffer {
    return hotlistBuffers[buffer.pointer] ?: HotlistBuffer.fromBuffer(buffer)
}


private fun CharSequence.toItalicizedSpannable(): CharSequence {
    return SpannableString(this).also {
        it.setSpan(StyleSpan(Typeface.ITALIC), 0, length, 0)
    }
}


// this is only used to push a suppressed bubble notification
// todo check if making a fake notification would be sufficient
fun getHotBuffer(pointer: Long): HotlistBuffer? {
    hotlistBuffers[pointer]?.let { return it }
    return BufferList.findByPointer(pointer)?.let { HotlistBuffer.fromBuffer(it) }
}
