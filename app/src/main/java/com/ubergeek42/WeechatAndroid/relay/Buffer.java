package com.ubergeek42.WeechatAndroid.relay;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.text.style.SuperscriptSpan;

import com.ubergeek42.WeechatAndroid.service.Events;
import com.ubergeek42.WeechatAndroid.service.P;
import com.ubergeek42.WeechatAndroid.utils.Linkify;
import com.ubergeek42.cats.Cat;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;
import com.ubergeek42.weechat.Color;
import com.ubergeek42.weechat.relay.protocol.Hashtable;
import com.ubergeek42.weechat.relay.protocol.RelayObject;

import java.util.ArrayList;
import java.util.Arrays;

import static com.ubergeek42.WeechatAndroid.relay.BufferList.LAST_READ_LINE_MISSING;
import static com.ubergeek42.WeechatAndroid.utils.Assert.assertThat;

public class Buffer {
    final private @Root Kitty kitty = Kitty.make();
    final private Kitty kitty_hot = kitty.kid("Hot");

    final public static int PRIVATE = 2;
    final private static int CHANNEL = 1;
    final public static int OTHER = 0;
    final public static int HARD_HIDDEN = -1;

    private BufferEye bufferEye;
    private BufferNicklistEye bufferNickListEye;

    public final long pointer;
    public String fullName, shortName, title;
    public int number;
    private int notifyLevel;
    Hashtable localVars;
    public boolean hidden;

    public long lastReadLineServer = LAST_READ_LINE_MISSING;
    public int readUnreads = 0;
    public int readHighlights = 0;

    // number of hotlist updates while syncing this buffer. if >= 2, when the new update arrives, we
    // keep own unreads/highlights as they have been correct since the last update
    private int hotlistUpdatesWhileSyncing = 0;

    volatile boolean flagResetHotMessagesOnNewOwnLine = false;

    final private Lines lines;
    final private Nicks nicks;

    public boolean isOpen = false;
    public boolean isWatched = false;

    public int type = OTHER;
    public int unreads = 0;
    public int highlights = 0;

    public Spannable printable = null; // printable buffer without title (for TextView)
    public Line titleLine;

    @WorkerThread Buffer(long pointer, int number, String fullName, String shortName, String title, int notifyLevel, Hashtable localVars, boolean hidden) {
        this.pointer = pointer;
        this.number = number;
        this.fullName = fullName;
        this.shortName = (shortName != null) ? shortName : fullName;
        this.title = title;
        this.notifyLevel = notifyLevel;
        this.localVars = localVars;
        this.hidden = hidden;
        kitty.setPrefix(this.shortName);

        bufferEye = detachedEye;

        processBufferType();
        processBufferTitle();

        lines = new Lines(shortName);
        nicks = new Nicks(shortName);

        if (P.isBufferOpen(pointer)) setOpen(true, false);
        P.restoreLastReadLine(this);
        kitty.trace("→ Buffer(number=%s, fullName=%s) isOpen? %s", number, fullName, isOpen);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// LINES
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // get a copy of lines, filtered or not according to global settings
    // contains read marker and header
    @AnyThread synchronized public @NonNull ArrayList<Line> getLinesCopy() {
        return lines.getCopy();
    }

    @AnyThread public boolean linesAreReady() {
        return lines.status.ready();
    }

    @AnyThread public @NonNull Lines.STATUS getLinesStatus() {
        return lines.status;
    }

    // sets buffer as open or closed
    // an open buffer is such that:
    //     has processed lines and processes lines as they come by
    //     is synced
    //     is marked as "open" in the buffer list fragment or wherever
    @AnyThread @Cat synchronized public void setOpen(boolean open, boolean syncHotlistOnOpen) {
        if (isOpen == open) return;
        isOpen = open;
        if (open) {
            BufferList.syncBuffer(this, syncHotlistOnOpen);
            lines.processAllMessages(false);
        } else {
            BufferList.desyncBuffer(this);
            lines.eraseProcessedMessages();
            if (P.optimizeTraffic) {
                // if traffic is optimized, the next time we open the buffer, it might have been updated
                // this presents two problems. first, we will not be able to update if we think
                // that we have all the lines needed. second, if we have lines and request lines again,
                // it'd be cumbersome to find the place to put lines. like, for iteration #3,
                // [[old lines] [#3] [#2] [#1]] unless #3 is inside old lines. hence, reset everything!
                lines.clear();
                nicks.clear();
                hotlistUpdatesWhileSyncing = 0;
            }
        }
        BufferList.notifyBuffersChanged();
    }

    // set buffer eye, i.e. something that watches buffer events
    // also requests all lines and nicknames, if needed (usually only done once per buffer)
    // we are requesting it here and not in setOpen() because:
    //     when the process gets killed and restored, we want to receive messages, including
    //     notifications, for that buffer. BUT the user might not visit that buffer at all.
    //     so we request lines and nicks upon user actually (getting close to) opening the buffer.
    // we are requesting nicks along with the lines because:
    //     nick completion
    @MainThread @Cat synchronized public void setBufferEye(@Nullable BufferEye bufferEye) {
        this.bufferEye = bufferEye == null ? detachedEye : bufferEye;
        if (bufferEye != null) {
            if (lines.status == Lines.STATUS.INIT) requestMoreLines();
            if (nicks.status == Nicks.STATUS.INIT) BufferList.requestNicklistForBufferByPointer(pointer);
            if (needsToBeNotifiedAboutGlobalPreferencesChanged) {
                bufferEye.onGlobalPreferencesChanged(false);
                needsToBeNotifiedAboutGlobalPreferencesChanged = false;
            }
        }
    }

    @MainThread synchronized public void requestMoreLines() {
        lines.onMoreLinesRequested();
        BufferList.requestLinesForBufferByPointer(pointer, lines.getMaxLines());
    }

    // tells buffer whether it is fully display on screen
    // called after setOpen(true) and before setOpen(false)
    // lines must be ready!
    // affects the way buffer advertises highlights/unreads count and notifications */
    @MainThread @Cat synchronized public void setWatched(boolean watched) {
        assertThat(linesAreReady()).isTrue();
        assertThat(isWatched).isNotEqualTo(watched);
        assertThat(isOpen).isTrue();
        isWatched = watched;
        if (watched) resetUnreadsAndHighlights();
        else lines.rememberCurrentSkipsOffset();
    }

    @MainThread synchronized public void moveReadMarkerToEnd() {
        lines.moveReadMarkerToEnd();
        if (P.hotlistSync) Events.SendMessageEvent.fire(
                "input 0x%1$x /buffer set hotlist -1\n" +
                "input 0x%1$x /input set_unread_current_buffer", pointer);
    }

    @AnyThread synchronized public int getHotCount() {
        return type == Buffer.PRIVATE ? unreads + highlights : highlights;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// stuff called by message handlers
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @WorkerThread @Cat("??") synchronized void addLine(final Line line, final boolean isLast) {
        // check if the line in question is already in the buffer
        // happens when reverse request throws in lines even though some are already here
        if (lines.contains(line)) return;

        if (isLast) lines.addLast(line); else lines.addFirst(line);

        // calculate spannable, if needed
        if (isOpen) line.processMessage();

        // notify levels: 0 none 1 highlight 2 message 3 all
        // treat hidden lines and lines that are not supposed to generate a “notification” as read
        if (isLast) {
            if (isWatched || type == HARD_HIDDEN || (P.filterLines && !line.visible) ||
                    (notifyLevel == 0) || (notifyLevel == 1 && !line.highlighted)) {
                if (line.highlighted) readHighlights++;
                else if (line.visible && line.type == Line.LINE_MESSAGE) readUnreads++;
            } else {
                if (line.highlighted) {
                    highlights++;
                    Hotlist.onNewHotLine(this, line);
                    BufferList.notifyBuffersChanged();
                } else if (line.visible && line.type == Line.LINE_MESSAGE) {
                    unreads++;
                    if (type == PRIVATE) Hotlist.onNewHotLine(this, line);
                    BufferList.notifyBuffersChanged();
                }
            }
        }

        // if current line's an event line and we've got a speaker, move nick to fist position
        // nick in question is supposed to be in the nicks already, for we only shuffle these
        // nicks when someone spoke, i.e. NOT when user joins.
        if (isLast && nicksAreReady()) nicks.bumpNickToTop(line.getNick());

        if (flagResetHotMessagesOnNewOwnLine && line.type == Line.LINE_OWN) {
            flagResetHotMessagesOnNewOwnLine = false;
            resetUnreadsAndHighlights();
        }
    }

    @WorkerThread public void setLastSeenLine(long pointer) {
        lines.setLastSeenLine(pointer);
    }

    @AnyThread public long getLastSeenLine() {
        return lines.getLastSeenLine();
    }

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
    @WorkerThread @Cat(value="Hot") synchronized boolean updateHotlist(
            int newHighlights, int newUnreads, long lastReadLine, long timeSinceLastHotlistUpdate) {
        boolean bufferHasBeenReadInWeechat = false;
        boolean syncedSinceLastUpdate = false;

        kitty_hot.tracel(shortName);
        kitty_hot.tracel("[U%s+%s H%s+%s]", readUnreads, unreads, readHighlights, highlights);

        if (isOpen || !P.optimizeTraffic) {
            hotlistUpdatesWhileSyncing++;
            syncedSinceLastUpdate = hotlistUpdatesWhileSyncing >= 2;
        }

        if (lastReadLine != lastReadLineServer) {
            setLastSeenLine(lastReadLine);
            lastReadLineServer = lastReadLine;
            if (lastReadLine != LAST_READ_LINE_MISSING ||
                    timeSinceLastHotlistUpdate > 10 * 60 * 1000) bufferHasBeenReadInWeechat = true;
        }

        kitty_hot.tracel("<watched=%s, synced=%s, read=%s>", isWatched, syncedSinceLastUpdate, bufferHasBeenReadInWeechat);

        boolean fullUpdate = !syncedSinceLastUpdate && bufferHasBeenReadInWeechat;
        if (!fullUpdate) {
            if (syncedSinceLastUpdate) {
                kitty_hot.tracel("diff/synced");
                readUnreads = newUnreads - unreads;
                readHighlights = newHighlights - highlights;
            } else {
                kitty_hot.tracel("diff/unsynced");
                unreads = newUnreads - readUnreads;
                highlights = newHighlights - readHighlights;
            }
        }

        if (fullUpdate || readUnreads < 0 || readHighlights < 0 || unreads < 0 || highlights < 0) {
            kitty_hot.tracel("full");
            unreads = newUnreads;
            highlights = newHighlights;
            readUnreads = readHighlights = 0;
        }

        kitty_hot.trace("[U%s+%s H%s+%s]", readUnreads, unreads, readHighlights, highlights);

        assertThat(unreads + readUnreads).isEqualTo(newUnreads);
        assertThat(highlights + readHighlights).isEqualTo(newHighlights);

        return fullUpdate;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @WorkerThread void onLineAdded() {
        bufferEye.onLineAdded();
    }

    @MainThread void onGlobalPreferencesChanged(boolean numberChanged) {
        synchronized (this) {lines.processAllMessages(!numberChanged);}
        bufferEye.onGlobalPreferencesChanged(numberChanged);
    }

    @WorkerThread void onLinesListed() {
        synchronized (this) {lines.onLinesListed();}
        bufferEye.onLinesListed();
    }

    @WorkerThread void onPropertiesChanged() {
        synchronized (this) {
            processBufferType();
            processBufferTitle();
            Hotlist.adjustHotListForBuffer(this, false);   // update buffer names in the notifications
        }
        bufferEye.onPropertiesChanged();
    }

    @WorkerThread @Cat void onBufferClosed() {
        synchronized(this) {
            highlights = unreads = 0;
            Hotlist.adjustHotListForBuffer(this, true);
        }
        bufferEye.onBufferClosed();
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// private stuffs

    // determine if the buffer is PRIVATE, CHANNEL, OTHER or HARD_HIDDEN
    // hard-hidden channels do not show in any way. to hide a channel,
    // do "/buffer set localvar_set_relay hard-hide"
    @WorkerThread private void processBufferType() {
        RelayObject t;
        t = localVars.get("relay");
        if (t != null && Arrays.asList(t.asString().split(",")).contains("hard-hide"))
            type = HARD_HIDDEN;
        else {
            t = localVars.get("type");
            if (t == null) type = OTHER;
            else if ("private".equals(t.asString())) type = PRIVATE;
            else if ("channel".equals(t.asString())) type = CHANNEL;
            else type = OTHER;
        }
    }

    private final static SuperscriptSpan SUPER = new SuperscriptSpan();
    private final static RelativeSizeSpan SMALL = new RelativeSizeSpan(0.6f);
    private final static int EX = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;

    @WorkerThread private void processBufferTitle() {
        Spannable spannable;
        final String number = this.number + " ";
        spannable = new SpannableString(number + shortName);
        spannable.setSpan(SUPER, 0, number.length(), EX);
        spannable.setSpan(SMALL, 0, number.length(), EX);
        printable = spannable;
        if (!TextUtils.isEmpty(title)) {
            titleLine = new Line(-123, null, null, title, true, false, null);
            SpannableString titleSpannable = new SpannableString(Color.stripEverything(title));
            Linkify.linkify(titleSpannable);
            titleLine.spannable = titleSpannable;
        }
    }

    // sets highlights/unreads to 0 and,
    // if something has actually changed, notifies whoever cares about it
    @AnyThread @Cat("?") synchronized private void resetUnreadsAndHighlights() {
        if ((unreads | highlights) == 0) return;
        readUnreads += unreads;
        readHighlights += highlights;
        unreads = highlights = 0;
        Hotlist.adjustHotListForBuffer(this, true);
        BufferList.notifyBuffersChanged();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// NICKS
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @AnyThread boolean nicksAreReady() {
        return nicks.status == Nicks.STATUS.READY;
    }

    @MainThread synchronized public @NonNull ArrayList<String> getMostRecentNicksMatching(String prefix) {
        return nicks.getMostRecentNicksMatching(prefix);
    }

    @AnyThread synchronized public @NonNull ArrayList<Nick> getNicksCopySortedByPrefixAndName() {
        return nicks.getCopySortedByPrefixAndName();
    }

    @MainThread @Cat("??")
    synchronized public void setBufferNicklistEye(@Nullable BufferNicklistEye bufferNickListEye) {
        this.bufferNickListEye = bufferNickListEye;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @WorkerThread @Cat("??") synchronized void addNick(Nick nick) {
        nicks.addNick(nick);
        notifyNicklistChanged();
    }

    @WorkerThread @Cat("??") synchronized void removeNick(long pointer) {
        nicks.removeNick(pointer);
        notifyNicklistChanged();
    }

    @WorkerThread @Cat("??") synchronized void updateNick(Nick nick) {
        nicks.updateNick(nick);
        notifyNicklistChanged();
    }

    @WorkerThread synchronized void removeAllNicks() {
        nicks.clear();
    }

    @WorkerThread @Cat("??") synchronized void onNicksListed() {
        nicks.sortNicksByLines(lines.getDescendingFilteredIterator());
    }

    @WorkerThread private void notifyNicklistChanged() {
        if (bufferNickListEye != null) bufferNickListEye.onNicklistChanged();
    }

    @Override public @NonNull String toString() {
        return "Buffer(" + shortName + ")";
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private boolean needsToBeNotifiedAboutGlobalPreferencesChanged = false;

    private final BufferEye detachedEye = new BufferEye() {
        @Override public void onLinesListed() {}
        @Override public void onLineAdded() {}
        @Override public void onPropertiesChanged() {}
        @Override public void onBufferClosed() {}
        @Override public void onGlobalPreferencesChanged(boolean numberChanged) {
            needsToBeNotifiedAboutGlobalPreferencesChanged = true;
        }
    };
}

