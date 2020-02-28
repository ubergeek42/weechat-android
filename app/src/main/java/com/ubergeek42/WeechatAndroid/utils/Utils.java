// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package com.ubergeek42.WeechatAndroid.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.net.Uri;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;

import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.CharBuffer;
import java.text.SimpleDateFormat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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

    @SuppressLint("SimpleDateFormat")
    public static boolean isValidTimestampFormat(@Nullable String s) {
        if (s == null)
            return false;
        try {
            new SimpleDateFormat(s);
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

    public static boolean isEmpty(byte[] bytes) {
        return bytes == null || bytes.length == 0;
    }

    public static <T> boolean isEmpty(T[] array) {
        return array == null || array.length == 0;
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
        return Long.toHexString(pointer);
    }

    public static long pointerFromString(String strPointer) {
        try {
            return Long.parseLong(strPointer, 16);
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
        stream.close();
        return stringBuilder;
    }

    public static CharSequence getLongStringSummary(CharSequence seq) {
        if (seq.length() <= 200) return seq;
        String out = "\"" + seq.subSequence(0, 80) + "..." + seq.subSequence(seq.length() - 80, seq.length()) + "\"";
        return out.replace("\n", "\\n");
    }
}
