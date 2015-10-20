/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.ubergeek42.WeechatAndroid.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

public class Connectivity extends BroadcastReceiver {
    private RelayService bone;
    private ConnectivityManager manager;
    private boolean networkAvailable = true;

    public void register(RelayService bone) {
        this.bone = bone;
        this.manager = (ConnectivityManager) bone.getSystemService(Context.CONNECTIVITY_SERVICE);
        bone.registerReceiver(
                this,
                new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        );
    }

    public void unregister() {
        bone.unregisterReceiver(this);
        this.bone = null;
        this.manager = null;
    }

    public boolean isNetworkAvailable() {
        NetworkInfo networkInfo = manager.getActiveNetworkInfo();
        return networkAvailable = (networkInfo != null && networkInfo.isConnected());
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (!networkAvailable && isNetworkAvailable() && bone.mustAutoConnect())
            bone.startThreadedConnectLoop();
    }
}
