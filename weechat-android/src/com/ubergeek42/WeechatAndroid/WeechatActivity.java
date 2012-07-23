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
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.ubergeek42.WeechatAndroid.fragments.BufferFragment;
import com.ubergeek42.WeechatAndroid.fragments.BufferListFragment;
import com.ubergeek42.WeechatAndroid.service.RelayService;
import com.ubergeek42.WeechatAndroid.service.RelayServiceBinder;
import com.ubergeek42.weechat.Buffer;
import com.ubergeek42.weechat.HotlistItem;
import com.ubergeek42.weechat.relay.RelayConnectionHandler;
import com.ubergeek42.weechat.relay.messagehandler.BufferManager;

public class WeechatActivity extends SherlockFragmentActivity implements BufferListFragment.OnBufferSelectedListener, RelayConnectionHandler {
	private static final String TAG = "WeechatActivity";
	private boolean mBound = false;
	private RelayServiceBinder rsb;
	private String currentBuffer;

	// We have 2 fragments(depending on layout); the bufferlist, and an active buffer
	private BufferListFragment bfl;

	private boolean tabletView = false;

    private SocketToggleConnection taskToggleConnection;
    private HotlistListAdapter hotlistListAdapter;
    private Menu actionBarMenu;

    /** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

		// Start the background service(if necessary)
	    startService(new Intent(this, RelayService.class));

	    // Load the layout
	    setContentView(R.layout.bufferlist_fragment);

	    if (findViewById(R.id.fragment_container) == null) {
	    	tabletView = true;
	    }
	    
        // TODO Read preferences from background, its IO, 31ms strict mode!
	    PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
	    
	    if (savedInstanceState != null) {
	    	return;
	    }
	    
        // Check whether the activity is using the layout version with
        // the fragment_container FrameLayout. If so, we must add the first fragment
        if (!tabletView) {
        	// Create a new fragment, and pass any extras to it
        	bfl = new BufferListFragment();
            bfl.setArguments(getIntent().getExtras());
            
            // Replace anything in the fragment container with our new fragment
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft = ft.replace(R.id.fragment_container, bfl);
            ft.commit();
        } else {
        	if (bfl == null) {
				 bfl = (BufferListFragment)getSupportFragmentManager().findFragmentById(R.id.bufferlist_fragment);
        	}
        	setTitle(getString(R.string.app_version));
        }
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
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		
	}

	ServiceConnection mConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			rsb = (RelayServiceBinder) service;
			rsb.addRelayConnectionHandler(WeechatActivity.this);

			mBound = true;
			// Check if the service is already connected to the weechat relay, and if so load it up
			if (rsb.isConnected()) {
				WeechatActivity.this.onConnect();
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			rsb.removeRelayConnectionHandler(WeechatActivity.this);
			mBound = false;
			rsb = null;
		}
	};

	@Override
	public void onConnect() {
		if (rsb != null && rsb.isConnected()) {
            // Create and update the hotlist
            hotlistListAdapter = new HotlistListAdapter(WeechatActivity.this, rsb);
		}

        // Make sure we update action bar menu after a connection change.
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateMenuContext(actionBarMenu);
            }
        });

	}
	@Override
	public void onDisconnect() {
        // Make sure we update action bar menu after a connection change.
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateMenuContext(actionBarMenu);
            }
        });
	}
	
	@Override
	public void onError(String arg0) {
		Log.d("WeechatActivity", "onError:" + arg0);
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
                        HotlistItem hotlistItem = hotlistListAdapter.getItem(position);
                        String name = hotlistItem.getFullName();
                        // TODO get the proper position in the bufferlistadapter, does it matter?
                        onBufferSelected(0, name);
                    }
                });
                builder.create().show();
                break;
            }
            case R.id.menu_nicklist: {
            	if(currentBuffer!=null) {
		            NickListAdapter nicklistAdapter = new NickListAdapter(WeechatActivity.this, rsb,rsb.getBufferByName(currentBuffer).getNicks() );

		            AlertDialog.Builder builder = new AlertDialog.Builder(this);
		            builder.setTitle("Nicklist");
		            builder.setAdapter(nicklistAdapter, new DialogInterface.OnClickListener() {
		                @Override
		                public void onClick(DialogInterface dialogInterface, int position) {
		                	//TODO define something to happen here
		                }
		            });
		            builder.create().show();
            	}
                break;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Replacement method for onPrepareOptionsMenu
     * due to rsb might be null on the event of clicking the menu button.
     *
     * Hence our activity stores the menu references in onCreateOptionsMenu
     * and we can update menu items underway from events like onConnect.
     * @param menu actionBarMenu to update context on
     */
    public void updateMenuContext (Menu menu) {
    	// Swap the text from connect to disconnect depending on connection status
        MenuItem connectionStatus = menu.findItem(R.id.menu_connection_state);
        if (rsb != null && rsb.isConnected())
            connectionStatus.setTitle(R.string.disconnect);
        else
            connectionStatus.setTitle(R.string.connect);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getSupportMenuInflater();
        menuInflater.inflate(R.menu.menu_actionbar, menu);

        updateMenuContext(menu);

        // Can safely hold on to this according to docs
        // http://developer.android.com/reference/android/app/Activity.html#onCreateOptionsMenu(android.view.Menu)
        actionBarMenu = menu;
        return super.onCreateOptionsMenu(menu);
    }

    public void onBufferSelected(int position, String buffer) {
    	// The user selected the buffer from the BufferlistFragment
		Log.d(TAG, "onBufferSelected() position:" + position + " buffer:" + buffer );

		// Do nothing if they selected the same buffer
		if (buffer == currentBuffer) return;
		
		//  Remove buffer from hotlist
		rsb.getHotlistManager().removeHotlistItem(buffer);

        // Capture the buffer fragment from the activity layout
        BufferFragment bufferFrag = (BufferFragment) getSupportFragmentManager().findFragmentById(R.id.buffer_fragment);

        if (tabletView && bufferFrag !=null) {
            // Call a method in the BufferFragment to update its content
            bufferFrag.updateBufferView(position, buffer);
        } else {
            // Create fragment and pass the correct arguments
            BufferFragment newFragment = new BufferFragment();
            Bundle args = new Bundle();
            args.putInt(BufferFragment.ARG_POSITION, position);
            args.putString("buffer", buffer);
            newFragment.setArguments(args);

            // Replace the current fragment with the buffer they selected
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.fragment_container, newFragment);
            transaction.addToBackStack(null);
            transaction.commit();
        }
        
		// Update the current buffer to reflect the change we made
		currentBuffer = buffer;
    }

    /**
     * Used to toggle the connection 
     */
    private class SocketToggleConnection extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Void... voids) {
            if (rsb.isConnected()) {
                rsb.shutdown();
            } else {
                return rsb.connect();
            }
            return true;
        }
        @Override
        protected void onPostExecute(Boolean success) {
            supportInvalidateOptionsMenu();
        }
    }
}
