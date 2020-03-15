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

    final public static int ANIMATION_DURATION = 500;          // ms

    final public static int THUMBNAIL_WIDTH = 250;
    final public static int THUMBNAIL_MIN_HEIGHT = 120;        // todo set to the height of 2 lines of text?
    final public static int THUMBNAIL_MAX_HEIGHT = THUMBNAIL_WIDTH * 2;
    final public static int THUMBNAIL_HORIZONTAL_MARGIN = 8;
    final public static int THUMBNAIL_VERTICAL_MARGIN = 4;

    final static long MAXIMUM_BODY_SIZE = 5 * 1024 * 1024;

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
                        "youtube",
                        Arrays.asList("www.youtube.com", "m.youtube.com", "youtube.com", "youtu.be"),
                        "https?://" +
                                "(?:" +
                                "(?:www\\.|m\\.)?youtube\\.com/watch\\?v=" +
                                "|" +
                                "youtu\\.be/" +
                                ")" +
                                "([A-Za-z0-9_-]+).*",
                        "https://img.youtube.com/vi/$1/mqdefault.jpg",
                        "https://img.youtube.com/vi/$1/hqdefault.jpg"),
                new StrategyRegex(
                        "i.imgur.com",
                        Collections.singletonList("i.imgur.com"),
                        "https?://i.imgur.com/([^.]+).*",
                        "https://i.imgur.com/$1m.jpg",
                        "https://i.imgur.com/$1h.jpg"),
                new StrategyRegex(
                        "9gag.com",
                        Collections.singletonList("9gag.com"),
                        "https?://9gag\\.com/gag/([^_]+)",
                        "https://images-cdn.9gag.com/photo/$1_700b.jpg",
                        "https://images-cdn.9gag.com/photo/$1_700b.jpg"),
                new StrategyRegex(
                        "9cache.com",
                        Collections.singletonList("img-9gag-fun.9cache.com"),
                        "https?://img-9gag-fun\\.9cache\\.com/photo/([^_]+).*",
                        "https://images-cdn.9gag.com/photo/$1_700b.jpg",
                        "https://images-cdn.9gag.com/photo/$1_700b.jpg"),
                new StrategyAny(
                        "pikabu.ru",
                        Arrays.asList("pikabu.ru", "www.pikabu.ru"),
                        "https://pikabu.ru/story/.+",
                        null, 4096),
                new StrategyAny(
                        "common→https",
                        Arrays.asList("*.wikipedia.org", "gfycat.com", "imgur.com"),
                        "https?://(.+)",
                        "https://$1",
                        4096 * 2),
                new StrategyAny(
                        "reddit",
                        Arrays.asList("v.redd.it", "reddit.com", "www.reddit.com", "old.reddit.com"),
                        null, null, 131072),
                new StrategyAny(
                        "any",
                        Collections.singletonList("*"),
                        null, null, 4096 * 2 * 2),
                new StrategyNull(
                        Arrays.asList("pastebin.com", "github.com", "bpaste.net", "dpaste.com"))
        );
    }

    // given an url, return a StrategyUrl that it the best candidate to handle it
    public static @Nullable @Cat(exit=true) Strategy.Url getStrategyUrl(String url, Strategy.Size size) {
        String host = getHost(url);
        if (host != null) {
            for (String subHost : new HostUtils.HostIterable(host)) {
                Strategy strategy = strategies.get(subHost);
                if (strategy != null) {
                    try {
                        Strategy.Url strategyUrl = strategy.make(url, size);
                        if (strategyUrl != null) return strategyUrl;
                    } catch (Strategy.CancelFurtherAttempts e) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    public static @NonNull List<Strategy.Url> getPossibleMediaCandidates(@NonNull URLSpan[] urls, Strategy.Size size) {
        List<Strategy.Url> candidates = new ArrayList<>();
        for (URLSpan url : urls) {
            Strategy.Url strategyUrl = getStrategyUrl(url.getURL(), size);
            if (strategyUrl != null) candidates.add(strategyUrl);
        }
        return candidates;
    }
}
