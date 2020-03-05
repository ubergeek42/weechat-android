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
import com.bumptech.glide.load.HttpException;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.util.Preconditions;
import com.ubergeek42.cats.Cat;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;

import java.io.IOException;
import java.io.InputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static com.ubergeek42.WeechatAndroid.media.Exceptions.*;
import static com.ubergeek42.WeechatAndroid.utils.Utils.readInputStream;

public class OkHttpStreamFetcher implements DataFetcher<InputStream> {
    final private static @Root Kitty kitty = Kitty.make();

    final private Call.Factory client;
    final private StrategyUrl strategyUrl;
    final private Strategy strategy;

    private DataCallback<? super InputStream> callback;

    volatile private CallHandler lastCallHandler;

    OkHttpStreamFetcher(Call.Factory client, StrategyUrl strategyUrl) {
        this.client = client;
        this.strategyUrl = strategyUrl;
        this.strategy = strategyUrl.getStrategy();
    }

    @Override @Cat public void loadData(@NonNull Priority priority, @NonNull DataCallback<? super InputStream> callback) {
        this.callback = callback;
        String requestUrl = strategy.getRequestUrl(strategyUrl.getOriginalUrl());
        fire(requestUrl, strategy.requestType());
    }

    private void fire(String url, RequestType requestType) {
        lastCallHandler = new CallHandler(url, requestType);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private class CallHandler implements Callback {
        final private @Root Kitty handler_kitty = kitty.kid("CallHandler");

        final private Call call;
        final private RequestType requestType;

        private ResponseBody body;

        @Cat CallHandler(String stringUrl, RequestType requestType) {
            this.requestType = requestType;
            Request.Builder builder = new Request.Builder()
                    .url(stringUrl)
                    .header("Accept", requestType.getAcceptHeader());
            if (requestType == RequestType.HTML && strategy.wantedHtmlBodySize() > 0)
                builder.header("Range", "bytes=0-" + strategy.wantedHtmlBodySize());
            call = client.newCall(builder.build());
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
                throw new HttpException(response.message(), response.code());

            long contentLength = body.contentLength();
            if (contentLength > Engine.MAXIMUM_BODY_SIZE)
                throw new ContentLengthExceedsLimitException(contentLength, Engine.MAXIMUM_BODY_SIZE);

            InputStream stream = new LimitedLengthInputStream(body.byteStream(), contentLength, Engine.MAXIMUM_BODY_SIZE);

            MediaType responseType = body.contentType();
            if (!requestType.matches(responseType))
                throw new UnacceptableMediaTypeException(requestType, responseType);

            if ("html".equals(responseType.subtype())) {
                CharSequence html = readInputStream(stream, strategy.wantedHtmlBodySize());

                String newRequestUrl = strategy.getRequestUrlFromBody(html);
                if (newRequestUrl == null)
                    throw new HtmlBodyLacksRequiredDataException(html);

                body.close();
                fire(newRequestUrl, RequestType.IMAGE);
            } else {
                callback.onDataReady(stream);               // body will be closed by the callback
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override @Cat public void cleanup() {
        if (lastCallHandler != null && lastCallHandler.body != null) lastCallHandler.body.close();
    }

    @Override @Cat public void cancel() {
        if (lastCallHandler != null) lastCallHandler.call.cancel();
    }

    @NonNull @Override public Class<InputStream> getDataClass() {
        return InputStream.class;
    }

    @NonNull @Override public DataSource getDataSource() {
        return DataSource.REMOTE;
    }
}