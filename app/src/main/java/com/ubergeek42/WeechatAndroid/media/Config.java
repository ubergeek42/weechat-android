package com.ubergeek42.WeechatAndroid.media;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ubergeek42.WeechatAndroid.service.P;
import com.ubergeek42.WeechatAndroid.utils.Utils;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;

import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public class Config {
    final private static @Root Kitty kitty = Kitty.make();

    final public static int ANIMATION_DURATION = 250;                           // ms

    final public static int THUMBNAIL_WIDTH = (int) (80 * P._1dp);
    final public static int THUMBNAIL_MIN_HEIGHT = (int) (40 * P._1dp);         // todo set to the height of 2 lines of text?
    final public static int THUMBNAIL_MAX_HEIGHT = THUMBNAIL_WIDTH * 2;
    final public static int THUMBNAIL_VERTICAL_MARGIN = (int) P._1_33dp;
    final public static int THUMBNAIL_HORIZONTAL_MARGIN = 2 * THUMBNAIL_VERTICAL_MARGIN;
    final        static int THUMBNAIL_CORNER_RADIUS = 4 * THUMBNAIL_VERTICAL_MARGIN;

    ////////////////////////////////////////////////////////////////////////////////////////////////

    enum SecureRequest {
        OPTIONAL,
        REQUIRED,
        REWRITE
    }

    enum Enable {
        NEVER,
        WIFI_ONLY,
        UNMETERED_ONLY,
        ALWAYS
    }

    static SecureRequest secureRequestsPolicy = SecureRequest.REWRITE;

    static Enable enabledForNetwork = Enable.UNMETERED_ONLY;

    static boolean enabledForChat = true;
    static boolean enabledForPaste = true;
    static boolean enabledForNotifications = true;

    static long maximumBodySize = 5 * 1024 * 1024;

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class Info {
        @Nullable public Pattern messageFilter;
        @Nullable public List<LineFilter> lineFilters;
        @Nullable public List<Strategy> strategies;

        Info(@Nullable Pattern messageFilter, @Nullable List<LineFilter> lineFilters, @Nullable List<Strategy> strategies) {
            this.messageFilter = messageFilter;
            this.lineFilters = lineFilters;
            this.strategies = strategies;
        }
    }

    @SuppressWarnings("unchecked")
    public static Info parseConfig(String text) throws Exception {
        String stage = "parsing document";
        try {
            Object obj = new Yaml().load(text);
            if (obj == null) return new Info(null, null, null);
            Map<String, Object> root = (Map) obj;
            Map<String, Object> map;

            stage = "parsing message filter";
            @Nullable String messageFilterO = (String) root.remove("message filter");
            @Nullable Pattern messageFilterPattern = messageFilterO == null ?
                    null : Pattern.compile(messageFilterO);

            stage = "parsing line filters";
            @Nullable List<Map<String, Object>> lineFiltersO = (List) root.remove("line filters");
            @Nullable List<LineFilter> lineFilters = Utils.isEmpty(lineFiltersO) ?
                    null : new ArrayList<>();
            if (lineFilters != null) {
                int i = 1;
                for (Object o : lineFiltersO) {
                    stage = "parsing line filter #" + i++;
                    map = (Map) o;
                    @Nullable List<String> nicks = requireListOf(String.class, (List) map.remove("nicks"));
                    @Nullable String regex = (String) map.remove("regex");
                    lineFilters.add(new LineFilter(nicks, regex));
                    requireEmpty(map);
                }
            }

            stage = "parsing strategies";
            @Nullable List<Map<String, Object>> strategiesO = (List) root.remove("strategies");
            @Nullable List<Strategy> strategies = Utils.isEmpty(strategiesO) ?
                    null : new ArrayList<>();
            if (strategies != null) {
                int i = 1;
                for (Object o : strategiesO) {
                    stage = "parsing strategy #" + i++;
                    map = (Map) o;
                    Strategy strategy;
                    @NonNull String name = requireNonNull((String) map.remove("name"), "name");
                    @NonNull String type = requireNonNull((String) map.remove("type"), "type");
                    @NonNull List<String> hosts = requireListOf(String.class, requireNonNull(
                            (List) map.remove("hosts"), "hosts"));
                    @Nullable String regex = (String) map.remove("regex");

                    switch (type) {
                        case "image":
                            @Nullable String small = (String) map.remove("small");
                            @Nullable String big = (String) map.remove("big");
                            strategy = new StrategyRegex(name, hosts, regex, small, big);
                            break;
                        case "any":
                            @Nullable String replacement = (String) map.remove("sub");
                            @Nullable Integer bodySize = (Integer) map.remove("body size");
                            if (bodySize == null) bodySize = 4096 * 2 * 2;
                            strategy = new StrategyAny(name, hosts, regex, replacement, bodySize);
                            break;
                        case "none":
                            strategy = new StrategyNull(name, hosts);
                            break;
                        default:
                            throw new IllegalArgumentException("Unknown type: " + type);
                    }
                    requireEmpty(map);
                    strategies.add(strategy);
                }
            }
            stage = "parsing document";
            requireEmpty(root);
            return new Info(messageFilterPattern, lineFilters, strategies);
        } catch (Exception e) {
            throw new ConfigException("Error while " + stage, e);
        }
    }

    private static <T> T requireNonNull(T o, String name) throws NullPointerException {
        return Objects.requireNonNull(o, name + " must not be null");
    }

    private static void requireEmpty(@NonNull Map<String, Object> map) throws IllegalArgumentException {
        if (map.isEmpty()) return;
        String key = map.entrySet().iterator().next().getKey();
        throw new IllegalArgumentException("Unexpected key: " + key);
    }

    @SuppressWarnings({"unchecked", "SameParameterValue"})
    private static <T> List<T> requireListOf(@NonNull Class<T> cls, @Nullable List<Object> list) throws ClassCastException {
        if (list == null) return null;
        for (Object o : list) {
            if (!(cls.isInstance(o))) throw new ClassCastException(
                    "Wanted a list of " + cls + ", found a list of " +
                    (o == null ? null : o.getClass()));
        }
        return (List<T>) list;
    }

    private static class ConfigException extends Exception {
        ConfigException(String stage, Throwable cause) {
            super(stage, cause);
        }

        @Nullable @Override public String getMessage() {
            return getCause() != null ?
                    super.getMessage() + ": " + getCause().getMessage() :
                    super.getMessage();
        }
    }
}
