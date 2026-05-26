package com.bitbond.app.status;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class ResilientRunnableTest {
    @Test
    public void runSwallowsThrowableSoNextScheduledRunCanContinue() {
        AtomicInteger calls = new AtomicInteger();
        ResilientRunnable task = new ResilientRunnable(() -> {
            if (calls.incrementAndGet() == 1) {
                throw new AssertionError("first run failed");
            }
        });

        task.run();
        task.run();

        assertEquals(2, calls.get());
    }
}
