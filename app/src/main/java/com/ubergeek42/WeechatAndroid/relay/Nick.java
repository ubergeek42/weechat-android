//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.

package com.ubergeek42.WeechatAndroid.relay;


public class Nick {
    final long pointer;
    String prefix;
    String name;
    boolean away;

    Nick(long pointer, String prefix, String name, boolean away) {
        this.prefix = prefix;
        this.name = name;
        this.pointer = pointer;
        this.away = away;
    }

    public String asString() {
        return prefix + name;
    }

    public String getName() {
        return name;
    }

    public boolean isAway() {
        return away;
    }
}
