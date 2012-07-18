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

import java.util.Arrays;
import java.util.Vector;

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

public class WeechatChatviewActivity extends WeechatActivity implements OnClickListener, OnKeyListener, BufferObserver, OnSharedPreferenceChangeListener {

	private ListView chatlines;
	private EditText inputBox;
	private Button sendButton;
	
	private boolean mBound;
	private RelayServiceBinder rsb;
	
	private String bufferName;
	private Buffer buffer;
	
	private ChatLinesAdapter chatlineAdapter;
	
	
	private String[] nickCache;

	// Settings for keeping track of the current tab completion stuff
	private boolean tabCompletingInProgress;
	private Vector<String> tabCompleteMatches;
	private int tabCompleteCurrentIndex;
	private int tabCompleteWordStart;
	private int tabCompleteWordEnd;
	
	private SharedPreferences prefs;
	private boolean enableTabComplete = true;
	

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.chatview_main);

	    Bundle extras = getIntent().getExtras();
	    if (extras == null) {
	    	finish(); // quit if no view given..
	    }
	    
	    prefs = PreferenceManager.getDefaultSharedPreferences(this.getBaseContext());
	    prefs.registerOnSharedPreferenceChangeListener(this);
	    enableTabComplete = prefs.getBoolean("tab_completion", true);
	    
	    
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
		if (buffer!=null) {
			buffer.removeObserver(this);
			rsb.unsubscribeBuffer(buffer.getPointer());
		}
		
		if(mBound) {
			unbindService(mConnection);
			mBound = false;
		}
	}
	
	private void initView() { // Called once we have a connection with the backend
		buffer = rsb.getBufferByName(bufferName);
		buffer.addObserver(this);
		
		// Subscribe to the buffer(gets the lines for it, and gets nicklist)
		rsb.subscribeBuffer(buffer.getPointer());
		
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
			tabCompletingInProgress=false;
			runOnUiThread(messageSender);
			return true;
		} else if((keycode == KeyEvent.KEYCODE_TAB || keycode == KeyEvent.KEYCODE_SEARCH) && event.getAction() == KeyEvent.ACTION_DOWN) {
			if (!enableTabComplete || nickCache == null) return true;
			
			// Get the current input text
			String txt = inputBox.getText().toString();
			if (tabCompletingInProgress == false) {
				int currentPos = inputBox.getSelectionStart()-1;
				int start = currentPos;
				// Search backwards to find the beginning of the word
				while(start>0 && txt.charAt(start) != ' ') start--;
				
				if (start>0) start++;
				String prefix = txt.substring(start, currentPos+1).toLowerCase();
				if (prefix.length()<2) {
					//No tab completion
					return true;
				}
				
				Vector<String> matches = new Vector<String>();
				for(String possible: nickCache) {
					
					String temp = possible.toLowerCase().trim();
					if (temp.startsWith(prefix)) {
						matches.add(possible.trim());
					}
				}
				if (matches.size() == 0) return true;
				
				tabCompletingInProgress = true;
				tabCompleteMatches = matches;
				tabCompleteCurrentIndex = 0;
				tabCompleteWordStart = start;
				tabCompleteWordEnd = currentPos;
			} else {
				tabCompleteWordEnd = tabCompleteWordStart + tabCompleteMatches.get(tabCompleteCurrentIndex).length()-1; // end of current tab complete word
				tabCompleteCurrentIndex = (tabCompleteCurrentIndex+1)%tabCompleteMatches.size(); // next match
			}
			
			String newtext = txt.substring(0, tabCompleteWordStart) + tabCompleteMatches.get(tabCompleteCurrentIndex) + txt.substring(tabCompleteWordEnd+1);
			tabCompleteWordEnd = tabCompleteWordStart + tabCompleteMatches.get(tabCompleteCurrentIndex).length(); // end of new tabcomplete word
			inputBox.setText(newtext);
			inputBox.setSelection(tabCompleteWordEnd);

			return true;
		} else if(KeyEvent.isModifierKey(keycode) || keycode == KeyEvent.KEYCODE_TAB || keycode == KeyEvent.KEYCODE_SEARCH) {
			// If it was a modifier key(or tab/search), don't kill tabCompletingInProgress
			return false;
		}
		tabCompletingInProgress = false;
		return false;
	}

	// Send button pressed
	@Override
	public void onClick(View arg0) {
		runOnUiThread(messageSender);
	}

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
	
	@Override
	public void onNicklistChanged() {
		nickCache = buffer.getNicks();
		Arrays.sort(nickCache);
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

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals("tab_completion")) {
			enableTabComplete = prefs.getBoolean("tab_completion", true);
		}
	}
}
