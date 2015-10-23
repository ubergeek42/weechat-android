package com.ubergeek42.WeechatAndroid.relay;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.SuperscriptSpan;

import com.ubergeek42.WeechatAndroid.service.P;
import com.ubergeek42.weechat.Color;
import com.ubergeek42.weechat.relay.protocol.Hashtable;
import com.ubergeek42.weechat.relay.protocol.RelayObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

public class Buffer {
    private static Logger logger = LoggerFactory.getLogger("Buffer");
    final private static boolean DEBUG_BUFFER = false;
    final private static boolean DEBUG_LINE = false;
    final private static boolean DEBUG_NICK = false;

    final public static int PRIVATE = 2;
    final public static int CHANNEL = 1;
    final public static int OTHER = 0;
    final public static int HARD_HIDDEN = -1;

    public final static int MAX_LINES = 200;

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
    public int totalReadUnreads = 0;
    public int totalReadHighlights = 0;

    // see BufferFragment.maybeMoveReadMarker()
    public long readMarkerLine = -1;
    public long lastVisibleLine = -1;

    private LinkedList<Line> lines = new LinkedList<>();
    private int visibleLinesCount = 0;

    private LinkedList<Nick> nicks = new LinkedList<>();

    public boolean isOpen = false;
    public boolean isWatched = false;
    public boolean holdsAllLines = false;
    public boolean holdsAllNicks = false;
    public int type = OTHER;
    public int unreads = 0;
    public int highlights = 0;

    public Spannable printableWithoutTitle = null; // printable buffer without title (for TextView)
    public Spannable printableWithTitle = null; // printable buffer with title

    Buffer(long pointer, int number, String fullName, String shortName, String title, int notifyLevel, Hashtable localVars) {
        this.pointer = pointer;
        this.number = number;
        this.fullName = fullName;
        this.shortName = (shortName != null) ? shortName : fullName;
        this.title = title;
        this.notifyLevel = notifyLevel;
        this.localVars = localVars;
        processBufferType();
        processBufferTitle();

        if (P.isBufferOpen(fullName)) setOpen(true);
        P.restoreLastReadLine(this);
        if (DEBUG_BUFFER) logger.warn("new Buffer(..., {}, {}, ...) isOpen? {}", number, fullName, isOpen);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// LINES
    ////////////////////////////////////////////////////////////////////////////////////////////////    stuff called by the UI
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////


    /** get a copy of all 200 lines or of lines that are not filtered using weechat filters.
     ** better call off the main thread */
    synchronized public @NonNull Line[] getLinesCopy() {
        Line[] l;
        if (!P.filterLines)
            l = lines.toArray(new Line[lines.size()]);
        else {
            l = new Line[visibleLinesCount];
            int i = 0;
            for (Line line: lines) {
                if (line.visible) l[i++] = line;
                // if read marker is on a line that is invisible
                // move it to the previous visible line
                else if (line.pointer == readMarkerLine && i > 0) readMarkerLine = l[i-1].pointer;
            }
        }
        if (l.length > 0) lastVisibleLine = l[l.length-1].pointer;
        return l;
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
    synchronized public void setOpen(boolean open) {
        if (DEBUG_BUFFER) logger.warn("{} setOpen({})", shortName, open);
        if (this.isOpen == open) return;
        this.isOpen = open;
        if (open) {
            BufferList.syncBuffer(fullName);
            for (Line line : lines) line.processMessageIfNeeded();
        }
        else {
            BufferList.desyncBuffer(fullName);
            for (Line line : lines) line.eraseProcessedMessage();
            if (P.optimizeTraffic) {
                // if traffic is optimized, the next time we open the buffer, it might have been updated
                // this presents two problems. first, we will not be able to update if we think
                // that we have all the lines needed. second, if we have lines and request lines again,
                // it'd be cumbersome to find the place to put lines. like, for iteration #3,
                // [[old lines] [#3] [#2] [#1]] unless #3 is inside old lines. hence, reset everything!
                holdsAllLines = holdsAllNicks = false;
                lines.clear();
                nicks.clear();
                visibleLinesCount = 0;
            }
        }
        BufferList.notifyBuffersSlightlyChanged();
    }

    /** set buffer eye, i.e. something that watches buffer events
     ** also requests all lines and nicknames, if needed (usually only done once per buffer)
     ** we are requesting it here and not in setOpen() because:
     **     when the process gets killed and restored, we want to receive messages, including
     **     notifications, for that buffer. BUT the user might not visit that buffer at all.
     **     so we request lines and nicks upon user actually (getting close to) opening the buffer.
     ** we are requesting nicks along with the lines because:
     **     nick completion */
    synchronized public void setBufferEye(@Nullable BufferEye bufferEye) {
        if (DEBUG_BUFFER) logger.warn("{} setBufferEye({})", shortName, bufferEye);
        this.bufferEye = bufferEye;
        if (bufferEye != null) {
            if (!holdsAllLines) BufferList.requestLinesForBufferByPointer(pointer);
            if (!holdsAllNicks) BufferList.requestNicklistForBufferByPointer(pointer);
        }
    }

    /** tells Buffer if it is ACTIVELY display on screen
     ** affects the way buffer advertises highlights/unreads count and notifications
     ** can be called multiple times without harm */
    synchronized public void setWatched(boolean watched) {
        if (DEBUG_BUFFER) logger.error("{} setWatched({})", shortName, watched);
        if (isWatched == watched) return;
        isWatched = watched;
        if (watched) resetUnreadsAndHighlights();
    }

    /** called when options has changed and the messages should be processed */
    synchronized public void forceProcessAllMessages() {
        if (DEBUG_BUFFER) logger.error("{} forceProcessAllMessages()", shortName);
        for (Line line : lines) line.processMessage();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// stuff called by message handlers
    ////////////////////////////////////////////////////////////////////////////////////////////////

    synchronized public void addLine(final Line line, final boolean isLast) {
        if (DEBUG_LINE) logger.warn("{} addLine('{}', {})", shortName, line.message, isLast);

        // check if the line in question is already in the buffer
        // happens when reverse request throws in lines even though some are already here
        for (Line l: lines) if (l.pointer == line.pointer) return;

        // remove a line if we are over the limit and add the new line
        // correct visibleLinesCount accordingly
        if (lines.size() >= MAX_LINES) if (lines.removeFirst().visible) visibleLinesCount--;
        if (isLast) lines.add(line);
        else lines.addFirst(line);
        if (line.visible) visibleLinesCount++;

        // calculate spannable, if needed
        if (isOpen) line.processMessage();

        // for messages that ARRIVE AS WE USE THE APPLICATION:
        // set unreads / highlights and notify BufferList
        // if the number of messages has increased, something will be wise enough to use
        //      provided by setMostRecentHotLine()
        // we are not using OLDER messages arriving from reverse request as well because
        //      unreads and highlights is filled by hotlist request
        //
        // if the buffer IS watched, remember that the lines in question are read
        if (isLast && notifyLevel >= 0 && type != HARD_HIDDEN) {
            if (!isWatched) {
                if (line.highlighted) {
                    highlights++;
                    BufferList.newHotLine(this, line);
                    BufferList.notifyBuffersSlightlyChanged(type == OTHER);
                } else if (line.type == Line.LINE_MESSAGE) {
                    unreads++;
                    if (type == PRIVATE) BufferList.newHotLine(this, line);
                    BufferList.notifyBuffersSlightlyChanged(type == OTHER);
                }
            }
            else {
                if (line.highlighted) totalReadHighlights++;
                else if (line.type == Line.LINE_MESSAGE) totalReadUnreads++;
            }
        }

        // notify our listener
        onLinesChanged();

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

        if (lines.size() >= MAX_LINES) holdsAllLines = true;
    }

    /** a buffer NOT will want a complete update if the last line unread stored in weechat buffer
     ** matches the one stored in our buffer. if they are not equal, the user must've read the buffer
     ** in weechat. assuming he read the very last line, total old highlights and unreads bear no meaning,
     ** so they should be erased. */
    synchronized public void updateLastReadLine(long linePointer) {
        wantsFullHotListUpdate = lastReadLineServer != linePointer;
        if (wantsFullHotListUpdate) {
            lastReadLineServer = linePointer;
            readMarkerLine = linePointer;
            totalReadHighlights = totalReadUnreads = 0;
        }
    }

    /** buffer will want full updates if it doesn't have a last read line
     ** that can happen if the last read line is so far in the queue it got erased (past 4096 lines or so)
     ** in most cases, that is OK with us, but in rare cases when the buffer was READ in weechat BUT
     ** has lost its last read lines again our read count will not have any meaning. AND it might happen
     ** that our number is actually HIGHER than amount of unread lines in the buffer. as a workaround,
     ** we check that we are not getting negative numbers. not perfect, but—! */
     synchronized public void updateHighlightsAndUnreads(int highlights, int unreads) {
        if (isWatched) {
            totalReadUnreads = unreads;
            totalReadHighlights = highlights;
        } else {
            final boolean full_update = wantsFullHotListUpdate ||
                    (totalReadUnreads > unreads) ||
                    (totalReadHighlights > highlights);
            if (full_update) {
                this.unreads = unreads;
                this.highlights = highlights;
                totalReadUnreads = totalReadHighlights = 0;
            } else {
                this.unreads = unreads - totalReadUnreads;
                this.highlights = highlights - totalReadHighlights;
            }

            int hots = this.highlights;
            if (type == PRIVATE) hots += this.unreads;
            BufferList.adjustHotMessagesForBuffer(this, hots);
        }
    }

    synchronized public void onLinesChanged() {
        if (bufferEye != null) bufferEye.onLinesChanged();
    }

    synchronized public void onLinesListed() {
        holdsAllLines = true;
        if (bufferEye != null) bufferEye.onLinesListed();
    }

    synchronized public void onPropertiesChanged() {
        processBufferType();
        processBufferTitle();
        if (bufferEye != null) bufferEye.onPropertiesChanged();
    }

    synchronized public void onBufferClosed() {
        if (DEBUG_BUFFER) logger.warn("{} onBufferClosed()", shortName);
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
        printableWithoutTitle = spannable;
        if (title == null || title.equals("")) {
            printableWithTitle = printableWithoutTitle;
        } else {
            spannable = new SpannableString(number + shortName + "\n" + Color.stripEverything(title));
            spannable.setSpan(SUPER, 0, number.length(), EX);
            spannable.setSpan(SMALL1, 0, number.length(), EX);
            spannable.setSpan(SMALL2, number.length() + shortName.length() + 1, spannable.length(), EX);
            printableWithTitle = spannable;
        }
    }

    /** sets highlights/unreads to 0 and,
     ** if something has actually changed, notifies whoever cares about it */
    synchronized public void resetUnreadsAndHighlights() {
        if (DEBUG_BUFFER) logger.error("{} resetUnreadsAndHighlights()", shortName);
        if ((unreads | highlights) == 0) return;
        totalReadUnreads += unreads;
        totalReadHighlights += highlights;
        unreads = highlights = 0;
        BufferList.removeHotMessagesForBuffer(this);
        BufferList.notifyBuffersSlightlyChanged(type == OTHER);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// NICKS
    ////////////////////////////////////////////////////////////////////////////////////////////////    stuff called by the UI
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /** sets and removes a single nicklist watcher
     ** used to notify of nicklist changes as new nicks arrive and others quit */
    synchronized public void setBufferNicklistEye(@Nullable BufferNicklistEye bufferNickListEye) {
        if (DEBUG_NICK) logger.warn("{} setBufferNicklistEye({})", shortName, bufferNickListEye);
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

    synchronized public void addNick(long pointer, String prefix, String name) {
        if (DEBUG_NICK) logger.debug("{} addNick({}, {}, {})", shortName, pointer, prefix, name);
        nicks.add(new Nick(pointer, prefix, name));
        notifyNicklistChanged();
    }

    synchronized public void removeNick(long pointer) {
        if (DEBUG_NICK) logger.debug("{} removeNick({})", new Object[]{shortName, pointer});
        for (Iterator<Nick> it = nicks.iterator(); it.hasNext();) {
            if (it.next().pointer == pointer) {
                it.remove();
                break;
            }
        }
        notifyNicklistChanged();
    }

    synchronized public void updateNick(long pointer, String prefix, String name) {
        if (DEBUG_NICK) logger.debug("{} updateNick({}, {}, {})", shortName, pointer, prefix, name);
        for (Nick nick: nicks) {
            if (nick.pointer == pointer) {
                nick.prefix = prefix;
                nick.name = name;
                break;
            }
        }
        notifyNicklistChanged();
    }

    synchronized public void removeAllNicks() {
        nicks.clear();
    }

    synchronized public void sortNicksByLines() {
        if (DEBUG_NICK) logger.debug("{} sortNicksByLines({})", shortName);
        final HashMap<String, Integer> nameToPosition = new HashMap<>();

        for (int i = lines.size() - 1; i >= 0; i--) {
            String name = lines.get(i).speakingNick;
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
}

