package com.ubergeek42.WeechatAndroid.media;

import com.ubergeek42.WeechatAndroid.utils.Utils;
import com.ubergeek42.cats.Cat;

import java.util.concurrent.ConcurrentHashMap;

import static com.ubergeek42.WeechatAndroid.utils.Assert.assertThat;

public class Cache {
    final private static int MINUTES = 60 * 1000;
    final private static int COOLDOWN_SUCCESS = 60 * MINUTES;     // treat recent successes as ready cache

    final private static int COOLDOWN_LONG = 60 * 24 * MINUTES;
    final private static int COOLDOWN_MEDIUM = 60 * MINUTES;
    final private static int COOLDOWN_SHORT = 10 * MINUTES;

    // given a previous error code, returns time in milliseconds during which no attempts to query
    // the server should be made. https://www.restapitutorial.com/httpstatuscodes.html
    private static int getErrorCooldown(int code) {
        assertThat(code).isNotEqualTo(0);
        if (Utils.isAnyOf(code,
                Attempt.UNKNOWN_IO_ERROR,
                502, // Bad Gateway
                504  // Gateway Timeout
               )) return COOLDOWN_SHORT;
        if (Utils.isAnyOf(code,
                403, // Forbidden
                404, // Not Found
                500, // 500 Internal Server Error
                503  // Service Unavailable
                )) return COOLDOWN_MEDIUM;
        if (Utils.isAnyOf(code,
                Attempt.HTML_BODY_LACKS_REQUIRED_DATA,
                400, // Bad Request
                401, // Unauthorized
                409, // Conflict
                501  // Not Implemented
               )) return COOLDOWN_LONG;
        return COOLDOWN_MEDIUM;
    }

    public static class Attempt {
        public final static int SUCCESS = 0;
        final static int HTML_BODY_LACKS_REQUIRED_DATA = -1;
        final static int UNKNOWN_IO_ERROR = -2;

        final int code;
        final long timestamp;
        final String description;

        Attempt(int code, long timestamp, String description) {
            this.code = code;
            this.timestamp = timestamp;
            this.description = description;
        }
    }

    private static ConcurrentHashMap<String, Attempt> cache = new ConcurrentHashMap<>();

    public enum Info {
        NEVER_ATTEMPTED,
        FETCHED_RECENTLY,
        FETCHED_BEFORE_BUT_MIGHT_NOT_WORK,
        FAILED_BEFORE_BUT_MIGHT_WORK,
        FAILED_RECENTLY
    }

    @Cat(exit=true) public static Info info(StrategyUrl url) {
        Attempt lastAttempt = cache.get(url.getCacheKey());
        if (lastAttempt == null) return Info.NEVER_ATTEMPTED;

        if (lastAttempt.code == Attempt.SUCCESS)
            return COOLDOWN_SUCCESS > System.currentTimeMillis() - lastAttempt.timestamp ?
                    Info.FETCHED_RECENTLY : Info.FETCHED_BEFORE_BUT_MIGHT_NOT_WORK;
        int cooldown = getErrorCooldown(lastAttempt.code);
        return cooldown > System.currentTimeMillis() - lastAttempt.timestamp ?
                Info.FAILED_RECENTLY : Info.FAILED_BEFORE_BUT_MIGHT_WORK;
    }

    @Cat public static void record(StrategyUrl url, int code, String description) {
        String key = url.getCacheKey();
        cache.put(key, new Attempt(code, System.currentTimeMillis(), description));
    }
}
