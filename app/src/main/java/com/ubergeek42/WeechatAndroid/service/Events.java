// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package com.ubergeek42.WeechatAndroid.service;

import android.support.annotation.NonNull;

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
            EventBus.getDefault().post(new SendMessageEvent(message + "\n"));
        }

        public static void fire(@NonNull String message, @NonNull Object... args) {
            fire(String.format(Locale.ROOT, message, args));
        }

        @Override public String toString() {
            return "SendMessageEvent(message=" + message + ")";
        }
    }
}
