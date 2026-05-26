package com.bitbond.app.status;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class StatusAccessibilityService extends AccessibilityService {
    private static final String TAG = "BitBondStatus";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private StatusMonitorRunner runner;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        runner = StatusMonitorDependencies.createRunner(this);
        Log.d(TAG, "status accessibility connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) {
            return;
        }

        StatusMonitorRunner currentRunner = runner;
        if (currentRunner == null) {
            return;
        }

        String packageName = event.getPackageName().toString().trim();
        if (packageName.isEmpty()) {
            return;
        }

        Log.d(TAG, "status accessibility event package=" + packageName);
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
}
