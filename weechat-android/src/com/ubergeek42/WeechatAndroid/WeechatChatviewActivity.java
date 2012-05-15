package com.ubergeek42.WeechatAndroid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import com.ubergeek42.weechat.Buffer;
import com.ubergeek42.weechat.BufferObserver;

public class WeechatChatviewActivity extends Activity implements OnClickListener, OnKeyListener, BufferObserver {

	private static Logger logger = LoggerFactory.getLogger(WeechatChatviewActivity.class);
	
	private ListView chatlines;
	private EditText inputBox;
	private Button sendButton;
	
	private boolean mBound;
	private RelayServiceBinder rsb;
	
	private String bufferName;
	private Buffer buffer;
	
	private ChatLinesAdapter chatlineAdapter;
	

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.chatview_main);

	    Bundle extras = getIntent().getExtras();
	    if (extras == null) {
	    	finish(); // quit if no view given..
	    }
	    
	    bufferName = extras.getString("buffer");

	    setTitle("Weechat - " + bufferName);
	    
	    chatlines = (ListView) findViewById(R.id.chatview_lines);
        inputBox = (EditText)findViewById(R.id.chatview_input);
        sendButton = (Button)findViewById(R.id.chatview_send);
        
        String[] message = {"Loading. Please wait..."};
		chatlines.setAdapter(new ArrayAdapter<String>(this, R.layout.tips_list_item, message));
        chatlines.setEmptyView(findViewById(android.R.id.empty));
        
        sendButton.setOnClickListener(this);
        inputBox.setOnKeyListener(this);
	}

	@Override
	protected void onStart() {
		super.onStart();
		// Bind to the relay service in the background
        Intent i = new Intent(this, RelayService.class);
        mBound = bindService(i, mConnection, Context.BIND_AUTO_CREATE);
        if (!mBound) {
        	finish();
        }
	}

	@Override
	protected void onStop() {
		super.onStop();
		if (buffer!=null)
			buffer.removeObserver(this);
		
		if(mBound) {
			unbindService(mConnection);
			mBound = false;
		}
	}
	
	private void initView() { // Called once we have a connection with the backend
		buffer = rsb.getBufferByName(bufferName);
		buffer.addObserver(this);
		
		chatlineAdapter = new ChatLinesAdapter(this, buffer);

		chatlines.setAdapter(chatlineAdapter);
		onLineAdded();
	}
	
	ServiceConnection mConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			rsb = (RelayServiceBinder) service;
			mBound = true;
			initView();
		}
		@Override
		public void onServiceDisconnected(ComponentName name) {
			mBound = false;
			rsb = null;
			// Only called when the background service crashes...
		}
	};
	
	// Sends the message if necessary
	Runnable messageSender = new Runnable(){
		@Override
		public void run() {
			String input = inputBox.getText().toString();
			if (input.length() == 0) return; // Ignore empty input box
			
			String message = "input " + bufferName + " " + input; 
			inputBox.setText("");
			rsb.sendMessage(message + "\n");
		}
	};
	
	// User pressed enter in the input box
	@Override
	public boolean onKey(View v, int keycode, KeyEvent event) {
		if (keycode == KeyEvent.KEYCODE_ENTER && event.getAction()==KeyEvent.ACTION_UP) {
			runOnUiThread(messageSender);
			return true;
		}
		// TODO: add code for nick completion here
		return false;
	}

	// Send button pressed
	@Override
	public void onClick(View arg0) {
		runOnUiThread(messageSender);
	}

	// Called whenever a new line is added to a buffer
	@Override
	public void onLineAdded() {
		rsb.resetNotifications();
		buffer.resetHighlight();
		buffer.resetUnread();
		
		chatlineAdapter.notifyChanged();
		
		chatlines.post(new Runnable() {
			@Override
			public void run() {
				chatlines.setSelectionFromTop(chatlineAdapter.getCount()-1, 0);				
			}
		});
	}

	@Override
	public void onBufferClosed() {
		// Close when the buffer is closed
		buffer.removeObserver(this);
		finish();
	}

	//==== Options Menu
	@Override
	// Build the options menu
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.clear();
		menu.add("Toggle Timestamps");
		menu.add("Toggle Filters");
		menu.add("Close");
		menu.add("Settings");
		return super.onPrepareOptionsMenu(menu);
	}
	@Override
	// Handle the options when the user presses the Menu key
	public boolean onOptionsItemSelected(MenuItem item) {
		String s = (String) item.getTitle();
		if (s.equals("Close")) {
			buffer.removeObserver(this);
			finish();
		} else if (s.equals("Settings")) {
			Intent i = new Intent(this, WeechatPreferencesActivity.class);
			startActivity(i);
		} else if (s.equals("Toggle Timestamps")) {
			chatlineAdapter.toggleTimestamps();
		} else if (s.equals("Toggle Filters")) {
			chatlineAdapter.toggleFilters();
		}
		return true;
	}
}
