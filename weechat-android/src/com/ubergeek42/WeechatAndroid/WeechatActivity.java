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
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

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
	// Build the options menu
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.clear();
		if (rsb != null && rsb.isConnected())
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
			if (rsb != null)rsb.shutdown();
			unbindService(mConnection);
			mBound = false;
			stopService(new Intent(this, RelayService.class));
			finish();
		} else if (s.equals("Disconnect")) {
			if (rsb != null)rsb.shutdown();
		} else if (s.equals("Connect")) {
			if (rsb != null)rsb.connect();
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
}
