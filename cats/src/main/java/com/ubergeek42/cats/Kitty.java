package com.ubergeek42.cats;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import static android.util.Log.ASSERT;
import static android.util.Log.DEBUG;
import static android.util.Log.ERROR;
import static android.util.Log.INFO;
import static android.util.Log.VERBOSE;
import static android.util.Log.WARN;

public abstract class Kitty {
    final @NonNull String tag;
    boolean enabled = false;

    public static RootKitty make() {
        return new RootKitty(Utils.getCallerClassSimpleName());
    }

    public static RootKitty make(String tag) {
        return new RootKitty(tag);
    }

    Kitty(@NonNull String tag) {
        this.tag = tag.intern();
    }

    abstract public KidKitty kid(@NonNull String tag);
    abstract public void setPrefix(@Nullable String prefix);
    abstract String getTag();
    abstract @Nullable String getPrefix();

    ////////////////////////////////////////////////////////////////////////////////////////////////

    static Printer printer = new Printer();

    ////////////////////////////////////////////////////////////////////////////////////////////////

    void aspectLog(int level, boolean linger, @NonNull String method, @NonNull CharSequence message) {
        //System.out.println(String.format("%s:%s:%s '%s'", level, linger, method, message));
        synchronized (Kitty.class) {
            if (linger) {
                if (lingerieIsValid(level, method)) {
                    lingerie.message.append(" » ").append(message);
                } else {
                    dumpLingerieIfPresent(false);
                    lingerie = new Lingerie(this, level, method,
                            message instanceof StringBuilder ? (StringBuilder) message : new StringBuilder(message));
                }
            } else {
                if (lingerieIsValid(level, method)) {
                    message = lingerie.message.append((message.charAt(0) == '←') ? " " :" » ").append(message);
                } else {
                    dumpLingerieIfPresent(false);
                }
                print(level, Thread.currentThread(), message);
                lingerie = null;
            }
        }
    }

    private void print(int level, Thread thread, CharSequence message) {
        String tag = getTag();
        String prefix = getPrefix();
        StringBuilder sb = Utils.addAndPad(new StringBuilder(64), thread.getName(), 4).append(" : ");
        if (prefix != null) Utils.addAndPad(sb, prefix, 4).append(" : ");
        sb.append(tag).append(": ").append(message);
        printer.println(level, "🐱", sb.toString());
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // these are non-aspect logger calls
    // proguard will be responsible for removing debug, trace and wtf calls

    private void log(int level, String message) {
        Log.println(level, getTag(), message);
    }

    private void log(int level, String message, Object... args) {
        Log.println(level, getTag(), Utils.format(message, args));
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override public String toString() {
        return "Kitty(tag=" + getTag() + ", enabled=" + enabled + ")";
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static Lingerie lingerie;                       // synchronize me!

    static class Lingerie {
        final @NonNull Kitty kitty;
        final @NonNull String method;
        final int level;
        final @NonNull Thread thread;
        @NonNull StringBuilder message;

        Lingerie(@NonNull Kitty kitty, int level, @NonNull String method, @NonNull StringBuilder message) {
            this.kitty = kitty;
            this.method = method;
            this.level = level;
            this.thread = Thread.currentThread();
            this.message = message;
        }
    }
    
    boolean lingerieIsValid(int level, String method) {     // synchronize me!
        return lingerie != null &&
                lingerie.kitty == this &&
                lingerie.method.equals(method) &&
                lingerie.level == level &&
                lingerie.thread == Thread.currentThread();
    }

    synchronized static void dumpLingerieIfPresent(boolean error) {
        synchronized (Kitty.class) {
            if (lingerie == null) return;
            lingerie.kitty.print(lingerie.level, lingerie.thread, error ? lingerie.message  + " 🔥" : lingerie.message + " ×");
            lingerie = null;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // the following methods don't actually run in debug mode
    // instead aspectj takes over and executes its code in their place

    public void wtf(String message) {
        log(ASSERT, message);
    }

    public void wtf(String message, final Object... args) {
        log(ASSERT, message, args);
    }

    public void error(String message) {
        log(ERROR, message);
    }

    public void error(String message, final Object... args) {
        log(ERROR, message, args);
    }

    public void warn(String message) {
        log(WARN, message);
    }

    public void warn(String message, final Object... args) {
        log(WARN, message, args);
    }

    public void info(String message) {
        log(INFO, message);
    }

    public void info(String message, final Object... args) {
        log(INFO, message, args);
    }

    public void debug(String message) {
        log(DEBUG, message);
    }

    public void debug(String message, final Object... args) {
        log(DEBUG, message, args);
    }

    public void trace(String message) {
        log(VERBOSE, message);
    }

    public void trace(String message, final Object... args) {
        log(VERBOSE, message, args);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public void wtfl(String message) {
        log(ASSERT, message);
    }

    public void wtfl(String message, final Object... args) {
        log(ASSERT, message, args);
    }

    public void errorl(String message) {
        log(ERROR, message);
    }

    public void errorl(String message, final Object... args) {
        log(ERROR, message, args);
    }

    public void warnl(String message) {
        log(WARN, message);
    }

    public void warnl(String message, final Object... args) {
        log(WARN, message, args);
    }

    public void infol(String message) {
        log(INFO, message);
    }

    public void infol(String message, final Object... args) {
        log(INFO, message, args);
    }

    public void debugl(String message) {
        log(DEBUG, message);
    }

    public void debugl(String message, final Object... args) {
        log(DEBUG, message, args);
    }

    public void tracel(String message) {
        log(VERBOSE, message);
    }

    public void tracel(String message, final Object... args) {
        log(VERBOSE, message, args);
    }
}
