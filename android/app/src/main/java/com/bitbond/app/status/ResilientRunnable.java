package com.bitbond.app.status;

import java.util.Objects;

final class ResilientRunnable implements Runnable {
    private final Runnable delegate;

    ResilientRunnable(Runnable delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void run() {
        try {
            delegate.run();
        } catch (Throwable ignored) {
            // ScheduledExecutorService cancels future runs when a periodic task throws.
        }
    }
}
