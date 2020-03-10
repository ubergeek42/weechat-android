package com.ubergeek42.WeechatAndroid.copypaste;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.ubergeek42.WeechatAndroid.R;

import java.util.List;

public class CopyAdapter extends RecyclerView.Adapter<CopyAdapter.CopyLine> {
    final private LayoutInflater inflater;
    final private AlertDialog dialog;
    final private List<String> items;

    CopyAdapter(Context context, AlertDialog dialog, List<String> items) {
        this.inflater = LayoutInflater.from(context);
        this.dialog = dialog;
        this.items = items;
    }

    class CopyLine extends RecyclerView.ViewHolder implements View.OnClickListener {
        final TextView textView;

        CopyLine(@NonNull TextView textView) {
            super(textView);
            this.textView = textView;
            textView.setOnClickListener(this);
        }

        void setText(String text) {
            textView.setText(text);
        }

        @Override public void onClick(View v) {
            Copy.setClipboard(textView.getContext(), textView.getText());
            dialog.dismiss();
        }
    }

    @NonNull @Override public CopyLine onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TextView view = (TextView) inflater.inflate(R.layout.select_dialog_item_material_2_lines, parent, false);
        return new CopyLine(view);
    }

    @Override public void onBindViewHolder(@NonNull CopyLine holder, int position) {
        holder.setText(items.get(position));
    }

    @Override public int getItemCount() {
        return items.size();
    }
}
