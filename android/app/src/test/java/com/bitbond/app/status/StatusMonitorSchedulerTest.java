package com.bitbond.app.status;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import org.junit.Test;

public class StatusMonitorSchedulerTest {
    @Test
    public void startRunsImmediatelyThenSchedulesAfterEachRun() {
        FakeScheduler scheduler = new FakeScheduler();
        Counter counter = new Counter();
        StatusMonitorScheduler monitorScheduler = new StatusMonitorScheduler(
                scheduler,
                counter::increment,
                15_000L,
                throwable -> {
                });

        monitorScheduler.start();

        assertEquals(1, scheduler.pendingCount());
        assertEquals(0L, scheduler.delays.get(0).longValue());

        scheduler.runNext();
        assertEquals(1, counter.value);
        assertEquals(1, scheduler.pendingCount());
        assertEquals(15_000L, scheduler.delays.get(1).longValue());

        scheduler.runNext();
        assertEquals(2, counter.value);
        assertEquals(1, scheduler.pendingCount());
        assertEquals(15_000L, scheduler.delays.get(2).longValue());
    }

    @Test
    public void startIsIdempotentWhileRunIsPending() {
        FakeScheduler scheduler = new FakeScheduler();
        StatusMonitorScheduler monitorScheduler = new StatusMonitorScheduler(
                scheduler,
                () -> {
                },
                15_000L,
                throwable -> {
                });

        monitorScheduler.start();
        monitorScheduler.start();

        assertEquals(1, scheduler.pendingCount());
    }

    @Test
    public void continuesAfterTaskThrows() {
        FakeScheduler scheduler = new FakeScheduler();
        List<Throwable> errors = new ArrayList<>();
        Counter counter = new Counter();
        StatusMonitorScheduler monitorScheduler = new StatusMonitorScheduler(
                scheduler,
                () -> {
                    counter.increment();
                    if (counter.value == 1) {
                        throw new AssertionError("boom");
                    }
                },
                15_000L,
                errors::add);

        monitorScheduler.start();
        scheduler.runNext();
        scheduler.runNext();

        assertEquals(2, counter.value);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0) instanceof AssertionError);
        assertEquals(1, scheduler.pendingCount());
    }

    @Test
    public void stopCancelsPendingRunAndPreventsReschedule() {
        FakeScheduler scheduler = new FakeScheduler();
        Counter counter = new Counter();
        StatusMonitorScheduler monitorScheduler = new StatusMonitorScheduler(
                scheduler,
                counter::increment,
                15_000L,
                throwable -> {
                });

        monitorScheduler.start();
        monitorScheduler.stop();
        scheduler.runNext();

        assertEquals(0, counter.value);
        assertEquals(0, scheduler.pendingCount());
    }

    private static final class Counter {
        private int value;

        private void increment() {
            value++;
        }
    }

    private static final class FakeScheduler implements StatusMonitorScheduler.Scheduler {
        private final Queue<FakeScheduledRun> pendingRuns = new ArrayDeque<>();
        private final List<Long> delays = new ArrayList<>();

        @Override
        public StatusMonitorScheduler.Cancellable schedule(Runnable runnable, long delayMillis) {
            delays.add(delayMillis);
            FakeScheduledRun scheduledRun = new FakeScheduledRun(runnable);
            pendingRuns.add(scheduledRun);
            return scheduledRun;
        }

        private int pendingCount() {
            int count = 0;
            for (FakeScheduledRun run : pendingRuns) {
                if (run.isPending()) {
                    count++;
                }
            }
            return count;
        }

        private void runNext() {
            FakeScheduledRun scheduledRun = pendingRuns.poll();
            if (scheduledRun != null) {
                scheduledRun.run();
            }
        }
    }

    private static final class FakeScheduledRun implements StatusMonitorScheduler.Cancellable {
        private final Runnable runnable;
        private boolean canceled;
        private boolean done;

        private FakeScheduledRun(Runnable runnable) {
            this.runnable = runnable;
        }

        private void run() {
            if (canceled) {
                return;
            }

            try {
                runnable.run();
            } finally {
                done = true;
            }
        }

        @Override
        public void cancel() {
            canceled = true;
        }

        @Override
        public boolean isPending() {
            return !canceled && !done;
        }
    }
}
