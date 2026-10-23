package com.ubergeek42.WeechatAndroid.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import static com.ubergeek42.WeechatAndroid.utils.Assert.assertThat;
import static com.ubergeek42.WeechatAndroid.utils.LoggingKt.jtrace;

public class Network {
    final private static Network instance = new Network();

    public static Network get() {
        return instance;
    }

    public enum Property {
        CONNECTED,
        WIFI,
        UNMETERED
    }

    private volatile EnumSet<Property> properties = EnumSet.noneOf(Property.class);

    public interface Callback {
        void onConnected();
    }

    public boolean hasProperty(Property property) {
        return properties.contains(property);
    }

    private void setProperties(EnumSet<Property> p) {
        jtrace(() -> String.format("Network: setProperties(p=%s)", p));
        boolean justConnected = !properties.contains(Property.CONNECTED) && p.contains(Property.CONNECTED);
        properties = p;
        if (justConnected) {
            for (Callback callback : contexts.values())
                if (callback != null) callback.onConnected();
        }
    }

    final private Map<Context, Callback> contexts = new HashMap<>();

    public void register(@NonNull Context context, @Nullable Callback callback) {
        if (contexts.isEmpty()) {
            Context applicationContext = context.getApplicationContext();
            ConnectivityManager manager = getManager(applicationContext);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                manager.registerDefaultNetworkCallback(networkCallback);
                android.net.Network network = manager.getActiveNetwork();
                NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
                if (network != null && capabilities != null)
                    networkCallback.onCapabilitiesChanged(network, capabilities);
            } else {
                applicationContext.registerReceiver(broadcastReceiver,
                        new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
                broadcastReceiver.onReceive(applicationContext, null);
            }
        }
        assertThat(contexts.containsKey(context)).isFalse();
        contexts.put(context, callback);
    }

    public void unregister(@NonNull Context context) {
        assertThat(contexts.containsKey(context)).isTrue();
        contexts.remove(context);
        if (contexts.isEmpty()) {
            Context applicationContext = context.getApplicationContext();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                getManager(applicationContext).unregisterNetworkCallback(networkCallback);
            } else {
                applicationContext.unregisterReceiver(broadcastReceiver);
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // broadcast receiver effectively behaves just like the network callback, however it's not
    // called if the network's metering status changes. it seems to be always called on registration
    final private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            ConnectivityManager manager = getManager(context);
            NetworkInfo info = manager.getActiveNetworkInfo();
            if (info == null || !info.isConnected()) {
                setProperties(EnumSet.noneOf(Property.class));
            } else {
                EnumSet<Property> p = EnumSet.of(Property.CONNECTED);
                if (info.getType() == ConnectivityManager.TYPE_WIFI)
                    p.add(Property.WIFI);
                if (!manager.isActiveNetworkMetered())
                    p.add(Property.UNMETERED);
                setProperties(p);
            }
        }
   };

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // on some earlier versions of android, onCapabilitiesChanged() is not called upon callback
    // registration, while onAvailable() is. it's not clear whether onAvailable() is guaranteed to
    // be called at all. todo make sure that onCapabilitiesChanged is called upon every connection
    final private ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override public void onLost(@NonNull android.net.Network network) {
            setProperties(EnumSet.noneOf(Property.class));
        }

        @Override public void onCapabilitiesChanged(@NonNull android.net.Network network, @NonNull NetworkCapabilities capabilities) {
            EnumSet<Property> p = EnumSet.of(Property.CONNECTED);
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
                p.add(Property.UNMETERED);
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
                p.add(Property.WIFI);
            setProperties(p);
        }
    };

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static ConnectivityManager getManager(Context context) {
        ConnectivityManager manager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        assertThat(manager).isNotNull();
        return manager;
    }
}
