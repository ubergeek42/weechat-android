package com.ubergeek42.WeechatAndroid.media;

import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import static com.ubergeek42.WeechatAndroid.utils.Assert.assertThat;

class OkHttpSecuringInterceptor implements Interceptor {
    final private static @Root Kitty kitty = Kitty.make();

    @NotNull @Override public Response intercept(@NotNull Chain chain) throws IOException {
        assertThat(Config.secureRequestsPolicy).isAnyOf(Config.SecureRequest.REQUIRED, Config.SecureRequest.REWRITE);
        Request request = chain.request();

        if ("http".equals(request.url().scheme())) {
            if (Config.secureRequestsPolicy == Config.SecureRequest.REQUIRED) {
                throw new Exceptions.SslRequiredException();
            } else {
                request = request.newBuilder().url(
                        request.url().newBuilder().scheme("https").build()).build();
            }
        }

        return chain.proceed(request);
    }
}
