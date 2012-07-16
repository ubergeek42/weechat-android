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

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.ubergeek42.weechat.Buffer;
import com.ubergeek42.weechat.relay.RelayConnectionHandler;

public class WeechatActivity extends Activity implements OnItemClickListener, RelayConnectionHandler {

	private boolean mBound = false;
	private RelayServiceBinder rsb;
	private ListView bufferlist;
	
	private BufferListAdapter m_adapter;
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
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
		if (mBound) {
			unbindService(mConnection);
			mBound = false;
		}
	}
		
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.main_activity, menu);
	    if (rsb != null && rsb.isConnected()) {
	    	menu.findItem(R.id.connect).setTitle("Disconnect");
	    }
	    
	    return true;
	}
		
	@Override
	// Handle the menu clicks
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.connect:
				String s = (String) item.getTitle();
				 if (s.equals("Disconnect")) {
						if (rsb != null)rsb.shutdown();
					} else if (s.equals("Connect")) {
						if (rsb != null)rsb.connect();
					}
				 return true;
			case R.id.quit:
				if (rsb != null)rsb.shutdown();
				unbindService(mConnection);
				mBound = false;
				stopService(new Intent(this, RelayService.class));
				finish();
				return true;
			case R.id.about:
				Intent ai = new Intent(this, WeechatAboutActivity.class);
				startActivity(ai);
				return true;
			case R.id.settings:
				Intent si = new Intent(this, WeechatPreferencesActivity.class);
				startActivity(si);
				return true;
	        case android.R.id.home:
	            // app icon in action bar clicked; go home
	            Intent intent = new Intent(this, WeechatActivity.class);
	            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
	            startActivity(intent);
	            return true;
			default:
				return super.onOptionsItemSelected(item);
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
			this.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					bufferlist.setAdapter(m_adapter);
					m_adapter.onBuffersChanged();
				}
			});
		}
	}

	@Override
	public void onDisconnect() {
		// Create and update the buffer list when we connect to the service
		m_adapter = null;
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
		
	}
}
