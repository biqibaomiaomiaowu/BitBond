package com.bitbond.app.status;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.json.JSONObject;
import org.junit.Test;

public class DebugForegroundServiceTest {
    @Test
    public void disabledDebugDoesNotReturnPackageName() throws Exception {
        DebugForegroundService service = new DebugForegroundService(false, () -> "com.tencent.mm");

        JSONObject result = service.debugForegroundApp();

        assertFalse(result.getBoolean("enabled"));
        assertEquals("debug_disabled", result.getString("code"));
        assertFalse(result.has("packageName"));
    }

    @Test
    public void enabledDebugReturnsPackageNameFromReader() throws Exception {
        DebugForegroundService service = new DebugForegroundService(true, () -> "com.tencent.mm");

        JSONObject result = service.debugForegroundApp();

        assertTrue(result.getBoolean("enabled"));
        assertEquals("ok", result.getString("code"));
        assertEquals("com.tencent.mm", result.getString("packageName"));
    }

    @Test
    public void enabledDebugUsesTwoHourDefaultLookback() throws Exception {
        AtomicLong capturedLookbackMillis = new AtomicLong();
        DebugForegroundService service = new DebugForegroundService(
                true,
                lookbackMillis -> {
                    capturedLookbackMillis.set(lookbackMillis);
                    return "com.tencent.mm";
                });

        service.debugForegroundApp();

        assertEquals(2 * 60 * 60 * 1000L, capturedLookbackMillis.get());
    }

    @Test
    public void releaseGateDoesNotInvokeReaderOrReturnPackageName() throws Exception {
        AtomicInteger callCount = new AtomicInteger();
        DebugForegroundService service = new DebugForegroundService(
                true,
                ignored -> {
                    callCount.incrementAndGet();
                    return "com.tencent.mm";
                },
                60_000L,
                false);

        JSONObject result = service.debugForegroundApp();

        assertFalse(result.getBoolean("enabled"));
        assertEquals("debug_disabled", result.getString("code"));
        assertFalse(result.has("packageName"));
        assertEquals(0, callCount.get());
    }

    @Test
    public void enabledDebugReturnsUnavailableWhenReaderThrows() throws Exception {
        DebugForegroundService service = new DebugForegroundService(
                true,
                ignored -> {
                    throw new SecurityException("usage access revoked");
                },
                60_000L,
                true);

        JSONObject result = service.debugForegroundApp();

        assertTrue(result.getBoolean("enabled"));
        assertEquals("foreground_unavailable", result.getString("code"));
        assertFalse(result.has("packageName"));
    }
}
