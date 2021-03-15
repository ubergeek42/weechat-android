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
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root
import com.ubergeek42.weechat.relay.protocol.Hdata
import com.ubergeek42.weechat.relay.protocol.RelayObject
import okhttp3.internal.toHexString
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList


const val LAST_READ_LINE_MISSING = -1L


object BufferList {
    @Root private val kitty: Kitty = Kitty.make()

    @Volatile private var buffersEye: BufferListEye? = null

    @JvmField var buffers = CopyOnWriteArrayList<Buffer>()

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @JvmStatic @WorkerThread fun onServiceAuthenticated() {
        defaultMessageHandlers.forEach { (id, handler) ->
            addMessageHandler(id, handler)
        }

        SendMessageEvent.fire("(listbuffers) hdata buffer:gui_buffers(*) " +
                "number,full_name,short_name,type,title,nicklist,local_variables,notify,hidden")
        syncHotlist()
        SendMessageEvent.fire(if (P.optimizeTraffic) "sync * buffers,upgrade" else "sync")
    }

    @JvmStatic @AnyThread fun onServiceStopped() {
        handlers.clear()
    }

    private val handlers = ConcurrentHashMap<String, HdataHandler>()

    @AnyThread private fun addMessageHandler(id: String, handler: HdataHandler) {
        Assert.assertThat(handlers.put(id, handler)).isNull()
    }

    @AnyThread fun addOneOffMessageHandler(handler: HdataHandler): String {
        val wrappedHandler = object : HdataHandler {
            override fun handleMessage(obj: Hdata, id: String) {
                handler.handleMessage(obj, id)
                removeMessageHandler(id, this)
            }
        }
        val id = counter++.toString()
        Assert.assertThat(handlers.put(id, wrappedHandler)).isNull()
        return id
    }

    @AnyThread fun removeMessageHandler(id: String, handler: HdataHandler) {
        handlers.remove(id, handler)
    }

    @JvmStatic @WorkerThread fun handleMessage(obj: RelayObject?, id: String) {
        if (obj is Hdata) {
            handlers[id]?.handleMessage(obj, id) ?: kitty.warn("no handler for message id: %s", id)
        }
    }

    // send synchronization data to weechat and return true. if not connected, return false
    @JvmStatic @AnyThread fun syncHotlist() {
        SendMessageEvent.fire("(last_read_lines) hdata buffer:gui_buffers(*)/own_lines/last_read_line/data buffer\n" +
                              "(hotlist) hdata hotlist:gui_hotlist(*) buffer,count")
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
    @JvmStatic @AnyThread fun setBufferListEye(buffersEye: BufferListEye?) {
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
        val id = addOneOffMessageHandler(LineListingHandler(pointer))
        SendMessageEvent.fire("(%s) hdata buffer:0x%x/own_lines/last_line(-%d)/data date," +
                "displayed,prefix,message,highlight,notify,tags_array", id, pointer, number)
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

    @JvmStatic @AnyThread fun findByPointer(pointer: Long): Buffer? {
        val buffer = buffers.firstOrNull { it.pointer == pointer }
        if (buffer == null) kitty.warn("did not find buffer pointer: ${pointer.toHexString()}")
        return buffer
    }

    @JvmStatic @AnyThread fun findByPointerNoWarn(pointer: Long): Buffer? {
        return buffers.firstOrNull { it.pointer == pointer }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////// message handlers
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////



    private val defaultMessageHandlers = setupDefaultMessageHandlers()

    fun interface HdataHandler {
        fun handleMessage(obj: Hdata, id: String)
    }


    @Suppress("IfThenToElvis")
    private fun setupDefaultMessageHandlers(): Map<String, HdataHandler> {
        val handlers = mutableMapOf<String, HdataHandler>()

        fun add(vararg ids: String, handler: HdataHandler) {
            ids.forEach { id -> handlers[id] = handler }
        }

        ////////////////////////////////////////////////////////////////////////////////////////////
        //////////////////////////////////////////////////////////////////////////////// buffer list
        ////////////////////////////////////////////////////////////////////////////////////////////

        add("listbuffers") { obj, _ ->
            val buffers = CopyOnWriteArrayList<Buffer>()

            obj.forEach { entry ->
                val buffer = BufferSpec(entry).toBuffer(openWhileRunning = false)
                buffers.add(buffer)

                findByPointerNoWarn(buffer.pointer)?.let { existingBuffer ->
                    // todo buffer.copyOldDataFrom(existingBuffer)
                }
            }

            this.buffers = buffers

            notifyBuffersChanged()
            Hotlist.makeSureHotlistDoesNotContainInvalidBuffers()
        }

        add("_buffer_opened") { obj, _ ->
            obj.forEach { entry ->
                val buffer = BufferSpec(entry).toBuffer(openWhileRunning = true)
                buffers.add(buffer)
            }

            notifyBuffersChanged()  // todo this wasn't present before -- why?
        }

        add("renumber") { obj, _ ->
            obj.forEachExistingBuffer { spec, buffer ->
                val number = spec.number

                if (buffer.number != number) {
                    buffer.number = number
                    buffer.onPropertiesChanged()
                }
            }

            notifyBuffersChanged()
            Hotlist.makeSureHotlistDoesNotContainInvalidBuffers()   // todo needed?
        }


        add("_buffer_renamed") { obj, _ ->
            obj.forEachExistingBuffer { spec, buffer ->
                buffer.fullName = spec.fullName
                buffer.shortName = spec.shortName ?: buffer.fullName
                buffer.onPropertiesChanged()
            }

            notifyBuffersChanged()
        }


        add("_buffer_title_changed") { obj, _ ->
            obj.forEachExistingBuffer { spec, buffer ->
                buffer.title = spec.title
                buffer.onPropertiesChanged()
            }

            notifyBuffersChanged()
        }


        add("_buffer_localvar_added", "_buffer_localvar_changed", "_buffer_localvar_removed") {
                obj, _ ->
            obj.forEachExistingBuffer { spec, buffer ->
                buffer.localVars = spec.localVariables
                buffer.onPropertiesChanged()
            }

            notifyBuffersChanged()
        }


        add("_buffer_moved", "_buffer_merged") { _, _ ->
            requestRenumber()
        }


        add("_buffer_hidden", "_buffer_unhidden") { obj, id ->
            val hidden = id == "_buffer_hidden"

            obj.forEachExistingBuffer { _, buffer ->
                buffer.hidden = hidden
                buffer.onPropertiesChanged()
            }

            notifyBuffersChanged()
        }


        add("_buffer_closing") { obj, _ ->
            obj.forEachExistingBuffer { _, buffer ->
                buffers.remove(buffer)
                buffer.onBufferClosed()
            }

            notifyBuffersChanged()
        }

        ////////////////////////////////////////////////////////////////////////////////////////////
        //////////////////////////////////////////////////////////////////////////////////// hotlist
        ////////////////////////////////////////////////////////////////////////////////////////////

        var bufferToLrl = LongSparseArray<Long>()
        var lastHotlistUpdateTime: Long = 0

        add("last_read_lines") { obj, _ ->
            val newBufferToLrl = LongSparseArray<Long>()

            obj.forEach { entry ->
                val spec = LastReadLineSpec(entry)
                bufferToLrl.put(spec.bufferPointer, spec.linePointer)
            }

            bufferToLrl = newBufferToLrl
        }

        add("hotlist") { obj, _ ->
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
                        lastReadLine = bufferToLrl[buffer.pointer, LAST_READ_LINE_MISSING],
                        timeSinceLastHotlistUpdate = timeSinceLastHotlistUpdate)
                Hotlist.adjustHotListForBuffer(buffer, hotMessagesInvalid)
            }

            notifyBuffersChanged()
        }

        ////////////////////////////////////////////////////////////////////////////////////////////
        ////////////////////////////////////////////////////////////////////////////////////// lines
        ////////////////////////////////////////////////////////////////////////////////////////////

        add("_buffer_line_added") { obj, _ ->
            obj.forEach { entry ->
                val spec = LineSpec(entry)
                findByPointer(spec.bufferPointer)?.let { buffer ->
                    buffer.addLineBottom(spec.toLine())
                    buffer.onLineAdded()
                }
            }
        }

        // see also LineListingHandler below

        ////////////////////////////////////////////////////////////////////////////////////////////
        ////////////////////////////////////////////////////////////////////////////////////// nicks
        ////////////////////////////////////////////////////////////////////////////////////////////

        add("nicklist", "_nicklist") { obj, id ->
            val updates = mutableMapOf<Long, MutableList<Nick>>()

            obj.forEach { entry ->
                val spec = NickSpec(entry)

                if (spec.visible && !spec.group) {
                    val nicks = updates.getOrPut(spec.bufferPointer) { mutableListOf() }
                    nicks.add(spec.toNick())
                }
            }

            updates.forEach { (bufferPointer, nicks) ->
                findByPointer(bufferPointer)?.let { buffer ->
                    // todo make sensible
                    buffer.removeAllNicks()
                    nicks.forEach { buffer.addNick(it) }

                    // sort nicknames when we receive them for the very first time
                    if (id == "nicklist") buffer.onNicksListed()
                }
            }
        }

        add("_nicklist_diff") { obj, _ ->
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

        return handlers
    }

    private class LineListingHandler(private val bufferPointer: Long) : HdataHandler {
        override fun handleMessage(obj: Hdata, id: String) {
            findByPointer(bufferPointer)?.let { buffer ->
                val newLines = ArrayList<Line>(obj.count)

                obj.forEachReversed { entry ->
                    val spec = LineSpec(entry)
                    newLines.add(spec.toLine())
                }

                buffer.replaceLines(newLines)
                buffer.onLinesListed()
                // removeMessageHandler(this.id, this) // todo ???
            }
        }
    }
}
