// Copyright 2012 Keith Johnson
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.ubergeek42.WeechatAndroid.adapters;

import android.support.annotation.AnyThread;
import android.support.annotation.MainThread;
import android.support.v7.util.DiffUtil;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.text.Spannable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.Weechat;
import com.ubergeek42.WeechatAndroid.relay.Buffer;
import com.ubergeek42.WeechatAndroid.relay.BufferList;
import com.ubergeek42.WeechatAndroid.relay.BufferListEye;
import com.ubergeek42.WeechatAndroid.service.P;
import com.ubergeek42.WeechatAndroid.utils.Utils;
import com.ubergeek42.cats.Cat;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;


public class BufferListAdapter extends RecyclerView.Adapter<ViewHolder> implements BufferListEye {

    final private static @Root Kitty kitty = Kitty.make();

    private ArrayList<VisualBuffer> buffers = new ArrayList<>();

    final private static int[][] COLORS = new int[][] {
            {0xaa525252, 0xaa6c6c6c}, // other
            {0xaa44525f, 0xaa596c7d}, // channel
            {0xaa57474f, 0xaa735e69}, // private
    };

    public BufferListAdapter() {
        // if setHasStableIds(true) is called here, RecyclerView will play move animations
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// VH
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static class Row extends ViewHolder implements View.OnClickListener {
        private String fullName;
        private TextView uiHot;
        private TextView uiWarm;
        private TextView uiBuffer;
        private View uiOpen;

        @MainThread Row(View view) {
            super(view);
            uiOpen = view.findViewById(R.id.open);
            uiBuffer = view.findViewById(R.id.buffer);
            uiWarm = view.findViewById(R.id.buffer_warm);
            uiHot = view.findViewById(R.id.buffer_hot);
            view.setOnClickListener(this);
        }

        @MainThread void update(VisualBuffer buffer) {
            fullName = buffer.fullName;
            uiBuffer.setText(buffer.printable);
            int unreads = buffer.unreads;
            int highlights = buffer.highlights;

            int important = (highlights > 0 || (unreads > 0 && buffer.type == Buffer.PRIVATE)) ? 1 : 0;
            uiBuffer.setBackgroundColor(COLORS[buffer.type][important]);
            uiOpen.setVisibility(buffer.isOpen ? View.VISIBLE : View.GONE);

            if (highlights > 0) {
                uiHot.setText(String.valueOf(highlights));
                uiHot.setVisibility(View.VISIBLE);
            } else
                uiHot.setVisibility(View.INVISIBLE);

            if (unreads > 0) {
                uiWarm.setText(String.valueOf(unreads));
                uiWarm.setVisibility(View.VISIBLE);
            } else
                uiWarm.setVisibility(View.GONE);
        }

        @MainThread @Override @SuppressWarnings("ConstantConditions")
        public void onClick(View v) {
            ((BufferListClickListener) Utils.getActivity(v)).onBufferClick(fullName);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// adapter methods
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @MainThread @Override @Cat("???") public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater i = LayoutInflater.from(parent.getContext());
        return new Row(i.inflate(R.layout.bufferlist_item, parent, false));
    }

    @MainThread @Override @Cat("???") public void onBindViewHolder(ViewHolder holder, int position) {
        ((Row) holder).update(buffers.get(position));
    }

    @MainThread @Override @Cat("???") public long getItemId(int position) {
        return buffers.get(position).pointer;
    }

    @MainThread @Override @Cat(value="???", exit=true) public int getItemCount() {
        return buffers.size();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// BufferListEye
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private ArrayList<VisualBuffer> _buffers = new ArrayList<>();
    @AnyThread @Override @Cat("??") synchronized public void onBuffersChanged() {
        final ArrayList<VisualBuffer> newBuffers = new ArrayList<>();

        synchronized (BufferList.class) {
            for (Buffer buffer : BufferList.buffers) {
                if (buffer.type == Buffer.HARD_HIDDEN) continue;
                if (P.filterBuffers && buffer.type == Buffer.OTHER && buffer.highlights == 0 && buffer.unreads == 0) continue;
                if (!buffer.fullName.toLowerCase().contains(P.filterLc) && !buffer.fullName.toUpperCase().contains(P.filterUc)) continue;
                newBuffers.add(new VisualBuffer(buffer));
            }
        }

        if (P.sortBuffers) Collections.sort(newBuffers, sortByHotAndMessageCountComparator);
        else Collections.sort(newBuffers, sortByHotCountAndNumberComparator);

        // store new buffers in _buffers for the sole purpose of doing a diff against, since
        // this method might be called again before buffers is assigned
        final DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffCallback(_buffers, newBuffers), false);
        _buffers = newBuffers;

        Weechat.runOnMainThread(() -> {
            buffers = newBuffers;
            diffResult.dispatchUpdatesTo(BufferListAdapter.this);
        });
    }

    @AnyThread synchronized public static void setFilter(final String s) {
        P.filterLc = s.toLowerCase();
        P.filterUc = s.toUpperCase();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// Diff
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static class VisualBuffer {
        String fullName;
        Spannable printable;
        boolean isOpen;
        int highlights, unreads, type, number;
        long pointer;

        VisualBuffer(Buffer buffer) {
            fullName = buffer.fullName;
            isOpen = buffer.isOpen;
            printable = buffer.printable;
            highlights = buffer.highlights;
            unreads = buffer.unreads;
            type = buffer.type;
            number = buffer.number;
            pointer = buffer.pointer;
        }
    }

    private static class DiffCallback extends DiffUtil.Callback {
        private ArrayList<VisualBuffer> oldBuffers, newBuffers;

        DiffCallback(ArrayList<VisualBuffer> oldBuffers, ArrayList<VisualBuffer> newBuffers) {
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
            VisualBuffer o = oldBuffers.get(oldItemPosition);
            VisualBuffer n = newBuffers.get(newItemPosition);
            return o.printable.equals(n.printable) &&
                    o.isOpen == n.isOpen &&
                    o.highlights == n.highlights &&
                    o.unreads == n.unreads;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    static private final Comparator<VisualBuffer> sortByHotCountAndNumberComparator = (left, right) -> {
        int l, r;
        if ((l = left.highlights) != (r = right.highlights)) return r - l;
        if ((l = left.type == Buffer.PRIVATE ? left.unreads : 0) !=
                (r = right.type == Buffer.PRIVATE ? right.unreads : 0)) return r - l;
        return left.number - right.number;
    };

    static private final Comparator<VisualBuffer> sortByHotAndMessageCountComparator = (left, right) -> {
        int l, r;
        if ((l = left.highlights) != (r = right.highlights)) return r - l;
        if ((l = left.type == Buffer.PRIVATE ? left.unreads : 0) !=
                (r = right.type == Buffer.PRIVATE ? right.unreads : 0)) return r - l;
        return right.unreads - left.unreads;
    };
}
