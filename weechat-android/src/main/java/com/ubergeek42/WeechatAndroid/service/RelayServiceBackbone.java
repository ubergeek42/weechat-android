package com.ubergeek42.WeechatAndroid.service;

import com.ubergeek42.weechat.relay.RelayConnection;

/**
 * Wow such abstrackshun
 */
public class RelayServiceBackbone {
    public RelayConnection connection;
    public BufferList buffer_list;
    public Nicklists nicklists;

    public RelayServiceBackbone(RelayConnection connection) {
        this.connection = connection;

        buffer_list = new BufferList(this);
        nicklists = new Nicklists(this);

        // Subscribe to any future changes
        connection.sendMsg("sync");
    }
}
