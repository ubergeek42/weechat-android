/*******************************************************************************
 * Copyright 2012 Keith Johnson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.ubergeek42.weechat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Color class takes care of parsing WeeChat's own color codes in strings to diplay attributes
 * (bold,underline) and colors on screen. WeeChat's color codes get mapped to HTML color codes
 * wrapped in a <code>font</code>-tag.
 *
 * This class can also help with stripping attributes and colors from the String.
 *
 * See WeeChat dev document for more information: <a
 * href="http://www.weechat.org/files/doc/devel/weechat_dev.en.html#color_codes_in_strings">here</a>
 */
public class Color {
    final private static boolean DEBUG = true;
    final private static Logger logger = LoggerFactory.getLogger("Color");

    // Default weechat colors...00-16
    private static final int weechatColors[] = new int[] {
            0xD3D3D3,	// Grey
            0x000000,	// Black
            0x545454,	// Dark Gray
            0xDC143C,	// Dark Red
            0xFF0000,	// Light Red
            0x006400,	// Dark Green
            0x90EE90,	// Light Green
            0xA52A2A,	// Brown
            0xFFFF00,	// Yellow
            0x00008B,	// Dark Blue
            0xADD8E6,	// Light Blue
            0x8B008B,	// Dark Magenta
            0xFF00FF,	// Light Magenta
            0x008B8B,	// Dark Cyan
            0x00FFFF,	// Cyan
            0xD3D3D3,	// Gray
            0xFFFFFF	// White
    };

    // these are weechat options
    private static final int[][] weechatOptions = new int[][] {
            {0xFFFFFF,       -1}, // #  0 default
            {0xFFFFFF,       -1}, // #  1 chat
            {0x999999,       -1}, // #  2 chat_time
            {0xFFFFFF,       -1}, // #  3 chat_time_delimiters
            {0xFF6633,       -1}, // #  4 chat_prefix_error
            {0x990099,       -1}, // #  5 chat_prefix_network
            {0x999999,       -1}, // #  6 chat_prefix_action
            {0x00CC00,       -1}, // #  7 chat_prefix_join
            {0xCC0000,       -1}, // #  8 chat_prefix_quit
            {0xCC00FF,       -1}, // #  9 chat_prefix_more
            {0x330099,       -1}, // # 10 chat_prefix_suffix
            {0xFFFFFF,       -1}, // # 11 chat_buffer
            {0xFFFFFF,       -1}, // # 12 chat_server
            {0xFFFFFF,       -1}, // # 13 chat_channel
            {0xFFFFFF,       -1}, // # 14 chat_nick
            {0xFFFFFF,       -1}, // # 15 chat_nick_self
            {0xFFFFFF,       -1}, // # 16 chat_nick_other
            {      -1,       -1}, // # 17 (nick1 -- obsolete)
            {      -1,       -1}, // # 18 (nick2 -- obsolete)
            {      -1,       -1}, // # 19 (nick3 -- obsolete)
            {      -1,       -1}, // # 20 (nick4 -- obsolete)
            {      -1,       -1}, // # 21 (nick5 -- obsolete)
            {      -1,       -1}, // # 22 (nick6 -- obsolete)
            {      -1,       -1}, // # 23 (nick7 -- obsolete)
            {      -1,       -1}, // # 24 (nick8 -- obsolete)
            {      -1,       -1}, // # 25 (nick9 -- obsolete)
            {      -1,       -1}, // # 26 (nick10 -- obsolete)
            {0x666666,       -1}, // # 27 chat_host
            {0x9999FF,       -1}, // # 28 chat_delimiters
            {0xFFFFFF, 0xFF1155}, // # 29 chat_highlight
            {      -1,       -1}, // # 30 chat_read_marker
            {      -1,       -1}, // # 31 chat_text_found
            {      -1,       -1}, // # 32 chat_value
            {      -1,       -1}, // # 33 chat_prefix_buffer
            {      -1,       -1}, // # 34 chat_tags
            {      -1,       -1}, // # 35 chat_inactive_window
            {      -1,       -1}, // # 36 chat_inactive_buffer
            {      -1,       -1}, // # 37 chat_prefix_buffer_inactive_buffer
    };

    private static int extendedColors[] = new int[256];
    static {
        // 16 basic terminal colors(from:
        // http://docs.oracle.com/cd/E19728-01/820-2550/term_em_colormaps.html)
        extendedColors[0] =  0x000000; // Black
        extendedColors[1] =  0xFF0000; // Light Red
        extendedColors[2] =  0x00FF00; // Light Green
        extendedColors[3] =  0xFFFF00; // Yellow
        extendedColors[4] =  0x0000FF; // Light blue
        extendedColors[5] =  0xFF00FF; // Light magenta
        extendedColors[6] =  0x00FFFF; // Light cyan
        extendedColors[7] =  0xFFFFFF; // High White
        extendedColors[8] =  0x808080; // Gray
        extendedColors[9] =  0x800000; // Red
        extendedColors[10] = 0x008000; // Green
        extendedColors[11] = 0x808000; // Brown
        extendedColors[12] = 0x000080; // Blue
        extendedColors[13] = 0x800080; // Magenta
        extendedColors[14] = 0x008080; // Cyan
        extendedColors[15] = 0xC0C0C0; // White

        // Extended terminal colors, from colortest.vim:
        // http://www.vim.org/scripts/script.php?script_id=1349
        int base[] = new int[] { 0, 95, 135, 175, 215, 255 };
        for (int i = 16; i < 232; i++) {
            int j = i - 16;
            extendedColors[i] = (base[(j / 36) % 6]) << 16 | (base[(j / 6) % 6] << 8 | (base[j % 6]));
        }
        for (int i = 232; i < 256; i++) {
            int j = 8 + i * 10;
            extendedColors[i] = j << 16 | j << 8 | j;
        }
    }

    public static String stripColors(String text) { return text; }
    public static String stripAllColorsAndAttributes(String text) { return text; }

    public static String stripEverything(String text) {
        parseColors(text);
        return out.toString();
    }

    public static String clean_message;
    public static int margin;
    public static ArrayList<Span> final_span_list;

    // prepares: clean_message, margin, final_span_list
    public static void parse(String timestamp, String prefix, String message, final boolean highlight, final int max, final boolean align_right) {
        int puff;
        int color;
        StringBuilder sb = new StringBuilder();
        final_span_list = new ArrayList<Span>();

        // timestamp uses no colors
        if (timestamp != null) {
            sb.append(timestamp);
            sb.append(" ");
        }

        // prefix should be adjusted accoring to the settings
        // also, if highlight is enabled, remove all colors from here and add highlight color later
        parseColors(prefix);
        prefix = out.toString();
        if (highlight) span_list.clear();
        boolean nick_has_been_cut = false;
        if (prefix.length() > max) {
            nick_has_been_cut = true;
            prefix = prefix.substring(0, max);
            for (Span span: span_list) if (span.end > max) span.end = max;
        }
        else if (align_right && prefix.length() < max) {
            int diff = max - prefix.length();
            for (int x = 0; x < diff; x++) sb.append(" ");
        }
        if (highlight) {
            color = weechatOptions[29][0];
            if (color != -1) {Span fg = new Span(); fg.start = 0; fg.end = prefix.length(); fg.type = Span.FGCOLOR; fg.color = color; span_list.add(fg);}
            color = weechatOptions[29][1];
            if (color != -1) {Span bg = new Span(); bg.start = 0; bg.end = prefix.length(); bg.type = Span.BGCOLOR; bg.color = color; span_list.add(bg);}
        }
        puff = sb.length();
        for (Span span : span_list) {
            span.start += puff;
            span.end += puff;
            final_span_list.add(span);
        }
        sb.append(prefix);
        if (nick_has_been_cut) {
            sb.append("+");
            Span fg = new Span(); fg.start = sb.length() - 1; fg.end = sb.length(); fg.type = Span.FGCOLOR; fg.color = 0x444444; final_span_list.add(fg);
        }
        else sb.append(" ");

        // here's our margin
        margin = sb.length();

        // the rest of the message
        parseColors(message);
        message = out.toString();
        puff = sb.length();
        for (Span span : span_list) {
            span.start += puff;
            span.end += puff;
            final_span_list.add(span);
        }
        sb.append(message);
        clean_message = sb.toString();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // output of parseColors()
    private static StringBuffer out;                                   // printable characters
    private static ArrayList<Span> span_list = new ArrayList<Span>();  // list of spans in “out”

    // working vars of parseColor()
    private static String msg;                                         // text currently being parsed by parseColors
    private static int index;                                          // parsing position in this
    private static Span[] spans = new Span[6];                         // list of currently open spans

    // this Span can be easily translated into android's spans (except REVERSE)
    public static class Span {
        final static public int BOLD =      0x00;
        final static public int UNDERLINE = 0x01;
        final static public int REVERSE =   0x02;
        final static public int ITALIC =    0x03;
        final static public int FGCOLOR =   0x04;
        final static public int BGCOLOR =   0x05;
        public int start;
        public int end;
        public int type;
        public int color;
    }

    private static char getChar() {
        if (index >= msg.length()) return ' ';
        return msg.charAt(index++);
    }

    private static char peekChar() {
        if (index >= msg.length()) return ' ';
        return msg.charAt(index);
    }

    /** adds a new span to the temporary span list
     ** closing a similar span if it's been open and,
     ** if possible, extending a recently closed span */
    private static void addSpan(int type) {addSpan(type, -1);}
    private static  void addSpan(int type, int color) {
        finalizeSpan(type);
        int pos = out.length();
        // get the old span if the same span is ending at this same spot
        // if found, remove it from the list
        Span span = null;
        boolean found = false;
        for (Iterator<Span> it = span_list.iterator(); it.hasNext();) {
            span = it.next();
            if (span.end == pos && span.type == type && span.color == color) {
                it.remove();
                found = true;
                break;
            }
        }
        // no old span found, make new one
        if (!found) {
            span = new Span();
            span.type = type;
            span.start = pos;
            span.color = color;
        }
        // put it into temporary list
        spans[type] = span;
    }

    /** takes a span from the temporary span list and put it in the output list
     ** if span is size 0, simply discards it */
    private static void finalizeSpan(int type) {
        Span span = spans[type];
        if (span != null) {
            spans[type] = null;
            span.end = out.length();
            if (span.start != span.end)
                span_list.add(span);
        }
    }

    /////////////////////////////////
    ///////////////////////////////// colors
    /////////////////////////////////

    /** sets weechat's special colors.
     ** sets, if available, both foreground and background colors.
     **can take form of: 05 */
    private static void setWeechatColor() {
        int color;
        int color_index = getNumberOfLengthUpTo(2);
        if (color_index < 0 || color_index >= weechatOptions.length) return;
        color = weechatOptions[color_index][0];
        if (color != -1) addSpan(Span.FGCOLOR, color);
        color = weechatOptions[color_index][1];
        if (color != -1) addSpan(Span.BGCOLOR, color);
    }

    /** parse colors/color & attribute combinations. can take form of:
     ** 05, @00123, *05, @*_00123 */
    private static void setColor(int type) {
        int color;
        boolean extended = (peekChar() == '@');
        if (extended) getChar();
        if (type == Span.FGCOLOR) maybeSetAttributes();
        color = (extended) ? getColorExtended() : getColor();
        if (color != -1) addSpan(type, color);
    }

    // returns color in the form 0xfffff or -1
    private static int getColor() {
        int color_index = getNumberOfLengthUpTo(2);
        if (color_index < 0 || color_index >= weechatColors.length) return -1;
        return weechatColors[color_index];
    }

    // returns color in the form 0xfffff or -1
    private static int getColorExtended() {
        int color_index = getNumberOfLengthUpTo(5);
        if (color_index < 0 || color_index >= extendedColors.length) return -1;
        return extendedColors[color_index];
    }

    // returns a number stored in the next “amount” characters
    // if any of the “amount” characters is not a number, returns -1
    private static final int[] multipliers = new int[]{1, 10, 100, 1000, 10000};
    private static int getNumberOfLengthUpTo(int amount) {
        int c;
        int ret = 0;
        for (amount--; amount >= 0; amount--) {     // 2: 1, 0;  5: 4, 3, 2, 1, 0
            c = peekChar();
            if (c < '0' || c > '9') return -1;
            ret += (getChar() - '0') * multipliers[amount];
        }
        return ret;
    }

    /////////////////////////////////
    ///////////////////////////////// attributes
    /////////////////////////////////

    // set as many attributes as we can, maybe 0
    private static void maybeSetAttributes() {
        while (true) {
            int type = getAttribute(peekChar());
            if (type < -1) return;                   // next char is not an attribute
            if (type > -1) addSpan(type);            // next char is an attribute
            getChar();                               // consume if an attribute or “|”
        }
    }

    // set 1 attribute
    private static void maybeSetAttribute() {
        int type = getAttribute(peekChar());
        if (type < -1) return;
        if (type > -1) addSpan(type);
        getChar();
    }

    // remove 1 attribute
    private static void maybeRemoveAttribute() {
        int type = getAttribute(peekChar());
        if (type < -1) return;
        if (type > -1) finalizeSpan(type);
        getChar();
    }

    // returns >= 0 if we've got useful attribute
    // returns -1 if no useful attributes are found, but a character should be consumed
    // returns -2 if nothing useful is found
    // actually weechat breaks the protocol here...
    private static int getAttribute(char c) {
        switch(c) {
            case '*': case 0x01: return Span.BOLD;
            case '!': case 0x02: return Span.REVERSE;
            case '/': case 0x03: return Span.ITALIC;
            case '_': case 0x04: return Span.UNDERLINE;
            case '|': return -1;
            default:  return -2;
        }
    }

    /////////////////////////////////
    ///////////////////////////////// wow such code
    /////////////////////////////////

    /** takes text as input
     ** sets out and span_list */
    synchronized private static void parseColors(String msg) {
        Color.msg = msg;
        index = 0;
        out = new StringBuffer();
        span_list.clear();

        char c;
        while (index < msg.length()) {
            c = getChar();
            switch (c) {
                case 0x1C:              // clear all attributes
                    for (int i = 0; i <= 5; i++) finalizeSpan(i);
                    break;
                case 0x1A:              // set attr
                    maybeSetAttribute();
                    break;
                case 0x1B:              // remove attr
                    maybeRemoveAttribute();
                    break;
                case 0x19:              // oh god
                    c = peekChar();
                    switch (c) {
                        case 0x1C:              // clear colors
                            finalizeSpan(Span.FGCOLOR);
                            finalizeSpan(Span.BGCOLOR);
                            break;
                        case '@':               // /color stuff. shouldn't happen, but just in case consume
                            setColor(Span.FGCOLOR);
                            break;
                        case 'b':               // bars stuff. consume two chars
                            getChar();
                        case 'E':               // emphasise? consume one char
                            getChar();
                            break;
                        case 'F':               // foreground
                        case '*':               // foreground followed by ',' followed by background
                            getChar();
                            setColor(Span.FGCOLOR);
                            if (c == 'F' || peekChar() != ',') break;
                        case 'B':               // background (same as fg but w/o optional attributes)
                            getChar();
                            setColor(Span.BGCOLOR);
                            break;
                        default:
                            setWeechatColor();  // this is determined by options
                            break;
                    }
                    break;
                default:
                    out.append(c);      // wow, we've got a printable character!
            }
        }
        for (int i = 0; i <= 5; i++) finalizeSpan(i);
        //logger.error("processed message: [{}]", msg);
        //for (Span span : span_list) logger.warn("> span #{} ({}-"+span.end+"): color " + span.color, span.type, span.start);
    }
}