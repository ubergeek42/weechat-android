package com.ubergeek42.weechat.relay.protocol;

public class ApiRelayObject extends RelayObject {
    public Object inner;

    ApiRelayObject(Object obj) {
        this.inner = obj;
    }
}