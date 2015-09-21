package com.ubergeek42.WeechatAndroid.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.IBinder;
import android.os.SystemClock;
import android.text.TextUtils;
import android.widget.Toast;

import com.ubergeek42.WeechatAndroid.Manifest;
import com.ubergeek42.WeechatAndroid.utils.Utils;
import com.ubergeek42.weechat.Color;
import com.ubergeek42.weechat.ColorScheme;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Properties;

import static com.ubergeek42.WeechatAndroid.utils.Constants.*;

/**
 ** the service can be started by:
 **     * activity
 **         in which case we don't want to anything special
 **     * system, when it restores the app after it's been shut down
 **         in which case we want to "reattach" ourselves to all buffers we had open
 **         but we don't want to request all lines for every buffer since user might
 **         not be coming back before another disconnection or service destruction
 **
 ** hence we maintain a list of open buffers.
 **
 ** upon Buffer creation, buffer determines if it's open and if so, subscribes to changes
 ** and starts processing all future incoming lines.
 **
 ** upon Buffer's attachment to a fragment, if needed, that Buffer should request missing lines
 ** and nicklist (TODO)
 **/

public class RelayService extends RelayServiceBackbone {
    private static Logger logger = LoggerFactory.getLogger("RelayService");
    final private static boolean DEBUG_PREFS = false;
    final private static boolean DEBUG_SAVE_RESTORE = false;

    private PingActionReceiver pingActionReceiver;

    /** super method sets 'prefs' */
    @Override
    public void onCreate() {
        super.onCreate();

        pingActionReceiver = new PingActionReceiver(this);
        registerReceiver(
                pingActionReceiver,
                new IntentFilter(PingActionReceiver.PING_ACTION),
                Manifest.permission.PING_ACTION,
                null
        );

        // buffer list preferences
        BufferList.SORT_BUFFERS = prefs.getBoolean(PREF_SORT_BUFFERS, PREF_SORT_BUFFERS_D);
        BufferList.SHOW_TITLE = prefs.getBoolean(PREF_SHOW_BUFFER_TITLES, PREF_SHOW_BUFFER_TITLES_D);
        BufferList.FILTER_NONHUMAN_BUFFERS = prefs.getBoolean(PREF_FILTER_NONHUMAN_BUFFERS, PREF_FILTER_NONHUMAN_BUFFERS_D);
        BufferList.OPTIMIZE_TRAFFIC = prefs.getBoolean(PREF_OPTIMIZE_TRAFFIC, PREF_OPTIMIZE_TRAFFIC_D);

        // buffer-wide preferences
        Buffer.FILTER_LINES = prefs.getBoolean(PREF_FILTER_LINES, PREF_FILTER_LINES_D);

        // buffer line-wide preferences
        Buffer.Line.MAX_WIDTH = Integer.parseInt(prefs.getString(PREF_MAX_WIDTH, PREF_MAX_WIDTH_D));
        Buffer.Line.ENCLOSE_NICK = prefs.getBoolean(PREF_ENCLOSE_NICK, PREF_ENCLOSE_NICK_D);
        Buffer.Line.DIM_DOWN_NON_HUMAN_LINES = prefs.getBoolean(PREF_DIM_DOWN, PREF_DIM_DOWN_D);
        setTimestampFormat();
        setAlignment();
        setTextSizeAndLetterWidth();
        loadColorScheme();
    }

    @Override
    public void onDisconnect() {
        saveStuff();
        super.onDisconnect();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new RelayServiceBinder(this);
    }

    /** called when all fragments et al detached, i.e. on app minimize
     ** it's not guaranteed that this will be called but it's called virtually always
     ** must return true in order to be called again. a bug within android? */
    @Override
    public boolean onUnbind(Intent intent) {
        saveStuff();
        return true;
    }

    final private static int SYNC_EVERY_MS = 60 * 5 * 1000; // 5 minutes

    /** called upon authenticating. let's do our job!
     ** TODO although it might be wise not to create everything from scratch...  */
    @Override
    void startHandlingBoneEvents() {
        restoreStuff();
        BufferList.OPTIMIZE_TRAFFIC = prefs.getBoolean(PREF_OPTIMIZE_TRAFFIC, PREF_OPTIMIZE_TRAFFIC_D);
        BufferList.launch(this);

        // schedule updates
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, SyncAlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + SYNC_EVERY_MS, SYNC_EVERY_MS, pi);

        // subscribe to any future changes
        // starting with weechat 1.1, "sync * buffers" also gets use buffer localvars,
        // so it's safe to request them; handling of these is no different from full sync
        connection.sendMsg(BufferList.OPTIMIZE_TRAFFIC ? "sync * buffers,upgrade" : "sync");
    }

    /** onDestroy will only be called when properly exiting the application
     ** maybe. anyways, user pressed Quit — erase open buffers */
    @Override
    public void onDestroy() {
        eraseStoredStuff();
        unregisterReceiver(pingActionReceiver);
        super.onDestroy();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// save/restore
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private final static String PREF_DATA = "sb";
    private final static String PREF_PROTOCOL_ID = "pid";

    /** save everything that is needed for successful restoration of the service */
    private void saveStuff() {
        if (DEBUG_SAVE_RESTORE) logger.debug("saveStuff()");
        SharedPreferences preferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        preferences.edit().putString(PREF_DATA, BufferList.getSerializedSaveData(true))
                          .putInt(PREF_PROTOCOL_ID, Utils.SERIALIZATION_PROTOCOL_ID).apply();
    }

    /** restore everything. if data is an invalid protocol, 'restore' null */
    private void restoreStuff() {
        if (DEBUG_SAVE_RESTORE) logger.debug("restoreStuff()");
        SharedPreferences preferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        boolean valid = preferences.getInt(PREF_PROTOCOL_ID, -1) == Utils.SERIALIZATION_PROTOCOL_ID;
        BufferList.setSaveDataFromString(valid ? preferences.getString(PREF_DATA, null) : null);
    }

    /** delete open buffers, so that buffers don't remain open (after Quit).
     ** don't delete protocol id & buffer to lrl. */
    private void eraseStoredStuff() {
        if (DEBUG_SAVE_RESTORE) logger.debug("eraseStoredStuff()");
        SharedPreferences preferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        preferences.edit().putString(PREF_DATA, BufferList.getSerializedSaveData(false)).apply();
        BufferList.syncedBuffersFullNames.clear();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// prefs
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (DEBUG_PREFS) logger.warn("onSharedPreferenceChanged()");

        // buffer list preferences
        super.onSharedPreferenceChanged(sharedPreferences, key);
        if (key.equals(PREF_SORT_BUFFERS)) {
            BufferList.SORT_BUFFERS = prefs.getBoolean(key, PREF_SORT_BUFFERS_D);
        } else if (key.equals(PREF_SHOW_BUFFER_TITLES)) {
            BufferList.SHOW_TITLE = prefs.getBoolean(key, PREF_SHOW_BUFFER_TITLES_D);
        } else if (key.equals(PREF_FILTER_NONHUMAN_BUFFERS)) {
            BufferList.FILTER_NONHUMAN_BUFFERS = prefs.getBoolean(key, PREF_FILTER_NONHUMAN_BUFFERS_D);

        // only update traffic optimization on connect
        //    // traffic preference
        //} else if (key.equals(PREF_OPTIMIZE_TRAFFIC)) {
        //    BufferList.OPTIMIZE_TRAFFIC = prefs.getBoolean(key, false);

            // buffer-wide preferences
        } else if (key.equals(PREF_FILTER_LINES)) {
            Buffer.FILTER_LINES = prefs.getBoolean(key, PREF_FILTER_LINES_D);

            // chat lines-wide preferences
        } else if (key.equals(PREF_MAX_WIDTH)) {
            Buffer.Line.MAX_WIDTH = Integer.parseInt(prefs.getString(key, PREF_MAX_WIDTH_D));
            BufferList.notifyOpenBuffersMustBeProcessed(false);
        } else if (key.equals(PREF_DIM_DOWN)) {
            Buffer.Line.DIM_DOWN_NON_HUMAN_LINES = prefs.getBoolean(key, PREF_DIM_DOWN_D);
            BufferList.notifyOpenBuffersMustBeProcessed(true);
        } else if (key.equals(PREF_TIMESTAMP_FORMAT)) {
            setTimestampFormat();
            BufferList.notifyOpenBuffersMustBeProcessed(false);
        } else if (key.equals(PREF_PREFIX_ALIGN)) {
            setAlignment();
            BufferList.notifyOpenBuffersMustBeProcessed(false);
        } else if (key.equals(PREF_ENCLOSE_NICK)) {
            Buffer.Line.ENCLOSE_NICK = prefs.getBoolean(key, PREF_ENCLOSE_NICK_D);
            BufferList.notifyOpenBuffersMustBeProcessed(false);
        } else if (key.equals(PREF_TEXT_SIZE)) {
            setTextSizeAndLetterWidth();
            BufferList.notifyOpenBuffersMustBeProcessed(true);
        } else if (key.equals(PREF_BUFFER_FONT)) {
            setTextSizeAndLetterWidth();
            BufferList.notifyOpenBuffersMustBeProcessed(true);
        } else if (key.equals(PREF_COLOR_SCHEME)) {
            loadColorScheme();
            BufferList.notifyOpenBuffersMustBeProcessed(true);
        }
    }

    private void loadColorScheme() {
        // Load color scheme
        // TODO use theme manager
        String colorScheme = prefs.getString(PREF_COLOR_SCHEME, PREF_COLOR_SCHEME_D);
        Properties p = new Properties();
        try {
            InputStream inputStream;
            if (colorScheme.startsWith("/")) {
                inputStream = new FileInputStream(colorScheme);
            } else {
                AssetManager assetManager = getApplicationContext().getAssets();
                inputStream = assetManager.open(colorScheme);
            }
            p.load(inputStream);
            ColorScheme cs = new ColorScheme(p);
            ColorScheme.setColorScheme(cs);
        } catch (IOException e) {
            Toast.makeText(this, "Error loading color scheme", Toast.LENGTH_SHORT).show();
            logger.debug("Failed to load color scheme properties file");
            logger.debug(e.toString());
        }
    }

    private void setTimestampFormat() {
        String timeformat = prefs.getString(PREF_TIMESTAMP_FORMAT, PREF_TIMESTAMP_FORMAT_D);
        Buffer.Line.DATEFORMAT = (timeformat.equals("")) ? null : new SimpleDateFormat(timeformat);
    }

    private void setAlignment() {
        String alignment = prefs.getString(PREF_PREFIX_ALIGN, PREF_PREFIX_ALIGN_D);
        if (alignment.equals("right")) Buffer.Line.ALIGN = Color.ALIGN_RIGHT;
        else if (alignment.equals("left")) Buffer.Line.ALIGN = Color.ALIGN_LEFT;
        else if (alignment.equals("timestamp")) Buffer.Line.ALIGN = Color.ALIGN_TIMESTAMP;
        else Buffer.Line.ALIGN = Color.ALIGN_NONE;
    }

    private void setTextSizeAndLetterWidth() {
        Buffer.Line.TEXT_SIZE = Float.parseFloat(prefs.getString(PREF_TEXT_SIZE, PREF_TEXT_SIZE_D));
        Paint p = new Paint();
        String fontPath = prefs.getString(PREF_BUFFER_FONT, PREF_BUFFER_FONT_D);
        if (!TextUtils.isEmpty(fontPath)) {
            Typeface tf;
            try {
                tf = Typeface.createFromFile(fontPath);
            } catch (RuntimeException r) {
                tf = Typeface.MONOSPACE;
            }
            p.setTypeface(tf);
        } else {
            p.setTypeface(Typeface.MONOSPACE);
        }
        p.setTextSize(Buffer.Line.TEXT_SIZE * getResources().getDisplayMetrics().scaledDensity);
        Buffer.Line.LETTER_WIDTH = (p.measureText("m"));
    }
}
