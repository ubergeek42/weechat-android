package androidx.preference;

/*
 * This file is a modified version of the original taken from the Android source code.
 * It was modified primarily to remove the @hide tag.
 *
 * https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/graphics/java/android/graphics/fonts/FontFileUtil.java
 * https://github.com/aosp-mirror/platform_frameworks_base/blob/android16-release/graphics/java/android/graphics/fonts/FontFileUtil.java
 */

/*
 * Copyright 2018 The Android Open Source Project
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

import android.util.ArraySet;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.Set;

public class FontFileUtil {
    private FontFileUtil() {}  // Do not instantiate

    private static final int SFNT_VERSION_1 = 0x00010000;
    private static final int SFNT_VERSION_OTTO = 0x4F54544F;
    private static final int TTC_TAG = 0x74746366;
    private static final int FVAR_TABLE_TAG = 0x66766172;


    private static int getUInt16(ByteBuffer buffer, int offset) {
        return ((int) buffer.getShort(offset)) & 0xFFFF;
    }

    /**
     * Returns supported axes of font
     *
     * @param buffer A buffer of the entire font file.
     * @param index A font index in case of font collection. Must be 0 otherwise.
     * @return set of supported axes tag. Returns empty set on error.
     */
    public static Set<Integer> getSupportedAxes(@NonNull ByteBuffer buffer, int index) {
        ByteOrder originalOrder = buffer.order();
        buffer.order(ByteOrder.BIG_ENDIAN);
        try {
            int fontFileOffset = 0;
            int magicNumber = buffer.getInt(0);
            if (magicNumber == TTC_TAG) {
                // TTC file.
                if (index >= buffer.getInt(8 /* offset to number of fonts in TTC */)) {
                    return Collections.EMPTY_SET;
                }
                fontFileOffset = buffer.getInt(
                        12 /* offset to array of offsets of font files */ + 4 * index);
            }
            int sfntVersion = buffer.getInt(fontFileOffset);

            if (sfntVersion != SFNT_VERSION_1 && sfntVersion != SFNT_VERSION_OTTO) {
                return Collections.EMPTY_SET;
            }

            int numTables = buffer.getShort(fontFileOffset + 4 /* offset to number of tables */);
            int fvarTableOffset = -1;
            for (int i = 0; i < numTables; ++i) {
                int tableOffset = fontFileOffset + 12 /* size of offset table */
                        + i * 16 /* size of table record */;
                if (buffer.getInt(tableOffset) == FVAR_TABLE_TAG) {
                    fvarTableOffset = buffer.getInt(tableOffset + 8 /* offset to the table */);
                    break;
                }
            }

            if (fvarTableOffset == -1) {
                // Couldn't find OS/2 table. use regular style
                return Collections.EMPTY_SET;
            }

            if (buffer.getShort(fvarTableOffset) != 1
                    || buffer.getShort(fvarTableOffset + 2) != 0) {
                return Collections.EMPTY_SET;
            }

            int axesArrayOffset = getUInt16(buffer, fvarTableOffset + 4);
            int axisCount = getUInt16(buffer, fvarTableOffset + 8);
            int axisSize = getUInt16(buffer, fvarTableOffset + 10);

            ArraySet<Integer> axes = new ArraySet<>();
            for (int i = 0; i < axisCount; ++i) {
                axes.add(buffer.getInt(fvarTableOffset + axesArrayOffset + axisSize * i));
            }

            return axes;
        } finally {
            buffer.order(originalOrder);
        }
    }

    private static final int TAG_ital = 0x6974616C;  // i(0x69), t(0x74), a(0x61), l(0x6c)
    private static final int TAG_wght = 0x77676874;  // w(0x77), g(0x67), h(0x68), t(0x74)

    public static String axisToString(int value) {
        StringBuilder builder = new StringBuilder(4);

        for (int i = 3; i >= 0; i--) {
            byte b = (byte) ((value >> (8 * i)) & 0xFF);
            builder.append((char) b);
        }

        return builder.toString();
    }
}
