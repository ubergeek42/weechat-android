package com.ubergeek42.WeechatAndroid.copypaste;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.media.Cache;
import com.ubergeek42.WeechatAndroid.media.Engine;
import com.ubergeek42.cats.Cat;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;

import java.util.List;

public class PasteAdapter extends RecyclerView.Adapter<PasteAdapter.PasteLine> {
    final private static  @Root Kitty kitty = Kitty.make();

    final private LayoutInflater inflater;
    final private OnClickListener onClickListener;
    final private List<Paste.PasteItem> items;

    PasteAdapter(Context context, List<Paste.PasteItem> items, OnClickListener onClickListener) {
        this.inflater = LayoutInflater.from(context);
        this.onClickListener = onClickListener;
        this.items = items;
    }

    interface OnClickListener {
        void onClick(Paste.PasteItem item);
    }

    class PasteLine extends RecyclerView.ViewHolder implements RequestListener<Bitmap>, View.OnClickListener  {
        final FancyViewSwitcher viewSwitcher;
        Paste.PasteItem item;

        PasteLine(@NonNull FancyViewSwitcher viewSwitcher) {
            super(viewSwitcher);
            this.viewSwitcher = viewSwitcher;
            viewSwitcher.setOnClickListener((View v) -> onClickListener.onClick(item));
        }

        void setText(Paste.PasteItem item) {
            this.item = item;

            Context context = viewSwitcher.getContext();
            // viewSwitcher.setBackgroundResource(item.isPaste ? R.color.pasteBackground : 0);
            ImageView imageView = viewSwitcher.findViewById(R.id.image);
            ((TextView) viewSwitcher.findViewById(R.id.text1)).setText(item.text);
            ((TextView) viewSwitcher.findViewById(R.id.text2)).setText(item.text);

            Cache.Info info = item.strategyUrl == null ? null : Cache.info(item.strategyUrl);
            viewSwitcher.reset(info == Cache.Info.FETCHED_RECENTLY ? 1 : 0);

            if (item.strategyUrl == null || info == Cache.Info.FAILED_RECENTLY) {
                Glide.with(context).clear(imageView);
                return;
            }

            kitty.info("loading: %s", item.strategyUrl);
            imageView.layout(0, 0, 0, 0);       // https://github.com/bumptech/glide/issues/1591
            imageView.requestLayout();
            Glide.with(context)
                    .asBitmap()
                    .apply(Engine.defaultRequestOptions)
                    .listener(Cache.listener)
                    .addListener(this)
                    .load(item.strategyUrl)
                    .into(imageView);
        }

        @Cat @Override public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Bitmap> target, boolean isFirstResource) {
            viewSwitcher.crossfadeTo(0);
            return false;
        }

        @Cat @Override public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target, DataSource dataSource, boolean isFirstResource) {
            viewSwitcher.crossfadeTo(1);
            return false;
        }

        @Override public void onClick(View v) {
            onClickListener.onClick(item);
        }
    }

    @NonNull @Override public PasteLine onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        FancyViewSwitcher view = (FancyViewSwitcher) inflater.inflate(R.layout.dialog_paste_line, parent, false);
        return new PasteLine(view);
    }

    @Override public void onBindViewHolder(@NonNull PasteLine holder, int position) {
        holder.setText(items.get(position));
    }

    @Override public int getItemCount() {
        return items.size();
    }
}
