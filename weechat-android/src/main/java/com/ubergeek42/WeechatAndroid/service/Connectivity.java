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
import android.support.annotation.MainThread;

public class Connectivity extends BroadcastReceiver {
    private RelayService bone;
    private ConnectivityManager manager;
    private boolean networkAvailable = true;

    // TODO manager can be null?
    @MainThread public void register(RelayService bone) {
        this.bone = bone;
        this.manager = (ConnectivityManager) bone.getSystemService(Context.CONNECTIVITY_SERVICE);
        bone.registerReceiver(
                this,
                new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        );
        networkAvailable = getNetworkAvailable();
    }

    @MainThread public void unregister() {
        bone.unregisterReceiver(this);
        this.bone = null;
        this.manager = null;
    }

    public boolean isNetworkAvailable() {
        return networkAvailable;
    }

    // this message can be called multiple times
    // make sure we only call _start() when network goes from unavailable to available
    // NOTE: inside onReceive(), getNetworkAvailable() will only return false if the device sees
    // no *possible* connections. outside of this method getNetworkAvailable() can return false
    // if the device is currently switching between networks.
    @MainThread @Override public void onReceive(Context context, Intent intent) {
        if (networkAvailable == getNetworkAvailable()) return;
        networkAvailable = !networkAvailable;
        if (networkAvailable && P.reconnect && bone.state.contains(RelayService.STATE.STARTED))
            bone._start();
    }

    private boolean getNetworkAvailable() {
        NetworkInfo networkInfo = manager.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }
}
