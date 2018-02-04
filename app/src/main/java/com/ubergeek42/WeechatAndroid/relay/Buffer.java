package com.ubergeek42.WeechatAndroid.relay;

import android.annotation.SuppressLint;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
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

import org.greenrobot.eventbus.EventBus;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

public class Buffer {
    final private @Root Kitty kitty = Kitty.make();
    final private Kitty kitty_q = kitty.kid("?");

    final public static int PRIVATE = 2;
    final public static int CHANNEL = 1;
    final public static int OTHER = 0;
    final public static int HARD_HIDDEN = -1;

    private BufferEye bufferEye;
    private BufferNicklistEye bufferNickListEye;

    public final long pointer;
    public String fullName, shortName, title;
    public int number, notifyLevel;
    public Hashtable localVars;

    /** the following four variables are needed to determine if the buffer was changed and,
     ** if not, the last two are subtracted from the newly arrived hotlist data, to make up
     ** for the lines that was read in relay.
     ** lastReadLineServer stores id of the last read line *in weechat*. -1 means all lines unread. */
    public long lastReadLineServer = -1;
    public boolean wantsFullHotListUpdate = false; // must be false for buffers without lastReadLineServer!
    public int totalReadOthers = 0;
    public int totalReadUnreads = 0;
    public int totalReadHighlights = 0;

    public Lines lines;

    private LinkedList<Nick> nicks = new LinkedList<>();

    public boolean isOpen = false;
    public boolean isWatched = false;
    public boolean holdsAllNicks = false;
    public int type = OTHER;
    public int others = 0;
    public int unreads = 0;
    public int highlights = 0;

    public Spannable printable = null; // printable buffer without title (for TextView)
    public Spannable printableWithTitle = null; // printable buffer with title
    public Spannable titleSpannable = null;
    public Line titleLine;

    Buffer(long pointer, int number, String fullName, String shortName, String title, int notifyLevel, Hashtable localVars) {
        this.pointer = pointer;
        this.number = number;
        this.fullName = fullName;
        this.shortName = (shortName != null) ? shortName : fullName;
        this.title = title;
        this.notifyLevel = notifyLevel;
        this.localVars = localVars;
        kitty.setPrefix(this.shortName);

        processBufferType();
        processBufferTitle();

        this.lines = new Lines(shortName);

        if (P.isBufferOpen(fullName)) setOpen(true);
        P.restoreLastReadLine(this);
        kitty.trace("→ Buffer(number=%s, fullName=%s) isOpen? %s", number, fullName, isOpen);
    }

    public String hexPointer() {
        return String.format("0x%x", pointer);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// LINES
    ////////////////////////////////////////////////////////////////////////////////////////////////    stuff called by the UI
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /** get a copy of all 200 lines or of lines that are not filtered using weechat filters.
     ** better call off the main thread */
    synchronized public @NonNull ArrayList<Line> getLinesCopy() {
        return lines.getCopy();
    }

    /** get a copy of last used nicknames
     ** to be used by tab completion thingie */
    synchronized public @NonNull String[] getLastUsedNicksCopy() {
        String[] out = new String[nicks.size()];
        int i = 0;
        for (Nick nick : nicks) out[i++] = nick.name;
        return out;
    }

    /** sets buffer as open or closed
     ** an open buffer is such that:
     **     has processed lines and processes lines as they come by
     **     is synced
     **     is marked as "open" in the buffer list fragment or wherever
     ** that's it, really
     ** can be called multiple times without harm
     ** somewhat heavy, better be called off the main thread */
    @Cat synchronized public void setOpen(boolean open) {
        if (this.isOpen == open) return;
        this.isOpen = open;
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
                holdsAllNicks = false;
                lines.clear();
                nicks.clear();
            }
        }
        BufferList.notifyBuffersChanged();
    }

    /** set buffer eye, i.e. something that watches buffer events
     ** also requests all lines and nicknames, if needed (usually only done once per buffer)
     ** we are requesting it here and not in setOpen() because:
     **     when the process gets killed and restored, we want to receive messages, including
     **     notifications, for that buffer. BUT the user might not visit that buffer at all.
     **     so we request lines and nicks upon user actually (getting close to) opening the buffer.
     ** we are requesting nicks along with the lines because:
     **     nick completion */
    @Cat synchronized public void setBufferEye(@Nullable BufferEye bufferEye) {
        this.bufferEye = bufferEye;
        if (bufferEye != null) {
            if (lines.status == null) requestMoreLines();
            if (!holdsAllNicks) BufferList.requestNicklistForBufferByPointer(pointer);
            if (needsToBeNotifiedAboutGlobalPreferencesChanged) bufferEye.onGlobalPreferencesChanged(false);
        }
    }

    synchronized public void requestMoreLines() {
        lines.onMoreLinesRequested();
        BufferList.requestLinesForBufferByPointer(pointer, lines.getMaxLines());
    }

    // tells buffer whether it is fully display on screen
    // called after setOpen(true) and before setOpen(false)
    // lines must be ready!
    // affects the way buffer advertises highlights/unreads count and notifications */
    @Cat synchronized public void setWatched(boolean watched) {
        Assert.assertTrue(lines.ready());
        Assert.assertNotEquals(isWatched, watched);
        Assert.assertTrue(isOpen);
        isWatched = watched;
        if (watched) resetUnreadsAndHighlights();
        else lines.rememberCurrentSkipsOffset();
    }

    @MainThread synchronized public void moveReadMarkerToEnd() {
        lines.moveReadMarkerToEnd();
        if (P.hotlistSync) EventBus.getDefault().post(new Events.SendMessageEvent(String.format(
                "input %1$s /buffer set hotlist -1\n" +
                "input %1$s /input set_unread_current_buffer", hexPointer())));
    }

    synchronized public boolean isHot() {
        return (type == Buffer.PRIVATE && unreads > 0) || highlights > 0;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// stuff called by message handlers
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Cat("??") synchronized public void addLine(final Line line, final boolean isLast) {
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
                    BufferList.newHotLine(this, line);
                    BufferList.notifyBuffersChanged();
                } else if (line.visible && line.type == Line.LINE_MESSAGE) {
                    unreads++;
                    if (type == PRIVATE) BufferList.newHotLine(this, line);
                    BufferList.notifyBuffersChanged();
                } else if (line.visible && line.type == Line.LINE_OTHER) others++;
            }
        }

        // notify our listener
        if (isLast) onLineAdded();

        // if current line's an event line and we've got a speaker, move nick to fist position
        // nick in question is supposed to be in the nicks already, for we only shuffle these
        // nicks when someone spoke, i.e. NOT when user joins.
        if (holdsAllNicks && isLast) {
            String name = line.speakingNick;
            if (name != null)
                for (Iterator<Nick> it = nicks.iterator(); it.hasNext(); ) {
                    Nick nick = it.next();
                    if (name.equals(nick.name)) {
                        it.remove();
                        nicks.addFirst(nick);
                        break;
                    }
                }
        }
    }

    /** a buffer NOT will want a complete update if the last line unread stored in weechat buffer
     ** matches the one stored in our buffer. if they are not equal, the user must've read the buffer
     ** in weechat. assuming he read the very last line, total old highlights and unreads bear no meaning,
     ** so they should be erased. */
    @Cat(value="?", linger=true) synchronized public void updateLastReadLine(long linePointer) {
        wantsFullHotListUpdate = lastReadLineServer != linePointer;
        kitty_q.trace("full update? %s", wantsFullHotListUpdate);
        if (wantsFullHotListUpdate) {
            lines.setLastSeenLine(linePointer);
            lastReadLineServer = linePointer;
            totalReadHighlights = totalReadUnreads = totalReadOthers = 0;
        }
    }

    /** buffer will want full updates if it doesn't have a last read line
     ** that can happen if the last read line is so far in the queue it got erased (past 4096 lines or so)
     ** in most cases, that is OK with us, but in rare cases when the buffer was READ in weechat BUT
     ** has lost its last read lines again our read count will not have any meaning. AND it might happen
     ** that our number is actually HIGHER than amount of unread lines in the buffer. as a workaround,
     ** we check that we are not getting negative numbers. not perfect, but—! */
     @Cat("?") synchronized public void updateHighlightsAndUnreads(int highlights, int unreads, int others) {
        if (isWatched && holdsAllNicks) {
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

            int hots = this.highlights;
            if (type == PRIVATE) hots += this.unreads;
            BufferList.adjustHotMessagesForBuffer(this, hots);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    synchronized public void onLineAdded() {
        if (bufferEye != null) bufferEye.onLineAdded();
    }

    private boolean needsToBeNotifiedAboutGlobalPreferencesChanged = false;
    @UiThread synchronized void onGlobalPreferencesChanged(boolean numberChanged) {
        if (bufferEye != null) bufferEye.onGlobalPreferencesChanged(numberChanged);
        else needsToBeNotifiedAboutGlobalPreferencesChanged = true;
    }

    synchronized public void onLinesListed() {
        lines.onLinesListed();
        if (bufferEye != null) bufferEye.onLinesListed();
    }

    synchronized public void onPropertiesChanged() {
        processBufferType();
        processBufferTitle();
        if (bufferEye != null) bufferEye.onPropertiesChanged();
    }

    @Cat synchronized public void onBufferClosed() {
        BufferList.removeHotMessagesForBuffer(this);
        setOpen(false);
        if (bufferEye != null) bufferEye.onBufferClosed();
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// private stuffs

    /** determine if the buffer is PRIVATE, CHANNEL, OTHER or HARD_HIDDEN
     ** hard-hidden channels do not show in any way. to hide a channel,
     ** do "/buffer set localvar_set_relay hard-hide" */
    private void processBufferType() {
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
    private final static RelativeSizeSpan SMALL1 = new RelativeSizeSpan(0.6f);
    private final static RelativeSizeSpan SMALL2 = new RelativeSizeSpan(0.6f);
    private final static int EX = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;

    private void processBufferTitle() {
        Spannable spannable;
        final String number = Integer.toString(this.number) + " ";
        spannable = new SpannableString(number + shortName);
        spannable.setSpan(SUPER, 0, number.length(), EX);
        spannable.setSpan(SMALL1, 0, number.length(), EX);
        printable = spannable;
        if (title == null || title.equals("")) {
            printableWithTitle = printable;
        } else {
            String t = Color.stripEverything(title);
            titleSpannable = new SpannableString(t);
            titleLine = new Line(-123, null, null, t, true, false, null);
            Linkify.linkify(titleSpannable);
            spannable = new SpannableString(number + shortName + "\n" + Color.stripEverything(title));
            spannable.setSpan(SUPER, 0, number.length(), EX);
            spannable.setSpan(SMALL1, 0, number.length(), EX);
            spannable.setSpan(SMALL2, number.length() + shortName.length() + 1, spannable.length(), EX);
            printableWithTitle = spannable;
        }
    }

    /** sets highlights/unreads to 0 and,
     ** if something has actually changed, notifies whoever cares about it */
    @Cat("?") synchronized private void resetUnreadsAndHighlights() {
        if ((unreads | highlights | others) == 0) return;
        totalReadUnreads += unreads;
        totalReadHighlights += highlights;
        totalReadOthers += others;
        unreads = highlights = others = 0;
        BufferList.removeHotMessagesForBuffer(this);
        BufferList.notifyBuffersChanged();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// NICKS
    ////////////////////////////////////////////////////////////////////////////////////////////////    stuff called by the UI
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /** sets and removes a single nicklist watcher
     ** used to notify of nicklist changes as new nicks arrive and others quit */
    @Cat("Nick") synchronized public void setBufferNicklistEye(@Nullable BufferNicklistEye bufferNickListEye) {
        this.bufferNickListEye = bufferNickListEye;
    }

    synchronized public @NonNull Nick[] getNicksCopy() {
        Nick[] n = nicks.toArray(new Nick[nicks.size()]);
        Arrays.sort(n, sortByNumberPrefixAndNameComparator);
        return n;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// called by event handlers
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Cat("??") synchronized public void addNick(long pointer, String prefix, String name, boolean away) {
        nicks.add(new Nick(pointer, prefix, name, away));
        notifyNicklistChanged();
    }

    @Cat("Nick") synchronized public void removeNick(long pointer) {
        for (Iterator<Nick> it = nicks.iterator(); it.hasNext();) {
            if (it.next().pointer == pointer) {
                it.remove();
                break;
            }
        }
        notifyNicklistChanged();
    }

    @Cat("Nick") synchronized public void updateNick(long pointer, String prefix, String name, boolean away) {
        for (Nick nick: nicks) {
            if (nick.pointer == pointer) {
                nick.prefix = prefix;
                nick.name = name;
                nick.away = away;
                break;
            }
        }
        notifyNicklistChanged();
    }

    synchronized public void removeAllNicks() {
        nicks.clear();
    }

    @Cat("Nick") synchronized public void sortNicksByLines() {
        final HashMap<String, Integer> nameToPosition = new HashMap<>();

        Iterator<Line> it = lines.getDescendingFilteredIterator();
        while (it.hasNext()) {
            String name = it.next().speakingNick;
            if (name != null && !nameToPosition.containsKey(name))
                nameToPosition.put(name, nameToPosition.size());
        }

        Collections.sort(nicks, new Comparator<Nick>() {
            @Override public int compare(Nick left, Nick right) {
                Integer l = nameToPosition.get(left.name);
                Integer r = nameToPosition.get(right.name);
                if (l == null) l = Integer.MAX_VALUE;
                if (r == null) r = Integer.MAX_VALUE;
                return l - r;
            }
        });
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// private stuffs

    synchronized private void notifyNicklistChanged() {
        if (bufferNickListEye != null) bufferNickListEye.onNicklistChanged();
    }

    /**
     *  this comparator sorts by prefix first
     */
    private final static Comparator<Nick> sortByNumberPrefixAndNameComparator = new Comparator<Nick>() {
        // Lower values = higher priority
        private int prioritizePrefix(String p) {
            if (p.length() == 0) return 100;
            char c = p.charAt(0);
            switch(c) {
                case '~': return 1; // Owners
                case '&': return 2; // Admins
                case '@': return 3; // Ops
                case '%': return 4; // Half-Ops
                case '+': return 5; // Voiced
            }
            return 100; // Other
        }
        @Override public int compare(Nick n1, Nick n2) {
            int p1 = prioritizePrefix(n1.prefix);
            int p2 = prioritizePrefix(n2.prefix);
            int diff = p1 - p2;
            return (diff != 0) ? diff : n1.name.compareToIgnoreCase(n2.name);
        }
    };

    @SuppressLint("DefaultLocale") @Override public String toString() {
        return String.format("[%s%-5.5s | %02d-%02d-%02d | %02d-%02d-%02d %s]",
                isOpen ? "o" : "-", shortName,
                highlights, unreads, others,
                totalReadHighlights, totalReadUnreads, totalReadOthers,
                wantsFullHotListUpdate ? "| fu " : "");
    }
}

