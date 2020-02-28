package com.ubergeek42.WeechatAndroid.media;

import android.content.Context;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.module.AppGlideModule;

import java.io.InputStream;

@GlideModule public class WAGlideModule extends AppGlideModule {
    @Override public void registerComponents(@NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
        registry.replace(StrategyUrl.class, InputStream.class, new OkHttpUrlLoader.Factory());
    }
}
