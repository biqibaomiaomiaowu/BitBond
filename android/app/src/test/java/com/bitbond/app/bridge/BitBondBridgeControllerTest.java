package com.bitbond.app.bridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.bitbond.app.MainActivity;
import com.bitbond.app.api.ApiError;
import com.bitbond.app.api.ApiResult;
import com.bitbond.app.auth.AuthGateway;
import com.bitbond.app.auth.AuthSession;
import com.bitbond.app.auth.SessionStore;
import com.bitbond.app.avatar.AvatarGateway;
import com.bitbond.app.avatar.AvatarModels.AvatarOption;
import com.bitbond.app.pairing.PairingGateway;
import com.bitbond.app.pairing.PairingModels.CoupleState;
import com.bitbond.app.pairing.PairingModels.PairInvite;
import com.bitbond.app.pairing.PairingModels.PartnerProfile;
import com.bitbond.app.status.DebugForegroundGateway;
import com.bitbond.app.status.StatusGateway;
import com.bitbond.app.status.StatusModels.PartnerStatus;
import com.bitbond.app.status.StatusUploadTrigger;
import com.bitbond.app.status.AccessibilityAccessGateway;
import com.bitbond.app.status.BatteryOptimizationGateway;
import com.bitbond.app.status.UsageAccessGateway;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class BitBondBridgeControllerTest {
    @Test
    public void pingReturnsStableBridgeMessage() {
        assertEquals("Android WebView bridge connected", new Fixtures().controller().ping());
    }

    @Test
    public void getInitialStateAuthenticatesAndReturnsCurrentCouple() throws Exception {
        Fixtures fixtures = new Fixtures();
        fixtures.pairing.currentCouple = CoupleState.paired(
                "couple-1",
                new PartnerProfile("小禾", "avatar_cat"));

        JSONObject response = json(fixtures.controller().getInitialState());

        assertTrue(response.getJSONObject("bridge").getBoolean("ready"));
        assertTrue(response.getJSONObject("pair").getBoolean("paired"));
        assertEquals("小禾", response.getJSONObject("pair").getString("nickname"));
        assertEquals("offline", response.getJSONObject("partner").getString("statusCode"));
        assertEquals(Arrays.asList("auth", "getCurrentCouple"), fixtures.calls);
    }

    @Test
    public void getInitialStateReturnsFrontendCompatibleUnpairedState() throws Exception {
        Fixtures fixtures = new Fixtures();

        JSONObject response = json(fixtures.controller().getInitialState());

        assertTrue(response.getJSONObject("bridge").getBoolean("ready"));
        assertFalse(response.getJSONObject("pair").getBoolean("paired"));
        assertEquals("", response.getJSONObject("pair").getString("nickname"));
        assertEquals("offline", response.getJSONObject("partner").getString("statusCode"));
    }

    @Test
    public void getInitialStateAuthFailureKeepsBridgeReadyWithOfflineState() throws Exception {
        Fixtures fixtures = new Fixtures();
        fixtures.auth.result = ApiResult.error(new ApiError("auth_failed", "Auth failed"));

        JSONObject response = json(fixtures.controller().getInitialState());

        assertFalse(response.has("ok"));
        assertTrue(response.getJSONObject("bridge").getBoolean("ready"));
        assertEquals("Android WebView bridge connected", response.getJSONObject("bridge").getString("message"));
        assertFalse(response.toString().contains("浏览器预览"));
        assertFalse(response.toString().contains("Bridge 初始化失败"));
        assertFalse(response.getJSONObject("pair").getBoolean("paired"));
        assertEquals("offline", response.getJSONObject("partner").getString("statusCode"));
    }

    @Test
    public void getInitialStatePairingFailureKeepsBridgeReadyWithOfflineState() throws Exception {
        Fixtures fixtures = new Fixtures();
        fixtures.pairing.currentCoupleResult = ApiResult.error(new ApiError("pairing_failed", "Pairing failed"));

        JSONObject response = json(fixtures.controller().getInitialState());

        assertFalse(response.has("ok"));
        assertTrue(response.getJSONObject("bridge").getBoolean("ready"));
        assertEquals("Android WebView bridge connected", response.getJSONObject("bridge").getString("message"));
        assertFalse(response.toString().contains("浏览器预览"));
        assertFalse(response.toString().contains("Bridge 初始化失败"));
        assertFalse(response.getJSONObject("pair").getBoolean("paired"));
        assertEquals("offline", response.getJSONObject("partner").getString("statusCode"));
    }

    @Test
    public void checkUsageAccessAuthenticatesAndReturnsBoolean() throws Exception {
        Fixtures fixtures = new Fixtures();
        fixtures.usageAccess.hasUsageAccess = true;

        JSONObject response = json(fixtures.controller().checkUsageAccess());

        assertOk(response);
        assertTrue(response.getJSONObject("data").getBoolean("hasUsageAccess"));
        assertEquals(Arrays.asList("auth", "hasUsageAccess"), fixtures.calls);
    }

    @Test
    public void openUsageAccessSettingsAuthenticatesAndOpensSettings() throws Exception {
        Fixtures fixtures = new Fixtures();

        JSONObject response = json(fixtures.controller().openUsageAccessSettings());

        assertOk(response);
        assertTrue(response.getJSONObject("data").getBoolean("opened"));
        assertEquals(1, fixtures.usageAccess.openCallCount);
        assertEquals(Arrays.asList("auth", "openUsageAccessSettings"), fixtures.calls);
    }

    @Test
    public void checkAccessibilityAccessAuthenticatesAndReturnsBoolean() throws Exception {
        Fixtures fixtures = new Fixtures();
        fixtures.accessibilityAccess.hasAccessibilityAccess = true;

        JSONObject response = json(fixtures.controller().checkAccessibilityAccess());

        assertOk(response);
        assertTrue(response.getJSONObject("data").getBoolean("hasAccessibilityAccess"));
        assertEquals(Arrays.asList("auth", "hasAccessibilityAccess"), fixtures.calls);
    }

    @Test
    public void openAccessibilitySettingsAuthenticatesAndOpensSettings() throws Exception {
        Fixtures fixtures = new Fixtures();

        JSONObject response = json(fixtures.controller().openAccessibilitySettings());

        assertOk(response);
        assertTrue(response.getJSONObject("data").getBoolean("opened"));
        assertEquals(1, fixtures.accessibilityAccess.openCallCount);
        assertEquals(Arrays.asList("auth", "openAccessibilitySettings"), fixtures.calls);
    }

    @Test
    public void checkBatteryOptimizationAuthenticatesAndReturnsBoolean() throws Exception {
        Fixtures fixtures = new Fixtures();
        fixtures.batteryOptimization.ignoringBatteryOptimizations = true;

        JSONObject response = json(fixtures.controller().checkBatteryOptimization());

        assertOk(response);
        assertTrue(response.getJSONObject("data").getBoolean("isIgnoringBatteryOptimizations"));
        assertEquals(Arrays.asList("auth", "isIgnoringBatteryOptimizations"), fixtures.calls);
    }

    @Test
    public void openBatteryOptimizationSettingsAuthenticatesAndOpensSettings() throws Exception {
        Fixtures fixtures = new Fixtures();

        JSONObject response = json(fixtures.controller().openBatteryOptimizationSettings());

        assertOk(response);
        assertTrue(response.getJSONObject("data").getBoolean("opened"));
        assertEquals(1, fixtures.batteryOptimization.openCallCount);
        assertEquals(Arrays.asList("auth", "openBatteryOptimizationSettings"), fixtures.calls);
    }

    @Test
    public void getDebugForegroundAppAuthenticatesAndWrapsDebugPayload() throws Exception {
        Fixtures fixtures = new Fixtures();
        fixtures.debugForeground.result = new JSONObject()
                .put("enabled", true)
                .put("code", "ok")
                .put("packageName", "com.spotify.music");

        JSONObject response = json(fixtures.controller().getDebugForegroundApp());

        assertOk(response);
        JSONObject data = response.getJSONObject("data");
        assertTrue(data.getBoolean("enabled"));
        assertEquals("ok", data.getString("code"));
        assertEquals("com.spotify.music", data.getString("packageName"));
        assertEquals(Arrays.asList("auth", "debugForegroundApp"), fixtures.calls);
    }

    @Test
    public void createPairInviteAuthenticatesAndSerializesInvite() throws Exception {
        Fixtures fixtures = new Fixtures();
        fixtures.pairing.invite = new PairInvite("123456", "2026-05-26T10:00:00Z");

        JSONObject response = json(fixtures.controller().createPairInvite());

        assertOk(response);
        assertEquals("123456", response.getJSONObject("data").getString("code"));
        assertEquals("2026-05-26T10:00:00Z", response.getJSONObject("data").getString("expiresAt"));
        assertEquals(Arrays.asList("auth", "createInvite"), fixtures.calls);
    }

    @Test
    public void acceptPairInviteTrimsCodeAndSerializesCoupleState() throws Exception {
        Fixtures fixtures = new Fixtures();
        fixtures.pairing.acceptedCouple = CoupleState.paired(
                "couple-2",
                new PartnerProfile("阿树", "avatar_fox"));

        JSONObject response = json(fixtures.controller().acceptPairInvite("{\"code\":\"  ABC123  \"}"));

        assertOk(response);
        JSONObject data = response.getJSONObject("data");
        assertTrue(data.getBoolean("paired"));
        assertEquals("couple-2", data.getString("coupleId"));
        assertEquals("阿树", data.getJSONObject("partner").getString("nickname"));
        assertEquals("avatar_fox", data.getJSONObject("partner").getString("avatarId"));
        assertEquals("ABC123", fixtures.pairing.lastAcceptedCode);
        assertEquals(Arrays.asList("auth", "acceptInvite"), fixtures.calls);
    }

    @Test
    public void acceptPairInviteBlankCodeReturnsInvalidInviteCodeWithoutCallingGateway() throws Exception {
        Fixtures fixtures = new Fixtures();

        JSONObject response = json(fixtures.controller().acceptPairInvite("{\"code\":\"   \",\"secret\":\"do-not-leak\"}"));

        assertError(response, "invalid_invite_code");
        assertFalse(response.toString().contains("do-not-leak"));
        assertEquals(Arrays.asList("auth"), fixtures.calls);
        assertEquals(0, fixtures.pairing.acceptCallCount);
    }

    @Test
    public void unlinkAuthenticatesAndReturnsUnlinkedFlag() throws Exception {
        Fixtures fixtures = new Fixtures();

        JSONObject response = json(fixtures.controller().unlink());

        assertOk(response);
        assertTrue(response.getJSONObject("data").getBoolean("unlinked"));
        assertEquals(Arrays.asList("auth", "unlink"), fixtures.calls);
    }

    @Test
    public void unlinkPayloadSignatureAuthenticatesAndReturnsUnlinkedFlag() throws Exception {
        Fixtures fixtures = new Fixtures();

        JSONObject response = json(fixtures.controller().unlink("{\"confirmed\":true}"));

        assertOk(response);
        assertTrue(response.getJSONObject("data").getBoolean("unlinked"));
        assertEquals(Arrays.asList("auth", "unlink"), fixtures.calls);
    }

    @Test
    public void listAvatarsAuthenticatesAndSerializesAvatarArray() throws Exception {
        Fixtures fixtures = new Fixtures();
        fixtures.avatar.avatars = Arrays.asList(
                new AvatarOption("avatar_cat", "Cat", "cat_asset"),
                new AvatarOption("avatar_fox", "Fox", "fox_asset"));

        JSONObject response = json(fixtures.controller().listAvatars());

        assertOk(response);
        JSONArray avatars = response.getJSONObject("data").getJSONArray("avatars");
        assertEquals(2, avatars.length());
        assertEquals("avatar_cat", avatars.getJSONObject(0).getString("id"));
        assertEquals("Cat", avatars.getJSONObject(0).getString("name"));
        assertEquals("cat_asset", avatars.getJSONObject(0).getString("assetKey"));
        assertEquals("avatar_fox", avatars.getJSONObject(1).getString("id"));
        assertEquals(Arrays.asList("auth", "listAvatars"), fixtures.calls);
    }

    @Test
    public void selectAvatarTrimsAvatarIdAndReturnsSelectedAvatarId() throws Exception {
        Fixtures fixtures = new Fixtures();
        fixtures.avatar.selectedAvatarId = "avatar_fox";

        JSONObject response = json(fixtures.controller().selectAvatar("{\"avatarId\":\" avatar_fox \"}"));

        assertOk(response);
        assertEquals("avatar_fox", response.getJSONObject("data").getString("avatarId"));
        assertEquals("avatar_fox", fixtures.avatar.lastSelectedAvatarId);
        assertEquals(Arrays.asList("auth", "selectAvatar"), fixtures.calls);
    }

    @Test
    public void selectAvatarBlankIdReturnsInvalidAvatarIdWithoutCallingGateway() throws Exception {
        Fixtures fixtures = new Fixtures();

        JSONObject response = json(fixtures.controller().selectAvatar("{\"avatarId\":\"   \"}"));

        assertError(response, "invalid_avatar_id");
        assertEquals(Arrays.asList("auth"), fixtures.calls);
        assertEquals(0, fixtures.avatar.selectCallCount);
    }

    @Test
    public void refreshPartnerStatusUploadsDetectedStatusBeforeReadingPartnerStatus() throws Exception {
        Fixtures fixtures = new Fixtures();
        fixtures.uploadTrigger.code = "deduplicated";
        fixtures.status.partnerStatus = pairedPartnerStatus();

        JSONObject response = json(fixtures.controller().refreshPartnerStatus());

        assertOk(response);
        JSONObject data = response.getJSONObject("data");
        assertEquals("deduplicated", data.getJSONObject("upload").getString("code"));
        assertTrue(data.getJSONObject("partnerStatus").getBoolean("paired"));
        assertEquals("music", data.getJSONObject("partnerStatus").getString("statusCode"));
        assertEquals(Arrays.asList("auth", "uploadDetectedStatus", "getPartnerStatus"), fixtures.calls);
    }

    @Test
    public void uploadCurrentStatusAuthenticatesAndReturnsUploadCode() throws Exception {
        Fixtures fixtures = new Fixtures();
        fixtures.uploadTrigger.code = "uploaded";

        JSONObject response = json(fixtures.controller().uploadCurrentStatus());

        assertOk(response);
        assertEquals("uploaded", response.getJSONObject("data").getString("code"));
        assertEquals(Arrays.asList("auth", "uploadDetectedStatus"), fixtures.calls);
    }

    @Test
    public void refreshHomeStateAuthenticatesUploadsThenFetchesPartnerStatusWithAppendixResponseShape() throws Exception {
        Fixtures fixtures = new Fixtures();
        fixtures.uploadTrigger.code = "uploaded";
        fixtures.status.partnerStatus = pairedPartnerStatus();

        String rawResponse = fixtures.controller().refreshHomeState();
        JSONObject response = json(rawResponse);

        assertOk(response);
        assertEquals(
                "{\"ok\":true,\"data\":{\"upload\":{\"code\":\"uploaded\"},\"partnerStatus\":{\"paired\":true}}}",
                rawResponse);
        assertEquals(Arrays.asList("auth", "uploadDetectedStatus", "getPartnerStatus"), fixtures.calls);
    }

    @Test
    public void gatewayFailureReturnsBridgeErrorInsteadOfThrowing() throws Exception {
        Fixtures fixtures = new Fixtures();
        fixtures.auth.result = ApiResult.error(new ApiError("auth_failed", "Auth failed"));

        JSONObject response = json(fixtures.controller().createPairInvite());

        assertError(response, "auth_failed");
    }

    @Test
    public void shouldEnableWebContentsDebuggingFollowsBuildDebugFlag() {
        assertTrue(MainActivity.shouldEnableWebContentsDebugging(true));
        assertFalse(MainActivity.shouldEnableWebContentsDebugging(false));
    }

    @Test
    public void notificationPermissionRequestIsOnlyNeededOnAndroidThirteenAndAboveWhenDenied() {
        assertFalse(MainActivity.shouldRequestPostNotificationsPermission(32, false));
        assertTrue(MainActivity.shouldRequestPostNotificationsPermission(33, false));
        assertFalse(MainActivity.shouldRequestPostNotificationsPermission(33, true));
    }

    @Test
    public void onResumeStatusSyncSchedulesNetworkWorkOnExecutor() {
        List<String> calls = new ArrayList<>();
        FakeAuthGateway auth = new FakeAuthGateway(calls);
        FakeStatusUploadTrigger upload = new FakeStatusUploadTrigger(calls);
        FakeUsageAccessGateway usageAccess = new FakeUsageAccessGateway(calls);
        usageAccess.hasUsageAccess = true;
        RecordingExecutor executor = new RecordingExecutor(calls);

        MainActivity.syncStatusOnResume(true, auth, upload, usageAccess, executor);

        assertEquals(Arrays.asList("hasUsageAccess", "execute"), calls);
        assertEquals(0, executor.runCount);

        executor.runNext();

        assertEquals(Arrays.asList("hasUsageAccess", "execute", "auth", "uploadDetectedStatus"), calls);
        assertEquals(1, executor.runCount);
    }

    @Test
    public void serialAuthGatewayPreventsConcurrentEnsureSessionCalls() throws Exception {
        BlockingAuthGateway delegate = new BlockingAuthGateway();
        AuthGateway serialized = MainActivity.serialAuthGateway(delegate);

        assertSerialized(delegate, serialized::ensureSession, serialized::ensureSession);

        assertEquals(1, delegate.maxConcurrentCalls());
    }

    @Test
    public void serialStatusUploadTriggerPreventsConcurrentUploadCalls() throws Exception {
        BlockingStatusUploadTrigger delegate = new BlockingStatusUploadTrigger();
        StatusUploadTrigger serialized = MainActivity.serialStatusUploadTrigger(delegate);

        assertSerialized(
                delegate,
                () -> serialized.uploadDetectedStatus(session()),
                () -> serialized.uploadDetectedStatus(session()));

        assertEquals(1, delegate.maxConcurrentCalls());
    }

    @Test
    public void persistingAuthGatewayRestoresStoredSessionBeforeDelegateRuns() {
        SessionStore store = new SessionStore();
        AuthSession persisted = session("persisted-access");
        FakeSessionPersistence persistence = new FakeSessionPersistence(persisted);
        StoreBackedAuthGateway delegate = new StoreBackedAuthGateway(store, session("fresh-access"));
        AuthGateway gateway = MainActivity.persistingAuthGateway(delegate, store, persistence);

        ApiResult<AuthSession> result = gateway.ensureSession();

        assertTrue(result.isSuccess());
        assertEquals("persisted-access", result.value().accessToken());
        assertEquals("persisted-access", store.read().accessToken());
    }

    @Test
    public void persistingAuthGatewaySavesSuccessfulSession() {
        SessionStore store = new SessionStore();
        FakeSessionPersistence persistence = new FakeSessionPersistence(null);
        StoreBackedAuthGateway delegate = new StoreBackedAuthGateway(store, session("fresh-access"));
        AuthGateway gateway = MainActivity.persistingAuthGateway(delegate, store, persistence);

        ApiResult<AuthSession> result = gateway.ensureSession();

        assertTrue(result.isSuccess());
        assertEquals("fresh-access", persistence.writtenSession.accessToken());
    }

    private static PartnerStatus pairedPartnerStatus() {
        return new PartnerStatus(
                true,
                new com.bitbond.app.status.StatusModels.PartnerProfile("小禾", "avatar_cat"),
                null,
                Instant.parse("2026-05-26T10:00:00Z"),
                Instant.parse("2026-05-26T10:15:00Z"),
                false,
                "{\"paired\":true,\"partner\":{\"nickname\":\"小禾\",\"avatarId\":\"avatar_cat\"},\"statusCode\":\"music\",\"statusUpdatedAt\":\"2026-05-26T10:00:00Z\",\"expiresAt\":\"2026-05-26T10:15:00Z\",\"isPaused\":false}");
    }

    private static JSONObject json(String rawJson) throws Exception {
        return new JSONObject(rawJson);
    }

    private static void assertOk(JSONObject response) throws Exception {
        assertTrue(response.getBoolean("ok"));
    }

    private static void assertError(JSONObject response, String code) throws Exception {
        assertFalse(response.getBoolean("ok"));
        assertEquals(code, response.getJSONObject("error").getString("code"));
    }

    private static final class Fixtures {
        private final List<String> calls = new ArrayList<>();
        private final FakeAuthGateway auth = new FakeAuthGateway(calls);
        private final FakePairingGateway pairing = new FakePairingGateway(calls);
        private final FakeAvatarGateway avatar = new FakeAvatarGateway(calls);
        private final FakeStatusGateway status = new FakeStatusGateway(calls);
        private final FakeStatusUploadTrigger uploadTrigger = new FakeStatusUploadTrigger(calls);
        private final FakeUsageAccessGateway usageAccess = new FakeUsageAccessGateway(calls);
        private final FakeAccessibilityAccessGateway accessibilityAccess = new FakeAccessibilityAccessGateway(calls);
        private final FakeBatteryOptimizationGateway batteryOptimization = new FakeBatteryOptimizationGateway(calls);
        private final FakeDebugForegroundGateway debugForeground = new FakeDebugForegroundGateway(calls);

        private BitBondBridgeController controller() {
            return new BitBondBridgeController(
                    auth,
                    pairing,
                    avatar,
                    status,
                    uploadTrigger,
                    usageAccess,
                    accessibilityAccess,
                    batteryOptimization,
                    debugForeground);
        }
    }

    private static final class FakeAuthGateway implements AuthGateway {
        private final List<String> calls;
        private ApiResult<AuthSession> result = ApiResult.success(session());

        private FakeAuthGateway(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public ApiResult<AuthSession> ensureSession() {
            calls.add("auth");
            return result;
        }
    }

    private static final class FakePairingGateway implements PairingGateway {
        private final List<String> calls;
        private PairInvite invite = new PairInvite("654321", "2026-05-26T12:00:00Z");
        private CoupleState acceptedCouple = CoupleState.paired("couple-accepted", new PartnerProfile("Partner", null));
        private CoupleState currentCouple = CoupleState.unpaired();
        private ApiResult<CoupleState> currentCoupleResult;
        private String lastAcceptedCode;
        private int acceptCallCount;

        private FakePairingGateway(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public ApiResult<PairInvite> createInvite(AuthSession session) {
            calls.add("createInvite");
            return ApiResult.success(invite);
        }

        @Override
        public ApiResult<CoupleState> acceptInvite(AuthSession session, String code) {
            calls.add("acceptInvite");
            acceptCallCount++;
            lastAcceptedCode = code;
            return ApiResult.success(acceptedCouple);
        }

        @Override
        public ApiResult<CoupleState> getCurrentCouple(AuthSession session) {
            calls.add("getCurrentCouple");
            if (currentCoupleResult != null) {
                return currentCoupleResult;
            }
            return ApiResult.success(currentCouple);
        }

        @Override
        public ApiResult<Boolean> unlink(AuthSession session) {
            calls.add("unlink");
            return ApiResult.success(Boolean.TRUE);
        }
    }

    private static final class FakeAvatarGateway implements AvatarGateway {
        private final List<String> calls;
        private List<AvatarOption> avatars = new ArrayList<>();
        private String selectedAvatarId = "avatar_cat";
        private String lastSelectedAvatarId;
        private int selectCallCount;

        private FakeAvatarGateway(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public ApiResult<List<AvatarOption>> listAvatars(AuthSession session) {
            calls.add("listAvatars");
            return ApiResult.success(avatars);
        }

        @Override
        public ApiResult<String> selectAvatar(AuthSession session, String avatarId) {
            calls.add("selectAvatar");
            selectCallCount++;
            lastSelectedAvatarId = avatarId;
            return ApiResult.success(selectedAvatarId);
        }
    }

    private static final class FakeStatusGateway implements StatusGateway {
        private final List<String> calls;
        private PartnerStatus partnerStatus = new PartnerStatus(false, null, null, null, null, false, "{\"paired\":false}");

        private FakeStatusGateway(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public ApiResult<PartnerStatus> getPartnerStatus(AuthSession session) {
            calls.add("getPartnerStatus");
            return ApiResult.success(partnerStatus);
        }
    }

    private static final class FakeStatusUploadTrigger implements StatusUploadTrigger {
        private final List<String> calls;
        private String code = "skipped";

        private FakeStatusUploadTrigger(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public ApiResult<String> uploadDetectedStatus(AuthSession session) {
            calls.add("uploadDetectedStatus");
            return ApiResult.success(code);
        }
    }

    private static final class FakeUsageAccessGateway implements UsageAccessGateway {
        private final List<String> calls;
        private boolean hasUsageAccess;
        private int openCallCount;

        private FakeUsageAccessGateway(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public boolean hasUsageAccess() {
            calls.add("hasUsageAccess");
            return hasUsageAccess;
        }

        @Override
        public void openUsageAccessSettings() {
            calls.add("openUsageAccessSettings");
            openCallCount++;
        }
    }

    private static final class FakeAccessibilityAccessGateway implements AccessibilityAccessGateway {
        private final List<String> calls;
        private boolean hasAccessibilityAccess;
        private int openCallCount;

        private FakeAccessibilityAccessGateway(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public boolean hasAccessibilityAccess() {
            calls.add("hasAccessibilityAccess");
            return hasAccessibilityAccess;
        }

        @Override
        public void openAccessibilitySettings() {
            calls.add("openAccessibilitySettings");
            openCallCount++;
        }
    }

    private static final class FakeBatteryOptimizationGateway implements BatteryOptimizationGateway {
        private final List<String> calls;
        private boolean ignoringBatteryOptimizations;
        private int openCallCount;

        private FakeBatteryOptimizationGateway(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public boolean isIgnoringBatteryOptimizations() {
            calls.add("isIgnoringBatteryOptimizations");
            return ignoringBatteryOptimizations;
        }

        @Override
        public void openBatteryOptimizationSettings() {
            calls.add("openBatteryOptimizationSettings");
            openCallCount++;
        }
    }

    private static final class FakeDebugForegroundGateway implements DebugForegroundGateway {
        private final List<String> calls;
        private JSONObject result = new JSONObject();

        private FakeDebugForegroundGateway(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public JSONObject debugForegroundApp() {
            calls.add("debugForegroundApp");
            return result;
        }
    }

    private static final class RecordingExecutor implements Executor {
        private final List<String> calls;
        private final Queue<Runnable> tasks = new ArrayDeque<>();
        private int runCount;

        private RecordingExecutor(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public void execute(Runnable command) {
            calls.add("execute");
            tasks.add(command);
        }

        private void runNext() {
            Runnable task = tasks.remove();
            task.run();
            runCount++;
        }
    }

    private static void assertSerialized(BlockingDelegate delegate, Runnable first, Runnable second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch secondAttemptingCall = new CountDownLatch(1);
        try {
            Future<?> firstRun = executor.submit(first);
            assertTrue(delegate.awaitFirstCallEntered());

            Future<?> secondRun = executor.submit(() -> {
                secondAttemptingCall.countDown();
                second.run();
            });
            assertTrue(secondAttemptingCall.await(5, TimeUnit.SECONDS));
            assertEquals(1, delegate.maxConcurrentCalls());
            assertFalse(secondRun.isDone());

            delegate.releaseFirstCall();
            firstRun.get();
            secondRun.get();
        } finally {
            delegate.releaseFirstCall();
            executor.shutdown();
        }
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private interface BlockingDelegate {
        boolean awaitFirstCallEntered() throws InterruptedException;

        void releaseFirstCall();

        int maxConcurrentCalls();
    }

    private abstract static class BlockingCallTracker implements BlockingDelegate {
        private final CountDownLatch firstCallEntered = new CountDownLatch(1);
        private final CountDownLatch releaseFirstCall = new CountDownLatch(1);
        private int activeCalls;
        private int maxConcurrentCalls;
        private boolean firstCall = true;

        protected void enter() {
            synchronized (this) {
                activeCalls++;
                maxConcurrentCalls = Math.max(maxConcurrentCalls, activeCalls);
                if (firstCall) {
                    firstCall = false;
                    firstCallEntered.countDown();
                }
            }

            if (maxConcurrentCalls == 1) {
                await(releaseFirstCall);
            }
        }

        protected synchronized void exit() {
            activeCalls--;
        }

        @Override
        public boolean awaitFirstCallEntered() throws InterruptedException {
            return firstCallEntered.await(5, TimeUnit.SECONDS);
        }

        @Override
        public void releaseFirstCall() {
            releaseFirstCall.countDown();
        }

        @Override
        public synchronized int maxConcurrentCalls() {
            return maxConcurrentCalls;
        }
    }

    private static final class BlockingAuthGateway extends BlockingCallTracker implements AuthGateway {
        @Override
        public ApiResult<AuthSession> ensureSession() {
            enter();
            exit();
            return ApiResult.success(session());
        }
    }

    private static final class BlockingStatusUploadTrigger extends BlockingCallTracker implements StatusUploadTrigger {
        @Override
        public ApiResult<String> uploadDetectedStatus(AuthSession session) {
            enter();
            exit();
            return ApiResult.success("uploaded");
        }
    }

    private static final class StoreBackedAuthGateway implements AuthGateway {
        private final SessionStore store;
        private final AuthSession fallbackSession;

        private StoreBackedAuthGateway(SessionStore store, AuthSession fallbackSession) {
            this.store = store;
            this.fallbackSession = fallbackSession;
        }

        @Override
        public ApiResult<AuthSession> ensureSession() {
            AuthSession cached = store.read();
            if (cached != null) {
                return ApiResult.success(cached);
            }

            store.write(fallbackSession);
            return ApiResult.success(fallbackSession);
        }
    }

    private static final class FakeSessionPersistence implements MainActivity.SessionPersistence {
        private final Map<String, AuthSession> saved = new HashMap<>();
        private AuthSession writtenSession;

        private FakeSessionPersistence(AuthSession session) {
            if (session != null) {
                saved.put("session", session);
            }
        }

        @Override
        public AuthSession read() {
            return saved.get("session");
        }

        @Override
        public void write(AuthSession session) {
            writtenSession = session;
            saved.put("session", session);
        }
    }

    private static AuthSession session() {
        return new AuthSession("access-token", "refresh-token", Long.MAX_VALUE);
    }

    private static AuthSession session(String accessToken) {
        return new AuthSession(accessToken, "refresh-token", Long.MAX_VALUE);
    }
}
