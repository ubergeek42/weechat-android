package com.ubergeek42.weechat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class ColorScheme {

    static final int OPT_FG = 0;
    public static final int OPT_BG = 1;

    private static ColorScheme currentColorScheme;

    public static ColorScheme get() {
        if (currentColorScheme == null) currentColorScheme = new ColorScheme();
        return currentColorScheme;
    }

    public static void set(ColorScheme cs) {
        currentColorScheme = cs;
    }

    // the following 3 are loaded from constants and then, as needed,
    // populated from the properties file
    // extended colors do not get to be loaded
    private int colors[][] = new int[DEFAULT_COLORS.length][2];
    private int options[][] = new int[44][2];

    // the following are used by Color.java and UI
    int[] chat_time;
    int[] chat_highlight;
    int[] chat_nick_prefix;
    int[] chat_nick_suffix;
    int[] chat_prefix_more;
    public int[] chat_read_marker;
    public int[] default_color = new int[2];
    public int[] chat_inactive_buffer;

    final public int colorPrimary;
    final public int colorPrimaryDark;

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////// constructors /////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private ColorScheme() {
        for (int i = 0; i < DEFAULT_COLORS.length; i++) colors[i][0] = colors[i][1] = DEFAULT_COLORS[i];
        default_color[0] = colors[7][0];
        default_color[1] = colors[0][1];
        loadDefaultOptions();
        setUsedFields();
        colorPrimary = colorPrimaryDark = NO_COLOR;
    }

    public ColorScheme(Properties p) {
        for (int i = 0; i < DEFAULT_COLORS.length; i++) {
            colors[i][0] = getPropertyInt(p, "color" + i, DEFAULT_COLORS[i]);
            colors[i][1] = getPropertyInt(p, "color" + i + "_bg", colors[i][0]);
        }

        // load default & default_bg
        default_color[0] = getPropertyInt(p, "default", colors[7][0]);
        default_color[1] = getPropertyInt(p, "default_bg", colors[0][1]);

        loadDefaultOptions();
        loadOptionsFromProperties(p);
        setUsedFields();

        // alpha better be specified
        colorPrimary = getPropertyInt(p, "primary", NO_COLOR);
        colorPrimaryDark = getPropertyInt(p, "primary_dark", NO_COLOR);
    }

    private void setUsedFields() {
        chat_time = getOptionColorPair("chat_time");
        chat_highlight = getOptionColorPair("chat_highlight");
        chat_nick_prefix = getOptionColorPair("chat_nick_prefix");
        chat_nick_suffix = getOptionColorPair("chat_nick_suffix");
        chat_prefix_more = getOptionColorPair("chat_prefix_more");
        chat_read_marker = getOptionColorPair("chat_read_marker");
        chat_inactive_buffer = getOptionColorPair("chat_inactive_buffer");
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // get a pair of weechat option colors by name (e.g. "chat" -> [0x123456, 0x234567])
    private int[] getOptionColorPair(String opt) {
        return getOptionColorPair(OPTION_TO_IDX.get(opt));
    }

    // get a pair of weechat option colors by index (e.g. 1 -> [0x123456, 0x234567])
    private final static int[] INVALID = new int[]{-1, -1};
    int[] getOptionColorPair(int i) {
        if (i < 0 || i >= options.length) return INVALID;
        return options[i];
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // get color value by index. returns NO_COLOR if not found
    int getColor(int i, int which) {
        if (i < 0 || i >= colors.length) return NO_COLOR;
        return colors[i][which];
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // get "weechat color" from a set of standard (STD) colors used in weechat
    // ctrl-f "weechat color" in https://weechat.org/files/doc/devel/weechat_dev.en.html
    // returns NO_COLOR if not found
    int getWeechatColor(int i, int which) {
        if (i == 0) return default_color[OPT_FG]; //todo?
        if (i < 0 || i >= WEECHAT_COLORS_TO_COLORS.length) return NO_COLOR;
        return getColor(WEECHAT_COLORS_TO_COLORS[i], which);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////// private static final ///
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // https://unix.stackexchange.com/questions/105568/how-can-i-list-the-available-color-names
    private final static int DEFAULT_COLORS_BASIC[] = new int[]{
            0x000000, //  0 Black
            0x800000, //  1 Red
            0x008000, //  2 Green
            0x808000, //  3 Yellow (Brown)
            0x000080, //  4 Blue
            0x800080, //  5 Magenta
            0x008080, //  6 Cyan
            0xC0C0C0, //  7 Gray

            0x808080, //  8 Light Black (Dark Gray)
            0xFF0000, //  9 Light Red
            0x00FF00, // 10 Light Green
            0xFFFF00, // 11 Light Yellow
            0x0000FF, // 12 Light Blue
            0xFF00FF, // 13 Light Magenta
            0x00FFFF, // 14 Light Cyan
            0xFFFFFF  // 15 Light Gray (White)
    };

    // basic plus extended terminal colors, from colortest.vim:
    // http://www.vim.org/scripts/script.php?script_id=1349
    private final static int DEFAULT_COLORS[] = Arrays.copyOf(DEFAULT_COLORS_BASIC, 256);
    static {
        int base[] = new int[]{0x00, 0x5F, 0x87, 0xAF, 0xD7, 0xFF};
        for (int i = 16; i < 232; i++) {
            int j = i - 16;
            DEFAULT_COLORS[i] = (base[(j / 36) % 6]) << 16 | (base[(j / 6) % 6] << 8 | (base[j % 6]));
        }
        for (int i = 232; i < 256; i++) {
            int j = (i * 10 - 8) & 0xff;
            DEFAULT_COLORS[i] = j << 16 | j << 8 | j;
        }
    }

    private final static int WEECHAT_COLORS_TO_COLORS[] = new int[] {
            0, // Default (handled in a special way)
            0, // Black
            8, // Dark Gray
            1, // Red
            9, // Light Red
            2, // Green
           10, // Light Green
            3, // Brown
           11, // Yellow
            4, // Blue
           12, // Light Blue
            5, // Magenta
           13, // Light Magenta
            6, // Cyan
           14, // Light Cyan
            7, // Gray
           15, // White
    };

    // default weechat options: id, fg, bg, name
    // fg and bg stand for basic colors; can be -1 for no color and -2/-3 for default/default bg
    // https://weechat.org/files/doc/devel/weechat_dev.en.html#color_codes_in_strings
    public static final int NO_COLOR = -1;
    private static final int DEFAULT = -2;
    private static final Object[] OPTIONS_RAW = new Object[]{
               12, NO_COLOR, "separator",                          //  0
          DEFAULT,  DEFAULT, "chat",                               //  1
          DEFAULT, NO_COLOR, "chat_time",                          //  2
                3, NO_COLOR, "chat_time_delimiters",               //  3
               11, NO_COLOR, "chat_prefix_error",                  //  4
               13, NO_COLOR, "chat_prefix_network",                //  5
               15, NO_COLOR, "chat_prefix_action",                 //  6
                2, NO_COLOR, "chat_prefix_join",                   //  7
                1, NO_COLOR, "chat_prefix_quit",                   //  8
                5, NO_COLOR, "chat_prefix_more",                   //  9
               10, NO_COLOR, "chat_prefix_suffix",                 // 10
               15, NO_COLOR, "chat_buffer",                        // 11
                3, NO_COLOR, "chat_server",                        // 12
               15, NO_COLOR, "chat_channel",                       // 13
                6, NO_COLOR, "chat_nick",                          // 14
               15, NO_COLOR, "chat_nick_self",                     // 15
               14, NO_COLOR, "chat_nick_other",                    // 16
         NO_COLOR, NO_COLOR, null,                                 // 17
         NO_COLOR, NO_COLOR, null,                                 // 18
         NO_COLOR, NO_COLOR, null,                                 // 19
         NO_COLOR, NO_COLOR, null,                                 // 20
         NO_COLOR, NO_COLOR, null,                                 // 21
         NO_COLOR, NO_COLOR, null,                                 // 22
         NO_COLOR, NO_COLOR, null,                                 // 23
         NO_COLOR, NO_COLOR, null,                                 // 24
         NO_COLOR, NO_COLOR, null,                                 // 25
         NO_COLOR, NO_COLOR, null,                                 // 26
               14, NO_COLOR, "chat_host",                          // 27
               10, NO_COLOR, "chat_delimiters",                    // 28
               11,       13, "chat_highlight",                     // 29
               13,  DEFAULT, "chat_read_marker",                   // 30
               11,        5, "chat_text_found",                    // 31
               14, NO_COLOR, "chat_value",                         // 32
                3, NO_COLOR, "chat_prefix_buffer",                 // 33
                9, NO_COLOR, "chat_tags",                          // 34
                8, NO_COLOR, "chat_inactive_window",               // 35
                8, NO_COLOR, "chat_inactive_buffer",               // 36
                8, NO_COLOR, "chat_prefix_buffer_inactive_buffer", // 37
                8, NO_COLOR, "chat_nick_offline",                  // 38
          DEFAULT,        8, "chat_nick_offline_highlight",        // 39
               10, NO_COLOR, "chat_nick_prefix",                   // 40
               10, NO_COLOR, "chat_nick_suffix",                   // 41
               11,       13, "emphasis",                           // 42
               14, NO_COLOR, "chat_day_change"                     // 43
    };

    // create mapping: name -> index ("separator" -> 0) (index 0 to 43)
    private static final HashMap<String, Integer> OPTION_TO_IDX = new HashMap<>();
    static {
        for (int i = 0; i < OPTIONS_RAW.length/3; i++) {
            String option = (String) OPTIONS_RAW[i*3 + 2];
            if (option != null) OPTION_TO_IDX.put(option, i);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////// options ////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // populate options with default values taken from OPTIONS_RAW
    // this uses default_color—must be set!
    // this uses colors!—must be transformed!
    private void loadDefaultOptions() {
        for (int i = 0; i < options.length; i++) {
            int fg = (int) OPTIONS_RAW[i*3], bg = (int) OPTIONS_RAW[i*3 + 1];
            options[i] = new int[]{getDefaultBasicColor(fg, 0), getDefaultBasicColor(bg, 1)};
        }
    }

    private int getDefaultBasicColor(int i, int which) {
        if (i == NO_COLOR) return NO_COLOR;
        if (i == DEFAULT) return default_color[which];
        if (i < 0 || i >= 16) throw new RuntimeException("wrong color index: " + i);
        return colors[i][which];
    }

    // replace colors in options using the properties file
    private void loadOptionsFromProperties(Properties p) {
        for (Map.Entry<String, Integer> option : OPTION_TO_IDX.entrySet()) {
            int fg = getPropertyInt(p, option.getKey(), NO_COLOR);
            int bg = getPropertyInt(p, option.getKey() + "_bg", NO_COLOR);
            if (fg != NO_COLOR) options[option.getValue()][OPT_FG] = fg;
            if (bg != NO_COLOR) options[option.getValue()][OPT_BG] = bg;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////// helpers ////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private int getPropertyInt(Properties p, String key, int d) {
        try {return Long.decode(p.getProperty(key)).intValue();}
        catch (Exception e) {return d;}
    }
}
