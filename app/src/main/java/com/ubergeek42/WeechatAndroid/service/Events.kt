// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package com.ubergeek42.WeechatAndroid.service

import com.ubergeek42.WeechatAndroid.relay.Buffer
import com.ubergeek42.WeechatAndroid.relay.as0x
import com.ubergeek42.WeechatAndroid.utils.Assert
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.greenrobot.eventbus.EventBus
import java.util.EnumSet

class Events {
    data class StateChangedEvent(@JvmField val state: EnumSet<RelayService.STATE>)

    data class ExceptionEvent(@JvmField val e: Exception)

    data class SendMessageEvent(@JvmField val message: String) {
        companion object {
            fun fire(message: String) {
                Assert.assertThat(message.endsWith("\n")).isFalse()
                EventBus.getDefault().post(SendMessageEvent(message))
            }

            fun fireInput(buffer: Buffer, input: String?) {
                if (input.isNullOrEmpty()) return

                P.addSentMessage(input)

                input.lineSequence().filter(String::isNotEmpty).forEach { line ->
                    fire(buildJsonObject {
                        put("request", "POST /api/input")

                        putJsonObject("body") {
                            put("buffer_id", buffer.pointer)
                            put("command", line)
                        }
                    }.toString())
                }
            }
        }
    }
}