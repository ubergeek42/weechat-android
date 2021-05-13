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
   val fullName: String,
   val shortName: String,
   val isPrivate: Boolean,

   val hotCount: Int,
   val messages: List<HotlistMessage>,
   val lastMessageTimestamp: Long,
) {
    companion object {
        fun fromBuffer(buffer: Buffer): HotlistBuffer {
            return HotlistBuffer(
                fullName = buffer.fullName,
                shortName = buffer.shortName,
                isPrivate = buffer.type === BufferSpec.Type.Private,
                hotCount = 0,
                messages = emptyList(),
                lastMessageTimestamp = 0 //System.currentTimeMillis()
            )
        }
    }

    fun updateByBuffer(buffer: Buffer, invalidateMessages: Boolean) {
        val newHotCount = buffer.hotCount
        val newShortName = buffer.shortName

        val updateHotCount = hotCount != newHotCount
        val updateShortName = shortName != newShortName

        if (updateHotCount || updateShortName || (invalidateMessages && messages.isNotEmpty())) {
            //kitty.info("updateHotCount(%s): %s -> %s (invalidate=%s)",
            //            buffer, hotCount, newHotCount, invalidateMessages)

            val messages = when {
                invalidateMessages -> emptyList()
                newHotCount > hotCount -> emptyList()
                messages.size > newHotCount -> messages.subList(messages.size - newHotCount, messages.size)
                else -> messages
            }

            copy(
                shortName = newShortName,
                isPrivate = buffer.type === BufferSpec.Type.Private,
                hotCount = newHotCount,
                messages = messages,
            ).pushUpdate(makeNoise = false)
        }
    }

    fun updateOnHotLine(buffer: Buffer, line: Line) {
        val message = HotlistMessage.fromLine(line, this)

        copy(
            hotCount = hotCount + 1,
            messages = messages + message
        ).pushUpdate(makeNoise = true)

        if (Engine.isEnabledAtAll() && Engine.isEnabledForLocation(Engine.Location.NOTIFICATION) &&
            Engine.isEnabledForLine(line)) {
            ContentUriFetcher.loadFirstUrlFromText(message.message) { imageUri: Uri ->
                val hotBuffer = getHotBuffer(buffer)
                if (hotBuffer.messages.lastOrNull() === message) {
                    message.image = imageUri
                    hotBuffer.pushUpdate()
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


internal var hotlistBuffers = mapOf<String, HotlistBuffer>()


private fun HotlistBuffer.pushUpdate(makeNoise: Boolean = false) {
    hotlistBuffers = (hotlistBuffers + (this.fullName to this))
        .filter { it.value.hotCount > 0 }
        .also { showHotNotification(this, it.values, makeNoise) }
}


////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////// public methods
////////////////////////////////////////////////////////////////////////////////////////////////////


object Hotlist {
    val totalHotMessageCount get() = hotlistBuffers.values.sumOf { it.hotCount }

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
    return hotlistBuffers[buffer.fullName] ?: HotlistBuffer.fromBuffer(buffer)
}


private fun CharSequence.toItalicizedSpannable(): CharSequence {
    return SpannableString(this).also {
        it.setSpan(StyleSpan(Typeface.ITALIC), 0, length, 0)
    }
}
