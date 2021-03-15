package com.ubergeek42.WeechatAndroid.relay

import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.RelativeSizeSpan
import android.text.style.SuperscriptSpan
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import com.ubergeek42.WeechatAndroid.service.Events
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.WeechatAndroid.utils.Assert
import com.ubergeek42.cats.Cat
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root
import com.ubergeek42.weechat.relay.protocol.Hashtable
import com.ubergeek42.weechat.relay.protocol.RelayObject
import java.util.*

class Buffer @WorkerThread constructor(
        @JvmField val pointer: Long,
        @JvmField var number: Int,
        @JvmField var fullName: String,
        shortName: String?,
        @JvmField var title: String?,
        private val notifyLevel: Int,
        @JvmField var localVars: Hashtable,
        @JvmField var hidden: Boolean,
        openWhileRunning: Boolean
) {
    // placed here for correct initialization
    private val detachedEye: BufferEye = object : BufferEye {
        override fun onLinesListed() {}
        override fun onLineAdded() {}
        override fun onPropertiesChanged() {}
        override fun onBufferClosed() {}
        override fun onGlobalPreferencesChanged(numberChanged: Boolean) {
            needsToBeNotifiedAboutGlobalPreferencesChanged = true
        }
    }

    private var bufferEye: BufferEye = detachedEye
    private var bufferNickListEye: BufferNicklistEye? = null

    @JvmField var shortName: String = shortName ?: fullName
    @JvmField var lastReadLineServer = LAST_READ_LINE_MISSING
    @JvmField var readUnreads = 0
    @JvmField var readHighlights = 0

    @Root private val kitty: Kitty = Kitty.make("Buffer").apply { setPrefix(this@Buffer.shortName) }

    // number of hotlist updates while syncing this buffer. if >= 2, when the new update arrives, we
    // keep own unreads/highlights as they have been correct since the last update
    private var hotlistUpdatesWhileSyncing = 0

    @JvmField @Volatile var flagResetHotMessagesOnNewOwnLine = false

    // todo make val again
    private var lines: Lines = Lines(this.shortName)
    private var nicks: Nicks = Nicks(this.shortName)

    // todo copy in a better way
    fun copyOldDataFrom(buffer: Buffer) {
        this.lines = buffer.lines
        this.nicks = buffer.nicks
        lines.status = Lines.STATUS.INIT
        nicks.status = Nicks.STATUS.INIT
    }

    @JvmField var isOpen = false
    @JvmField var isWatched = false

    @JvmField var type = OTHER
    @JvmField var unreads = 0
    @JvmField var highlights = 0

    @JvmField var printable: Spannable? = null  // printable buffer without title (for TextView)
    @JvmField var titleLine: Line? = null

    init {
        processBufferType()
        processBufferTitle()

        if (P.isBufferOpen(pointer)) setOpen(open = true, syncHotlistOnOpen = false)

        // saved data would be meaningless for a newly opened buffer
        if (!openWhileRunning) P.restoreLastReadLine(this)

        // when a buffer is open while the application is already connected, such as when someone
        // pms us, we don't have lastReadLine yet and so the subsequent hotlist update will trigger
        // a full update. in order to avoid that, if we are syncing, let's pretend that a hotlist
        // update has happened at this point so that the next update is “synced”
        if (openWhileRunning && !P.optimizeTraffic) hotlistUpdatesWhileSyncing++
        kitty.trace("→ Buffer(number=%s, fullName=%s) isOpen? %s", number, fullName, isOpen)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////// LINES
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // get a copy of lines, filtered or not according to global settings
    // contains read marker and header
    val linesCopy: ArrayList<Line>
        @Synchronized @AnyThread get() = lines.copy

    @AnyThread fun linesAreReady() = lines.status.ready()

    val linesStatus: Lines.STATUS
        @AnyThread get() = lines.status

    // sets buffer as open or closed
    // an open buffer is such that:
    //     has processed lines and processes lines as they come by
    //     is synced
    //     is marked as "open" in the buffer list fragment or wherever
    @AnyThread @Cat @Synchronized fun setOpen(open: Boolean, syncHotlistOnOpen: Boolean) {
        if (isOpen == open) return
        isOpen = open
        if (open) {
            BufferList.syncBuffer(this, syncHotlistOnOpen)
            lines.ensureSpannables()
        } else {
            BufferList.desyncBuffer(this)
            lines.invalidateSpannables()
            if (P.optimizeTraffic) {
                // request lines & nicks on the next sync
                // the previous comment here was stupid
                lines.status = Lines.STATUS.INIT
                nicks.status = Nicks.STATUS.INIT
                hotlistUpdatesWhileSyncing = 0
            }
        }
        BufferList.notifyBuffersChanged()
    }

    // set buffer eye, i.e. something that watches buffer events
    // also requests all lines and nicknames, if needed (usually only done once per buffer)
    // we are requesting it here and not in setOpen() because:
    //     when the process gets killed and restored, we want to receive messages, including
    //     notifications, for that buffer. BUT the user might not visit that buffer at all.
    //     so we request lines and nicks upon user actually (getting close to) opening the buffer.
    // we are requesting nicks along with the lines because:
    //     nick completion
    @MainThread @Cat @Synchronized fun setBufferEye(bufferEye: BufferEye?) {
        this.bufferEye = bufferEye ?: detachedEye
        if (bufferEye != null) {
            if (lines.status == Lines.STATUS.INIT) requestMoreLines()
            if (nicks.status == Nicks.STATUS.INIT) BufferList.requestNicklistForBuffer(pointer)
            if (needsToBeNotifiedAboutGlobalPreferencesChanged) {
                bufferEye.onGlobalPreferencesChanged(false)
                needsToBeNotifiedAboutGlobalPreferencesChanged = false
            }
        }
    }

    @MainThread @Synchronized fun requestMoreLines() {
        requestMoreLines(lines.maxLines + P.lineIncrement)
    }

    @MainThread @Synchronized fun requestMoreLines(newSize: Int) {
        if (lines.maxLines >= newSize) return
        if (lines.status == Lines.STATUS.EVERYTHING_FETCHED) return
        lines.onMoreLinesRequested(newSize)
        BufferList.requestLinesForBuffer(pointer, lines.maxLines)
    }

    // tells buffer whether it is fully display on screen
    // called after setOpen(true) and before setOpen(false)
    // lines must be ready!
    // affects the way buffer advertises highlights/unreads count and notifications */
    @MainThread @Cat @Synchronized fun setWatched(watched: Boolean) {
        Assert.assertThat(linesAreReady()).isTrue()
        Assert.assertThat(isWatched).isNotEqualTo(watched)
        Assert.assertThat(isOpen).isTrue()
        isWatched = watched
        if (watched) resetUnreadsAndHighlights() else lines.rememberCurrentSkipsOffset()
    }

    @MainThread @Synchronized fun moveReadMarkerToEnd() {
        lines.moveReadMarkerToEnd()
        if (P.hotlistSync) Events.SendMessageEvent.fire(
                "input 0x%1${"$"}x /buffer set hotlist -1" +
                "input 0x%1${"$"}x /input set_unread_current_buffer", pointer)
    }

    val hotCount: Int
        @AnyThread @Synchronized get() = if (type == PRIVATE) unreads + highlights else highlights

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////// stuff called by message handlers
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @WorkerThread @Synchronized fun replaceLines(newLines: Collection<Line>) {
        if (isOpen) {
            newLines.forEach { it.ensureSpannable() }
        }

        synchronized(this) {
            lines.replaceLines(newLines)
        }
   }

    @WorkerThread fun addLineBottom(line: Line) {
        if (isOpen) line.ensureSpannable()

        val notifyHighlight = line.notify == LineSpec.NotifyLevel.Highlight
        val notifyPm = line.notify == LineSpec.NotifyLevel.Private
        val notifyPmOrMessage = line.notify == LineSpec.NotifyLevel.Message || notifyPm

        synchronized(this) {
            lines.addLast(line)

            // notify levels: 0 none 1 highlight 2 message 3 all
            // treat hidden lines and lines that are not supposed to generate a “notification” as read
            if (isWatched || type == HARD_HIDDEN || (P.filterLines && !line.isVisible) ||
                    notifyLevel == 0 || (notifyLevel == 1 && !notifyHighlight)) {
                if (notifyHighlight) { readHighlights++ }
                else if (notifyPmOrMessage) { readUnreads++ }
            } else {
                if (notifyHighlight) {
                    highlights++
                    Hotlist.onNewHotLine(this, line)
                    BufferList.notifyBuffersChanged()
                } else if (notifyPmOrMessage) {
                    unreads++
                    if (notifyPm) Hotlist.onNewHotLine(this, line)
                    BufferList.notifyBuffersChanged()
                }
            }

            // if current line's an event line and we've got a speaker, move nick to fist position
            // nick in question is supposed to be in the nicks already, for we only shuffle these
            // nicks when someone spoke, i.e. NOT when user joins.
            if (nicksAreReady() && line.type == LineSpec.Type.IncomingMessage) {
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
            if (lastReadLine != LAST_READ_LINE_MISSING ||
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

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @WorkerThread fun onLineAdded() {
        bufferEye.onLineAdded()
    }

    @MainThread fun onGlobalPreferencesChanged(numberChanged: Boolean) {
        synchronized(this) {
            if (!numberChanged) lines.invalidateSpannables()
            lines.ensureSpannables()
        }
        bufferEye.onGlobalPreferencesChanged(numberChanged)
    }

    @WorkerThread fun onLinesListed() {
        synchronized(this) { lines.onLinesListed() }
        bufferEye.onLinesListed()
    }

    @WorkerThread fun onPropertiesChanged() {
        synchronized(this) {
            processBufferType()
            processBufferTitle()
            Hotlist.adjustHotListForBuffer(this, false) // update buffer names in the notifications
        }
        bufferEye.onPropertiesChanged()
    }

    @WorkerThread fun onBufferClosed() {
        synchronized(this) {
            unreads = 0
            highlights = 0
            Hotlist.adjustHotListForBuffer(this, true)
        }
        bufferEye.onBufferClosed()
    }

    ///////////////////////////////////////////////////////////////////////////////// private stuffs

    // determine if the buffer is PRIVATE, CHANNEL, OTHER or HARD_HIDDEN
    // hard-hidden channels do not show in any way. to hide a channel,
    // do "/buffer set localvar_set_relay hard-hide"
    @WorkerThread private fun processBufferType() {
        val relay: RelayObject? = localVars["relay"]
        type = if (relay != null && relay.asString().split(",").contains("hard-hide")) {
            HARD_HIDDEN
        } else {
            val type: RelayObject? = localVars["type"]
            when {
                type == null -> OTHER
                type.asString() == "private" -> PRIVATE
                type.asString() == "channel" -> CHANNEL
                else -> OTHER
            }
        }
    }

    @WorkerThread private fun processBufferTitle() {
        val numberString = "$number "
        val spannable = SpannableString(numberString + shortName)
        spannable.setSpan(SUPER, 0, numberString.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(SMALL, 0, numberString.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        printable = spannable

        if (!TextUtils.isEmpty(title)) {
            titleLine = TitleLine(title)
        }
    }

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

    @AnyThread fun nicksAreReady() = nicks.status == Nicks.STATUS.READY

    @MainThread @Synchronized fun getMostRecentNicksMatching(prefix: String?, ignoreChars: String?): List<String> {
        return nicks.getMostRecentNicksMatching(prefix, ignoreChars)
    }

    val nicksCopySortedByPrefixAndName: ArrayList<Nick>
        @AnyThread @Synchronized get() = nicks.copySortedByPrefixAndName

    @MainThread @Synchronized fun setBufferNicklistEye(bufferNickListEye: BufferNicklistEye?) {
        this.bufferNickListEye = bufferNickListEye
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @WorkerThread @Synchronized fun addNick(nick: Nick?) {
        nicks.addNick(nick)
        notifyNicklistChanged()
    }

    @WorkerThread @Synchronized fun removeNick(pointer: Long) {
        nicks.removeNick(pointer)
        notifyNicklistChanged()
    }

    @WorkerThread @Synchronized fun updateNick(nick: Nick?) {
        nicks.updateNick(nick)
        notifyNicklistChanged()
    }

    @WorkerThread @Synchronized fun replaceAllNicks(newNicks: Collection<Nick>) {
        nicks.replaceNicks(newNicks)
    }

    @WorkerThread @Synchronized fun onNicksListed() {
        nicks.sortNicksByLines(lines.descendingFilteredIterator)
    }

    @WorkerThread private fun notifyNicklistChanged() {
        bufferNickListEye?.onNicklistChanged()
    }

    override fun toString() = "Buffer($shortName)"

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private var needsToBeNotifiedAboutGlobalPreferencesChanged = false

    companion object {
        const val PRIVATE = 2
        const val CHANNEL = 1
        const val OTHER = 0
        const val HARD_HIDDEN = -1
    }
}


private val SUPER = SuperscriptSpan()
private val SMALL = RelativeSizeSpan(0.6f)

