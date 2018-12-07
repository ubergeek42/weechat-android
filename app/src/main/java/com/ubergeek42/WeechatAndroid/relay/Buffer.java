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

import org.junit.Assert;

import java.util.ArrayList;
import java.util.Arrays;

public class Buffer {
    final private @Root Kitty kitty = Kitty.make();
    final private Kitty kitty_q = kitty.kid("?");

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

    // the following four variables are needed to determine if the buffer was changed and,
    // if not, the last two are subtracted from the newly arrived hotlist data, to make up
    // for the lines that was read in relay.
    // lastReadLineServer stores id of the last read line *in weechat*. -1 means all lines unread.
    public long lastReadLineServer = -1;
    private boolean wantsFullHotListUpdate = false; // must be false for buffers without lastReadLineServer!
    public int totalReadOthers = 0;
    public int totalReadUnreads = 0;
    public int totalReadHighlights = 0;

    volatile boolean flagResetHotMessagesOnNewOwnLine = false;

    final private Lines lines;
    final private Nicks nicks;

    public boolean isOpen = false;
    public boolean isWatched = false;

    public int type = OTHER;
    private int others = 0;
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

        if (P.isBufferOpen(fullName)) setOpen(true);
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
    @AnyThread @Cat synchronized public void setOpen(boolean open) {
        if (isOpen == open) return;
        isOpen = open;
        if (open) {
            BufferList.syncBuffer(this);
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
        Assert.assertTrue(linesAreReady());
        Assert.assertNotEquals(isWatched, watched);
        Assert.assertTrue(isOpen);
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
                if (line.highlighted) totalReadHighlights++;
                else if (line.visible && line.type == Line.LINE_MESSAGE) totalReadUnreads++;
                else if (line.visible && line.type == Line.LINE_OTHER) totalReadOthers++;
            } else {
                if (line.highlighted) {
                    highlights++;
                    Hotlist.onNewHotLine(this, line);
                    BufferList.notifyBuffersChanged();
                } else if (line.visible && line.type == Line.LINE_MESSAGE) {
                    unreads++;
                    if (type == PRIVATE) Hotlist.onNewHotLine(this, line);
                    BufferList.notifyBuffersChanged();
                } else if (line.visible && line.type == Line.LINE_OTHER) others++;
            }
        }

        // notify our listener
        if (isLast) onLineAdded();

        // if current line's an event line and we've got a speaker, move nick to fist position
        // nick in question is supposed to be in the nicks already, for we only shuffle these
        // nicks when someone spoke, i.e. NOT when user joins.
        if (isLast && nicksAreReady()) nicks.bumpNickToTop(line.speakingNick);

        if (flagResetHotMessagesOnNewOwnLine && line.type == Line.LINE_OWN) {
            flagResetHotMessagesOnNewOwnLine = false;
            resetUnreadsAndHighlights();
        }
    }

    // a buffer NOT will want a complete update if the last line unread stored in weechat buffer
    // matches the one stored in our buffer. if they are not equal, the user must've read the buffer
    // in weechat. assuming he read the very last line, total old highlights and unreads bear no meaning,
    // so they should be erased
    @WorkerThread @Cat(value="?", linger=true) synchronized void updateLastReadLine(long linePointer) {
        wantsFullHotListUpdate = lastReadLineServer != linePointer;
        kitty_q.trace("full update? %s", wantsFullHotListUpdate);
        if (wantsFullHotListUpdate) {
            setLastSeenLine(linePointer);
            lastReadLineServer = linePointer;
            totalReadHighlights = totalReadUnreads = totalReadOthers = 0;
        }
    }

    @WorkerThread public void setLastSeenLine(long pointer) {
        lines.setLastSeenLine(pointer);
    }

    @AnyThread public long getLastSeenLine() {
        return lines.getLastSeenLine();
    }

    // buffer will want full updates if it doesn't have a last read line
    // that can happen if the last read line is so far in the queue it got erased (past 4096 lines or so)
    // in most cases, that is OK with us, but in rare cases when the buffer was READ in weechat BUT
    // has lost its last read lines again our read count will not have any meaning. AND it might happen
    // that our number is actually HIGHER than amount of unread lines in the buffer. as a workaround,
    // we check that we are not getting negative numbers. not perfect, but—!
     @WorkerThread @Cat("?") synchronized void updateHotList(int highlights, int unreads, int others) {
        if (isWatched && nicksAreReady()) {
            // occasionally, when this method is called for the first time on a new connection, a
            // buffer is already watched. in this case, we don't want to lose highlights and unreads
            // as then we won't get the scroll, so we check holdsAllNicks to see if we are ready
            totalReadUnreads = unreads;
            totalReadHighlights = highlights;
            totalReadOthers = others;
        } else {
            final boolean full_update = wantsFullHotListUpdate ||
                    (totalReadUnreads > unreads) ||
                    (totalReadHighlights > highlights) ||
                    (totalReadOthers > others);
            if (full_update) {
                this.others = others;
                this.unreads = unreads;
                this.highlights = highlights;
                totalReadUnreads = totalReadHighlights = totalReadOthers = 0;
            } else {
                this.others = others - totalReadOthers;
                this.unreads = unreads - totalReadUnreads;
                this.highlights = highlights - totalReadHighlights;
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @WorkerThread private void onLineAdded() {
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
        }
        bufferEye.onPropertiesChanged();
    }

    @WorkerThread @Cat void onBufferClosed() {
        synchronized(this) {
            highlights = unreads = others = 0;
            Hotlist.adjustHotListForBuffer(this);
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
        final String number = Integer.toString(this.number) + " ";
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
        if ((unreads | highlights | others) == 0) return;
        totalReadUnreads += unreads;
        totalReadHighlights += highlights;
        totalReadOthers += others;
        unreads = highlights = others = 0;
        Hotlist.adjustHotListForBuffer(this);
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

