package com.ubergeek42.WeechatAndroid;

import java.util.LinkedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.Html;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.ubergeek42.weechat.Buffer;
import com.ubergeek42.weechat.BufferLine;
import com.ubergeek42.weechat.BufferObserver;
import com.ubergeek42.weechat.relay.RelayConnection;

public class WeechatChatviewActivity extends Activity implements OnClickListener, OnKeyListener, BufferObserver {

	private static Logger logger = LoggerFactory.getLogger(WeechatChatviewActivity.class);
	
	private ScrollView scrollview;
	private EditText inputBox;
	private TableLayout table;
	private Button sendButton;
	
	private boolean mBound;
	private RelayServiceBinder rsb;
	
	private String bufferName;
	private Buffer buffer;
	private LayoutInflater inflater;
	
	private boolean enableTimestamp = true;
	private boolean enableColor = true;
	private boolean enableFilters = true;
	
	private LRUMap<BufferLine,TableRow> tableCache = new LRUMap<BufferLine,TableRow>(Buffer.MAXLINES, Buffer.MAXLINES);

	private SharedPreferences prefs;
	

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
	    
	    inflater = getLayoutInflater();
	    
	    scrollview = (ScrollView) findViewById(R.id.chatview_scrollview);
        inputBox = (EditText)findViewById(R.id.chatview_input);
        table = (TableLayout)findViewById(R.id.chatview_lines);
        sendButton = (Button)findViewById(R.id.chatview_send);
        
        scrollview.setFocusable(false);
        table.setFocusable(false);
        
        sendButton.setOnClickListener(this);
        inputBox.setOnKeyListener(this);
        
        prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		
		// Load the preferences
		enableColor = prefs.getBoolean("chatview_colors", true);
		enableTimestamp = prefs.getBoolean("chatview_timestamps", true);
		enableFilters = prefs.getBoolean("chatview_filters", true);
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
	
	@Override
	// Build the options menu
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.clear();
		menu.add("Close Buffer");
		menu.add("Settings");
		menu.add("Toggle Filters");
		menu.add("Toggle Colors");
		menu.add("Toggle Timestamps");
		return super.onPrepareOptionsMenu(menu);
	}
	@Override
	// Handle the options when the user presses the Menu key
	public boolean onOptionsItemSelected(MenuItem item) {
		String s = (String) item.getTitle();
		if (s.equals("Toggle Filters")) {
			enableFilters = !enableFilters;
			refreshView();
		} else if (s.equals("Toggle Colors")) {
			enableColor = !enableColor;
			tableCache.clear();
			refreshView();
		} else if (s.equals("Toggle Timestamps")) {
			enableTimestamp = !enableTimestamp;
			tableCache.clear();
			refreshView();
		} else if (s.equals("Close Buffer")) {
			buffer.removeObserver(this);
			finish();
		} else if (s.equals("Settings")) {
			Intent i = new Intent(this, WeechatPreferencesActivity.class);
			startActivity(i);
		}
		return true;
	}
	private void initView() {
		buffer = rsb.getBufferByName(bufferName);
		buffer.addObserver(this);
        refreshView();
	}
	
	private void refreshView() {
		runOnUiThread(refreshView);
	}
	
	Runnable refreshView = new Runnable() {
		@Override
		public void run() {
			// Mark all messages as read since we are looking at them
			rsb.resetNotifications();
			buffer.resetHighlight();
			buffer.resetUnread();
			
			long start = System.currentTimeMillis();
			LinkedList<BufferLine> lines = buffer.getLines();
			table.removeAllViews();
			
			synchronized(tableCache) {
				for(BufferLine cm: lines) {
					TableRow toAdd = null;
					if (tableCache.containsKey(cm)) {
						toAdd = tableCache.get(cm);
					} else {
						TableRow tr;
						tr = (TableRow)inflater.inflate(R.layout.chatview_line, null);

						TextView timestamp = (TextView) tr.findViewById(R.id.chatline_timestamp);
						if (enableTimestamp) {
							timestamp.setText(cm.getTimestampStr());
						} else {
							tr.removeView(timestamp);
						}
						
						TextView prefix = (TextView) tr.findViewById(R.id.chatline_prefix);
						if(cm.getHighlight()) {
							prefix.setBackgroundColor(Color.MAGENTA);
							prefix.setTextColor(Color.YELLOW);
							prefix.setText(cm.getPrefix());
						} else {
							if (enableColor) {
								prefix.setText(Html.fromHtml(cm.getPrefixHTML()), TextView.BufferType.SPANNABLE);
							} else {
								prefix.setText(cm.getPrefix());
							}
						}
						
						TextView message = (TextView) tr.findViewById(R.id.chatline_message);
						if (enableColor) {
							message.setText(Html.fromHtml(cm.getMessageHTML()), TextView.BufferType.SPANNABLE);
						} else {
							message.setText(cm.getMessage());
						}
	
						// Add to the cache
						tableCache.put(cm, tr);
						toAdd = tr;
					}
					// Skip drawing filtered lines(but we render them in case the user wants to toggle them)
					if (enableFilters && !cm.getVisible())
						continue;
					table.addView(toAdd);
				}
			}
			table.invalidate();
			
			Log.d("WeechatBufferActivity","updateChatView took: " + (System.currentTimeMillis() - start) + "ms");
			scrollview.post(new Runnable() {
				@Override
				public void run() {
					scrollview.fullScroll(ScrollView.FOCUS_DOWN);					
				}
			});
		}
	};
	
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
	@Override
	public boolean onKey(View v, int keycode, KeyEvent event) {
		if (keycode == KeyEvent.KEYCODE_ENTER && event.getAction()==KeyEvent.ACTION_UP) {
			runOnUiThread(messageSender);
			return true;
		}
		return false;
	}

	@Override
	public void onClick(View arg0) {
		runOnUiThread(messageSender);
	}

	@Override
	public void onLineAdded() {
		refreshView();
	}

	@Override
	public void onBufferClosed() {
		// Close when the buffer is closed
		buffer.removeObserver(this);
		finish();
	}
	
}
