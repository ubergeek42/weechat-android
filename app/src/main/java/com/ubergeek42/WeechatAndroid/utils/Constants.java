package com.ubergeek42.WeechatAndroid.utils;

import android.os.Build;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.Weechat;
import com.ubergeek42.WeechatAndroid.service.P;

import java.util.Set;

public class Constants {

    // connection type
    final static public String PREF_CONNECTION_GROUP = "connection_group";
    final static public String PREF_CONNECTION_TYPE = "connection_type";
    final static public String PREF_TYPE_SSH = "ssh";
    final static public String PREF_TYPE_SSL = "ssl";
    final static public String PREF_TYPE_WEBSOCKET = "websocket";
    final static public String PREF_TYPE_WEBSOCKET_SSL = "websocket-ssl";
    final static private String PREF_TYPE_PLAIN = "plain"; final public static String PREF_CONNECTION_TYPE_D = PREF_TYPE_PLAIN;

    // ssl group
    final static public String PREF_SSL_GROUP = "ssl_group";
    final static public String PREF_SSL_PIN_REQUIRED = "ssl_pin_required"; final static public boolean PREF_SSL_PIN_REQUIRED_D = false;
    final static public String PREF_SSL_CLEAR_CERTS = "ssl_clear_certs";
    final static public String PREF_SSL_CLIENT_CERTIFICATE = "ssl_client_certificate"; final static public boolean PREF_SSL_CLIENT_CERTIFICATE_D = false;

    // websocket
    final static public String PREF_WS_PATH = "ws_path"; final public static String PREF_WS_PATH_D = "weechat";

    // ssh group & insides
    final static public String PREF_SSH_GROUP = "ssh_group";
    final static public String PREF_SSH_HOST = "ssh_host"; final public static String PREF_SSH_HOST_D = "";
    final static public String PREF_SSH_PORT = "ssh_port"; final public static String PREF_SSH_PORT_D = "22";
    final static public String PREF_SSH_USER = "ssh_user"; final public static String PREF_SSH_USER_D = "";
    final static public String PREF_SSH_AUTHENTICATION_METHOD = "ssh_authentication_method";
    final static public String PREF_SSH_AUTHENTICATION_METHOD_PASSWORD = "password";
    final static public String PREF_SSH_AUTHENTICATION_METHOD_KEY = "key";
    final public static String PREF_SSH_AUTHENTICATION_METHOD_D = PREF_SSH_AUTHENTICATION_METHOD_PASSWORD;
    final static public String PREF_SSH_PASSWORD = "ssh_password";
    final public static String PREF_SSH_PASSWORD_D = "";
    final static public String PREF_SSH_KEY_FILE = "ssh_key_file";
    final public static String PREF_SSH_KEY_FILE_D = null;
    final static public String PREF_SSH_SERVER_KEY_VERIFIER = "ssh_server_key_verifier";
    final public static String PREF_SSH_SERVER_KEY_VERIFIER_D = "";

    // relay
    final static public String PREF_HOST = "host";
    final public static String PREF_HOST_D = "";
    final static public String PREF_PORT = "port";
    final public static String PREF_PORT_D = "9001";
    final static public String PREF_PASSWORD = "password";
    final public static String PREF_PASSWORD_D = "";
    final static public String PREF_HANDSHAKE_METHOD = "handshake_method";
    final static public String PREF_HANDSHAKE_METHOD_COMPATIBILITY = "compatibility";
    final static public String PREF_HANDSHAKE_METHOD_MODERN_FAST_ONLY = "modern_fast_only";
    final static public String PREF_HANDSHAKE_METHOD_MODERN_FAST_AND_SLOW = "modern_fast_and_slow";
    final static public String PREF_HANDSHAKE_METHOD_D = PREF_HANDSHAKE_METHOD_COMPATIBILITY;


    // misc
    final static public String PREF_LINE_INCREMENT = "line_increment";
    final public static String PREF_LINE_INCREMENT_D = "300";
    final static public String PREF_SEARCH_LINE_INCREMENT = "line_number_to_request_when_starting_search";
    final static public String PREF_SEARCH_LINE_INCREMENT_D = "4097";
    final static public String PREF_HANDLE_BUFFER_LINE_DATA_CHANGED = "handle_buffer_line_data_changed";
    final static public Boolean PREF_HANDLE_BUFFER_LINE_DATA_CHANGED_D = true;
    final static public String PREF_RECONNECT = "reconnect";
    final public static boolean PREF_RECONNECT_D = true;
    final static public String PREF_BOOT_CONNECT = "boot_connect";
    final public static boolean PREF_BOOT_CONNECT_D = false;
    public static final String PREF_OPTIMIZE_TRAFFIC = "optimize_traffic";
    final public static boolean PREF_OPTIMIZE_TRAFFIC_D = false;
    public final static String PREF_HOTLIST_SYNC = "hotlist_sync";
    final public static boolean PREF_HOTLIST_SYNC_D = false;

    // ping
    final static public String PREF_PING_GROUP = "ping_group";
    final static public String PREF_PING_ENABLED = "ping_enabled";
    final public static boolean PREF_PING_ENABLED_D = true;
    final static public String PREF_PING_IDLE = "ping_idle";
    final public static String PREF_PING_IDLE_D = "300";
    final static public String PREF_PING_TIMEOUT = "ping_timeout";
    final public static String PREF_PING_TIMEOUT_D = "30";

    // buffer list
    public static final String PREF_BUFFERLIST_GROUP = "bufferlist_group";
    public static final String PREF_SORT_BUFFER_LIST = "sort_buffer_list";
    public static final String PREF_SORT_BUFFER_LIST_BY_NUMBER = "by_number";
    public static final String PREF_SORT_BUFFER_LIST_BY_HOT_MESSAGES_THEN_BY_NUMBER = "by_hot_messages_then_by_number";
    public static final String PREF_SORT_BUFFER_LIST_BY_HOT_MESSAGES_THEN_BY_OTHER_MESSAGES_THEN_BY_NUMBER = "by_hot_messages_then_by_other_messages_then_by_number";
    final public static String PREF_SORT_BUFFER_LIST_D = PREF_SORT_BUFFER_LIST_BY_NUMBER;

    public static final String PREF_HIDE_HIDDEN_BUFFERS = "hide_hidden_buffers";
    final public static boolean PREF_HIDE_HIDDEN_BUFFERS_D = true;
    public static final String PREF_FILTER_NONHUMAN_BUFFERS = "filter_nonhuman_buffers";
    final public static boolean PREF_FILTER_NONHUMAN_BUFFERS_D = true;
    public static final String PREF_SHOW_BUFFER_FILTER = "show_buffer_filter";
    final public static boolean PREF_SHOW_BUFFER_FILTER_D = true;
    public static final String PREF_USE_GESTURE_EXCLUSION_ZONE = "use_gesture_exclusion_zone";
    final public static boolean PREF_USE_GESTURE_EXCLUSION_ZONE_D = true;

    // look & feel
    final static public String PREF_LOOKFEEL_GROUP = "lookfeel_group";
    public static final String PREF_TEXT_SIZE = "text_size";
    final public static String PREF_TEXT_SIZE_D = "16";
    public static final String PREF_AUTO_HIDE_ACTIONBAR = "auto_hide_actionbar";
    public static final boolean PREF_AUTO_HIDE_ACTIONBAR_D = true;
    public static final String PREF_FILTER_LINES = "chatview_filters";
    final public static boolean PREF_FILTER_LINES_D = true;
    public static final String PREF_PREFIX_ALIGN = "prefix_align";
    final public static String PREF_PREFIX_ALIGN_D = "right";
    final static public String PREF_MAX_WIDTH = "prefix_max_width";
    final public static String PREF_MAX_WIDTH_D = "7";
    public static final String PREF_ENCLOSE_NICK = "enclose_nick";
    final public static boolean PREF_ENCLOSE_NICK_D = false;
    final static public String PREF_TIMESTAMP_FORMAT = "timestamp_format";
    final public static String PREF_TIMESTAMP_FORMAT_D = "HH:mm:ss";
    public static final String PREF_DIM_DOWN = "dim_down";
    final public static boolean PREF_DIM_DOWN_D = true;
    public static final String PREF_BUFFER_FONT = "buffer_font";
    final public static String PREF_BUFFER_FONT_D = "";
    public static final String PREF_COLOR_SCHEME_DAY = "color_scheme_day";
    final public static String PREF_COLOR_SCHEME_DAY_D = "squirrely-light-theme.properties";
    public static final String PREF_COLOR_SCHEME_NIGHT = "color_scheme_night";
    final public static String PREF_COLOR_SCHEME_NIGHT_D = "squirrely-dark-theme.properties";

    // buttons
    public final static String PREF_SHOW_SEND = "sendbtn_show";
    final public static boolean PREF_SHOW_SEND_D = true;
    public final static String PREF_SHOW_TAB = "tabbtn_show";
    final public static boolean PREF_SHOW_TAB_D = true;
    public final static String PREF_VOLUME_ROLE = "buttons__volume";
    final public static String PREF_VOLUME_ROLE_D = P.VolumeRole.ChangeTextSize.value;

    final public static String PREF_SHOW_PAPERCLIP = "buttons__show_paperclip";
    final public static boolean PREF_SHOW_PAPERCLIP_D = true;
    final public static String PREF_PAPERCLIP_ACTION_NONE = "none";
    final public static String PREF_PAPERCLIP_ACTION_CONTENT_IMAGES = "content_images";
    final public static String PREF_PAPERCLIP_ACTION_CONTENT_MEDIA = "content_media";
    final public static String PREF_PAPERCLIP_ACTION_CONTENT_ANYTHING = "content_anything";
    final public static String PREF_PAPERCLIP_ACTION_MEDIASTORE_IMAGES = "mediastore_images";
    final public static String PREF_PAPERCLIP_ACTION_MEDIASTORE_MEDIA = "mediastore_media";
    final public static String PREF_PAPERCLIP_ACTION_CAMERA = "camera";
    final public static String PREF_PAPERCLIP_ACTION_1 = "buttons__paperclip_action_1";
    final public static String PREF_PAPERCLIP_ACTION_2 = "buttons__paperclip_action_2";
    final public static String PREF_PAPERCLIP_ACTION_1_D = PREF_PAPERCLIP_ACTION_CONTENT_MEDIA;
    final public static String PREF_PAPERCLIP_ACTION_2_D = PREF_PAPERCLIP_ACTION_CAMERA;

    // notifications
    final static public String PREF_NOTIFICATION_GROUP = "notif_group";
    final static public String PREF_NOTIFICATION_ENABLE = "notification_enable"; final public static boolean PREF_NOTIFICATION_ENABLE_D = true;
    final static public String PREF_NOTIFICATION_SOUND = "notification_sound"; final public static String PREF_NOTIFICATION_SOUND_D = "content://settings/system/notification_sound";
    final static public String PREF_NOTIFICATION_VIBRATE = "notification_vibrate"; final public static boolean PREF_NOTIFICATION_VIBRATE_D = false;
    final static public String PREF_NOTIFICATION_LIGHT = "notification_light"; final public static boolean PREF_NOTIFICATION_LIGHT_D = false;

    public final static String EXTRA_BUFFER_POINTER = "com.ubergeek42.BUFFER_POINTER";
    public final static String EXTRA_BUFFER_FULL_NAME = "com.ubergeek42.BUFFER_FULL_NAME";
    public final static long EXTRA_BUFFER_POINTER_ANY = 0;
    public final static String EXTRA_BUFFER_FULL_NAME_ANY = "";

    // night mode
    final static public String PREF_THEME_GROUP = "theme_group";
    final public static String PREF_THEME = "theme";
    final public static String PREF_THEME_SYSTEM = "system";
    final public static String PREF_THEME_DARK = "dark";
    final public static String PREF_THEME_LIGHT = "light";
    final public static String PREF_THEME_D = PREF_THEME_SYSTEM;

    // media preview
    final public static String PREF_MEDIA_PREVIEW_GROUP = "media_preview_group";
    final public static String PREF_MEDIA_PREVIEW_ENABLED_FOR_NETWORK = "media_preview_enabled_for_network";
    final public static String PREF_MEDIA_PREVIEW_ENABLED_FOR_NETWORK_NEVER = "never";
    final public static String PREF_MEDIA_PREVIEW_ENABLED_FOR_NETWORK_WIFI_ONLY = "wifi_only";
    final public static String PREF_MEDIA_PREVIEW_ENABLED_FOR_NETWORK_UNMETERED_ONLY = "unmetered_only";
    final public static String PREF_MEDIA_PREVIEW_ENABLED_FOR_NETWORK_ALWAYS = "always";
    final public static String PREF_MEDIA_PREVIEW_ENABLED_FOR_NETWORK_D = PREF_MEDIA_PREVIEW_ENABLED_FOR_NETWORK_NEVER;

    final public static String PREF_MEDIA_PREVIEW_ENABLED_FOR_LOCATION = "media_preview_enabled_for_location";
    final public static String PREF_MEDIA_PREVIEW_ENABLED_FOR_LOCATION_CHAT = "chat";
    final public static String PREF_MEDIA_PREVIEW_ENABLED_FOR_LOCATION_PASTE = "paste";
    final public static String PREF_MEDIA_PREVIEW_ENABLED_FOR_LOCATION_NOTIFICATIONS = "notifications";
    final public static Set<String> PREF_MEDIA_PREVIEW_ENABLED_FOR_LOCATION_D =
            Build.VERSION.SDK_INT >= 24 ?
                    Utils.makeSet("chat", "paste", "notifications") :
                    Utils.makeSet("chat", "paste");

    final public static String PREF_MEDIA_PREVIEW_SECURE_REQUEST = "media_preview_secure_request";
    final public static String PREF_MEDIA_PREVIEW_SECURE_REQUEST_OPTIONAL = "optional";
    final public static String PREF_MEDIA_PREVIEW_SECURE_REQUEST_REWRITE = "rewrite";
    final public static String PREF_MEDIA_PREVIEW_SECURE_REQUEST_REQUIRED = "required";
    final public static String PREF_MEDIA_PREVIEW_SECURE_REQUEST_D = PREF_MEDIA_PREVIEW_SECURE_REQUEST_REWRITE;

    final public static String PREF_MEDIA_PREVIEW_HELP = "media_preview_help";
    final public static String PREF_MEDIA_PREVIEW_ADVANCED_GROUP = "media_preview_advanced_group";


    final public static String PREF_MEDIA_PREVIEW_MAXIMUM_BODY_SIZE = "media_preview_maximum_body_size";
    final public static String PREF_MEDIA_PREVIEW_MAXIMUM_BODY_SIZE_D = "10";
    final public static String PREF_IMAGE_DISK_CACHE_SIZE = "image_disk_cache_size";
    final public static String PREF_IMAGE_DISK_CACHE_SIZE_D = "250";
    final public static String PREF_MEDIA_PREVIEW_SUCCESS_COOLDOWN = "media_preview_success_cooldown";
    final public static String PREF_MEDIA_PREVIEW_SUCCESS_COOLDOWN_D = "24";

    final public static String PREF_MEDIA_PREVIEW_THUMBNAIL_WIDTH = "media_preview_thumbnail_width";
    final public static String PREF_MEDIA_PREVIEW_THUMBNAIL_WIDTH_D = "80";
    final public static String PREF_MEDIA_PREVIEW_THUMBNAIL_MIN_HEIGHT = "media_preview_thumbnail_min_height";
    final public static String PREF_MEDIA_PREVIEW_THUMBNAIL_MIN_HEIGHT_D = "40";
    final public static String PREF_MEDIA_PREVIEW_THUMBNAIL_MAX_HEIGHT = "media_preview_thumbnail_max_height";
    final public static String PREF_MEDIA_PREVIEW_THUMBNAIL_MAX_HEIGHT_D = "160";

    final public static String PREF_MEDIA_PREVIEW_STRATEGIES = "media_preview_strategies";
    final public static String PREF_MEDIA_PREVIEW_STRATEGIES_D = getResourceString(R.string.pref__media_preview__strategies_default);


    // uploading
    final public static String PREF_UPLOAD_GROUP = "upload_group";
    final public static String PREF_UPLOAD_ACCEPT = "upload_accept_shared";
    final public static String PREF_UPLOAD_ACCEPT_TEXT_ONLY = "text_only";
    final public static String PREF_UPLOAD_ACCEPT_TEXT_AND_MEDIA = "text_and_media";
    final public static String PREF_UPLOAD_ACCEPT_EVERYTHING = "everything";
    final public static String PREF_UPLOAD_ACCEPT_D = PREF_UPLOAD_ACCEPT_EVERYTHING;

    final public static String PREF_UPLOAD_NO_OF_DIRECT_SHARE_TARGETS = "upload_no_of_direct_share_targets";    // "0" ... "4"
    final public static String PREF_UPLOAD_NO_OF_DIRECT_SHARE_TARGETS_D = "2";


    final public static String PREF_UPLOAD_URI = "upload_uri";
    final public static String PREF_UPLOAD_URI_D = "";
    final public static String PREF_UPLOAD_FORM_FIELD_NAME = "upload_form_field_name";
    final public static String PREF_UPLOAD_FORM_FIELD_NAME_D = "file";
    final public static String PREF_UPLOAD_REGEX = "upload_regex";
    final public static String PREF_UPLOAD_REGEX_D = "^https://\\S+";
    final public static String PREF_UPLOAD_HELP = "upload_help";
    final public static String PREF_UPLOAD_ADVANCED_GROUP = "upload_advanced_group";
    final public static String PREF_UPLOAD_ADDITIONAL_HEADERS = "upload_additional_headers";
    final public static String PREF_UPLOAD_ADDITIONAL_HEADERS_D = "";
    final public static String PREF_UPLOAD_ADDITIONAL_FIELDS = "upload_additional_fields";
    final public static String PREF_UPLOAD_ADDITIONAL_FIELDS_D = "";
    final public static String PREF_UPLOAD_AUTHENTICATION = "upload_authentication";
    final public static String PREF_UPLOAD_AUTHENTICATION_NONE = "none";
    final public static String PREF_UPLOAD_AUTHENTICATION_BASIC = "basic";
    final public static String PREF_UPLOAD_AUTHENTICATION_D = PREF_UPLOAD_AUTHENTICATION_NONE;
    final public static String PREF_UPLOAD_AUTHENTICATION_BASIC_USER = "upload_authentication_basic_user";
    final public static String PREF_UPLOAD_AUTHENTICATION_BASIC_USER_D = "";
    final public static String PREF_UPLOAD_AUTHENTICATION_BASIC_PASSWORD = "upload_authentication_basic_password";
    final public static String PREF_UPLOAD_AUTHENTICATION_BASIC_PASSWORD_D = "";
    final public static String PREF_UPLOAD_REMEMBER_UPLOADS_FOR = "upload_remember_uploads_for";
    final public static String PREF_UPLOAD_REMEMBER_UPLOADS_FOR_D = "24";   // hours


    // etc
    final public static String PREF_THEME_SWITCH = "theme_switch";
    final public static boolean PREF_THEME_SWITCH_D = false;

    @SuppressWarnings("SameParameterValue")
    private static String getResourceString(int id) {
        return Weechat.applicationContext.getResources().getString(id);
    }

    static public class Deprecated {
        final static public String PREF_SSH_PASS = "ssh_pass"; final public static String PREF_SSH_PASS_D = "";
        final static public String PREF_SSH_KEY = "ssh_key"; final public static String PREF_SSH_KEY_D = null;
        final static public String PREF_SSH_KEY_PASSPHRASE = "ssh_key_passphrase"; final public static String PREF_SSH_KEY_PASSPHRASE_D = null;
        final static public String PREF_SSH_KNOWN_HOSTS = "ssh_known_hosts"; final public static String PREF_SSH_KNOWN_HOSTS_D = "";
        final static public String PREF_VOLUME_BTN_SIZE = "volumebtn_size"; final public static boolean PREF_VOLUME_BTN_SIZE_D = true;
    }
}
