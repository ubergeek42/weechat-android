// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package com.ubergeek42.WeechatAndroid.service;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PrivateKeyPickerPreference;
import androidx.preference.ThemeManager;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.Weechat;
import com.ubergeek42.WeechatAndroid.media.Config;
import com.ubergeek42.WeechatAndroid.relay.Buffer;
import com.ubergeek42.WeechatAndroid.relay.BufferList;
import com.ubergeek42.WeechatAndroid.upload.UploadConfigKt;
import com.ubergeek42.WeechatAndroid.utils.Constants;
import com.ubergeek42.WeechatAndroid.utils.MigratePreferences;
import com.ubergeek42.WeechatAndroid.utils.ThemeFix;
import com.ubergeek42.WeechatAndroid.utils.Utils;
import com.ubergeek42.cats.Cat;
import com.ubergeek42.cats.CatD;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;
import com.ubergeek42.weechat.Color;
import com.ubergeek42.weechat.ColorScheme;
import com.ubergeek42.weechat.relay.connection.HandshakeMethod;
import com.ubergeek42.weechat.relay.connection.SSHConnection;
import com.ubergeek42.weechat.relay.connection.SSHServerKeyVerifier;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.Serializable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;

import javax.net.ssl.SSLSocketFactory;

import static com.ubergeek42.WeechatAndroid.utils.Constants.*;


public class P implements SharedPreferences.OnSharedPreferenceChangeListener{
    final private static @Root Kitty kitty = Kitty.make();

    @SuppressLint("StaticFieldLeak")
    private static Context context;
    private static SharedPreferences p;

    // we need to keep a reference, huh
    @SuppressLint("StaticFieldLeak")
    private static P instance;

    @MainThread public static void init(@NonNull Context context) {
        if (instance != null) return;
        instance = new P();
        P.context = context;
        p = PreferenceManager.getDefaultSharedPreferences(context);
        new MigratePreferences(context).migrate();
        loadUIPreferences();
        p.registerOnSharedPreferenceChangeListener(instance);
        calculateWeaselWidth();
        Config.initPreferences();
        UploadConfigKt.initPreferences();
    }

    // sets the width of weasel (effectively the recycler view) for LineView. this is a workaround
    // necessary in order to circumvent a bug (?) in ViewPager: sometimes, when measuring views, the
    // RecyclerView will have a width of 0 (esp. when paging through buffers fast) and hence
    // LineView will receive a suggested maximum width of 0 in its onMeasure().
    //      note: other views in RecyclerView don't seem to have this problem. they either receive
    //      correct values or somehow recover from width 0. the difference seems to lie in the fact
    //      that they are inflated, and not created programmatically.
    // this method is called from onStart() instead of onCreate() as onCreate() is called when the
    // activities get recreated due to theme/battery state change. for some reason, the activities
    // get recreated even though the user is using another app; if it happens in the wrong screen
    // orientation, the value is wrong.
    // todo: switch to ViewPager2 and get rid of this nonsense
    public static @Cat void calculateWeaselWidth() {
        int windowWidth = context.getResources().getDisplayMetrics().widthPixels;
        boolean slidy = context.getResources().getBoolean(R.bool.slidy);
        P.weaselWidth = slidy ? windowWidth :
                windowWidth - context.getResources().getDimensionPixelSize(R.dimen.drawer_width);
    }

    // set colorPrimary and colorPrimaryDark according to color scheme or app theme
    // must be called after theme change (activity.onCreate(), for ThemeFix.fixIconAndColor()) and
    // after color scheme change (onStart(), as in this case the activity is not recreated, before applyColorSchemeToViews())
    // this method could be called from elsewhere but it needs *activity* context
    public static void storeThemeOrColorSchemeColors(Context context) {
        ColorScheme scheme = ColorScheme.get();
        TypedArray colors = context.obtainStyledAttributes(
                new int[] {R.attr.colorPrimary, R.attr.colorPrimaryDark, R.attr.toolbarIconColor});
        colorPrimary = scheme.colorPrimary != ColorScheme.NO_COLOR ?
                scheme.colorPrimary : colors.getColor(0, ColorScheme.NO_COLOR);
        colorPrimaryDark = scheme.colorPrimaryDark != ColorScheme.NO_COLOR ?
                scheme.colorPrimaryDark : colors.getColor(1, ColorScheme.NO_COLOR);
        toolbarIconColor = colors.getColor(2, ColorScheme.NO_COLOR);
        colors.recycle();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////// ui

    final public static float _1dp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1,
            Weechat.applicationContext.getResources().getDisplayMetrics());
    final public static float _1_33dp = Math.round(1.33f * _1dp);
    final public static float _4dp = 4 * _1dp;
    final public static float _200dp = 200 * _1dp;

    public static boolean sortBuffers;
    public static boolean filterBuffers;
    public static boolean hideHiddenBuffers;
    public static boolean optimizeTraffic;
    public static boolean useGestureExclusionZone;
    public static boolean filterLines, autoHideActionbar;
    public static int maxWidth;
    public static boolean encloseNick, dimDownNonHumanLines;
    public static @Nullable DateTimeFormatter dateFormat;
    public static int align;

    public static int weaselWidth = 1080;
    public static float textSize, letterWidth;
    public static TextPaint textPaint;

    static boolean notificationEnable;
    static boolean notificationTicker;
    static boolean notificationLight;
    static boolean notificationVibrate;
    static String notificationSound;

    public static boolean showSend, showTab, showPaperclip, hotlistSync, volumeBtnSize;

    public static boolean showBufferFilter;

    public static boolean themeSwitchEnabled;
    public static boolean darkThemeActive = false;

    public static int colorPrimary = ColorScheme.NO_COLOR;
    public static int colorPrimaryDark = ColorScheme.NO_COLOR;
    public static int toolbarIconColor = ColorScheme.NO_COLOR;

    @MainThread private static void loadUIPreferences() {
        // buffer list preferences
        sortBuffers = p.getBoolean(PREF_SORT_BUFFERS, PREF_SORT_BUFFERS_D);
        filterBuffers = p.getBoolean(PREF_FILTER_NONHUMAN_BUFFERS, PREF_FILTER_NONHUMAN_BUFFERS_D);
        hideHiddenBuffers = p.getBoolean(PREF_HIDE_HIDDEN_BUFFERS, PREF_HIDE_HIDDEN_BUFFERS_D);
        optimizeTraffic = p.getBoolean(PREF_OPTIMIZE_TRAFFIC, PREF_OPTIMIZE_TRAFFIC_D);  // okay this is out of sync with onChanged stuff—used for the bell icon
        useGestureExclusionZone = p.getBoolean(PREF_USE_GESTURE_EXCLUSION_ZONE,
                Constants.PREF_USE_GESTURE_EXCLUSION_ZONE_D);

        // buffer-wide preferences
        filterLines = p.getBoolean(PREF_FILTER_LINES, PREF_FILTER_LINES_D);
        autoHideActionbar = p.getBoolean(PREF_AUTO_HIDE_ACTIONBAR, PREF_AUTO_HIDE_ACTIONBAR_D);
        maxWidth = Integer.parseInt(getString(PREF_MAX_WIDTH, PREF_MAX_WIDTH_D));
        encloseNick = p.getBoolean(PREF_ENCLOSE_NICK, PREF_ENCLOSE_NICK_D);
        dimDownNonHumanLines = p.getBoolean(PREF_DIM_DOWN, PREF_DIM_DOWN_D);
        setTimestampFormat();
        setAlignment();

        // theme
        applyThemePreference();
        themeSwitchEnabled = p.getBoolean(PREF_THEME_SWITCH, PREF_THEME_SWITCH_D);

        // notifications
        notificationEnable = p.getBoolean(PREF_NOTIFICATION_ENABLE, PREF_NOTIFICATION_ENABLE_D);
        notificationSound = p.getString(PREF_NOTIFICATION_SOUND, PREF_NOTIFICATION_SOUND_D);
        notificationTicker = p.getBoolean(PREF_NOTIFICATION_TICKER, PREF_NOTIFICATION_TICKER_D);
        notificationLight = p.getBoolean(PREF_NOTIFICATION_LIGHT, PREF_NOTIFICATION_LIGHT_D);
        notificationVibrate = p.getBoolean(PREF_NOTIFICATION_VIBRATE, PREF_NOTIFICATION_VIBRATE_D);

        // buffer fragment
        showSend = p.getBoolean(PREF_SHOW_SEND, PREF_SHOW_SEND_D);
        showTab = p.getBoolean(PREF_SHOW_TAB, PREF_SHOW_TAB_D);
        showPaperclip = p.getBoolean(PREF_SHOW_PAPERCLIP, PREF_SHOW_PAPERCLIP_D);
        hotlistSync = p.getBoolean(PREF_HOTLIST_SYNC, PREF_HOTLIST_SYNC_D);
        volumeBtnSize = p.getBoolean(PREF_VOLUME_BTN_SIZE, PREF_VOLUME_BTN_SIZE_D);

        // buffer list filter
        showBufferFilter = p.getBoolean(PREF_SHOW_BUFFER_FILTER, PREF_SHOW_BUFFER_FILTER_D);
    }

    // a brief recap on how themes work here
    // * first, we set night mode here for the whole application. applyThemePreference() does't know
    //   about activities. at this point we can't tell the effective theme, as activities can have
    //   their own local settings. this call will recreate activities, if necessary.
    // * after an activity is created, applyThemeAfterActivityCreation() is called. that's when we
    //   know the actual theme that is going to be used. this theme will be used during the whole
    //   lifecycle of the activity; if changed—by the user or the system—the activity is recreated.
    // * color scheme can be changed without changing the theme. so we call it on activity creation
    //   an on preference change.
    private static void applyThemePreference() {
        String theme = p.getString(PREF_THEME, PREF_THEME_D);
        int flag = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ?
                AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        if       (PREF_THEME_DARK.equals(theme)) flag = AppCompatDelegate.MODE_NIGHT_YES;
        else if (PREF_THEME_LIGHT.equals(theme)) flag = AppCompatDelegate.MODE_NIGHT_NO;
        AppCompatDelegate.setDefaultNightMode(flag);
    }

    public static void applyThemeAfterActivityCreation(AppCompatActivity activity) {
        darkThemeActive = ThemeFix.isNightModeEnabledForActivity(activity);
        changeColorScheme();
    }

    // todo optimize this method—might be too expensive
    private static @CatD void changeColorScheme() {
        ThemeManager.loadColorSchemeFromPreferences(context);
        setTextSizeColorAndLetterWidth();
        BufferList.onGlobalPreferencesChanged(false);
    }

    ///////////////////////////////////////////////////////////////////////////////////// connection

    public static String host;
    static String wsPath;
    static String pass;
    static String connectionType;
    static String sshHost;
    static String sshUser;
    static SSHConnection.AuthenticationMethod sshAuthenticationMethod;
    static String sshPassword;
    static byte[] sshSerializedKey;
    static public SSHServerKeyVerifier sshServerKeyVerifier;
    static public int port;
    static public HandshakeMethod handshakeMethod;
    static int sshPort;
    static SSLSocketFactory sslSocketFactory;
    static boolean reconnect;
    static public boolean pinRequired;

    static boolean pingEnabled;
    static long pingIdleTime, pingTimeout;
    public static int lineIncrement;
    public static int searchLineIncrement;

    static String printableHost;
    static boolean connectionSurelyPossibleWithCurrentPreferences;

    @MainThread public static void loadConnectionPreferences() {
        host = p.getString(PREF_HOST, PREF_HOST_D);
        pass = p.getString(PREF_PASSWORD, PREF_PASSWORD_D);
        port = Integer.parseInt(getString(PREF_PORT, PREF_PORT_D));
        handshakeMethod = HandshakeMethod.fromString(
                p.getString(PREF_HANDSHAKE_METHOD, PREF_HANDSHAKE_METHOD_D));

        wsPath = p.getString(PREF_WS_PATH, PREF_WS_PATH_D);
        pinRequired = p.getBoolean(PREF_SSL_PIN_REQUIRED, PREF_SSL_PIN_REQUIRED_D);

        connectionType = p.getString(PREF_CONNECTION_TYPE, PREF_CONNECTION_TYPE_D);
        sshHost = p.getString(PREF_SSH_HOST, PREF_SSH_HOST_D);
        sshPort = Integer.valueOf(getString(PREF_SSH_PORT, PREF_SSH_PORT_D));
        sshUser = p.getString(PREF_SSH_USER, PREF_SSH_USER_D);
        sshAuthenticationMethod = PREF_SSH_AUTHENTICATION_METHOD_KEY.equals(
                p.getString(PREF_SSH_AUTHENTICATION_METHOD, PREF_SSH_AUTHENTICATION_METHOD_D)) ?
                        SSHConnection.AuthenticationMethod.KEY : SSHConnection.AuthenticationMethod.PASSWORD;
        sshPassword = p.getString(PREF_SSH_PASSWORD, PREF_SSH_PASSWORD_D);
        sshSerializedKey = PrivateKeyPickerPreference.getData(p.getString(PREF_SSH_KEY_FILE, PREF_SSH_KEY_FILE_D));

        loadServerKeyVerifier();

        lineIncrement = Integer.parseInt(getString(PREF_LINE_INCREMENT, PREF_LINE_INCREMENT_D));
        searchLineIncrement = Integer.parseInt(getString(PREF_SEARCH_LINE_INCREMENT, PREF_SEARCH_LINE_INCREMENT_D));

        reconnect = p.getBoolean(PREF_RECONNECT, PREF_RECONNECT_D);
        optimizeTraffic = p.getBoolean(PREF_OPTIMIZE_TRAFFIC, PREF_OPTIMIZE_TRAFFIC_D);

        pingEnabled = p.getBoolean(PREF_PING_ENABLED, PREF_PING_ENABLED_D);
        pingIdleTime = Integer.parseInt(getString(PREF_PING_IDLE, PREF_PING_IDLE_D)) * 1000;
        pingTimeout = Integer.parseInt(getString(PREF_PING_TIMEOUT, PREF_PING_TIMEOUT_D)) * 1000;

        if (Utils.isAnyOf(connectionType, PREF_TYPE_SSL, PREF_TYPE_WEBSOCKET_SSL)) {
            sslSocketFactory = SSLHandler.getInstance(context).getSSLSocketFactory();
        } else {
            sslSocketFactory = null;
        }

        printableHost = connectionType.equals(PREF_TYPE_SSH) ? sshHost + "/" + host : host;
        connectionSurelyPossibleWithCurrentPreferences = false;     // and don't call me Shirley
    }

    public static void loadServerKeyVerifier() {
        if (sshServerKeyVerifier != null)
            return;

        String data = p.getString(PREF_SSH_SERVER_KEY_VERIFIER, PREF_SSH_SERVER_KEY_VERIFIER_D);

        if (data != null && !TextUtils.isEmpty(data)) {
            try {
                sshServerKeyVerifier = SSHServerKeyVerifier.decodeFromString(data);
            } catch (Exception e) {
                kitty.warn("Error while decoding server key verifier", e);
            }
        }

        if (sshServerKeyVerifier == null) sshServerKeyVerifier = new SSHServerKeyVerifier();

        sshServerKeyVerifier.setListener(() -> {
            String newData = sshServerKeyVerifier.encodeToString();
            p.edit().putString(PREF_SSH_SERVER_KEY_VERIFIER, newData).apply();
        });
    }

    @MainThread public static @StringRes int validateConnectionPreferences() {
        if (TextUtils.isEmpty(host)) return R.string.error__pref_validation__relay_host_not_set;
        if (connectionType.equals(PREF_TYPE_SSH)) {
            if (TextUtils.isEmpty(sshHost)) return R.string.error__pref_validation__ssh_host_not_set;
            if (sshAuthenticationMethod == SSHConnection.AuthenticationMethod.KEY) {
                if (Utils.isEmpty(sshSerializedKey)) return R.string.error__pref_validation__ssh_key_not_set;
            } else {
                if (TextUtils.isEmpty(sshPassword)) return R.string.error__pref_validation__ssh_password_not_set;
            }
        }
        return 0;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @MainThread @Override @CatD public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch (key) {
            // buffer list preferences
            case PREF_SORT_BUFFERS: sortBuffers = p.getBoolean(key, PREF_SORT_BUFFERS_D); break;
            case PREF_FILTER_NONHUMAN_BUFFERS: filterBuffers = p.getBoolean(key, PREF_FILTER_NONHUMAN_BUFFERS_D); break;
            case PREF_HIDE_HIDDEN_BUFFERS: hideHiddenBuffers = p.getBoolean(key, PREF_HIDE_HIDDEN_BUFFERS_D); break;
            case PREF_AUTO_HIDE_ACTIONBAR: autoHideActionbar = p.getBoolean(key, PREF_AUTO_HIDE_ACTIONBAR_D); break;
            case PREF_USE_GESTURE_EXCLUSION_ZONE: useGestureExclusionZone = p.getBoolean(key, PREF_USE_GESTURE_EXCLUSION_ZONE_D); break;

            // buffer-wide preferences
            case PREF_FILTER_LINES:
                filterLines = p.getBoolean(key, PREF_FILTER_LINES_D);
                BufferList.onGlobalPreferencesChanged(true);
                break;
            case PREF_MAX_WIDTH:
                maxWidth = Integer.parseInt(getString(key, PREF_MAX_WIDTH_D));
                BufferList.onGlobalPreferencesChanged(false);
                break;
            case PREF_ENCLOSE_NICK:
                encloseNick = p.getBoolean(key, PREF_ENCLOSE_NICK_D);
                BufferList.onGlobalPreferencesChanged(false);
                break;
            case PREF_DIM_DOWN:
                dimDownNonHumanLines = p.getBoolean(key, PREF_DIM_DOWN_D);
                BufferList.onGlobalPreferencesChanged(false);
                break;
            case PREF_THEME_SWITCH:
                themeSwitchEnabled = p.getBoolean(key, PREF_THEME_SWITCH_D);
                break;
            case PREF_TIMESTAMP_FORMAT:
                setTimestampFormat();
                BufferList.onGlobalPreferencesChanged(false);
                break;
            case PREF_PREFIX_ALIGN:
                setAlignment();
                BufferList.onGlobalPreferencesChanged(false);
                break;
            case PREF_TEXT_SIZE:
            case PREF_BUFFER_FONT:
                setTextSizeColorAndLetterWidth();
                BufferList.onGlobalPreferencesChanged(false);
                break;
            case PREF_THEME:
                applyThemePreference();
                break;
            case PREF_COLOR_SCHEME_DAY:
            case PREF_COLOR_SCHEME_NIGHT:
                changeColorScheme();
                break;

            // notifications
            case PREF_NOTIFICATION_ENABLE: notificationEnable = p.getBoolean(key, PREF_NOTIFICATION_ENABLE_D); break;
            case PREF_NOTIFICATION_SOUND: notificationSound = p.getString(key, PREF_NOTIFICATION_SOUND_D); break;
            case PREF_NOTIFICATION_TICKER: notificationTicker = p.getBoolean(key, PREF_NOTIFICATION_TICKER_D); break;
            case PREF_NOTIFICATION_LIGHT: notificationLight = p.getBoolean(key, PREF_NOTIFICATION_LIGHT_D); break;
            case PREF_NOTIFICATION_VIBRATE: notificationVibrate = p.getBoolean(key, PREF_NOTIFICATION_VIBRATE_D); break;

            // buffer fragment
            case PREF_SHOW_SEND: showSend = p.getBoolean(key, PREF_SHOW_SEND_D); break;
            case PREF_SHOW_TAB: showTab = p.getBoolean(key, PREF_SHOW_TAB_D); break;
            case PREF_SHOW_PAPERCLIP: showPaperclip = p.getBoolean(key, PREF_SHOW_PAPERCLIP_D); break;
            case PREF_HOTLIST_SYNC: hotlistSync = p.getBoolean(key, PREF_HOTLIST_SYNC_D); break;
            case PREF_VOLUME_BTN_SIZE: volumeBtnSize = p.getBoolean(key, PREF_VOLUME_BTN_SIZE_D); break;

            // buffer list fragment
            case PREF_SHOW_BUFFER_FILTER: showBufferFilter = p.getBoolean(key, PREF_SHOW_BUFFER_FILTER_D); break;

            default:
                Config.onSharedPreferenceChanged(p, key);
                UploadConfigKt.onSharedPreferenceChanged(p, key);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @MainThread private static void setTimestampFormat() {
        String t = p.getString(PREF_TIMESTAMP_FORMAT, PREF_TIMESTAMP_FORMAT_D);
        dateFormat = (TextUtils.isEmpty(t)) ? null : DateTimeFormat.forPattern(t);
    }

    @MainThread private static void setAlignment() {
        String alignment = getString(PREF_PREFIX_ALIGN, PREF_PREFIX_ALIGN_D);
        switch (alignment) {
            case "right":     align = Color.ALIGN_RIGHT; break;
            case "left":      align = Color.ALIGN_LEFT; break;
            case "timestamp": align = Color.ALIGN_TIMESTAMP; break;
            default:          align = Color.ALIGN_NONE; break;
        }
    }

    @MainThread private static void setTextSizeColorAndLetterWidth() {
        textSize = Float.parseFloat(getString(PREF_TEXT_SIZE, PREF_TEXT_SIZE_D));
        String bufferFont = p.getString(PREF_BUFFER_FONT, PREF_BUFFER_FONT_D);

        Typeface typeface = Typeface.MONOSPACE;
        try {typeface = Typeface.createFromFile(bufferFont);} catch (Exception ignored) {}

        textPaint = new TextPaint();
        textPaint.setAntiAlias(true);
        textPaint.setTypeface(typeface);
        textPaint.setColor(0xFF000000 | ColorScheme.get().default_color[0]);
        textPaint.setTextSize(textSize * context.getResources().getDisplayMetrics().scaledDensity);

        letterWidth = (textPaint.measureText("m"));
    }

    @MainThread public static void setTextSizeColorAndLetterWidth(float size) {
        p.edit().putString(PREF_TEXT_SIZE, Float.toString(size)).apply();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    final private static String ALIVE = "alive";

    public static boolean isServiceAlive() {
        return p.getBoolean(ALIVE, false);
    }

    static void setServiceAlive(boolean alive) {
        p.edit().putBoolean(ALIVE, alive).apply();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// save/restore
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private final static String PREF_DATA = "sb";
    private final static String PREF_PROTOCOL_ID = "pid";

    // protocol must be changed each time anything that uses the following function changes
    // needed to make sure nothing crashes if we cannot restore the data
    private static final int PROTOCOL_ID = 18;

    @AnyThread @Cat public static void saveStuff() {
        for (Buffer buffer : BufferList.buffers) saveLastReadLine(buffer);
        String data = Utils.serialize(new Object[]{openBuffers, bufferToLastReadLine, sentMessages});
        p.edit().putString(PREF_DATA, data).putInt(PREF_PROTOCOL_ID, PROTOCOL_ID).apply();
    }

    @SuppressWarnings("unchecked")
    @MainThread @Cat public static void restoreStuff() {
        if (p.getInt(PREF_PROTOCOL_ID, -1) != PROTOCOL_ID) return;
        Object o = Utils.deserialize(p.getString(PREF_DATA, null));
        if (!(o instanceof Object[])) return;
        Object[] array = (Object[]) o;
        if (array[0] instanceof LinkedHashSet) openBuffers = (LinkedHashSet<Long>) array[0];
        if (array[1] instanceof LinkedHashMap) bufferToLastReadLine = (LinkedHashMap<Long, BufferHotData>) array[1];
        if (array[2] instanceof LinkedList) sentMessages = (LinkedList<String>) array[2];
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // contains names of open buffers. needs more synchronization?
    static public @NonNull LinkedHashSet<Long> openBuffers = new LinkedHashSet<>();

    // this stores information about last read line (in `desktop` weechat) and according number of
    // read lines/highlights. this is subtracted from highlight counts client receives from the server
    static private @NonNull LinkedHashMap<Long, BufferHotData> bufferToLastReadLine = new LinkedHashMap<>();

    synchronized public static boolean isBufferOpen(long pointer) {
        return openBuffers.contains(pointer);
    }

    synchronized public static void setBufferOpen(long pointer, boolean open) {
        if (open) openBuffers.add(pointer);
        else openBuffers.remove(pointer);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static class BufferHotData implements Serializable {
        long lastSeenLine = -1;
        long lastReadLineServer = -1;
        int readUnreads = 0;
        int readHighlights = 0;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // restore buffer's stuff. this is called for every buffer upon buffer creation
    @WorkerThread synchronized public static void restoreLastReadLine(Buffer buffer) {
        BufferHotData data = bufferToLastReadLine.get(buffer.pointer);
        if (data != null) {
            buffer.setLastSeenLine(data.lastSeenLine);
            buffer.lastReadLineServer = data.lastReadLineServer;
            buffer.readUnreads = data.readUnreads;
            buffer.readHighlights = data.readHighlights;
        }
    }

    // save buffer's stuff. this is called when information is about to be written to disk
    private synchronized static void saveLastReadLine(Buffer buffer) {
        BufferHotData data = bufferToLastReadLine.get(buffer.pointer);
        if (data == null) {
            data = new BufferHotData();
            bufferToLastReadLine.put(buffer.pointer, data);
        }
        data.lastSeenLine = buffer.getLastSeenLine();
        data.lastReadLineServer = buffer.lastReadLineServer;
        data.readUnreads = buffer.readUnreads;
        data.readHighlights = buffer.readHighlights;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// saving messages
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static LinkedList<String> sentMessages = new LinkedList<>();

    static void addSentMessage(String line) {
        for (Iterator<String> it = sentMessages.iterator(); it.hasNext();) {
            String s = it.next();
            if (line.equals(s)) it.remove();
        }
        sentMessages.add(Utils.cut(line, 2000));
        if (sentMessages.size() > 40)
            sentMessages.pop();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static String getString(String key, String defValue) {
        return p.getString(key, defValue);
    }
}
