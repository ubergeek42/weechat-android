package com.ubergeek42.WeechatAndroid.media;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.ubergeek42.WeechatAndroid.Weechat;
import com.ubergeek42.WeechatAndroid.relay.BufferList;
import com.ubergeek42.WeechatAndroid.service.P;
import com.ubergeek42.WeechatAndroid.utils.Linkify;
import com.ubergeek42.WeechatAndroid.utils.Utils;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;

import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_MEDIA_PREVIEW_ENABLED_FOR_LOCATION;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_MEDIA_PREVIEW_ENABLED_FOR_LOCATION_CHAT;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_MEDIA_PREVIEW_ENABLED_FOR_LOCATION_D;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_MEDIA_PREVIEW_ENABLED_FOR_LOCATION_NOTIFICATIONS;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_MEDIA_PREVIEW_ENABLED_FOR_LOCATION_PASTE;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_MEDIA_PREVIEW_ENABLED_FOR_NETWORK;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_MEDIA_PREVIEW_ENABLED_FOR_NETWORK_ALWAYS;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_MEDIA_PREVIEW_ENABLED_FOR_NETWORK_D;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_MEDIA_PREVIEW_ENABLED_FOR_NETWORK_NEVER;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_MEDIA_PREVIEW_ENABLED_FOR_NETWORK_UNMETERED_ONLY;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_MEDIA_PREVIEW_ENABLED_FOR_NETWORK_WIFI_ONLY;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_MEDIA_PREVIEW_MAXIMUM_BODY_SIZE;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_MEDIA_PREVIEW_MAXIMUM_BODY_SIZE_D;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_MEDIA_PREVIEW_SECURE_REQUEST;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_MEDIA_PREVIEW_SECURE_REQUEST_D;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_MEDIA_PREVIEW_SECURE_REQUEST_OPTIONAL;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_MEDIA_PREVIEW_SECURE_REQUEST_REQUIRED;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_MEDIA_PREVIEW_SECURE_REQUEST_REWRITE;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_MEDIA_PREVIEW_STRATEGIES;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_MEDIA_PREVIEW_STRATEGIES_D;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_MEDIA_PREVIEW_SUCCESS_COOLDOWN;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_MEDIA_PREVIEW_SUCCESS_COOLDOWN_D;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_MEDIA_PREVIEW_THUMBNAIL_MAX_HEIGHT;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_MEDIA_PREVIEW_THUMBNAIL_MAX_HEIGHT_D;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_MEDIA_PREVIEW_THUMBNAIL_MIN_HEIGHT;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_MEDIA_PREVIEW_THUMBNAIL_MIN_HEIGHT_D;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_MEDIA_PREVIEW_THUMBNAIL_WIDTH;
import static com.ubergeek42.WeechatAndroid.utils.Constants.PREF_MEDIA_PREVIEW_THUMBNAIL_WIDTH_D;

public class Config {
    final private static @Root Kitty kitty = Kitty.make();

    final public static int ANIMATION_DURATION = 250;                           // ms

    final public static int THUMBNAIL_VERTICAL_MARGIN = (int) P._1_33dp;
    final public static int THUMBNAIL_HORIZONTAL_MARGIN = 2 * THUMBNAIL_VERTICAL_MARGIN;
    final public static int THUMBNAIL_CORNER_RADIUS = 4 * THUMBNAIL_VERTICAL_MARGIN;

    public static int thumbnailWidth = (int) (80 * P._1dp);
    public static int thumbnailMinHeight = (int) (40 * P._1dp);
    public static int thumbnailMaxHeight = thumbnailWidth * 2;

    public static int thumbnailAreaWidth = thumbnailWidth + THUMBNAIL_HORIZONTAL_MARGIN * 2;
    public static int thumbnailAreaMinHeight = thumbnailMinHeight + THUMBNAIL_VERTICAL_MARGIN * 2;

    ////////////////////////////////////////////////////////////////////////////////////////////////

    enum SecureRequest {
        OPTIONAL,
        REWRITE,
        REQUIRED;

        static SecureRequest fromString(String string) {
            switch (string) {
                case PREF_MEDIA_PREVIEW_SECURE_REQUEST_OPTIONAL: return OPTIONAL;
                default:
                case PREF_MEDIA_PREVIEW_SECURE_REQUEST_REWRITE: return REWRITE;
                case PREF_MEDIA_PREVIEW_SECURE_REQUEST_REQUIRED: return REQUIRED;
            }
        }
    }

    enum Enable {
        NEVER,
        WIFI_ONLY,
        UNMETERED_ONLY,
        ALWAYS;

        static Enable fromString(String string) {
            switch (string) {
                default:
                case PREF_MEDIA_PREVIEW_ENABLED_FOR_NETWORK_NEVER: return NEVER;
                case PREF_MEDIA_PREVIEW_ENABLED_FOR_NETWORK_WIFI_ONLY: return WIFI_ONLY;
                case PREF_MEDIA_PREVIEW_ENABLED_FOR_NETWORK_UNMETERED_ONLY: return UNMETERED_ONLY;
                case PREF_MEDIA_PREVIEW_ENABLED_FOR_NETWORK_ALWAYS: return ALWAYS;
            }
        }
    }

    static SecureRequest secureRequestsPolicy = SecureRequest.REWRITE;

    static Enable enabledForNetwork = Enable.UNMETERED_ONLY;

    static boolean enabledForChat = true;
    static boolean enabledForPaste = true;
    static boolean enabledForNotifications = true;

    static long maximumBodySize = 10 * 1000 * 1000;     // 10 MB in bytes
    static long successCooldown = 24 * 60 * 60 * 1000;  // 24 hours in ms

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

    public static void initPreferences() {
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(Weechat.applicationContext);
        for (String key : new String[] {
                PREF_MEDIA_PREVIEW_ENABLED_FOR_NETWORK,
                PREF_MEDIA_PREVIEW_ENABLED_FOR_LOCATION,
                PREF_MEDIA_PREVIEW_SECURE_REQUEST,
                PREF_MEDIA_PREVIEW_STRATEGIES,
                PREF_MEDIA_PREVIEW_MAXIMUM_BODY_SIZE,
                PREF_MEDIA_PREVIEW_SUCCESS_COOLDOWN,
                PREF_MEDIA_PREVIEW_THUMBNAIL_WIDTH,
                PREF_MEDIA_PREVIEW_THUMBNAIL_MIN_HEIGHT,
                PREF_MEDIA_PREVIEW_THUMBNAIL_MAX_HEIGHT
        }) {
            onSharedPreferenceChanged(p, key);
        }
    }

    public static void onSharedPreferenceChanged(SharedPreferences p, String key) {
        switch (key) {
            case PREF_MEDIA_PREVIEW_ENABLED_FOR_NETWORK:
                enabledForNetwork = Enable.fromString(p.getString(key, PREF_MEDIA_PREVIEW_ENABLED_FOR_NETWORK_D));
                break;
            case PREF_MEDIA_PREVIEW_ENABLED_FOR_LOCATION:
                Set<String> set = p.getStringSet(key, PREF_MEDIA_PREVIEW_ENABLED_FOR_LOCATION_D);
                enabledForChat = set.contains(PREF_MEDIA_PREVIEW_ENABLED_FOR_LOCATION_CHAT);
                enabledForPaste = set.contains(PREF_MEDIA_PREVIEW_ENABLED_FOR_LOCATION_PASTE);
                enabledForNotifications = set.contains(PREF_MEDIA_PREVIEW_ENABLED_FOR_LOCATION_NOTIFICATIONS);
                break;
            case PREF_MEDIA_PREVIEW_SECURE_REQUEST:
                secureRequestsPolicy = SecureRequest.fromString(p.getString(key, PREF_MEDIA_PREVIEW_SECURE_REQUEST_D));
                break;
            case PREF_MEDIA_PREVIEW_STRATEGIES:
                Info info = parseConfigSafe(p.getString(key, PREF_MEDIA_PREVIEW_STRATEGIES_D));
                if (info != null) {
                    Linkify.setMessageFilter(info.messageFilter);
                    Engine.setLineFilters(info.lineFilters);
                    Engine.setStrategies(info.strategies);
                }
                break;
            case PREF_MEDIA_PREVIEW_MAXIMUM_BODY_SIZE:
                maximumBodySize = (long) (Float.parseFloat(p.getString(key, PREF_MEDIA_PREVIEW_MAXIMUM_BODY_SIZE_D)) * 1000 * 1000);
                break;
            case PREF_MEDIA_PREVIEW_SUCCESS_COOLDOWN:
                successCooldown = (long) (Float.parseFloat(p.getString(key, PREF_MEDIA_PREVIEW_SUCCESS_COOLDOWN_D)) * 60 * 60 * 1000);
                break;
            case PREF_MEDIA_PREVIEW_THUMBNAIL_WIDTH:
                thumbnailWidth = (int) (P._1dp * Float.parseFloat(p.getString(key, PREF_MEDIA_PREVIEW_THUMBNAIL_WIDTH_D)));
                thumbnailAreaWidth = thumbnailWidth + THUMBNAIL_HORIZONTAL_MARGIN * 2;
                break;
            case PREF_MEDIA_PREVIEW_THUMBNAIL_MIN_HEIGHT:
                thumbnailMinHeight = (int) (P._1dp * Float.parseFloat(p.getString(key, PREF_MEDIA_PREVIEW_THUMBNAIL_MIN_HEIGHT_D)));
                thumbnailAreaMinHeight = thumbnailMinHeight + THUMBNAIL_VERTICAL_MARGIN * 2;
                break;
            case PREF_MEDIA_PREVIEW_THUMBNAIL_MAX_HEIGHT:
                thumbnailMaxHeight = (int) (P._1dp * Float.parseFloat(p.getString(key, PREF_MEDIA_PREVIEW_THUMBNAIL_MAX_HEIGHT_D)));
                break;
            default:
                return;
        }
        BufferList.onGlobalPreferencesChanged(false);
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static @Nullable Info parseConfigSafe(String text) {
        try {
            return parseConfig(text);
        } catch (ConfigException e) {
            kitty.warn("Error while parsing media preview config", e);
            Weechat.showLongToast(e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Info parseConfig(String text) throws ConfigException {
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
