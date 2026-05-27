package com.bitbond.app.status;

import android.accessibilityservice.AccessibilityService;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class StatusAccessibilityService extends AccessibilityService {
    private static final String TAG = "BitBondStatus";
    private static final long SAME_PACKAGE_DEBOUNCE_MILLIS = 1_000L;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private StatusMonitorRunner runner;
    private StatusAccessibilityEventGate eventGate;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        runner = StatusMonitorDependencies.createRunner(this);
        eventGate = new StatusAccessibilityEventGate(getPackageName(), SAME_PACKAGE_DEBOUNCE_MILLIS);
        Log.d(TAG, "status accessibility connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }

        StatusMonitorRunner currentRunner = runner;
        if (currentRunner == null) {
            return;
        }

        StatusAccessibilityEventGate currentGate = eventGate;
        if (currentGate == null) {
            currentGate = new StatusAccessibilityEventGate(getPackageName(), SAME_PACKAGE_DEBOUNCE_MILLIS);
            eventGate = currentGate;
        }

        String packageName = currentGate.packageForEvent(
                event.getEventType(),
                event.getPackageName(),
                eventTimeMillis(event));
        if (packageName == null) {
            Log.d(TAG, "status accessibility event ignored "
                    + StatusAccessibilityEventLog.ignoredEventDetails(
                            event.getEventType(),
                            event.getPackageName(),
                            event.getEventTime(),
                            sourceForLog(event),
                            currentGate.lastIgnoredReason()));
            return;
        }

        Log.d(TAG, "status accessibility foreground event " + eventDetails(event));
        executor.execute(() -> currentRunner.runForPackage(packageName));
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "status accessibility interrupted");
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private static long eventTimeMillis(AccessibilityEvent event) {
        long eventTime = event.getEventTime();
        if (eventTime > 0L) {
            return eventTime;
        }
        return SystemClock.uptimeMillis();
    }

    private static String eventDetails(AccessibilityEvent event) {
        return "eventType=" + event.getEventType()
                + " package=" + logValue(event.getPackageName())
                + " eventTime=" + event.getEventTime()
                + " source=" + sourceForLog(event);
    }

    private static String sourceForLog(AccessibilityEvent event) {
        return "windowId=" + event.getWindowId();
    }

    private static String logValue(CharSequence value) {
        if (value == null || value.toString().trim().isEmpty()) {
            return "<none>";
        }
        return value.toString().trim();
    }
}

final class StatusAccessibilityEventGate {
    private final String ownPackageName;
    private final long debounceMillis;
    private String lastPackageName;
    private long lastEventTimeMillis;
    private String lastIgnoredReason = "unknown";

    StatusAccessibilityEventGate(String ownPackageName, long debounceMillis) {
        this.ownPackageName = ownPackageName;
        this.debounceMillis = Math.max(0L, debounceMillis);
    }

    synchronized String packageForEvent(int eventType, CharSequence eventPackage, long eventTimeMillis) {
        if (!isForegroundWindowEvent(eventType)) {
            lastIgnoredReason = "event_type";
            return null;
        }
        if (eventPackage == null) {
            lastIgnoredReason = "missing_package";
            return null;
        }

        String packageName = eventPackage.toString().trim();
        if (!ForegroundPackageFilter.isSelectableForegroundPackage(packageName, ownPackageName)) {
            lastIgnoredReason = "rejected_package";
            return null;
        }
        if (isDebounced(packageName, eventTimeMillis)) {
            lastIgnoredReason = "debounced";
            return null;
        }

        lastIgnoredReason = null;
        lastPackageName = packageName;
        lastEventTimeMillis = eventTimeMillis;
        return packageName;
    }

    synchronized String lastIgnoredReason() {
        if (lastIgnoredReason == null || lastIgnoredReason.trim().isEmpty()) {
            return "unknown";
        }
        return lastIgnoredReason;
    }

    private static boolean isForegroundWindowEvent(int eventType) {
        return eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED;
    }

    private boolean isDebounced(String packageName, long eventTimeMillis) {
        if (!packageName.equals(lastPackageName)) {
            return false;
        }

        long elapsedMillis = eventTimeMillis - lastEventTimeMillis;
        return elapsedMillis >= 0L && elapsedMillis < debounceMillis;
    }
}

final class StatusAccessibilityEventLog {
    private StatusAccessibilityEventLog() {
    }

    static String ignoredEventDetails(
            int eventType,
            CharSequence eventPackage,
            long eventTime,
            String source,
            String reason) {
        return "eventType=" + eventType
                + " package=" + redactedPackageValue(eventPackage)
                + " eventTime=" + eventTime
                + " source=" + safeLogValue(source)
                + " reason=" + safeLogValue(reason);
    }

    private static String redactedPackageValue(CharSequence eventPackage) {
        if (eventPackage == null || eventPackage.toString().trim().isEmpty()) {
            return "<none>";
        }
        return "<redacted>";
    }

    private static String safeLogValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "unknown";
        }
        return value.trim();
    }
}
