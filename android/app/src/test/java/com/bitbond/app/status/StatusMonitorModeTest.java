package com.bitbond.app.status;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StatusMonitorModeTest {
    @Test
    public void shouldPollUsageStatsWhenAccessibilityEventModeIsDisabled() {
        StatusMonitorMode mode = new StatusMonitorMode(new FakeAccessibilityAccess(false));

        assertTrue(mode.shouldPollUsageStats());
        assertFalse(mode.isAccessibilityEventMode());
    }

    @Test
    public void shouldNotPollUsageStatsWhenAccessibilityEventModeIsEnabled() {
        StatusMonitorMode mode = new StatusMonitorMode(new FakeAccessibilityAccess(true));

        assertFalse(mode.shouldPollUsageStats());
        assertTrue(mode.isAccessibilityEventMode());
    }

    @Test
    public void shouldKeepPollingWhenAccessibilityCheckFails() {
        StatusMonitorMode mode = new StatusMonitorMode(new ThrowingAccessibilityAccess());

        assertTrue(mode.shouldPollUsageStats());
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
