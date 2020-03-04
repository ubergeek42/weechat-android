package com.ubergeek42.WeechatAndroid.media;

import android.graphics.Bitmap;
import android.system.ErrnoException;
import android.system.OsConstants;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.HttpException;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.ubergeek42.WeechatAndroid.utils.Utils;
import com.ubergeek42.cats.Cat;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.ConcurrentHashMap;

import static com.ubergeek42.WeechatAndroid.utils.Assert.assertThat;

public class Cache {
    final private static @Root Kitty kitty = Kitty.make();

    public static class Attempt {
        final int code;
        final long timestamp;

        Attempt(int code, long timestamp) {
            this.code = code;
            this.timestamp = timestamp;
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

        if (lastAttempt.code == SUCCESS)
            return COOLDOWN_SUCCESS > System.currentTimeMillis() - lastAttempt.timestamp ?
                    Info.FETCHED_RECENTLY : Info.FETCHED_BEFORE_BUT_MIGHT_NOT_WORK;
        int cooldown = getErrorCooldown(lastAttempt.code);
        return cooldown > System.currentTimeMillis() - lastAttempt.timestamp ?
                Info.FAILED_RECENTLY : Info.FAILED_BEFORE_BUT_MIGHT_WORK;
    }

    @Cat private static void record(StrategyUrl url, int code) {
        String key = url.getCacheKey();
        cache.put(key, new Attempt(code, System.currentTimeMillis()));
    }

    public final static RequestListener<Bitmap> listener = new RequestListener<Bitmap>() {
        final private @Root Kitty kitty = Kitty.make();

        @Override @Cat public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Bitmap> target, boolean isFirstResource) {
            Cache.record((StrategyUrl) model, getErrorCode(e));
            return false;
        }

        @Override @Cat public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target, DataSource dataSource, boolean isFirstResource) {
            Cache.record((StrategyUrl) model, SUCCESS);
            return false;
        }
    };

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////// error handling
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // online : invalid ip
    // online : invalid host
    // offline : valid host
    //      java.net.UnknownHostException: Unable to resolve host "192.168.1.600": No address associated with hostname
    //      android.system.GaiException: android_getaddrinfo failed: EAI_NODATA (No address associated with hostname)
    //      error = 7
    // online : valid ip : no service on port
    // online : valid host : no service on port
    //      java.net.ConnectException: Failed to connect to /192.168.1.107:80
    //      java.net.ConnectException: failed to connect to /192.168.1.107 (port 80) from /192.168.1.55 (port 45970) after 10000ms: isConnected failed: ECONNREFUSED (Connection refused)
    //      android.system.ErrnoException: isConnected failed: ECONNREFUSED (Connection refused)
    //          errno = 111
    // online : valid ip : packet drop
    //      java.net.SocketTimeoutException: failed to connect to /192.168.1.107 (port 8090) from /192.168.1.55 (port 46066) after 10000ms
    // offline : valid ip
    //      java.net.ConnectException: Failed to connect to /192.168.1.107:8090
    //      java.net.ConnectException: failed to connect to /192.168.1.107 (port 8090) from /:: (port 0) after 10000ms: connect failed: ENETUNREACH (Network is unreachable)
    //      android.system.ErrnoException: connect failed: ENETUNREACH (Network is unreachable)
    //          errno = 101
    // wrong service
    //      java.net.ProtocolException: Unexpected status line: SSH-2.0-OpenSSH_7.9p1 Raspbian-10+deb10u2
    // content-length smaller than body: only content-length bytes are fetched, image shown partially loaded
    //      no error
    // content-length larger than body:
    //      <empty root exception list>
    //      either SocketTimeoutException or ProtocolException is fired depending on whether or not
    //      the stream was closed or remains open; however the exception is ignored
    //      see com.bumptech.glide.load.model.StreamEncoder.StreamEncoder#encode
    // decode failed
    //      com.bumptech.glide.load.engine.GlideException: Failed to load resource
    //          There were 4 causes:
    //          java.io.IOException(java.lang.RuntimeException: setDataSourceCallback failed: status = 0x80000000)
    //          java.io.IOException(java.lang.RuntimeException: setDataSource failed: status = 0x80000000)
    //          java.io.IOException(java.lang.RuntimeException: setDataSourceCallback failed: status = 0x80000000)
    //          java.io.IOException(java.lang.RuntimeException: setDataSource failed: status = 0x80000000)

    // see also https://www-numi.fnal.gov/offline_software/srt_public_context/WebDocs/Errors/unix_system_errors.html
    // see also https://www.restapitutorial.com/httpstatuscodes.html

    private final static int SUCCESS = 0;
    private final static int ERROR_UNKNOWN_ERROR = -1;
    final static int ERROR_HTML_BODY_LACKS_REQUIRED_DATA = -2;
    final static int ERROR_UNACCEPTABLE_FILE_SIZE = -3;

    private final static int ERROR_TIMEOUT = -110;
    private final static int ERROR_INTERNET_UNREACHABLE = -101;
    private final static int ERROR_LIKELY_TEMPORARY_NETWORK_PROBLEM = -100;
    private final static int ERROR_CONNECTION_REFUSED = -111;
    private final static int ERROR_UNKNOWN_HOST = -200;

    private static int getErrorCode(GlideException e) {
        if (!internetAvailable())
            return ERROR_INTERNET_UNREACHABLE;
        if (e == null)
            return ERROR_UNKNOWN_ERROR;

        HttpException httpException = findException(e, HttpException.class);
        if (httpException != null)
            return httpException.getStatusCode();
        Exceptions.CodeException codeException = findException(e, Exceptions.CodeException.class);
        if (codeException != null)
            return codeException.getCode();

        if (e.getRootCauses().isEmpty())
            return ERROR_TIMEOUT;      // see notes
        if (findException(e, SocketTimeoutException.class) != null)
            return ERROR_TIMEOUT;
        if (findException(e, UnknownHostException.class) != null)
            return ERROR_UNKNOWN_HOST;
        ErrnoException errnoException = findException(e, ErrnoException.class);
        if (errnoException != null) {
            int errno = errnoException.errno;
            if (errno == OsConstants.ECONNREFUSED)
                return ERROR_CONNECTION_REFUSED;
            if (errno == OsConstants.ENETUNREACH)
                return ERROR_INTERNET_UNREACHABLE;
            if (Utils.isAnyOf(errno,
                    OsConstants.ENETDOWN,
                    OsConstants.ENETRESET,
                    OsConstants.ECONNABORTED,
                    OsConstants.ECONNRESET,
                    OsConstants.EHOSTUNREACH)) return ERROR_LIKELY_TEMPORARY_NETWORK_PROBLEM;
        }
        return ERROR_UNKNOWN_ERROR;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    final private static int MINUTES = 60 * 1000;

    final private static int COOLDOWN_SUCCESS = 60 * MINUTES;     // treat recent successes as ready cache

    final private static int COOLDOWN_NONE = 0;
    final private static int COOLDOWN_LONG = 60 * 24 * MINUTES;
    final private static int COOLDOWN_MEDIUM = 60 * MINUTES;
    final private static int COOLDOWN_SHORT = 10 * MINUTES;

    // given a previous error code, returns time in milliseconds during which no attempts to query
    // the server should be made
    private static int getErrorCooldown(int code) {
        assertThat(code).isNotEqualTo(0);
        if (Utils.isAnyOf(code,
                ERROR_TIMEOUT,
                ERROR_INTERNET_UNREACHABLE,
                ERROR_LIKELY_TEMPORARY_NETWORK_PROBLEM
        )) return COOLDOWN_NONE;
        if (Utils.isAnyOf(code,
                502, // Bad Gateway
                504  // Gateway Timeout
        )) return COOLDOWN_SHORT;
        if (Utils.isAnyOf(code,
                ERROR_HTML_BODY_LACKS_REQUIRED_DATA,
                ERROR_UNACCEPTABLE_FILE_SIZE,
                ERROR_UNKNOWN_HOST,
                400, // Bad Request
                401, // Unauthorized
                409, // Conflict
                501  // Not Implemented
        )) return COOLDOWN_LONG;
        return COOLDOWN_MEDIUM;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static <T extends Throwable> T findException(@Nullable Throwable e, @NonNull Class<T> cls) {
        if (e == null) return null;
        if (cls.isInstance(e)) return (T) e;
        if (e instanceof GlideException) {
            for (Throwable cause : ((GlideException) e).getRootCauses()) {
                T t = findException(cause, cls);
                if (t != null) return t;
            }
            return null;
        } else {
            return findException(e.getCause(), cls);
        }
    }

    private static boolean internetAvailable() {
        return true;    // todo
    }
}