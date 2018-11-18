package com.ubergeek42.weechat.relay.connection;

import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;

public class Events {

    @FunctionalInterface
    interface ThrowingEvent {
        void run() throws Exception;
    }

    @FunctionalInterface
    interface Event extends Runnable {
        void run();
    }

    @FunctionalInterface
    interface PoisonEvent extends Event {
        @Override void run();
    }

    static class EventStream {
        final private LinkedBlockingQueue<Event> queue = new LinkedBlockingQueue<>();
        final private Thread thread;

        EventStream(String name, int iteration) {
            this.thread = new Utils.FriendlyThread(name, iteration, runnable);
        }

        void start() {
            thread.start();
        }

        synchronized void post(Event... events) {
            queue.addAll(Arrays.asList(events));
        }

        synchronized void close(PoisonEvent poisonEvent) {
            post(poisonEvent);
        }

        synchronized void close() {
            close(() -> {});
        }

        synchronized void clearQueueAndClose(PoisonEvent poisonEvent) {
            queue.clear();
            close(poisonEvent);
        }

        Runnable runnable = () -> {
            while (!Thread.interrupted()) {
                Event event;
                try {
                    event = queue.take();
                } catch (InterruptedException e) {
                    return;
                }
                event.run();
                if (event instanceof PoisonEvent) return;
            }
        };
    }
}
