package com.ubergeek42.WeechatAndroid.media;

import android.text.Html;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.util.Preconditions;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static com.ubergeek42.WeechatAndroid.utils.Utils.readInputStream;

class StrategyAny extends Strategy {
    final private static Pattern OG = Pattern.compile(
            "<\\s*meta" +                                           // tag
            "\\s[^>]{0,50}?" +                                      // a space and optionally other parameters
            "property\\s*=\\s*" +
            "(['\"])og:image(?::(?:secure_)?url)?\\1" +             // all possible image url combos
            "\\s[^>]{0,50}?" +                                      // space, etc
            "content\\s*=\\s*" +
            "(['\"])(https://\\S+?)\\2",                            // an https url
            Pattern.CASE_INSENSITIVE);

    final private @Nullable Pattern regex;
    final private @Nullable String replacement;
    final private int wantedBodySize;

    StrategyAny(String name, List<String> hosts, @Nullable String regex, @Nullable String replacement, int wantedBodySize) {
        super(name, hosts);
        this.regex = regex == null ? null : Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        this.replacement = replacement;
        this.wantedBodySize = wantedBodySize;
    }

    @Nullable @Override Url make(String originalUrl, Size size) {
        String modifiedUrl = originalUrl;
        if (regex != null) {
            Matcher matcher = regex.matcher(originalUrl);
            if (!matcher.matches())
                return null;
            modifiedUrl = replacement != null ? matcher.replaceFirst(replacement) : originalUrl;
        }
        return new Url(originalUrl, modifiedUrl);
    }

    private class Url extends Strategy.Url {
        final String originalUrl;
        final String modifiedUrl;

        int stage = 0;

        private Url(String originalUrl, String modifiedUrl) {
            this.originalUrl = originalUrl;
            this.modifiedUrl = modifiedUrl;
        }

        @Override String getCacheKey() {
            return modifiedUrl;
        }

        @NonNull @Override Request getFirstRequest() {
            return RequestType.HTML_OR_IMAGE.makeRequest(modifiedUrl).build();
        }

        @Nullable @Override Request getNextRequest(@NonNull Response response, InputStream stream) throws IOException {
            ResponseBody body = Preconditions.checkNotNull(response.body());
            MediaType responseType = body.contentType();

            if (RequestType.IMAGE.matches(responseType))
                return null;

            if (!RequestType.HTML.matches(responseType))
                throw new Exceptions.UnacceptableMediaTypeException(RequestType.HTML_OR_IMAGE, responseType);

            if (stage > 0)      // got html as a second response
                throw new Exceptions.UnacceptableMediaTypeException(RequestType.IMAGE, responseType);
            stage++;

            CharSequence html = readInputStream(stream, wantedBodySize);
            Matcher matcher = OG.matcher(html);
            if (!matcher.find())
                throw new Exceptions.HtmlBodyLacksRequiredDataException(html);

            String escapedUrl = matcher.group(3);
            String validUrl = Html.fromHtml(escapedUrl).toString();

            return RequestType.IMAGE.makeRequest(validUrl).build();
        }
    }
}
