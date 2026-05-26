package com.bitbond.app.status;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StatusMonitorWakeLockTest {
    @Test
    public void acquireSetsNonReferenceCountedAndAcquiresOnlyWhenNotHeld() {
        FakeWakeLock wakeLock = new FakeWakeLock(false);
        StatusMonitorWakeLock monitorWakeLock = new StatusMonitorWakeLock(wakeLock);

        monitorWakeLock.acquire();
        monitorWakeLock.acquire();

        assertEquals(1, wakeLock.setReferenceCountedCalls);
        assertEquals(false, wakeLock.lastReferenceCounted);
        assertEquals(1, wakeLock.acquireCalls);
    }

    @Test
    public void releaseOnlyWhenHeld() {
        FakeWakeLock wakeLock = new FakeWakeLock(true);
        StatusMonitorWakeLock monitorWakeLock = new StatusMonitorWakeLock(wakeLock);

        monitorWakeLock.release();
        monitorWakeLock.release();

        assertEquals(1, wakeLock.releaseCalls);
    }

    private static final class FakeWakeLock implements StatusMonitorWakeLock.WakeLockHandle {
        private boolean held;
        private int setReferenceCountedCalls;
        private boolean lastReferenceCounted = true;
        private int acquireCalls;
        private int releaseCalls;

        private FakeWakeLock(boolean held) {
            this.held = held;
        }

        @Override
        public void setReferenceCounted(boolean referenceCounted) {
            setReferenceCountedCalls++;
            lastReferenceCounted = referenceCounted;
        }

        @Override
        public void acquire() {
            acquireCalls++;
            held = true;
        }

        @Override
        public void release() {
            releaseCalls++;
            held = false;
        }

        @Override
        public boolean isHeld() {
            return held;
        }
    }
}
