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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.ubergeek42.WeechatAndroid.fragments.BufferFragment;
import com.ubergeek42.WeechatAndroid.fragments.BufferListFragment;
import com.ubergeek42.WeechatAndroid.service.RelayService;
import com.ubergeek42.WeechatAndroid.service.RelayServiceBinder;
import com.ubergeek42.weechat.HotlistItem;
import com.ubergeek42.weechat.relay.RelayConnectionHandler;

public class WeechatActivity extends SherlockFragmentActivity implements BufferListFragment.OnBufferSelectedListener, RelayConnectionHandler {
	private static Logger logger = LoggerFactory.getLogger(WeechatActivity.class);
	private boolean mBound = false;
	private RelayServiceBinder rsb;

	// We have 2 fragments: the bufferlist, and an active buffer
	private BufferListFragment bufferListFrag;
	private BufferFragment currentBufferFrag;
	
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

	    if (findViewById(R.id.fragment_container_tablet) != null) {
	    	tabletView = true;
	    } else {
	    	tabletView = false;
	    }
	    
        // TODO Read preferences from background, its IO, 31ms strict mode!
	    PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
	    
	    // Get the bufferlistfragment from the view
    	if (bufferListFrag == null) {
			 bufferListFrag = (BufferListFragment)getSupportFragmentManager().findFragmentById(R.id.bufferlist_fragment);
    	}
	    
    	if (currentBufferFrag == null) {
    		BufferFragment newFragment = new BufferFragment();
    		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.replace(R.id.buffer_fragment, newFragment);
            ft.commit();
    		currentBufferFrag = newFragment;
    	}
    	
	    if (savedInstanceState != null) {
	    	return;
	    }
	    
    	if (!tabletView) {
    		// Hide the buffer fragment
    		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
    		ft.hide(currentBufferFrag);
    		ft.commit();
    	}
    	
    	setTitle(getString(R.string.app_version));
    }
	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		setIntent(intent);
		logger.debug("onNewIntent called");
		handleIntent();
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
			rsb.removeRelayConnectionHandler(WeechatActivity.this);
			unbindService(mConnection);
			mBound = false;
		}
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
			
			handleIntent();
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
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
                builder.setTitle(R.string.hotlist);
                builder.setAdapter(hotlistListAdapter, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int position) {
                        HotlistItem hotlistItem = hotlistListAdapter.getItem(position);
                        String name = hotlistItem.getFullName();
                        onBufferSelected(name);
                    }
                });
                builder.create().show();
                break;
            }
            case R.id.menu_nicklist: {
            	// No nicklist if they aren't looking at a buffer
            	if (currentBufferFrag.isHidden()) break;
            	
        		String[] nicks = currentBufferFrag.getNicklist();
        		if (nicks == null) break;
        		
	            NickListAdapter nicklistAdapter = new NickListAdapter(WeechatActivity.this, nicks);
	            	
	            AlertDialog.Builder builder = new AlertDialog.Builder(this);
	            builder.setTitle(getString(R.string.nicklist_menu) + " (" + nicks.length + ")");
	            builder.setAdapter(nicklistAdapter, new DialogInterface.OnClickListener() {
	                @Override
	                public void onClick(DialogInterface dialogInterface, int position) {
	                	//TODO define something to happen here
	                }
	            });
	            builder.create().show();
                break;
            }
            case R.id.menu_bufferlist: {
                // Replace anything in the fragment container with our new fragment
                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                if (bufferListFrag.isHidden()) {
                    ft.show(bufferListFrag);
                    if (!tabletView) ft.hide(currentBufferFrag);
                } else {
                    ft.hide(bufferListFrag);
                    if (!tabletView) ft.show(currentBufferFrag);
                }
                
                ft.commit();
            	
            }
            break;
        }
        return super.onOptionsItemSelected(item);
    }

    // when we get an intent, do something with it
    private void handleIntent() {
    	// Load a buffer if necessary
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
        	// Load the given buffer
        	onBufferSelected(extras.getString("buffer"));
        } else {
        	// Load the bufferlist
        }
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
    	if (menu==null) return;
    	
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
    
    // Called by whatever fragment is loaded, to set the currentview
    public void setCurrentFragment(Fragment frag) {
    	currentBufferFrag = (BufferFragment) frag;
    }

    public void onBufferSelected(String buffer) {
    	// The user selected the buffer from the BufferlistFragment
		logger.debug("onBufferSelected() buffer:" + buffer );

		if (buffer == currentBufferFrag.getBufferName()) {
			// Show the buffer if it isn't visible
			if (currentBufferFrag.isHidden()) {
				FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
				ft.show(currentBufferFrag);
				ft.hide(bufferListFrag);
				ft.commit();
			}
			return;
		}
		
		// Does the buffer actually exist?(If not, return)
		if (rsb.getBufferByName(buffer)==null)
			return;
		
		// Create fragment for the buffer and setup the arguments
        BufferFragment newFragment = new BufferFragment();
        Bundle args = new Bundle();
        args.putString("buffer", buffer);
        newFragment.setArguments(args);
		
        // Replace the current fragment with the buffer they selected
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.buffer_fragment, newFragment);
        //ft.addToBackStack(null);
        if (tabletView == false) {
        	// hide the bufferlist, show the buffer
        	ft.show(currentBufferFrag);
        	ft.hide(bufferListFrag);
        }
        ft.commit();
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
