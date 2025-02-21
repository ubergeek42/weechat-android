package com.ubergeek42.WeechatAndroid.media;

import static com.ubergeek42.WeechatAndroid.utils.ApplicationContextKt.applicationContext;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.Target;
import com.ubergeek42.WeechatAndroid.Weechat;
import com.ubergeek42.WeechatAndroid.utils.Linkify;
import com.ubergeek42.cats.Cat;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;

import java.io.File;

// fetch the url into a file and return a content uri via the callback
// see https://github.com/bumptech/glide/issues/459

public class ContentUriFetcher {
    final private static @Root Kitty kitty = Kitty.make();

    final public static String FILE_PROVIDER_SUFFIX = ".file_provider";

    public interface ContentUriReadyCallback {
        void onContentUriReady(Uri uri);
    }

    @Cat public static void loadFirstUrlFromText(CharSequence text, ContentUriReadyCallback callback) {
        Context context = applicationContext;
        String textUrl = Linkify.getFirstUrlFromString(text);
        if (textUrl == null) return;
        Strategy.Url strategyUrl = Engine.getStrategyUrl(textUrl, Strategy.Size.BIG);
        if (strategyUrl == null) return;

        new AsyncTask<String, Void, File>() {
            @Override protected File doInBackground(String... strings) {
                try {
                    return Glide
                            .with(context)
                            .downloadOnly()
                            .listener(Cache.fileListener)
                            .load(strategyUrl)
                            .onlyRetrieveFromCache(Engine.isDisabledForCurrentNetwork())
                            .submit(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
                            .get();
                } catch (Exception e) {
                    // glide prints warnings itself. todo print exception on non-debug builds?
                    // kitty.warn("Glide couldn't load %s", strategyUrl, e);
                    return null;
                }
            }

            @Override protected void onPostExecute(@Nullable File file) {
                if (file == null) return;
                Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + FILE_PROVIDER_SUFFIX, file);
                callback.onContentUriReady(uri);
            }
        }.execute();
    }
}
