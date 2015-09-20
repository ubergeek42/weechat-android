package com.ubergeek42.WeechatAndroid.utils;

public class Constants {
    public static final String PREF_NAME = "kittens!";
    final static public String PREF_MUST_STAY_DISCONNECTED = "wow!";

    // connection type
    final static public String PREF_CONNECTION_GROUP = "connection_group";
    final static public String PREF_CONNECTION_TYPE = "connection_type";
    final static public String PREF_TYPE_SSH = "ssh";
    final static public String PREF_TYPE_STUNNEL = "stunnel";
    final static public String PREF_TYPE_SSL = "ssl";
    final static public String PREF_TYPE_WEBSOCKET = "websocket";
    final static public String PREF_TYPE_WEBSOCKET_SSL = "websocket-ssl";
    final static public String PREF_TYPE_PLAIN = "plain";

    // stunnel group & insides
    final static public String PREF_STUNNEL_GROUP = "stunnel_group";
    final static public String PREF_STUNNEL_CERT = "stunnel_cert";
    final static public String PREF_STUNNEL_PASS = "stunnel_pass";

    // ssh group & insides
    final static public String PREF_SSH_GROUP = "ssh_group";
    final static public String PREF_SSH_HOST = "ssh_host";
    final static public String PREF_SSH_PORT = "ssh_port";
    final static public String PREF_SSH_USER = "ssh_user";
    final static public String PREF_SSH_PASS = "ssh_pass";
    final static public String PREF_SSH_KEYFILE = "ssh_keyfile";

    // relay
    final static public String PREF_HOST = "host";
    final static public String PREF_PORT = "port";
    final static public String PREF_PASSWORD = "password";

    // misc
    final static public String PREF_AUTO_CONNECT = "autoconnect";
    final static public String PREF_AUTO_START = "autostart";
    public static final String PREF_OPTIMIZE_TRAFFIC = "optimize_traffic";
    public final static String PREF_HOTLIST_SYNC = "hotlist_sync";

    // ping
    final static public String PREF_PING_GROUP = "ping_group";
    final static public String PREF_PING_ENABLED = "ping_enabled";
    final static public String PREF_PING_IDLE = "ping_idle";
    final static public String PREF_PING_TIMEOUT = "ping_timeout";

    // buffer list
    public static final String PREF_SORT_BUFFERS = "sort_buffers";
    public static final String PREF_FILTER_NONHUMAN_BUFFERS = "filter_nonhuman_buffers";
    public static final String PREF_SHOW_BUFFER_TITLES = "show_buffer_titles";
    public static final String PREF_SHOW_BUFFER_FILTER = "show_buffer_filter";

    // look & feel
    final static public String PREF_LOOKFEEL_GROUP = "lookfeel_group";
    public static final String PREF_TEXT_SIZE = "text_size";
    public static final String PREF_FILTER_LINES = "chatview_filters";
    public static final String PREF_PREFIX_ALIGN = "prefix_align";
    final static public String PREF_MAX_WIDTH = "prefix_max_width";
    public static final String PREF_ENCLOSE_NICK = "enclose_nick";
    final static public String PREF_TIMESTAMP_FORMAT = "timestamp_format";
    public static final String PREF_DIM_DOWN = "dim_down";
    public static final String PREF_BUFFER_FONT = "buffer_font";
    public static final String PREF_COLOR_SCHEME = "color_scheme";

    // buttons
    public final static String PREF_SHOW_SEND = "sendbtn_show";
    public final static String PREF_SHOW_TAB = "tabbtn_show";
    public final static String PREF_VOLUME_BTN_SIZE = "volumebtn_size";

    // notifications
    final static public String PREF_NOTIFICATION_ENABLE = "notification_enable";
    final static public String PREF_NOTIFICATION_SOUND = "notification_sound";
    final static public String PREF_NOTIFICATION_VIBRATE = "notification_vibrate";
    final static public String PREF_NOTIFICATION_LIGHT = "notification_light";
    final static public String PREF_NOTIFICATION_TICKER = "notification_ticker";
}
