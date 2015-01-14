package com.ubergeek42.WeechatAndroid.fragments;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.RelativeLayout;

import com.actionbarsherlock.app.SherlockListFragment;
import com.ubergeek42.WeechatAndroid.adapters.BufferListAdapter;
import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.WeechatActivity;
import com.ubergeek42.WeechatAndroid.service.Buffer;
import com.ubergeek42.WeechatAndroid.service.BufferList;
import com.ubergeek42.WeechatAndroid.service.BufferListEye;
import com.ubergeek42.WeechatAndroid.service.RelayService;
import com.ubergeek42.WeechatAndroid.service.RelayServiceBinder;
import com.ubergeek42.weechat.relay.RelayConnectionHandler;

public class BufferListFragment extends SherlockListFragment implements RelayConnectionHandler,
        BufferListEye, OnSharedPreferenceChangeListener, View.OnClickListener {

    private static Logger logger = LoggerFactory.getLogger("BufferListFragment");
    final private static boolean DEBUG_LIFECYCLE = false;
    final private static boolean DEBUG_MESSAGES = false;
    final private static boolean DEBUG_CONNECTION = false;
    final private static boolean DEBUG_PREFERENCES = false;
    final private static boolean DEBUG_CLICK = false;

    private WeechatActivity activity;
    private RelayServiceBinder relay;
    private BufferListAdapter adapter;

    private RelativeLayout ui_filter_bar;
    private EditText ui_filter;
    private ImageButton ui_filter_clear;
    private SharedPreferences prefs;

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// lifecycle
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /** This makes sure that the container activity has implemented
     ** the callback interface. If not, it throws an exception. */
    @Override
    public void onAttach(Activity activity) {
        if (DEBUG_LIFECYCLE) logger.warn("onAttach()");
        super.onAttach(activity);
        this.activity = (WeechatActivity) activity;
    }

    /** Supposed to be called only once
     ** since we are setting setRetainInstance(true) */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (DEBUG_LIFECYCLE) logger.warn("onCreate()");
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getBaseContext());
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (DEBUG_LIFECYCLE) logger.warn("onCreateView()");
        View v = inflater.inflate(R.layout.bufferlist, container, false);
        ui_filter = (EditText) v.findViewById(R.id.bufferlist_filter);
        ui_filter.addTextChangedListener(filterTextWatcher);
        ui_filter_clear = (ImageButton) v.findViewById(R.id.bufferlist_filter_clear);
        ui_filter_clear.setOnClickListener(this);
        ui_filter_bar = (RelativeLayout) v.findViewById(R.id.filter_bar);
        ui_filter_bar.setVisibility(prefs.getBoolean("show_buffer_filter", false) ? View.VISIBLE : View.GONE);
        return v;
    }

    @Override
    public void onDestroyView() {
        if (DEBUG_LIFECYCLE) logger.warn("onDestroyView()");
        super.onDestroyView();
        ui_filter.removeTextChangedListener(filterTextWatcher);
    }

    @Override
    public void onStart() {
        if (DEBUG_LIFECYCLE) logger.warn("onStart()");
        super.onStart();
        activity.bindService(new Intent(getActivity(), RelayService.class), service_connection,
                Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        if (DEBUG_LIFECYCLE) logger.warn("onStop()");
        super.onStop();
        detachFromBufferList();
        relay = null;
        activity.unbindService(service_connection);                                     // TODO safe to call?
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// service connection
    ////////////////////////////////////////////////////////////////////////////////////////////////

    ServiceConnection service_connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {           // TODO can this be called after Fragment.onStop()?
            if (DEBUG_LIFECYCLE) logger.warn("onServiceConnected()");
            relay = (RelayServiceBinder) service;
            attachToBufferList();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (DEBUG_LIFECYCLE) logger.error("onServiceDisconnected() <- should not happen!");
            relay = null;
        }
    };

    //////////////////////////////////////////////////////////////////////////////////////////////// RelayConnectionHandler

    @Override public void onConnecting() {}
    @Override public void onConnect() {}
    @Override public void onAuthenticated() {}
    @Override public void onAuthenticationFailed() {}
    @Override public void onDisconnect() {}
    @Override public void onError(String err, Object extraInfo) {}

    /** this is called when the list of buffers has been finalised */
    @Override
    public void onBuffersListed() {
        if (DEBUG_CONNECTION) logger.warn("onBuffersListed()");
        attachToBufferList();
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// the juice

    private void attachToBufferList() {
        adapter = new BufferListAdapter(activity);
        BufferList.setBufferListEye(this);
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                setListAdapter(adapter);
            }
        });
        setFilter(ui_filter.getText());
        onBuffersChanged();
        relay.addRelayConnectionHandler(BufferListFragment.this);                       // connect/disconnect watcher
    }

    private void detachFromBufferList() {
        if (relay != null)
            relay.removeRelayConnectionHandler(BufferListFragment.this);                // connect/disconnect watcher (safe to call)
        BufferList.setBufferListEye(null);                                              // buffer change watcher (safe to call)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// on click
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /** this is the mother method, it actually opens buffers */
    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        if (DEBUG_CLICK) logger.warn("onListItemClick(..., ..., {}, ...)", position);
        Object obj = getListView().getItemAtPosition(position);
        if (obj instanceof Buffer)
            activity.openBuffer(((Buffer) obj).full_name);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// BufferListEye
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override public void onBuffersChanged() {
        if (DEBUG_MESSAGES) logger.warn("onBuffersChanged()");
        adapter.onBuffersChanged();
    }

    @Override public void onHotCountChanged() {
        if (DEBUG_MESSAGES) logger.warn("onHotCountChanged()");
        activity.updateHotCount(BufferList.getHotCount());
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// other
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (DEBUG_PREFERENCES) logger.warn("onSharedPreferenceChanged()");
        if (key.equals("show_buffer_filter"))
            ui_filter_bar.setVisibility(prefs.getBoolean("show_buffer_filter", false) ? View.VISIBLE : View.GONE);
    }

    /** TextWatcher object used for filtering the buffer list */
    private TextWatcher filterTextWatcher = new TextWatcher() {
        @Override public void afterTextChanged(Editable a) {}
        @Override public void beforeTextChanged(CharSequence arg0, int a, int b, int c) {}

        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (DEBUG_PREFERENCES) logger.warn("onTextChanged({}, ...)", s);
            if (adapter != null) {
                setFilter(s);
                adapter.onBuffersChanged();
            }
        }
    };

    private void setFilter(final CharSequence s) {
        BufferList.setFilter(s.toString());
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                ui_filter_clear.setVisibility((s.length() == 0) ? View.INVISIBLE : View.VISIBLE);
            }
        });
    }

    /** the only button we've got: clear text in the filter */
    @Override
    public void onClick(View v) {
        ui_filter.setText(null);
    }
}
