package com.ubergeek42.WeechatAndroid.fragments;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
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

public class BufferListFragment extends SherlockListFragment implements RelayConnectionHandler {
	private static final String[] message = {"Press Menu->Connect to get started"};

	private boolean mBound = false;
	private RelayServiceBinder rsb;
	
	private BufferListAdapter m_adapter;

    OnBufferSelectedListener mCallback;

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
    }

	@Override
	public void onConnect() {
		Log.d("BufferListFragment","onConnect called");
		if (rsb != null && rsb.isConnected()) {
			// Create and update the buffer list when we connect to the service
			m_adapter = new BufferListAdapter((WeechatActivity) getActivity(), rsb);
			getActivity().runOnUiThread(new Runnable() {
				@Override
				public void run() {
					setListAdapter(m_adapter);
					m_adapter.onBuffersChanged();
				}
			});
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
}