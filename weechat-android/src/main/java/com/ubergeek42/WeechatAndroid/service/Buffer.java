package com.ubergeek42.WeechatAndroid.service;

import android.graphics.Typeface;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.SuperscriptSpan;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;
import android.text.util.Linkify;

import com.ubergeek42.WeechatAndroid.BuildConfig;
import com.ubergeek42.weechat.Color;
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
import java.util.List;

public class Buffer {
    private static Logger logger = LoggerFactory.getLogger("Buffer");
    final private static boolean DEBUG_BUFFER = BuildConfig.DEBUG;
    final private static boolean DEBUG_LINE = false;
    final private static boolean DEBUG_NICK = false;

    //prefs
    public static boolean FILTER_LINES = false;

    final public static int PRIVATE = 2;
    final public static int CHANNEL = 1;
    final public static int OTHER = 0;

    public final static int MAX_LINES = 200;
    public static BufferList buffer_list;

    private BufferEye buffer_eye;
    private BufferNicklistEye buffer_nicklist_eye;

    public final int pointer;
    public String full_name, short_name, title;
    public int number, notify_level;
    public Hashtable local_vars;

    private LinkedList<Line> lines = new LinkedList<Line>();
    private int visible_lines_count = 0;

    private LinkedList<Nick> nicks = new LinkedList<Nick>();

    public boolean is_open = false;
    public boolean is_watched = false;
    public boolean holds_all_lines_it_is_supposed_to_hold = false;
    public boolean holds_all_nicks = false;
    public int type = OTHER;
    public int unreads = 0;
    public int highlights = 0;

    public Spannable printable1 = null;
    public Spannable printable2 = null;

    Buffer(int pointer, int number, String full_name, String short_name, String title, int notify_level, Hashtable local_vars) {
        this.pointer = pointer;
        this.number = number;
        this.full_name = full_name;
        this.short_name = (short_name != null) ? short_name : full_name;
        this.title = title;
        this.notify_level = notify_level;
        this.local_vars = local_vars;
        this.type = getBufferType();
        processBufferTitle();
        if (buffer_list.isSynced(full_name)) setOpen(true);
        if (DEBUG_BUFFER) logger.warn("new Buffer(..., {}, {}, ...) is_open? {}", new Object[]{number, full_name, is_open});
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
            Line[] l = new Line[visible_lines_count];
            int i = 0;
            for (Line line: lines) {
                if (line.visible) l[i++] = line;
            }
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
        if (DEBUG_BUFFER) logger.warn("{} setOpen({})", short_name, open);
        if (this.is_open == open) return;
        this.is_open = open;
        if (open) {
            buffer_list.syncBuffer(full_name);
            for (Line line : lines) line.processMessageIfNeeded();
        }
        else {
            buffer_list.desyncBuffer(full_name);
            for (Line line : lines) line.eraseProcessedMessage();
        }
        buffer_list.notifyBuffersSlightlyChanged();
    }

    /** set buffer eye, i.e. something that watches buffer events
     ** also requests all lines and nicknames, if needed (usually only done once per buffer)
     ** we are requesting it here and not in setOpen() because:
     **     when the process gets killed and restored, we want to receive messages, including
     **     notifications, for that buffer. BUT the user might not visit that buffer at all.
     **     so we request lines and nicks upon user actually (getting close to) opening the buffer.
     ** we are requesting nicks along with the lines because:
     **     nick completion */
    synchronized public void setBufferEye(@Nullable BufferEye buffer_eye) {
        if (DEBUG_BUFFER) logger.warn("{} setBufferEye({})", short_name, buffer_eye);
        this.buffer_eye = buffer_eye;
        if (buffer_eye != null) {
            if (!holds_all_lines_it_is_supposed_to_hold && lines.size() < MAX_LINES)
                buffer_list.requestLinesForBufferByPointer(pointer);
            if (!holds_all_nicks)
                buffer_list.requestNicklistForBufferByPointer(pointer);
        }
    }

    /** tells Buffer if it is ACTIVELY display on screen
     ** affects the way buffer advertises highlights/unreads count and notifications
     ** can be called multiple times without harm */
    synchronized public void setWatched(boolean watched) {
        if (DEBUG_BUFFER) logger.warn("{} setWatched({})", short_name, watched);
        if (is_watched == watched) return;
        is_watched = watched;
        if (watched) resetUnreadsAndHighlights();
    }

    /** called when options has changed and the messages should be processed */
    synchronized public void forceProcessAllMessages() {
        if (DEBUG_BUFFER) logger.error("{} forceProcessAllMessages()", short_name);
        for (Line line : lines) line.processMessage();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// stuff called by message handlers
    ////////////////////////////////////////////////////////////////////////////////////////////////

    synchronized public void addLine(final Line line, final boolean is_last) {
        if (DEBUG_LINE) logger.warn("{} addLine('{}', {})", new Object[]{short_name, line.message, is_last});

        // check if the line in question is already in the buffer
        // happens when reverse request throws in lines even though some are already here
        for (Line l: lines) if (l.pointer == line.pointer) return;

        // remove a line if we are over the limit and add the new line
        // correct visible_lines_count accordingly
        if (lines.size() > MAX_LINES) {if (lines.getFirst().visible) visible_lines_count--; lines.removeFirst();}
        if (is_last) lines.add(line);
        else lines.addFirst(line);
        if (line.visible) visible_lines_count++;

        // calculate spannable, if needed
        if (is_open) line.processMessage();

        // for messages that ARRIVE AS WE USE THE APPLICATION:
        // set unreads / highlights and notify BufferList
        // if the number of messages has increased, something will be wise enough to use
        //      provided by setMostRecentHotLine()
        // we are not using OLDER messages arriving from reverse request as well because
        //      unreads and highlights is filled by hotlist request
        if (!is_watched && is_last && notify_level >= 0) {
            if (line.highlighted) {
                highlights += 1;
                BufferList.setMostRecentHotLine(this, line);
                buffer_list.notifyBuffersSlightlyChanged(type == OTHER);
            }
            else if (line.from_human_and_visible) {
                unreads += 1;
                if (type == PRIVATE) BufferList.setMostRecentHotLine(this, line);
                buffer_list.notifyBuffersSlightlyChanged(type == OTHER);
            }
        }

        // notify our listener
        onLinesChanged();

        // if current line's an event line and we've got a speaker, move nick to fist position
        // nick in question is supposed to be in the nicks already, for we only shuffle these
        // nicks when someone spoke, i.e. NOT when user joins.
        if (holds_all_nicks && is_last) {
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
    }

    synchronized public void onLinesChanged() {
        if (buffer_eye != null) buffer_eye.onLinesChanged();
    }

    synchronized public void onLinesListed() {
        holds_all_lines_it_is_supposed_to_hold = true;
        if (buffer_eye != null) buffer_eye.onLinesListed();
    }

    synchronized public void onPropertiesChanged() {
        type = getBufferType();
        processBufferTitle();
        if (buffer_eye != null) buffer_eye.onPropertiesChanged();
    }

    synchronized public void onBufferClosed() {
        if (DEBUG_BUFFER) logger.warn("{} onBufferClosed()", short_name);
        if (buffer_eye != null) buffer_eye.onBufferClosed();
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// private stuffs

    /** determine if the buffer is PRIVATE, CHANNEL, or OTHER */
    private int getBufferType() {
        RelayObject type = local_vars.get("type");
        if (type == null) return OTHER;
        if (type.asString().equals("private")) return PRIVATE;
        if (type.asString().equals("channel")) return CHANNEL;
        return OTHER;
    }

    private final static SuperscriptSpan SUPER = new SuperscriptSpan();
    private final static RelativeSizeSpan SMALL1 = new RelativeSizeSpan(0.6f);
    private final static RelativeSizeSpan SMALL2 = new RelativeSizeSpan(0.6f);
    private final static int EX = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;

    private void processBufferTitle() {
        Spannable spannable;
        final String number = Integer.toString(this.number) + " ";
        spannable = new SpannableString(number + short_name);
        spannable.setSpan(SUPER, 0, number.length(), EX);
        spannable.setSpan(SMALL1, 0, number.length(), EX);
        printable1 = spannable;
        if (title == null || title.equals("")) {
            printable2 = printable1;
        } else {
            spannable = new SpannableString(number + short_name + "\n" + Color.stripEverything(title));
            spannable.setSpan(SUPER, 0, number.length(), EX);
            spannable.setSpan(SMALL1, 0, number.length(), EX);
            spannable.setSpan(SMALL2, number.length() + short_name.length() + 1, spannable.length(), EX);
            printable2 = spannable;
        }
    }

    public int old_unreads = 0;
    public int old_highlights = 0;

    /** sets highlights/unreads to 0 and,
     ** if something has actually changed, notifies whoever cares about it */
    synchronized public void resetUnreadsAndHighlights() {
        if (DEBUG_BUFFER) logger.error("{} resetUnreadsAndHighlights()", short_name);
        if ((unreads | highlights) == 0) return;
        old_unreads = unreads;
        old_highlights = highlights;
        unreads = highlights = 0;
        buffer_list.notifyBuffersSlightlyChanged(type == OTHER);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// NICKS
    ////////////////////////////////////////////////////////////////////////////////////////////////    stuff called by the UI
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /** sets and removes a single nicklist watcher
     ** used to notify of nicklist changes as new nicks arrive and others quit */
    synchronized public void setBufferNicklistEye(@Nullable BufferNicklistEye buffer_nicklist_eye) {
        if (DEBUG_NICK) logger.warn("{} setBufferNicklistEye({})", short_name, buffer_nicklist_eye);
        this.buffer_nicklist_eye = buffer_nicklist_eye;
    }

    synchronized public @NonNull Nick[] getNicksCopy() {
        sortNicks();
        return nicks.toArray(new Nick[nicks.size()]);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// called by event handlers
    ////////////////////////////////////////////////////////////////////////////////////////////////

    synchronized public void addNick(int pointer, String prefix, String name) {
        if (DEBUG_NICK) logger.debug("{} addNick({}, {}, {})", new Object[]{short_name, pointer, prefix, name});
        nicks.add(new Nick(pointer, prefix, name));
        notifyNicklistChanged();
    }

    synchronized public void removeNick(int pointer) {
        if (DEBUG_NICK) logger.debug("{} removeNick({})", new Object[]{short_name, pointer});
        for (Iterator<Nick> it = nicks.iterator(); it.hasNext();) {
            if (it.next().pointer == pointer) {
                it.remove();
                break;
            }
        }
        notifyNicklistChanged();
    }

    synchronized public void updateNick(int pointer, String prefix, String name) {
        if (DEBUG_NICK) logger.debug("{} updateNick({}, {}, {})", new Object[]{short_name, pointer, prefix, name});
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
        if (DEBUG_NICK) logger.debug("{} sortNicksByLines({})", short_name);
        final HashMap<String, Integer> name_to_position = new HashMap<String, Integer>();

        for (int i = lines.size() - 1; i >= 0; i--) {
            String name = lines.get(i).findSpeakingNick();
            if (name != null && !name_to_position.containsKey(name))
                name_to_position.put(name, name_to_position.size());
        }

        Collections.sort(nicks, new Comparator<Nick>() {
            @Override public int compare(Nick left, Nick right) {
                Integer l = name_to_position.get(left.name);
                Integer r = name_to_position.get(right.name);
                if (l == null) l = Integer.MAX_VALUE;
                if (r == null) r = Integer.MAX_VALUE;
                return l - r;
            }
        });
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// private stuffs

    synchronized private void notifyNicklistChanged() {
        if (buffer_nicklist_eye != null) buffer_nicklist_eye.onNicklistChanged();
    }

    private void sortNicks() {
        Collections.sort(nicks, sortByNumberPrefixAndNameComparator);
    }

    /** this comparator sorts by prefix first
     ** sorting by prefix is done in a dumb way, this will order prexis like this:
     **      ' ', '%', '&', '+', '@'
     ** well. it's almost good... */
    private final static Comparator<Nick> sortByNumberPrefixAndNameComparator = new Comparator<Nick>() {
        @Override public int compare(Nick n1, Nick n2) {
            int diff = n2.prefix.compareTo(n1.prefix);
            return (diff != 0) ? diff : n1.name.compareTo(n2.name);
        }
    };

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// Buffer.Line CLASS
    ////////////////////////////////////////////////////////////////////////////////////////////////    really should've put that into a separate file, but—
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class Line {
        // constants
        public final static int ALIGN_NONE = 0;
        public final static int ALIGN_LEFT = 1;
        public final static int ALIGN_RIGHT = 2;

        // preferences for all lines
        public static float TEXT_SIZE = 10;
        public static @Nullable DateFormat DATEFORMAT = new SimpleDateFormat("HH:mm");
        public static int ALIGN = ALIGN_RIGHT;
        public static int MAX_WIDTH = 7;
        public static float LETTER_WIDTH = 12;

        // core message data
        final public int pointer;
        final public Date date;
        final public String prefix;
        final public String message;

        // additional data
        final public boolean visible;
        final public boolean from_human_and_visible;
        final public boolean highlighted;
        final private @Nullable String[] tags;

        // processed data
        // might not be present
        volatile public @Nullable Spannable spannable = null;

        public Line(int pointer, Date date, String prefix, @Nullable String message,
                          boolean displayed, boolean highlighted, @Nullable String[] tags) {
            this.pointer = pointer;
            this.date = date;
            this.prefix = prefix;
            this.message = (message == null) ? "" : message;
            this.visible = displayed;
            this.highlighted = highlighted;
            this.tags = tags;
            from_human_and_visible = findOutIfFromHuman();
        }

        private boolean findOutIfFromHuman() {
            if (highlighted) return true;
            if (!visible) return false;

            // there's no tags, probably it's an old version of weechat, so we err
            // on the safe side and treat it as from human
            if (tags == null) return true;

            // Every "message" to user should have one or more of these tags
            // notify_message, notify_highlight or notify_message
            if (tags.length == 0) return false;
            final List list = Arrays.asList(tags);
            return list.contains("notify_message") || list.contains("notify_highlight")
                    || list.contains("notify_private");
        }

        private @Nullable String findSpeakingNick() {
            if (!from_human_and_visible || tags == null) return null;
            for (String tag : tags)
                if (tag.startsWith("nick_"))
                    return tag.substring(5);
            return null;
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
            Color.parse(timestamp, prefix, message, highlighted, MAX_WIDTH, ALIGN == ALIGN_RIGHT);
            Spannable spannable = new SpannableString(Color.clean_message);

            Object droid_span;
            for (Color.Span span : Color.final_span_list) {
                switch (span.type) {
                    case Color.Span.FGCOLOR:   droid_span = new ForegroundColorSpan(span.color | 0xFF000000); break;
                    case Color.Span.BGCOLOR:   droid_span = new BackgroundColorSpan(span.color | 0xFF000000); break;
                    case Color.Span.ITALIC:    droid_span = new StyleSpan(Typeface.ITALIC);                   break;
                    case Color.Span.BOLD:      droid_span = new StyleSpan(Typeface.BOLD);                     break;
                    case Color.Span.UNDERLINE: droid_span = new UnderlineSpan();                              break;
                    default: continue;
                }
                spannable.setSpan(droid_span, span.start, span.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            if (ALIGN != ALIGN_NONE) {
                LeadingMarginSpan margin_span = new LeadingMarginSpan.Standard(0, (int) (LETTER_WIDTH * Color.margin));
                spannable.setSpan(margin_span, 0, spannable.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            }

            // now this is extremely stupid.
            Linkify.addLinks(spannable, Linkify.WEB_URLS);
            for (URLSpan urlspan : spannable.getSpans(0, spannable.length(), URLSpan.class)) {
                spannable.setSpan(new URLSpan2(urlspan.getURL()), spannable.getSpanStart(urlspan), spannable.getSpanEnd(urlspan), 0);
                spannable.removeSpan(urlspan);
            }

            this.spannable = spannable;
        }

        /** is to be run rarely—only when we need to display a notification */
        public String getNotificationString() {
            boolean action = false;
            if (tags != null)
                for (String tag: tags)
                    if (tag.endsWith("action")) {
                        action = true;
                        break;
                    }
            return String.format(action ? "%s %s" : "<%s> %s",
                    Color.stripEverything(prefix),
                    Color.stripEverything(message));
        }

        //////////////////////////////////////////////////////////////////////////////////////////// private stuffs

        /** just an url span that doesn't change the color of the link */
        private static class URLSpan2 extends URLSpan {
            public URLSpan2(String url) {
                super(url);
            }

            @Override public void updateDrawState(@NonNull TextPaint ds) {
                ds.setUnderlineText(true);
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// Nick
    ////////////////////////////////////////////////////////////////////////////////////////////////    wow such abstracshun
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class Nick {
        public final int pointer;
        public String prefix;
        public String name;

        public Nick(int pointer, String prefix, String name) {
            this.prefix = prefix;
            this.name = name;
            this.pointer = pointer;
        }
    }
}

