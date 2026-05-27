package com.bitbond.app.status;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StatusMonitorModeTest {
    @Test
    public void shouldPollUsageStatsWhenAccessibilityEventModeIsDisabled() {
        StatusMonitorMode mode = new StatusMonitorMode(new FakeAccessibilityAccess(false));

        assertTrue(mode.shouldPollUsageStats());
        assertEquals(StatusMonitorService.POLL_INTERVAL_MS, mode.usageStatsPollIntervalMillis());
        assertFalse(mode.isAccessibilityEventMode());
    }

    @Test
    public void shouldPollUsageStatsWhenAccessibilityEventModeIsEnabled() {
        StatusMonitorMode mode = new StatusMonitorMode(new FakeAccessibilityAccess(true));

        assertTrue(mode.shouldPollUsageStats());
        assertEquals(120_000L, mode.usageStatsPollIntervalMillis());
        assertTrue(mode.isAccessibilityEventMode());
    }

    @Test
    public void shouldKeepPollingWhenAccessibilityCheckFails() {
        StatusMonitorMode mode = new StatusMonitorMode(new ThrowingAccessibilityAccess());

        assertTrue(mode.shouldPollUsageStats());
        assertEquals(StatusMonitorService.POLL_INTERVAL_MS, mode.usageStatsPollIntervalMillis());
        assertFalse(mode.isAccessibilityEventMode());
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

    private static final class ThrowingAccessibilityAccess implements AccessibilityAccessGateway {
        @Override
        public boolean hasAccessibilityAccess() {
            throw new IllegalStateException("settings unavailable");
        }

        @Override
        public void openAccessibilitySettings() {
        }
    }
}
