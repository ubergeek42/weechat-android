package com.ubergeek42.WeechatAndroid.media;

import android.text.style.URLSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.MultiTransformation;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.ubergeek42.cats.Cat;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static com.ubergeek42.WeechatAndroid.media.HostUtils.getHost;

public class Engine {
    final private static @Root Kitty kitty = Kitty.make();

    final static int MAXIMUM_BODY_SIZE = 5 * 1024 * 1024;

    final public static RequestOptions defaultRequestOptions = new RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .transform(new MultiTransformation<>(new CenterCrop(), new RoundedCorners(16)));

    private static final HashMap<String, Strategy> strategies = new HashMap<>();

    private static void registerStrategy(Strategy... strategies) {
        for (Strategy strategy : strategies) {
            for (String host : strategy.getHosts()) {
                Engine.strategies.put(host, strategy);
            }
        }
    }

    static {
        //noinspection SpellCheckingInspection
        registerStrategy(
                new StrategyRegex(
                        "Youtube",
                        Arrays.asList("www.youtube.com", "m.youtube.com", "youtube.com", "youtu.be"),
                        "https?://" +
                                "(?:" +
                                "(?:www\\.|m\\.)?youtube\\.com/watch\\?v=" +
                                "|" +
                                "youtu\\.be/" +
                                ")" +
                                "([A-Za-z0-9_-]+).*",
                        "https://img.youtube.com/vi/$1/mqdefault.jpg"),
                new StrategyOpenGraph(
                        "v.Reddit",
                        Collections.singletonList("v.redd.it"),
                        "https://v\\.redd\\.it/([a-z0-9]+)",
                        131072),
                new StrategyOpenGraph(
                        "Pikabu",
                        Arrays.asList("pikabu.ru", "www.pikabu.ru"),
                        "https://pikabu.ru/story/.+",
                        4096),
                new StrategyOpenGraph(
                        "Common OpenGraph",
                        Arrays.asList("*.wikipedia.org", "gfycat.com", "imgur.com"),
                        "https://.+",
                        4096 * 2),
                new StrategyRegex(
                        "Any image link to https",
                        Collections.singletonList("*"),
                        "https?://(.+)\\.(jpe?g|png|gif|webp)$",
                        "https://$1.$2")
        );
    }

    // given an url, return a StrategyUrl that it the best candidate to handle it
    private static @Nullable @Cat(exit=true) StrategyUrl getStrategyUrl(String url) {
        StrategyUrl strategyUrl = null;
        String host = getHost(url);
        kitty.debugl("host=%s", host);
        if (host != null) {
            for (String subHost : new HostUtils.HostIterable(host)) {
                kitty.debugl("subHost=%s", subHost);
                Strategy strategy = strategies.get(subHost);
                if (strategy != null) {
                    strategyUrl = StrategyUrl.make(strategy, url);
                    if (strategyUrl != null) break;
                }
            }
        }
        return strategyUrl;
    }

    public static @NonNull List<StrategyUrl> getPossibleMediaCandidates(@NonNull URLSpan[] urls) {
        List<StrategyUrl> candidates = new ArrayList<>();
        for (URLSpan url : urls) {
            StrategyUrl strategyUrl = getStrategyUrl(url.getURL());
            if (strategyUrl != null) candidates.add(strategyUrl);
        }
        return candidates;
    }
}
