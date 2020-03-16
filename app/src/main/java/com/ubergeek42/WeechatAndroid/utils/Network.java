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

import com.ubergeek42.cats.Cat;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import static com.ubergeek42.WeechatAndroid.utils.Assert.assertThat;

public class Network {
    final private @Root Kitty kitty = Kitty.make();

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

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean hasProperty(Property property) {
        return properties.contains(property);
    }

    @Cat private void setProperties(EnumSet<Property> p) {
        boolean justConnected = !properties.contains(Property.CONNECTED) && p.contains(Property.CONNECTED);
        properties = p;
        if (justConnected) {
            for (Callback callback : contexts.values())
                if (callback != null) callback.onConnected();
        }
    }

    final private Map<Context, Callback> contexts = new HashMap<>();

    @Cat public void register(@NonNull Context context, @Nullable Callback callback) {
        if (contexts.isEmpty()) {
            Context applicationContext = context.getApplicationContext();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                getManager(applicationContext).registerDefaultNetworkCallback(networkCallback);
            } else {
                applicationContext.registerReceiver(broadcastReceiver,
                        new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
            }
        }
        assertThat(contexts.containsKey(context)).isFalse();
        contexts.put(context, callback);
    }

    @Cat public void unregister(@NonNull Context context) {
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
    // called if the network's metering status changes
    final private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        final private @Root Kitty kitty_b = kitty.kid("Broadcast");

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

    final private ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        final private @Root Kitty kitty_n = kitty.kid("Callback");

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
