package com.ubergeek42.weechat;

import java.util.HashMap;
import java.util.Properties;

public class ColorScheme {

    public static final int OPT_FG = 0;
    public static final int OPT_BG = 1;
    static ColorScheme currentColorScheme;
    public static ColorScheme currentScheme() {
        if (currentColorScheme == null) {
            currentColorScheme = new ColorScheme();
        }
        return currentColorScheme;
    }
    public static void setColorScheme(ColorScheme cs) {
        currentColorScheme = cs;
    }

    public static HashMap<String, Integer> settings = new HashMap<String, Integer>();
    static {
        settings.put("separator", 0);
        settings.put("chat", 1);
        settings.put("default", 1);
        settings.put("chat_time", 2);
        settings.put("chat_time_delimiters", 3);
        settings.put("chat_prefix_error", 4);
        settings.put("chat_prefix_network", 5);
        settings.put("chat_prefix_action", 6);
        settings.put("chat_prefix_join", 7);
        settings.put("chat_prefix_quit", 8);
        settings.put("chat_prefix_more", 9);
        settings.put("chat_prefix_suffix", 10);
        settings.put("chat_buffer", 11);
        settings.put("chat_server", 12);
        settings.put("chat_channel", 13);
        settings.put("chat_nick", 14);
        settings.put("chat_nick_self", 15);
        settings.put("chat_nick_other", 16);
        settings.put("(nick1 -- obsolete)", 17);
        settings.put("(nick2 -- obsolete)", 18);
        settings.put("(nick3 -- obsolete)", 19);
        settings.put("(nick4 -- obsolete)", 20);
        settings.put("(nick5 -- obsolete)", 21);
        settings.put("(nick6 -- obsolete)", 22);
        settings.put("(nick7 -- obsolete)", 23);
        settings.put("(nick8 -- obsolete)", 24);
        settings.put("(nick9 -- obsolete)", 25);
        settings.put("(nick10 -- obsolete)", 26);
        settings.put("chat_host", 27);
        settings.put("chat_delimiters", 28);
        settings.put("chat_highlight", 29);
        settings.put("chat_read_marker", 30);
        settings.put("chat_text_found", 31);
        settings.put("chat_value", 32);
        settings.put("chat_prefix_buffer", 33);
        settings.put("chat_tags", 34);
        settings.put("chat_inactive_window", 35);
        settings.put("chat_inactive_buffer", 36);
        settings.put("chat_prefix_buffer_inactive_buffer", 37);
        settings.put("chat_nick_offline", 38);
        settings.put("chat_nick_offline_highlight", 39);
        settings.put("chat_nick_prefix", 40);
        settings.put("chat_nick_suffix", 41);
        settings.put("emphasis", 42);
        settings.put("chat_day_change", 43);
    }

    public int basicColors[] = new int[16];
    private int extendedColors[] = new int[256];


    // 16 standard basic colors
    // http://www.calmar.ws/vim/256-xterm-24bit-rgb-color-chart.html
    private static int COLOR0  = 0x000000; // Black
    private static int COLOR1  = 0x800000; // Light Red
    private static int COLOR2  = 0x008000; // Light Green
    private static int COLOR3  = 0x808000; // Light Yellow(Brown)
    private static int COLOR4  = 0x000080; // Light Blue
    private static int COLOR5  = 0x800080; // Light Magenta
    private static int COLOR6  = 0x008080; // Light Cyan
    private static int COLOR7  = 0xC0C0C0; // Light Gray
    private static int COLOR8  = 0x808080; // Gray
    private static int COLOR9  = 0xFF0000; // Red
    private static int COLOR10 = 0x00FF00; // Green
    private static int COLOR11 = 0xFFFF00; // Yellow
    private static int COLOR12 = 0x0000FF; // Blue
    private static int COLOR13 = 0xFF00FF; // Magenta
    private static int COLOR14 = 0x00FFFF; // Cyan
    private static int COLOR15 = 0xFFFFFF; // White
    private static int DEFAULT      = COLOR7;
    private static int DEFAULT_BG   = COLOR0;

    public static int[][] weechatOptions = new int[44][2];

    public ColorScheme() {
        basicColors = new int[] {
                COLOR0,
                COLOR1,
                COLOR2,
                COLOR3,
                COLOR4,
                COLOR5,
                COLOR6,
                COLOR7,
                COLOR8,
                COLOR9,
                COLOR10,
                COLOR11,
                COLOR12,
                COLOR13,
                COLOR14,
                COLOR15,
        };
        // Extended terminal colors, from colortest.vim:
        // http://www.vim.org/scripts/script.php?script_id=1349
        int base[] = new int[]{0x00, 0x5F, 0x87, 0xAF, 0xD7, 0xFF};
        for (int i = 16; i < 232; i++) {
            int j = i - 16;
            extendedColors[i] = (base[(j / 36) % 6]) << 16 | (base[(j / 6) % 6] << 8 | (base[j % 6]));
        }
        for (int i = 232; i < 256; i++) {
            int j = 8 + i * 10;
            extendedColors[i] = j << 16 | j << 8 | j;
        }
        loadDefaultOptions();
    }

    public ColorScheme(Properties p) {
        this(); // Initialize some defaults, then load everything from our properties file
        COLOR0  = basicColors[ 0] = Integer.decode(p.getProperty("color0", "0x000000"));
        COLOR1  = basicColors[ 1] = Integer.decode(p.getProperty("color1", "0x800000"));
        COLOR2  = basicColors[ 2] = Integer.decode(p.getProperty("color2", "0x008000"));
        COLOR3  = basicColors[ 3] = Integer.decode(p.getProperty("color3", "0x808000"));
        COLOR4  = basicColors[ 4] = Integer.decode(p.getProperty("color4", "0x000080"));
        COLOR5  = basicColors[ 5] = Integer.decode(p.getProperty("color5", "0x800080"));
        COLOR6  = basicColors[ 6] = Integer.decode(p.getProperty("color6", "0x008080"));
        COLOR7  = basicColors[ 7] = Integer.decode(p.getProperty("color7", "0xC0C0C0"));
        COLOR8  = basicColors[ 8] = Integer.decode(p.getProperty("color8", "0x808080"));
        COLOR9  = basicColors[ 9] = Integer.decode(p.getProperty("color9", "0xFF0000"));
        COLOR10 = basicColors[10] = Integer.decode(p.getProperty("color10", "0x00FF00"));
        COLOR11 = basicColors[11] = Integer.decode(p.getProperty("color11", "0xFFFF00"));
        COLOR12 = basicColors[12] = Integer.decode(p.getProperty("color12", "0x0000FF"));
        COLOR13 = basicColors[13] = Integer.decode(p.getProperty("color13", "0xFF00FF"));
        COLOR14 = basicColors[14] = Integer.decode(p.getProperty("color14", "0x00FFFF"));
        COLOR15 = basicColors[15] = Integer.decode(p.getProperty("color15", "0xFFFFFF"));

        DEFAULT      = Integer.decode(p.getProperty("DEFAULT",    Integer.toString(basicColors[7])));
        DEFAULT_BG   = Integer.decode(p.getProperty("DEFAULT_BG", Integer.toString(basicColors[0])));

        loadDefaultOptions();
        for (String s: settings.keySet()) {
            String fg = p.getProperty(s);
            String bg = p.getProperty(s + "_bg");
            if (fg != null) weechatOptions[settings.get(s)][OPT_FG] = Integer.decode(fg);
            if (bg != null) weechatOptions[settings.get(s)][OPT_BG] = Integer.decode(bg);
        }

    }
    private void loadDefaultOptions(){
        // Load default option colors
        weechatOptions[ 0] = new int[]{COLOR12,           -1}; // #  0 separator
        weechatOptions[ 1] = new int[]{DEFAULT,   DEFAULT_BG}; // #  1 chat
        weechatOptions[ 2] = new int[]{DEFAULT,           -1}; // #  2 chat_time
        weechatOptions[ 3] = new int[]{ COLOR3,           -1}; // #  3 chat_time_delimiters
        weechatOptions[ 4] = new int[]{COLOR11,           -1}; // #  4 chat_prefix_error
        weechatOptions[ 5] = new int[]{COLOR13,           -1}; // #  5 chat_prefix_network
        weechatOptions[ 6] = new int[]{COLOR15,           -1}; // #  6 chat_prefix_action
        weechatOptions[ 7] = new int[]{ COLOR2,           -1}; // #  7 chat_prefix_join
        weechatOptions[ 8] = new int[]{ COLOR1,           -1}; // #  8 chat_prefix_quit
        weechatOptions[ 9] = new int[]{ COLOR5,           -1}; // #  9 chat_prefix_more
        weechatOptions[10] = new int[]{COLOR10,           -1}; // # 10 chat_prefix_suffix
        weechatOptions[11] = new int[]{COLOR15,           -1}; // # 11 chat_buffer
        weechatOptions[12] = new int[]{ COLOR3,           -1}; // # 12 chat_server
        weechatOptions[13] = new int[]{COLOR15,           -1}; // # 13 chat_channel
        weechatOptions[14] = new int[]{ COLOR6,           -1}; // # 14 chat_nick
        weechatOptions[15] = new int[]{COLOR15,           -1}; // # 15 chat_nick_self
        weechatOptions[16] = new int[]{COLOR14,           -1}; // # 16 chat_nick_other
        weechatOptions[17] = new int[]{     -1,           -1}; // # 17 (nick1 -- obsolete)
        weechatOptions[18] = new int[]{     -1,           -1}; // # 18 (nick2 -- obsolete)
        weechatOptions[19] = new int[]{     -1,           -1}; // # 19 (nick3 -- obsolete)
        weechatOptions[20] = new int[]{     -1,           -1}; // # 20 (nick4 -- obsolete)
        weechatOptions[21] = new int[]{     -1,           -1}; // # 21 (nick5 -- obsolete)
        weechatOptions[22] = new int[]{     -1,           -1}; // # 22 (nick6 -- obsolete)
        weechatOptions[23] = new int[]{     -1,           -1}; // # 23 (nick7 -- obsolete)
        weechatOptions[24] = new int[]{     -1,           -1}; // # 24 (nick8 -- obsolete)
        weechatOptions[25] = new int[]{     -1,           -1}; // # 25 (nick9 -- obsolete)
        weechatOptions[26] = new int[]{     -1,           -1}; // # 26 (nick10 -- obsolete)
        weechatOptions[27] = new int[]{COLOR14,           -1}; // # 27 chat_host
        weechatOptions[28] = new int[]{COLOR10,           -1}; // # 28 chat_delimiters
        weechatOptions[29] = new int[]{COLOR11,      COLOR13}; // # 29 chat_highlight
        weechatOptions[30] = new int[]{COLOR13,      DEFAULT}; // # 30 chat_read_marker
        weechatOptions[31] = new int[]{COLOR11,       COLOR5}; // # 31 chat_text_found
        weechatOptions[32] = new int[]{COLOR14,           -1}; // # 32 chat_value
        weechatOptions[33] = new int[]{ COLOR3,           -1}; // # 33 chat_prefix_buffer
        weechatOptions[34] = new int[]{ COLOR9,           -1}; // # 34 chat_tags
        weechatOptions[35] = new int[]{ COLOR8,           -1}; // # 35 chat_inactive_window
        weechatOptions[36] = new int[]{ COLOR8,           -1}; // # 36 chat_inactive_buffer
        weechatOptions[37] = new int[]{ COLOR8,           -1}; // # 37 chat_prefix_buffer_inactive_buffer
        weechatOptions[38] = new int[]{ COLOR8,           -1}; // # 38 chat_nick_offline
        weechatOptions[39] = new int[]{DEFAULT,       COLOR8}; // # 39 chat_nick_offline_highlight
        weechatOptions[40] = new int[]{COLOR10,           -1}; // # 40 chat_nick_prefix
        weechatOptions[41] = new int[]{COLOR10,           -1}; // # 41 chat_nick_suffix
        weechatOptions[42] = new int[]{COLOR11,      COLOR13}; // # 42 emphasis
        weechatOptions[43] = new int[]{COLOR14,           -1}; // # 43 chat_day_change
    }

    public int getColor(int i) {
        if (i< 16) {
            return basicColors[i];
        } else {
            return extendedColors[i];
        }
    }

    public int[] getOptionColor(int optindex) {
        if (optindex <0 || optindex > weechatOptions.length) {
            return new int[]{-1, -1};
        }
        return weechatOptions[optindex];
    }
    public int[] getOptionColor(String opt) {
        int index = settings.get(opt);
        return getOptionColor(index);
    }

    public int getWeechatColor(String color) {
        if (color.equalsIgnoreCase("default"))      { return getOptionColor("default")[0]; }
        if (color.equalsIgnoreCase("black"))        { return getColor( 0); }
        if (color.equalsIgnoreCase("darkgray"))     { return getColor( 8); }
        if (color.equalsIgnoreCase("red"))          { return getColor( 9); }
        if (color.equalsIgnoreCase("lightred"))     { return getColor( 1); }
        if (color.equalsIgnoreCase("green"))        { return getColor(10); }
        if (color.equalsIgnoreCase("lightgreen"))   { return getColor( 2); }
        if (color.equalsIgnoreCase("brown"))        { return getColor( 3); }
        if (color.equalsIgnoreCase("yellow"))       { return getColor(11); }
        if (color.equalsIgnoreCase("blue"))         { return getColor(12); }
        if (color.equalsIgnoreCase("lightblue"))    { return getColor( 4); }
        if (color.equalsIgnoreCase("magenta"))      { return getColor(13); }
        if (color.equalsIgnoreCase("lightmagenta")) { return getColor( 5); }
        if (color.equalsIgnoreCase("cyan"))         { return getColor(14); }
        if (color.equalsIgnoreCase("lightcyan"))    { return getColor( 6); }
        if (color.equalsIgnoreCase("gray"))         { return getColor( 7); }
        if (color.equalsIgnoreCase("white"))        { return getColor(15); }
        return getOptionColor("default")[0]; // Default color
    }
    public int getWeechatColor(int color) {
        if (color == 0) {
            return getOptionColor("default")[OPT_FG];
        }
        int mapping[] = new int[] {
                 0, // Default(handled above)
                 0, // Black
                 8, // Dark Gray
                 9, // Red
                 1, // Light Red
                10, // Green
                 2, // Light Green
                 3, // Brown
                11, // Yellow
                12, // Blue
                 4, // Light Blue
                13, // Magenta
                 5, // Light Magenta
                14, // Cyan
                 6, // Light Cyan
                 7, // Gray
                15, // White
        };
        return getColor(mapping[color]);
    }
}
