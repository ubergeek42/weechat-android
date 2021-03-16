//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
package com.ubergeek42.WeechatAndroid.relay

class Nick(
    @JvmField val pointer: Long,
    @JvmField val prefix: String,
    @JvmField val name: String,
    @JvmField val away: Boolean,
) {
    fun asString() = prefix + name
}