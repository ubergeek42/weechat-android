/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.ubergeek42.weechat.relay.connection;

import com.ubergeek42.weechat.relay.RelayMessage;

public interface Connection {

    ////////////////////////////////////////////////////////////////////////////////////// lifecycle

    enum STATE {
        UNKNOWN,
        CONNECTING,
        CONNECTED,
        DISCONNECTED,
    }

    STATE getState();

    void connect();
    void disconnect();

    /////////////////////////////////////////////////////////////////////////////////////////// send

    void sendMessage(String string);

    /////////////////////////////////////////////////////////////////////////////////////// observer

    void setObserver(Observer observer);

    interface Observer {
        void onStateChanged(STATE state);
        void onException(Exception e);
        void onMessage(RelayMessage message);
    }
}
