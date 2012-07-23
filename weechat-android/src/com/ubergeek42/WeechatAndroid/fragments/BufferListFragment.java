package com.ubergeek42.WeechatAndroid.fragments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

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

public class BufferListFragment extends SherlockListFragment implements RelayConnectionHandler, BufferManagerObserver, OnSharedPreferenceChangeListener  {
	private static final String[] message = {"Press Menu->Connect to get started"};

	private boolean mBound = false;
	private RelayServiceBinder rsb;
	
	private BufferListAdapter m_adapter;

    OnBufferSelectedListener mCallback;
	private BufferManager bufferManager;

	private SharedPreferences prefs;
	private boolean enableBufferSorting;
	private int currentPosition = -1;

    

    // The container Activity must implement this interface so the frag can deliver messages
    public interface OnBufferSelectedListener {
        /** Called by BufferlistFragment when a list item is selected 
         * @param b */
        public void onBufferSelected(int position, String fullBufferName);
    }
    
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

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
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setRetainInstance(true);
        
		setListAdapter(new ArrayAdapter<String>(getActivity(), R.layout.tips_list_item, message));
		prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getBaseContext());
	    prefs.registerOnSharedPreferenceChangeListener(this);
	    enableBufferSorting = prefs.getBoolean("sort_buffers", true);


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
			rsb.removeRelayConnectionHandler(BufferListFragment.this);
			mBound = false;
			rsb = null;
			Log.d("DISCONNECT", "ONSERVICEDISCONNECTED called");
		}
	};

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {    	
    	// Get the buffer they clicked
		Buffer b = (Buffer) getListView().getItemAtPosition(position);

		// Tell our parent to load the buffer
        mCallback.onBufferSelected(position, b.getFullName());
        
        // Set the item as checked to be highlighted when in two-pane layout
        getListView().setItemChecked(position, true);
        currentPosition = position;
    }

	@Override
	public void onConnect() {
		Log.d("BufferListFragment","onConnect called");
		if (rsb != null && rsb.isConnected()) {
			// Create and update the buffer list when we connect to the service
			m_adapter = new BufferListAdapter((WeechatActivity) getActivity());
			bufferManager = rsb.getBufferManager();
			bufferManager.onChanged(BufferListFragment.this);


			getActivity().runOnUiThread(new Runnable() {
				@Override
				public void run() {
					setListAdapter(m_adapter);
				}
			});
			m_adapter.notifyDataSetChanged();

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
		getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				ArrayList<Buffer> buffers;
				int position = currentPosition;
				Buffer b = null;
				
				if(position>=0) {
					try{
						b = m_adapter.getItem(position);
					}catch(ArrayIndexOutOfBoundsException e)
					{
						Log.d("BufferListFragment", "OutOfBounds:"+position);
					}
				}
				
				buffers = bufferManager.getBuffers();
				// Sort buffers based on unread count
				if (enableBufferSorting) {
					Collections.sort(buffers, bufferComparator);
				}
				m_adapter.buffers = buffers;
				m_adapter.notifyDataSetChanged();
				
				if(b!=null) {
					for(int i=0;i<buffers.size();i++) {
						if(b.getFullName().equals(buffers.get(i).getFullName())) {
							currentPosition = i;
							break;
						}
					}
					// Set the item as checked to be highlighted when in two-pane layout
			        getListView().setItemChecked(currentPosition, true);

				}	
			}
		});
	}
	private final Comparator<Buffer> bufferComparator = new Comparator<Buffer>() {
		@Override
		public int compare(Buffer b1, Buffer b2) {
        	int b1Highlights = b1.getHighlights();
        	int b2Highlights = b2.getHighlights();
        	if(b2Highlights > 0 || b1Highlights > 0) {
        		return b2Highlights - b1Highlights;
        	}
            return b2.getUnread() - b1.getUnread();
        }
	};

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals("sort_buffers")) {
			enableBufferSorting = prefs.getBoolean("sort_buffers", true);
			onBuffersChanged();
		}
	}

}