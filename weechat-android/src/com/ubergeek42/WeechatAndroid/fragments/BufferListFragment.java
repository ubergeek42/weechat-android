package com.ubergeek42.WeechatAndroid.fragments;

import java.util.ArrayList;

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
import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.service.RelayService;
import com.ubergeek42.WeechatAndroid.service.RelayServiceBinder;
import com.ubergeek42.weechat.Buffer;
import com.ubergeek42.weechat.relay.RelayConnectionHandler;
import com.ubergeek42.weechat.relay.messagehandler.BufferManager;
import com.ubergeek42.weechat.relay.messagehandler.BufferManagerObserver;
import com.ubergeek42.weechat.relay.protocol.RelayObject;

public class BufferListFragment extends SherlockListFragment implements RelayConnectionHandler,
        BufferManagerObserver, OnSharedPreferenceChangeListener {
    private static Logger logger = LoggerFactory.getLogger(BufferListFragment.class);
    private static final String[] message = { "Press Menu->Connect to get started" };

    private boolean mBound = false;
    private RelayServiceBinder rsb;

    private BufferListAdapter m_adapter;

    OnBufferSelectedListener mCallback;
    private BufferManager bufferManager;

    // Used for filtering the list of buffers displayed
    private EditText bufferlistFilter;
    
    private SharedPreferences prefs;
    private boolean enableBufferSorting;
    private boolean hideServerBuffers;

    // Are we attached to an activity?
    private boolean attached;
    

    // The container Activity must implement this interface so the frag can deliver messages
    public interface OnBufferSelectedListener {
        /**
         * Called by BufferlistFragment when a list item is selected
         * 
         * @param b
         */
        public void onBufferSelected(String fullBufferName);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        logger.debug("BufferListFragment onAttach called");

        // This makes sure that the container activity has implemented
        // the callback interface. If not, it throws an exception.
        try {
            mCallback = (OnBufferSelectedListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnBufferSelectedListener");
        }
    }
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View v = inflater.inflate(R.layout.bufferlist, null);
            bufferlistFilter = (EditText) v.findViewById(R.id.bufferlist_filter);
            bufferlistFilter.addTextChangedListener(filterTextWatcher);
            if (prefs.getBoolean("show_buffer_filter", true)) {
                bufferlistFilter.setVisibility(View.VISIBLE);
            } else {
                bufferlistFilter.setVisibility(View.GONE);
            }
            return v;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);

        setListAdapter(new ArrayAdapter<String>(getActivity(), R.layout.tips_list_item, message));
        prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getBaseContext());
        prefs.registerOnSharedPreferenceChangeListener(this);
        enableBufferSorting = prefs.getBoolean("sort_buffers", true);
        hideServerBuffers = prefs.getBoolean("hide_server_buffers", true);
        
        
        // TODO ondestroy: bufferlistFilter.removeTextChangedListener(filterTextWatcher);
    }

    @Override
    public void onStart() {
        super.onStart();

        // Bind to the Relay Service
        if (mBound == false) {
            getActivity().bindService(new Intent(getActivity(), RelayService.class), mConnection,
                    Context.BIND_AUTO_CREATE);
        }

        attached = true;
    }

    @Override
    public void onStop() {
        super.onStop();
        attached = false;
        if (mBound) {
            rsb.removeRelayConnectionHandler(BufferListFragment.this);
            getActivity().unbindService(mConnection);
            mBound = false;
        }
    }

    ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            rsb = (RelayServiceBinder) service;
            rsb.addRelayConnectionHandler(BufferListFragment.this);

            mBound = true;
            if (rsb.isConnected()) {
                BufferListFragment.this.onConnect();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
            rsb = null;
        }
    };

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
    	Object obj = getListView().getItemAtPosition(position);
    	if (obj instanceof Buffer) {
	        // Get the buffer they clicked
	        Buffer b = (Buffer) obj;

	        // Tell our parent to load the buffer
	        mCallback.onBufferSelected(b.getFullName());
    	}
    }

    @Override
    public void onConnect() {
        if (rsb != null && rsb.isConnected()) {
            // Create and update the buffer list when we connect to the service
            m_adapter = new BufferListAdapter(getActivity());
            bufferManager = rsb.getBufferManager();
            m_adapter.setBuffers(bufferManager.getBuffers());
            bufferManager.onChanged(BufferListFragment.this);

            m_adapter.enableSorting(prefs.getBoolean("sort_buffers", true));
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setListAdapter(m_adapter);
                }
            });

            onBuffersChanged();
        }
    }

    @Override
    public void onDisconnect() {
        // Create and update the buffer list when we connect to the service
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setListAdapter(new ArrayAdapter<String>(getActivity(), R.layout.tips_list_item,
                        message));
            }
        });
    }
    @Override
    public void onError(String err, Object extraInfo) {
        // We don't do anything with the error message(the activity/service does though)
    }

    @Override
    public void onBuffersChanged() {
        // Need to make sure we are attached to an activity, otherwise getActivity can be null
        if (!attached) {
            return;
        }

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ArrayList<Buffer> buffers;

                buffers = bufferManager.getBuffers();

                // Remove server buffers(if unwanted)
                if (hideServerBuffers) {
                    ArrayList<Buffer> newBuffers = new ArrayList<Buffer>();
                    for (Buffer b : buffers) {
                        RelayObject relayobj = b.getLocalVar("type");
                        if (relayobj != null && relayobj.asString().equals("server")) {
                            continue;
                        }
                        newBuffers.add(b);
                    }
                    buffers = newBuffers;
                }

                m_adapter.setBuffers(buffers);
            }
        });
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals("sort_buffers")) {
            m_adapter.enableSorting(prefs.getBoolean("sort_buffers", true));
        } else if (key.equals("hide_server_buffers")) {
            hideServerBuffers = prefs.getBoolean("hide_server_buffers", true);
            onBuffersChanged();
        } else if(key.equals("show_buffer_filter") && bufferlistFilter != null) {
            if (prefs.getBoolean("show_buffer_filter", true)) {
                bufferlistFilter.setVisibility(View.VISIBLE);
            } else {
                bufferlistFilter.setVisibility(View.GONE);
            }
        }
    }
    
    // TextWatcher object for filtering the buffer list
    private TextWatcher filterTextWatcher = new TextWatcher() {
        @Override
        public void afterTextChanged(Editable a) { }
        @Override
        public void beforeTextChanged(CharSequence arg0, int a, int b, int c) {}
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (m_adapter!=null) {
                m_adapter.filterBuffers(s.toString());
            }
        }
    };
}