/*******************************************************************************
 * Copyright 2012 Keith Johnson
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.ubergeek42.WeechatAndroid;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;

import com.ubergeek42.weechat.Buffer;
import com.ubergeek42.weechat.relay.RelayConnectionHandler;

public class WeechatActivity extends FragmentActivity implements BufferListFragment.OnBufferSelectedListener, OnItemClickListener, RelayConnectionHandler {
	private static final String TAG = "WeechatActivity";
	private boolean mBound = false;
	private RelayServiceBinder rsb;
	//private ListView bufferlist;
	//private BufferListFragment bufferlistFragment;
	//private BufferListAdapter m_adapter;
	
	/** Called when the activity is first created. */
	@SuppressLint("NewApi") @Override
	public void onCreate(Bundle savedInstanceState) {
		Log.d(TAG, "onCreate()");
	    super.onCreate(savedInstanceState);
	    StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build(); 	 	
	    StrictMode.setThreadPolicy(policy); 

	    //setContentView(R.layout.bufferlist);
	    setContentView(R.layout.bufferlist_fragment);
	    
		// Start the service(if necessary)
	    startService(new Intent(this, RelayService.class));

	    setTitle(getString(R.string.app_version));
	    PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
	    
        // Check whether the activity is using the layout version with
        // the fragment_container FrameLayout. If so, we must add the first fragment
        if (findViewById(R.id.fragment_container) != null) {

            // However, if we're being restored from a previous state,
            // then we don't need to do anything and should return or else
            // we could end up with overlapping fragments.
            // FIXME
        	if (savedInstanceState != null) {
                return;
            }

            BufferListFragment bufferlistFragment = new BufferListFragment();

            // In case this activity was started with special instructions from an Intent,
            // pass the Intent's extras to the fragment as arguments
            bufferlistFragment.setArguments(getIntent().getExtras());

            // Add the fragment to the 'fragment_container' FrameLayout
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, bufferlistFragment).commit();
        }
		//Log.d(TAG, "onCreate() bflf" + bufferlistFragment);
	}
	
	@Override
	protected void onStart() {
		super.onStart();

		// Bind to the Relay Service
	    bindService(new Intent(this, RelayService.class), mConnection, Context.BIND_AUTO_CREATE);
	}

	@Override
	protected void onStop() {
		super.onStop();
		if (mBound) {
			unbindService(mConnection);
			mBound = false;
		}
	}

	@Override
	// Build the options menu
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.clear();
		if (getRsb() != null && getRsb().isConnected())
			menu.add("Disconnect");
		else
			menu.add("Connect");
		menu.add("Preferences");
		menu.add("About");
		menu.add("Quit");
		return super.onPrepareOptionsMenu(menu);
	}
	@Override
	// Handle the options when the user presses the Menu key
	public boolean onOptionsItemSelected(MenuItem item) {
		String s = (String) item.getTitle();
		if (s.equals("Quit")) {
			if (getRsb() != null)getRsb().shutdown();
			unbindService(mConnection);
			mBound = false;
			stopService(new Intent(this, RelayService.class));
			finish();
		} else if (s.equals("Disconnect")) {
			if (getRsb() != null)getRsb().shutdown();
		} else if (s.equals("Connect")) {
			if (getRsb() != null)getRsb().connect();
		} else if (s.equals("About")) {
			Intent i = new Intent(this, WeechatAboutActivity.class);
			startActivity(i);
		} else if (s.equals("Preferences")) {
			Intent i = new Intent(this, WeechatPreferencesActivity.class);
			startActivity(i);
		}
		return true;
	}

	
	ServiceConnection mConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			setRsb((RelayServiceBinder) service);
			getRsb().setRelayConnectionHandler(WeechatActivity.this);

			mBound = true;
			
			// Check if the service is already connected to the weechat relay, and if so load it up
			if (getRsb().isConnected()) {
				WeechatActivity.this.onConnect();
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			mBound = false;
			setRsb(null);
		}
	};

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		Log.d(TAG, "onItemClick()");
	}

	@Override
	public void onConnect() {
		Log.d(TAG, "onConnect()");
        final BufferListFragment bfl = (BufferListFragment)
                getSupportFragmentManager().findFragmentById(R.id.bufferlist_fragment);
		if (rsb != null && rsb.isConnected()) {
			// Create and update the buffer list when we connect to the service
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					BufferListAdapter m_adapter = new BufferListAdapter(WeechatActivity.this, getRsb());
   					Log.d(TAG, "onConnect m_adapter:" + m_adapter);
   					Log.d(TAG, "onConnect bfl:" + bfl);
					//bfl.getListAdapter().onBuffersChanged();
   					// In porttrait mode FIXME this should probably live somewhere else
   				    if(bfl==null) {
   				    	BufferListFragment bflnew = new BufferListFragment();
						bflnew.setListAdapter(m_adapter);
   				    }else {
						bfl.setListAdapter(m_adapter);
   				    }
					m_adapter.onBuffersChanged();
				}
			});
		}
	}

	@Override
	public void onDisconnect() {
		// Create and update the buffer list when we connect to the service
		this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				String[] message = {"Press Menu->Connect to get started"};
				BufferListFragment bfl = (BufferListFragment)
		                getSupportFragmentManager().findFragmentById(R.id.bufferlist_fragment);
				if (bfl!=null)
					bfl.setListAdapter(new ArrayAdapter<String>(WeechatActivity.this, R.layout.tips_list_item, message));
			}
		});
	}

	@Override
	public void onError(String arg0) {
		
	}
    public RelayServiceBinder getRServiceB() {
    	return getRsb();
    }

    public void onBufferSelected(int position, Buffer buffer) {        
    	// The user selected the buffer from the BufferlistFragment
		Log.d(TAG, "onBufferSelected() position:" + position + " buffer:" + buffer );
		
        // Capture the buffer fragment from the activity layout
        BufferFragment bufferFrag = (BufferFragment)
                getSupportFragmentManager().findFragmentById(R.id.buffer_fragment);


        if (bufferFrag != null) {
            // If buffer frag is available, we're in two-pane layout...

            // Call a method in the BufferFragment to update its content
            bufferFrag.updateBufferView(position, buffer.getFullName());
        } else {
            // If the frag is not available, we're in the one-pane layout and must swap frags...

            // Create fragment and give it an argument for the selected article
            BufferFragment newFragment = new BufferFragment();
            Bundle args = new Bundle();
            args.putInt(BufferFragment.ARG_POSITION, position);
            args.putString("buffer", buffer.getFullName());
            newFragment.setArguments(args);
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

            // Replace whatever is in the fragment_container view with this fragment,
            // and add the transaction to the back stack so the user can navigate back
            transaction.replace(R.id.fragment_container, newFragment);
            transaction.addToBackStack(null);

            // Commit the transaction
            transaction.commit();
        }
    }

    /**
     * getter for the service binder, used by the BufferFragment
     * @return rsb
     */
	public RelayServiceBinder getRsb() {
		return rsb;
	}
	public void setRsb(RelayServiceBinder rsb) {
		this.rsb = rsb;
	}


}
