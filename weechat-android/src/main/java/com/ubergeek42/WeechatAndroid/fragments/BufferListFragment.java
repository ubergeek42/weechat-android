package com.ubergeek42.WeechatAndroid.fragments;

import java.util.ArrayList;
import java.util.Iterator;

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
import com.ubergeek42.WeechatAndroid.service.Buffers;
import com.ubergeek42.WeechatAndroid.service.BuffersEye;
import com.ubergeek42.WeechatAndroid.service.RelayService;
import com.ubergeek42.WeechatAndroid.service.RelayServiceBinder;
import com.ubergeek42.weechat.relay.RelayConnectionHandler;
import com.ubergeek42.weechat.relay.protocol.RelayObject;

public class BufferListFragment extends SherlockListFragment implements RelayConnectionHandler,
        BuffersEye, OnSharedPreferenceChangeListener {

    private static Logger logger = LoggerFactory.getLogger("list");
    final private static boolean DEBUG = BuildConfig.DEBUG && true;

    private static final String[] empty_list = { "Press Menu->Connect to get started" };

    private RelayServiceBinder relay;
    private BufferListAdapter adapter;
    private Buffers buffers;

    private EditText bufferlistFilter;
    private SharedPreferences prefs;
    private boolean hide_server_buffers;    // a preference

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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (DEBUG) logger.warn("onCreate()");
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getBaseContext());
        prefs.registerOnSharedPreferenceChangeListener(this);
        hide_server_buffers = prefs.getBoolean("hide_server_buffers", true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (DEBUG) logger.warn("onCreateView()");
        View v = inflater.inflate(R.layout.bufferlist, null);
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
        getActivity().bindService(new Intent(getActivity(), RelayService.class), service_connection,
                Context.BIND_AUTO_CREATE);
    }

    /** here we remove RelayConnectionHandler & buffer_manager's onchange handler
     ** it should be safe to call all unbinding functions */
    @Override
    public void onStop() {
        if (DEBUG) logger.warn("onStop()");
        super.onStop();
        if (buffers != null) {
            buffers.setBuffersEye(null);                                     // buffer change watcher (safe to call)
            buffers = null;
        }
        if (relay != null) {
            relay.removeRelayConnectionHandler(BufferListFragment.this);                // connect/disconnect watcher (safe to call)
            relay = null;
        }
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
            buffers = null;
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
            ((WeechatActivity) getActivity()).openBuffer(buffer.pointer);
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
        if (DEBUG) logger.warn("onConnect()");
        adapter = new BufferListAdapter(getActivity());
        buffers = relay.getBuffers();
        buffers.setBuffersEye(this);                                       // buffer change watcher
        adapter.enableSorting(prefs.getBoolean("sort_buffers", true));
        adapter.filterBuffers(bufferlistFilter.getText().toString());  // resume sorting
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

    /** the only method and the only implementer of BuffersChangedObserver */
    @Override
    public void onBuffersChanged() {
        if (false && DEBUG) logger.warn("onBuffersChanged()");
        final ArrayList<Buffer> bufs = buffers.getBuffersCopy();
//        if (hide_server_buffers) {
//            Iterator<Buffer> iter = bufs.iterator();
//            while (iter.hasNext()) {
//                RelayObject relayobj = iter.next().getLocalVar("type");
//                if (relayobj != null && relayobj.asString().equals("server"))
//                    iter.remove();
//            }
//        }
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (buffers != null) adapter.setBuffers(buffers.getBuffersCopy());               // are we still online?
            }
        });
    }

    /////////////////////////
    ///////////////////////// other
    /////////////////////////

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (DEBUG) logger.warn("onSharedPreferenceChanged()");
        if (key.equals("sort_buffers")) {
            if (adapter != null)
                adapter.enableSorting(prefs.getBoolean("sort_buffers", true));
        } else if (key.equals("hide_server_buffers")) {
            hide_server_buffers = prefs.getBoolean("hide_server_buffers", true);
        } else if(key.equals("show_buffer_filter") && bufferlistFilter != null) {
            if (prefs.getBoolean("show_buffer_filter", false)) {
                bufferlistFilter.setVisibility(View.VISIBLE);
            } else {
                bufferlistFilter.setVisibility(View.GONE);
            }
        }
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
            if (adapter != null)
                adapter.filterBuffers(s.toString());
        }
    };


}
