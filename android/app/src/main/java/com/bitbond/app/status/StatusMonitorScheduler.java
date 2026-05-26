package com.bitbond.app.status;

import java.util.Objects;
import java.util.function.Consumer;

final class StatusMonitorScheduler {
    interface Scheduler {
        Cancellable schedule(Runnable runnable, long delayMillis);
    }

    interface Cancellable {
        void cancel();

        boolean isPending();
    }

    private final Scheduler scheduler;
    private final Runnable pollTask;
    private final long intervalMillis;
    private final Consumer<Throwable> errorHandler;
    private Cancellable pendingRun;
    private boolean stopped;

    StatusMonitorScheduler(
            Scheduler scheduler,
            Runnable pollTask,
            long intervalMillis,
            Consumer<Throwable> errorHandler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.pollTask = Objects.requireNonNull(pollTask, "pollTask");
        this.intervalMillis = intervalMillis;
        this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler");
    }

    synchronized void start() {
        if (stopped || (pendingRun != null && pendingRun.isPending())) {
            return;
        }

        scheduleLocked(0L);
    }

    synchronized void stop() {
        stopped = true;
        if (pendingRun != null) {
            pendingRun.cancel();
            pendingRun = null;
        }
    }

    private void runAndReschedule() {
        try {
            pollTask.run();
        } catch (Throwable throwable) {
            handleError(throwable);
        } finally {
            synchronized (this) {
                if (stopped) {
                    return;
                }

                pendingRun = null;
                scheduleLocked(intervalMillis);
            }
        }
    }

    private void scheduleLocked(long delayMillis) {
        try {
            pendingRun = scheduler.schedule(this::runAndReschedule, delayMillis);
        } catch (RuntimeException exception) {
            handleError(exception);
        }
    }

    private void handleError(Throwable throwable) {
        try {
            errorHandler.accept(throwable);
        } catch (RuntimeException ignored) {
            // Monitoring must not crash because diagnostic logging failed.
        }
    }
}
