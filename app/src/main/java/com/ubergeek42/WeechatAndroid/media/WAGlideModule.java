package com.ubergeek42.WeechatAndroid.media;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.module.AppGlideModule;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;

import java.io.InputStream;

import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_IMAGE_DISK_CACHE_SIZE;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_IMAGE_DISK_CACHE_SIZE_D;

@GlideModule public class WAGlideModule extends AppGlideModule {
    final private static @Root Kitty kitty = Kitty.make();

    @Override public void registerComponents(@NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
        registry.replace(Strategy.Url.class, InputStream.class, new OkHttpUrlLoader.Factory());
    }

    @Override public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        String cacheSizeMb = p.getString(PREF_IMAGE_DISK_CACHE_SIZE, PREF_IMAGE_DISK_CACHE_SIZE_D);
        long cacheSize = (long) (Float.parseFloat(cacheSizeMb) * 1000 * 1000);
        builder.setDiskCache(new InternalCacheDiskCacheFactory(context, cacheSize));
    }

    // this is used to prevent random crashes when trying to load images while the activity somehow
    // got destroyed.
    // see: https://github.com/ubergeek42/weechat-android/issues/470
    // see: https://github.com/bumptech/glide/issues/803
    public static boolean isContextValidForGlide(@Nullable Context context) {
        if (context == null) {
            kitty.warn("isContextValidForGlide(): null context!");
            return false;
        }

        if (context instanceof Activity) {
            boolean finishing = ((Activity) context).isFinishing();
            boolean destroyed = ((Activity) context).isDestroyed();

            if (finishing || destroyed) {
                kitty.warn("isContextValidForGlide(): activity in a bad state! " +
                           "finishing: %s, destroyed: %s ", finishing, destroyed);
                return false;
            }
        }

        return true;
    }
}
