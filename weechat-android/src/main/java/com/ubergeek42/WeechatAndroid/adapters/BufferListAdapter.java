/*******************************************************************************
 * Copyright 2012 Keith Johnson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.ubergeek42.WeechatAndroid.adapters;

import java.util.ArrayList;

import android.support.v7.app.AppCompatActivity;
import android.support.v7.util.DiffUtil;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.relay.Buffer;
import com.ubergeek42.WeechatAndroid.relay.BufferList;
import com.ubergeek42.WeechatAndroid.relay.BufferListEye;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BufferListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements BufferListEye {

    private static Logger logger = LoggerFactory.getLogger("BufferListAdapter");

    private AppCompatActivity activity;
    private ArrayList<Buffer> buffers = new ArrayList<>();

    final private static int[][] COLORS = new int[][] {
            {0xaa525252, 0xaa6c6c6c}, // other
            {0xaa44525f, 0xaa596c7d}, // channel
            {0xaa57474f, 0xaa735e69}, // private
    };
    
    public BufferListAdapter() {
        setHasStableIds(true);
    }

    public void attach(AppCompatActivity activity) {
        this.activity = activity;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// VH
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static class Row extends RecyclerView.ViewHolder implements View.OnClickListener {
        private String fullName;
        private TextView uiHot;
        private TextView uiWarm;
        private TextView uiBuffer;
        private View uiOpen;

        Row(View view) {
            super(view);
            uiOpen = view.findViewById(R.id.open);
            uiBuffer = view.findViewById(R.id.buffer);
            uiWarm = view.findViewById(R.id.buffer_warm);
            uiHot = view.findViewById(R.id.buffer_hot);
            view.setOnClickListener(this);
        }

        void update(Buffer buffer) {
            logger.trace("update {}", buffer.shortName);
            fullName = buffer.fullName;
            uiBuffer.setText(buffer.printableWithoutTitle);
            int unreads = buffer.unreads;
            int highlights = buffer.highlights;

            int important = (highlights > 0 || (unreads > 0 && buffer.type == Buffer.PRIVATE)) ? 1 : 0;
            uiBuffer.setBackgroundColor(COLORS[buffer.type][important]);
            uiOpen.setVisibility(buffer.isOpen ? View.VISIBLE : View.GONE);

            if (highlights > 0) {
                uiHot.setText(Integer.toString(highlights));
                uiHot.setVisibility(View.VISIBLE);
            } else
                uiHot.setVisibility(View.INVISIBLE);

            if (unreads > 0) {
                uiWarm.setText(Integer.toString(unreads));
                uiWarm.setVisibility(View.VISIBLE);
            } else
                uiWarm.setVisibility(View.GONE);
        }

        @Override public void onClick(View v) {
            ((BufferListClickListener) ((AppCompatActivity) v.getContext()).getSupportFragmentManager()
                    .findFragmentById(R.id.bufferlist_fragment)).onBufferClick(fullName);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// adapter methods
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        logger.trace("onCreateViewHolder({})", viewType);
        LayoutInflater i = LayoutInflater.from(parent.getContext());
        return new Row(i.inflate(R.layout.bufferlist_item, parent, false));
    }

    @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        logger.trace("onBindViewHolder(..., {}, {})", position);
        ((Row) holder).update(buffers.get(position));
    }

    @Override public long getItemId(int position) {
        logger.trace("getItemId({})", position);
        return buffers.get(position).pointer;
    }

    @Override public int getItemCount() {
        logger.trace("getItemCount() -> {}", buffers.size());
        return buffers.size();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// BufferListEye
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override synchronized public void onBuffersChanged() {
        logger.trace("onBuffersChanged()");
        if (activity == null) return;
        final ArrayList<Buffer> newBuffers = BufferList.getBufferList();
        logger.trace("onBuffersChanged() {} -> {}", buffers.size(), newBuffers.size());
        final DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffCallback(buffers, newBuffers), true);
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                logger.trace("...proceeding {}", BufferListAdapter.this.hasObservers());
                buffers = newBuffers;
                diffResult.dispatchUpdatesTo(BufferListAdapter.this);
            }
        });
    }

    @Override public void onHotCountChanged() {}

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// Diff
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static class Wrapper {
        Buffer buffer;
        Object printableWithoutTitle;
        boolean isOpen;
        int highlights, unreads;

        public Wrapper(Buffer buffer) {
            this.buffer = buffer;
            isOpen = buffer.isOpen;
            printableWithoutTitle = buffer.printableWithoutTitle;
            highlights = buffer.highlights;
            unreads = buffer.unreads;
        }
    }

    private class DiffCallback extends DiffUtil.Callback {

        private ArrayList<Buffer> oldBuffers, newBuffers;

        DiffCallback(ArrayList<Buffer> oldBuffers, ArrayList<Buffer> newBuffers) {
            this.oldBuffers = oldBuffers;
            this.newBuffers = newBuffers;
        }
        @Override public int getOldListSize() {
            return oldBuffers.size();
        }

        @Override public int getNewListSize() {
            return newBuffers.size();
        }

        @Override public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldBuffers.get(oldItemPosition).pointer == newBuffers.get(newItemPosition).pointer;
        }

        @Override public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            return false;
        }
    }
}
