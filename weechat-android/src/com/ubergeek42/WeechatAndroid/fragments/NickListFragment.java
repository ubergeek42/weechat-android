package com.ubergeek42.WeechatAndroid.fragments;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ListView;

import com.actionbarsherlock.app.SherlockListFragment;
import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.service.RelayServiceBinder;

public class NickListFragment extends SherlockListFragment {
    private static final String TAG = "NickListFragment";
    RelayServiceBinder rsb;

    OnNickSelectedListener mCallback;

    // The container Activity must implement this interface so the frag can deliver messages
    public interface OnNickSelectedListener {
        /**
         * Called by NickListFragment when a list item is selected
         * 
         * @param b
         */
        public void onNickSelected(int position, String nick);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate()");
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onStart() {
        Log.d(TAG, "onStart()");
        super.onStart();

        // When in two-pane layout, set the listview to highlight the selected list item
        // (We do this during onStart because at the point the listview is available.)
        if (getFragmentManager().findFragmentById(R.id.buffer_fragment) != null) {
            getListView().setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
        }
        // RelayServiceBinder rsb = (RelayServiceBinder)((WeechatActivity) getActivity()).getRsb();
    }

    @Override
    public void onAttach(Activity activity) {
        Log.d(TAG, "onAttach()");
        super.onAttach(activity);

        // This makes sure that the container activity has implemented
        // the callback interface. If not, it throws an exception.
        try {
            mCallback = (OnNickSelectedListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnNickSelectedListener");
        }
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        // Notify the parent activity of selected item

        // Get the current buffer
        // Buffer b = (Buffer) getListView().getItemAtPosition(position);

        // FIXME
        mCallback.onNickSelected(position, "nick");

        // Set the item as checked to be highlighted when in two-pane layout
        getListView().setItemChecked(position, true);
    }

    public void updateNickListView(String bufferName) {
        Log.d(TAG, "updateNickListView() bN:" + bufferName);

        // Called without bufferName, can't do anything.
        if (bufferName.equals("")) {
            return;
            /*
             * chatlines = (ListView) getActivity().findViewById(R.id.chatview_lines); inputBox =
             * (EditText) getActivity().findViewById(R.id.chatview_input); sendButton = (Button)
             * getActivity().findViewById(R.id.chatview_send);
             * 
             * NickListAdapter nladapter = new NickListAdapter(((WeechatActivity) getActivity()),
             * ((WeechatActivity) getActivity()).getRsb());
             * 
             * chatlines.setAdapter(new ArrayAdapter<String>(getActivity(), R.layout.tips_list_item,
             * message)); chatlines.setEmptyView( getActivity().findViewById(android.R.id.empty));
             * 
             * 
             * this.bufferName = bufferName; rsb = (RelayServiceBinder)((WeechatActivity)
             * getActivity()).getRsb(); buffer = rsb.getBufferByName(bufferName);
             * 
             * // TODO this could be settings defined by user
             * getActivity().setTitle(buffer.getShortName() + " " + buffer.getTitle());
             * 
             * buffer.addObserver(this);
             * 
             * chatlineAdapter = new ChatLinesAdapter(getActivity(), buffer);
             * setAdapter(chatlineAdapter); mCurrentPosition = position;
             */
        }
    }

}