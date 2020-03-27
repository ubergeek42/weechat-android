package com.ubergeek42.WeechatAndroid.media;

import com.ubergeek42.WeechatAndroid.service.P;
import com.ubergeek42.WeechatAndroid.utils.Linkify;

import java.util.Arrays;
import java.util.Collections;

public class Config {
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

    @SuppressWarnings("SpellCheckingInspection")
    private static void setup() {
        Linkify.setMessageFilter("^<[^ ]{1,16} \".{1,33}\"> ");

        Engine.setLineFilters(Arrays.asList(
                new LineFilter(Collections.singletonList("tacgnol"), null),
                new LineFilter(null, "^(?:Title: |↑ )")
        ));

        Engine.registerStrategy(Arrays.asList(
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
                new StrategyAny("imgur.com/gallery",
                        Arrays.asList("imgur.com", "www.imgur.com"),
                        "https?://(?:www.)imgur.com/gallery/(.*)",
                        "https://imgur.com/a/$1",
                        4096 * 2),
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
                        "mobile.twitter.com",
                        Collections.singletonList("mobile.twitter.com"),
                        "https?://mobile.twitter.com/(.*)",
                        "https://twitter.com/$1",
                        4096 * 4),
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
        ));
    }

    static {
        setup();
    }
}
