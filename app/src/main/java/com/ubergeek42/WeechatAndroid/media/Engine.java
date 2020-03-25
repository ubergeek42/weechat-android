package com.ubergeek42.WeechatAndroid.media;

import android.text.TextUtils;
import android.text.style.URLSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.MultiTransformation;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.ubergeek42.WeechatAndroid.relay.Line;
import com.ubergeek42.WeechatAndroid.utils.Network;
import com.ubergeek42.cats.Cat;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

import static com.ubergeek42.WeechatAndroid.media.HostUtils.getHost;

public class Engine {
    final private static @Root Kitty kitty = Kitty.make();

    final private static boolean ENABLED = true;

    final public static int ANIMATION_DURATION = 250;          // ms

    final public static int THUMBNAIL_WIDTH = 250;
    final public static int THUMBNAIL_MIN_HEIGHT = 120;        // todo set to the height of 2 lines of text?
    final public static int THUMBNAIL_MAX_HEIGHT = THUMBNAIL_WIDTH * 2;
    final public static int THUMBNAIL_HORIZONTAL_MARGIN = 8;
    final public static int THUMBNAIL_VERTICAL_MARGIN = 4;

    final static long MAXIMUM_BODY_SIZE = 5 * 1024 * 1024;

    final public static RequestOptions defaultRequestOptions = new RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .transform(new MultiTransformation<>(new CenterCrop(), new RoundedCorners(16)));

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static boolean isEnabledAtAll() {
        return Config.enabled != Config.Enable.NEVER;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public enum Location {
        CHAT,
        PASTE,
        NOTIFICATION
    }

    public static boolean isEnabledForLocation(Location location) {
        return ENABLED;     // todo
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static @NonNull List<LineFilter> lineFilters = new ArrayList<>();

    static void setLineFilters(@NonNull List<LineFilter> filters) {
        lineFilters = filters;
    }

    public static boolean isEnabledForLine(Line line) {
        if (line.type == Line.Type.OTHER)
            return false;
        for (LineFilter filter : lineFilters)
            if (filter.filters(line)) return false;
        return true;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static boolean isDisabledForCurrentNetwork() {
        switch (Config.enabled) {
            case NEVER: return true;
            case WIFI_ONLY: return !Network.get().hasProperty(Network.Property.WIFI);
            case UNMETERED_ONLY: return !Network.get().hasProperty(Network.Property.UNMETERED);
            case ALWAYS:
            default: return false;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static final HashMap<String, Strategy> strategies = new HashMap<>();

    @Cat static void registerStrategy(List<Strategy> strategies) {
        for (Strategy strategy : strategies) {
            for (String host : strategy.getHosts()) {
                Engine.strategies.put(host, strategy);
            }
        }
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

    static boolean hasNullStrategyFor(@NonNull HttpUrl url) {
        for (String subHost : new HostUtils.HostIterable(url.host())) {
            Strategy strategy = strategies.get(subHost);
            if (strategy instanceof StrategyNull) return true;
        }
        return false;
    }

    static {
        Config.setup();
    }
}
