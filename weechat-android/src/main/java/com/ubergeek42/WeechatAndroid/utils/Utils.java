/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.ubergeek42.WeechatAndroid.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Iterator;

public class Utils {

    final private static Logger logger = LoggerFactory.getLogger("Utils");

    public static void setImageDrawableWithFade(final @NonNull ImageView imageView,
                                                final @NonNull Drawable drawable, int duration) {
        Drawable current = imageView.getDrawable();

        if ((current != null) && (current instanceof TransitionDrawable))
            current = ((LayerDrawable) current).getDrawable(1);

        if (current != null) {
            TransitionDrawable transitionDrawable = new TransitionDrawable(new Drawable[]{current, drawable});
            transitionDrawable.setCrossFadeEnabled(true);
            imageView.setImageDrawable(transitionDrawable);
            transitionDrawable.startTransition(duration);
        } else {
            imageView.setImageDrawable(drawable);
        }
    }

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
            logger.error("deserialize()", e);
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
            logger.error("serialize()", e);
            return null;
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// string cuts

    public static @NonNull String unCrLf(@NonNull String text) {
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

    public static @NonNull byte[] readFromUri(Context context, Uri uri) throws IOException {
        InputStream in = null; int len;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        try {
            in = context.getContentResolver().openInputStream(uri);
            if (in == null) throw new IOException("Input stream is null");
            while ((len = in.read(buffer)) != -1) out.write(buffer, 0, len);
            if (out.size() == 0) throw new IOException("File is empty");
            return out.toByteArray();
        } finally {
            try {if (in != null) in.close();} catch (IOException ignored) {}
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

    public static void runOnUiThread(Runnable action) {
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.post(action);
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// debug stuff

    // get permission for showing system alert, see next method
    @RequiresApi(api = Build.VERSION_CODES.M)
    public static void checkDrawOverlayPermission(AppCompatActivity activity) {
        if (!Settings.canDrawOverlays(activity.getApplicationContext())) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + activity.getPackageName()));
            activity.startActivityForResult(intent, 123);
        }
    }

    public static void showSystemAlert(final Context ctx, final String message, final Object... args) {
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> {
            AlertDialog dialog = new AlertDialog.Builder(ctx.getApplicationContext()).setMessage(String.format(message, args)).create();
            if (dialog.getWindow() == null) throw new RuntimeException("dialog.getWindow() is null");
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            dialog.show();
        });
    }

    public static void showLongToast(final Context ctx, final String message, final Object... args) {
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> Toast.makeText(ctx, String.format(message, args), Toast.LENGTH_LONG).show());
    }

    public static String getExceptionAsString(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }

    public static String getIntentExtrasAsString(Intent i){
        Bundle bundle = i.getExtras();
        if (bundle == null) return "[]";
        Iterator<String> it = bundle.keySet().iterator();
        String out = "[";
        while (it.hasNext()) {
            String key = it.next();
            out += key + "=" + bundle.get(key) + ", ";
        }
        return out.substring(0, out.length() - 2) + "]";
    }

    public static void saveLogCatToFile(Context ctx) {
        String path = ctx.getDir("log", Context.MODE_PRIVATE) + "/logcat.txt";
        logger.trace("saving log to: {}", path);
        try {
            bash("echo \\n\\n\\n >> " + path);
            bash("date >> " + path);
            bash("logcat -t 100 >> " + path);
        } catch (IOException | InterruptedException e) {
            logger.error("error writing file", e);
        }
    }

    private static void bash(String command) throws IOException, InterruptedException {
        if (Runtime.getRuntime().exec(new String[] {"sh", "-c", command}).waitFor() != 0)
            throw new IOException("error while executing: " + command);
    }
}
