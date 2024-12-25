package com.ubergeek42.WeechatAndroid.relay

import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.SuperscriptSpan
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import com.ubergeek42.WeechatAndroid.notifications.Hotlist
import com.ubergeek42.WeechatAndroid.notifications.shortcuts
import com.ubergeek42.WeechatAndroid.service.Events
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.WeechatAndroid.utils.Assert
import com.ubergeek42.WeechatAndroid.utils.updatable
import com.ubergeek42.cats.Cat
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root

class Buffer @WorkerThread constructor(
    @JvmField val pointer: Long,
) {
    @JvmField var number: Int = 0
    @JvmField var fullName: String = ""
    @JvmField var shortName: String = ""
    @JvmField var hidden: Boolean = false
    @JvmField var type = BufferSpec.Type.Other

    private var notify: Notify = Notify.default

    inner class Updater {
        internal var updateNameOrNumber = false
        internal var updateShortName = false
        internal var updateTitle = false
        internal var updateHidden = false
        internal var updateType = false
        internal var updateNotifyLevel = false

        var number: Int by updatable(::updateNameOrNumber, this@Buffer::number)
        var fullName: String by updatable(::updateNameOrNumber, this@Buffer::fullName)
        var shortName: String? by updatable(::updateShortName, this@Buffer::shortName)
        var title: String? by updatable(::updateTitle)
        var hidden: Boolean by updatable(::updateHidden)
        var type: BufferSpec.Type by updatable(::updateType)
        var notify: Notify? by updatable(::updateNotifyLevel)
    }

    fun update(silently: Boolean = false, block: Updater.() -> Unit) {
        val updater = Updater()
        updater.block()

        if (updater.updateNameOrNumber || updater.updateShortName) {
            number = updater.number
            fullName = updater.fullName
            shortName = updater.shortName ?: fullName
            kitty.setPrefix(shortName)
            processBufferNameSpannable()
        }

        if (updater.updateShortName) {
            shortcuts.updateShortcutNameIfNeeded(this@Buffer)
            if (!silently) Hotlist.adjustHotListForBuffer(this, false)
        }

        if (updater.updateTitle) {
            lines.title = updater.title ?: ""
            bufferEyes.forEach { it.onTitleChanged() }
        }

        if (updater.updateNotifyLevel) notify = updater.notify ?: Notify.default
        if (updater.updateHidden) hidden = updater.hidden
        if (updater.updateType) type = updater.type
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private var bufferEyes = listOf<BufferEye>()
    private var bufferNickListEye: BufferNicklistEye? = null

    @JvmField var lastReadLineServer = LINE_MISSING
    @JvmField var readUnreads = 0
    @JvmField var readHighlights = 0

    @Root private val kitty: Kitty = Kitty.make("Buffer")

    // number of hotlist updates while syncing this buffer. if >= 2, when the new update arrives, we
    // keep own unreads/highlights as they have been correct since the last update
    var hotlistUpdatesWhileSyncing = 0

    @JvmField @Volatile var flagResetHotMessagesOnNewOwnLine = false

    private var lines: Lines = Lines()
    private var nicks: Nicks = Nicks()

    fun copyOldDataFrom(buffer: Buffer) {
        lines = buffer.lines.apply { status = Lines.Status.Init }
        nicks = buffer.nicks.apply { status = Nicks.Status.Init }
    }

    val isOpen get() = openKeys.isNotEmpty()

    @JvmField var unreads = 0
    @JvmField var highlights = 0

    @JvmField var printable: Spannable? = null  // printable buffer without title (for TextView)

    init { kitty.trace("→ Buffer(number=%s, fullName=%s)", number, fullName) }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////// LINES
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // get a copy of lines, filtered or not according to global settings
    // contains read marker and header
    @AnyThread @Synchronized fun getLinesCopy() = lines.getCopy()

    @AnyThread fun linesAreReady() = lines.status.ready()

    val linesStatus: Lines.Status
        @AnyThread get() = lines.status

    private val openKeys = Keys<String>()

    // sets buffer as open or closed
    // an open buffer is such that:
    //     has processed lines and processes lines as they come by
    //     is synced
    //     is marked as "open" in the buffer list fragment or wherever
    @Synchronized fun addOpenKey(key: String, syncHotlistOnOpen: Boolean) {
        if (openKeys.add(key) == Keys.Change.BecameNotEmpty) {
            BufferList.syncBuffer(this, syncHotlistOnOpen)
            lines.ensureSpannables()
            BufferList.notifyBuffersChanged()
        }
    }

    @Synchronized fun removeOpenKey(key: String) {
        if (openKeys.remove(key) == Keys.Change.BecameEmpty) {
            BufferList.desyncBuffer(this)
            lines.invalidateSpannables()
            if (P.optimizeTraffic) {
                // request lines & nicks on the next sync
                // the previous comment here was stupid
                lines.status = Lines.Status.Init
                nicks.status = Nicks.Status.Init
                hotlistUpdatesWhileSyncing = 0
            }
            BufferList.notifyBuffersChanged()
        }
    }

    // add buffer eye, i.e. something that watches buffer events
    // also requests all lines and nicknames, if needed (usually only done once per buffer)
    // we are requesting it here and not in setOpen() because:
    //     when the process gets killed and restored, we want to receive messages, including
    //     notifications, for that buffer. BUT the user might not visit that buffer at all.
    //     so we request lines and nicks upon user actually (getting close to) opening the buffer.
    // we are requesting nicks along with the lines because:
    //     nick completion
    @MainThread @Cat @Synchronized fun addBufferEye(bufferEye: BufferEye) {
        bufferEyes = bufferEyes + bufferEye
        if (lines.status == Lines.Status.Init) requestMoreLines()
        if (nicks.status == Nicks.Status.Init) BufferList.requestNicklistForBuffer(pointer)
    }

    @MainThread @Cat @Synchronized fun removeBufferEye(bufferEye: BufferEye) {
        bufferEyes = bufferEyes - bufferEye
    }

    @MainThread @Synchronized fun requestMoreLines() {
        requestMoreLines(lines.maxUnfilteredSize + P.lineIncrement)
    }

    @MainThread @Synchronized fun requestMoreLines(newSize: Int) {
        if (lines.maxUnfilteredSize >= newSize) return
        if (lines.status == Lines.Status.EverythingFetched) return
        lines.onMoreLinesRequested(newSize)
        BufferList.requestLinesForBuffer(pointer, lines.maxUnfilteredSize)
    }

    private val watchedKeys = Keys<String>()

    @Synchronized fun addWatchedKey(key: String) {
        watchedKeys.add(key)
        resetUnreadsAndHighlights()
    }

    @Synchronized fun removeWatchedKey(key: String) {
        if (watchedKeys.remove(key) == Keys.Change.BecameEmpty) {
            lines.rememberCurrentSkipsOffset()
        }
    }

    @Synchronized fun isWatchedByKey(key: String) = watchedKeys.contains(key)

    @MainThread @Synchronized fun moveReadMarkerToEnd() {
        lines.moveReadMarkerToEnd()
        if (P.hotlistSync) Events.SendMessageEvent.fire(
                "input ${pointer.as0x} /buffer set hotlist -1\n" +
                "input ${pointer.as0x} /input set_unread_current_buffer")
    }

    val hotCount: Int
        get() = if (type == BufferSpec.Type.Private) unreads + highlights else highlights

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////// stuff called by message handlers
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @WorkerThread fun replaceLines(newLines: Collection<Line>) {
        if (isOpen) {
            newLines.forEach { it.ensureSpannable() }
        }

        synchronized(this) {
            lines.replaceLines(newLines)
        }
   }

    @WorkerThread fun replaceLine(line: Line) {
        if (isOpen) line.ensureSpannable()

        synchronized(this) {
            lines.replaceLine(line)
        }
    }

    @WorkerThread fun addLineBottom(line: Line) {
        if (isOpen) line.ensureSpannable()

        val notifyHighlight = line.notifyLevel == LineSpec.NotifyLevel.Highlight
        val notifyPm = line.notifyLevel == LineSpec.NotifyLevel.Private
        val notifyPmOrMessage = line.notifyLevel == LineSpec.NotifyLevel.Message || notifyPm

        synchronized(this) {
            lines.addLast(line)

            // notify levels: 0 none 1 highlight 2 message 3 all
            // treat hidden lines and lines that are not supposed to generate a “notification” as read
            if (watchedKeys.isNotEmpty()
                    || type == BufferSpec.Type.HardHidden
                    || (P.filterLines && !line.isVisible)
                    || notify == Notify.NeverAddToHotlist
                    || (notify == Notify.AddHighlightsOnly && !notifyHighlight)) {
                if (notifyHighlight) { readHighlights++ }
                else if (notifyPmOrMessage) { readUnreads++ }
            } else {
                if (notifyHighlight) {
                    highlights++
                    Hotlist.reportNewHotLine(this, line)
                    BufferList.notifyBuffersChanged()
                } else if (notifyPmOrMessage) {
                    unreads++
                    if (notifyPm) Hotlist.reportNewHotLine(this, line)
                    BufferList.notifyBuffersChanged()
                }
            }

            // if current line's an event line and we've got a speaker, move nick to fist position
            // nick in question is supposed to be in the nicks already, for we only shuffle these
            // nicks when someone spoke, i.e. NOT when user joins.
            if (nicksAreReady() && line.type == LineSpec.Type.IncomingMessage && line.nick != null) {
                nicks.bumpNickToTop(line.nick)
            }

            if (flagResetHotMessagesOnNewOwnLine && line.type == LineSpec.Type.OutgoingMessage) {
                flagResetHotMessagesOnNewOwnLine = false
                resetUnreadsAndHighlights()
            }
        }
    }

    var lastSeenLine: Long
        @AnyThread get() = lines.lastSeenLine
        @WorkerThread set(pointer) { lines.lastSeenLine = pointer }

    // possible changes in the pointer:
    // legend for hotlist changes (numbers) if the buffer is NOT synchronized:
    //      [R] reset, [-] do nothing
    // legend for the validity of stored highlights if the buffer is NOT synchronized:
    //      [I] invalidate [-] keep
    //
    // 1. 123 → 456: [RI] at some point the buffer was read & blurred in weechat. weechat's hotlist
    //                    has completely changed. our internal hotlist might have some overlap with
    //                    weechat's hotlist, but we can't be sure that the last messages are correct
    //                    even if the number of weechat's hotlist messages didn't change.
    //                    this could have happened multiple times (123 → 456 → 789)
    // 2.  -1 → 123: two possibilities here:
    //      2.1. [RI] same as 1, if the buffer had lost its last read line naturally
    //      2.2. [RI] the buffer had been focused and got blurred. similarly, we don't know when this
    //                happened, so new hotlist doesn't translate to anything useful
    // 3. 123 →  -1: three possibilities here:
    //      3.1. [??] buffer is focused in weechat right now. the hotlist will read zero
    //      3.2. [RI] buffer was read, blurred, and lost its last read line. that is, it went like
    //                this: 123 → 456 (1.) → -1 (3.3.) all while we weren't looking! this takes
    //                quite some time, so we can detect this change.
    //      3.3. [--] the buffer lost its last read line naturally—due to new lines. both the
    //                hotlist and the hot messages are still correct!
    // this tries to satisfy the following equation: server unreads = this.unreads + this.readUnreads
    // when synced, we are trying to not touch unreads/highlights; when unsynced, these are the ones
    // updated. in some circumstances, especially when the buffer has been read in weechat, the
    // number of new unreads can be smaller than either value stored in the buffer. in such cases,
    // we opt for full update.
    // returns whether local hot messages are to be invalidated
    @WorkerThread @Synchronized fun updateHotlist(
            newHighlights: Int, newUnreads: Int, lastReadLine: Long, timeSinceLastHotlistUpdate: Long
    ): Boolean {
        var bufferHasBeenReadInWeechat = false
        var syncedSinceLastUpdate = false

        if (isOpen || !P.optimizeTraffic) {
            hotlistUpdatesWhileSyncing++
            syncedSinceLastUpdate = hotlistUpdatesWhileSyncing >= 2
        }

        if (lastReadLine != lastReadLineServer) {
            lastSeenLine = lastReadLine
            lastReadLineServer = lastReadLine
            if (lastReadLine != LINE_MISSING ||
                    timeSinceLastHotlistUpdate > 10 * 60 * 1000) bufferHasBeenReadInWeechat = true
        }

        val fullUpdate = !syncedSinceLastUpdate && bufferHasBeenReadInWeechat
        if (!fullUpdate) {
            if (syncedSinceLastUpdate) {
                readUnreads = newUnreads - unreads
                readHighlights = newHighlights - highlights
            } else {
                unreads = newUnreads - readUnreads
                highlights = newHighlights - readHighlights
            }
        }

        if (fullUpdate || readUnreads < 0 || readHighlights < 0 || unreads < 0 || highlights < 0) {
            unreads = newUnreads
            highlights = newHighlights
            readHighlights = 0
            readUnreads = readHighlights
        }

        Assert.assertThat(unreads + readUnreads).isEqualTo(newUnreads)
        Assert.assertThat(highlights + readHighlights).isEqualTo(newHighlights)

        return fullUpdate
    }

    fun updateLastLineInfo(lastPointer: Long?, lastVisiblePointer: Long?) {
        lines.updateLastLineInfo(lastPointer, lastVisiblePointer)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @WorkerThread fun onLineAdded() {
        bufferEyes.forEach { it.onLineAdded() }
    }

    var style = 0

    @MainThread fun onGlobalPreferencesChanged(numberChanged: Boolean) {
        synchronized(this) {
            if (!numberChanged) {
                lines.invalidateSpannables()
                style++
            }
            lines.ensureSpannables()
        }
        bufferEyes.forEach { it.onGlobalPreferencesChanged(numberChanged) }
    }

    @WorkerThread fun onLinesListed() {
        synchronized(this) { lines.onLinesListed() }
        bufferEyes.forEach { it.onLinesListed() }
    }

    @WorkerThread fun onLinesCleared() {
        synchronized(this) {
            lines = Lines().apply {
                status = Lines.Status.EverythingFetched
                title = lines.title
            }
            resetUnreadsAndHighlights()
        }
        bufferEyes.forEach { it.onLinesListed() }
    }

    @WorkerThread fun onBufferClosed() {
        synchronized(this) {
            unreads = 0
            highlights = 0
            Hotlist.adjustHotListForBuffer(this, true)
        }
        bufferEyes.forEach { it.onBufferClosed() }
    }

    ///////////////////////////////////////////////////////////////////////////////// private stuffs

    // sets highlights/unreads to 0 and,
    // if something has actually changed, notifies whoever cares about it
    @AnyThread @Synchronized private fun resetUnreadsAndHighlights() {
        if (unreads == 0 && highlights == 0) return
        readUnreads += unreads
        readHighlights += highlights
        highlights = 0
        unreads = 0
        Hotlist.adjustHotListForBuffer(this, true)
        BufferList.notifyBuffersChanged()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////// NICKS
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @AnyThread fun nicksAreReady() = nicks.status == Nicks.Status.Ready

    @MainThread @Synchronized fun getMostRecentNicksMatching(prefix: String, ignoreChars: String): List<String> {
        return nicks.getMostRecentNicksMatching(prefix, ignoreChars)
    }

    val nicksCopySortedByPrefixAndName: ArrayList<Nick>
        @AnyThread @Synchronized get() = nicks.getCopySortedByPrefixAndName()

    @MainThread @Synchronized fun setBufferNicklistEye(bufferNickListEye: BufferNicklistEye?) {
        this.bufferNickListEye = bufferNickListEye
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @WorkerThread @Synchronized fun addNick(nick: Nick) {
        nicks.addNick(nick)
        notifyNicklistChanged()
    }

    @WorkerThread @Synchronized fun removeNick(pointer: Long) {
        nicks.removeNick(pointer)
        notifyNicklistChanged()
    }

    @WorkerThread @Synchronized fun updateNick(nick: Nick) {
        nicks.updateNick(nick)
        notifyNicklistChanged()
    }

    @WorkerThread @Synchronized fun onNicksListed(newNicks: Collection<Nick>) {
        nicks.replaceNicks(newNicks)
        nicks.sortNicksByNamesThatSpokeLast(lines.namesThatSpokeLast)
    }

    @WorkerThread private fun notifyNicklistChanged() {
        bufferNickListEye?.onNicklistChanged()
    }

    override fun toString() = "Buffer($shortName)"

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun processBufferNameSpannable() {
        val numberString = "$number "
        printable = SpannableString(numberString + shortName).apply {
            setSpan(SUPER, 0, numberString.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(SMALL, 0, numberString.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}


private val SUPER = SuperscriptSpan()
private val SMALL = RelativeSizeSpan(0.6f)


////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////


private class Keys<T> {
    enum class Change {
        NoChange,
        BecameEmpty,
        BecameNotEmpty,
        SomeItemsAddedOrRemoved,
    }

    private var keys = setOf<T>()

    private fun set(newKeys: Set<T>): Change {
        val emptyBefore = keys.isEmpty()
        val emptyAfter = newKeys.isEmpty()

        keys = newKeys

        return when {
            emptyBefore && !emptyAfter -> Change.BecameNotEmpty
            !emptyBefore && emptyAfter -> Change.BecameEmpty
            else -> Change.SomeItemsAddedOrRemoved
        }
    }

    fun add(key: T) = if (keys.contains(key)) Change.NoChange else set(keys + key)

    fun remove(key: T) = if (keys.contains(key)) set(keys - key) else Change.NoChange

    fun isNotEmpty() = keys.isNotEmpty()

    fun contains(key: T) = keys.contains(key)
}