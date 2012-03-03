package com.ubergeek42;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

import com.ubergeek42.weechat.ChatBuffers;
import com.ubergeek42.weechat.MessageHandler;
import com.ubergeek42.weechat.Nicklist;
import com.ubergeek42.weechat.WeechatBuffer;
import com.ubergeek42.weechat.weechatrelay.WMessageHandler;
import com.ubergeek42.weechat.weechatrelay.WRelayConnection;
import com.ubergeek42.weechat.weechatrelay.WRelayConnectionHandler;

public class WeechatActivity extends Activity implements OnItemClickListener,
		OnCancelListener, WRelayConnectionHandler {
	private static final Logger logger = LoggerFactory.getLogger(WeechatActivity.class);
	
	BufferListAdapter m_adapter;
	ChatBuffers cbs;
	WRelayConnection wr;
	WMessageHandler msgHandler;
	ProgressDialog progressDialog;
	ListView bufferlist;
	TabHost tabhost;
	LayoutInflater inflater;

	ArrayList<ChatViewTab> chats = new ArrayList<ChatViewTab>();
	ArrayList<TabHost.TabSpec> tabs = new ArrayList<TabHost.TabSpec>();
	private SharedPreferences prefs;
	
	
	private Nicklist nickhandler;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		inflater = getLayoutInflater();

		tabhost = (TabHost) findViewById(R.id.TabHost01);
		tabhost.setup();

		addTab("buffers_tab", "Buffers", R.id.maintab);

		cbs = new ChatBuffers();
		m_adapter = new BufferListAdapter(this, cbs);
		msgHandler = new MessageHandler(cbs);
		nickhandler = new Nicklist(cbs);
				
		bufferlist = (ListView) this.findViewById(R.id.bufferlist);
		bufferlist.setAdapter(m_adapter);
		bufferlist.setOnItemClickListener(this);

		if (prefs.getBoolean("connect_onstart", false))
			connect();
	}

	public void addTab(String tag, String title, int viewID) {
		TabHost.TabSpec tabspec = tabhost.newTabSpec(tag);

		View tabview = inflater.inflate(R.layout.tabs_bg, null);
		TextView tv = (TextView) tabview.findViewById(R.id.tabs_text);
		tv.setText(title);

		tabspec.setIndicator(tabview);
		tabspec.setContent(viewID);
		tabs.add(tabspec);
		tabhost.addTab(tabspec);
	}

	public void addTab(String tag, String title, TabHost.TabContentFactory tcv) {
		TabHost.TabSpec tabspec = tabhost.newTabSpec(tag);

		View tabview = inflater.inflate(R.layout.tabs_bg, null);
		TextView tv = (TextView) tabview.findViewById(R.id.tabs_text);
		tv.setText(title);

		tabspec.setIndicator(tabview);
		tabspec.setContent(tcv);
		tabs.add(tabspec);
		tabhost.addTab(tabspec);
	}

	public void connect() {
		logger.debug("Connect called");
		progressDialog = ProgressDialog.show(this, "Connecting to server",
				"Please wait...");
		progressDialog.setCancelable(true);
		progressDialog.setOnCancelListener(this);

		String server = prefs.getString("connect_host", "0.0.0.0");
		String port = prefs.getString("connect_port", "8001");
		String password = prefs.getString("connect_password", "");

		// Show them the preferences page, then exit
		if (server.equals("0.0.0.0")) {
			Intent i = new Intent(this, WeechatPreferencesActivity.class);
			startActivity(i);
			return;
		}

		wr = new WRelayConnection(server, port, password);
		wr.setConnectionHandler(this);
		setTitle("Weechat - " + wr.getServer());
		wr.tryConnect();
	}
	
	public void disconnect() {
		if (wr!=null && wr.isConnected())
			wr.disconnect();
		
		cbs = new ChatBuffers();
		m_adapter = new BufferListAdapter(this, cbs);
		msgHandler = new MessageHandler(cbs);
		nickhandler = new Nicklist(cbs);
		bufferlist.setAdapter(m_adapter);
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.clear();
		if (wr.isConnected())
			menu.add("Disconnect");
		else
			menu.add("Connect");
		menu.add("Preferences");
		menu.add("Nicklist");
		menu.add("Close Tab");
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		String s = (String) item.getTitle();
		if (s.equals("Quit")) {
			finish();
			android.os.Process.killProcess(android.os.Process.myPid());
		} else if (s.compareTo("Preferences") == 0) {
			Intent i = new Intent(this, WeechatPreferencesActivity.class);
			startActivity(i);
		} else if (s.equals("Connect")) {
			connect();
		} else if (s.equals("Disconnect")) {
			disconnect();
		} else if (s.equals("Nicklist")) {
			int curtab = tabhost.getCurrentTab();
			if(curtab==0) {
				return true; // no nicklist for buffers tab
			}
			ChatViewTab cvt = chats.get(curtab-1);
			WeechatBuffer wb = cvt.getBuffer();
			
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle("Nicks in channel:");
			builder.setItems(wb.getNicks(), new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					// Do nothing...
				}
			});
			builder.create().show();
		} else if (s.equals("Close Tab")) {
			int toRemove = tabhost.getCurrentTab();
			removeTab(toRemove);
			if (toRemove >= 1)
				chats.remove(toRemove-1);
		} else {
			return false;
		}
		return true;
	}

	public void removeDestroyedTabs() {
		ArrayList<ChatViewTab> toremove = new ArrayList<ChatViewTab>();
		int index = 1;// first tab is for buffers which don't have a ChatViewTab
		for(ChatViewTab cvt: chats) {
			if (cvt.isDestroyed()) {
				toremove.add(cvt);
				removeTab(index);
				index++;
			}
		}
	}
	
	private void removeTab(int index) {
		if (index != 0) { // Can't remove main tab
			// Focus/Visibility is a workaround for the following issues:
			// http://code.google.com/p/android/issues/detail?id=2772
			// http://groups.google.com/group/android-developers/browse_thread/thread/8a24314b853bccb5
			tabhost.setFocusable(false);
			tabhost.setVisibility(View.GONE);
			
			tabhost.setCurrentTab(0);
			tabhost.clearAllTabs();
			tabs.remove(index);
			for (TabHost.TabSpec ts : tabs) {
				tabhost.addTab(ts);
			}
			tabhost.setCurrentTab(index - 1);
			tabhost.setFocusable(true);
			tabhost.setVisibility(View.VISIBLE);
		}
	}
	
	@Override
	public void onBackPressed() {
		finish();
		disconnect();
		android.os.Process.killProcess(android.os.Process.myPid());
		super.onBackPressed();
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		WeechatBuffer wb = (WeechatBuffer) bufferlist.getItemAtPosition(position);
		String tag = wb.getFullName();

		ChatViewTab cv = new ChatViewTab(wb, this);

		String title = "Unknown";
		if (wb.getShortName() != null)
			title = wb.getShortName();
		addTab(tag, title, cv);
		tabhost.setCurrentTabByTag(tag);

		// Add to our global list of chats currently open
		chats.add(cv);
	}

	@Override
	public void onCancel(DialogInterface arg0) {
		// Canceling the connect progress dialog
		disconnect();
	}

	@Override
	/**
	 * Called when the connection to the server is successful
	 */
	public void onConnect() {
		runOnUiThread(new Runnable() {
			public void run() {
				if (progressDialog != null && progressDialog.isShowing()) {
					progressDialog.dismiss();
				}
				Toast.makeText(getBaseContext(),
						"Connected to server: " + wr.getServer(), 1000).show();
			}
		});

		// Get a list of buffers
		wr.addHandler("listbuffers", cbs);
		wr.sendMsg("(listbuffers) hdata buffer:gui_buffers(*) number,full_name,short_name,type,title,nicklist,local_variables");

		// Handle event messages regarding buffers
		wr.addHandler("_buffer_opened", cbs);
		wr.addHandler("_buffer_type_changed", cbs);
		wr.addHandler("_buffer_moved", cbs);
		wr.addHandler("_buffer_merged", cbs);
		wr.addHandler("_buffer_unmerged", cbs);
		wr.addHandler("_buffer_renamed", cbs);
		wr.addHandler("_buffer_title_changed", cbs);
		wr.addHandler("_buffer_localvar_added", cbs);
		wr.addHandler("_buffer_localvar_changed", cbs);
		wr.addHandler("_buffer_localvar_removed", cbs);
		wr.addHandler("_buffer_closing", cbs);

		// Buffer line changes(added or removed)
		wr.addHandler("_buffer_line_added", msgHandler);
		wr.addHandler("listlines_reverse", msgHandler);
		wr.sendMsg("(listlines_reverse) hdata buffer:gui_buffers(*)/own_lines/last_line(-" + WeechatBuffer.MAXLINES + ")/data date,displayed,prefix,message");
		
		// Nicklist changes
		wr.addHandler("nicklist", nickhandler);
		wr.addHandler("_nicklist", nickhandler);
		wr.sendMsg("nicklist","nicklist","");
		
		// Subscribe to any future changes
		wr.sendMsg("sync");
	}

	@Override
	/**
	 * Called when the connection to the server is lost
	 */
	public void onDisconnect() {
		logger.trace("Disconnected from server");
		runOnUiThread(new Runnable() {
			public void run() {
				disconnect();
				if (progressDialog != null && progressDialog.isShowing()) {
					progressDialog.dismiss();
				}
				Toast.makeText(getBaseContext(), "Disconnected from server", 1000).show();
			}
		});
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
	}
}