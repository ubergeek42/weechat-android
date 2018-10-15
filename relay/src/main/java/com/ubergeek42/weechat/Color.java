// Copyright 2012 Keith Johnson
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

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
    final private static boolean DEBUG = false;
    final private static Logger logger = LoggerFactory.getLogger("Color");

    // constants
    public final static int ALIGN_NONE = 0;
    public final static int ALIGN_LEFT = 1;
    public final static int ALIGN_RIGHT = 2;
    public final static int ALIGN_TIMESTAMP = 3;

    static String stripColors(String text) { return text; }

    public Color() {}

    public static String stripEverything(String text) {
        return new Color().parseColors(text).toString();
    }

    public String cleanMessage;
    public int margin;
    public ArrayList<Span> finalSpanList;

    // prepares: cleanMessage, margin, finalSpanList
    public Color(String timestamp, String prefix, String message, final boolean enclose_nick, final boolean highlight, final int max, final int alignment) {
        if (DEBUG) logger.debug("parse(timestamp='{}', prefix='{}', message='{}', enclose_nick={}, highlight={}, max={}, align_right={})",
                timestamp, prefix, message, enclose_nick, highlight, max, alignment);
        int puff;
        ColorScheme cs = ColorScheme.get();
        StringBuilder sb = new StringBuilder();
        finalSpanList = new ArrayList<>();

        if (timestamp != null) {
            sb.append(timestamp);
            maybeMakeAndAddSpans(0, sb.length(), cs.chat_time, finalSpanList);
            sb.append(" ");
        }

        // here's our margin
        if (alignment == ALIGN_TIMESTAMP) {
            margin = sb.length();
        }

        // prefix should be adjusted according to the settings
        // also, if highlight is enabled, remove all colors from here and add highlight color later
        prefix = parseColors(prefix).toString();
        if (highlight) spanList.clear();
        boolean nickHasBeenCut = false;
        int maxAdjusted = enclose_nick ? Math.max(0, max - 2) : max;
        if (prefix.length() > maxAdjusted) {
            nickHasBeenCut = true;
            prefix = prefix.substring(0, maxAdjusted);
            Span span;
            for (Iterator<Span> it = spanList.iterator(); it.hasNext();) {
                span = it.next();
                if (span.end > maxAdjusted) span.end = maxAdjusted;
                if (span.end <= span.start) it.remove();
            }
        }
        else if (alignment==ALIGN_RIGHT && prefix.length() < maxAdjusted) {
            int diff = maxAdjusted - prefix.length();
            for (int x = 0; x < diff; x++) sb.append(" "); // spaces for padding
        }
        if (highlight) {
            maybeMakeAndAddSpans(0, prefix.length(), cs.chat_highlight, spanList);
        }
        if (enclose_nick && max >= 1) {
            sb.append("<");
            maybeMakeAndAddSpans(sb.length() - 1, sb.length(), cs.chat_nick_prefix, finalSpanList);
        }
        puff = sb.length();
        for (Span span : spanList) {
            span.start += puff;
            span.end += puff;
            finalSpanList.add(span);
        }
        sb.append(prefix);
        if (nickHasBeenCut) {
            if (enclose_nick && max >= 2) {
                sb.append(">");
                maybeMakeAndAddSpans(sb.length() - 1, sb.length(), cs.chat_nick_suffix, finalSpanList);
            }
            sb.append("+");
            maybeMakeAndAddSpans(sb.length() - 1, sb.length(), cs.chat_prefix_more, finalSpanList);
        }
        else if (enclose_nick && max >= 2) {
            sb.append("> ");
            maybeMakeAndAddSpans(sb.length() - 2, sb.length() - 1, cs.chat_nick_suffix, finalSpanList);
        }
        else sb.append(" ");

        // here's our margin
        if (alignment != ALIGN_TIMESTAMP) {
            margin = sb.length();
        }

        // the rest of the message
        message = parseColors(message).toString();
        puff = sb.length();
        for (Span span : spanList) {
            span.start += puff;
            span.end += puff;
            finalSpanList.add(span);
        }
        sb.append(message);
        cleanMessage = sb.toString();
    }

    private static void maybeMakeAndAddSpans(int start, int end, int[] color, ArrayList<Span> list) {
        if (color[0] != -1) {Span fg = new Span(); fg.start = start; fg.end = end; fg.type = Span.FGCOLOR; fg.color = color[0]; list.add(fg);}
        if (color[1] != -1) {Span bg = new Span(); bg.start = start; bg.end = end; bg.type = Span.BGCOLOR; bg.color = color[1]; list.add(bg);}
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // output of parseColors()
    private StringBuffer out;                                   // printable characters
    private ArrayList<Span> spanList = new ArrayList<>();       // list of spans in “out”

    // working vars of parseColor()
    private String msg;                                         // text currently being parsed by parseColors
    private int index;                                          // parsing position in this
    private Span[] spans = new Span[6];                         // list of currently open spans

    // this Span can be easily translated into android's spans (except REVERSE)
    public static class Span {
        final static public int BOLD =      0x00;
        final static public int UNDERLINE = 0x01;
        final static        int REVERSE =   0x02;
        final static public int ITALIC =    0x03;
        final static public int FGCOLOR =   0x04;
        final static public int BGCOLOR =   0x05;
        public int start;
        public int end;
        public int type;
        public int color;
    }

    private char getChar() {
        if (index >= msg.length()) return ' ';
        return msg.charAt(index++);
    }

    private char peekChar() {
        if (index >= msg.length()) return ' ';
        return msg.charAt(index);
    }

    /** adds a new span to the temporary span list
     ** closing a similar span if it's been open and,
     ** if possible, extending a recently closed span */
    private void addSpan(int type) {addSpan(type, -1);}
    private void addSpan(int type, int color) {
        finalizeSpan(type);
        int pos = out.length();
        // get the old span if the same span is ending at this same spot
        // if found, remove it from the list
        Span span = null;
        boolean found = false;
        for (Iterator<Span> it = spanList.iterator(); it.hasNext();) {
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
    private void finalizeSpan(int type) {
        Span span = spans[type];
        if (span != null) {
            spans[type] = null;
            span.end = out.length();
            if (span.start != span.end) {
                if (DEBUG) logger.debug("finalizeSpan(...): type={}, start={}, end={}, color={}", span.type, span.start, span.end, span.color);
                spanList.add(span);
            }
        }
    }

    /////////////////////////////////
    ///////////////////////////////// colors
    /////////////////////////////////

    /** sets weechat's special colors.
     ** sets, if available, both foreground and background colors.
     **can take form of: 05 */
    private void setWeechatColor() {
        int color_index = getNumberOfLengthUpTo(2);
        int colors[] = ColorScheme.get().getOptionColor(color_index);
        if (colors[ColorScheme.OPT_FG] != -1) addSpan(Span.FGCOLOR, colors[ColorScheme.OPT_FG]);
        if (colors[ColorScheme.OPT_BG] != -1) addSpan(Span.BGCOLOR, colors[ColorScheme.OPT_BG]);
    }

    /** parse colors/color & attribute combinations. can take form of:
     ** 05, @00123, *05, @*_00123 */
    private void setColor(int type) {
        int color;
        boolean extended = (peekChar() == '@');
        if (extended) getChar();
        if (type == Span.FGCOLOR) maybeSetAttributes();
        int which = type == Span.FGCOLOR ? 0 : 1;
        color = (extended) ? getColorExtended(which) : getColor(which);
        if (color != -1) addSpan(type, color);
    }

    // returns color in the form 0xfffff or -1
    private int getColor(int which) {
        int color_index = getNumberOfLengthUpTo(2);
        return ColorScheme.get().getWeechatColor(color_index, which);
    }

    // returns color in the form 0xfffff or -1
    private int getColorExtended(int which) {
        int color_index = getNumberOfLengthUpTo(5);
        return ColorScheme.get().getColor(color_index, which);
    }

    // returns a number stored in the next “amount” characters
    // if any of the “amount” characters is not a number, returns -1
    private static final int[] multipliers = new int[]{1, 10, 100, 1000, 10000};
    private int getNumberOfLengthUpTo(int amount) {
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
    private void maybeSetAttributes() {
        while (true) {
            int type = getAttribute(peekChar());
            if (type < -1) return;                   // next char is not an attribute
            if (type > -1) addSpan(type);            // next char is an attribute
            getChar();                               // consume if an attribute or “|”
        }
    }

    // set 1 attribute
    private void maybeSetAttribute() {
        int type = getAttribute(peekChar());
        if (type < -1) return;
        if (type > -1) addSpan(type);
        getChar();
    }

    // remove 1 attribute
    private void maybeRemoveAttribute() {
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

    // takes text as input
    // sets out and spanList
    private CharSequence parseColors(String msg) {
        if (DEBUG) logger.debug("parseColors({})", msg);

        this.msg = msg;
        index = 0;
        out = new StringBuffer();
        spanList.clear();

        if (msg == null) return out;

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
        return out;
    }
}
