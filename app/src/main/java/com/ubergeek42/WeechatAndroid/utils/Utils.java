// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package com.ubergeek42.WeechatAndroid.utils;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;

import org.joda.time.format.DateTimeFormat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings({"SameParameterValue", "WeakerAccess", "unused"})
public class Utils {

    final private static @Root Kitty kitty = Kitty.make();

    //////////////////////////////////////////////////////////////////////////////////////////////// serialization

    public static @Nullable Object deserialize(@Nullable String string) {
        if (string == null) return null;
        try {
            byte[] data = Base64.decode(string, Base64.DEFAULT);
            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data));
            Object o = ois.readObject();
            ois.close();
            return o;
        } catch (Exception e) {
            kitty.error("deserialize()", e);
            return null;
        }
    }

    public static @Nullable String serialize(@Nullable Serializable serializable) {
        if (serializable == null) return null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(serializable);
            oos.close();
            return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
        } catch (Exception e) {
            kitty.error("serialize()", e);
            return null;
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// string cuts

    static @NonNull String unCrLf(@NonNull String text) {
        return text.replaceAll("\\r\\n|\\r|\\n", "⏎ ");
    }

    // cut string at 100 characters
    public static @NonNull String cut(@NonNull String text, int at) {
        return (text.length() > at) ?
                text.substring(0, Math.min(text.length(), at)) + "…" : text;
    }

    public static boolean isAllDigits(@Nullable String s) {
        if (s == null || s.isEmpty())
            return false;
        for (int i = 0; i < s.length(); i++)
            if (!Character.isDigit(s.charAt(i)))
                return false;
        return true;
    }

    public static boolean isValidTimestampFormat(@Nullable String s) {
        if (s == null)
            return false;
        try {
            DateTimeFormat.forPattern(s);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static boolean isAnyOf(String left, String ... rights) {
        for (String right : rights)
            if (left.equals(right))
                return true;
        return false;
    }

    public static boolean isAnyOf(int left, int ... rights) {
        for (int right : rights)
            if (left == right)
                return true;
        return false;
    }

    public static boolean isEmpty(byte[] bytes) {
        return bytes == null || bytes.length == 0;
    }

    public static <T> boolean isEmpty(T[] array) {
        return array == null || array.length == 0;
    }

    public static <T> boolean isEmpty(Collection<T> collection) {
        return collection == null || collection.isEmpty();
    }

    public static @NonNull byte[] readFromUri(Context context, Uri uri) throws IOException {
        int len;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Input stream is null");
            while ((len = in.read(buffer)) != -1) out.write(buffer, 0, len);
            if (out.size() == 0) throw new IOException("File is empty");
            return out.toByteArray();
        }
    }

    public static void setBottomMargin(View view, int margin) {
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        params.bottomMargin = margin;
        view.setLayoutParams(params);
    }

    public static Activity getActivity(View view) {
        Context context = view.getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

//    public interface Checker <E> {
//        boolean check(E e);
//    }
//
//    public static @Nullable <E> E findStartingFromEnd(LinkedList<E> list, Checker<E> checker) {
//        Iterator<E> it = list.descendingIterator();
//        while (it.hasNext()) {
//            E e = it.next();
//            if (checker.check(e)) return e;
//        }
//        return null;
//    }

    public interface Predicate<T> {
        boolean test(T t);
    }

    public static String pointerToString(long pointer) {
        return Long.toUnsignedString(pointer, 16);
    }

    public static long pointerFromString(String strPointer) {
        try {
            return Long.parseUnsignedLong(strPointer, 16);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    final private static int BUFFER_SIZE = 2048;
    public static CharSequence readInputStream(InputStream stream, int wantedSize) throws IOException {
        InputStreamReader reader = new InputStreamReader(stream);
        StringBuilder stringBuilder = new StringBuilder(wantedSize + BUFFER_SIZE);
        CharBuffer buffer =  CharBuffer.allocate(BUFFER_SIZE);
        int length;
        while ((length = reader.read(buffer)) != -1) {
            buffer.flip();
            stringBuilder.append(buffer, 0, length);
            if (stringBuilder.length() >= wantedSize) break;
        }
        try {
            stream.close();
        } catch (IOException e) {
            kitty.warn("readInputStream(): exception while closing stream", e);
        }
        return stringBuilder;
    }

    public static CharSequence getLongStringSummary(CharSequence seq) {
        if (seq.length() <= 200) return seq;
        String out = "\"" + seq.subSequence(0, 80) + "..." + seq.subSequence(seq.length() - 80, seq.length()) + "\"";
        return out.replace("\n", "\\n");
    }

    public static CharSequence getLongStringExcerpt(CharSequence seq, int around) {
        if (seq.length() <= 200) return seq;
        return seq.subSequence(
                Math.max(0, around - 80),
                Math.min(seq.length(), around + 80))
                .toString().replace("\n", "\\n");
    }

    static CharSequence replaceWithSpaces(CharSequence seq, Pattern pattern) {
        StringBuilder sb = null;
        int cursor = 0;

        Matcher m = pattern.matcher(seq);
        while (m.find()) {
            if (sb == null) sb = new StringBuilder(seq.length());
            int start = m.start();
            int end = m.end();
            if (cursor != start)
                sb.append(seq, cursor, start);
            if (end != start)
                addSpaces(sb, end - start);
            cursor = end;
        }
        if (cursor == 0)
            return seq;
        if (cursor != seq.length())
            sb.append(seq, cursor, seq.length());
        return sb;
    }

    public static void addSpaces(StringBuilder sb, int n) {
        sb.ensureCapacity(sb.length() + n);
        while (n-- > 0)
            sb.append(' ');
    }

    public static void runInBackground(Runnable runnable) {
        new AsyncTask<Void, Void, Void>() {
            @Override protected Void doInBackground(Void... voids) {
                runnable.run();
                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public static String replaceAfterFind(Matcher matcher, String replacement) {
        StringBuffer sb = new StringBuffer();
        matcher.appendReplacement(sb, replacement);
        return sb.toString();
    }

    public static CharSequence join(CharSequence separator, Collection<?> list) {
        int i = 0, size = list.size();
        StringBuilder sb = new StringBuilder();
        for (Object element : list) {
            sb.append(element);
            if (++i < size) sb.append(separator);
        }
        return sb;
    }

    @SafeVarargs public static <T> Set<T> makeSet(T... t) {
        return new HashSet<>(Arrays.asList(t));
    }

    public static <T> T[] reversed(T[] array) {
        T[] copy = array.clone();
        Collections.reverse(Arrays.asList(copy));
        return copy;
    }

    abstract public static class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
}
