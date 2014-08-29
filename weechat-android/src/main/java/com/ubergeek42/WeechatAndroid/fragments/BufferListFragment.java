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
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockListFragment;
import com.ubergeek42.WeechatAndroid.BufferListAdapter;
import com.ubergeek42.WeechatAndroid.BuildConfig;
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
    final private static boolean DEBUG = BuildConfig.DEBUG;
    final private static boolean DEBUG_LIFECYCLE = false;
    final private static boolean DEBUG_MESSAGES = false;
    final private static boolean DEBUG_CONNECTION = false;
    final private static boolean DEBUG_PREFERENCES = false;


    private RelayServiceBinder relay;
    private BufferListAdapter adapter;

    private RelativeLayout ui_filter_bar;
    private TextView ui_empty;
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
        if (!(activity instanceof WeechatActivity))
            throw new ClassCastException(activity.toString() + " must be WeechatActivity");
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
        ui_filter_bar = (RelativeLayout) v.findViewById(R.id.filter_bar);
        ui_empty = (TextView) v.findViewById(android.R.id.empty);
        ui_filter = (EditText) v.findViewById(R.id.bufferlist_filter);
        ui_filter.addTextChangedListener(filterTextWatcher);
        ui_filter_clear = (ImageButton) v.findViewById(R.id.bufferlist_filter_clear);
        ui_filter_bar.setVisibility(prefs.getBoolean("show_buffer_filter", false) ? View.VISIBLE : View.GONE);
        ImageButton ui_filter_clear = (ImageButton) v.findViewById(R.id.bufferlist_filter_clear);
        ui_filter_clear.setOnClickListener(this);
        return v;
    }

    @Override
    public void onDestroyView() {
        if (DEBUG_LIFECYCLE) logger.warn("onDestroyView()");
        super.onDestroyView();
        ui_filter.removeTextChangedListener(filterTextWatcher);
    }

    /** relay is ALWAYS null on start
     ** binding to relay service results in:
     **   service_connection.onServiceConnected() which:
     **     binds to RelayConnectionHandler, and,
     **     if connected,
     **       calls on Connect()
     **         which results in buffer_manager.setOnChangedHandler()
     **     else
     **       calls onDisconnect(), which sets the “please connect” message */
    @Override
    public void onStart() {
        if (DEBUG_LIFECYCLE) logger.warn("onStart()");
        super.onStart();
        if (DEBUG_LIFECYCLE) logger.warn("...calling bindService()");
        getActivity().bindService(new Intent(getActivity(), RelayService.class), service_connection,
                Context.BIND_AUTO_CREATE);
    }

    /** here we remove RelayConnectionHandler & buffer_manager's onchange handler
     ** it should be safe to call all unbinding functions */
    @Override
    public void onStop() {
        if (DEBUG_LIFECYCLE) logger.warn("onStop()");
        super.onStop();
        BufferList.setBufferListEye(null);                                              // buffer change watcher (safe to call)
        if (relay != null) {
            relay.removeRelayConnectionHandler(BufferListFragment.this);                // connect/disconnect watcher (safe to call)
            relay = null;
        }
        if (DEBUG_LIFECYCLE) logger.warn("...calling unbindService()");
        getActivity().unbindService(service_connection);                                // TODO safe to call?
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// service connection
    ////////////////////////////////////////////////////////////////////////////////////////////////

    ServiceConnection service_connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {           // TODO can this be called after Fragment.onStop()?
            if (DEBUG_LIFECYCLE) logger.warn("onServiceConnected()");
            relay = (RelayServiceBinder) service;
            if (relay.isConnection(RelayService.BUFFERS_LISTED))
                BufferListFragment.this.onBuffersListed();
            else if (relay.isConnection(RelayService.CONNECTING))
                BufferListFragment.this.onConnecting();
            else if (relay.isConnection(RelayService.DISCONNECTED))
                BufferListFragment.this.onDisconnect();
            relay.addRelayConnectionHandler(BufferListFragment.this);                   // connect/disconnect watcher
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (DEBUG) logger.error("onServiceDisconnected() <- should not happen!");
            relay = null;
        }
    };

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// on click
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /** this is the mother method, it actually opens buffers */
    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        if (DEBUG) logger.warn("onListItemClick(..., ..., {}, ...)", position);
        Object obj = getListView().getItemAtPosition(position);
        if (obj instanceof Buffer)
            ((WeechatActivity) getActivity()).openBuffer(((Buffer) obj).full_name, true);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// RelayConnectionHandler
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override public void onConnecting() {
        if (DEBUG_CONNECTION) logger.warn("onConnecting()");
        setEmptyText("Connecting 🏃", false);
    }

    @Override public void onConnect() {}

    @Override public void onAuthenticated() {
        if (DEBUG_CONNECTION) logger.warn("onAuthenticated()");
        setEmptyText("Connected! 😎", false);
    }

    /** this is called when the list of buffers has been finalised */
    @Override
    public void onBuffersListed() {
        if (DEBUG_CONNECTION) logger.warn("onBuffersListed()");
        adapter = new BufferListAdapter(getActivity());
        BufferList.setBufferListEye(this);
        onHotCountChanged();
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setListAdapter(adapter);
            }
        });
        setFilter(ui_filter.getText());
        onBuffersChanged();
        setEmptyText("Whatcha lookin' at? 😾", false);
    }

    /** called on actual disconnect even and from other methods
     ** displays empty list */
    @Override
    public void onDisconnect() {
        if (DEBUG_CONNECTION) logger.warn("onDisconnect()");
        setEmptyText("Disconnected 😨", true);
    }

    @Override public void onError(String err, Object extraInfo) {}

    public void setEmptyText(final CharSequence text, final boolean remove_adapter) {
        getActivity().runOnUiThread(new Runnable() {
            @Override public void run() {
                ui_empty.setText(text);
                if (remove_adapter) setListAdapter(null);
            }
        });
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
        ((WeechatActivity) getActivity()).updateHotCount(BufferList.hot_count);
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
        BufferList.setFilter(s);
        getActivity().runOnUiThread(new Runnable() {
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
