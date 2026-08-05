// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
package com.ubergeek42.WeechatAndroid.relay

import android.util.LongSparseArray
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import com.ubergeek42.WeechatAndroid.notifications.Hotlist
import com.ubergeek42.WeechatAndroid.service.Events.SendMessageEvent
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root
import com.ubergeek42.weechat.relay.protocol.ApiRelayObject
import com.ubergeek42.weechat.relay.protocol.RelayObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList


const val LINE_MISSING = -1L


object BufferList {
    @Root private val kitty = Kitty.make()

    ////////////////////////////////////////////////////////////////////////////////////// lifecycle

    @JvmStatic @WorkerThread fun onServiceAuthenticated() {
        defaultMessageHandlers.forEach { (id, handler) -> addMessageHandler(id, handler) }

        SendMessageEvent.fire(
            listOf(
                BufferSpec.listBuffersRequest,
                //LastLinesSpec.request,          // see Lines.shouldAddSquiggleOnNewLastLine
                //LastReadLineSpec.request,
                //HotlistSpec.request,
                //if (P.optimizeTraffic) "sync * buffers,upgrade" else "sync",
                buildJsonObject {
                    put("request", "POST /api/sync")

                    putJsonObject("body") {
                        put("colors", "weechat")
                    }
                }
            ).joinToString(",", prefix = "[", postfix = "]")
        )
    }

    @JvmStatic @AnyThread fun onServiceStopped() {
        handlers.clear()
    }

    //////////////////////////////////////////////////////////////////////////////////////// buffers

    @JvmField @Volatile var buffers = CopyOnWriteArrayList<Buffer>()

    @JvmStatic @AnyThread fun findByPointer(pointer: Long): Buffer? {
        return buffers.firstOrNull { it.pointer == pointer }.also {
            it ?: kitty.warn("did not find buffer pointer: ${pointer.as0x}")
        }
    }

    @JvmStatic @AnyThread fun findByFullName(fullName: String): Buffer? {
        return buffers.firstOrNull { it.fullName == fullName }.also {
            it ?: kitty.warn("did not find buffer pointer: $fullName")
        }
    }

    @JvmStatic @AnyThread private fun findByPointerNoWarn(pointer: Long): Buffer? {
        return buffers.firstOrNull { it.pointer == pointer }
    }

    /////////////////////////////////////////////////////////////////////////////////////// handlers

    private val handlers = ConcurrentHashMap<String, RelayObjectHandler>()
    private var handlerIdCounter = 0

    @AnyThread private fun addMessageHandler(id: String, handler: RelayObjectHandler) {
        handlers[id] = handler
    }

    @AnyThread private fun removeMessageHandler(id: String) {
        handlers.remove(id)
    }

    @AnyThread fun addOneOffMessageHandler(handler: RelayObjectHandler): String {
        val id = handlerIdCounter++.toString()
        addMessageHandler(id) { obj, _ ->
            removeMessageHandler(id)
            handler.handleMessage(obj, id)
        }
        return id
    }

    @JvmStatic @WorkerThread fun handleMessage(obj: RelayObject, id: String) {
        handlers[id]?.handleMessage(obj, id) ?: kitty.warn("no handler for message id: %s", id)
    }

    //////////////////////////////////////////////////////////////////////////////////////////// eye

    @Volatile var bufferListEye: BufferListEye? = null

    @AnyThread fun notifyBuffersChanged() {
        bufferListEye?.onBuffersChanged()
    }

    // process all open buffers and, if specified, notify them of the change
    // todo make sense of this
    @JvmStatic @MainThread fun onGlobalPreferencesChanged(numberChanged: Boolean) {
        buffers.filter { it.isOpen }.forEach { it.onGlobalPreferencesChanged(numberChanged) }
    }

    /////////////////////////////////////////////////////////////////////////////////////////// misc

    @JvmStatic @AnyThread fun sortOpenBuffersByBuffers(pointers: ArrayList<Long>?) {
        val bufferToNumber = LongSparseArray<Int>()
        buffers.forEach { bufferToNumber.put(it.pointer, it.number) }
        pointers?.sortWith { l, r -> bufferToNumber[l, -1] - bufferToNumber[r, -1] }
    }

    @JvmStatic @MainThread fun hasData() = buffers.size > 0

    @MainThread fun getNextHotBuffer() = buffers.firstOrNull { it.hotCount > 0 }

    val hotBufferCount: Int @MainThread get() = buffers.count { it.hotCount > 0 }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////// requests
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @JvmStatic @AnyThread fun syncHotlist() {
        //SendMessageEvent.fire(LastReadLineSpec.request + "\n" + HotlistSpec.request)
    }

    // if optimizing traffic, sync hotlist to make sure the number of unread messages is correct
    // syncHotlist parameter is used to avoid synchronizing hotlist several times in a row when
    // restoring open buffers.
    // todo simplify
    @AnyThread fun syncBuffer(buffer: Buffer, syncHotlist: Boolean) {
        if (!P.optimizeTraffic) return
        SendMessageEvent.fire("sync ${buffer.pointer.as0x}")
        if (syncHotlist) syncHotlist()
    }

    @AnyThread fun desyncBuffer(buffer: Buffer) {
        if (!P.optimizeTraffic) return
        SendMessageEvent.fire("desync ${buffer.pointer.as0x}")
    }

    @MainThread fun requestLinesForBuffer(pointer: Long, numberOfLines: Int) {
        val id = addOneOffMessageHandler(LineListingHandler(pointer))
        SendMessageEvent.fire(LineSpec.makeLastLinesRequest(id, pointer, numberOfLines))
    }

    @MainThread fun requestNicklistForBuffer(pointer: Long) {
        //SendMessageEvent.fire(NickSpec.makeNicklistRequest(pointer))
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////// default message handlers
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private val defaultMessageHandlers = setupDefaultMessageHandlers()


    fun interface RelayObjectHandler {
        fun handleMessage(obj: RelayObject, id: String)
    }

    private inline fun <reified T : RelayObject> MutableMap<String, RelayObjectHandler>.add(
        vararg ids: String,
        crossinline handler: (obj: T, id: String) -> Unit
    ) {
        ids.forEach { id ->
            this[id] = RelayObjectHandler { obj, msgId ->
                if (obj is T) {
                    handler(obj, msgId)
                }
            }
        }
    }

    private fun setupDefaultMessageHandlers(): Map<String, RelayObjectHandler> {
        val handlers = mutableMapOf<String, RelayObjectHandler>()

        ////////////////////////////////////////////////////////////////////////////////////////////
        //////////////////////////////////////////////////////////////////////////////// buffer list
        ////////////////////////////////////////////////////////////////////////////////////////////

        handlers.add<ApiRelayObject>("listbuffers") { obj, _ ->
            val buffers = CopyOnWriteArrayList<Buffer>()

            (obj.inner as List<com.ubergeek42.weechat.Buffer>).forEach { entry ->
                val buffer = BufferSpec(entry).toBuffer(openWhileRunning = false)
                buffers.add(buffer)

                findByPointerNoWarn(buffer.pointer)?.let { existingBuffer ->
                    buffer.copyOldDataFrom(existingBuffer)
                }
            }

            this.buffers = buffers

            notifyBuffersChanged()
            Hotlist.makeSureHotlistDoesNotContainInvalidBuffers()
        }

        handlers.add<ApiRelayObject>("_buffer_opened") { obj, _ ->
            (obj.inner as List<com.ubergeek42.weechat.Buffer>).forEach { entry ->
                val buffer = BufferSpec(entry).toBuffer(openWhileRunning = true)
                buffers.add(buffer)
            }

            notifyBuffersChanged()  // todo this wasn't present before -- why?
        }

        handlers.add<ApiRelayObject>("renumber") { obj, _ ->
            (obj.inner as List<com.ubergeek42.weechat.Buffer>).forEachExistingBuffer { spec, buffer ->
                val number = spec.number

                if (buffer.number != number) {
                    buffer.update { this.number = number }
                }
            }

            notifyBuffersChanged()
            Hotlist.makeSureHotlistDoesNotContainInvalidBuffers()   // todo needed?
        }


        handlers.add<ApiRelayObject>("_buffer_renamed") { obj, _ ->
            (obj.inner as List<com.ubergeek42.weechat.Buffer>).forEachExistingBuffer { spec, buffer ->
                buffer.update { fullName = spec.fullName; shortName = spec.shortName }
            }

            notifyBuffersChanged()
        }


        handlers.add<ApiRelayObject>("_buffer_title_changed") { obj, _ ->
            (obj.inner as List<com.ubergeek42.weechat.Buffer>).forEachExistingBuffer { spec, buffer ->
                buffer.update { title = spec.title }
            }

            notifyBuffersChanged()
        }


        handlers.add<ApiRelayObject>("_buffer_localvar_added", "_buffer_localvar_changed", "_buffer_localvar_removed") {
                obj, _ ->
            (obj.inner as List<com.ubergeek42.weechat.Buffer>).forEachExistingBuffer { spec, buffer ->
                buffer.update { type = spec.type }
            }

            notifyBuffersChanged()
        }


        handlers.add<ApiRelayObject>("_buffer_moved", "_buffer_merged") { _, _ ->
            SendMessageEvent.fire(BufferSpec.renumberRequest)
        }


        handlers.add<ApiRelayObject>("_buffer_hidden", "_buffer_unhidden") { obj, id ->
            val hidden = id == "_buffer_hidden"

            (obj.inner as List<com.ubergeek42.weechat.Buffer>).forEachExistingBuffer { _, buffer ->
                buffer.update { this.hidden = hidden  }
            }

            notifyBuffersChanged()
        }


        handlers.add<ApiRelayObject>("_buffer_closing") { obj, _ ->
            (obj.inner as List<com.ubergeek42.weechat.Buffer>).forEachExistingBuffer { _, buffer ->
                buffers.remove(buffer)
                buffer.onBufferClosed()
            }

            notifyBuffersChanged()
        }

        ////////////////////////////////////////////////////////////////////////////////////////////
        //////////////////////////////////////////////////////////////////////////////////// hotlist
        ////////////////////////////////////////////////////////////////////////////////////////////

        var bufferToLastReadLine = LongSparseArray<Long>()
        var lastHotlistUpdateTime = 0L

        /*
        handlers.add<Hdata>("last_read_lines") { obj, _ ->
            bufferToLastReadLine = LongSparseArray<Long>().apply {
                obj.forEach { entry ->
                    val spec = LastReadLineSpec(entry)
                    put(spec.bufferPointer, spec.linePointer)
                }
            }
        }
         */

        handlers.add<ApiRelayObject>("last_lines") { obj, _ ->
            class PointerPair(var lastPointer: Long? = null, var lastVisiblePointer: Long? = null)

            val bufferToPointers = mutableMapOf<Long, PointerPair>()

            (obj.inner as List<com.ubergeek42.weechat.BufferLine>).forEach { entry ->      // last lines com first
                val spec = LastLinesSpec(entry)
                val pair = bufferToPointers.getOrPut(spec.bufferPointer) { PointerPair() }
                val linePointer = spec.linePointer
                if (pair.lastPointer == null) pair.lastPointer = linePointer
                if (pair.lastVisiblePointer == null && spec.visible) pair.lastVisiblePointer = linePointer
            }

            bufferToPointers.forEach { (bufferPointer, pair) ->
                findByPointer(bufferPointer)?.updateLastLineInfo(
                        pair.lastPointer, pair.lastVisiblePointer)
            }
        }

        /*
        handlers.add<Hdata>("hotlist") { obj, _ ->
            val bufferToHotlistSpec = LongSparseArray<HotlistSpec>()

            obj.forEach { entry ->
                val spec = HotlistSpec(entry)
                bufferToHotlistSpec.put(spec.bufferPointer, spec)
            }

            val now = System.currentTimeMillis()
            val timeSinceLastHotlistUpdate = now - lastHotlistUpdateTime
            lastHotlistUpdateTime = now

            buffers.forEach { buffer ->
                val spec: HotlistSpec? = bufferToHotlistSpec[buffer.pointer]
                val hotMessagesInvalid = buffer.updateHotlist(
                        newHighlights = spec?.highlights ?: 0,
                        newUnreads = spec?.unreads ?: 0,
                        lastReadLine = bufferToLastReadLine[buffer.pointer, LINE_MISSING],
                        timeSinceLastHotlistUpdate = timeSinceLastHotlistUpdate)
                Hotlist.adjustHotListForBuffer(buffer, hotMessagesInvalid)
            }

            notifyBuffersChanged()
        }
         */

        ////////////////////////////////////////////////////////////////////////////////////////////
        ////////////////////////////////////////////////////////////////////////////////////// nicks
        ////////////////////////////////////////////////////////////////////////////////////////////

        /*
        handlers.add<Hdata>("nicklist", "_nicklist") { obj, _ ->
            val updates = mutableMapOf<Long, MutableList<Nick>>()

            obj.forEach { entry ->
                val spec = NickSpec(entry)
                val nicks = updates.getOrPut(spec.bufferPointer) { mutableListOf() }

                if (spec.visible && !spec.group) {
                    nicks.add(spec.toNick())
                }
            }

            // before, onNicksListed() was only called on id == nicklist
            updates.forEach { (bufferPointer, nicks) ->
                findByPointer(bufferPointer)?.onNicksListed(nicks)
            }
        }

        handlers.add<Hdata>("_nicklist_diff") { obj, _ ->
            var buffer: Buffer? = null

            obj.forEach { entry ->
                val spec = NickSpec(entry)
                val bufferPointer = spec.bufferPointer

                if (buffer?.pointer != bufferPointer) {
                    buffer = findByPointer(bufferPointer)
                }

                buffer?.run {
                    when (NickDiffSpec(entry).command) {
                        ADD -> addNick(spec.toNick())
                        UPDATE -> updateNick(spec.toNick())
                        REMOVE -> removeNick(spec.pointer)
                    }
                }
            }
        }

         */

        ////////////////////////////////////////////////////////////////////////////////////////////
        ////////////////////////////////////////////////////////////////////////////////////// lines
        ////////////////////////////////////////////////////////////////////////////////////////////

        handlers.add<ApiRelayObject>("_buffer_line_added") { obj, _ ->
            listOf(obj.inner as com.ubergeek42.weechat.BufferLine).forEach { entry ->
                val spec = LineSpec(entry)
                findByPointer(spec.bufferPointer)?.let { buffer ->
                    buffer.addLineBottom(spec.toLine())
                    buffer.onLineAdded()
                }
            }
        }

        handlers.add<ApiRelayObject>("_buffer_cleared") { obj, _ ->
            (obj.inner as List<com.ubergeek42.weechat.Buffer>).forEachExistingBuffer { _, buffer ->
                buffer.onLinesCleared()
            }
        }

        handlers.add<ApiRelayObject>("_buffer_line_data_changed") { obj, _ ->
            if (!P.handleBufferLineDataChanged) return@add

            listOf((obj.inner as com.ubergeek42.weechat.BufferLine)).forEach { entry ->
                val spec = LineSpec(entry)
                findByPointer(spec.bufferPointer)?.let { buffer ->
                    buffer.replaceLine(spec.toLine())
                    buffer.onLinesListed()
                }
            }
        }

        return handlers
    }

    private class LineListingHandler(private val bufferPointer: Long) : RelayObjectHandler {
        override fun handleMessage(obj: RelayObject, id: String) {
            findByPointer(bufferPointer)?.let { buffer ->
                val newLines = ArrayList<Line>(((obj as ApiRelayObject).inner as List<com.ubergeek42.weechat.BufferLine>).count())

                (obj.inner as List<com.ubergeek42.weechat.BufferLine>).forEach { entry ->
                    newLines.add(LineSpec(entry).toLine())
                }

                buffer.replaceLines(newLines)
                buffer.onLinesListed()
            }
        }
    }
}
