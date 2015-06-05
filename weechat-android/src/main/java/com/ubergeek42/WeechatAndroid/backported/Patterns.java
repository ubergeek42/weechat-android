package com.ubergeek42.WeechatAndroid.backported;

import java.util.regex.Pattern;

/**
 * We borrowed this from Android 5.0 because older versions don't linkify some of the new GTLDs.
 * See issue: https://github.com/ubergeek42/weechat-android/issues/193
 *
 * TODO: Remove this once we no longer support devices older than 5.0
 *
 * Taken from:
 * https://github.com/android/platform_frameworks_base/blob/7d72fedd7e60070a26d5b5647fe93e2245c1903e/core/java/android/util/Patterns.java
 *
 */
public class Patterns {
    /*
     * Copyright (C) 2007 The Android Open Source Project
     *
     * Licensed under the Apache License, Version 2.0 (the "License");
     * you may not use this file except in compliance with the License.
     * You may obtain a copy of the License at
     *
     *      http://www.apache.org/licenses/LICENSE-2.0
     *
     * Unless required by applicable law or agreed to in writing, software
     * distributed under the License is distributed on an "AS IS" BASIS,
     * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     * See the License for the specific language governing permissions and
     * limitations under the License.
     */

    /**
     * Good characters for Internationalized Resource Identifiers (IRI).
     * This comprises most common used Unicode characters allowed in IRI
     * as detailed in RFC 3987.
     * Specifically, those two byte Unicode characters are not included.
     */
    public static final String GOOD_IRI_CHAR =
            "a-zA-Z0-9\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF";

    public static final Pattern IP_ADDRESS
            = Pattern.compile(
                    "((25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9])\\.(25[0-5]|2[0-4]"
                    + "[0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1]"
                    + "[0-9]{2}|[1-9][0-9]|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}"
                    + "|[1-9][0-9]|[0-9]))");

    /**
     * RFC 1035 Section 2.3.4 limits the labels to a maximum 63 octets.
     */
    private static final String IRI
            = "[" + GOOD_IRI_CHAR + "]([" + GOOD_IRI_CHAR + "\\-]{0,61}[" + GOOD_IRI_CHAR + "]){0,1}";

    private static final String GOOD_GTLD_CHAR =
            "a-zA-Z\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF";
    private static final String GTLD = "[" + GOOD_GTLD_CHAR + "]{2,63}";
    private static final String HOST_NAME = "(" + IRI + "\\.)+" + GTLD;

    public static final Pattern DOMAIN_NAME
            = Pattern.compile("(" + HOST_NAME + "|" + IP_ADDRESS + ")");

    /**
     *  Regular expression pattern to match most part of RFC 3987
     *  Internationalized URLs, aka IRIs.  Commonly used Unicode characters are
     *  added.
     */
    public static final Pattern WEB_URL = Pattern.compile(
            "((?:(http|https|Http|Https|rtsp|Rtsp):\\/\\/(?:(?:[a-zA-Z0-9\\$\\-\\_\\.\\+\\!\\*\\'\\(\\)"
            + "\\,\\;\\?\\&\\=]|(?:\\%[a-fA-F0-9]{2})){1,64}(?:\\:(?:[a-zA-Z0-9\\$\\-\\_"
            + "\\.\\+\\!\\*\\'\\(\\)\\,\\;\\?\\&\\=]|(?:\\%[a-fA-F0-9]{2})){1,25})?\\@)?)?"
            + "(?:" + DOMAIN_NAME + ")"
            + "(?:\\:\\d{1,5})?)" // plus option port number
            + "(\\/(?:(?:[" + GOOD_IRI_CHAR + "\\;\\/\\?\\:\\@\\&\\=\\#\\~"  // plus option query params
            + "\\-\\.\\+\\!\\*\\'\\(\\)\\,\\_])|(?:\\%[a-fA-F0-9]{2}))*)?"
            + "(?:\\b|$)"); // and finally, a word boundary or end of
                            // input.  This is to stop foo.sure from
                            // matching as foo.su
}
