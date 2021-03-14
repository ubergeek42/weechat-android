// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
package com.ubergeek42.WeechatAndroid.relay

import android.util.LongSparseArray
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import com.ubergeek42.WeechatAndroid.service.Events.SendMessageEvent
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.WeechatAndroid.service.RelayService
import com.ubergeek42.WeechatAndroid.utils.Assert
import com.ubergeek42.WeechatAndroid.utils.isAnyOf
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root
import com.ubergeek42.weechat.relay.RelayMessageHandler
import com.ubergeek42.weechat.relay.protocol.Array
import com.ubergeek42.weechat.relay.protocol.Hashtable
import com.ubergeek42.weechat.relay.protocol.Hdata
import com.ubergeek42.weechat.relay.protocol.HdataEntry
import com.ubergeek42.weechat.relay.protocol.RelayObject
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object BufferList {
    @Root private val kitty: Kitty = Kitty.make()

    @Volatile var relay: RelayService? = null
    @Volatile private var buffersEye: BufferListEye? = null

    @JvmField val buffers = CopyOnWriteArrayList<Buffer>()

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @JvmStatic @WorkerThread fun launch(relay: RelayService?) {
        Assert.assertThat(BufferList.relay).isNull()
        BufferList.relay = relay

        // handle buffer list changes
        // including initial hotlist
        addMessageHandler("listbuffers", bufferListWatcher)
        addMessageHandler("renumber", bufferListWatcher)
        addMessageHandler("_buffer_opened", bufferListWatcher)
        addMessageHandler("_buffer_renamed", bufferListWatcher)
        addMessageHandler("_buffer_title_changed", bufferListWatcher)
        addMessageHandler("_buffer_localvar_added", bufferListWatcher)
        addMessageHandler("_buffer_localvar_changed", bufferListWatcher)
        addMessageHandler("_buffer_localvar_removed", bufferListWatcher)
        addMessageHandler("_buffer_closing", bufferListWatcher)
        addMessageHandler("_buffer_moved", bufferListWatcher)
        addMessageHandler("_buffer_merged", bufferListWatcher)
        addMessageHandler("_buffer_hidden", bufferListWatcher)
        addMessageHandler("_buffer_unhidden", bufferListWatcher)

        addMessageHandler("hotlist", hotlistInitWatcher)
        addMessageHandler("last_read_lines", lastReadLinesWatcher)

        // handle newly arriving chat lines
        // and chatlines we are reading in reverse
        addMessageHandler("_buffer_line_added", newLineWatcher)

        // handle nicklist init and changes
        addMessageHandler("nicklist", nickListWatcher)
        addMessageHandler("_nicklist", nickListWatcher)
        addMessageHandler("_nicklist_diff", nickListWatcher)

        // request a list of buffers current open, along with some information about them
        SendMessageEvent.fire("(listbuffers) hdata buffer:gui_buffers(*) " +
                "number,full_name,short_name,type,title,nicklist,local_variables,notify,hidden")
        syncHotlist()
        SendMessageEvent.fire(if (P.optimizeTraffic) "sync * buffers,upgrade" else "sync")
    }

    @JvmStatic @AnyThread fun stop() {
        relay = null
        handlers.clear()
    }

    private val handlers = ConcurrentHashMap<String, RelayMessageHandler>()

    @AnyThread private fun addMessageHandler(id: String, handler: RelayMessageHandler) {
        Assert.assertThat(handlers.put(id, handler)).isNull()
    }

    @AnyThread fun addOneOffMessageHandler(handler: RelayMessageHandler): String {
        val wrappedHandler: RelayMessageHandler = object : RelayMessageHandler {
            override fun handleMessage(obj: RelayObject, id: String) {
                handler.handleMessage(obj, id)
                removeMessageHandler(id, this)
            }
        }
        val id = counter++.toString()
        Assert.assertThat(handlers.put(id, wrappedHandler)).isNull()
        return id
    }

    @AnyThread fun removeMessageHandler(id: String, handler: RelayMessageHandler) {
        handlers.remove(id, handler)
    }

    @JvmStatic @WorkerThread fun handleMessage(obj: RelayObject?, id: String) {
        handlers[id]?.handleMessage(obj, id)
    }

    // send synchronization data to weechat and return true. if not connected, return false
    @JvmStatic @AnyThread fun syncHotlist(): Boolean {
        val authenticated = relay?.state?.contains(RelayService.STATE.AUTHENTICATED) == true
        return if (authenticated) {
            SendMessageEvent.fire("(last_read_lines) hdata buffer:gui_buffers(*)/own_lines/last_read_line/data buffer\n" +
                                  "(hotlist) hdata hotlist:gui_hotlist(*) buffer,count")
            true
        } else {
            false
        }
    }

    @JvmStatic @AnyThread fun sortOpenBuffersByBuffers(pointers: ArrayList<Long>?) {
        val bufferToNumber = LongSparseArray<Int>()
        buffers.forEach { bufferToNumber.put(it.pointer, it.number) }
        pointers?.sortWith { l, r -> bufferToNumber[l, -1] - bufferToNumber[r, -1] }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////// called by the Eye
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // todo make val
    @JvmStatic @MainThread fun hasData() = buffers.size > 0

    // todo make setter
    @AnyThread fun setBufferListEye(buffersEye: BufferListEye?) {
        this.buffersEye = buffersEye
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // returns a "random" hot buffer or null
    val hotBuffer: Buffer?
        @MainThread get() = buffers.firstOrNull { it.hotCount > 0 }

    val hotBufferCount: Int
        @MainThread get() = buffers.count { it.hotCount > 0 }

    //////////////////////////////////////////////////////////////// called on the Eye
    ////////////////////////////////////////////////////////////////    from this and Buffer (local)
    ////////////////////////////////////////////////////////////////    (also alert Buffer)

    @AnyThread fun notifyBuffersChanged() {
        buffersEye?.onBuffersChanged()
    }

    // called when no buffers has been added or removed, but
    // buffer changes are such that we should reorder the buffer list
    @WorkerThread private fun notifyBufferPropertiesChanged(buffer: Buffer) {
        buffer.onPropertiesChanged()
        notifyBuffersChanged()
    }

    // process all open buffers and, if specified, notify them of the change
    @JvmStatic @MainThread fun onGlobalPreferencesChanged(numberChanged: Boolean) {
        buffers.filter { it.isOpen }.forEach { it.onGlobalPreferencesChanged(numberChanged) }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////// called from Buffer & RelayService (local)
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // if optimizing traffic, sync hotlist to make sure the number of unread messages is correct
    // syncHotlist parameter is used to avoid synchronizing hotlist several times in a row when
    // restoring open buffers.
    // todo simplify
    @AnyThread fun syncBuffer(buffer: Buffer, syncHotlist: Boolean) {
        if (!P.optimizeTraffic) return
        SendMessageEvent.fire("sync 0x%x", buffer.pointer)
        if (syncHotlist) syncHotlist()
    }

    @AnyThread fun desyncBuffer(buffer: Buffer) {
        if (!P.optimizeTraffic) return
        SendMessageEvent.fire("desync 0x%x", buffer.pointer)
    }

    private var counter = 0
    @MainThread fun requestLinesForBufferByPointer(pointer: Long, number: Int) {
        val id = counter.toString()
        addMessageHandler(id, BufferLineWatcher(id, pointer))
        SendMessageEvent.fire("(%d) hdata buffer:0x%x/own_lines/last_line(-%d)/data date," +
                "displayed,prefix,message,highlight,notify,tags_array", counter, pointer, number)
        counter++
    }

    @MainThread fun requestNicklistForBufferByPointer(pointer: Long) {
        SendMessageEvent.fire("(nicklist) nicklist 0x%x", pointer)
    }

    private fun requestRenumber() {
        SendMessageEvent.fire("(renumber) hdata buffer:gui_buffers(*) number")
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////// private stuffs
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @JvmStatic @AnyThread fun findByPointer(pointer: Long) =
            buffers.firstOrNull { it.pointer == pointer }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////// yay!! message handlers!! the joy
    /////////////////////////////////////////////////////////////// buffer list
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private val bufferListWatcher: RelayMessageHandler = object : RelayMessageHandler {
        @WorkerThread override fun handleMessage(obj: RelayObject, id: String) {
            val data = obj as Hdata

            if (id == "listbuffers") buffers.clear()

            data.forEach { entry ->
                if (id == "listbuffers" || id == "_buffer_opened") {
                    val buffer = Buffer(entry.pointerLong,
                            entry.getItem("number").asInt(),
                            entry.getItem("full_name").asString(),
                            entry.getItem("short_name").asString(),
                            entry.getItem("title").asString(),
                            entry.getItem("notify")?.asInt() ?: 3,
                            entry.getItem("local_variables") as Hashtable,
                            entry.getItem("hidden")?.asInt() != 0,
                            id == "_buffer_opened")
                    buffers.add(buffer)
                } else if (id == "renumber") {
                    val buffer = findByPointer(entry.getPointerLong(0))
                    val number = entry.getItem("number").asInt()
                    if (buffer != null && buffer.number != number) {
                        buffer.number = number
                        buffer.onPropertiesChanged()
                    }
                } else {
                    val buffer = findByPointer(entry.getPointerLong(0))
                    if (buffer == null) {
                        kitty.warn("handleMessage(..., %s): buffer is not present", id)
                    } else {
                        if (id == "_buffer_renamed") {
                            buffer.fullName = entry.getItem("full_name").asString()
                            val shortName = entry.getItem("short_name").asString()
                            buffer.shortName = shortName ?: buffer.fullName
                            buffer.localVars = (entry.getItem("local_variables") as Hashtable)
                            notifyBufferPropertiesChanged(buffer)
                        } else if (id == "_buffer_title_changed") {
                            buffer.title = entry.getItem("title").asString()
                            notifyBufferPropertiesChanged(buffer)
                        } else if (id.startsWith("_buffer_localvar_")) {
                            buffer.localVars = (entry.getItem("local_variables") as Hashtable)
                            notifyBufferPropertiesChanged(buffer)
                        } else if (id.isAnyOf("_buffer_moved", "_buffer_merged")) {
                            requestRenumber()
                        } else if (id.isAnyOf("_buffer_hidden", "_buffer_unhidden")) {
                            buffer.hidden = !id.endsWith("unhidden")
                            notifyBuffersChanged()
                        } else if (id == "_buffer_closing") {
                            buffers.remove(buffer)
                            buffer.onBufferClosed()
                            notifyBuffersChanged()
                        } else {
                            kitty.warn("handleMessage(..., %s): unknown message id", id)
                        }
                    }
                }
            }

            if (id.isAnyOf("listbuffers", "renumber")) {
                notifyBuffersChanged()
                Hotlist.makeSureHotlistDoesNotContainInvalidBuffers()
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////// hotlist
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // last_read_lines
    private val lastReadLinesWatcher: RelayMessageHandler = object : RelayMessageHandler {
        @WorkerThread override fun handleMessage(obj: RelayObject, id: String) {
            if (obj !is Hdata) return

            bufferToLrl.clear()
            obj.forEach { entry ->
                val bufferPointer = entry.getItem("buffer").asPointerLong()
                val linePointer = entry.pointerLong
                bufferToLrl.put(bufferPointer, linePointer)
            }
        }
    }

    const val LAST_READ_LINE_MISSING = -1L
    private val bufferToLrl = LongSparseArray<Long>()
    private var lastHotlistUpdateTime: Long = 0

    // hotlist
    private val hotlistInitWatcher: RelayMessageHandler = object : RelayMessageHandler {
        @WorkerThread override fun handleMessage(obj: RelayObject, id: String) {
            if (obj !is Hdata) return

            val bufferToHotlist = LongSparseArray<Array>()

            obj.forEach { entry ->
                val pointer = entry.getItem("buffer").asPointerLong()
                val count = entry.getItem("count").asArray()
                bufferToHotlist.put(pointer, count)
            }

            val newLastHotlistUpdateTime = System.currentTimeMillis()
            val timeSinceLastHotlistUpdate = newLastHotlistUpdateTime - lastHotlistUpdateTime
            lastHotlistUpdateTime = newLastHotlistUpdateTime

            for (buffer in buffers) {
                val count = bufferToHotlist[buffer.pointer]
                val unreads = if (count == null) 0 else count[1].asInt() + count[2].asInt() // chat messages & private messages
                val highlights = count?.get(3)?.asInt() ?: 0                                // highlights

                val linePointer = bufferToLrl[buffer.pointer, LAST_READ_LINE_MISSING]
                val hotMessagesInvalid = buffer.updateHotlist(highlights, unreads, linePointer, timeSinceLastHotlistUpdate)
                Hotlist.adjustHotListForBuffer(buffer, hotMessagesInvalid)
            }

            notifyBuffersChanged()
        }
    }

    // todo ???
    private val newLineWatcher = BufferLineWatcher("", -1)

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////// nicklist
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private const val ADD = '+'
    private const val REMOVE = '-'
    private const val UPDATE = '*'

    // the following two are rather the same thing. so check if it's not _diff
    // nicklist
    // _nicklist
    // _nicklist_diff
    private val nickListWatcher: RelayMessageHandler = object : RelayMessageHandler {
        @WorkerThread override fun handleMessage(obj: RelayObject, id: String) {
            if (obj !is Hdata) return

            val diff = id == "_nicklist_diff"
            val renickedBuffers = HashSet<Buffer>()


            obj.forEach { entry ->
                // find buffer
                val buffer = findByPointer(entry.getPointerLong(0))

                if (buffer == null) {
                    kitty.warn("handleMessage(..., %s): no buffer to update", id)
                } else if (diff && !buffer.nicksAreReady()) {
                    // if buffer doesn't hold all nicknames yet, break execution, since full nicks will be requested anyway later
                } else {
                    // erase nicklist if we have a full list here
                    if (!diff && renickedBuffers.add(buffer)) buffer.removeAllNicks()

                    // decide whether it's adding, removing or updating nicks
                    // if _nicklist, treat as if we have _diff = '+'
                    val command = if (diff) entry.getItem("_diff").asChar() else ADD

                    // do the job, but
                    // care only for items that are visible (e.g. not 'root')
                    // and that are not grouping items
                    if (command == ADD || command == UPDATE) {
                        if (entry.getItem("visible").asChar() != 0.toChar() && entry.getItem("group").asChar() != 1.toChar()) {
                            val pointer = entry.pointerLong
                            var prefix = entry.getItem("prefix").asString()
                            if (" " == prefix) prefix = ""
                            val name = entry.getItem("name").asString()
                            val color = entry.getItem("color").asString()
                            val away = color != null && color.contains("weechat.color.nicklist_away")

                            val nick = Nick(pointer, prefix, name, away)
                            if (command == ADD) buffer.addNick(nick) else buffer.updateNick(nick)
                        }
                    } else if (command == REMOVE) {
                        buffer.removeNick(entry.pointerLong)
                    }
                }
            }

            // sort nicknames when we receive them for the very first time
            if (id == "nicklist") for (buffer in renickedBuffers) buffer.onNicksListed()
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////// line
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // _buffer_line_added
    // (digit)
    internal class BufferLineWatcher(private val id: String, private val bufferPointer: Long) : RelayMessageHandler {
        @WorkerThread override fun handleMessage(obj: RelayObject, id: String) {
            if (obj !is Hdata) return

            val isBottom = id == "_buffer_line_added"
            val buffer = findByPointer(if (isBottom) obj.getItem(0).getItem("buffer").asPointerLong() else bufferPointer)

            if (buffer == null) {
                kitty.warn("handleMessage(..., %s): no buffer to update", id)
                return
            }

            if (isBottom) {
                Assert.assertThat(obj.count).isEqualTo(1)
                val line = Line.make(obj.getItem(0))
                buffer.addLineBottom(line)
                buffer.onLineAdded()
            } else {
                val dataSize = obj.count
                val lines = ArrayList<Line>(obj.count)
                for (i in dataSize - 1 downTo 0) {
                    lines.add(Line.make(obj.getItem(i)))
                }
                buffer.replaceLines(lines)
                buffer.onLinesListed()
                removeMessageHandler(this.id, this) // todo ???
            }
        }
    }
}


inline fun Hdata.forEach(block: (HdataEntry) -> Unit) {
    for (i in 0 until count) {
        block(getItem(i))
    }
}