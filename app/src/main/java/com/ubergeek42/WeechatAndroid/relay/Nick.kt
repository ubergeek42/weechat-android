//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.

package com.ubergeek42.WeechatAndroid.relay;


public class Nick {
    final public long pointer;
    final String prefix;
    final public String name;
    final public boolean away;

    public Nick(long pointer, String prefix, String name, boolean away) {
        this.pointer = pointer;
        this.prefix = prefix;
        this.name = name;
        this.away = away;
    }

    public String asString() {
        return prefix + name;
    }
}
