// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package com.ubergeek42.WeechatAndroid.service

import com.ubergeek42.WeechatAndroid.relay.Buffer
import com.ubergeek42.WeechatAndroid.relay.as0x
import com.ubergeek42.WeechatAndroid.utils.Assert
import org.greenrobot.eventbus.EventBus
import java.util.EnumSet
import java.util.Locale

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
                    fire("input ${buffer.pointer.as0x} $line")
                }
            }
        }
    }
}