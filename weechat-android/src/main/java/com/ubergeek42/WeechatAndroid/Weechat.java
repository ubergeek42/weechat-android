/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.ubergeek42.WeechatAndroid;

import android.app.Application;

import com.ubergeek42.WeechatAndroid.service.Events;
import com.ubergeek42.WeechatAndroid.service.Notificator;
import com.ubergeek42.WeechatAndroid.service.P;
import com.ubergeek42.WeechatAndroid.service.RelayService.STATE;

import org.greenrobot.eventbus.EventBus;

import java.util.EnumSet;

public class Weechat extends Application {
    @Override public void onCreate() {
        super.onCreate();
        P.init(getApplicationContext());
        P.restoreStuff();
        Notificator.init(this);
        EventBus.builder().logNoSubscriberMessages(false).eventInheritance(false).installDefaultEventBus();
        EventBus.getDefault().postSticky(new Events.StateChangedEvent(EnumSet.of(STATE.STOPPED)));
    }
}
