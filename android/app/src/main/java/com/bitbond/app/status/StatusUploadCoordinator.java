package com.bitbond.app.status;

import com.bitbond.app.api.ApiResult;
import com.bitbond.app.auth.AuthSession;
import com.bitbond.app.status.StatusModels.CurrentStatusResult;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;

public final class StatusUploadCoordinator implements StatusUploadTrigger {
    private static final long FOREGROUND_LOOKBACK_MILLIS = Duration.ofMinutes(5).toMillis();
    private static final Duration DEDUPLICATION_WINDOW = Duration.ofMinutes(15);

    private final UsageAccessGateway usageAccessGateway;
    private final ForegroundAppReader foregroundAppReader;
    private final StatusMapper statusMapper;
    private final StatusUploader statusUploader;
    private final Supplier<Instant> clock;

    private String lastUploadedForegroundKey;
    private Instant lastUploadedAt;

    public StatusUploadCoordinator(
            UsageAccessGateway usageAccessGateway,
            ForegroundAppReader foregroundAppReader,
            StatusMapper statusMapper,
            StatusUploader statusUploader,
            Supplier<Instant> clock) {
        this.usageAccessGateway = Objects.requireNonNull(usageAccessGateway, "usageAccessGateway");
        this.foregroundAppReader = Objects.requireNonNull(foregroundAppReader, "foregroundAppReader");
        this.statusMapper = Objects.requireNonNull(statusMapper, "statusMapper");
        this.statusUploader = Objects.requireNonNull(statusUploader, "statusUploader");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public ApiResult<String> uploadDetectedStatus(AuthSession session) {
        Objects.requireNonNull(session, "session");

        if (!usageAccessGateway.hasUsageAccess()) {
            return ApiResult.success("skipped");
        }

        Instant detectedAt = Objects.requireNonNull(clock.get(), "clock instant");
        String packageName = foregroundAppReader.readMostRecentForegroundPackage(FOREGROUND_LOOKBACK_MILLIS);
        String statusCode = statusMapper.mapPackageName(packageName);
        String foregroundKey = foregroundKey(packageName, statusCode);

        if (isDuplicateWithinWindow(foregroundKey, detectedAt)) {
            return ApiResult.success("deduplicated");
        }

        ApiResult<CurrentStatusResult> result = statusUploader.uploadCurrentStatus(session, statusCode, detectedAt);
        if (!result.isSuccess()) {
            return ApiResult.error(result.error());
        }

        lastUploadedForegroundKey = foregroundKey;
        lastUploadedAt = detectedAt;
        return ApiResult.success(statusCode);
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
}
