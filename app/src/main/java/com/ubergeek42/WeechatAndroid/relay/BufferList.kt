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
import com.ubergeek42.weechat.relay.protocol.Infolist
import com.ubergeek42.weechat.relay.protocol.ApiRelayObject
import com.ubergeek42.weechat.relay.protocol.BufferList
import com.ubergeek42.weechat.relay.protocol.Hdata
import com.ubergeek42.weechat.relay.protocol.LineList
import com.ubergeek42.weechat.relay.protocol.RelayObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger


const val LINE_MISSING = -1L


object BufferList {
    @Root private val kitty = Kitty.make()

    ////////////////////////////////////////////////////////////////////////////////////// lifecycle

    @JvmStatic @WorkerThread fun onServiceAuthenticated() {
        setupDefaultMessageHandlers()

        SendMessageEvent.fire(
            listOf(
                OptionsSpec.dayChangeRequest,
                BufferSpec.listBuffersRequest,
                //LastLinesSpec.request,          // see Lines.shouldAddSquiggleOnNewLastLine
                //LastReadLineSpec.request,
                //HotlistSpec.request,
                buildJsonObject {
                    put("request", "POST /api/sync")

                    putJsonObject("body") {
                        put("colors", "weechat")
                        putJsonArray("options") {
                            add("weechat.look.day_change")
                        }
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

    private val handlers = ConcurrentHashMap<String, RelayObjectHandler<RelayObject>>()
    private val handlerIdCounter = AtomicInteger()

    @AnyThread private fun <T : RelayObject> addMessageHandler(
        type: Class<T>,
        vararg ids: String,
        handler: RelayObjectHandler<T>,
    ) {
        val relayObjectHandler = RelayObjectHandler<RelayObject> { obj, id ->
            if (type.isInstance(obj)) handler.handleMessage(type.cast(obj)!!, id)
            else kitty.warn(
                "id %s: expected %s, got %s",
                id, type.simpleName, obj.javaClass.simpleName
            )
        }
        ids.forEach { handlers[it] = relayObjectHandler }
    }

    @AnyThread private inline fun <reified T : RelayObject> addMessageHandler(
        vararg ids: String,
        handler: RelayObjectHandler<T>,
    ) = addMessageHandler(T::class.java, *ids, handler = handler)

    @AnyThread fun <T : RelayObject> addOneOffMessageHandler(type: Class<T>, handler: RelayObjectHandler<T>): String {
        val id = handlerIdCounter.getAndIncrement().toString()
        addMessageHandler(type, id) { obj, _ ->
            handlers.remove(id)
            handler.handleMessage(obj, id)
        }
        return id
    }

    @AnyThread inline fun <reified T : RelayObject> addOneOffMessageHandler(handler: RelayObjectHandler<T>): String =
        addOneOffMessageHandler(T::class.java, handler)

    @JvmStatic @WorkerThread fun handleMessage(obj: RelayObject?, id: String) {
        if (obj != null) {
            handlers[id]?.handleMessage(obj, id) ?: kitty.warn("no handler for message id: %s", id)
        }
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

    fun interface RelayObjectHandler<T : RelayObject> {
        fun handleMessage(obj: T, id: String)
    }


    private fun setupDefaultMessageHandlers() {
        ////////////////////////////////////////////////////////////////////////////////////////////
        //////////////////////////////////////////////////////////////////////////////// buffer list
        ////////////////////////////////////////////////////////////////////////////////////////////

        addMessageHandler<BufferList>("listbuffers") { obj, _ ->
            val buffers = CopyOnWriteArrayList<Buffer>()

            obj.forEach { entry ->
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

        /*
        addMessageHandler<Hdata>("_buffer_opened") { obj, _ ->
            obj.forEach { entry ->
                val buffer = BufferSpec(entry).toBuffer(openWhileRunning = true)
                buffers.add(buffer)
            }

            notifyBuffersChanged()  // todo this wasn't present before -- why?
        }

        addMessageHandler<Hdata>("renumber") { obj, _ ->
            obj.forEachExistingBuffer { spec, buffer ->
                val number = spec.number

                if (buffer.number != number) {
                    buffer.update { this.number = number }
                }
            }

            notifyBuffersChanged()
            Hotlist.makeSureHotlistDoesNotContainInvalidBuffers()   // todo needed?
        }


        addMessageHandler<Hdata>("_buffer_renamed") { obj, _ ->
            obj.forEachExistingBuffer { spec, buffer ->
                buffer.update { fullName = spec.fullName; shortName = spec.shortName }
            }

            notifyBuffersChanged()
        }


        addMessageHandler<Hdata>("_buffer_title_changed") { obj, _ ->
            obj.forEachExistingBuffer { spec, buffer ->
                buffer.update { title = spec.title }
            }

            notifyBuffersChanged()
        }


        addMessageHandler<Hdata>("_buffer_localvar_added", "_buffer_localvar_changed", "_buffer_localvar_removed") {
                obj, _ ->
            obj.forEachExistingBuffer { spec, buffer ->
                buffer.update { type = spec.type }
            }

            notifyBuffersChanged()
        }


        addMessageHandler<Hdata>("_buffer_moved", "_buffer_merged") { _, _ ->
            SendMessageEvent.fire(BufferSpec.renumberRequest)
        }


        addMessageHandler<Hdata>("_buffer_hidden", "_buffer_unhidden") { obj, id ->
            val hidden = id == "_buffer_hidden"

            obj.forEachExistingBuffer { _, buffer ->
                buffer.update { this.hidden = hidden  }
            }

            notifyBuffersChanged()
        }


        addMessageHandler<Hdata>("_buffer_closing") { obj, _ ->
            obj.forEachExistingBuffer { _, buffer ->
                buffers.remove(buffer)
                buffer.onBufferClosed()
            }

            notifyBuffersChanged()
        }
         */


        addMessageHandler<Infolist>("options") { obj, _ ->
            OptionsSpec.init(obj)
        }

        addMessageHandler<Infolist>("_option_changed") { obj, _ ->
            OptionsSpec.init(obj)
        }

        ////////////////////////////////////////////////////////////////////////////////////////////
        //////////////////////////////////////////////////////////////////////////////////// hotlist
        ////////////////////////////////////////////////////////////////////////////////////////////

        var bufferToLastReadLine = LongSparseArray<Long>()
        var lastHotlistUpdateTime = 0L

        /*
        addMessageHandler<Hdata>("last_read_lines") { obj, _ ->
            bufferToLastReadLine = LongSparseArray<Long>().apply {
                obj.forEach { entry ->
                    val spec = LastReadLineSpec(entry)
                    put(spec.bufferPointer, spec.linePointer)
                }
            }
        }

        addMessageHandler<Hdata>("last_lines") { obj, _ ->
            class PointerPair(var lastPointer: Long? = null, var lastVisiblePointer: Long? = null)

            val bufferToPointers = mutableMapOf<Long, PointerPair>()

            obj.forEach { entry ->      // last lines com first
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

        addMessageHandler<Hdata>("hotlist") { obj, _ ->
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
        addMessageHandler<Hdata>("nicklist", "_nicklist") { obj, _ ->
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

        addMessageHandler<Hdata>("_nicklist_diff") { obj, _ ->
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

        addMessageHandler<LineList>("_buffer_line_added") { obj, _ ->
            obj.forEach { entry ->
                val spec = LineSpec(entry)
                findByPointer(spec.bufferPointer)?.let { buffer ->
                    buffer.addLineBottom(spec.toLine())
                    buffer.onLineAdded()
                }
            }
        }

        /*
        addMessageHandler<Hdata>("_buffer_cleared") { obj, _ ->
            obj.forEachExistingBuffer { _, buffer ->
                buffer.onLinesCleared()
            }
        }

        addMessageHandler<Hdata>("_buffer_line_data_changed") { obj, _ ->
            if (!P.handleBufferLineDataChanged) return@addMessageHandler

            obj.forEach { entry ->
                val spec = LineSpec(entry)
                findByPointer(spec.bufferPointer)?.let { buffer ->
                    buffer.replaceLine(spec.toLine())
                    buffer.onLinesListed()
                }
            }
        }
         */
    }

    @JvmStatic fun clearPrecomputedLayoutsEtcForAllBuffers() {
        buffers.forEach { it.clearPrecomputedLayoutsEtc() }
    }
    private class LineListingHandler(private val bufferPointer: Long) : RelayObjectHandler<LineList> {
        override fun handleMessage(obj: LineList, id: String) {
            findByPointer(bufferPointer)?.let { buffer ->
                val newLines = ArrayList<Line>(obj.count())

                obj.forEach { entry ->
                    newLines.add(LineSpec(entry).toLine())
                }

                buffer.replaceLines(newLines)
                buffer.onLinesListed()
            }
        }
    }
}
