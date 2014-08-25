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
    final private static boolean DEBUG = BuildConfig.DEBUG && true;

    public static final String PREFS_NAME = "kittens!";

    public BufferList buffer_list;

    /** super method sets 'prefs' */
    @Override
    public void onCreate() {
        super.onCreate();

        // buffer list preferences
        BufferList.SORT_BUFFERS = prefs.getBoolean("sort_buffers", false);
        BufferList.SHOW_TITLE = prefs.getBoolean("show_buffer_titles", true);
        BufferList.FILTER_NONHUMAN_BUFFERS = prefs.getBoolean("filter_nonhuman_buffers", false);
        BufferList.OPTIMIZE_TRAFFIC = prefs.getBoolean("optimize_traffic", false);

        // buffer-wide preferences
        Buffer.FILTER_LINES = prefs.getBoolean("chatview_filters", true);

        // buffer line-wide preferences
        Buffer.Line.MAX_WIDTH = Integer.parseInt(prefs.getString("prefix_max_width", "7"));
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
        buffer_list = new BufferList(this);

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
        if (DEBUG) logger.debug("saveStuff()");
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        preferences.edit().putString("open_buffers", BufferList.getSyncedBuffersAsString()).commit();
    }

    /** restore everything */
    private void restoreStuff() {
        if (DEBUG) logger.debug("restoreStuff()");
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        BufferList.setSyncedBuffersFromString(preferences.getString("open_buffers", ""));
    }

    /** erase stuff as we no longer need it */
    private void eraseStoredStuff() {
        if (DEBUG) logger.debug("eraseStoredStuff()");
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        preferences.edit().remove("open_buffers").commit();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// prefs
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (DEBUG) logger.warn("onSharedPreferenceChanged()");

        // buffer list preferences
        super.onSharedPreferenceChanged(sharedPreferences, key);
        if (key.equals("sort_buffers")) {
            BufferList.SORT_BUFFERS = prefs.getBoolean("sort_buffers", false);
        } else if (key.equals("show_buffer_titles")) {
            BufferList.SHOW_TITLE = prefs.getBoolean("show_buffer_titles", false);
        } else if (key.equals("filter_nonhuman_buffers")) {
            BufferList.FILTER_NONHUMAN_BUFFERS = prefs.getBoolean("filter_nonhuman_buffers", false);

        // traffic preference
        } else if (key.equals("optimize_traffic")) {
            BufferList.OPTIMIZE_TRAFFIC = prefs.getBoolean("optimize_traffic", false);

        // buffer-wide preferences
        } else if (key.equals("chatview_filters")) {
            Buffer.FILTER_LINES = prefs.getBoolean("chatview_filters", true);

        // chat lines-wide preferences
        } else if (key.equals("prefix_max_width")) {
            Buffer.Line.MAX_WIDTH = Integer.parseInt(prefs.getString(key, "7"));
            buffer_list.notifyOpenBuffersMustBeProcessed(false);
        } else if (key.equals("timestamp_format")) {
            setTimestampFormat();
            buffer_list.notifyOpenBuffersMustBeProcessed(false);
        } else if (key.equals("prefix_align")) {
            setAlignment();
            buffer_list.notifyOpenBuffersMustBeProcessed(false);
        } else if (key.equals("text_size")) {
            setTextSizeAndLetterWidth();
            buffer_list.notifyOpenBuffersMustBeProcessed(true);
        }
    }

    private void setTimestampFormat() {
        String timeformat = prefs.getString("timestamp_format", "HH:mm:ss");
        Buffer.Line.DATEFORMAT = (timeformat.equals("")) ? null : new SimpleDateFormat(timeformat);
    }

    private void setAlignment() {
        String alignment = prefs.getString("prefix_align", "right");
        if (alignment.equals("right")) Buffer.Line.ALIGN = Buffer.Line.ALIGN_RIGHT;
        else if (alignment.equals("left")) Buffer.Line.ALIGN = Buffer.Line.ALIGN_LEFT;
        else Buffer.Line.ALIGN = Buffer.Line.ALIGN_NONE;
    }

    private void setTextSizeAndLetterWidth() {
        Buffer.Line.TEXT_SIZE = Float.parseFloat(prefs.getString("text_size", "10"));
        LayoutInflater li = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        TextView textview = (TextView) li.inflate(R.layout.chatview_line, null).findViewById(R.id.chatline_message);
        textview.setTextSize(Buffer.Line.TEXT_SIZE);
        Buffer.Line.LETTER_WIDTH = (textview.getPaint().measureText("m"));
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// notifications
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public void changeNotification(boolean new_highlight, int new_hot_count, @Nullable Buffer buffer, @Nullable Buffer.Line line) {
        if (DEBUG) logger.warn("changeNotification({}, {}, {}, {})", new Object[]{new_highlight, new_hot_count, buffer, line});
        hot_count = new_hot_count;
        if (new_highlight && buffer != null && line != null)
            displayHighlightNotification(buffer.full_name, line.getNotificationString());
        else
            displayDefaultNotification();
    }
}
