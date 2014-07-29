package com.ubergeek42.WeechatAndroid.service;

import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.StyleSpan;
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
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class Buffer {
    private static Logger logger = LoggerFactory.getLogger("Buffer");
    final private static boolean DEBUG = true;

    public final static int MAX_LINES = 200;
    public static BufferList buffer_list;

    private BufferEye buffer_eye;

    public final int pointer;
    public String full_name, short_name, title;
    public int number, notify_level;
    public Hashtable local_vars;

    public LinkedList<Line> lines = new LinkedList<Line>();
    private int visible_lines_count = 0;

    public boolean is_open = false;
    public boolean holds_all_lines_it_is_supposed_to_hold = false;
    public boolean is_a_human_buffer = false;
    public int unreads = 0;
    public int highlights = 0;

    Buffer(int pointer, int number, String full_name, String short_name, String title, int notify_level, Hashtable local_vars) {
        this.pointer = pointer;
        this.number = number;
        this.full_name = full_name;
        this.short_name = short_name;
        this.title = title;
        this.notify_level = notify_level;
        this.local_vars = local_vars;
        this.is_a_human_buffer = isAHumanBuffer();
        if (BufferList.open_buffers_pointers.contains((Integer) pointer)) is_open = true;
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

    /** better call off the main thread */
    synchronized public void setOpen(boolean open) {
        if (DEBUG) logger.warn("{} setOpen({})", short_name, open);
        if (this.is_open != open) {
            this.is_open = open;
            if (open) {
                BufferList.open_buffers_pointers.add((Integer) pointer);
                for (Line line : lines) line.processMessageIfNeeded();
            }
            else {
                BufferList.open_buffers_pointers.remove((Integer) pointer);
                for (Line line : lines) line.eraseProcessedMessage();
            }
        }
    }

    synchronized public void setBufferEye(BufferEye buffer_eye) {
        if (DEBUG) logger.warn("{} setBufferEye({})", short_name, buffer_eye);
        this.buffer_eye = buffer_eye;
    }

    /** to be called when options has changed and the messages should be processed */
    synchronized public void forceProcessAllMessages() {
        if (DEBUG) logger.warn("{} forceProcessAllMessages()", this.short_name);
        for (Line line : lines) line.processMessage();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    synchronized public void addLine(final Line line, final boolean is_last) {
        //logger.warn("{} addLine({}, {})", new Object[]{this, line, is_last});
        for (Line l: lines) if (l.pointer == line.pointer) return;

        if (lines.size() > MAX_LINES) {if (lines.getFirst().visible) visible_lines_count--; lines.removeFirst();}
        if (is_last) lines.add(line);
        else lines.addFirst(line);
        if (line.visible) visible_lines_count++;

        if (buffer_eye != null) line.processMessage();
        if (is_last && notify_level >= 0) {
            if (line.highlighted) highlights += 1;
            if (line.from_human) {
                unreads += 1;
                buffer_list.notifyBuffersSlightlyChanged();
            }
        }
        if (buffer_eye != null) buffer_eye.onLinesChanged();
    }

    synchronized public void onPropertiesChanged() {
        if (buffer_eye != null) buffer_eye.onPropertiesChanged();
    }

    synchronized  public void onBufferClosed() {
        if (buffer_eye != null) buffer_eye.onBufferClosed();
    }

    private boolean isAHumanBuffer() {
        RelayObject type = local_vars.get("type");
        return type != null && (type.asString().equals("private") || type.asString().equals("channel"));
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
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
        public Spannable spannable = null;

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
            spannable = null;
        }

        public void processMessageIfNeeded() {
            if (spannable == null) processMessage();
        }

        public void processMessage() {
            if (DEBUG) logger.warn("processMessage()");
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
        public void updateDrawState(TextPaint ds) {ds.setUnderlineText(true);}
    }
}

