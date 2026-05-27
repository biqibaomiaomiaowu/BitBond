package com.bitbond.app.status;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.bitbond.app.api.ApiResult;
import com.bitbond.app.api.ApiError;
import com.bitbond.app.auth.AuthSession;
import com.bitbond.app.status.StatusModels.CurrentStatusResult;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.junit.Test;

public class StatusUploadCoordinatorTest {
    @Test
    public void readsForegroundPackageMapsToStatusCodeUploadsOnlyStatusCodeAndDeduplicates() {
        FakeStatusUploader uploader = new FakeStatusUploader();
        StatusUploadCoordinator coordinator = new StatusUploadCoordinator(
                new FakeUsageAccessService(true),
                new FakeForegroundReader("com.tencent.mm"),
                StatusMapper.fromJson("[{\"package\":\"com.tencent.mm\",\"statusCode\":\"social\"}]"),
                uploader,
                () -> Instant.parse("2026-05-25T12:00:00Z"),
                LastForegroundStore.inMemory()
        );
        assertTrue(coordinator.uploadDetectedStatus(session()).isSuccess());
        assertEquals("social", uploader.lastStatusCode());
        assertFalse(uploader.lastPayloadContains("com.tencent.mm"));
        assertTrue(coordinator.uploadDetectedStatus(session()).isSuccess());
        assertEquals(1, uploader.callCount());
    }

    @Test
    public void doesNotUploadPackageName() {
        FakeStatusUploader uploader = new FakeStatusUploader();
        StatusUploadCoordinator coordinator = coordinator(
                true,
                "com.spotify.music",
                "[{\"package\":\"com.spotify.music\",\"statusCode\":\"music\"}]",
                uploader,
                fixedClock("2026-05-25T12:00:00Z"),
                LastForegroundStore.inMemory());

        ApiResult<String> result = coordinator.uploadDetectedStatus(session());

        assertTrue(result.isSuccess());
        assertEquals("music", uploader.lastStatusCode());
        assertFalse(uploader.lastPayloadContains("com.spotify.music"));
        assertFalse(uploader.lastPayloadContains("package"));
        assertFalse(uploader.lastPayloadContains("packageName"));
    }

    @Test
    public void skipsWithoutRpcWhenUsageAccessIsMissing() {
        FakeStatusUploader uploader = new FakeStatusUploader();
        FakeForegroundReader foregroundReader = new FakeForegroundReader("com.tencent.mm");
        StatusUploadCoordinator coordinator = new StatusUploadCoordinator(
                new FakeUsageAccessService(false),
                foregroundReader,
                StatusMapper.fromJson("[{\"package\":\"com.tencent.mm\",\"statusCode\":\"social\"}]"),
                uploader,
                fixedClock("2026-05-25T12:00:00Z"),
                LastForegroundStore.inMemory());

        ApiResult<String> result = coordinator.uploadDetectedStatus(session());

        assertTrue(result.isSuccess());
        assertEquals(0, uploader.callCount());
        assertEquals(0, foregroundReader.callCount());
    }

    @Test
    public void skipsWithoutUploadingOnlineWhenForegroundPackageIsUnavailableAndCacheIsEmpty() {
        FakeStatusUploader uploader = new FakeStatusUploader();
        StatusUploadCoordinator coordinator = coordinator(
                true,
                null,
                "[{\"package\":\"com.tencent.mm\",\"statusCode\":\"social\"}]",
                uploader,
                fixedClock("2026-05-25T12:00:00Z"),
                LastForegroundStore.inMemory());

        ApiResult<String> result = coordinator.uploadDetectedStatus(session());

        assertTrue(result.isSuccess());
        assertEquals("skipped", result.value());
        assertEquals(0, uploader.callCount());
    }

    @Test
    public void fallsBackToFreshCachedForegroundStatusWhenReaderReturnsNull() {
        FakeStatusUploader uploader = new FakeStatusUploader();
        LastForegroundStore foregroundStore = LastForegroundStore.inMemory();
        foregroundStore.save(
                "com.spotify.music",
                "music",
                Instant.parse("2026-05-25T10:30:00Z"));
        StatusUploadCoordinator coordinator = coordinator(
                true,
                null,
                "[{\"package\":\"com.spotify.music\",\"statusCode\":\"music\"}]",
                uploader,
                fixedClock("2026-05-25T12:00:00Z"),
                foregroundStore);

        ApiResult<String> result = coordinator.uploadDetectedStatus(session());

        assertTrue(result.isSuccess());
        assertEquals("music", result.value());
        assertEquals("music", uploader.lastStatusCode());
        assertFalse(uploader.lastPayloadContains("com.spotify.music"));
    }

    @Test
    public void ignoresStaleCachedForegroundStatusWhenReaderReturnsNull() {
        FakeStatusUploader uploader = new FakeStatusUploader();
        LastForegroundStore foregroundStore = LastForegroundStore.inMemory();
        foregroundStore.save(
                "com.spotify.music",
                "music",
                Instant.parse("2026-05-25T09:59:59Z"));
        StatusUploadCoordinator coordinator = coordinator(
                true,
                null,
                "[{\"package\":\"com.spotify.music\",\"statusCode\":\"music\"}]",
                uploader,
                fixedClock("2026-05-25T12:00:00Z"),
                foregroundStore);

        ApiResult<String> result = coordinator.uploadDetectedStatus(session());

        assertTrue(result.isSuccess());
        assertEquals("skipped", result.value());
        assertEquals(0, uploader.callCount());
    }

    @Test
    public void readsForegroundEventsWithTwoHourLookback() {
        FakeStatusUploader uploader = new FakeStatusUploader();
        FakeForegroundReader foregroundReader = new FakeForegroundReader("com.tencent.mm");
        StatusUploadCoordinator coordinator = new StatusUploadCoordinator(
                new FakeUsageAccessService(true),
                foregroundReader,
                StatusMapper.fromJson("[{\"package\":\"com.tencent.mm\",\"statusCode\":\"social\"}]"),
                uploader,
                fixedClock("2026-05-25T12:00:00Z"),
                LastForegroundStore.inMemory());

        assertTrue(coordinator.uploadDetectedStatus(session()).isSuccess());

        assertEquals(Duration.ofHours(2).toMillis(), foregroundReader.lastLookbackMillis());
    }

    @Test
    public void deduplicatesSameStatusWithinFifteenMinutes() {
        MutableClock clock = new MutableClock("2026-05-25T12:00:00Z");
        FakeStatusUploader uploader = new FakeStatusUploader();
        StatusUploadCoordinator coordinator = coordinator(
                true,
                "com.tencent.mm",
                "[{\"package\":\"com.tencent.mm\",\"statusCode\":\"social\"}]",
                uploader,
                clock,
                LastForegroundStore.inMemory());

        assertTrue(coordinator.uploadDetectedStatus(session()).isSuccess());
        clock.set("2026-05-25T12:14:59Z");
        assertTrue(coordinator.uploadDetectedStatus(session()).isSuccess());

        assertEquals(1, uploader.callCount());
    }

    @Test
    public void uploadsImmediatelyWhenForegroundPackageChangesEvenWithSameStatus() {
        MutableClock clock = new MutableClock("2026-05-25T12:00:00Z");
        FakeForegroundReader foregroundReader = new FakeForegroundReader("com.tencent.mm");
        FakeStatusUploader uploader = new FakeStatusUploader();
        StatusUploadCoordinator coordinator = new StatusUploadCoordinator(
                new FakeUsageAccessService(true),
                foregroundReader,
                StatusMapper.fromJson("""
                        [
                          {"package":"com.tencent.mm","statusCode":"social"},
                          {"package":"com.sina.weibo","statusCode":"social"}
                        ]
                """),
                uploader,
                clock,
                LastForegroundStore.inMemory());

        assertTrue(coordinator.uploadDetectedStatus(session()).isSuccess());
        foregroundReader.setPackageName("com.sina.weibo");
        clock.set("2026-05-25T12:01:00Z");
        assertTrue(coordinator.uploadDetectedStatus(session()).isSuccess());

        assertEquals(2, uploader.callCount());
        assertEquals("social", uploader.lastStatusCode());
        assertEquals(Instant.parse("2026-05-25T12:01:00Z"), uploader.lastStatusUpdatedAt());
    }

    @Test
    public void uploadsSameStatusAfterFifteenMinutes() {
        MutableClock clock = new MutableClock("2026-05-25T12:00:00Z");
        FakeStatusUploader uploader = new FakeStatusUploader();
        StatusUploadCoordinator coordinator = coordinator(
                true,
                "com.tencent.mm",
                "[{\"package\":\"com.tencent.mm\",\"statusCode\":\"social\"}]",
                uploader,
                clock,
                LastForegroundStore.inMemory());

        assertTrue(coordinator.uploadDetectedStatus(session()).isSuccess());
        clock.set("2026-05-25T12:15:00Z");
        assertTrue(coordinator.uploadDetectedStatus(session()).isSuccess());

        assertEquals(2, uploader.callCount());
        assertEquals(Instant.parse("2026-05-25T12:15:00Z"), uploader.lastStatusUpdatedAt());
    }

    @Test
    public void uploadsImmediatelyWhenStatusCodeChanges() {
        MutableClock clock = new MutableClock("2026-05-25T12:00:00Z");
        FakeForegroundReader foregroundReader = new FakeForegroundReader("com.tencent.mm");
        FakeStatusUploader uploader = new FakeStatusUploader();
        StatusUploadCoordinator coordinator = new StatusUploadCoordinator(
                new FakeUsageAccessService(true),
                foregroundReader,
                StatusMapper.fromJson("""
                        [
                          {"package":"com.tencent.mm","statusCode":"social"},
                          {"package":"com.spotify.music","statusCode":"music"}
                        ]
                """),
                uploader,
                clock,
                LastForegroundStore.inMemory());

        assertTrue(coordinator.uploadDetectedStatus(session()).isSuccess());
        foregroundReader.setPackageName("com.spotify.music");
        clock.set("2026-05-25T12:01:00Z");
        assertTrue(coordinator.uploadDetectedStatus(session()).isSuccess());

        assertEquals(2, uploader.callCount());
        assertEquals("music", uploader.lastStatusCode());
        assertEquals(Instant.parse("2026-05-25T12:01:00Z"), uploader.lastStatusUpdatedAt());
    }

    @Test
    public void retriesSameForegroundAfterFailedUploadWithoutPersistingDedupState() {
        MutableClock clock = new MutableClock("2026-05-25T12:00:00Z");
        FakeStatusUploader uploader = new FakeStatusUploader();
        uploader.failNextUpload();
        StatusUploadCoordinator coordinator = coordinator(
                true,
                "com.tencent.mm",
                "[{\"package\":\"com.tencent.mm\",\"statusCode\":\"social\"}]",
                uploader,
                clock,
                LastForegroundStore.inMemory());

        assertFalse(coordinator.uploadDetectedStatus(session()).isSuccess());
        assertTrue(coordinator.uploadDetectedStatus(session()).isSuccess());

        assertEquals(2, uploader.callCount());
    }

    @Test
    public void lastForegroundStoreReturnsFreshRecordsAndDropsStaleRecords() {
        LastForegroundStore foregroundStore = LastForegroundStore.inMemory();
        foregroundStore.save(
                "com.tencent.mm",
                "social",
                Instant.parse("2026-05-25T10:00:00Z"));

        LastForegroundStore.Entry freshRecord = foregroundStore.readFresh(
                Duration.ofHours(2).toMillis(),
                Instant.parse("2026-05-25T12:00:00Z"));
        LastForegroundStore.Entry staleRecord = foregroundStore.readFresh(
                Duration.ofHours(2).toMillis(),
                Instant.parse("2026-05-25T12:00:01Z"));

        assertEquals("com.tencent.mm", freshRecord.packageName());
        assertEquals("social", freshRecord.statusCode());
        assertEquals(Instant.parse("2026-05-25T10:00:00Z"), freshRecord.detectedAt());
        assertEquals(null, staleRecord);
    }

    @Test
    public void lastForegroundStoreRejectsFutureRecords() {
        LastForegroundStore foregroundStore = LastForegroundStore.inMemory();
        foregroundStore.save(
                "com.tencent.mm",
                "social",
                Instant.parse("2026-05-25T12:00:01Z"));

        LastForegroundStore.Entry futureRecord = foregroundStore.readFresh(
                Duration.ofHours(2).toMillis(),
                Instant.parse("2026-05-25T12:00:00Z"));

        assertEquals(null, futureRecord);
    }

    private static StatusUploadCoordinator coordinator(
            boolean hasUsageAccess,
            String packageName,
            String rawMap,
            FakeStatusUploader uploader,
            Supplier<Instant> clock,
            LastForegroundStore foregroundStore) {
        return new StatusUploadCoordinator(
                new FakeUsageAccessService(hasUsageAccess),
                new FakeForegroundReader(packageName),
                StatusMapper.fromJson(rawMap),
                uploader,
                clock,
                foregroundStore);
    }

    private static Supplier<Instant> fixedClock(String value) {
        return () -> Instant.parse(value);
    }

    private static AuthSession session() {
        return new AuthSession("access-token", "refresh-token", Long.MAX_VALUE);
    }

    private static final class FakeUsageAccessService implements UsageAccessGateway {
        private final boolean hasUsageAccess;

        private FakeUsageAccessService(boolean hasUsageAccess) {
            this.hasUsageAccess = hasUsageAccess;
        }

        @Override
        public boolean hasUsageAccess() {
            return hasUsageAccess;
        }

        @Override
        public void openUsageAccessSettings() {
        }
    }

    private static final class FakeForegroundReader implements ForegroundAppReader {
        private String packageName;
        private int callCount;
        private long lastLookbackMillis;

        private FakeForegroundReader(String packageName) {
            this.packageName = packageName;
        }

        @Override
        public String readMostRecentForegroundPackage(long lookbackMillis) {
            callCount++;
            lastLookbackMillis = lookbackMillis;
            return packageName;
        }

        private void setPackageName(String packageName) {
            this.packageName = packageName;
        }

        private int callCount() {
            return callCount;
        }

        private long lastLookbackMillis() {
            return lastLookbackMillis;
        }
    }

    private static final class FakeStatusUploader implements StatusUploader {
        private final List<UploadCall> calls = new ArrayList<>();
        private boolean failNextUpload;

        @Override
        public ApiResult<CurrentStatusResult> uploadCurrentStatus(
                AuthSession session,
                String statusCode,
                Instant statusUpdatedAt) {
            calls.add(new UploadCall(statusCode, statusUpdatedAt));
            if (failNextUpload) {
                failNextUpload = false;
                return ApiResult.error(new ApiError("upload_failed", "Upload failed"));
            }
            return ApiResult.success(new CurrentStatusResult(
                    statusCode,
                    statusUpdatedAt,
                    statusUpdatedAt.plusSeconds(900),
                    payloadFor(statusCode, statusUpdatedAt)));
        }

        private int callCount() {
            return calls.size();
        }

        private String lastStatusCode() {
            return lastCall().statusCode;
        }

        private Instant lastStatusUpdatedAt() {
            return lastCall().statusUpdatedAt;
        }

        private boolean lastPayloadContains(String value) {
            return lastCall().payload.contains(value);
        }

        private UploadCall lastCall() {
            return calls.get(calls.size() - 1);
        }

        private void failNextUpload() {
            failNextUpload = true;
        }

        private static String payloadFor(String statusCode, Instant statusUpdatedAt) {
            return "{\"next_status_code\":\""
                    + statusCode
                    + "\",\"next_status_updated_at\":\""
                    + statusUpdatedAt
                    + "\"}";
        }
    }

    private static final class MutableClock implements Supplier<Instant> {
        private Instant now;

        private MutableClock(String now) {
            this.now = Instant.parse(now);
        }

        @Override
        public Instant get() {
            return now;
        }

        private void set(String now) {
            this.now = Instant.parse(now);
        }
    }

    private static final class UploadCall {
        private final String statusCode;
        private final Instant statusUpdatedAt;
        private final String payload;

        private UploadCall(String statusCode, Instant statusUpdatedAt) {
            this.statusCode = statusCode;
            this.statusUpdatedAt = statusUpdatedAt;
            this.payload = FakeStatusUploader.payloadFor(statusCode, statusUpdatedAt);
        }
    }
}
