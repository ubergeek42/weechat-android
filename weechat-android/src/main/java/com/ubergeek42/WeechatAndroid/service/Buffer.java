package com.ubergeek42.WeechatAndroid.service;

import android.graphics.Typeface;
import android.support.annotation.NonNull;
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

import com.ubergeek42.weechat.Color;
import com.ubergeek42.weechat.relay.protocol.Hashtable;
import com.ubergeek42.weechat.relay.protocol.RelayObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class Buffer {
    private static Logger logger = LoggerFactory.getLogger("Buffer");
    final private static boolean DEBUG = true;

    final public static int PRIVATE = 2;
    final public static int CHANNEL = 1;
    final public static int OTHER = 0;

    public final static int MAX_LINES = 200;
    public static BufferList buffer_list;

    private BufferEye buffer_eye;

    public final int pointer;
    public String full_name, short_name, title;
    public int number, notify_level;
    public Hashtable local_vars;

    private LinkedList<Line> lines = new LinkedList<Line>();
    private int visible_lines_count = 0;

    private List<Nick> nicks = new ArrayList<Nick>();
    private LinkedList<String> last_used_nicks = new LinkedList<String>();

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
        if (DEBUG) logger.warn("new Buffer(..., {}, {}, ...) is_open? {}", new Object[]{number, full_name, is_open});
    }

    /** better call off the main thread */
    synchronized public Line[] getLinesCopy() {return lines.toArray(new Line[lines.size()]);}

    /** better call off the main thread */
    synchronized public Line[] getLinesFilteredCopy() {
        Line[] l = new Line[visible_lines_count];
        int i = 0;
        for (Line line: lines) {
            if (line.visible) l[i++] = line;
        }
        return l;
    }

    synchronized  public String[] getLastUsedNicksCopy() {
        return last_used_nicks.toArray(new String[last_used_nicks.size()]);
    }

    /** better call off the main thread */
    synchronized public void setOpen(boolean open) {
        if (DEBUG) logger.warn("{} setOpen({})", short_name, open);
        if (this.is_open == open) return;
        this.is_open = open;
        if (open) {
            buffer_list.syncBuffer(full_name);
            if (DEBUG) logger.error("...processMessageIfNeeded(): {}", (lines.size() > 0 && lines.getLast().spannable == null) ? "MOST LIKELY NEEDED: " + lines.size() : "nah");
            for (Line line : lines) line.processMessageIfNeeded();
        }
        else {
            buffer_list.desyncBuffer(full_name);
            if (DEBUG) logger.error("...eraseProcessedMessage()");
            for (Line line : lines) line.eraseProcessedMessage();
        }
        buffer_list.notifyBuffersSlightlyChanged();
    }

    synchronized public void setBufferEye(BufferEye buffer_eye) {
        if (DEBUG) logger.warn("{} setBufferEye({})", short_name, buffer_eye);
        this.buffer_eye = buffer_eye;
        if (!holds_all_lines_it_is_supposed_to_hold && lines.size() < MAX_LINES)
            buffer_list.requestLinesForBufferByPointer(pointer);
        if (!holds_all_nicks)
            buffer_list.requestNicklistForBufferByPointer(pointer);
    }

    synchronized public void setWatched(boolean watched) {
        if (is_watched == watched) return;
        is_watched = watched;
        if (watched) resetUnreadsAndHighlights();
    }

    /** to be called when options has changed and the messages should be processed */
    synchronized public void forceProcessAllMessages() {
        if (DEBUG) logger.error("{} forceProcessAllMessages()", short_name);
        for (Line line : lines) line.processMessage();
    }

    synchronized public void resetUnreadsAndHighlights() {
        if ((unreads | highlights) == 0) return;
        unreads = highlights = 0;
        buffer_list.notifyBuffersSlightlyChanged();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    synchronized public void addLine(final Line line, final boolean is_last) {
        if (DEBUG) logger.warn("{} addLine('{}', {})", new Object[]{short_name, line.message, is_last});
        for (Line l: lines) if (l.pointer == line.pointer) return;

        if (lines.size() > MAX_LINES) {if (lines.getFirst().visible) visible_lines_count--; lines.removeFirst();}
        if (is_last) lines.add(line);
        else lines.addFirst(line);
        if (line.visible) visible_lines_count++;

        if (is_open) line.processMessage();
        if (!is_watched && is_last && notify_level >= 0) {
            if (line.highlighted) highlights += 1;
            else if (line.from_human) unreads += 1;
            if (line.highlighted || line.from_human) buffer_list.notifyBuffersSlightlyChanged();
        }
        if (buffer_eye != null) buffer_eye.onLinesChanged();

        // this is a line that comes with a nickname (at all times?)
        // change snicks accordingly, placing last used nickname first
        if (line.from_human)
            for (String tag : line.tags)
                if (tag.startsWith("nick_")) {
                    String nick = tag.substring(5);
                    if (is_last) {
                        last_used_nicks.remove(nick);
                        last_used_nicks.addFirst(nick);
                    }
                    else {
                        // this should run before addNick stuff
                        if (!last_used_nicks.contains(nick))
                            last_used_nicks.addLast(nick);
                    }
                    break;
                }
    }

    synchronized public void onPropertiesChanged() {
        type = getBufferType();
        processBufferTitle();
        if (buffer_eye != null) buffer_eye.onPropertiesChanged();
    }

    synchronized public void onBufferClosed() {
        if (DEBUG) logger.warn("{} onBufferClosed() (buffer_eye = {})", short_name, buffer_eye);
        if (buffer_eye != null) buffer_eye.onBufferClosed();
    }

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

    public void processBufferTitle() {
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

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    synchronized public void addNick(int pointer, String prefix, String name) {
        if (DEBUG) logger.debug("{} addNick({}, {}, {})", new Object[]{short_name, pointer, prefix, name});
        nicks.add(new Nick(pointer, prefix, name));
        sortNicks();
        if (!last_used_nicks.contains(name)) last_used_nicks.addLast(name);
    }

    synchronized public void removeNick(int pointer) {
        if (DEBUG) logger.debug("{} removeNick({})", new Object[]{short_name, pointer});
        for (Iterator<Nick> it = nicks.iterator(); it.hasNext();) {
            Nick nick = it.next();
            if (nick.pointer == pointer) {
                it.remove();
                last_used_nicks.remove(nick.name);
                return;
            }
        }
    }

    synchronized public void updateNick(int pointer, String prefix, String name) {
        if (DEBUG) logger.debug("{} updateNick({}, {}, {})", new Object[]{short_name, pointer, prefix, name});
        for (Nick nick: nicks) {
            if (nick.pointer == pointer) {
                int idx = last_used_nicks.indexOf(nick.name);
                if (idx != -1) last_used_nicks.set(idx, name);
                nick.prefix = prefix;
                nick.name = name;
                break;
            }
        }
        sortNicks();
    }

    private final Comparator<Nick> sortByNumberPrefixAndNameComparator = new Comparator<Nick>() {
        @Override
        public int compare(Nick n1, Nick n2) {
            int diff = n1.prefix.compareTo(n2.prefix);
            return (diff != 0) ? diff : n1.name.compareTo(n2.name);
        }
    };

    private void sortNicks() {
        Collections.sort(nicks, sortByNumberPrefixAndNameComparator);
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// Line
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class Line {
        // constants
        public final static int ALIGN_NONE = 0;
        public final static int ALIGN_LEFT = 1;
        public final static int ALIGN_RIGHT = 2;

        // preferences for all lines
        public static DateFormat DATEFORMAT = new SimpleDateFormat("HH:mm");
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
        final public boolean from_human;
        final public boolean highlighted;
        final private String[] tags;

        // processed data
        // might not be present
        volatile public Spannable spannable = null;

        public Line(int pointer, Date date, String prefix, String message,
                          boolean displayed, boolean highlighted, String[] tags) {
            this.pointer = pointer;
            this.date = date;
            this.prefix = prefix;
            this.message = (message == null) ? "" : message;
            this.visible = displayed;
            this.highlighted = highlighted;
            this.tags = tags;

            from_human = findOutIfFromHuman();

            //logger.warn("NEWLINE: [{}/{}] {} ", new Object[]{from_human, highlighted, message});
        }

        private boolean findOutIfFromHuman() {
            if (highlighted) return true;
            if (!visible) return false;

            // there's no tags, probably it's an old version of weechat, so we err
            // on the safe side and treat it as from_human
            if (tags == null) return true;

            // Every "message" to user should have one or more of these tags
            // notify_message, notify_highlight or notify_message
            if (tags.length == 0) return false;
            final List list = Arrays.asList(tags);
            return list.contains("notify_message") || list.contains("notify_highlight")
                    || list.contains("notify_private");
        }

        public void eraseProcessedMessage() {
            if (false && DEBUG) logger.warn("eraseProcessedMessage()");
            spannable = null;
        }

        public void processMessageIfNeeded() {
            if (false && DEBUG) logger.warn("processMessageIfNeeded()");
            if (spannable == null) processMessage();
        }

        public void processMessage() {
            if (false && DEBUG) logger.warn("processMessage()");
            String timestamp = (DATEFORMAT == null) ? null : DATEFORMAT.format(date);
            Color.parse(timestamp, prefix, message, highlighted, MAX_WIDTH, ALIGN == ALIGN_RIGHT);
            Spannable spannable = new SpannableString(Color.clean_message);

            Object javaspan;
            for (Color.Span span : Color.final_span_list) {
                switch (span.type) {
                    case Color.Span.FGCOLOR:   javaspan = new ForegroundColorSpan(span.color | 0xFF000000); break;
                    case Color.Span.BGCOLOR:   javaspan = new BackgroundColorSpan(span.color | 0xFF000000); break;
                    case Color.Span.ITALIC:    javaspan = new StyleSpan(Typeface.ITALIC);                   break;
                    case Color.Span.BOLD:      javaspan = new StyleSpan(Typeface.BOLD);                     break;
                    case Color.Span.UNDERLINE: javaspan = new UnderlineSpan();                              break;
                    default: continue;
                }
                spannable.setSpan(javaspan, span.start, span.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            if (ALIGN != ALIGN_NONE) {
                LeadingMarginSpan margin_span = new LeadingMarginSpan.Standard(0, (int) (LETTER_WIDTH * Color.margin));
                spannable.setSpan(margin_span, 0, spannable.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            }

            Linkify.addLinks(spannable, Linkify.WEB_URLS);
            for (URLSpan urlspan : spannable.getSpans(0, spannable.length(), URLSpan.class)) {
                spannable.setSpan(new URLSpan2(urlspan.getURL()), spannable.getSpanStart(urlspan), spannable.getSpanEnd(urlspan), 0);
                spannable.removeSpan(urlspan);
            }

            this.spannable = spannable;
        }
    }

    private static class URLSpan2 extends URLSpan {
        public URLSpan2(String url) {super(url);}

        @Override
        public void updateDrawState(@NonNull TextPaint ds) {ds.setUnderlineText(true);}
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// Nick
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class Nick {
        public int pointer;
        public String prefix;
        public String name;

        public Nick(int pointer, String prefix, String name) {
            this.prefix = prefix;
            this.name = name;
            this.pointer = pointer;
        }
    }
}

