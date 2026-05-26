package com.bitbond.app.status;

import java.util.Objects;

final class StatusMonitorMode {
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
        return !isAccessibilityEventMode();
    }
}
