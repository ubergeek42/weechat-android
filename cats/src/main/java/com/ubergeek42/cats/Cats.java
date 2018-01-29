package com.ubergeek42.cats;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;

public class Cats {
    static String TAG = "🐱";
    static String FORMAT_WITH_PREFIX =    "%-4s : %3$-4s : %2$s: %4$s";
    static String FORMAT_WITHOUT_PREFIX = "%-4s : %s: %4$s";

    final static HashSet<String> DISABLED = new HashSet<>();

    public static void setupKittens(Context ctx) {
        if (BuildConfig.DEBUG) prepareExceptionHandler();
        loadConfiguration(ctx);
    }

    private static void prepareExceptionHandler() {
        Thread.UncaughtExceptionHandler original = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler ((Thread t, Throwable e) -> {
            Kitty.dumpLingerieIfPresent(true);
            original.uncaughtException(t, e);
        });
    }

    private static void loadConfiguration(Context ctx) {
        try {
            InputStream is = ctx.getApplicationContext().getResources().openRawResource(R.raw.cats);
            BufferedReader in = new BufferedReader(new InputStreamReader(is));
            String line = in.readLine();
            for (int n = 0; line != null; line = in.readLine()) {
                line = line.trim();
                if (line.startsWith("#") || "".equals(line)) continue;
                switch (n++) {
                    case 0: TAG = line; break;
                    case 1: FORMAT_WITH_PREFIX = line; break;
                    case 2: FORMAT_WITHOUT_PREFIX = line; break;
                    default: DISABLED.add(line); break;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("error while loading kitty configuration", e);
        }
    }
}
