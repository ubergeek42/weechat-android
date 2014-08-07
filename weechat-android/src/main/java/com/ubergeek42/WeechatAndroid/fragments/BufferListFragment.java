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
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;

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
        BufferListEye, OnSharedPreferenceChangeListener {

    private static Logger logger = LoggerFactory.getLogger("BufferListFragment");
    final private static boolean DEBUG = BuildConfig.DEBUG && true;

    private static final String[] empty_list = { "Press Menu->Connect to get started" };

    private RelayServiceBinder relay;
    private BufferListAdapter adapter;
    private BufferList buffer_list;

    private EditText bufferlistFilter;
    private SharedPreferences prefs;

    /////////////////////////
    ///////////////////////// lifecycle
    /////////////////////////

    /** This makes sure that the container activity has implemented
     ** the callback interface. If not, it throws an exception. */
    @Override
    public void onAttach(Activity activity) {
        if (DEBUG) logger.warn("onAttach()");
        super.onAttach(activity);
        if (!(activity instanceof WeechatActivity))
            throw new ClassCastException(activity.toString() + " must be WeechatActivity");
    }

    /** Supposed to be called only once
     ** since we are setting setRetainInstance(true) */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (DEBUG) logger.warn("onCreate()");
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getBaseContext());
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (DEBUG) logger.warn("onCreateView()");
        View v = inflater.inflate(R.layout.bufferlist, container, false);
        bufferlistFilter = (EditText) v.findViewById(R.id.bufferlist_filter);
        bufferlistFilter.addTextChangedListener(filterTextWatcher);
        bufferlistFilter.setVisibility(prefs.getBoolean("show_buffer_filter", false) ? View.VISIBLE : View.GONE);
        return v;
    }

    @Override
    public void onDestroyView() {
        if (DEBUG) logger.warn("onDestroyView()");
        super.onDestroyView();
        bufferlistFilter.removeTextChangedListener(filterTextWatcher);
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
        if (DEBUG) logger.warn("onStart()");
        super.onStart();
        if (DEBUG) logger.warn("...calling bindService()");
        getActivity().bindService(new Intent(getActivity(), RelayService.class), service_connection,
                Context.BIND_AUTO_CREATE);
    }

    /** here we remove RelayConnectionHandler & buffer_manager's onchange handler
     ** it should be safe to call all unbinding functions */
    @Override
    public void onStop() {
        if (DEBUG) logger.warn("onStop()");
        super.onStop();
        if (buffer_list != null) {
            buffer_list.setBufferListEye(null);                                         // buffer change watcher (safe to call)
            buffer_list = null;
        }
        if (relay != null) {
            relay.removeRelayConnectionHandler(BufferListFragment.this);                // connect/disconnect watcher (safe to call)
            relay = null;
        }
        if (DEBUG) logger.warn("...calling unbindService()");
        getActivity().unbindService(service_connection);                                // TODO safe to call?
    }

    /////////////////////////
    /////////////////////////
    /////////////////////////

    ServiceConnection service_connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {           // TODO can this be called after Fragment.onStop()?
            if (DEBUG) logger.warn("onServiceConnected()");
            relay = (RelayServiceBinder) service;
            if (relay.isConnection(RelayService.CONNECTED))
                BufferListFragment.this.onConnect();
            else
                BufferListFragment.this.onDisconnect();
            relay.addRelayConnectionHandler(BufferListFragment.this);                   // connect/disconnect watcher
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (DEBUG) logger.error("onServiceDisconnected() <- should not happen!");
            relay = null;
            buffer_list = null;
        }
    };

    /////////////////////////
    ///////////////////////// method of android.support.v4.app.ListFragment
    /////////////////////////

    /** this is the mother method, it actually opens buffers */
    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        if (DEBUG) logger.warn("onListItemClick(..., ..., {}, ...)", position);
        Object obj = getListView().getItemAtPosition(position);
        if (obj instanceof Buffer) {
            Buffer buffer = (Buffer) obj;
            ((WeechatActivity) getActivity()).openBuffer(buffer.full_name, true);
        }
    }

    /////////////////////////
    ///////////////////////// methods of RelayConnectionHandler
    /////////////////////////

    @Override
    public void onConnecting() {}

    /** called on actual connection event and from other methods
     ** creates and updates the buffer list */
    @Override
    public void onConnect() {
        if (DEBUG) logger.warn("onConnect(), buffer_list = {}", relay.getBufferList());
        buffer_list = relay.getBufferList();
        adapter = new BufferListAdapter(getActivity(), buffer_list);
        buffer_list.setBufferListEye(this);                                       // buffer change watcher
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setListAdapter(adapter);
            }
        });
        onBuffersChanged();
    }

    @Override
    public void onAuthenticated() {}

    /** this is called when the list of buffers has been finalised
     ** technically buffers should be listening to this but let's do it here for now... */
    @Override
    public void onBuffersListed() {
        logger.warn("onBuffersListed()");
    }

    /** called on actual disconnect even and from other methods
     ** displays empty list */
    @Override
    public void onDisconnect() {
        if (DEBUG) logger.warn("onDisconnect()");
        // Create and update the buffer list when we connect to the service
        if (getActivity() != null) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setListAdapter(new ArrayAdapter<String>(getActivity(), R.layout.tips_list_item,
                            empty_list));
                }
            });
        }
    }

    /** do nothing: activity is handling errors */
    @Override
    public void onError(String err, Object extraInfo) {}

    /////////////////////////
    ///////////////////////// BuffersChangedObserver
    /////////////////////////

    /** TODO */
    @Override
    public void onBuffersChanged() {
        if (DEBUG) logger.warn("onBuffersChanged()");
        adapter.onBuffersChanged();
    }

    @Override
    public void onBuffersSlightlyChanged() {
        if (DEBUG) logger.warn("onBuffersSlightlyChanged()");
        adapter.onBuffersSlightlyChanged();
    }

    /////////////////////////
    ///////////////////////// other
    /////////////////////////

    @Override
         public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (DEBUG) logger.warn("onSharedPreferenceChanged()");
        if (key.equals("show_buffer_filter"))
            bufferlistFilter.setVisibility(prefs.getBoolean("show_buffer_filter", false) ? View.VISIBLE : View.GONE);
    }

    /** TextWatcher object used for filtering the buffer list */
    private TextWatcher filterTextWatcher = new TextWatcher() {
        @Override
        public void afterTextChanged(Editable a) {}

        @Override
        public void beforeTextChanged(CharSequence arg0, int a, int b, int c) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (false && DEBUG) logger.warn("onTextChanged({}, ...)", s);
            if (adapter != null) {
                BufferList.FILTER = (s.length() == 0) ? null : s.toString();
                adapter.onBuffersChanged();
            }
        }
    };


}
