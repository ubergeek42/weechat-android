package com.ubergeek42.WeechatAndroid.media;

// this  class is a modified version of the same class from the sources of Glide
// https://github.com/bumptech/glide/blob/master/integration/okhttp3/src/main/java/com/bumptech/glide/integration/okhttp3/OkHttpStreamFetcher.java

// Copyright 2014 Google, Inc. All rights reserved.

// Redistribution and use in source and binary forms, with or without modification, are
// permitted provided that the following conditions are met:

// 1. Redistributions of source code must retain the above copyright notice, this list of
// conditions and the following disclaimer.

// 2. Redistributions in binary form must reproduce the above copyright notice, this list
// of conditions and the following disclaimer in the documentation and/or other materials
// provided with the distribution.

// THIS SOFTWARE IS PROVIDED BY GOOGLE, INC. ``AS IS'' AND ANY EXPRESS OR IMPLIED
// WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
// FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL GOOGLE, INC. OR
// CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
// SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
// ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
// NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
// ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

// note that when using GlideUrl, calling onLoadFailed results in an additional call, see
// https://github.com/bumptech/glide/issues/2943 -- something to potentially worry about?

import androidx.annotation.NonNull;

import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.util.Preconditions;
import com.ubergeek42.cats.Cat;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;

import java.io.IOException;
import java.io.InputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static com.ubergeek42.WeechatAndroid.media.Exceptions.ContentLengthExceedsLimitException;
import static com.ubergeek42.WeechatAndroid.media.Exceptions.HttpException;

public class OkHttpStreamFetcher implements DataFetcher<InputStream> {
    final private static @Root Kitty kitty = Kitty.make();

    private final static Call.Factory regularClient = new OkHttpClient.Builder()
            .addNetworkInterceptor(new OkHttpNullifyingInterceptor())
            .build();
    private final static Call.Factory sslOnlyClient = new OkHttpClient.Builder()
            .followSslRedirects(false)
            .addNetworkInterceptor(new OkHttpNullifyingInterceptor())
            .addInterceptor(new OkHttpSecuringInterceptor())
            .build();

    final private Strategy.Url strategyUrl;

    private DataCallback<? super InputStream> callback;

    volatile private CallHandler lastCallHandler;

    OkHttpStreamFetcher(Strategy.Url strategyUrl) {
        this.strategyUrl = strategyUrl;
    }

    @Override @Cat public void loadData(@NonNull Priority priority, @NonNull DataCallback<? super InputStream> callback) {
        this.callback = callback;
        try {
            fire(strategyUrl.getFirstRequest());
        } catch (IOException e) {
            callback.onLoadFailed(e);
        }
    }

    private void fire(Request request) {
        lastCallHandler = new CallHandler(request);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private class CallHandler implements Callback {
        final private Call call;

        private ResponseBody body;

        CallHandler(Request request) {
            Call.Factory client = Config.secureRequestsPolicy == Config.SecureRequest.OPTIONAL ?
                    regularClient : sslOnlyClient;
            call = client.newCall(request);
            call.enqueue(this);
        }

        @Override @Cat public void onFailure(@NonNull Call call, @NonNull IOException e) {
            callback.onLoadFailed(e);
        }

        @Override @Cat public void onResponse(@NonNull Call call, Response response)  {
            body = Preconditions.checkNotNull(response.body());         // must be closed!

            try {
                processBody(response);
            } catch (IOException e) {
                callback.onLoadFailed(e);
                body.close();
            }
        }

        // contentLength will be -1 if not present in the request. it's often missing if the
        // request is served with gzip, as the resulting file size might not be known in advance
        private void processBody(Response response) throws IOException {
            if (!response.isSuccessful())
                throw new HttpException(response.code(), response.message());

            long contentLength = body.contentLength();
            if (contentLength > Config.maximumBodySize)
                throw new ContentLengthExceedsLimitException(contentLength, Config.maximumBodySize);

            InputStream stream = new LimitedLengthInputStream(body.byteStream(),
                    contentLength,  Config.maximumBodySize);

            Request nextRequest = strategyUrl.getNextRequest(response, stream);
            if (nextRequest == null) {
                callback.onDataReady(stream);
            } else {
                fire(nextRequest);
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override public void cleanup() {
        if (lastCallHandler != null && lastCallHandler.body != null) lastCallHandler.body.close();
    }

    @Override public void cancel() {
        if (lastCallHandler != null) lastCallHandler.call.cancel();
    }

    @NonNull @Override public Class<InputStream> getDataClass() {
        return InputStream.class;
    }

    @NonNull @Override public DataSource getDataSource() {
        return DataSource.REMOTE;
    }
}