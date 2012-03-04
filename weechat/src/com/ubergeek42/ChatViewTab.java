package com.ubergeek42;

import java.util.LinkedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.text.Html;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TabHost;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.ubergeek42.weechat.Buffer;
import com.ubergeek42.weechat.BufferLine;
import com.ubergeek42.weechat.BufferObserver;

public class ChatViewTab implements TabHost.TabContentFactory, BufferObserver, OnClickListener, OnKeyListener {

	private static final Logger logger = LoggerFactory.getLogger(ChatViewTab.class);
	
	private Buffer wb;
	private LayoutInflater inflater;

	private ScrollView scrollview;
	private TextView titlestr;
	private TableLayout table;
	private EditText inputBox;
	private Button sendButton;
	private WeechatActivity activity;
	private boolean destroyed = false;
	
	// Used to cache rendered table rows(as it is very slow to build them every refresh)
	private LRUMap<BufferLine,TableRow> tableCache = new LRUMap<BufferLine,TableRow>(Buffer.MAXLINES, Buffer.MAXLINES);
	
	// TODO: expose setting in the UI for coloring messages(Note: somewhat slow)
	private static boolean enableColor = true;
	
	protected static void setColorsEnabled(boolean enabled) {
		enableColor = enabled;
	}
	
	public ChatViewTab(Buffer wb, WeechatActivity activity) {
		this.inflater = activity.getLayoutInflater();
		this.activity = activity;
		this.wb = wb;
		
		wb.addObserver(this);
	}
	
	public void destroy() {
		if (wb!=null) {
			wb.removeObserver(this);
			wb = null;
		}
		this.destroyed  = true;
		
		// Signal the activity to remove any tabs that were destroyed(i.e. this one)
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				activity.removeDestroyedTabs();
			}
		});
	}
	
	// Runs on the uiThread to update the contents of the various buffers
	Runnable updateCV = new Runnable() {
		@Override
		public void run() {
			long start = System.currentTimeMillis();
			if (destroyed)return;
			LinkedList<BufferLine> lines = wb.getLines();
			table.removeAllViews();
			for(BufferLine cm: lines) {
				if (tableCache.containsKey(cm))
					table.addView(tableCache.get(cm));
				else {
					TableRow tr = (TableRow)inflater.inflate(R.layout.chatline, null);
					
					TextView timestamp = (TextView) tr.findViewById(R.id.chatline_timestamp);
					timestamp.setText(cm.getTimestampStr());
					
					TextView prefix = (TextView) tr.findViewById(R.id.chatline_prefix);
					if (enableColor) {
						prefix.setText(Html.fromHtml(cm.getPrefixHTML()), TextView.BufferType.SPANNABLE);
					} else {
						prefix.setText(cm.getPrefix());
					}
					
					TextView message = (TextView) tr.findViewById(R.id.chatline_message);
					if (enableColor) {
						message.setText(Html.fromHtml(cm.getMessageHTML()), TextView.BufferType.SPANNABLE);
					} else {
						message.setText(cm.getMessage());
					}

					// Add to the cache
					tableCache.put(cm, tr);
					table.addView(tr);
				}
			}
			logger.debug("updateChatView took: " + (System.currentTimeMillis() - start) + "ms");
			scrollview.post(new Runnable() {
				@Override
				public void run() {
					scrollview.fullScroll(ScrollView.FOCUS_DOWN);					
				}
			});
		}
	};
	Runnable messageSender = new Runnable(){
		@Override
		public void run() {
			if (destroyed)return;
			String input = inputBox.getText().toString();
			if (input.length() == 0) return; // Ignore empty input box
			
			String message = "input " + wb.getFullName() + " " + input; 
			inputBox.setText("");
			activity.wr.sendMsg(message + "\n");
		}
	};
	
	
	@Override
	public View createTabContent(String tag) {
        View x = inflater.inflate(R.layout.chatview, null);
        scrollview = (ScrollView) x.findViewById(R.id.chatview_scrollview);
        inputBox = (EditText)x.findViewById(R.id.chatview_input);
        table = (TableLayout)x.findViewById(R.id.chatview_lines);
        titlestr = (TextView) x.findViewById(R.id.chatview_title);
        sendButton = (Button) x.findViewById(R.id.chatview_send);
        
        //titlestr.setText("random text for the title this is kinda long and hopefully more than 2 lines and takes up a bunch of space so it needs to marquee");
        if (!destroyed)
        	titlestr.setText(wb.getTitle());
        
        // TODO: figure out best way to have scrollable title
        //titlestr.setMovementMethod(new ScrollingMovementMethod());
        
        scrollview.setFocusable(false);
        table.setFocusable(false);
        titlestr.setFocusable(false);
        
        sendButton.setOnClickListener(this);
        inputBox.setOnKeyListener(this);
        
        activity.runOnUiThread(updateCV);
        x.post(new Runnable() {
			@Override
			public void run() {
				inputBox.requestFocus();
			}
        });
		return x;
	}

	@Override
	public void onLineAdded() {
		activity.runOnUiThread(updateCV);
	}
	
	@Override
	public void onClick(View v) {
		// Send the message contents
		activity.runOnUiThread(messageSender);
	}

	@Override
	public boolean onKey(View v, int keycode, KeyEvent event) {
		if (keycode == KeyEvent.KEYCODE_ENTER && event.getAction()==KeyEvent.ACTION_UP) {
			activity.runOnUiThread(messageSender);
			return true;
		}
		return false;
	}

	public Buffer getBuffer() {
		return this.wb;
	}

	@Override
	public void onBufferClosed() {
		this.destroy();
	}

	public boolean isDestroyed() {
		return destroyed;
	}

}
