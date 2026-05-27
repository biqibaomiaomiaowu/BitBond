package com.bitbond.app.status;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class StatusMonitorServiceTest {
    @Test
    public void schedulerIntervalUsesNormalCadenceWhenAccessibilityIsDisabled() {
        StatusMonitorMode mode = new StatusMonitorMode(new FakeAccessibilityAccess(false));

        assertEquals(StatusMonitorService.POLL_INTERVAL_MS, StatusMonitorService.schedulerIntervalMillis(mode));
    }

    @Test
    public void schedulerIntervalUsesLowFrequencyFallbackWhenAccessibilityIsEnabled() {
        StatusMonitorMode mode = new StatusMonitorMode(new FakeAccessibilityAccess(true));

        assertEquals(
                StatusMonitorMode.ACCESSIBILITY_FALLBACK_POLL_INTERVAL_MS,
                StatusMonitorService.schedulerIntervalMillis(mode));
    }

    @Test
    public void schedulerStateSwitchesFromNormalToAccessibilityFallbackInterval() {
        List<Long> createdIntervals = new ArrayList<>();
        List<FakeCancellable> cancellables = new ArrayList<>();
        StatusMonitorServiceSchedulerState schedulerState = new StatusMonitorServiceSchedulerState(
                intervalMillis -> {
                    createdIntervals.add(intervalMillis);
                    FakeCancellable cancellable = new FakeCancellable();
                    cancellables.add(cancellable);
                    return new StatusMonitorScheduler(
                            (runnable, delayMillis) -> cancellable,
                            () -> {
                            },
                            intervalMillis,
                            throwable -> {
                            });
                });

        schedulerState.start(StatusMonitorService.POLL_INTERVAL_MS);
        schedulerState.start(StatusMonitorMode.ACCESSIBILITY_FALLBACK_POLL_INTERVAL_MS);

        assertEquals(
                List.of(
                        StatusMonitorService.POLL_INTERVAL_MS,
                        StatusMonitorMode.ACCESSIBILITY_FALLBACK_POLL_INTERVAL_MS),
                createdIntervals);
        assertTrue(cancellables.get(0).canceled);
    }

    @Test
    public void schedulerStateSwitchesFromAccessibilityFallbackToNormalInterval() {
        List<Long> createdIntervals = new ArrayList<>();
        List<FakeCancellable> cancellables = new ArrayList<>();
        StatusMonitorServiceSchedulerState schedulerState = new StatusMonitorServiceSchedulerState(
                intervalMillis -> {
                    createdIntervals.add(intervalMillis);
                    FakeCancellable cancellable = new FakeCancellable();
                    cancellables.add(cancellable);
                    return new StatusMonitorScheduler(
                            (runnable, delayMillis) -> cancellable,
                            () -> {
                            },
                            intervalMillis,
                            throwable -> {
                            });
                });

        schedulerState.start(StatusMonitorMode.ACCESSIBILITY_FALLBACK_POLL_INTERVAL_MS);
        schedulerState.start(StatusMonitorService.POLL_INTERVAL_MS);

        assertEquals(
                List.of(
                        StatusMonitorMode.ACCESSIBILITY_FALLBACK_POLL_INTERVAL_MS,
                        StatusMonitorService.POLL_INTERVAL_MS),
                createdIntervals);
        assertTrue(cancellables.get(0).canceled);
    }

    private static final class FakeAccessibilityAccess implements AccessibilityAccessGateway {
        private final boolean enabled;

        private FakeAccessibilityAccess(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public boolean hasAccessibilityAccess() {
            return enabled;
        }

        @Override
        public void openAccessibilitySettings() {
        }
    }

    private static final class FakeCancellable implements StatusMonitorScheduler.Cancellable {
        private boolean canceled;

        @Override
        public void cancel() {
            canceled = true;
        }

        @Override
        public boolean isPending() {
            return !canceled;
        }
    }
}
