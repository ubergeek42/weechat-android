package com.ubergeek42.WeechatAndroid.fragments;

import android.support.annotation.AnyThread;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

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

import com.ubergeek42.WeechatAndroid.Weechat;
import com.ubergeek42.WeechatAndroid.adapters.BufferListAdapter;
import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.WeechatActivity;
import com.ubergeek42.WeechatAndroid.relay.BufferList;
import com.ubergeek42.WeechatAndroid.relay.BufferListEye;
import com.ubergeek42.WeechatAndroid.relay.Hotlist;
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

    private RecyclerView uiRecycler;
    private RelativeLayout uiFilterBar;
    private EditText uiFilter;
    private ImageButton uiFilterClear;

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// lifecycle
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @MainThread @Override @Cat public void onAttach(Context context) {
        super.onAttach(context);
        this.activity = (WeechatActivity) context;
    }

    @MainThread @Override @Cat public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        adapter = new BufferListAdapter();
    }

    @MainThread @Override @Cat public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bufferlist, container, false);
        uiRecycler = view.findViewById(R.id.recycler);
        uiRecycler.setAdapter(adapter);
        uiFilter = view.findViewById(R.id.bufferlist_filter);
        uiFilter.addTextChangedListener(filterTextWatcher);
        uiFilterClear = view.findViewById(R.id.bufferlist_filter_clear);
        uiFilterClear.setOnClickListener(this);
        uiFilterBar = view.findViewById(R.id.filter_bar);
        return view;
    }

    @MainThread @Override @Cat public void onDestroyView() {
        super.onDestroyView();
        uiFilter.removeTextChangedListener(filterTextWatcher);
    }

    @MainThread @Override @Cat public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
        uiFilterBar.setVisibility(P.showBufferFilter ? View.VISIBLE : View.GONE);
    }

    @MainThread @Override @Cat public void onStop() {
        super.onStop();
        detachFromBufferList();
        EventBus.getDefault().unregister(this);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// event
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Subscribe(sticky=true)
    @AnyThread @Cat public void onEvent(Events.StateChangedEvent event) {
        if (event.state.contains(LISTED)) attachToBufferList();
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// the juice

    @AnyThread private void attachToBufferList() {
        BufferList.setBufferListEye(this);
        setFilter();
        onBuffersChanged();
    }

    @MainThread private void detachFromBufferList() {
        BufferList.setBufferListEye(null);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// BufferListEye
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @AnyThread @Override @Cat public void onBuffersChanged() {
        adapter.onBuffersChanged();
        int hotCount = Hotlist.getHotCount();
        Weechat.runOnMainThread(() -> {
            if (hotCount > 0) uiRecycler.smoothScrollToPosition(0);
            activity.updateHotCount(hotCount);
        });
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// other
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private TextWatcher filterTextWatcher = new TextWatcher() {
        @MainThread @Override public void afterTextChanged(Editable a) {}
        @MainThread @Override public void beforeTextChanged(CharSequence arg0, int a, int b, int c) {}

        @MainThread @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (adapter != null) {
                setFilter();
                adapter.onBuffersChanged();
            }
        }
    };


    @AnyThread private void setFilter() {
        String text = uiFilter.getText().toString();
        BufferListAdapter.setFilter(text);
        Weechat.runOnMainThread(() -> uiFilterClear.setVisibility((text.length() == 0) ?
                View.INVISIBLE : View.VISIBLE));
    }

    // the only button we've got: clear text in the filter
    @MainThread @Override public void onClick(View v) {
        uiFilter.setText(null);
    }

    @Override public String toString() {
        return "BL";
    }
}
