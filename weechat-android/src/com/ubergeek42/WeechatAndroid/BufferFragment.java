/*
 * Copyright (C) 2012 Tor Hveem
 *
 */ 
package com.ubergeek42.WeechatAndroid;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import com.ubergeek42.weechat.Buffer;

public class BufferFragment extends Fragment {
    final static String ARG_POSITION = "position";
    final static String TAG = "BufferFragment";
	RelayServiceBinder rsb;
	private boolean mBound;


    int mCurrentPosition = -1;
	
    /*public BufferFragment(RelayServiceBinder rsb) {
		this.rsb = rsb;
	}*/
    public BufferFragment(){};

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
        //TextView Buffer = (TextView) getActivity().findViewById(R.id.Buffer);
        //Buffer.setText(Ipsum.Buffers[position]);
        
	    //setTitle("Weechat - " + bufferName);
		ListView chatlines;
		EditText inputBox;
		Button sendButton;
		
	    Buffer buffer;
	    
	    chatlines = (ListView)  getActivity().findViewById(R.id.chatview_lines);
        inputBox = (EditText) getActivity().findViewById(R.id.chatview_input);
        sendButton = (Button) getActivity().findViewById(R.id.chatview_send);
        
        String[] message = {"Loading. Please wait..."};
		chatlines.setAdapter(new ArrayAdapter<String>(getActivity(), R.layout.tips_list_item, message));
        chatlines.setEmptyView( getActivity().findViewById(android.R.id.empty));
        //Context ctx = (Context)BufferFragment.this.getActivity();
       // ctx.bin

        Log.d(TAG, rsb + bufferName + "");
		//buffer = rsb.getBufferByName(bufferName);
		//buffer.addObserver(getActivity());
		
		// Subscribe to the buffer(gets the lines for it, and gets nicklist)
		//rsb.subscribeBuffer(buffer.getPointer());
		
		//ChatLinesAdapter chatlineAdapter = new ChatLinesAdapter(getActivity(), buffer);
		//chatlines.setAdapter(chatlineAdapter);
		//onLineAdded();
		
        mCurrentPosition = position;
    }
    

    
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // Save the current Buffer selection in case we need to recreate the fragment
        outState.putInt(ARG_POSITION, mCurrentPosition);
    }
}


