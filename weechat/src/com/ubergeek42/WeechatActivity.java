package com.ubergeek42;

import java.util.ArrayList;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.widget.TabHost.TabSpec;
import android.widget.Toast;

import com.ubergeek42.weechat.ChatBuffers;
import com.ubergeek42.weechat.MessageHandler;
import com.ubergeek42.weechat.WeechatBuffer;
import com.ubergeek42.weechat.weechatrelay.WMessageHandler;
import com.ubergeek42.weechat.weechatrelay.WRelayConnection;

public class WeechatActivity extends Activity implements OnItemClickListener {
	BufferListAdapter m_adapter;
	ChatBuffers cbs;
	WRelayConnection wr;
	WMessageHandler msgHandler;
	ProgressDialog progressDialog;
	ListView bufferlist;
	TabHost tabhost;
	LayoutInflater inflater;
	
	ArrayList<ChatView> chats = new ArrayList<ChatView>();
	ArrayList<TabHost.TabSpec> tabs = new ArrayList<TabHost.TabSpec>();
	
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        inflater = getLayoutInflater();
        
        tabhost = (TabHost) findViewById(R.id.TabHost01);
        tabhost.setup();
        
        TabHost.TabSpec maintab = tabhost.newTabSpec("buffers_tab");
        maintab.setIndicator("Buffers"); // TODO: use custom view for the tab widget
        maintab.setContent(R.id.maintab);
        tabs.add(maintab);
        tabhost.addTab(maintab);
        
        cbs = new ChatBuffers();
        m_adapter = new BufferListAdapter(this, cbs);
        
        bufferlist = (ListView) this.findViewById(R.id.bufferlist);
        bufferlist.setAdapter(m_adapter);
        bufferlist.setOnItemClickListener(this);
        
		//connect();
    }
    
    public void connect() {
    	progressDialog = ProgressDialog.show(this,"Connecting to server","Please wait...");
    	
    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    	
    	String server = prefs.getString("connect_host", "0.0.0.0");
		String port = prefs.getString("connect_port","8001");
		String password = prefs.getString("connect_password","");

		// Show them the preferences page, then exit
		if (server.equals("0.0.0.0")) {
			Intent i = new Intent(this, WeechatPreferencesActivity.class);
    		startActivity(i);
    		return;
		}
		
    	wr = new WRelayConnection(server,port,password);
    	setTitle("Weechat - " + wr.getServer());
		wr.connect();
		
		if (wr.isConnected() == false) {
			if (progressDialog!=null && progressDialog.isShowing()) {
				progressDialog.dismiss();
			}
			Toast.makeText(getBaseContext(), "Error connecting to server", 1000).show();
		}
		
		// Get a list of buffers
		wr.addHandler("listbuffers", cbs);
		wr.writeMsg("(listbuffers) hdata buffer:gui_buffers(*) number,full_name,short_name,type,title,nicklist,local_variables");
		
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
		
		msgHandler = new MessageHandler(cbs);
		wr.addHandler("_buffer_line_added", msgHandler);

		wr.writeMsg("sync");
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	menu.clear();
    	menu.add("Connect");
    	menu.add("Preferences");
    	menu.add("Close Tab");
    	return super.onPrepareOptionsMenu(menu);
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	String s = (String) item.getTitle();
    	if (s.compareTo("Quit")==0) {
    		finish();
    		android.os.Process.killProcess(android.os.Process.myPid());
    	} else if (s.compareTo("Preferences")==0) {
    		Intent i = new Intent(this, WeechatPreferencesActivity.class);
    		startActivity(i);
    	} else if (s.compareTo("Connect")==0) {
    		// disconnect/etc first
    		connect();
    	} else if (s.compareTo("Close Tab")==0) {
    		int toRemove = tabhost.getCurrentTab();
    		if (toRemove != 0) { // Can't remove main tab
    			tabhost.setCurrentTab(0);
    			tabhost.clearAllTabs();
    			tabs.remove(toRemove);
    			for(TabHost.TabSpec ts: tabs) {
    				tabhost.addTab(ts);
    			}
    			tabhost.setCurrentTab(toRemove-1);
    		}
    	} else {
    		return false;
    	}
    	return true;
    }
    
    @Override
    public void onBackPressed() {
    	finish();
    	if (wr!=null) wr.disconnect();
    	android.os.Process.killProcess(android.os.Process.myPid());
    	super.onBackPressed();
    }
	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		WeechatBuffer wb = (WeechatBuffer) bufferlist.getItemAtPosition(position);
		String tag = wb.getFullName();
		
		TabHost.TabSpec ts = tabhost.newTabSpec(tag);
		ChatView cv = new ChatView(wb, ts, this);
        tabhost.addTab(ts);
        tabhost.setCurrentTabByTag(tag);
        
        // Add to our global list of chats currently open
		chats.add(cv);
		tabs.add(ts);
	}
}