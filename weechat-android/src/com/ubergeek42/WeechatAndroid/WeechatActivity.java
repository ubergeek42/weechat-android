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
import com.ubergeek42.weechat.Buffer;
import com.ubergeek42.weechat.HotlistItem;
import com.ubergeek42.weechat.relay.RelayConnectionHandler;
import com.ubergeek42.weechat.relay.messagehandler.BufferManager;

public class WeechatActivity extends SherlockFragmentActivity implements BufferListFragment.OnBufferSelectedListener, OnItemClickListener, RelayConnectionHandler {
	private static final String TAG = "WeechatActivity";
	private boolean mBound = false;
	private RelayServiceBinder rsb;
	private String currentBuffer;
	/*private ListView bufferlist;
	*/
	private BufferListAdapter m_adapter;
	BufferListFragment bfl;

    private static final boolean DEVELOPER_MODE = true; // todo: maven to configure this variable
    private SocketToggleConnection taskToggleConnection;
    private HotlistListAdapter hotlistListAdapter;

    /** Called when the activity is first created. */
	@SuppressLint("NewApi")
	@Override
	public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		Log.d(TAG, "onCreate()");

        if (DEVELOPER_MODE) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build());
        }
		// Start the service(if necessary)
	    startService(new Intent(this, RelayService.class));

	    //setContentView(R.layout.bufferlist);
	    setContentView(R.layout.bufferlist_fragment);
	    
	    setTitle(getString(R.string.app_version));
        // Check whether the activity is using the layout version with
        // the fragment_container FrameLayout. If so, we must add the first fragment
        if (findViewById(R.id.fragment_container) != null) {
    		Log.d(TAG, "onCreate() MARKER 1");

            // However, if we're being restored from a previous state,
            // then we don't need to do anything and should return or else
            // we could end up with overlapping fragments.
            // FIXME
        	//if (savedInstanceState != null) {
        	//	Log.d(TAG, "onCreate() MARKER 2");
            //    return;
            //}

            bfl = new BufferListFragment();

            // In case this activity was started with special instructions from an Intent,
            // pass the Intent's extras to the fragment as arguments
            bfl.setArguments(getIntent().getExtras());

            // Add the fragment to the 'fragment_container' FrameLayout
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, bfl).commit();
        }
        else {
    		Log.d(TAG, "onCreate() MARKER 3");

        	if (bfl == null) {
        		Log.d(TAG, "onCreate() MARKER 4");
				 bfl = (BufferListFragment)
		                getSupportFragmentManager().findFragmentById(R.id.bufferlist_fragment);
        	}

        }
		String[] message = {"Press Menu->Connect to get started"};
		bfl.setListAdapter(new ArrayAdapter<String>(WeechatActivity.this, R.layout.tips_list_item, message));

        // TODO Read preferences from background, its IO, 31ms strictmode!
	    PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
	    

	}
	
	@Override
	protected void onStart() {
		super.onStart();
		Log.d(TAG, "onStart()");

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
		if (rsb != null && rsb.isConnected()) {
            // Create and update the buffer list when we connect to the service
			m_adapter = new BufferListAdapter(WeechatActivity.this, getRsb());
            // Create and update the hotlist
            hotlistListAdapter = new HotlistListAdapter(WeechatActivity.this, getRsb());
				Log.d(TAG, "onConnect bfl1:" + bfl);

			runOnUiThread(new Runnable() {
				@Override
				public void run() {
   					Log.d(TAG, "onConnect bfl2:" + bfl);
					//bfl.getListAdapter().onBuffersChanged();
   					// In porttrait mode FIXME this should probably live somewhere else
   				    	//BufferListFragment bflnew = new BufferListFragment();
						//bflnew.setListAdapter(m_adapter);

					bfl.setListAdapter(m_adapter);
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
		            ;
		            builder.create().show();
            	}
/*
                // Capture the buffer fragment from the activity layout
                //NickListFragment nlFrag = (NickListFragment) getSupportFragmentManager().findFragmentById(R.id.nicklist_fragment);
                
                // Create fragment and give it an argument for the selected article
                BufferFragment newFragment = new BufferFragment();
                Bundle args = new Bundle();
                //args.putInt(BufferFragment.ARG_POSITION, position);
                //args.putString("buffer", buffer);
                newFragment.setArguments(args);
                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

                // Replace whatever is in the fragment_container view with this fragment,
                // and add the transaction to the back stack so the user can navigate back
                //transaction.replace(R.id.fragment_container, newFragment);
                //transaction.addToBackStack(null);
                
               
                //ft.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right);
                NickListFragment nlFrag = new NickListFragment();
                nlFrag.setListAdapter(nicklistAdapter);

                //ft.replace(R.id.details_fragment_container, newFragment, "detailFragment");
                ft.add(nlFrag, "nicklistfragment");

                // Start the animated transition.
                ft.commit();
                */
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
            if (rsb != null && rsb.isConnected())
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

    public void onBufferSelected(int position, String buffer) {
    	// The user selected the buffer from the BufferlistFragment
		Log.d(TAG, "onBufferSelected() position:" + position + " buffer:" + buffer );
		
		currentBuffer = buffer;
		
		//  Remove buffer from hotlist
		rsb.getHotlistManager().removeHotlistItem(buffer);

        // Capture the buffer fragment from the activity layout
        BufferFragment bufferFrag = (BufferFragment)
                getSupportFragmentManager().findFragmentById(R.id.buffer_fragment);
      
        if (bufferFrag != null) {
            // If buffer frag is available, we're in two-pane layout...

            // Call a method in the BufferFragment to update its content
            bufferFrag.updateBufferView(position, buffer);

        } else {
            // If the frag is not available, we're in the one-pane layout and must swap frags...
            // Create fragment and give it an argument for the selected article
            BufferFragment newFragment = new BufferFragment();
            Bundle args = new Bundle();
            args.putInt(BufferFragment.ARG_POSITION, position);
            args.putString("buffer", buffer);
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
