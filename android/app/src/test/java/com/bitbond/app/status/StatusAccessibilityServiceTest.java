package com.bitbond.app.status;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.view.accessibility.AccessibilityEvent;

import org.junit.Test;

public class StatusAccessibilityServiceTest {
    private static final String OWN_PACKAGE = "com.bitbond.app";
    private static final long DEBOUNCE_MILLIS = 1_000L;

    @Test
    public void packageForEventIgnoresNonWindowForegroundEventTypes() {
        StatusAccessibilityEventGate gate = new StatusAccessibilityEventGate(OWN_PACKAGE, DEBOUNCE_MILLIS);

        assertNull(gate.packageForEvent(
                AccessibilityEvent.TYPE_VIEW_CLICKED,
                "com.tencent.mm",
                1_000L));
    }

    @Test
    public void packageForEventAcceptsWindowForegroundEventTypes() {
        StatusAccessibilityEventGate gate = new StatusAccessibilityEventGate(OWN_PACKAGE, DEBOUNCE_MILLIS);

        assertEquals(
                "com.tencent.mm",
                gate.packageForEvent(
                        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                        "  com.tencent.mm  ",
                        1_000L));
        assertEquals(
                "com.tencent.mobileqq",
                gate.packageForEvent(
                        AccessibilityEvent.TYPE_WINDOWS_CHANGED,
                        "com.tencent.mobileqq",
                        1_001L));
    }

    @Test
    public void packageForEventIgnoresPackagesRejectedByForegroundFilter() {
        StatusAccessibilityEventGate gate = new StatusAccessibilityEventGate(OWN_PACKAGE, DEBOUNCE_MILLIS);

        assertNull(gate.packageForEvent(
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                OWN_PACKAGE,
                1_000L));
        assertNull(gate.packageForEvent(
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                "com.android.systemui",
                1_001L));
        assertNull(gate.packageForEvent(
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                "com.android.launcher3",
                1_002L));
        assertNull(gate.packageForEvent(
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                "com.android.settings",
                1_003L));
        assertNull(gate.packageForEvent(
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                "com.google.android.inputmethod.latin",
                1_004L));
        assertNull(gate.packageForEvent(
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                "com.miui.securitycenter",
                1_005L));
    }

    @Test
    public void packageForEventDebouncesRepeatedSamePackageInsideWindow() {
        StatusAccessibilityEventGate gate = new StatusAccessibilityEventGate(OWN_PACKAGE, DEBOUNCE_MILLIS);

        assertEquals(
                "com.tencent.mm",
                gate.packageForEvent(
                        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                        "com.tencent.mm",
                        1_000L));
        assertNull(gate.packageForEvent(
                AccessibilityEvent.TYPE_WINDOWS_CHANGED,
                "com.tencent.mm",
                1_999L));
    }

    @Test
    public void packageForEventAllowsSamePackageOutsideDebounceWindow() {
        StatusAccessibilityEventGate gate = new StatusAccessibilityEventGate(OWN_PACKAGE, DEBOUNCE_MILLIS);

        assertEquals(
                "com.tencent.mm",
                gate.packageForEvent(
                        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                        "com.tencent.mm",
                        1_000L));
        assertEquals(
                "com.tencent.mm",
                gate.packageForEvent(
                        AccessibilityEvent.TYPE_WINDOWS_CHANGED,
                        "com.tencent.mm",
                        2_000L));
    }

    @Test
    public void packageForEventAllowsDifferentPackageImmediately() {
        StatusAccessibilityEventGate gate = new StatusAccessibilityEventGate(OWN_PACKAGE, DEBOUNCE_MILLIS);

        assertEquals(
                "com.tencent.mm",
                gate.packageForEvent(
                        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                        "com.tencent.mm",
                        1_000L));
        assertEquals(
                "com.tencent.mobileqq",
                gate.packageForEvent(
                        AccessibilityEvent.TYPE_WINDOWS_CHANGED,
                        "com.tencent.mobileqq",
                        1_001L));
    }

    @Test
    public void ignoredEventLogDetailsDoNotIncludeRawPackageName() {
        String logDetails = StatusAccessibilityEventLog.ignoredEventDetails(
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                "com.android.settings",
                1_234L,
                "windowId=4",
                "rejected_package");

        assertTrue(logDetails.contains("eventType=" + AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED));
        assertTrue(logDetails.contains("eventTime=1234"));
        assertTrue(logDetails.contains("source=windowId=4"));
        assertTrue(logDetails.contains("reason=rejected_package"));
        assertFalse(logDetails.contains("com.android.settings"));
    }
}
