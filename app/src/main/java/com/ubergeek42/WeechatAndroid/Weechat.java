// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package com.ubergeek42.WeechatAndroid;

import android.app.Application;
import android.content.Context;
import android.os.Handler;

import com.ubergeek42.WeechatAndroid.media.CachePersist;
import com.ubergeek42.WeechatAndroid.service.Events;
import com.ubergeek42.WeechatAndroid.notifications.NotificatorKt;
import com.ubergeek42.WeechatAndroid.service.P;
import com.ubergeek42.WeechatAndroid.service.RelayService.STATE;
import com.ubergeek42.WeechatAndroid.upload.UploadDatabase;
import com.ubergeek42.cats.Cats;

import net.danlew.android.joda.JodaTimeAndroid;

import org.greenrobot.eventbus.EventBus;

import java.util.EnumSet;


public class Weechat extends Application {
    static Thread mainThread = Thread.currentThread();
    static Handler mainHandler = new Handler();
    static public Context applicationContext;

    @Override public void onCreate() {
        super.onCreate();
        applicationContext = getApplicationContext();
        JodaTimeAndroid.init(applicationContext);
        Cats.setup(applicationContext);
        CachePersist.restore();
        P.init(getApplicationContext());
        P.restoreStuff();
        UploadDatabase.restore();   // wants cache max age from P
        NotificatorKt.initializeNotificator(this);
        EventBus.builder().logNoSubscriberMessages(false).eventInheritance(false).installDefaultEventBus();
        EventBus.getDefault().postSticky(new Events.StateChangedEvent(EnumSet.of(STATE.STOPPED)));
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// main thread
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // like runOnUiThread, but always queue
    public static void runOnMainThread(Runnable action) {
        mainHandler.post(action);
    }

    public static void runOnMainThread(Runnable action, long delay) {
        mainHandler.postDelayed(action, delay);
    }

    public static void runOnMainThreadASAP(Runnable action) {
        if (Thread.currentThread() == mainThread) action.run();
        else mainHandler.post(action);
    }
}
