/*
 * Copyright (C) 2012 Tor Hveem
 *
 */ 
package com.ubergeek42.WeechatAndroid;

import java.util.Arrays;
import java.util.Vector;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import com.ubergeek42.weechat.Buffer;
import com.ubergeek42.weechat.BufferObserver;

public class BufferFragment extends Fragment implements BufferObserver, OnKeyListener, OnSharedPreferenceChangeListener, OnClickListener {
    final static String ARG_POSITION = "position";
    final static String TAG = "BufferFragment";
	RelayServiceBinder rsb;
    Buffer buffer;
	private String bufferName;
	private String[] nickCache;
    //private final String[] message = {"Loading. Please wait..."};
    private final String[] message = {""};

	ChatLinesAdapter chatlineAdapter;
	private ListView chatlines;
	private EditText inputBox;
	private Button sendButton;


    int mCurrentPosition = -1;
	// Settings for keeping track of the current tab completion stuff
	private boolean tabCompletingInProgress;
	private Vector<String> tabCompleteMatches;
	private int tabCompleteCurrentIndex;
	private int tabCompleteWordStart;
	private int tabCompleteWordEnd;
	
	private SharedPreferences prefs;
	private boolean enableTabComplete = true;
	
	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, 
        Bundle savedInstanceState) {

        // If activity recreated (such as from screen rotate), restore
        // the previous Buffer selection set by onSaveInstanceState().
        // This is primarily necessary when in the two-pane layout.
        if (savedInstanceState != null) {
            mCurrentPosition = savedInstanceState.getInt(ARG_POSITION);
        }

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.chatview_main, container, false);
    }
        	
	/** Called when the activity is first created. */
    @Override
    public void onStart() {
        super.onStart();
		Log.d(TAG, "onStart");
            
	    prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getBaseContext());
	    prefs.registerOnSharedPreferenceChangeListener(this);
	    enableTabComplete = prefs.getBoolean("tab_completion", true);

        // During startup, check if there are arguments passed to the fragment.
        // onStart is a good place to do this because the layout has already been
        // applied to the fragment at this point so we can safely call the method
        // below that sets the Buffer text.
        Bundle args = getArguments();
        if (args != null) {
            // Set Buffer based on argument passed in
            updateBufferView(args.getInt(ARG_POSITION), args.getString("buffer"));
        } else if (mCurrentPosition != -1) {
            // Set Buffer based on saved instance state defined during onCreateView
            updateBufferView(mCurrentPosition, "");
        }       
    }

    public void updateBufferView(int position, String bufferName) {
        Log.d(TAG, "updateBufferView() p:" + position + "bN:"+bufferName);
       
        
	    // Called without bufferName, can't do anything.
        if (bufferName.equals(""))
        	return;
	    
	    chatlines = (ListView)  getActivity().findViewById(R.id.chatview_lines);
        inputBox = (EditText) getActivity().findViewById(R.id.chatview_input);
        sendButton = (Button) getActivity().findViewById(R.id.chatview_send);
        
		chatlines.setAdapter(new ArrayAdapter<String>(getActivity(), R.layout.tips_list_item, message));
        chatlines.setEmptyView( getActivity().findViewById(android.R.id.empty));

        Log.d(TAG, rsb + bufferName + "");
        
        this.bufferName = bufferName;
        rsb = (RelayServiceBinder)((WeechatActivity) getActivity()).getRsb();
		buffer = rsb.getBufferByName(bufferName);
		
		// TODO this could be settings defined by user
	    getActivity().setTitle(buffer.getShortName() + " " + buffer.getTitle());

		buffer.addObserver(this);
		
		// Subscribe to the buffer(gets the lines for it, and gets nicklist)
		rsb.subscribeBuffer(buffer.getPointer());
		
		chatlineAdapter = new ChatLinesAdapter(getActivity(), buffer);
		chatlines.setAdapter(chatlineAdapter);
		onLineAdded();
		
        sendButton.setOnClickListener(this);
        inputBox.setOnKeyListener(this);

        mCurrentPosition = position;
    }
        
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // Save the current Buffer selection in case we need to recreate the fragment
        outState.putInt(ARG_POSITION, mCurrentPosition);
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
		buffer.removeObserver(this);
	}
	@Override
	public void onNicklistChanged() {
		nickCache = buffer.getNicks();
		Arrays.sort(nickCache);
	}
	// User pressed enter in the input box
	@Override
	public boolean onKey(View v, int keycode, KeyEvent event) {
		if (keycode == KeyEvent.KEYCODE_ENTER && event.getAction()==KeyEvent.ACTION_UP) {
			tabCompletingInProgress=false;
			getActivity().runOnUiThread(messageSender);
			return true;
        }
		// check for terminal resizing keys
        else if (keycode == KeyEvent.KEYCODE_VOLUME_UP && event.getAction()==KeyEvent.ACTION_DOWN) {
			float text_size = Float.parseFloat(prefs.getString("text_size", "10")) + 1;
			// Max text_size of 30
			if (text_size>30) text_size = 30;
			SharedPreferences.Editor editor = prefs.edit();
			editor.putString("text_size", Float.toString(text_size));
	        editor.commit();
			return true;
		} else if(keycode == KeyEvent.KEYCODE_VOLUME_DOWN && event.getAction()==KeyEvent.ACTION_DOWN) {
			float text_size = Float.parseFloat(prefs.getString("text_size", "10")) - 1;
			// Enforce a minimum text size of 5
			if (text_size < 5) text_size = 5;
			SharedPreferences.Editor editor = prefs.edit();
			editor.putString("text_size", Float.toString(text_size));
	        editor.commit();
			return true;
		} else if(keycode == KeyEvent.KEYCODE_VOLUME_DOWN || keycode == KeyEvent.KEYCODE_VOLUME_UP) {
			return true;// Eat these keys
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

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals("tab_completion")) {
			enableTabComplete = prefs.getBoolean("tab_completion", true);
		}
	}
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
	// Send button pressed
	@Override
	public void onClick(View arg0) {
		getActivity().runOnUiThread(messageSender);
	}
}


