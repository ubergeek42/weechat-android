// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package com.ubergeek42.WeechatAndroid.service;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.os.Build;
import android.preference.PreferenceManager;
import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.FilePreference;
import androidx.preference.ThemeManager;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.relay.Buffer;
import com.ubergeek42.WeechatAndroid.relay.BufferList;
import com.ubergeek42.WeechatAndroid.utils.ThemeFix;
import com.ubergeek42.WeechatAndroid.utils.Utils;
import com.ubergeek42.cats.Cat;
import com.ubergeek42.cats.CatD;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;
import com.ubergeek42.weechat.Color;
import com.ubergeek42.weechat.ColorScheme;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Locale;

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
        loadUIPreferences();
        p.registerOnSharedPreferenceChangeListener(instance);
        _4dp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, context.getResources().getDisplayMetrics());
        _200dp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 200, context.getResources().getDisplayMetrics());
        calculateWeaselWidth();
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
                new int[] {R.attr.colorPrimary, R.attr.colorPrimaryDark});
        colorPrimary = scheme.colorPrimary != ColorScheme.NO_COLOR ?
                scheme.colorPrimary : colors.getColor(0, ColorScheme.NO_COLOR);
        colorPrimaryDark = scheme.colorPrimaryDark != ColorScheme.NO_COLOR ?
                scheme.colorPrimaryDark : colors.getColor(1, ColorScheme.NO_COLOR);
        colors.recycle();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////// ui

    public static float _4dp;
    public static float _200dp;

    public static boolean sortBuffers;
    public static boolean filterBuffers;
    public static boolean hideHiddenBuffers;
    public static boolean optimizeTraffic;
    public static boolean filterLines, autoHideActionbar;
    public static int maxWidth;
    public static boolean encloseNick, dimDownNonHumanLines;
    public static @Nullable DateFormat dateFormat;
    public static int align;

    public static int weaselWidth = 200;
    public static float textSize, letterWidth;
    public static Typeface typeface;
    public static TextPaint textPaint;

    static boolean notificationEnable;
    static boolean notificationTicker;
    static boolean notificationLight;
    static boolean notificationVibrate;
    static String notificationSound;

    public static boolean showSend, showTab, hotlistSync, volumeBtnSize;

    public static boolean showBufferFilter;

    public static boolean themeSwitchEnabled;
    public static boolean darkThemeActive = false;

    public static int colorPrimary = ColorScheme.NO_COLOR;
    public static int colorPrimaryDark = ColorScheme.NO_COLOR;

    public static @NonNull String filterLc = "";
    public static @NonNull String filterUc = "";

    @MainThread private static void loadUIPreferences() {
        // buffer list preferences
        sortBuffers = p.getBoolean(PREF_SORT_BUFFERS, PREF_SORT_BUFFERS_D);
        filterBuffers = p.getBoolean(PREF_FILTER_NONHUMAN_BUFFERS, PREF_FILTER_NONHUMAN_BUFFERS_D);
        hideHiddenBuffers = p.getBoolean(PREF_HIDE_HIDDEN_BUFFERS, PREF_HIDE_HIDDEN_BUFFERS_D);
        optimizeTraffic = p.getBoolean(PREF_OPTIMIZE_TRAFFIC, PREF_OPTIMIZE_TRAFFIC_D);  // okay this is out of sync with onChanged stuff—used for the bell icon

        // buffer-wide preferences
        filterLines = p.getBoolean(PREF_FILTER_LINES, PREF_FILTER_LINES_D);
        autoHideActionbar = p.getBoolean(PREF_AUTO_HIDE_ACTIONBAR, PREF_AUTO_HIDE_ACTIONBAR_D);
        maxWidth = Integer.parseInt(getString(PREF_MAX_WIDTH, PREF_MAX_WIDTH_D));
        encloseNick = p.getBoolean(PREF_ENCLOSE_NICK, PREF_ENCLOSE_NICK_D);
        dimDownNonHumanLines = p.getBoolean(PREF_DIM_DOWN, PREF_DIM_DOWN_D);
        setTimestampFormat();
        setAlignment();

        // theme
        applyThemePreference(null);
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
    private static void applyThemePreference(@Nullable String theme) {
        if (theme == null) theme = p.getString(PREF_THEME, PREF_THEME_D);
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

    private static void changeColorScheme() {
        ThemeManager.loadColorSchemeFromPreferences(context);
        BufferList.onGlobalPreferencesChanged(false);
        setTextSizeColorAndLetterWidth();
    }

    ///////////////////////////////////////////////////////////////////////////////////// connection

    public static String host;
    static String wsPath;
    static String pass;
    static String connectionType;
    static String sshHost;
    static String sshUser;
    static String sshPass;
    static byte[] sshKey, sshKnownHosts;
    static public int port;
    static int sshPort;
    static SSLSocketFactory sslSocketFactory;
    static boolean reconnect;

    static boolean pingEnabled;
    static long pingIdleTime, pingTimeout;
    public static int lineIncrement;

    static String printableHost;
    static boolean connectionSurelyPossibleWithCurrentPreferences;

    @MainThread public static void loadConnectionPreferences() {
        host = p.getString(PREF_HOST, PREF_HOST_D);
        pass = p.getString(PREF_PASSWORD, PREF_PASSWORD_D);
        port = Integer.parseInt(getString(PREF_PORT, PREF_PORT_D));
        wsPath = p.getString(PREF_WS_PATH, PREF_WS_PATH_D);

        connectionType = p.getString(PREF_CONNECTION_TYPE, PREF_CONNECTION_TYPE_D);
        sshHost = p.getString(PREF_SSH_HOST, PREF_SSH_HOST_D);
        sshPort = Integer.valueOf(getString(PREF_SSH_PORT, PREF_SSH_PORT_D));
        sshUser = p.getString(PREF_SSH_USER, PREF_SSH_USER_D);
        sshPass = p.getString(PREF_SSH_PASS, PREF_SSH_PASS_D);
        sshKey = FilePreference.getData(p.getString(PREF_SSH_KEY, PREF_SSH_KEY_D));
        sshKnownHosts = FilePreference.getData(p.getString(PREF_SSH_KNOWN_HOSTS, PREF_SSH_KNOWN_HOSTS_D));

        lineIncrement = Integer.parseInt(getString(PREF_LINE_INCREMENT, PREF_LINE_INCREMENT_D));
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

    @MainThread public static @StringRes int validateConnectionPreferences() {
        if (TextUtils.isEmpty(host)) return R.string.pref_error_relay_host_not_set;
        if (TextUtils.isEmpty(pass)) return R.string.pref_error_relay_password_not_set;
        if (connectionType.equals(PREF_TYPE_SSH)) {
            if (TextUtils.isEmpty(sshHost)) return R.string.pref_error_ssh_host_not_set;
            if (Utils.isEmpty(sshKey) && TextUtils.isEmpty(sshPass)) return R.string.pref_error_no_ssh_key;
            if (Utils.isEmpty(sshKnownHosts)) return R.string.pref_error_no_ssh_known_hosts;
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
                applyThemePreference(null);
            case PREF_COLOR_SCHEME_DAY:
            case PREF_COLOR_SCHEME_NIGHT:
                changeColorScheme();
                break;
            case PREF_USE_LAUNCHER_ICON_KITTY:
                changeLauncherIcon(p.getBoolean(key, PREF_USE_LAUNCHER_ICON_KITTY_D));
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
            case PREF_HOTLIST_SYNC: hotlistSync = p.getBoolean(key, PREF_HOTLIST_SYNC_D); break;
            case PREF_VOLUME_BTN_SIZE: volumeBtnSize = p.getBoolean(key, PREF_VOLUME_BTN_SIZE_D); break;

            // buffer list fragment
            case PREF_SHOW_BUFFER_FILTER: showBufferFilter = p.getBoolean(key, PREF_SHOW_BUFFER_FILTER_D); break;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @MainThread private static void setTimestampFormat() {
        String t = p.getString(PREF_TIMESTAMP_FORMAT, PREF_TIMESTAMP_FORMAT_D);
        dateFormat = (TextUtils.isEmpty(t)) ? null : new SimpleDateFormat(t, Locale.US);
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

        typeface = Typeface.MONOSPACE;
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

    @MainThread private static void changeLauncherIcon(boolean useKitty) {
        setComponentEnabled(WEECHAT_ACTIVITY_WEECHAT, !useKitty);
        setComponentEnabled(SHARE_TEXT_ACTIVITY_WEECHAT, !useKitty);
        setComponentEnabled(WEECHAT_ACTIVITY_KITTY, useKitty);
        setComponentEnabled(SHARE_TEXT_ACTIVITY_KITTY, useKitty);
    }

    @MainThread private static void setComponentEnabled(ComponentName component, boolean enabled) {
        context.getPackageManager().setComponentEnabledSetting(
                component,
                enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
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
    private static final int PROTOCOL_ID = 13;

    @AnyThread @Cat public static void saveStuff() {
        synchronized (BufferList.class) {for (Buffer buffer : BufferList.buffers) saveLastReadLine(buffer);}
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
        int totalOldUnreads = 0;
        int totalOldHighlights = 0;
        int totalOldOthers = 0;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // restore buffer's stuff. this is called for every buffer upon buffer creation
    @WorkerThread synchronized public static void restoreLastReadLine(Buffer buffer) {
        BufferHotData data = bufferToLastReadLine.get(buffer.pointer);
        if (data != null) {
            buffer.setLastSeenLine(data.lastSeenLine);
            buffer.lastReadLineServer = data.lastReadLineServer;
            buffer.totalReadUnreads = data.totalOldUnreads;
            buffer.totalReadHighlights = data.totalOldHighlights;
            buffer.totalReadOthers = data.totalOldOthers;
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
        data.totalOldUnreads = buffer.totalReadUnreads;
        data.totalOldHighlights = buffer.totalReadHighlights;
        data.totalOldOthers = buffer.totalReadOthers;
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
