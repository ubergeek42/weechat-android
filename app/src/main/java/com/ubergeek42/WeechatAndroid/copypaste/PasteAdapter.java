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
import com.ubergeek42.WeechatAndroid.media.Strategy;
import com.ubergeek42.cats.Cat;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;

import java.util.List;

import static com.ubergeek42.WeechatAndroid.media.WAGlideModule.isContextValidForGlide;

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
            Strategy.Url url = null;
            Cache.Info info = null;

            this.item = item;

            Context context = viewSwitcher.getContext();
            viewSwitcher.getChildAt(0).setBackgroundResource(item.isPaste ? R.drawable.bg_paste_dialog_clipboard : 0);
            viewSwitcher.getChildAt(1).setBackgroundResource(item.isPaste ? R.drawable.bg_paste_dialog_clipboard : 0);
            ImageView imageView = viewSwitcher.findViewById(R.id.image);
            ((TextView) viewSwitcher.findViewById(R.id.text1)).setText(item.text);
            ((TextView) viewSwitcher.findViewById(R.id.text2)).setText(item.text);

            if (Engine.isEnabledAtAll() && Engine.isEnabledForLocation(Engine.Location.PASTE)) {
                url = item.strategyUrl;
                if (url != null) info = Cache.info(item.strategyUrl);
            }

            viewSwitcher.reset(info == Cache.Info.FETCHED_RECENTLY ? 1 : 0);

            if (url == null || info == Cache.Info.FAILED_RECENTLY) {
                if (isContextValidForGlide(context)) {
                    Glide.with(context).clear(imageView);
                }
                return;
            }

            kitty.info("loading: %s", url);
            imageView.layout(0, 0, 0, 0);       // https://github.com/bumptech/glide/issues/1591
            imageView.requestLayout();
            if (isContextValidForGlide(context)) {
                Glide.with(context)
                        .asBitmap()
                        .apply(Engine.defaultRequestOptions)
                        .listener(Cache.bitmapListener)
                        .addListener(this)
                        .load(url)
                        .onlyRetrieveFromCache(Engine.isDisabledForCurrentNetwork())
                        .into(imageView);
            }
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
