package com.ubergeek42.WeechatAndroid.service;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.widget.TextView;

import com.ubergeek42.WeechatAndroid.BuildConfig;
import com.ubergeek42.WeechatAndroid.R;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;

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
    final private static boolean DEBUG = BuildConfig.DEBUG;
    final private static boolean DEBUG_PREFS = false;
    final private static boolean DEBUG_SAVE_RESTORE = false;
    final private static boolean DEBUG_NOTIFICATIONS = false;

    public static final String PREFS_NAME = "kittens!";
    public static final String PREFS_SORT_BUFFERS = "sort_buffers";
    public static final String PREFS_SHOW_BUFFER_TITLES = "show_buffer_titles";
    public static final String PREFS_FILTER_NONHUMAN_BUFFERS = "filter_nonhuman_buffers";
    public static final String PREFS_OPTIMIZE_TRAFFIC = "optimize_traffic";
    public static final String PREFS_FILTER_LINES = "chatview_filters";
    public static final String PREFS_MAX_WIDTH = "prefix_max_width";
    public static final String PREFS_DIM_DOWN = "dim_down";
    public static final String PREFS_TIMESTAMP_FORMAT = "timestamp_format";
    public static final String PREFS_PREFIX_ALIGN = "prefix_align";
    public static final String PREFS_TEXT_SIZE = "text_size";

    /** super method sets 'prefs' */
    @Override
    public void onCreate() {
        super.onCreate();

        // buffer list preferences
        BufferList.SORT_BUFFERS = prefs.getBoolean(PREFS_SORT_BUFFERS, false);
        BufferList.SHOW_TITLE = prefs.getBoolean(PREFS_SHOW_BUFFER_TITLES, true);
        BufferList.FILTER_NONHUMAN_BUFFERS = prefs.getBoolean(PREFS_FILTER_NONHUMAN_BUFFERS, false);
        BufferList.OPTIMIZE_TRAFFIC = prefs.getBoolean(PREFS_OPTIMIZE_TRAFFIC, false);

        // buffer-wide preferences
        Buffer.FILTER_LINES = prefs.getBoolean(PREFS_FILTER_LINES, true);

        // buffer line-wide preferences
        Buffer.Line.MAX_WIDTH = Integer.parseInt(prefs.getString(PREFS_MAX_WIDTH, "7"));
        Buffer.Line.DIM_DOWN_NON_HUMAN_LINES = prefs.getBoolean(PREFS_DIM_DOWN, false);
        setTimestampFormat();
        setAlignment();
        setTextSizeAndLetterWidth();
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

    /** called upon authenticating. let's do our job!
     ** TODO although it might be wise not to create everything from scratch...  */
    @Override
    void startHandlingBoneEvents() {
        restoreStuff();
        BufferList.launch(this);

        // Subscribe to any future changes
        if (!BufferList.OPTIMIZE_TRAFFIC)
            connection.sendMsg("sync");
    }

    /** onDestroy will only be called when properly exiting the application
     ** maybe. anyways, user pressed Quit — erase open buffers */
    @Override
    public void onDestroy() {
        eraseStoredStuff();
        super.onDestroy();
    }

    /** save everything that is needed for successful restoration of the service */
    private void saveStuff() {
        if (DEBUG_SAVE_RESTORE) logger.debug("saveStuff()");
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        preferences.edit().putString("open_buffers", BufferList.getSyncedBuffersAsString()).commit();
    }

    /** restore everything */
    private void restoreStuff() {
        if (DEBUG_SAVE_RESTORE) logger.debug("restoreStuff()");
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        BufferList.setSyncedBuffersFromString(preferences.getString("open_buffers", ""));
    }

    /** erase stuff as we no longer need it */
    private void eraseStoredStuff() {
        if (DEBUG_SAVE_RESTORE) logger.debug("eraseStoredStuff()");
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        preferences.edit().remove("open_buffers").commit();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// prefs
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (DEBUG_PREFS) logger.warn("onSharedPreferenceChanged()");

        // buffer list preferences
        super.onSharedPreferenceChanged(sharedPreferences, key);
        if (key.equals(PREFS_SORT_BUFFERS)) {
            BufferList.SORT_BUFFERS = prefs.getBoolean(key, false);
        } else if (key.equals(PREFS_SHOW_BUFFER_TITLES)) {
            BufferList.SHOW_TITLE = prefs.getBoolean(key, false);
        } else if (key.equals(PREFS_FILTER_NONHUMAN_BUFFERS)) {
            BufferList.FILTER_NONHUMAN_BUFFERS = prefs.getBoolean(key, false);

            // traffic preference
        } else if (key.equals(PREFS_OPTIMIZE_TRAFFIC)) {
            BufferList.OPTIMIZE_TRAFFIC = prefs.getBoolean(key, false);

            // buffer-wide preferences
        } else if (key.equals(PREFS_FILTER_LINES)) {
            Buffer.FILTER_LINES = prefs.getBoolean(key, true);

            // chat lines-wide preferences
        } else if (key.equals(PREFS_MAX_WIDTH)) {
            Buffer.Line.MAX_WIDTH = Integer.parseInt(prefs.getString(key, "7"));
            BufferList.notifyOpenBuffersMustBeProcessed(false);
        } else if (key.equals(PREFS_DIM_DOWN)) {
            Buffer.Line.DIM_DOWN_NON_HUMAN_LINES = prefs.getBoolean(key, false);
            BufferList.notifyOpenBuffersMustBeProcessed(true);
        } else if (key.equals(PREFS_TIMESTAMP_FORMAT)) {
            setTimestampFormat();
            BufferList.notifyOpenBuffersMustBeProcessed(false);
        } else if (key.equals(PREFS_PREFIX_ALIGN)) {
            setAlignment();
            BufferList.notifyOpenBuffersMustBeProcessed(false);
        } else if (key.equals(PREFS_TEXT_SIZE)) {
            setTextSizeAndLetterWidth();
            BufferList.notifyOpenBuffersMustBeProcessed(true);
        }
    }

    private void setTimestampFormat() {
        String timeformat = prefs.getString(PREFS_TIMESTAMP_FORMAT, "HH:mm:ss");
        Buffer.Line.DATEFORMAT = (timeformat.equals("")) ? null : new SimpleDateFormat(timeformat);
    }

    private void setAlignment() {
        String alignment = prefs.getString(PREFS_PREFIX_ALIGN, "right");
        if (alignment.equals("right")) Buffer.Line.ALIGN = Buffer.Line.ALIGN_RIGHT;
        else if (alignment.equals("left")) Buffer.Line.ALIGN = Buffer.Line.ALIGN_LEFT;
        else Buffer.Line.ALIGN = Buffer.Line.ALIGN_NONE;
    }

    private void setTextSizeAndLetterWidth() {
        Buffer.Line.TEXT_SIZE = Float.parseFloat(prefs.getString(PREFS_TEXT_SIZE, "10"));
        LayoutInflater li = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        TextView textview = (TextView) li.inflate(R.layout.chatview_line, null).findViewById(R.id.chatline_message);
        textview.setTextSize(Buffer.Line.TEXT_SIZE);
        Buffer.Line.LETTER_WIDTH = (textview.getPaint().measureText("m"));
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// notifications
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public void changeNotification(boolean new_highlight, int new_hot_count, @Nullable Buffer buffer, @Nullable Buffer.Line line) {
        if (DEBUG_NOTIFICATIONS) logger.warn("changeNotification({}, {}, {}, {})", new Object[]{new_highlight, new_hot_count, buffer, line});
        hot_count = new_hot_count;
        if (new_highlight && buffer != null && line != null)
            displayHighlightNotification(buffer.full_name, line.getNotificationString());
        else
            displayDefaultNotification();
    }
}
