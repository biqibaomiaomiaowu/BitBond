package com.bitbond.app.status;

import com.bitbond.app.BuildConfig;

import java.util.function.Supplier;

import org.json.JSONException;
import org.json.JSONObject;

public final class DebugForegroundService implements DebugForegroundGateway {
    private static final long DEFAULT_LOOKBACK_MILLIS = 2 * 60 * 60 * 1000L;

    private final boolean enabled;
    private final ForegroundAppReader foregroundAppReader;
    private final long lookbackMillis;
    private final boolean buildDebuggable;

    public DebugForegroundService(boolean enabled, Supplier<String> foregroundPackageReader) {
        this(enabled, ignored -> foregroundPackageReader.get());
    }

    public DebugForegroundService(boolean enabled, ForegroundAppReader foregroundAppReader) {
        this(enabled, foregroundAppReader, DEFAULT_LOOKBACK_MILLIS);
    }

    public DebugForegroundService(boolean enabled, ForegroundAppReader foregroundAppReader, long lookbackMillis) {
        this(enabled, foregroundAppReader, lookbackMillis, BuildConfig.DEBUG);
    }

    DebugForegroundService(
            boolean enabled,
            ForegroundAppReader foregroundAppReader,
            long lookbackMillis,
            boolean buildDebuggable) {
        if (foregroundAppReader == null) {
            throw new IllegalArgumentException("foregroundAppReader is required");
        }

        this.enabled = enabled;
        this.foregroundAppReader = foregroundAppReader;
        this.lookbackMillis = lookbackMillis;
        this.buildDebuggable = buildDebuggable;
    }

    @Override
    public JSONObject debugForegroundApp() {
        try {
            JSONObject result = new JSONObject();
            boolean debugEnabled = enabled && buildDebuggable;
            result.put("enabled", debugEnabled);

            if (!debugEnabled) {
                result.put("code", "debug_disabled");
                return result;
            }

            String packageName;
            try {
                packageName = foregroundAppReader.readMostRecentForegroundPackage(lookbackMillis);
            } catch (RuntimeException exception) {
                result.put("code", "foreground_unavailable");
                return result;
            }

            if (packageName == null || packageName.trim().isEmpty()) {
                result.put("code", "foreground_unavailable");
                return result;
            }

            result.put("code", "ok");
            result.put("packageName", packageName.trim());
            return result;
        } catch (JSONException exception) {
            throw new IllegalStateException("Failed to build debug foreground result", exception);
        }
    }
}
