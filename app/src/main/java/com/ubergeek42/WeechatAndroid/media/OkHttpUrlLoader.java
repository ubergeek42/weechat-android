package com.ubergeek42.WeechatAndroid.media;

// this boilerplate class is mostly copied from Glide
// https://github.com/bumptech/glide/blob/master/integration/okhttp3/src/main/java/com/bumptech/glide/integration/okhttp3/OkHttpUrlLoader.java

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

import androidx.annotation.NonNull;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;
import com.ubergeek42.cats.Cat;

import java.io.InputStream;
import okhttp3.Call;
import okhttp3.OkHttpClient;

public class OkHttpUrlLoader implements ModelLoader<StrategyUrl, InputStream> {
    private final Call.Factory client;

    private OkHttpUrlLoader(@NonNull Call.Factory client) {
        this.client = client;
    }

    @Override @Cat public boolean handles(@NonNull StrategyUrl url) {
        return true;
    }

    @Override public @Cat LoadData<InputStream> buildLoadData(@NonNull StrategyUrl model, int width, int height, @NonNull Options options) {
        return new LoadData<>(model, new OkHttpStreamFetcher(client, model));
    }

    public static class Factory implements ModelLoaderFactory<StrategyUrl, InputStream> {
        private static volatile Call.Factory internalClient;
        private final Call.Factory client;

        private static Call.Factory getInternalClient() {
            if (internalClient == null) {
                synchronized (Factory.class) {
                    if (internalClient == null) {
                        internalClient = new OkHttpClient();
                    }
                }
            }
            return internalClient;
        }

        Factory() {
            this(getInternalClient());
        }

        Factory(@NonNull Call.Factory client) {
            this.client = client;
        }

        @NonNull @Override public ModelLoader<StrategyUrl, InputStream> build(@NonNull MultiModelLoaderFactory multiFactory) {
            return new OkHttpUrlLoader(client);
        }

        @Override public void teardown() {}
    }
}
