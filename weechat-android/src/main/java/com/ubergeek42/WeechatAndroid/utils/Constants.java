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
    final static public String PREF_TYPE_PLAIN = "plain"; final public static String PREF_CONNECTION_TYPE_D = PREF_TYPE_PLAIN;

    // stunnel group & insides
    final static public String PREF_STUNNEL_GROUP = "stunnel_group";
    final static public String PREF_STUNNEL_CERT = "stunnel_cert"; final public static String PREF_STUNNEL_CERT_D = "/sdcard/weechat/client.p12";
    final static public String PREF_STUNNEL_PASS = "stunnel_pass"; final public static String PREF_STUNNEL_PASS_D = "";

    // ssh group & insides
    final static public String PREF_SSH_GROUP = "ssh_group";
    final static public String PREF_SSH_HOST = "ssh_host"; final public static String PREF_SSH_HOST_D = "";
    final static public String PREF_SSH_PORT = "ssh_port"; final public static String PREF_SSH_PORT_D = "22";
    final static public String PREF_SSH_USER = "ssh_user"; final public static String PREF_SSH_USER_D = "";
    final static public String PREF_SSH_PASS = "ssh_pass"; final public static String PREF_SSH_PASS_D = "";
    final static public String PREF_SSH_KEYFILE = "ssh_keyfile"; final public static String PREF_SSH_KEYFILE_D = "/sdcard/weechat/sshkey.id_rsa";

    // relay
    final static public String PREF_HOST = "host"; final public static String PREF_HOST_D = null;
    final static public String PREF_PORT = "port"; final public static String PREF_PORT_D = "9001";
    final static public String PREF_PASSWORD = "password"; final public static String PREF_PASSWORD_D = null;

    // misc
    final static public String PREF_AUTO_CONNECT = "autoconnect"; final public static boolean PREF_AUTO_CONNECT_D = false;
    final static public String PREF_AUTO_START = "autostart"; final public static boolean PREF_AUTO_START_D = false;
    public static final String PREF_OPTIMIZE_TRAFFIC = "optimize_traffic"; final public static boolean PREF_OPTIMIZE_TRAFFIC_D = false;
    public final static String PREF_HOTLIST_SYNC = "hotlist_sync"; final public static boolean PREF_HOTLIST_SYNC_D = false;

    // ping
    final static public String PREF_PING_GROUP = "ping_group";
    final static public String PREF_PING_ENABLED = "ping_enabled"; final public static boolean PREF_PING_ENABLED_D = true;
    final static public String PREF_PING_IDLE = "ping_idle"; final public static String PREF_PING_IDLE_D = "300";
    final static public String PREF_PING_TIMEOUT = "ping_timeout"; final public static String PREF_PING_TIMEOUT_D = "30";

    // buffer list
    public static final String PREF_SORT_BUFFERS = "sort_buffers"; final public static boolean PREF_SORT_BUFFERS_D = false;
    public static final String PREF_FILTER_NONHUMAN_BUFFERS = "filter_nonhuman_buffers"; final public static boolean PREF_FILTER_NONHUMAN_BUFFERS_D = false;
    public static final String PREF_SHOW_BUFFER_TITLES = "show_buffer_titles"; final public static boolean PREF_SHOW_BUFFER_TITLES_D = false; 
    public static final String PREF_SHOW_BUFFER_FILTER = "show_buffer_filter"; final public static boolean PREF_SHOW_BUFFER_FILTER_D = false;

    // look & feel
    final static public String PREF_LOOKFEEL_GROUP = "lookfeel_group";
    public static final String PREF_TEXT_SIZE = "text_size"; final public static String PREF_TEXT_SIZE_D = "12";
    public static final String PREF_FILTER_LINES = "chatview_filters"; final public static boolean PREF_FILTER_LINES_D = true;
    public static final String PREF_PREFIX_ALIGN = "prefix_align"; final public static String PREF_PREFIX_ALIGN_D = "right";
    final static public String PREF_MAX_WIDTH = "prefix_max_width"; final public static String PREF_MAX_WIDTH_D = "7";
    public static final String PREF_ENCLOSE_NICK = "enclose_nick"; final public static boolean PREF_ENCLOSE_NICK_D = false;
    final static public String PREF_TIMESTAMP_FORMAT = "timestamp_format"; final public static String PREF_TIMESTAMP_FORMAT_D = "HH:mm:ss";
    public static final String PREF_DIM_DOWN = "dim_down"; final public static boolean PREF_DIM_DOWN_D = false;
    public static final String PREF_BUFFER_FONT = "buffer_font"; final public static String PREF_BUFFER_FONT_D = "";
    public static final String PREF_COLOR_SCHEME = "color_scheme"; final public static String PREF_COLOR_SCHEME_D = "default-theme.properties";

    // buttons
    public final static String PREF_SHOW_SEND = "sendbtn_show"; final public static boolean PREF_SHOW_SEND_D = true;
    public final static String PREF_SHOW_TAB = "tabbtn_show"; final public static boolean PREF_SHOW_TAB_D = true;
    public final static String PREF_VOLUME_BTN_SIZE = "volumebtn_size"; final public static boolean PREF_VOLUME_BTN_SIZE_D = true;

    // notifications
    final static public String PREF_NOTIFICATION_ENABLE = "notification_enable"; final public static boolean PREF_NOTIFICATION_ENABLE_D = true;
    final static public String PREF_NOTIFICATION_SOUND = "notification_sound"; final public static String PREF_NOTIFICATION_SOUND_D = "content://settings/system/notification_sound";
    final static public String PREF_NOTIFICATION_VIBRATE = "notification_vibrate"; final public static boolean PREF_NOTIFICATION_VIBRATE_D = false;
    final static public String PREF_NOTIFICATION_LIGHT = "notification_light"; final public static boolean PREF_NOTIFICATION_LIGHT_D = false;
    final static public String PREF_NOTIFICATION_TICKER = "notification_ticker"; final public static boolean PREF_NOTIFICATION_TICKER_D = true;
}
