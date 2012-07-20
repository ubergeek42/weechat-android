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

import android.app.AlertDialog;
import android.content.*;
import android.os.*;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.ubergeek42.weechat.Buffer;
import com.ubergeek42.weechat.HotlistItem;
import com.ubergeek42.weechat.relay.RelayConnectionHandler;

public class WeechatActivity extends SherlockFragmentActivity implements BufferListFragment.OnBufferSelectedListener, OnItemClickListener, RelayConnectionHandler {
	private static final String TAG = "WeechatActivity";
	private boolean mBound = false;
	private RelayServiceBinder rsb;
	/*private ListView bufferlist;
	private BufferListAdapter m_adapter;
    private HotlistListAdapter hotlistListAdapter;*/
    private static final boolean DEVELOPER_MODE = true; // todo: maven to configure this variable
    private SocketToggleConnection taskToggleConnection;

    /** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if (DEVELOPER_MODE) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build());
        }

	    


	    //setContentView(R.layout.bufferlist);
	    setContentView(R.layout.bufferlist_fragment);
	    
		// Start the service(if necessary)
	    startService(new Intent(this, RelayService.class));

	    setTitle(getString(R.string.app_version));
        // todo Read preferences from background, its IO, 31ms strictmode!
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

        if (taskToggleConnection != null && taskToggleConnection.getStatus()!=AsyncTask.Status.FINISHED) {
            taskToggleConnection.cancel(true);
            taskToggleConnection = null;
        }

		if (mBound) {
			unbindService(mConnection);
			mBound = false;
		}
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

                    /*// Create and update the hotlist
                    hotlistListAdapter = new HotlistListAdapter(WeechatActivity.this, rsb);*/

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
    // Handle the options when the user presses the Menu key
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connection_state: {
                if (rsb != null) {
                    taskToggleConnection = new SocketToggleConnection();
                    taskToggleConnection.execute();
                }
                break;
            }
            case R.id.menu_preferences: {
                Intent i = new Intent(this, WeechatPreferencesActivity.class);
                startActivity(i);
                break;
            }
            case R.id.menu_about: {
                Intent i = new Intent(this, WeechatAboutActivity.class);
                startActivity(i);
                break;
            }
            case R.id.menu_quit: {
                if (rsb != null)rsb.shutdown();
                unbindService(mConnection);
                mBound = false;
                stopService(new Intent(this, RelayService.class));
                finish();
                break;
            }
            case R.id.menu_hotlist: {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Hotlist");
                builder.setAdapter(hotlistListAdapter, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int position) {
                        HotlistItem hostlistItem = hotlistListAdapter.getItem(position);

                        Intent intent = new Intent(WeechatActivity.this, WeechatChatviewActivity.class);
                        intent.putExtra("buffer", hostlistItem.getFullName());
                        startActivity(intent);
                    }
                });
                builder.create().show();
                break;
            }
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPrepareOptionsMenu (Menu menu) {
        Log.d(TAG, "onPrepareOptionsMenu()");


        MenuItem connectionStatus = menu.findItem(R.id.wee_submenu);
        if (connectionStatus != null) {
            connectionStatus = connectionStatus.getSubMenu().findItem(R.id.menu_connection_state);
            if (rsb != null & rsb.isConnected())
                connectionStatus.setTitle(R.string.disconnect);
            else
                connectionStatus.setTitle(R.string.connect);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getSupportMenuInflater();
        menuInflater.inflate(R.menu.menu_actionbar, menu);
        return super.onCreateOptionsMenu(menu);
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
		Log.d("WeechatActivity", "onError:" + arg0);
		
	}

    protected class SocketToggleConnection extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Void... voids) {
            if (rsb.isConnected())
                rsb.shutdown();
            else
                return rsb.connect();

            return false;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            supportInvalidateOptionsMenu();
        }
    }
}
