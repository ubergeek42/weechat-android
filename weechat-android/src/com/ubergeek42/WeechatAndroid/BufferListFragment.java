package com.ubergeek42.WeechatAndroid;

import com.ubergeek42.weechat.Buffer;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class BufferListFragment extends ListFragment {
	private static final String TAG = "BufferListFragment";
	private static final String[] message = {"Press Menu->Connect to get started"};


    OnBufferSelectedListener mCallback;

    // The container Activity must implement this interface so the frag can deliver messages
    public interface OnBufferSelectedListener {
        /** Called by BufferlistFragment when a list item is selected 
         * @param b */
        public void onBufferSelected(int position, String fullBufferName);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate()");
        super.onCreate(savedInstanceState);

        // We need to use a different list item layout for devices older than Honeycomb
        int layout = Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB ?
                android.R.layout.simple_list_item_activated_1 : android.R.layout.simple_list_item_1;
    }

    @Override
    public void onStart() {
        Log.d(TAG, "onStart()");
        super.onStart();
        		
        // When in two-pane layout, set the listview to highlight the selected list item
        // (We do this during onStart because at the point the listview is available.)
        if (getFragmentManager().findFragmentById(R.id.buffer_fragment) != null) {
            getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        }
        RelayServiceBinder rsb = (RelayServiceBinder)((WeechatActivity) getActivity()).getRsb();
    }

    @Override
    public void onAttach(Activity activity) {
    	Log.d(TAG, "onAttach()");
        super.onAttach(activity);
        

        // This makes sure that the container activity has implemented
        // the callback interface. If not, it throws an exception.
        try {
            mCallback = (OnBufferSelectedListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnBufferSelectedListener");
        }
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        // Notify the parent activity of selected item
    	
    	// Get the current buffer
		Buffer b = (Buffer) getListView().getItemAtPosition(position);

        mCallback.onBufferSelected(position, b.getFullName());
        
        // Set the item as checked to be highlighted when in two-pane layout
        getListView().setItemChecked(position, true);
    }
}