package com.ubergeek42.WeechatAndroid.media;

import android.graphics.Bitmap;
import android.text.style.URLSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.MultiTransformation;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.ubergeek42.WeechatAndroid.relay.Line;
import com.ubergeek42.WeechatAndroid.relay.LineSpec;
import com.ubergeek42.WeechatAndroid.utils.DefaultHashMap;
import com.ubergeek42.WeechatAndroid.utils.Network;
import com.ubergeek42.cats.Cat;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import okhttp3.HttpUrl;

import static com.ubergeek42.WeechatAndroid.media.Config.THUMBNAIL_CORNER_RADIUS;
import static com.ubergeek42.WeechatAndroid.media.HostUtils.getHost;

public class Engine {
    final private static @Root Kitty kitty = Kitty.make();

    private static @NonNull HashMap<String, List<Strategy>> strategies = new HashMap<>();
    private static @Nullable List<LineFilter> lineFilters = new ArrayList<>();

    public enum Location {
        CHAT,
        PASTE,
        NOTIFICATION
    }

    @SuppressWarnings("Convert2Diamond") // `MultiTransformation<>` produces a build warning
    final public static RequestOptions defaultRequestOptions = new RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .transform(new MultiTransformation<Bitmap>(new CenterCrop(), new RoundedCorners(THUMBNAIL_CORNER_RADIUS)));

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static boolean isEnabledAtAll() {
        return Config.enabledForNetwork != Config.Enable.NEVER;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static boolean isEnabledForLocation(Location location) {
        switch (location) {
            case CHAT: return Config.enabledForChat;
            case PASTE: return Config.enabledForPaste;
            case NOTIFICATION: return Config.enabledForNotifications;
            default: return false;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    static void setLineFilters(@Nullable List<LineFilter> filters) {
        lineFilters = filters;
    }

    public static boolean isEnabledForLine(Line line) {
        if (line.type == LineSpec.Type.Other)
            return false;
        if (lineFilters != null) {
            for (LineFilter filter : lineFilters)
                if (filter.filters(line)) return false;
        }
        return true;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static boolean isDisabledForCurrentNetwork() {
        switch (Config.enabledForNetwork) {
            case NEVER: return true;
            case WIFI_ONLY: return !Network.get().hasProperty(Network.Property.WIFI);
            case UNMETERED_ONLY: return !Network.get().hasProperty(Network.Property.UNMETERED);
            case ALWAYS:
            default: return false;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Cat static void setStrategies(@Nullable List<Strategy> strategyList) {
        DefaultHashMap<String, List<Strategy>> strategies = new DefaultHashMap<>(key -> new ArrayList<>());
        if (strategyList != null) {
            for (Strategy strategy : strategyList) {
                for (String host : strategy.getHosts()) {
                    strategies.computeIfAbsent(host).add(strategy);
                }
            }
        }
        Engine.strategies = strategies;
    }

    // given an url, return a StrategyUrl that it the best candidate to handle it
    public static @Nullable @Cat(exit=true) Strategy.Url getStrategyUrl(String url, Strategy.Size size) {
        String host = getHost(url);
        if (host != null) {
            for (String subHost : new HostUtils.HostIterable(host)) {
                List<Strategy> strategies = Engine.strategies.get(subHost);
                if (strategies != null) {
                    for (Strategy strategy : strategies) {
                        try {
                            Strategy.Url strategyUrl = strategy.make(url, size);
                            if (strategyUrl != null) return strategyUrl;
                        } catch (Strategy.CancelFurtherAttempts e) {
                            return null;
                        }
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

    static boolean hasNullStrategyFor(@NonNull HttpUrl url) {
        for (String subHost : new HostUtils.HostIterable(url.host())) {
            List<Strategy> strategies = Engine.strategies.get(subHost);
            if (strategies != null) {
                for (Strategy strategy : strategies) {
                    try {
                        Strategy.Url strategyUrl = strategy.make(url.toString(), Strategy.Size.SMALL);
                        if (strategyUrl != null) return false;
                    } catch (Strategy.CancelFurtherAttempts e) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
