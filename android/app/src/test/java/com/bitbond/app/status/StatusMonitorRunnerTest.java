package com.bitbond.app.status;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.bitbond.app.api.ApiResult;
import com.bitbond.app.auth.AuthGateway;
import com.bitbond.app.auth.AuthSession;
import com.bitbond.app.status.StatusModels.CurrentStatusResult;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
    public void runOnceUploadsOnlineWhenForegroundPackageIsUnavailable() {
        List<String> calls = new ArrayList<>();
        FakeUsageAccessGateway usageAccess = new FakeUsageAccessGateway(calls, true);
        FakeAuthGateway auth = new FakeAuthGateway(calls);
        FakeForegroundReader foregroundReader = new FakeForegroundReader(calls, null);
        FakeStatusUploader uploader = new FakeStatusUploader(calls);
        StatusMonitorRunner runner = runner(true, auth, usageAccess, foregroundReader, uploader);

        runner.runOnce();

        assertEquals(List.of("hasUsageAccess", "readForeground", "auth", "upload:online"), calls);
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
    public void runOnceReadsForegroundEventsWithFiveMinuteLookback() {
        List<String> calls = new ArrayList<>();
        FakeUsageAccessGateway usageAccess = new FakeUsageAccessGateway(calls, true);
        FakeAuthGateway auth = new FakeAuthGateway(calls);
        FakeForegroundReader foregroundReader = new FakeForegroundReader(calls, "com.xingin.xhs");
        FakeStatusUploader uploader = new FakeStatusUploader(calls);
        StatusMonitorRunner runner = runner(true, auth, usageAccess, foregroundReader, uploader);

        runner.runOnce();

        assertEquals(Duration.ofMinutes(5).toMillis(), foregroundReader.lastLookbackMillis);
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
            return ApiResult.success(new CurrentStatusResult(
                    statusCode,
                    statusUpdatedAt,
                    statusUpdatedAt.plusSeconds(900),
                    "{}"));
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
