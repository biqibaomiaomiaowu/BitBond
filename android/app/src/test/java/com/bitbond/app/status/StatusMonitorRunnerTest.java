package com.bitbond.app.status;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.bitbond.app.api.ApiError;
import com.bitbond.app.api.ApiResult;
import com.bitbond.app.auth.AuthGateway;
import com.bitbond.app.auth.AuthSession;
import com.bitbond.app.background.BackgroundRefreshPolicy;
import com.bitbond.app.status.StatusModels.CurrentStatusResult;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.junit.Test;

public class StatusMonitorRunnerTest {
    @Test
    public void runOnceAuthenticatesAndUploadsKnownPackageWhenConfiguredAndUsageAccessGranted() {
        List<String> calls = new ArrayList<>();
        FakeUsageAccessGateway usageAccess = new FakeUsageAccessGateway(calls, true);
        FakeAuthGateway auth = new FakeAuthGateway(calls);
        FakeForegroundReader foregroundReader = new FakeForegroundReader(calls, "com.xingin.xhs");
        FakeStatusUploader uploader = new FakeStatusUploader(calls);
        StatusMonitorRunner runner = runner(true, auth, usageAccess, foregroundReader, uploader);

        runner.runOnce();

        assertEquals(List.of("hasUsageAccess", "readForeground", "auth", "upload:social"), calls);
    }

    @Test
    public void runOnceSkipsWorkWhenUsageAccessIsMissing() {
        List<String> calls = new ArrayList<>();
        FakeUsageAccessGateway usageAccess = new FakeUsageAccessGateway(calls, false);
        FakeAuthGateway auth = new FakeAuthGateway(calls);
        FakeForegroundReader foregroundReader = new FakeForegroundReader(calls, "com.xingin.xhs");
        FakeStatusUploader uploader = new FakeStatusUploader(calls);
        StatusMonitorRunner runner = runner(true, auth, usageAccess, foregroundReader, uploader);

        runner.runOnce();

        assertEquals(List.of("hasUsageAccess"), calls);
    }

    @Test
    public void runOnceSkipsWorkWhenSupabaseIsNotConfigured() {
        List<String> calls = new ArrayList<>();
        FakeUsageAccessGateway usageAccess = new FakeUsageAccessGateway(calls, true);
        FakeAuthGateway auth = new FakeAuthGateway(calls);
        FakeForegroundReader foregroundReader = new FakeForegroundReader(calls, "com.xingin.xhs");
        FakeStatusUploader uploader = new FakeStatusUploader(calls);
        StatusMonitorRunner runner = runner(false, auth, usageAccess, foregroundReader, uploader);

        runner.runOnce();

        assertEquals(List.of(), calls);
    }

    @Test
    public void runOnceSwallowsRuntimeExceptions() {
        List<String> calls = new ArrayList<>();
        UsageAccessGateway usageAccess = new FakeUsageAccessGateway(calls, true);
        AuthGateway auth = () -> {
            calls.add("auth");
            throw new IllegalStateException("auth failed");
        };
        FakeForegroundReader foregroundReader = new FakeForegroundReader(calls, "com.xingin.xhs");
        FakeStatusUploader uploader = new FakeStatusUploader(calls);
        StatusMonitorRunner runner = runner(true, auth, usageAccess, foregroundReader, uploader);

        runner.runOnce();

        assertEquals(List.of("hasUsageAccess", "readForeground", "auth"), calls);
    }

    @Test
    public void runOnceUploadsOnlineForUnknownPackages() {
        List<String> calls = new ArrayList<>();
        FakeUsageAccessGateway usageAccess = new FakeUsageAccessGateway(calls, true);
        FakeAuthGateway auth = new FakeAuthGateway(calls);
        FakeForegroundReader foregroundReader = new FakeForegroundReader(calls, "com.miui.home");
        FakeStatusUploader uploader = new FakeStatusUploader(calls);
        StatusMonitorRunner runner = runner(true, auth, usageAccess, foregroundReader, uploader);

        runner.runOnce();

        assertEquals(List.of("hasUsageAccess", "readForeground", "auth", "upload:online"), calls);
    }

    @Test
    public void runOnceDoesNotUploadOnlineWhenForegroundPackageIsUnavailableAndCacheIsEmpty() {
        List<String> calls = new ArrayList<>();
        FakeUsageAccessGateway usageAccess = new FakeUsageAccessGateway(calls, true);
        FakeAuthGateway auth = new FakeAuthGateway(calls);
        FakeForegroundReader foregroundReader = new FakeForegroundReader(calls, null);
        FakeStatusUploader uploader = new FakeStatusUploader(calls);
        StatusMonitorRunner runner = runner(true, auth, usageAccess, foregroundReader, uploader);

        runner.runOnce();

        assertEquals(List.of("hasUsageAccess", "readForeground"), calls);
        assertEquals(0, uploader.uploadCount);
    }

    @Test
    public void runOnceUploadsAgainWhenPackageChangesEvenIfStatusCodeIsSame() {
        List<String> calls = new ArrayList<>();
        FakeUsageAccessGateway usageAccess = new FakeUsageAccessGateway(calls, true);
        FakeAuthGateway auth = new FakeAuthGateway(calls);
        FakeForegroundReader foregroundReader = new FakeForegroundReader(calls, "com.tencent.mm");
        FakeStatusUploader uploader = new FakeStatusUploader(calls);
        StatusMonitorRunner runner = runner(true, auth, usageAccess, foregroundReader, uploader);

        runner.runOnce();
        foregroundReader.packageName = "com.xingin.xhs";
        runner.runOnce();

        assertEquals(
                List.of(
                        "hasUsageAccess",
                        "readForeground",
                        "auth",
                        "upload:social",
                        "hasUsageAccess",
                        "readForeground",
                        "auth",
                        "upload:social"),
                calls);
    }

    @Test
    public void runForPackageUploadsProvidedPackageWithoutUsageAccessOrReadingForegroundEvents() {
        List<String> calls = new ArrayList<>();
        FakeUsageAccessGateway usageAccess = new FakeUsageAccessGateway(calls, false);
        FakeAuthGateway auth = new FakeAuthGateway(calls);
        FakeForegroundReader foregroundReader = new FakeForegroundReader(calls, "com.tencent.mm");
        FakeStatusUploader uploader = new FakeStatusUploader(calls);
        StatusMonitorRunner runner = runner(true, auth, usageAccess, foregroundReader, uploader);

        runner.runForPackage("com.xingin.xhs");

        assertEquals(List.of("auth", "upload:social"), calls);
    }

    @Test
    public void runOnceSkipsDuplicatePackage() {
        List<String> calls = new ArrayList<>();
        FakeUsageAccessGateway usageAccess = new FakeUsageAccessGateway(calls, true);
        FakeAuthGateway auth = new FakeAuthGateway(calls);
        FakeForegroundReader foregroundReader = new FakeForegroundReader(calls, "com.xingin.xhs");
        FakeStatusUploader uploader = new FakeStatusUploader(calls);
        StatusMonitorRunner runner = runner(true, auth, usageAccess, foregroundReader, uploader);

        runner.runOnce();
        runner.runOnce();

        assertEquals(List.of("hasUsageAccess", "readForeground", "auth", "upload:social", "hasUsageAccess", "readForeground"), calls);
    }

    @Test
    public void runOnceReadsForegroundEventsWithTwoHourLookback() {
        List<String> calls = new ArrayList<>();
        FakeUsageAccessGateway usageAccess = new FakeUsageAccessGateway(calls, true);
        FakeAuthGateway auth = new FakeAuthGateway(calls);
        FakeForegroundReader foregroundReader = new FakeForegroundReader(calls, "com.xingin.xhs");
        FakeStatusUploader uploader = new FakeStatusUploader(calls);
        StatusMonitorRunner runner = runner(true, auth, usageAccess, foregroundReader, uploader);

        runner.runOnce();

        assertEquals(Duration.ofHours(2).toMillis(), foregroundReader.lastLookbackMillis);
    }

    @Test
    public void runOnceRefreshesSamePackageAfterDeduplicationWindow() {
        List<String> calls = new ArrayList<>();
        MutableClock clock = new MutableClock("2026-05-26T12:00:00Z");
        FakeUsageAccessGateway usageAccess = new FakeUsageAccessGateway(calls, true);
        FakeAuthGateway auth = new FakeAuthGateway(calls);
        FakeForegroundReader foregroundReader = new FakeForegroundReader(calls, "com.xingin.xhs");
        FakeStatusUploader uploader = new FakeStatusUploader(calls);
        StatusMonitorRunner runner = runner(true, auth, usageAccess, foregroundReader, uploader, clock);

        runner.runOnce();
        clock.set("2026-05-26T12:15:00Z");
        runner.runOnce();

        assertEquals(2, uploader.uploadCount);
    }

    @Test
    public void runOnceUsesBackgroundRefreshPolicyAndPersistsLastRefreshAt() {
        List<String> calls = new ArrayList<>();
        MutableClock clock = new MutableClock("2026-05-27T08:00:00Z");
        AtomicReference<Instant> lastRefreshAt = new AtomicReference<>();
        FakeUsageAccessGateway usageAccess = new FakeUsageAccessGateway(calls, true);
        FakeAuthGateway auth = new FakeAuthGateway(calls);
        FakeForegroundReader foregroundReader = new FakeForegroundReader(calls, "com.xingin.xhs");
        FakeStatusUploader uploader = new FakeStatusUploader(calls);
        StatusMonitorRunner runner = new StatusMonitorRunner(
                true,
                auth,
                usageAccess,
                foregroundReader,
                statusMapper(),
                uploader,
                clock,
                new BackgroundRefreshPolicy(Duration.ofHours(6)),
                () -> {
                    calls.add("readLastRefreshAt");
                    return lastRefreshAt.get();
                },
                instant -> {
                    calls.add("writeLastRefreshAt:" + instant);
                    lastRefreshAt.set(instant);
                });

        runner.runOnce();
        clock.set("2026-05-27T13:59:59Z");
        runner.runOnce();
        clock.set("2026-05-27T14:00:00Z");
        runner.runOnce();

        assertEquals(2, uploader.uploadCount);
        assertEquals(Instant.parse("2026-05-27T14:00:00Z"), lastRefreshAt.get());
    }

    @Test
    public void runOnceReadsForegroundEvenWhenBackgroundRefreshIsNotDueAndUploadsChangedPackage() {
        List<String> calls = new ArrayList<>();
        MutableClock clock = new MutableClock("2026-05-27T08:00:00Z");
        AtomicReference<Instant> lastRefreshAt = new AtomicReference<>();
        FakeUsageAccessGateway usageAccess = new FakeUsageAccessGateway(calls, true);
        FakeAuthGateway auth = new FakeAuthGateway(calls);
        FakeForegroundReader foregroundReader = new FakeForegroundReader(calls, "com.tencent.mm");
        FakeStatusUploader uploader = new FakeStatusUploader(calls);
        StatusMonitorRunner runner = new StatusMonitorRunner(
                true,
                auth,
                usageAccess,
                foregroundReader,
                statusMapper(),
                uploader,
                clock,
                new BackgroundRefreshPolicy(Duration.ofHours(6)),
                () -> {
                    calls.add("readLastRefreshAt");
                    return lastRefreshAt.get();
                },
                instant -> {
                    calls.add("writeLastRefreshAt:" + instant);
                    lastRefreshAt.set(instant);
                });

        runner.runOnce();
        foregroundReader.packageName = "com.xingin.xhs";
        clock.set("2026-05-27T08:01:00Z");
        runner.runOnce();

        assertEquals(2, uploader.uploadCount);
        assertEquals(
                List.of(
                        "hasUsageAccess",
                        "readForeground",
                        "auth",
                        "upload:social",
                        "writeLastRefreshAt:2026-05-27T08:00:00Z",
                        "hasUsageAccess",
                        "readForeground",
                        "auth",
                        "upload:social",
                        "writeLastRefreshAt:2026-05-27T08:01:00Z"),
                calls);
    }

    @Test
    public void freshRunnerDoesNotBypassBackgroundRefreshForUnchangedCachedForeground() {
        List<String> calls = new ArrayList<>();
        AtomicReference<Instant> lastRefreshAt = new AtomicReference<>();
        AtomicReference<String> lastRefreshForegroundKey = new AtomicReference<>();
        LastForegroundStore foregroundStore = LastForegroundStore.inMemory();
        FakeUsageAccessGateway usageAccess = new FakeUsageAccessGateway(calls, true);
        FakeAuthGateway auth = new FakeAuthGateway(calls);
        FakeForegroundReader foregroundReader = new FakeForegroundReader(calls, "com.tencent.mm");
        FakeStatusUploader uploader = new FakeStatusUploader(calls);
        StatusMonitorRunner firstRunner = policyRunner(
                calls,
                auth,
                usageAccess,
                foregroundReader,
                uploader,
                new MutableClock("2026-05-27T08:00:00Z"),
                lastRefreshAt,
                lastRefreshForegroundKey,
                foregroundStore);

        firstRunner.runOnce();

        StatusMonitorRunner freshRunner = policyRunner(
                calls,
                auth,
                usageAccess,
                foregroundReader,
                uploader,
                new MutableClock("2026-05-27T08:01:00Z"),
                lastRefreshAt,
                lastRefreshForegroundKey,
                foregroundStore);
        freshRunner.runOnce();

        assertEquals(1, uploader.uploadCount);
        assertEquals(
                List.of(
                        "hasUsageAccess",
                        "readForeground",
                        "auth",
                        "upload:social",
                        "writeLastRefreshAt:2026-05-27T08:00:00Z",
                        "hasUsageAccess",
                        "readForeground",
                        "readLastRefreshAt"),
                calls);
    }

    @Test
    public void freshPollRunnerDoesNotDuplicateSuccessfulAccessibilityUploadForSameForeground() {
        List<String> calls = new ArrayList<>();
        AtomicReference<Instant> lastRefreshAt = new AtomicReference<>();
        AtomicReference<String> lastRefreshForegroundKey = new AtomicReference<>();
        LastForegroundStore foregroundStore = LastForegroundStore.inMemory();
        FakeUsageAccessGateway usageAccess = new FakeUsageAccessGateway(calls, true);
        FakeAuthGateway auth = new FakeAuthGateway(calls);
        FakeForegroundReader foregroundReader = new FakeForegroundReader(calls, "com.tencent.mm");
        FakeStatusUploader uploader = new FakeStatusUploader(calls);
        StatusMonitorRunner accessibilityRunner = policyRunner(
                calls,
                auth,
                usageAccess,
                foregroundReader,
                uploader,
                new MutableClock("2026-05-27T08:00:00Z"),
                lastRefreshAt,
                lastRefreshForegroundKey,
                foregroundStore);

        accessibilityRunner.runForPackage("com.tencent.mm");

        StatusMonitorRunner pollRunner = policyRunner(
                calls,
                auth,
                usageAccess,
                foregroundReader,
                uploader,
                new MutableClock("2026-05-27T08:00:15Z"),
                lastRefreshAt,
                lastRefreshForegroundKey,
                foregroundStore);
        pollRunner.runOnce();

        assertEquals(1, uploader.uploadCount);
        assertEquals(Instant.parse("2026-05-27T08:00:00Z"), lastRefreshAt.get());
        assertEquals("com.tencent.mm|social", lastRefreshForegroundKey.get());
        assertEquals(
                List.of(
                        "auth",
                        "upload:social",
                        "writeLastRefreshAt:2026-05-27T08:00:00Z",
                        "hasUsageAccess",
                        "readForeground",
                        "readLastRefreshAt"),
                calls);
    }

    @Test
    public void failedAccessibilityUploadDoesNotPersistSuccessfulForegroundAndCanRetry() {
        List<String> calls = new ArrayList<>();
        AtomicReference<Instant> lastRefreshAt = new AtomicReference<>();
        AtomicReference<String> lastRefreshForegroundKey = new AtomicReference<>();
        LastForegroundStore foregroundStore = LastForegroundStore.inMemory();
        FakeUsageAccessGateway usageAccess = new FakeUsageAccessGateway(calls, true);
        FakeAuthGateway auth = new FakeAuthGateway(calls);
        FakeForegroundReader foregroundReader = new FakeForegroundReader(calls, "com.tencent.mm");
        FakeStatusUploader uploader = new FakeStatusUploader(calls);
        uploader.failNextUpload();
        StatusMonitorRunner failedRunner = policyRunner(
                calls,
                auth,
                usageAccess,
                foregroundReader,
                uploader,
                new MutableClock("2026-05-27T08:00:00Z"),
                lastRefreshAt,
                lastRefreshForegroundKey,
                foregroundStore);

        failedRunner.runForPackage("com.tencent.mm");

        assertEquals(null, lastRefreshAt.get());
        assertEquals(null, lastRefreshForegroundKey.get());

        StatusMonitorRunner retryRunner = policyRunner(
                calls,
                auth,
                usageAccess,
                foregroundReader,
                uploader,
                new MutableClock("2026-05-27T08:00:15Z"),
                lastRefreshAt,
                lastRefreshForegroundKey,
                foregroundStore);
        retryRunner.runForPackage("com.tencent.mm");

        assertEquals(2, uploader.uploadCount);
        assertEquals(Instant.parse("2026-05-27T08:00:15Z"), lastRefreshAt.get());
        assertEquals("com.tencent.mm|social", lastRefreshForegroundKey.get());
        assertEquals(
                List.of(
                        "auth",
                        "upload:social",
                        "auth",
                        "upload:social",
                        "writeLastRefreshAt:2026-05-27T08:00:15Z"),
                calls);
    }

    @Test
    public void eventUploadDedupesSameForegroundInsideFifteenMinutes() {
        List<String> calls = new ArrayList<>();
        AtomicReference<Instant> lastRefreshAt = new AtomicReference<>();
        AtomicReference<String> lastRefreshForegroundKey = new AtomicReference<>();
        LastForegroundStore foregroundStore = LastForegroundStore.inMemory();
        FakeUsageAccessGateway usageAccess = new FakeUsageAccessGateway(calls, true);
        FakeAuthGateway auth = new FakeAuthGateway(calls);
        FakeForegroundReader foregroundReader = new FakeForegroundReader(calls, "com.tencent.mm");
        FakeStatusUploader uploader = new FakeStatusUploader(calls);
        MutableClock clock = new MutableClock("2026-05-27T08:00:00Z");
        StatusMonitorRunner runner = policyRunner(
                calls,
                auth,
                usageAccess,
                foregroundReader,
                uploader,
                clock,
                lastRefreshAt,
                lastRefreshForegroundKey,
                foregroundStore);

        runner.runForPackage("com.tencent.mm");
        clock.set("2026-05-27T08:14:59Z");
        runner.runForPackage("com.tencent.mm");

        assertEquals(1, uploader.uploadCount);
        assertEquals(Instant.parse("2026-05-27T08:00:00Z"), lastRefreshAt.get());
        assertEquals("com.tencent.mm|social", lastRefreshForegroundKey.get());
    }

    @Test
    public void eventUploadRefreshesSameForegroundAfterFifteenMinutesDespiteBackgroundPolicy() {
        List<String> calls = new ArrayList<>();
        AtomicReference<Instant> lastRefreshAt = new AtomicReference<>();
        AtomicReference<String> lastRefreshForegroundKey = new AtomicReference<>();
        LastForegroundStore foregroundStore = LastForegroundStore.inMemory();
        FakeUsageAccessGateway usageAccess = new FakeUsageAccessGateway(calls, true);
        FakeAuthGateway auth = new FakeAuthGateway(calls);
        FakeForegroundReader foregroundReader = new FakeForegroundReader(calls, "com.tencent.mm");
        FakeStatusUploader uploader = new FakeStatusUploader(calls);
        MutableClock clock = new MutableClock("2026-05-27T08:00:00Z");
        StatusMonitorRunner runner = policyRunner(
                calls,
                auth,
                usageAccess,
                foregroundReader,
                uploader,
                clock,
                lastRefreshAt,
                lastRefreshForegroundKey,
                foregroundStore);

        runner.runForPackage("com.tencent.mm");
        clock.set("2026-05-27T08:15:00Z");
        runner.runForPackage("com.tencent.mm");

        assertEquals(2, uploader.uploadCount);
        assertEquals(Instant.parse("2026-05-27T08:15:00Z"), lastRefreshAt.get());
        assertEquals("com.tencent.mm|social", lastRefreshForegroundKey.get());
    }

    @Test
    public void overlappingRunnerInstancesDoNotDuplicateSameForegroundUpload() throws Exception {
        List<String> calls = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<Instant> lastRefreshAt = new AtomicReference<>();
        AtomicReference<String> lastRefreshForegroundKey = new AtomicReference<>();
        LastForegroundStore foregroundStore = LastForegroundStore.inMemory();
        FakeUsageAccessGateway usageAccess = new FakeUsageAccessGateway(calls, true);
        FakeAuthGateway auth = new FakeAuthGateway(calls);
        FakeForegroundReader foregroundReader = new FakeForegroundReader(calls, "com.tencent.mm");
        BlockingFirstUploadStatusUploader uploader = new BlockingFirstUploadStatusUploader(calls);
        StatusMonitorRunner firstRunner = policyRunner(
                calls,
                auth,
                usageAccess,
                foregroundReader,
                uploader,
                new MutableClock("2026-05-27T08:00:00Z"),
                lastRefreshAt,
                lastRefreshForegroundKey,
                foregroundStore);
        StatusMonitorRunner secondRunner = policyRunner(
                calls,
                auth,
                usageAccess,
                foregroundReader,
                uploader,
                new MutableClock("2026-05-27T08:00:01Z"),
                lastRefreshAt,
                lastRefreshForegroundKey,
                foregroundStore);

        Thread firstThread = new Thread(() -> firstRunner.runForPackage("com.tencent.mm"));
        firstThread.start();
        assertTrue(uploader.awaitFirstUploadStarted());

        Thread secondThread = new Thread(() -> secondRunner.runForPackage("com.tencent.mm"));
        secondThread.start();
        secondThread.join(100L);
        assertTrue(secondThread.isAlive());

        uploader.finishFirstUpload();
        firstThread.join(1_000L);
        secondThread.join(1_000L);

        assertFalse(firstThread.isAlive());
        assertFalse(secondThread.isAlive());
        assertEquals(1, uploader.uploadCount);
        assertEquals(Instant.parse("2026-05-27T08:00:00Z"), lastRefreshAt.get());
        assertEquals("com.tencent.mm|social", lastRefreshForegroundKey.get());
    }

    @Test
    public void runnerUsesNewerSharedSuccessKeyWhenInstanceHasOlderUpload() {
        List<String> calls = new ArrayList<>();
        AtomicReference<Instant> lastRefreshAt = new AtomicReference<>();
        AtomicReference<String> lastRefreshForegroundKey = new AtomicReference<>();
        LastForegroundStore foregroundStore = LastForegroundStore.inMemory();
        FakeUsageAccessGateway usageAccess = new FakeUsageAccessGateway(calls, true);
        FakeAuthGateway auth = new FakeAuthGateway(calls);
        FakeForegroundReader foregroundReader = new FakeForegroundReader(calls, "com.tencent.mm");
        FakeStatusUploader uploader = new FakeStatusUploader(calls);
        MutableClock firstClock = new MutableClock("2026-05-27T08:00:00Z");
        StatusMonitorRunner firstRunner = policyRunner(
                calls,
                auth,
                usageAccess,
                foregroundReader,
                uploader,
                firstClock,
                lastRefreshAt,
                lastRefreshForegroundKey,
                foregroundStore);
        StatusMonitorRunner secondRunner = policyRunner(
                calls,
                auth,
                usageAccess,
                foregroundReader,
                uploader,
                new MutableClock("2026-05-27T08:01:00Z"),
                lastRefreshAt,
                lastRefreshForegroundKey,
                foregroundStore);

        firstRunner.runForPackage("com.tencent.mm");
        secondRunner.runForPackage("com.xingin.xhs");
        foregroundReader.packageName = "com.xingin.xhs";
        firstClock.set("2026-05-27T08:02:00Z");
        firstRunner.runOnce();

        assertEquals(2, uploader.uploadCount);
        assertEquals(Instant.parse("2026-05-27T08:01:00Z"), lastRefreshAt.get());
        assertEquals("com.xingin.xhs|social", lastRefreshForegroundKey.get());
    }

    @Test
    public void backgroundRefreshUsesSharedKeyAndTimeFromSameSuccessfulState() {
        List<String> calls = new ArrayList<>();
        AtomicReference<Instant> lastRefreshAt = new AtomicReference<>();
        AtomicReference<String> lastRefreshForegroundKey = new AtomicReference<>();
        LastForegroundStore foregroundStore = LastForegroundStore.inMemory();
        FakeUsageAccessGateway usageAccess = new FakeUsageAccessGateway(calls, true);
        FakeAuthGateway auth = new FakeAuthGateway(calls);
        FakeForegroundReader foregroundReader = new FakeForegroundReader(calls, "com.tencent.mm");
        FakeStatusUploader uploader = new FakeStatusUploader(calls);
        MutableClock pollClock = new MutableClock("2026-05-27T08:00:00Z");
        StatusMonitorRunner pollRunner = policyRunner(
                calls,
                auth,
                usageAccess,
                foregroundReader,
                uploader,
                pollClock,
                lastRefreshAt,
                lastRefreshForegroundKey,
                foregroundStore);
        StatusMonitorRunner accessibilityRunner = policyRunner(
                calls,
                auth,
                usageAccess,
                foregroundReader,
                uploader,
                new MutableClock("2026-05-27T08:01:00Z"),
                lastRefreshAt,
                lastRefreshForegroundKey,
                foregroundStore);

        pollRunner.runOnce();
        accessibilityRunner.runForPackage("com.xingin.xhs");
        pollClock.set("2026-05-27T08:20:00Z");
        foregroundReader.packageName = "com.tencent.mm";
        pollRunner.runOnce();

        assertEquals(3, uploader.uploadCount);
        assertEquals(Instant.parse("2026-05-27T08:20:00Z"), lastRefreshAt.get());
        assertEquals("com.tencent.mm|social", lastRefreshForegroundKey.get());
    }

    @Test
    public void freshRunnerRetriesChangedPackageAfterFailedUploadDespiteCachedDetection() {
        List<String> calls = new ArrayList<>();
        AtomicReference<Instant> lastRefreshAt = new AtomicReference<>();
        AtomicReference<String> lastRefreshForegroundKey = new AtomicReference<>();
        LastForegroundStore foregroundStore = LastForegroundStore.inMemory();
        FakeUsageAccessGateway usageAccess = new FakeUsageAccessGateway(calls, true);
        FakeAuthGateway auth = new FakeAuthGateway(calls);
        FakeForegroundReader foregroundReader = new FakeForegroundReader(calls, "com.tencent.mm");
        FakeStatusUploader uploader = new FakeStatusUploader(calls);
        StatusMonitorRunner firstRunner = policyRunner(
                calls,
                auth,
                usageAccess,
                foregroundReader,
                uploader,
                new MutableClock("2026-05-27T08:00:00Z"),
                lastRefreshAt,
                lastRefreshForegroundKey,
                foregroundStore);

        firstRunner.runOnce();
        foregroundReader.packageName = "com.xingin.xhs";
        uploader.failNextUpload();

        StatusMonitorRunner failedChangedPackageRunner = policyRunner(
                calls,
                auth,
                usageAccess,
                foregroundReader,
                uploader,
                new MutableClock("2026-05-27T08:01:00Z"),
                lastRefreshAt,
                lastRefreshForegroundKey,
                foregroundStore);
        failedChangedPackageRunner.runOnce();

        assertEquals(2, uploader.uploadCount);
        assertEquals(Instant.parse("2026-05-27T08:00:00Z"), lastRefreshAt.get());

        StatusMonitorRunner retryRunner = policyRunner(
                calls,
                auth,
                usageAccess,
                foregroundReader,
                uploader,
                new MutableClock("2026-05-27T08:02:00Z"),
                lastRefreshAt,
                lastRefreshForegroundKey,
                foregroundStore);
        retryRunner.runOnce();

        assertEquals(3, uploader.uploadCount);
        assertEquals(Instant.parse("2026-05-27T08:02:00Z"), lastRefreshAt.get());
        assertEquals(
                List.of(
                        "hasUsageAccess",
                        "readForeground",
                        "auth",
                        "upload:social",
                        "writeLastRefreshAt:2026-05-27T08:00:00Z",
                        "hasUsageAccess",
                        "readForeground",
                        "auth",
                        "upload:social",
                        "hasUsageAccess",
                        "readForeground",
                        "auth",
                        "upload:social",
                        "writeLastRefreshAt:2026-05-27T08:02:00Z"),
                calls);
    }

    @Test
    public void failedUploadDoesNotPersistRefreshOrDedupState() {
        List<String> calls = new ArrayList<>();
        MutableClock clock = new MutableClock("2026-05-27T08:00:00Z");
        AtomicReference<Instant> lastRefreshAt = new AtomicReference<>();
        FakeUsageAccessGateway usageAccess = new FakeUsageAccessGateway(calls, true);
        FakeAuthGateway auth = new FakeAuthGateway(calls);
        FakeForegroundReader foregroundReader = new FakeForegroundReader(calls, "com.tencent.mm");
        FakeStatusUploader uploader = new FakeStatusUploader(calls);
        uploader.failNextUpload();
        StatusMonitorRunner runner = new StatusMonitorRunner(
                true,
                auth,
                usageAccess,
                foregroundReader,
                statusMapper(),
                uploader,
                clock,
                new BackgroundRefreshPolicy(Duration.ofHours(6)),
                () -> {
                    calls.add("readLastRefreshAt");
                    return lastRefreshAt.get();
                },
                instant -> {
                    calls.add("writeLastRefreshAt:" + instant);
                    lastRefreshAt.set(instant);
                });

        runner.runOnce();
        assertEquals(null, lastRefreshAt.get());
        clock.set("2026-05-27T08:01:00Z");
        runner.runOnce();

        assertEquals(2, uploader.uploadCount);
        assertEquals(Instant.parse("2026-05-27T08:01:00Z"), lastRefreshAt.get());
        assertEquals(
                List.of(
                        "hasUsageAccess",
                        "readForeground",
                        "auth",
                        "upload:social",
                        "hasUsageAccess",
                        "readForeground",
                        "auth",
                        "upload:social",
                        "writeLastRefreshAt:2026-05-27T08:01:00Z"),
                calls);
    }

    private static AuthSession session() {
        return new AuthSession("access-token", "refresh-token", Long.MAX_VALUE);
    }

    private static StatusMonitorRunner runner(
            boolean supabaseConfigured,
            AuthGateway auth,
            UsageAccessGateway usageAccess,
            ForegroundAppReader foregroundReader,
            StatusUploader uploader) {
        return runner(
                supabaseConfigured,
                auth,
                usageAccess,
                foregroundReader,
                uploader,
                () -> Instant.parse("2026-05-26T12:00:00Z"));
    }

    private static StatusMonitorRunner runner(
            boolean supabaseConfigured,
            AuthGateway auth,
            UsageAccessGateway usageAccess,
            ForegroundAppReader foregroundReader,
            StatusUploader uploader,
            Supplier<Instant> clock) {
        return new StatusMonitorRunner(
                supabaseConfigured,
                auth,
                usageAccess,
                foregroundReader,
                StatusMapper.fromJson("""
                        [
                          {"package":"com.tencent.mm","statusCode":"social"},
                          {"package":"com.xingin.xhs","statusCode":"social"}
                        ]
                        """),
                uploader,
                clock);
    }

    private static StatusMapper statusMapper() {
        return StatusMapper.fromJson("""
                [
                  {"package":"com.tencent.mm","statusCode":"social"},
                  {"package":"com.xingin.xhs","statusCode":"social"}
                ]
                """);
    }

    private static StatusMonitorRunner policyRunner(
            List<String> calls,
            AuthGateway auth,
            UsageAccessGateway usageAccess,
            ForegroundAppReader foregroundReader,
            StatusUploader uploader,
            Supplier<Instant> clock,
            AtomicReference<Instant> lastRefreshAt,
            AtomicReference<String> lastRefreshForegroundKey,
            LastForegroundStore foregroundStore) {
        return new StatusMonitorRunner(
                true,
                auth,
                usageAccess,
                foregroundReader,
                statusMapper(),
                uploader,
                clock,
                new BackgroundRefreshPolicy(Duration.ofHours(6)),
                () -> {
                    calls.add("readLastRefreshAt");
                    return lastRefreshAt.get();
                },
                instant -> {
                    calls.add("writeLastRefreshAt:" + instant);
                    lastRefreshAt.set(instant);
                },
                foregroundStore,
                lastRefreshForegroundKey::get,
                lastRefreshForegroundKey::set);
    }

    private static final class FakeUsageAccessGateway implements UsageAccessGateway {
        private final List<String> calls;
        private final boolean hasUsageAccess;

        private FakeUsageAccessGateway(List<String> calls, boolean hasUsageAccess) {
            this.calls = calls;
            this.hasUsageAccess = hasUsageAccess;
        }

        @Override
        public boolean hasUsageAccess() {
            calls.add("hasUsageAccess");
            return hasUsageAccess;
        }

        @Override
        public void openUsageAccessSettings() {
        }
    }

    private static final class FakeAuthGateway implements AuthGateway {
        private final List<String> calls;

        private FakeAuthGateway(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public ApiResult<AuthSession> ensureSession() {
            calls.add("auth");
            return ApiResult.success(session());
        }
    }

    private static final class FakeForegroundReader implements ForegroundAppReader {
        private final List<String> calls;
        private String packageName;
        private long lastLookbackMillis;

        private FakeForegroundReader(List<String> calls, String packageName) {
            this.calls = calls;
            this.packageName = packageName;
        }

        @Override
        public String readMostRecentForegroundPackage(long lookbackMillis) {
            calls.add("readForeground");
            lastLookbackMillis = lookbackMillis;
            return packageName;
        }
    }

    private static final class FakeStatusUploader implements StatusUploader {
        private final List<String> calls;
        private int uploadCount;
        private boolean failNextUpload;

        private FakeStatusUploader(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public ApiResult<CurrentStatusResult> uploadCurrentStatus(
                AuthSession session,
                String statusCode,
                Instant statusUpdatedAt) {
            calls.add("upload:" + statusCode);
            uploadCount++;
            if (failNextUpload) {
                failNextUpload = false;
                return ApiResult.error(new ApiError("upload_failed", "Upload failed"));
            }
            return ApiResult.success(new CurrentStatusResult(
                    statusCode,
                    statusUpdatedAt,
                    statusUpdatedAt.plusSeconds(900),
                    "{}"));
        }

        private void failNextUpload() {
            failNextUpload = true;
        }
    }

    private static final class BlockingFirstUploadStatusUploader implements StatusUploader {
        private final List<String> calls;
        private final CountDownLatch firstUploadStarted = new CountDownLatch(1);
        private final CountDownLatch finishFirstUpload = new CountDownLatch(1);
        private int uploadCount;

        private BlockingFirstUploadStatusUploader(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public ApiResult<CurrentStatusResult> uploadCurrentStatus(
                AuthSession session,
                String statusCode,
                Instant statusUpdatedAt) {
            synchronized (this) {
                calls.add("upload:" + statusCode);
                uploadCount++;
            }
            if (uploadCount == 1) {
                firstUploadStarted.countDown();
                try {
                    assertTrue(finishFirstUpload.await(1, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }
            }
            return ApiResult.success(new CurrentStatusResult(
                    statusCode,
                    statusUpdatedAt,
                    statusUpdatedAt.plusSeconds(900),
                    "{}"));
        }

        private boolean awaitFirstUploadStarted() throws InterruptedException {
            return firstUploadStarted.await(1, TimeUnit.SECONDS);
        }

        private void finishFirstUpload() {
            finishFirstUpload.countDown();
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
}
