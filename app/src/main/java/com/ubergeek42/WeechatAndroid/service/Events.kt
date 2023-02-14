// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
package com.ubergeek42.WeechatAndroid.service

import android.text.TextUtils
import com.ubergeek42.WeechatAndroid.relay.Buffer
import com.ubergeek42.WeechatAndroid.utils.Assert
import org.greenrobot.eventbus.EventBus
import java.util.EnumSet
import java.util.Locale

class Events {
    class StateChangedEvent(val state: EnumSet<RelayService.STATE>) {
        override fun toString(): String {
            return "StateChangedEvent(state=$state)"
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    class ExceptionEvent internal constructor(val e: Exception) {
        override fun toString(): String {
            return "ExceptionEvent(e=$e)"
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    class SendMessageEvent private constructor(val message: String) {
        override fun toString(): String {
            return "SendMessageEvent(message=$message)"
        }

        companion object {
            fun fire(message: String) {
                Assert.assertThat(message.endsWith("\n")).isFalse()
                EventBus.getDefault().post(SendMessageEvent(message))
            }

            fun fire(message: String, vararg args: Any) {
                fire(String.format(Locale.ROOT, message, *args))
            }

            fun fireInput(buffer: Buffer, input: String?) {
                if (TextUtils.isEmpty(input)) return
                P.addSentMessage(input)
                for (line in input!!.split("\n".toRegex())
                        .dropLastWhile { it.isEmpty() }
                        .toTypedArray()) if (!TextUtils.isEmpty(line)) fire("input 0x%x %s",
                                                                            buffer.pointer,
                                                                            line)
            }
        }
    }
}