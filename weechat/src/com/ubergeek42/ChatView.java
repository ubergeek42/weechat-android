package com.ubergeek42;

import java.util.LinkedList;

import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TabHost;
import android.widget.TabHost.TabSpec;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.ubergeek42.weechat.ChatMessage;
import com.ubergeek42.weechat.WBufferObserver;
import com.ubergeek42.weechat.WeechatBuffer;

public class ChatView implements TabHost.TabContentFactory, WBufferObserver, OnClickListener, OnKeyListener {

	private WeechatBuffer wb;
	private LayoutInflater inflater;
	private TabSpec tabspec;

	private ScrollView scrollview;
	private TextView titlestr;
	private TableLayout table;
	private EditText inputBox;
	private Button sendButton;
	private WeechatActivity activity;
	private boolean destroyed = false;

	public ChatView(WeechatBuffer wb, TabSpec tabspec, WeechatActivity activity) {
		this.inflater = activity.getLayoutInflater();
		this.activity = activity;
		this.wb = wb;
		this.tabspec = tabspec;
		
		wb.addObserver(this);
		
		if (wb.getShortName()!=null)
			tabspec.setIndicator(wb.getShortName());
		else
			tabspec.setIndicator("blah");
		tabspec.setContent(this);
	}
	
	public void destroy() {
		wb.removeObserver(this);
		this.destroyed  = true;
	}

	// Runs on the uiThread to update the contents of the various buffers
	Runnable updateCV = new Runnable() {
		@Override
		public void run() {
			if (destroyed)return;
			LinkedList<ChatMessage> lines = wb.getLines();
			table.removeAllViews();
			for(ChatMessage cm: lines) {
				TableRow tr = (TableRow)inflater.inflate(R.layout.chatline, null);
				
				TextView timestamp = (TextView) tr.findViewById(R.id.chatline_timestamp);
				timestamp.setText(cm.getTimestampStr());
				
				TextView prefix = (TextView) tr.findViewById(R.id.chatline_prefix);
				prefix.setText(cm.getPrefix());
				
				TextView message = (TextView) tr.findViewById(R.id.chatline_message);
				message.setText(cm.getMessage());
				
				table.addView(tr);
			}
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
			activity.wr.writeMsg(message + "\n");
			inputBox.setText("");
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
        titlestr.setText(wb.getTitle());
        
        // TODO: figure out best way to have scrollable title
        //titlestr.setMovementMethod(new ScrollingMovementMethod());
        
        sendButton.setOnClickListener(this);
        inputBox.setOnKeyListener(this);
        
        activity.runOnUiThread(updateCV);
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

}
