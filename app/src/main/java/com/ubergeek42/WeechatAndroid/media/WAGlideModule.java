package com.ubergeek42.WeechatAndroid.media;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.module.AppGlideModule;
import com.ubergeek42.WeechatAndroid.upload.NonImageUriLoader;

import java.io.InputStream;

import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_IMAGE_DISK_CACHE_SIZE;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_IMAGE_DISK_CACHE_SIZE_D;

@GlideModule public class WAGlideModule extends AppGlideModule {
    @Override public void registerComponents(@NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
        registry.replace(Strategy.Url.class, InputStream.class, new OkHttpUrlLoader.Factory());
        registry.append(Uri.class, Bitmap.class, new NonImageUriLoader.Factory());
    }

    @Override public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        String cacheSizeMb = p.getString(PREF_IMAGE_DISK_CACHE_SIZE, PREF_IMAGE_DISK_CACHE_SIZE_D);
        long cacheSize = (long) (Float.parseFloat(cacheSizeMb) * 1000 * 1000);
        builder.setDiskCache(new InternalCacheDiskCacheFactory(context, cacheSize));
    }
}
