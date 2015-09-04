package com.ubergeek42.WeechatAndroid.service;

import android.graphics.Typeface;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.SuperscriptSpan;
import android.text.style.UnderlineSpan;

import com.ubergeek42.WeechatAndroid.utils.Linkify;
import com.ubergeek42.weechat.Color;
import com.ubergeek42.weechat.ColorScheme;
import com.ubergeek42.weechat.relay.protocol.Hashtable;
import com.ubergeek42.weechat.relay.protocol.RelayObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

public class Buffer {
    private static Logger logger = LoggerFactory.getLogger("Buffer");
    final private static boolean DEBUG_BUFFER = false;
    final private static boolean DEBUG_LINE = false;
    final private static boolean DEBUG_NICK = false;

    //prefs
    public static boolean FILTER_LINES = false;

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
     ** if not, the last two are substracted from the newly arrived hotlist data, to make up
     ** for the lines that was read in relay.
     ** lastReadLine stores id of the last read line *in weechat*. -1 means all lines unread. */
    public long lastReadLine = -1;
    public boolean wantsFullHotListUpdate = false; // must be false for buffers without lastReadLine!
    public int totalReadUnreads = 0;
    public int totalReadHighlights = 0;

    // This is used purely by the GUI to show a last line read marker
    // It is a buffer line id(pointer)
    public long uiLastViewedLine = -1;


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

        if (BufferList.isSynced(fullName)) setOpen(true);
        BufferList.restoreLastReadLine(this);
        if (DEBUG_BUFFER) logger.warn("new Buffer(..., {}, {}, ...) isOpen? {}", new Object[]{number, fullName, isOpen});
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
        if (!FILTER_LINES)
            return lines.toArray(new Line[lines.size()]);
        else {
            Line[] l = new Line[visibleLinesCount];
            int i = 0;
            for (Line line: lines) if (line.visible) l[i++] = line;
            return l;
        }
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
            if (BufferList.OPTIMIZE_TRAFFIC) {
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

    public void setLastViewedLine(long id) {
        uiLastViewedLine = id;
    }
    public long getLastViewedLine() {
        return uiLastViewedLine;
    }
    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// stuff called by message handlers
    ////////////////////////////////////////////////////////////////////////////////////////////////

    synchronized public void addLine(final Line line, final boolean isLast) {
        if (DEBUG_LINE) logger.warn("{} addLine('{}', {})", new Object[]{shortName, line.message, isLast});

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
            String name = line.findSpeakingNick();
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
        wantsFullHotListUpdate = lastReadLine != linePointer;
        if (wantsFullHotListUpdate) {
            lastReadLine = linePointer;
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
     ** hard-hidden channels do not show in any way. to hide a cannel,
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
        if (DEBUG_NICK) logger.debug("{} addNick({}, {}, {})", new Object[]{shortName, pointer, prefix, name});
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
        if (DEBUG_NICK) logger.debug("{} updateNick({}, {}, {})", new Object[]{shortName, pointer, prefix, name});
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
            String name = lines.get(i).findSpeakingNick();
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

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// Buffer.Line CLASS
    ////////////////////////////////////////////////////////////////////////////////////////////////    really should've put that into a separate file, but—
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class Line {


        // preferences for all lines
        public static float TEXT_SIZE = 10;
        public static @Nullable DateFormat DATEFORMAT = new SimpleDateFormat("HH:mm");
        public static int ALIGN = Color.ALIGN_RIGHT;
        public static int MAX_WIDTH = 7;
        public static float LETTER_WIDTH = 12;
        public static boolean ENCLOSE_NICK = false;
        public static boolean DIM_DOWN_NON_HUMAN_LINES = true;

        // core message data
        final public long pointer;
        final public Date date;
        final public String prefix;
        final public String message;

        // additional data
        final public boolean visible;
        final public int type;
        final public boolean highlighted;
        private @Nullable String speakingNick;
        private boolean privmsg;
        private boolean action;

        // sole purpose of this is to prevent onClick event on inner URLSpans to be fired
        // when user long-presses on the screen and a context menu is shown
        public boolean clickDisabled = false;

        // processed data
        // might not be present
        volatile public @Nullable Spannable spannable = null;

        public Line(long pointer, Date date, String prefix, @Nullable String message,
                          boolean displayed, boolean highlighted, @Nullable String[] tags) {
            this.pointer = pointer;
            this.date = date;
            this.prefix = prefix;
            this.message = (message == null) ? "" : message;
            this.visible = displayed;
            this.highlighted = highlighted;

            if (tags != null) {
                boolean log1 = false;
                boolean notifyNone = false;

                for (String tag : tags) {
                    if (tag.equals("log1"))
                        log1 = true;
                    else if (tag.equals("notify_none"))
                        notifyNone = true;
                    else if (tag.startsWith("nick_"))
                        this.speakingNick = tag.substring(5);
                    else if (tag.endsWith("_privmsg"))
                        this.privmsg = true;
                    else if (tag.endsWith("_action"))
                        this.action = true;
                }

                if (tags.length == 0 || !log1) {
                    this.type = LINE_OTHER;
                } else {
                    // Every "message" to user should have one or more of these tags
                    // notifyNone, notify_highlight or notify_message
                    this.type = notifyNone ? LINE_OWN : LINE_MESSAGE;
                }
            } else {
                // there are no tags, it's probably an old version of weechat, so we err
                // on the safe side and treat it as from human
                this.type = LINE_MESSAGE;
            }
        }

        final public static int LINE_OTHER = 0;
        final public static int LINE_OWN = 1;
        final public static int LINE_MESSAGE = 2;

        private @Nullable String findSpeakingNick() {
            if (type != LINE_MESSAGE) return null;
            return speakingNick;
        }

        //////////////////////////////////////////////////////////////////////////////////////////// processing stuff

        public void eraseProcessedMessage() {
            if (DEBUG_LINE) logger.warn("eraseProcessedMessage()");
            spannable = null;
        }

        public void processMessageIfNeeded() {
            if (DEBUG_LINE) logger.warn("processMessageIfNeeded()");
            if (spannable == null) processMessage();
        }

        /** process the message and create a spannable object according to settings
         ** TODO: reuse span objects (how? would that do any good?)
         ** the problem is that one has to use distinct spans on the same string
         ** TODO: allow variable width font (should be simple enough */
        public void processMessage() {
            if (DEBUG_LINE) logger.warn("processMessage()");
            String timestamp = (DATEFORMAT == null) ? null : DATEFORMAT.format(date);
            boolean encloseNick = ENCLOSE_NICK && privmsg && !action;
            Color.parse(timestamp, prefix, message, encloseNick, highlighted, MAX_WIDTH, ALIGN);
            Spannable spannable = new SpannableString(Color.cleanMessage);

            if (this.type == LINE_OTHER && DIM_DOWN_NON_HUMAN_LINES) {
                spannable.setSpan(new ForegroundColorSpan(ColorScheme.currentScheme().getOptionColor("chat_inactive_buffer")[ColorScheme.OPT_FG] | 0xFF000000), 0, spannable.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                CharacterStyle droidSpan;
                for (Color.Span span : Color.finalSpanList) {
                    switch (span.type) {
                        case Color.Span.FGCOLOR:   droidSpan = new ForegroundColorSpan(span.color | 0xFF000000); break;
                        case Color.Span.BGCOLOR:   droidSpan = new BackgroundColorSpan(span.color | 0xFF000000); break;
                        case Color.Span.ITALIC:    droidSpan = new StyleSpan(Typeface.ITALIC);                   break;
                        case Color.Span.BOLD:      droidSpan = new StyleSpan(Typeface.BOLD);                     break;
                        case Color.Span.UNDERLINE: droidSpan = new UnderlineSpan();                              break;
                        default: continue;
                    }
                    spannable.setSpan(droidSpan, span.start, span.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }

            if (ALIGN != Color.ALIGN_NONE) {
                LeadingMarginSpan margin_span = new LeadingMarginSpan.Standard(0, (int) (LETTER_WIDTH * Color.margin));
                spannable.setSpan(margin_span, 0, spannable.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            }

            // what a nice little custom linkifier we've got us here
            Linkify.linkify(spannable);

            this.spannable = spannable;
        }

        /** is to be run rarely—only when we need to display a notification */
        public String getNotificationString() {
            return String.format((!privmsg || action) ? "%s %s" : "<%s> %s",
                    Color.stripEverything(prefix),
                    Color.stripEverything(message));
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// Nick
    ////////////////////////////////////////////////////////////////////////////////////////////////    wow such abstracshun
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class Nick {
        public final long pointer;
        public String prefix;
        public String name;

        public Nick(long pointer, String prefix, String name) {
            this.prefix = prefix;
            this.name = name;
            this.pointer = pointer;
        }
    }
}

