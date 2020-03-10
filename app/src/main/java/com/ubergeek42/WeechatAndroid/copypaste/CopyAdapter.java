package com.ubergeek42.WeechatAndroid.copypaste;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ubergeek42.WeechatAndroid.R;

import java.util.List;

public class CopyAdapter extends RecyclerView.Adapter<CopyAdapter.CopyLine> {
    final private LayoutInflater inflater;
    final private OnClickListener onClickListener;
    final private List<CharSequence> items;

    CopyAdapter(Context context, List<CharSequence> items, OnClickListener onClickListener) {
        this.inflater = LayoutInflater.from(context);
        this.onClickListener = onClickListener;
        this.items = items;
    }

    interface OnClickListener {
        void onClick(String item);
    }

    class CopyLine extends RecyclerView.ViewHolder implements View.OnClickListener {
        final TextView textView;

        CopyLine(@NonNull TextView textView) {
            super(textView);
            this.textView = textView;
            textView.setOnClickListener(this);
        }

        void setText(CharSequence text) {
            textView.setText(text);
        }

        @Override public void onClick(View v) {
            onClickListener.onClick(textView.getText().toString());
        }
    }

    @NonNull @Override public CopyLine onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TextView view = (TextView) inflater.inflate(R.layout.dialog_copy_line, parent, false);
        return new CopyLine(view);
    }

    @Override public void onBindViewHolder(@NonNull CopyLine holder, int position) {
        holder.setText(items.get(position));
    }

    @Override public int getItemCount() {
        return items.size();
    }
}
