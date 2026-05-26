package com.bitbond.app.status;

import java.util.Objects;

final class StatusMonitorWakeLock {
    interface WakeLockHandle {
        void setReferenceCounted(boolean referenceCounted);

        void acquire();

        void release();

        boolean isHeld();
    }

    private final WakeLockHandle wakeLock;

    StatusMonitorWakeLock(WakeLockHandle wakeLock) {
        this.wakeLock = Objects.requireNonNull(wakeLock, "wakeLock");
    }

    void acquire() {
        if (wakeLock.isHeld()) {
            return;
        }

        wakeLock.setReferenceCounted(false);
        wakeLock.acquire();
    }

    void release() {
        if (!wakeLock.isHeld()) {
            return;
        }

        wakeLock.release();
    }
}
