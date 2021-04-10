package com.ubergeek42.cats;

import android.content.Context;
import androidx.annotation.NonNull;

import org.aspectj.lang.JoinPoint;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

public class Cats {
    final static HashSet<String> disabled = new HashSet<>();
    final static private Map<Object, Kitty> kitties = Collections.synchronizedMap(new WeakHashMap<>());
    final static private Map<Object, CatInfo> cats = Collections.synchronizedMap(new IdentityHashMap<>());

    public static void setup(Context ctx) {
        if (!BuildConfig.DEBUG) throw new RuntimeException("cats should only work in debug mode");
        prepareExceptionHandler();
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
            for (String line = in.readLine(); line != null; line = in.readLine()) {
                line = line.trim();
                if (line.startsWith("#") || "".equals(line)) continue;
                disabled.add(line);
            }
        } catch (Exception e) {
            throw new RuntimeException("error while loading kitty configuration", e);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    static @NonNull Kitty getKitty(JoinPoint point) {
        Kitty kitty = null;

        Object object = point.getThis();
        if (object != null) kitty = kitties.get(object);
        if (kitty != null) return kitty;

        Class cls = point.getSignature().getDeclaringType();
        kitty = kitties.get(cls);
        if (kitty != null) return kitty;

        // when constructor is called, non-static fields are not initialized until after
        // the constructor calls its super(). in this case, and also when no kitty has been made,
        // return a default static kitty
        kitty = Kitty.make(getClassName(cls));
        setKitty(cls, kitty);
        return kitty;
    }

    private static String getClassName(@NonNull Class cls) {
        String name = cls.getSimpleName();
        while (true) {
            cls = cls.getEnclosingClass();
            if (cls == null) return name;
            name = cls.getSimpleName() + "." + name;
        }
    }

    @SuppressWarnings("WeakerAccess")
    public static Kitty getKitty(Class cls) {
        return kitties.get(cls);
    }

    static void setKitty(@NonNull Object o, @NonNull Kitty kitty) {
        kitties.put(o, kitty);
        //System.out.println("::: setting kitty for: " + o);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // cache Cat annotations in a concurrent hash map since
    // annotation method calls such as cat.value() are rather expensive on android

    static CatInfo getCatInfo(Object o) {
        CatInfo info = cats.get(o);
        if (info != null) return info;
        info = new CatInfo(o);
        cats.put(o, info);
        return info;
    }

    static class CatInfo {
        String tag = "";
        boolean linger = false;
        boolean exit = false;

        CatInfo(Object o) {
            if (o instanceof Cat) {
                Cat cat = (Cat) o;
                tag = cat.value().intern();
                exit = cat.exit();
                linger = exit || cat.linger();
            } else if (o instanceof CatD) {
                CatD cat = (CatD) o;
                tag = cat.value().intern();
                exit = cat.exit();
                linger = exit || cat.linger();
            }
        }
    }
}
