package com.ubergeek42.weechat.relay.connection;

import com.ubergeek42.weechat.relay.RelayMessage;

public interface IObserver {
    void onStateChanged(RelayConnection.STATE state);
    void onException(Exception e);
    void onMessage(RelayMessage message);
}
