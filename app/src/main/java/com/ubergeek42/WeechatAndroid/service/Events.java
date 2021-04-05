// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package com.ubergeek42.WeechatAndroid.service;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.text.TextUtils;

import com.ubergeek42.WeechatAndroid.relay.Buffer;
import com.ubergeek42.WeechatAndroid.service.RelayService.STATE;

import org.greenrobot.eventbus.EventBus;

import java.util.EnumSet;
import java.util.Locale;

import static com.ubergeek42.WeechatAndroid.fragments.CharacterStyleMenuCallbackKt.toMircCodedString;
import static com.ubergeek42.WeechatAndroid.utils.Assert.assertThat;


public class Events {

    public static class StateChangedEvent {
        final public EnumSet<STATE> state;

        public StateChangedEvent(EnumSet<STATE> state) {
            this.state = state;
        }

        @Override public @NonNull String toString() {
            return "StateChangedEvent(state=" + state + ")";
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class ExceptionEvent {
        final public Exception e;

        ExceptionEvent(Exception e) {
            this.e = e;
        }

        @Override public @NonNull String toString() {
            return "ExceptionEvent(e=" + e + ")";
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class SendMessageEvent {
        final public String message;

        private SendMessageEvent(String message) {
            this.message = message;
        }

        public static void fire(@NonNull String message) {
            assertThat(message.endsWith("\n")).isFalse();
            EventBus.getDefault().post(new SendMessageEvent(message));
        }

        public static void fire(@NonNull String message, @NonNull Object... args) {
            fire(String.format(Locale.ROOT, message, args));
        }

        public static void fireInput(@NonNull Buffer buffer, @Nullable CharSequence input) {
            if (input == null) return;
            if (TextUtils.isEmpty(input)) return;

            P.addSentMessage(input);
            input = toMircCodedString(input);

            for (String line : input.toString().split("\n"))
                if (!TextUtils.isEmpty(line))
                    fire("input 0x%x %s", buffer.pointer, line);
        }

        @Override public @NonNull String toString() {
            return "SendMessageEvent(message=" + message + ")";
        }
    }
}
