package com.ubergeek42.WeechatAndroid.fragments;

import android.support.v4.app.Fragment;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RelativeLayout;

import com.ubergeek42.WeechatAndroid.adapters.BufferListAdapter;
import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.WeechatActivity;
import com.ubergeek42.WeechatAndroid.adapters.BufferListClickListener;
import com.ubergeek42.WeechatAndroid.relay.BufferList;
import com.ubergeek42.WeechatAndroid.relay.BufferListEye;
import com.ubergeek42.WeechatAndroid.service.Events;
import com.ubergeek42.WeechatAndroid.service.P;
import com.ubergeek42.cats.Cat;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;

import static com.ubergeek42.WeechatAndroid.service.RelayService.STATE.*;

public class BufferListFragment extends Fragment implements BufferListEye, View.OnClickListener {

    final private static @Root Kitty kitty = Kitty.make("BLF");

    private WeechatActivity activity;
    private BufferListAdapter adapter;

    private RelativeLayout uiFilterBar;
    private EditText uiFilter;
    private ImageButton uiFilterClear;

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// lifecycle
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /** This makes sure that the container activity has implemented
     ** the callback interface. If not, it throws an exception. */
    @Override @Cat public void onAttach(Context context) {
        super.onAttach(context);
        this.activity = (WeechatActivity) context;
    }

    /** Supposed to be called only once
     ** since we are setting setRetainInstance(true) */
    @Override @Cat public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        adapter = new BufferListAdapter();
    }

    @Override @Cat public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bufferlist, container, false);
        RecyclerView uiRecycler = view.findViewById(R.id.recycler);
        uiRecycler.setAdapter(adapter);
        uiFilter = view.findViewById(R.id.bufferlist_filter);
        uiFilter.addTextChangedListener(filterTextWatcher);
        uiFilterClear = view.findViewById(R.id.bufferlist_filter_clear);
        uiFilterClear.setOnClickListener(this);
        uiFilterBar = view.findViewById(R.id.filter_bar);
        return view;
    }

    @Override @Cat public void onDestroyView() {
        super.onDestroyView();
        uiFilter.removeTextChangedListener(filterTextWatcher);
    }

    @Override @Cat public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
        uiFilterBar.setVisibility(P.showBufferFilter ? View.VISIBLE : View.GONE);
    }

    @Override @Cat public void onStop() {
        EventBus.getDefault().unregister(this);
        super.onStop();
        detachFromBufferList();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// event
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Subscribe(sticky = true)
    @Cat public void onEvent(Events.StateChangedEvent event) {
        if (event.state.contains(LISTED)) attachToBufferList();
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// the juice

    private void attachToBufferList() {
        BufferList.setBufferListEye(this);
        setFilter(uiFilter.getText().toString());
        onBuffersChanged();
    }

    private void detachFromBufferList() {
        BufferList.setBufferListEye(null);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// BufferListEye
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override @Cat public void onBuffersChanged() {
        adapter.onBuffersChanged();
    }

    @Override @Cat public void onHotCountChanged() {
        activity.updateHotCount(BufferList.getHotCount());
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// other
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /** TextWatcher object used for filtering the buffer list */
    private TextWatcher filterTextWatcher = new TextWatcher() {
        @Override public void afterTextChanged(Editable a) {}
        @Override public void beforeTextChanged(CharSequence arg0, int a, int b, int c) {}

        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (adapter != null) {
                setFilter(s.toString());
                adapter.onBuffersChanged();
            }
        }
    };


    private void setFilter(final String s) {
        adapter.setFilter(s);
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                uiFilterClear.setVisibility((s.length() == 0) ? View.INVISIBLE : View.VISIBLE);
            }
        });
    }

    /** the only button we've got: clear text in the filter */
    @Override
    public void onClick(View v) {
        uiFilter.setText(null);
    }

    @Override public String toString() {
        return "BL";
    }
}
