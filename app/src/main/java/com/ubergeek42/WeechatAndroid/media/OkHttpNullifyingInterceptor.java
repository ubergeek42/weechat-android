package com.ubergeek42.WeechatAndroid.media;

import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

class OkHttpNullifyingInterceptor implements Interceptor {
    final private static @Root Kitty kitty = Kitty.make();

    @NotNull @Override public Response intercept(@NotNull Chain chain) throws IOException {
        Request request = chain.request();

        if (Engine.hasNullStrategyFor(request.url()))
            throw new Exceptions.RedirectToNullStrategyException(request.url().toString());

        return chain.proceed(request);
    }
}
