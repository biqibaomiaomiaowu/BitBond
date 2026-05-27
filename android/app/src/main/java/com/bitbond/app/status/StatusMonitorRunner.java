package com.bitbond.app.status;

import com.bitbond.app.api.ApiResult;
import com.bitbond.app.auth.AuthGateway;
import com.bitbond.app.auth.AuthSession;
import com.bitbond.app.background.BackgroundRefreshPolicy;

import android.util.Log;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class StatusMonitorRunner {
    private static final String TAG = "BitBondStatus";
    private static final long FOREGROUND_LOOKBACK_MILLIS = Duration.ofMinutes(5).toMillis();
    private static final Duration DEDUPLICATION_WINDOW = Duration.ofMinutes(15);

    private final boolean supabaseConfigured;
    private final AuthGateway authGateway;
    private final UsageAccessGateway usageAccessGateway;
    private final ForegroundAppReader foregroundAppReader;
    private final StatusMapper statusMapper;
    private final StatusUploader statusUploader;
    private final Supplier<Instant> clock;
    private final BackgroundRefreshPolicy backgroundRefreshPolicy;
    private final Supplier<Instant> lastRefreshAtReader;
    private final Consumer<Instant> lastRefreshAtWriter;
    private String lastUploadedForegroundKey;
    private Instant lastUploadedAt;

    public StatusMonitorRunner(
            boolean supabaseConfigured,
            AuthGateway authGateway,
            UsageAccessGateway usageAccessGateway,
            ForegroundAppReader foregroundAppReader,
            StatusMapper statusMapper,
            StatusUploader statusUploader,
            Supplier<Instant> clock) {
        this(
                supabaseConfigured,
                authGateway,
                usageAccessGateway,
                foregroundAppReader,
                statusMapper,
                statusUploader,
                clock,
                null,
                () -> null,
                instant -> {
                });
    }

    public StatusMonitorRunner(
            boolean supabaseConfigured,
            AuthGateway authGateway,
            UsageAccessGateway usageAccessGateway,
            ForegroundAppReader foregroundAppReader,
            StatusMapper statusMapper,
            StatusUploader statusUploader,
            Supplier<Instant> clock,
            BackgroundRefreshPolicy backgroundRefreshPolicy,
            Supplier<Instant> lastRefreshAtReader,
            Consumer<Instant> lastRefreshAtWriter) {
        this.supabaseConfigured = supabaseConfigured;
        this.authGateway = Objects.requireNonNull(authGateway, "authGateway");
        this.usageAccessGateway = Objects.requireNonNull(usageAccessGateway, "usageAccessGateway");
        this.foregroundAppReader = Objects.requireNonNull(foregroundAppReader, "foregroundAppReader");
        this.statusMapper = Objects.requireNonNull(statusMapper, "statusMapper");
        this.statusUploader = Objects.requireNonNull(statusUploader, "statusUploader");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.backgroundRefreshPolicy = backgroundRefreshPolicy;
        this.lastRefreshAtReader = Objects.requireNonNull(lastRefreshAtReader, "lastRefreshAtReader");
        this.lastRefreshAtWriter = Objects.requireNonNull(lastRefreshAtWriter, "lastRefreshAtWriter");
    }

    public void runOnce() {
        try {
            if (!canPollUsageStats()) {
                return;
            }

            Instant detectedAt = Objects.requireNonNull(clock.get(), "clock instant");
            if (!shouldRunBackgroundRefresh(detectedAt)) {
                return;
            }

            String packageName = foregroundAppReader.readMostRecentForegroundPackage(FOREGROUND_LOOKBACK_MILLIS);
            uploadDetectedPackage(packageName, detectedAt, true);
        } catch (RuntimeException exception) {
            logWarn("status poll crashed", exception);
            // Background sync is best-effort and must never crash the process.
        }
    }

    public void runForPackage(String packageName) {
        try {
            if (!canUploadFromKnownPackage()) {
                return;
            }

            uploadDetectedPackage(
                    packageName,
                    Objects.requireNonNull(clock.get(), "clock instant"),
                    false);
        } catch (RuntimeException exception) {
            logWarn("status event upload crashed", exception);
            // Background sync is best-effort and must never crash the process.
        }
    }

    private boolean canPollUsageStats() {
        if (!supabaseConfigured) {
            logDebug("status poll skipped: supabase not configured");
            return false;
        }
        if (!usageAccessGateway.hasUsageAccess()) {
            logDebug("status poll skipped: usage access missing");
            return false;
        }

        return true;
    }

    private boolean canUploadFromKnownPackage() {
        if (!supabaseConfigured) {
            logDebug("status event skipped: supabase not configured");
            return false;
        }

        return true;
    }

    private boolean shouldRunBackgroundRefresh(Instant detectedAt) {
        if (backgroundRefreshPolicy == null) {
            return true;
        }

        Instant lastRefreshAt = null;
        try {
            lastRefreshAt = lastRefreshAtReader.get();
        } catch (RuntimeException exception) {
            logWarn("status poll last refresh read failed", exception);
        }

        boolean shouldRefresh = backgroundRefreshPolicy.shouldRefresh(
                supabaseConfigured,
                true,
                lastRefreshAt,
                detectedAt);
        if (!shouldRefresh) {
            logDebug("status poll skipped: background refresh interval not elapsed");
        }
        return shouldRefresh;
    }

    private void uploadDetectedPackage(String packageName, Instant detectedAt, boolean recordBackgroundRefresh) {
        String statusCode = statusMapper.mapPackageName(packageName);
        String foregroundKey = foregroundKey(packageName, statusCode);
        logDebug("status poll detected package=" + safeLogValue(packageName)
                + " status=" + statusCode
                + " key=" + foregroundKey);
        if (isDuplicateWithinWindow(foregroundKey, detectedAt)) {
            logDebug("status poll skipped: duplicate within window key=" + foregroundKey);
            return;
        }

        ApiResult<AuthSession> sessionResult = authGateway.ensureSession();
        if (sessionResult.isSuccess()) {
            ApiResult<?> uploadResult = statusUploader.uploadCurrentStatus(
                    sessionResult.value(),
                    statusCode,
                    detectedAt);
            if (uploadResult.isSuccess()) {
                lastUploadedForegroundKey = foregroundKey;
                lastUploadedAt = detectedAt;
                if (recordBackgroundRefresh) {
                    writeLastRefreshAt(detectedAt);
                }
                logDebug("status upload success status=" + statusCode + " key=" + foregroundKey);
            } else {
                logWarn("status upload failed code=" + uploadResult.error().code()
                        + " message=" + uploadResult.error().message());
            }
        } else {
            logWarn("status auth failed code=" + sessionResult.error().code()
                    + " message=" + sessionResult.error().message());
        }
    }

    private void writeLastRefreshAt(Instant detectedAt) {
        if (backgroundRefreshPolicy == null) {
            return;
        }

        try {
            lastRefreshAtWriter.accept(detectedAt);
        } catch (RuntimeException exception) {
            logWarn("status poll last refresh write failed", exception);
        }
    }

    private boolean isDuplicateWithinWindow(String foregroundKey, Instant detectedAt) {
        if (!foregroundKey.equals(lastUploadedForegroundKey) || lastUploadedAt == null) {
            return false;
        }

        return Duration.between(lastUploadedAt, detectedAt).compareTo(DEDUPLICATION_WINDOW) < 0;
    }

    private static String foregroundKey(String packageName, String statusCode) {
        if (packageName == null || packageName.trim().isEmpty()) {
            return statusCode;
        }

        return packageName.trim();
    }

    private static String safeLogValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "<none>";
        }
        return value.trim();
    }

    private static void logDebug(String message) {
        try {
            Log.d(TAG, message);
        } catch (RuntimeException ignored) {
            // android.util.Log is a JVM test stub outside Android.
        }
    }

    private static void logWarn(String message) {
        try {
            Log.w(TAG, message);
        } catch (RuntimeException ignored) {
            // android.util.Log is a JVM test stub outside Android.
        }
    }

    private static void logWarn(String message, Throwable throwable) {
        try {
            Log.w(TAG, message, throwable);
        } catch (RuntimeException ignored) {
            // android.util.Log is a JVM test stub outside Android.
        }
    }
}
