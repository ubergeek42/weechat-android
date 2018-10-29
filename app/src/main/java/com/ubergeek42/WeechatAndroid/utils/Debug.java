// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package com.ubergeek42.WeechatAndroid.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.WindowManager;
import android.widget.Toast;

import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.Set;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

@SuppressWarnings("unused")
public class Debug {
    final private static @Root Kitty kitty = Kitty.make();

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
        StringBuilder out = new StringBuilder("[");
        while (it.hasNext()) {
            String key = it.next();
            out.append(key).append("=").append(bundle.get(key)).append(", ");
        }
        return out.substring(0, out.length() - 2) + "]";
    }

    public static void saveLogCatToFile(Context ctx) {
        String path = ctx.getDir("log", Context.MODE_PRIVATE) + "/logcat.txt";
        kitty.trace("saving log to: {}", path);
        try {
            bash("echo \\n\\n\\n >> " + path);
            bash("date >> " + path);
            bash("logcat -t 100 >> " + path);
        } catch (IOException | InterruptedException e) {
            kitty.error("error writing file", e);
        }
    }

    private static void bash(String command) throws IOException, InterruptedException {
        if (Runtime.getRuntime().exec(new String[] {"sh", "-c", command}).waitFor() != 0)
            throw new IOException("error while executing: " + command);
    }

    // allows the application to print A LOT of logging
    private static void turnOffChatty() {
        int pid = android.os.Process.myPid();
        String whiteList = "logcat -P '" + pid + "'";
        try {
            Runtime.getRuntime().exec(whiteList).waitFor();
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }
    }

    public static void dumpThreads() {
        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
        for (Thread t : threadSet) {
            kitty.info("%s -> %s [a %s, d %s]", t.getName(), t.getState(), t.isAlive(), t.isDaemon());
        }
    }
}
