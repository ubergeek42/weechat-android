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
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.actionbarsherlock.app.SherlockListFragment;
import com.ubergeek42.WeechatAndroid.BufferListAdapter;
import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.WeechatActivity;
import com.ubergeek42.WeechatAndroid.service.RelayService;
import com.ubergeek42.WeechatAndroid.service.RelayServiceBinder;
import com.ubergeek42.weechat.Buffer;
import com.ubergeek42.weechat.relay.RelayConnectionHandler;
import com.ubergeek42.weechat.relay.messagehandler.BufferManager;
import com.ubergeek42.weechat.relay.messagehandler.BufferManagerObserver;
import com.ubergeek42.weechat.relay.protocol.RelayObject;

public class BufferListFragment extends SherlockListFragment implements RelayConnectionHandler, BufferManagerObserver, OnSharedPreferenceChangeListener  {
	private static Logger logger = LoggerFactory.getLogger(BufferListFragment.class);
	private static final String[] message = {"Press Menu->Connect to get started"};

	private boolean mBound = false;
	private RelayServiceBinder rsb;
	
	private BufferListAdapter m_adapter;

    OnBufferSelectedListener mCallback;
	private BufferManager bufferManager;

	private SharedPreferences prefs;
	private boolean enableBufferSorting;
	private boolean hideServerBuffers;
	private int currentPosition = -1;

	// Are we attached to an activity?
	private boolean attached;

    

    // The container Activity must implement this interface so the frag can deliver messages
    public interface OnBufferSelectedListener {
        /** Called by BufferlistFragment when a list item is selected 
         * @param b */
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
        attached = true;
        
        WeechatActivity parent = (WeechatActivity)activity;
		parent.setCurrentFragment(this);
    }
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
    	super.onActivityCreated(savedInstanceState);
    	
    	logger.debug("BufferListFragment onActivityCreated called");
    	
    	WeechatActivity parent = (WeechatActivity)getActivity();
		parent.setCurrentFragment(this);
    }
    
    @Override
    public void onDetach() {
    	super.onDetach();
    	attached = false;
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setRetainInstance(true);
        
		setListAdapter(new ArrayAdapter<String>(getActivity(), R.layout.tips_list_item, message));
		prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getBaseContext());
	    prefs.registerOnSharedPreferenceChangeListener(this);
	    enableBufferSorting = prefs.getBoolean("sort_buffers", true);
	    hideServerBuffers = prefs.getBoolean("hide_server_buffers", false);
    }
    
    @Override
    public void onStart() {
        super.onStart();
        
        getActivity().setTitle(getString(R.string.app_version));
        
        // When in two-pane layout, set the listview to highlight the selected list item
        // (We do this during onStart because at the point the listview is available.)
        if (getFragmentManager().findFragmentById(R.id.buffer_fragment) != null) {
            getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        }
        
        // Bind to the Relay Service
        if (mBound == false)
        	getActivity().bindService(new Intent(getActivity(), RelayService.class), mConnection, Context.BIND_AUTO_CREATE);
    }
    
    @Override
	public void onStop() {
		super.onStop();
		if (mBound) {
			rsb.removeRelayConnectionHandler(BufferListFragment.this);
			getActivity().unbindService(mConnection);
			mBound = false;
		}
	}
    
	ServiceConnection mConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			Log.d("BufferListFragment","Bufferlistfragment onserviceconnected");
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
    	// Get the buffer they clicked
		Buffer b = (Buffer) getListView().getItemAtPosition(position);

		// Tell our parent to load the buffer
        mCallback.onBufferSelected(b.getFullName());
        
        // Set the item as checked to be highlighted when in two-pane layout
        getListView().setItemChecked(position, true);
        currentPosition = position;
    }

	@Override
	public void onConnect() {
		Log.d("BufferListFragment","onConnect called");
		if (rsb != null && rsb.isConnected()) {
			// Create and update the buffer list when we connect to the service
			m_adapter = new BufferListAdapter(getActivity());
			bufferManager = rsb.getBufferManager();
			m_adapter.setBuffers(bufferManager.getBuffers());
			bufferManager.onChanged(BufferListFragment.this);


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
				setListAdapter(new ArrayAdapter<String>(getActivity(), R.layout.tips_list_item, message));
			}
		});
	}

	@Override
	public void onError(String err) {
		
	}
	@Override
	public void onBuffersChanged() {
		// Need to make sure we are attached to an activity, otherwise getActivity can be null
		if (!attached) return;
		
		getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				ArrayList<Buffer> buffers;
				int position = currentPosition;
				Buffer lastBuffer = null;
				
				if(position>=0) {
					try{
						lastBuffer = m_adapter.getItem(position);
					}catch(ArrayIndexOutOfBoundsException e) {
						Log.d("BufferListFragment", "AOutOfBounds:"+position);
					}catch(IndexOutOfBoundsException e) {
						Log.d("BufferListFragment", "IOutOfBounds:"+position);
					}
				}
				
				buffers = bufferManager.getBuffers();
				
				
				// Remove server buffers(if unwanted)
				if (hideServerBuffers) {
					ArrayList<Buffer> newBuffers = new ArrayList<Buffer>();
					for (Buffer b: buffers) {
						RelayObject relayobj = b.getLocalVar("type");
						if (relayobj != null && relayobj.asString().equals("server"))
							continue;
						newBuffers.add(b);
					}
					buffers = newBuffers;
				}
				
				m_adapter.setBuffers(buffers);
				// Sort buffers based on unread count
				if (enableBufferSorting) {
					m_adapter.sortBuffers();
				}

				// If we had a buffer selected, make sure it stays highlighted when in two-pane layout(as things may have shuffled around)
				if (lastBuffer != null) {
					currentPosition = m_adapter.findBufferPosition(lastBuffer);
					if (currentPosition>=0)
						getListView().setItemChecked(currentPosition, true);
					// TODO: crash, content view not yet created(maybe this is being called too early?)
				}	
			}
		});
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals("sort_buffers")) {
			enableBufferSorting = prefs.getBoolean("sort_buffers", true);
			onBuffersChanged();
		} else if(key.equals("hide_server_buffers")) {
			hideServerBuffers = prefs.getBoolean("hide_server_buffers", false);
			onBuffersChanged();
		}
	}

}