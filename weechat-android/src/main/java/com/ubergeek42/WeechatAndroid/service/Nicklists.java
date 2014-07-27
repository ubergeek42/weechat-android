package com.ubergeek42.WeechatAndroid.service;

import com.ubergeek42.weechat.relay.RelayConnection;
import com.ubergeek42.weechat.relay.RelayMessageHandler;
import com.ubergeek42.weechat.relay.protocol.RelayObject;

/**
 * Created by sq on 25/07/2014.
 */
public class Nicklists implements RelayMessageHandler {
    RelayServiceBackbone bone;
    RelayConnection connection;

    Nicklists(RelayServiceBackbone bone) {
        this.bone = bone;
        this.connection = bone.connection;

        // Handle changes to the nicklist for buffers
        connection.addHandler("nicklist", this);
        connection.addHandler("_nicklist", this);
        connection.addHandler("_nicklist_diff", this);
    }

    @Override
    public void handleMessage(RelayObject obj, String id) {

    }
}
