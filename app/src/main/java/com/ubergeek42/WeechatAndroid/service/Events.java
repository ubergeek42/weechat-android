// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package com.ubergeek42.WeechatAndroid.service;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.text.TextUtils;

import com.ubergeek42.WeechatAndroid.service.RelayService.STATE;

import org.greenrobot.eventbus.EventBus;
import org.junit.Assert;

import java.util.EnumSet;
import java.util.Locale;


public class Events {

    public static class StateChangedEvent {
        final public EnumSet<STATE> state;

        public StateChangedEvent(EnumSet<STATE> state) {
            this.state = state;
        }

        @Override public String toString() {
            return "StateChangedEvent(state=" + state + ")";
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class ExceptionEvent {
        final public Exception e;

        ExceptionEvent(Exception e) {
            this.e = e;
        }

        @Override public String toString() {
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
            Assert.assertFalse(message.endsWith("\n"));
            EventBus.getDefault().post(new SendMessageEvent(message));
        }

        public static void fire(@NonNull String message, @NonNull Object... args) {
            fire(String.format(Locale.ROOT, message, args));
        }

        public static void fireInput(@NonNull String fullName, @Nullable String input) {
            if (TextUtils.isEmpty(input)) return;
            P.addSentMessage(input);
            for (String line : input.split("\n"))
                if (!TextUtils.isEmpty(line))
                    fire("input %s %s", fullName, line);
        }

        @Override public String toString() {
            return "SendMessageEvent(message=" + message + ")";
        }
    }
}
