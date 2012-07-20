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

public class WeechatActivity extends SherlockActivity implements OnItemClickListener, RelayConnectionHandler {

    private static final String TAG = "WeeChatActivity";

	private boolean mBound = false;
	private RelayServiceBinder rsb;
	private ListView bufferlist;
	private BufferListAdapter m_adapter;
    private HotlistListAdapter hotlistListAdapter;
    private static final boolean DEVELOPER_MODE = true; // todo: maven to configure this variable
    private SocketToggleConnection taskToggleConnection;

    /** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
        if (DEVELOPER_MODE) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build());
        }

	    super.onCreate(savedInstanceState);
	    
	    setContentView(R.layout.bufferlist);
	    setTitle(getString(R.string.app_version));
	    PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
	    
	    bufferlist = (ListView) this.findViewById(R.id.bufferlist_list);
		bufferlist.setOnItemClickListener(this);
		// See also code in the onDisconnect handler(its a copy/paste)
		String[] message = {"Press Menu->Connect to get started"};
		bufferlist.setAdapter(new ArrayAdapter<String>(WeechatActivity.this, R.layout.tips_list_item, message));
        
		// Start the service(if necessary)
	    startService(new Intent(this, RelayService.class));
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
			rsb = (RelayServiceBinder) service;
			rsb.setRelayConnectionHandler(WeechatActivity.this);

			mBound = true;
			
			
			// Check if the service is already connected to the weechat relay, and if so load it up
			if (rsb.isConnected()) {
				WeechatActivity.this.onConnect();
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			mBound = false;
			rsb = null;
		}
	};

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		if (bufferlist.getAdapter() instanceof ArrayAdapter<?>) {
			return;
		}
		
		// Handles the user clicking on a buffer
		Buffer b = (Buffer) bufferlist.getItemAtPosition(position);
		
		// Start new activity for the given buffer
		Intent i = new Intent(this, WeechatChatviewActivity.class);
		i.putExtra("buffer", b.getFullName());
		startActivity(i);
	}

	@Override
	public void onConnect() {
		if (rsb != null && rsb.isConnected()) {
			// Create and update the buffer list when we connect to the service
			m_adapter = new BufferListAdapter(WeechatActivity.this, rsb);
            // Create and update the hotlist
            hotlistListAdapter = new HotlistListAdapter(WeechatActivity.this, rsb);

            this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    bufferlist.setAdapter(m_adapter);
                    m_adapter.onBuffersChanged();

                    /*hotlistManager = rsb.getHotlistManager();
                    hotlistManager.onChanged(WeechatActivity.this);*/



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
		m_adapter = null;
		hotlistListAdapter = null;
		this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				
				String[] message = {"Press Menu->Connect to get started"};
				bufferlist.setAdapter(new ArrayAdapter<String>(WeechatActivity.this, R.layout.tips_list_item, message));
			}
		});
	}

	@Override
	public void onError(String arg0) {
		Log.d("WeechatActivity", "onError:" + arg0);
		
	}

    protected class SocketToggleConnection extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            if (rsb.isConnected())
                rsb.shutdown();
            else
                rsb.connect();
            return null;
        }

        @Override
        protected void onPostExecute(Void ignore) {
            supportInvalidateOptionsMenu();
        }
    }
}
