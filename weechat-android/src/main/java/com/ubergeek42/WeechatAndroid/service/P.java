/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.ubergeek42.WeechatAndroid.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.preference.ThemeManager;
import android.text.TextUtils;

import com.ubergeek42.WeechatAndroid.relay.BufferList;
import com.ubergeek42.WeechatAndroid.utils.Utils;
import com.ubergeek42.weechat.Color;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import javax.net.ssl.SSLContext;

import static com.ubergeek42.WeechatAndroid.utils.Constants.*;

public class P implements SharedPreferences.OnSharedPreferenceChangeListener{
    final private static Logger logger = LoggerFactory.getLogger("P");
    final private static boolean DEBUG_PREFS = true;
    final private static boolean DEBUG_SAVE_RESTORE = true;

    private static Context context;
    private static SharedPreferences p;

    // we need to keep a reference, huh
    @SuppressWarnings({"FieldCanBeLocal", "unused"}) private static P instance;

    public static void init(@NonNull Context context) {
        if (P.context != null) return;
        P.context = context;
        p = PreferenceManager.getDefaultSharedPreferences(context);
        loadUIPreferences();
        p.registerOnSharedPreferenceChangeListener(instance = new P());
    }

    public static boolean sortBuffers, showTitle, filterBuffers, optimizeTraffic;
    public static boolean filterLines;
    public static int maxWidth;
    public static boolean encloseNick, dimDownNonHumanLines;
    public static @Nullable DateFormat dateFormat;
    public static int align;
    public static float textSize ,letterWidth;

    public static boolean notificationEnable, notificationTicker, notificationLight, notificationVibrate;
    public static String notificationSound;

    public static void loadUIPreferences() {
        // buffer list preferences
        sortBuffers = p.getBoolean(PREF_SORT_BUFFERS, PREF_SORT_BUFFERS_D);
        showTitle = p.getBoolean(PREF_SHOW_BUFFER_TITLES, PREF_SHOW_BUFFER_TITLES_D);
        filterBuffers = p.getBoolean(PREF_FILTER_NONHUMAN_BUFFERS, PREF_FILTER_NONHUMAN_BUFFERS_D);
        optimizeTraffic = p.getBoolean(PREF_OPTIMIZE_TRAFFIC, PREF_OPTIMIZE_TRAFFIC_D);

        // buffer-wide preferences
        filterLines = p.getBoolean(PREF_FILTER_LINES, PREF_FILTER_LINES_D);

        // buffer line-wide preferences
        maxWidth = Integer.parseInt(p.getString(PREF_MAX_WIDTH, PREF_MAX_WIDTH_D));
        encloseNick = p.getBoolean(PREF_ENCLOSE_NICK, PREF_ENCLOSE_NICK_D);
        dimDownNonHumanLines = p.getBoolean(PREF_DIM_DOWN, PREF_DIM_DOWN_D);
        setTimestampFormat();
        setAlignment();
        setTextSizeAndLetterWidth();
        ThemeManager.loadColorSchemeFromPreferences(context);

        notificationEnable = p.getBoolean(PREF_NOTIFICATION_ENABLE, PREF_NOTIFICATION_ENABLE_D);
        notificationSound = p.getString(PREF_NOTIFICATION_SOUND, PREF_NOTIFICATION_SOUND_D);
        notificationTicker = p.getBoolean(PREF_NOTIFICATION_TICKER, PREF_NOTIFICATION_TICKER_D);
        notificationLight = p.getBoolean(PREF_NOTIFICATION_LIGHT, PREF_NOTIFICATION_LIGHT_D);
        notificationVibrate = p.getBoolean(PREF_NOTIFICATION_VIBRATE, PREF_NOTIFICATION_VIBRATE_D);
    }

    public static String host;
    public static String pass, connectionType, sshHost, sshUser, sshPass, sshKeyfile;
    public static int port, sshPort;
    public static SSLContext sslContext;
    public static boolean reconnect;

    public static void loadConnectionPreferences() {
        host = p.getString(PREF_HOST, PREF_HOST_D);
        pass = p.getString(PREF_PASSWORD, PREF_PASSWORD_D);
        port = Integer.parseInt(p.getString(PREF_PORT, PREF_PORT_D));

        connectionType = p.getString(PREF_CONNECTION_TYPE, PREF_CONNECTION_TYPE_D);
        sshHost = p.getString(PREF_SSH_HOST, PREF_SSH_HOST_D);
        sshPort = Integer.valueOf(p.getString(PREF_SSH_PORT, PREF_SSH_PORT_D));
        sshUser = p.getString(PREF_SSH_USER, PREF_SSH_USER_D);
        sshPass = p.getString(PREF_SSH_PASS, PREF_SSH_PASS_D);
        sshKeyfile = p.getString(PREF_SSH_KEYFILE, PREF_SSH_KEYFILE_D);

        reconnect = p.getBoolean(PREF_RECONNECT, PREF_RECONNECT_D);

        if (Utils.isAnyOf(connectionType, PREF_TYPE_SSL, PREF_TYPE_WEBSOCKET_SSL)) {
            sslContext = SSLHandler.getInstance(context).getSSLContext();
            if (sslContext == null) throw new RuntimeException("could not init sslContext");
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// save/restore
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private final static String PREF_DATA = "sb";
    private final static String PREF_PROTOCOL_ID = "pid";

    // save everything that is needed for successful restoration of the service
    public static void saveStuff() {
        if (DEBUG_SAVE_RESTORE) logger.debug("saveStuff()");
        p.edit().putString(PREF_DATA, BufferList.getSerializedSaveData(true))
                          .putInt(PREF_PROTOCOL_ID, Utils.SERIALIZATION_PROTOCOL_ID).apply();
    }

    // restore everything. if data is an invalid protocol, 'restore' null
    public static void restoreStuff() {
        if (DEBUG_SAVE_RESTORE) logger.debug("restoreStuff()");
        boolean valid = p.getInt(PREF_PROTOCOL_ID, -1) == Utils.SERIALIZATION_PROTOCOL_ID;
        BufferList.setSaveDataFromString(valid ? p.getString(PREF_DATA, null) : null);
    }

//    /** delete open buffers, so that buffers don't remain open (after Quit).
//     ** don't delete protocol id & buffer to lrl. */
//    public static void eraseStoredStuff() {
//        if (DEBUG_SAVE_RESTORE) logger.debug("eraseStoredStuff()");
//        p.edit().putString(PREF_DATA, BufferList.getSerializedSaveData(false)).apply();
//        BufferList.syncedBuffersFullNames.clear();
//    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// prefs
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (DEBUG_PREFS) logger.warn("onSharedPreferenceChanged()");

        switch (key) {
            // buffer list preferences
            case PREF_SORT_BUFFERS: sortBuffers = p.getBoolean(key, PREF_SORT_BUFFERS_D); break;
            case PREF_SHOW_BUFFER_TITLES: showTitle = p.getBoolean(key, PREF_SHOW_BUFFER_TITLES_D); break;
            case PREF_FILTER_NONHUMAN_BUFFERS: filterBuffers = p.getBoolean(key, PREF_FILTER_NONHUMAN_BUFFERS_D); break;
            case PREF_FILTER_LINES: filterLines = p.getBoolean(key, PREF_FILTER_LINES_D); break;

            // chat lines-wide preferences
            case PREF_MAX_WIDTH:
                maxWidth = Integer.parseInt(p.getString(key, PREF_MAX_WIDTH_D));
                BufferList.notifyOpenBuffersMustBeProcessed(false);
                break;
            case PREF_DIM_DOWN:
                dimDownNonHumanLines = p.getBoolean(key, PREF_DIM_DOWN_D);
                BufferList.notifyOpenBuffersMustBeProcessed(true);
                break;
            case PREF_TIMESTAMP_FORMAT:
                setTimestampFormat();
                BufferList.notifyOpenBuffersMustBeProcessed(false);
                break;
            case PREF_PREFIX_ALIGN:
                setAlignment();
                BufferList.notifyOpenBuffersMustBeProcessed(false);
                break;
            case PREF_ENCLOSE_NICK:
                encloseNick = p.getBoolean(key, PREF_ENCLOSE_NICK_D);
                BufferList.notifyOpenBuffersMustBeProcessed(false);
                break;
            case PREF_TEXT_SIZE:
                setTextSizeAndLetterWidth();
                BufferList.notifyOpenBuffersMustBeProcessed(true);
                break;
            case PREF_BUFFER_FONT:
                setTextSizeAndLetterWidth();
                BufferList.notifyOpenBuffersMustBeProcessed(true);
                break;
            case PREF_COLOR_SCHEME:
                ThemeManager.loadColorSchemeFromPreferences(context);
                BufferList.notifyOpenBuffersMustBeProcessed(true);
                break;

            // notifications
            case PREF_NOTIFICATION_ENABLE: notificationEnable = p.getBoolean(key, PREF_NOTIFICATION_ENABLE_D); break;
            case PREF_NOTIFICATION_SOUND: notificationSound = p.getString(key, PREF_NOTIFICATION_SOUND_D); break;
            case PREF_NOTIFICATION_TICKER: notificationTicker = p.getBoolean(key, PREF_NOTIFICATION_TICKER_D); break;
            case PREF_NOTIFICATION_LIGHT: notificationLight = p.getBoolean(key, PREF_NOTIFICATION_LIGHT_D); break;
            case PREF_NOTIFICATION_VIBRATE: notificationVibrate = p.getBoolean(key, PREF_NOTIFICATION_VIBRATE_D); break;
        }
    }

    private static void setTimestampFormat() {
        String t = p.getString(PREF_TIMESTAMP_FORMAT, PREF_TIMESTAMP_FORMAT_D);
        dateFormat = (TextUtils.isEmpty(t)) ? null : new SimpleDateFormat(t);
    }

    private static void setAlignment() {
        String alignment = p.getString(PREF_PREFIX_ALIGN, PREF_PREFIX_ALIGN_D);
        switch (alignment) {
            case "right":     align = Color.ALIGN_RIGHT; break;
            case "left":      align = Color.ALIGN_LEFT; break;
            case "timestamp": align = Color.ALIGN_TIMESTAMP; break;
            default:          align = Color.ALIGN_NONE; break;
        }
    }

    private static void setTextSizeAndLetterWidth() {
        textSize = Float.parseFloat(p.getString(PREF_TEXT_SIZE, PREF_TEXT_SIZE_D));
        String fontPath = p.getString(PREF_BUFFER_FONT, PREF_BUFFER_FONT_D);
        Paint paint = new Paint();
        if (!TextUtils.isEmpty(fontPath)) {
            Typeface tf;
            try {
                tf = Typeface.createFromFile(fontPath);
            } catch (RuntimeException r) {
                tf = Typeface.MONOSPACE;
            }
            paint.setTypeface(tf);
        } else {
            paint.setTypeface(Typeface.MONOSPACE);
        }
        paint.setTextSize(textSize * context.getResources().getDisplayMetrics().scaledDensity);
        letterWidth = (paint.measureText("m"));
    }
}
