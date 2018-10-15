package com.ubergeek42.weechat;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class ColorScheme {

    public static final int OPT_FG = 0;
    public static final int OPT_BG = 1;

    static ColorScheme currentColorScheme;

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
    private int basic[][] = new int[BASIC.length][2];
    private int extended[][] = new int[EXTENDED.length][2];
    private int options[][] = new int[44][2];
    private int def[] = new int[2];

    // the following are used by Color.java and UI
    public int[] chat_time;
    public int[] chat_highlight;
    public int[] chat_nick_prefix;
    public int[] chat_nick_suffix;
    public int[] chat_prefix_more;
    public int[] chat_read_marker;
    public int[] defaul;
    public int[] chat_inactive_buffer;

    private final static int[] INVALID = new int[]{-1, -1};

    //private float factor = 1.0f;

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////// constructors /////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private ColorScheme() {
        for (int i = 0; i < BASIC.length; i++) basic[i][0] = basic[i][1] = BASIC[i];
        for (int i = 0; i < EXTENDED.length; i++) extended[i][0] = extended[i][1] = EXTENDED[i];
        def[0] = BASIC[7];
        def[1] = BASIC[0];
        loadDefaultOptions();
        setUsedFields();
    }

    public ColorScheme(Properties p) {
        for (int i = 0; i < BASIC.length; i++) basic[i][0] = basic[i][1] = getPropertyInt(p, "color" + i, BASIC[i]);
        for (int i = 0; i < EXTENDED.length; i++) extended[i][0] = extended[i][1] = EXTENDED[i];

        // load default & default_bg
        def[0] = getPropertyInt(p, "DEFAULT", basic[7][0]);
        def[1] = getPropertyInt(p, "DEFAULT_BG", basic[0][1]);

        loadDefaultOptions();
        loadOptionsFromProperties(p);
        setUsedFields();

        // transform colors
        float brightness = getPropertyFloat(p, "BRIGHTNESS", 1.0f);
        float saturation = getPropertyFloat(p, "SATURATION", 1.0f);
        float soften = getPropertyFloat(p, "SOFTEN", 0.0f);
        for (int i = 0; i < basic.length; i++) basic[i][0] = transform(basic[i][0], saturation, brightness, soften);
        for (int i = 0; i < extended.length; i++) extended[i][0] = transform(extended[i][0], saturation, brightness, soften);

        brightness = getPropertyFloat(p, "BRIGHTNESS_BG", 1.0f);
        saturation = getPropertyFloat(p, "SATURATION_BG", 1.0f);
        soften = getPropertyFloat(p, "SOFTEN_BG", 0.0f);
        for (int i = 0; i < basic.length; i++) basic[i][1] = transform(basic[i][1], saturation, brightness, soften);
        for (int i = 0; i < extended.length; i++) extended[i][1] = transform(extended[i][1], saturation, brightness, soften);

    }

    private void setUsedFields() {
        chat_time = getOptionColor("chat_time");
        chat_highlight = getOptionColor("chat_highlight");
        chat_nick_prefix = getOptionColor("chat_nick_prefix");
        chat_nick_suffix = getOptionColor("chat_nick_suffix");
        chat_prefix_more = getOptionColor("chat_prefix_more");
        chat_read_marker = getOptionColor("chat_read_marker");
        defaul = getOptionColor("default");
        chat_inactive_buffer = getOptionColor("chat_inactive_buffer");
    }

    // will crash if string is not found!
    private int[] getOptionColor(String opt) {
        return getOptionColor(OPTIONS.get(opt));
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////// public methods ///////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /** get a pair of colors that fulfil a certain role. see OPTIONS_RAW
     ** returns {-1, -1} if not found */
    public int[] getOptionColor(int i) {
        if (i < 0 || i >= options.length) return INVALID;
        return options[i];
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /** get a basic or an extended color. made possible by extended only starting at 16
     ** returns -1 if not found */
    public int getColor(int i, int which) {
        if (i < 0) return -1;
        if (i < basic.length) return basic[i][which];
        if (i < extended.length) return extended[i][which];
        return -1;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /** get "weechat color" from a set of standard (STD) colors used in weechat
     ** ctrl-f "weechat color" in https://weechat.org/files/doc/devel/weechat_dev.en.html
     ** returns -1 if not found */
    public int getWeechatColor(int i, int which) {
        if (i == 0) return defaul[OPT_FG]; //todo?
        if (i < 0 || i >= WEECHAT_COLORS_TO_BASIC_COLORS.length) return -1;
        return getColor(WEECHAT_COLORS_TO_BASIC_COLORS[i], which);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////// private static final ///
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // https://unix.stackexchange.com/questions/105568/how-can-i-list-the-available-color-names
    private final static int BASIC[] = new int[]{
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

    // Extended terminal colors, from colortest.vim:
    // http://www.vim.org/scripts/script.php?script_id=1349
    private final static int EXTENDED[] = new int[256];
    static {
        int base[] = new int[]{0x00, 0x5F, 0x87, 0xAF, 0xD7, 0xFF};
        for (int i = 16; i < 232; i++) {
            int j = i - 16;
            EXTENDED[i] = (base[(j / 36) % 6]) << 16 | (base[(j / 6) % 6] << 8 | (base[j % 6]));
        }
        for (int i = 232; i < 256; i++) {
            int j = 8 + i * 10;
            EXTENDED[i] = j << 16 | j << 8 | j;
        }
    }

    private final static int WEECHAT_COLORS_TO_BASIC_COLORS[] = new int[] {
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
    private static final Object[] OPTIONS_RAW = new Object[]{
         12, -1, "separator",                          //  0
         -2, -3, "chat",                               //  1
         -2, -1, "chat_time",                          //  2
          3, -1, "chat_time_delimiters",               //  3
         11, -1, "chat_prefix_error",                  //  4
         13, -1, "chat_prefix_network",                //  5
         15, -1, "chat_prefix_action",                 //  6
          2, -1, "chat_prefix_join",                   //  7
          1, -1, "chat_prefix_quit",                   //  8
          5, -1, "chat_prefix_more",                   //  9
         10, -1, "chat_prefix_suffix",                 // 10
         15, -1, "chat_buffer",                        // 11
          3, -1, "chat_server",                        // 12
         15, -1, "chat_channel",                       // 13
          6, -1, "chat_nick",                          // 14
         15, -1, "chat_nick_self",                     // 15
         14, -1, "chat_nick_other",                    // 16
         -1, -1, null,                                 // 17
         -1, -1, null,                                 // 18
         -1, -1, null,                                 // 19
         -1, -1, null,                                 // 20
         -1, -1, null,                                 // 21
         -1, -1, null,                                 // 22
         -1, -1, null,                                 // 23
         -1, -1, null,                                 // 24
         -1, -1, null,                                 // 25
         -1, -1, null,                                 // 26
         14, -1, "chat_host",                          // 27
         10, -1, "chat_delimiters",                    // 28
         11, 13, "chat_highlight",                     // 29
         13, -2, "chat_read_marker",                   // 30
         11,  5, "chat_text_found",                    // 31
         14, -1, "chat_value",                         // 32
          3, -1, "chat_prefix_buffer",                 // 33
          9, -1, "chat_tags",                          // 34
          8, -1, "chat_inactive_window",               // 35
          8, -1, "chat_inactive_buffer",               // 36
          8, -1, "chat_prefix_buffer_inactive_buffer", // 37
          8, -1, "chat_nick_offline",                  // 38
         -2,  8, "chat_nick_offline_highlight",        // 39
         10, -1, "chat_nick_prefix",                   // 40
         10, -1, "chat_nick_suffix",                   // 41
         11, 13, "emphasis",                           // 42
         14, -1, "chat_day_change"                     // 43
    };

    // option name -> id (size 44!!)
    private static final HashMap<String, Integer> OPTIONS = new HashMap<>();
    static {
        for (int i = 0; i < OPTIONS_RAW.length/3; i++) {
            String option = (String) OPTIONS_RAW[i*3 + 2];
            if (option != null) OPTIONS.put(option, i);
        }
        OPTIONS.put("default", 1);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////// options ////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // this loads stuff from "basic" and "def"/"def_bg". see OPTIONS_RAW
    private void loadDefaultOptions() {
        for (int i = 0; i < options.length; i++) {
            int fg = (int) OPTIONS_RAW[i*3], bg = (int) OPTIONS_RAW[i*3 + 1];
            options[i] = new int[]{getBasicColorOrDefaultFgBg(fg, 0), getBasicColorOrDefaultFgBg(bg, 1)};
        }
    }

    private void loadOptionsFromProperties(Properties p) {
        for (Map.Entry<String, Integer> option : OPTIONS.entrySet()) {
            int fg = getPropertyInt(p, option.getKey(), -2);
            int bg = getPropertyInt(p, option.getKey() + "_bg", -2);
            if (fg != -2) options[option.getValue()][OPT_FG] = fg;
            if (bg != -2) options[option.getValue()][OPT_BG] = bg;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////// helpers ////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private int getPropertyInt(Properties p, String key, int d) {
        try {return Integer.decode(p.getProperty(key));}
        catch (Exception e) {return d;}
    }

    private float getPropertyFloat(Properties p, String key, float d) {
        try {return Float.parseFloat(p.getProperty(key));}
        catch (Exception e) {return d;}
    }

    private int getBasicColorOrDefaultFgBg(int i, int which) {
        if (i == -3) return def[1];
        if (i == -2) return def[0];
        if (i < 0 || i >= basic.length) return -1;
        return basic[i][which];
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////// transform ////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static int transform(int color, float saturation, float brightness, float soften) {
        if (saturation == 1.0f && brightness == 1.0f && soften == 0.0f) return color;

        float[] hsl = new float[3];
        ColorUtils.RGBToHSL(color, hsl);
        hsl[1] *= saturation;
        hsl[2] = soften(hsl[2], soften);
        hsl[2] *= brightness;

        hsl[1] = ColorUtils.constrain(hsl[1], 0, 1);
        //hsl[2] = ColorUtils.constrain(hsl[2], 0, 1);
        hsl[2] = ColorUtils.constrain(hsl[2], 0.125f, 0.875f);

        return ColorUtils.HSLToRGB(hsl);
    }

    // this is just random bs
    private static float soften(float lightness, float soften) {
        if (soften == 0f) return lightness;
        return (float) Math.log(soften * lightness + 1f) / soften;
    }
}
