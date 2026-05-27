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
    private static final long FOREGROUND_LOOKBACK_MILLIS = Duration.ofHours(2).toMillis();
    private static final long FOREGROUND_CACHE_MAX_AGE_MILLIS = Duration.ofHours(2).toMillis();
    private static final Duration DEDUPLICATION_WINDOW = Duration.ofMinutes(15);
    private static final Object UPLOAD_LOCK = new Object();

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
    private final LastForegroundStore lastForegroundStore;
    private final Supplier<String> lastRefreshForegroundKeyReader;
    private final Consumer<String> lastRefreshForegroundKeyWriter;
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
                },
                LastForegroundStore.noOp());
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
        this(
                supabaseConfigured,
                authGateway,
                usageAccessGateway,
                foregroundAppReader,
                statusMapper,
                statusUploader,
                clock,
                backgroundRefreshPolicy,
                lastRefreshAtReader,
                lastRefreshAtWriter,
                LastForegroundStore.noOp(),
                () -> null,
                foregroundKey -> {
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
            Consumer<Instant> lastRefreshAtWriter,
            LastForegroundStore lastForegroundStore) {
        this(
                supabaseConfigured,
                authGateway,
                usageAccessGateway,
                foregroundAppReader,
                statusMapper,
                statusUploader,
                clock,
                backgroundRefreshPolicy,
                lastRefreshAtReader,
                lastRefreshAtWriter,
                lastForegroundStore,
                () -> null,
                foregroundKey -> {
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
            Consumer<Instant> lastRefreshAtWriter,
            LastForegroundStore lastForegroundStore,
            Supplier<String> lastRefreshForegroundKeyReader,
            Consumer<String> lastRefreshForegroundKeyWriter) {
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
        this.lastForegroundStore = Objects.requireNonNull(lastForegroundStore, "lastForegroundStore");
        this.lastRefreshForegroundKeyReader = Objects.requireNonNull(
                lastRefreshForegroundKeyReader,
                "lastRefreshForegroundKeyReader");
        this.lastRefreshForegroundKeyWriter = Objects.requireNonNull(
                lastRefreshForegroundKeyWriter,
                "lastRefreshForegroundKeyWriter");
    }

    public void runOnce() {
        try {
            if (!canPollUsageStats()) {
                return;
            }

            Instant detectedAt = Objects.requireNonNull(clock.get(), "clock instant");
            String packageName = foregroundAppReader.readMostRecentForegroundPackage(FOREGROUND_LOOKBACK_MILLIS);
            LastForegroundStore.Entry foregroundRecord = foregroundRecordForPackage(packageName, detectedAt);
            if (foregroundRecord == null) {
                foregroundRecord = lastForegroundStore.readFresh(FOREGROUND_CACHE_MAX_AGE_MILLIS, detectedAt);
            }
            if (foregroundRecord == null) {
                logDebug("status poll skipped: foreground unavailable and cache empty");
                return;
            }

            uploadDetectedForeground(foregroundRecord, detectedAt, UploadPath.POLL);
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

            Instant detectedAt = Objects.requireNonNull(clock.get(), "clock instant");
            LastForegroundStore.Entry foregroundRecord = foregroundRecordForPackage(packageName, detectedAt);
            if (foregroundRecord == null) {
                logDebug("status event skipped: foreground package unavailable");
                return;
            }

            uploadDetectedForeground(foregroundRecord, detectedAt, UploadPath.EVENT);
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

    private boolean shouldRunBackgroundRefresh(
            String foregroundKey,
            Instant detectedAt) {
        if (backgroundRefreshPolicy == null) {
            return true;
        }

        String persistedForegroundKey = readPersistedLastRefreshForegroundKey();
        if (persistedForegroundKey != null) {
            if (!foregroundKey.equals(persistedForegroundKey)) {
                return true;
            }
            return shouldRunBackgroundRefreshSince(readPersistedLastRefreshAt(), detectedAt);
        }

        if (lastUploadedForegroundKey == null || !foregroundKey.equals(lastUploadedForegroundKey)) {
            return true;
        }
        return shouldRunBackgroundRefreshSince(lastUploadedAt, detectedAt);
    }

    private boolean shouldRunBackgroundRefreshSince(Instant lastRefreshAt, Instant detectedAt) {
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

    private LastForegroundStore.Entry foregroundRecordForPackage(String packageName, Instant detectedAt) {
        if (!hasPackageName(packageName)) {
            return null;
        }

        String statusCode = statusMapper.mapPackageName(packageName);
        lastForegroundStore.save(packageName, statusCode, detectedAt);
        return new LastForegroundStore.Entry(packageName, statusCode, detectedAt);
    }

    private void uploadDetectedForeground(
            LastForegroundStore.Entry foregroundRecord,
            Instant detectedAt,
            UploadPath uploadPath) {
        String statusCode = foregroundRecord.statusCode();
        String foregroundKey = foregroundKey(foregroundRecord.packageName(), statusCode);
        synchronized (UPLOAD_LOCK) {
            logDebug("status poll detected package=" + safeLogValue(foregroundRecord.packageName())
                    + " status=" + statusCode
                    + " key=" + foregroundKey);
            if (isDuplicateWithinWindow(foregroundKey, detectedAt)
                    || isSharedDuplicateWithinWindow(foregroundKey, detectedAt)) {
                logDebug("status poll skipped: duplicate within window key=" + foregroundKey);
                return;
            }
            if (uploadPath == UploadPath.POLL
                    && !shouldRunBackgroundRefresh(foregroundKey, detectedAt)) {
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
                    writeLastRefreshAt(detectedAt);
                    writeLastRefreshForegroundKey(foregroundKey);
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
    }

    private void writeLastRefreshForegroundKey(String foregroundKey) {
        if (backgroundRefreshPolicy == null) {
            return;
        }

        try {
            lastRefreshForegroundKeyWriter.accept(foregroundKey);
        } catch (RuntimeException exception) {
            logWarn("status poll last refresh foreground key write failed", exception);
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

    private boolean isSharedDuplicateWithinWindow(String foregroundKey, Instant detectedAt) {
        String lastRefreshForegroundKey = readPersistedLastRefreshForegroundKey();
        if (lastRefreshForegroundKey == null || !foregroundKey.equals(lastRefreshForegroundKey)) {
            return false;
        }

        Instant lastRefreshAt = readPersistedLastRefreshAt();
        if (lastRefreshAt == null) {
            return false;
        }

        return Duration.between(lastRefreshAt, detectedAt).compareTo(DEDUPLICATION_WINDOW) < 0;
    }

    private String readPersistedLastRefreshForegroundKey() {
        try {
            String foregroundKey = lastRefreshForegroundKeyReader.get();
            if (foregroundKey == null || foregroundKey.trim().isEmpty()) {
                return null;
            }
            return foregroundKey.trim();
        } catch (RuntimeException exception) {
            logWarn("status poll last refresh foreground key read failed", exception);
            return null;
        }
    }

    private Instant readPersistedLastRefreshAt() {
        try {
            return lastRefreshAtReader.get();
        } catch (RuntimeException exception) {
            logWarn("status poll last refresh read failed", exception);
            return null;
        }
    }

    private static String foregroundKey(String packageName, String statusCode) {
        return packageName.trim() + "|" + statusCode;
    }

    private static boolean hasPackageName(String packageName) {
        return packageName != null && !packageName.trim().isEmpty();
    }

    private enum UploadPath {
        POLL,
        EVENT
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
