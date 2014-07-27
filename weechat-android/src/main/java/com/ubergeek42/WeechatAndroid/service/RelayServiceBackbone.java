package com.ubergeek42.WeechatAndroid.service;

import com.ubergeek42.weechat.relay.RelayConnection;

/**
 * Wow such abstrackshun
 */
public class RelayServiceBackbone {
    public RelayConnection connection;
    public Buffers buffers;
    public Nicklists nicklists;

    public RelayServiceBackbone(RelayConnection connection) {
        this.connection = connection;

        buffers = new Buffers(this);
        nicklists = new Nicklists(this);

        // Subscribe to any future changes
        connection.sendMsg("sync");
    }
}
