package com.bitbond.app.status;

import java.util.Objects;

final class StatusMonitorMode {
    static final long ACCESSIBILITY_FALLBACK_POLL_INTERVAL_MS = 120_000L;

    private final AccessibilityAccessGateway accessibilityAccess;

    StatusMonitorMode(AccessibilityAccessGateway accessibilityAccess) {
        this.accessibilityAccess = Objects.requireNonNull(accessibilityAccess, "accessibilityAccess");
    }

    boolean isAccessibilityEventMode() {
        try {
            return accessibilityAccess.hasAccessibilityAccess();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    boolean shouldPollUsageStats() {
        return true;
    }

    long usageStatsPollIntervalMillis() {
        if (isAccessibilityEventMode()) {
            return ACCESSIBILITY_FALLBACK_POLL_INTERVAL_MS;
        }
        return StatusMonitorService.POLL_INTERVAL_MS;
    }
}
