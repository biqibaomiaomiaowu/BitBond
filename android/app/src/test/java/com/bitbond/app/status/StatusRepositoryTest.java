package com.bitbond.app.status;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.bitbond.app.api.ApiResult;
import com.bitbond.app.api.SupabaseRpcClient;
import com.bitbond.app.auth.AuthSession;
import com.bitbond.app.status.StatusModels.CurrentStatusResult;
import com.bitbond.app.status.StatusModels.PartnerStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.json.JSONObject;
import org.junit.Test;

public class StatusRepositoryTest {
    private static final AuthSession SESSION = new AuthSession("access-token", "refresh-token", Long.MAX_VALUE);

    @Test
    public void implementsUploaderAndGateway() {
        StatusRepository repository = new StatusRepository(new FakeRpcClient());

        assertTrue(repository instanceof StatusUploader);
        assertTrue(repository instanceof StatusGateway);
    }

    @Test
    public void uploadCurrentStatusCallsUpsertRpcWithPublicPayloadAndParsesResult() throws Exception {
        FakeRpcClient rpcClient = new FakeRpcClient()
                .respondWith("upsert_current_status", new JSONObject()
                        .put("statusCode", "music")
                        .put("statusUpdatedAt", "2026-05-25T08:30:00Z")
                        .put("expiresAt", "2026-05-25T08:45:00Z"));
        StatusRepository repository = new StatusRepository(rpcClient);

        ApiResult<CurrentStatusResult> result = repository.uploadCurrentStatus(
                SESSION,
                "music",
                Instant.parse("2026-05-25T08:30:00Z"));

        assertTrue(result.isSuccess());
        assertEquals("access-token", rpcClient.calls.get(0).accessToken);
        assertEquals("upsert_current_status", rpcClient.calls.get(0).functionName);
        assertEquals(Set.of("next_status_code", "next_status_updated_at"), objectKeys(rpcClient.calls.get(0).payload));
        assertEquals("music", rpcClient.calls.get(0).payload.getString("next_status_code"));
        assertEquals("2026-05-25T08:30:00Z", rpcClient.calls.get(0).payload.getString("next_status_updated_at"));
        assertEquals("music", result.value().statusCode());
        assertEquals(Instant.parse("2026-05-25T08:30:00Z"), result.value().statusUpdatedAt());
        assertEquals(Instant.parse("2026-05-25T08:45:00Z"), result.value().expiresAt());
    }

    @Test
    public void uploadCurrentStatusRejectsBlankStatusCodeWithoutRpc() {
        FakeRpcClient rpcClient = new FakeRpcClient();
        StatusRepository repository = new StatusRepository(rpcClient);

        ApiResult<CurrentStatusResult> result = repository.uploadCurrentStatus(
                SESSION,
                " ",
                Instant.parse("2026-05-25T08:30:00Z"));

        assertFalse(result.isSuccess());
        assertEquals("invalid_status_code", result.error().code());
        assertEquals(0, rpcClient.calls.size());
    }

    @Test
    public void uploadCurrentStatusRejectsUnknownStatusCodeWithoutRpc() {
        FakeRpcClient rpcClient = new FakeRpcClient();
        StatusRepository repository = new StatusRepository(rpcClient);

        ApiResult<CurrentStatusResult> result = repository.uploadCurrentStatus(
                SESSION,
                "shopping",
                Instant.parse("2026-05-25T08:30:00Z"));

        assertFalse(result.isSuccess());
        assertEquals("invalid_status_code", result.error().code());
        assertEquals(0, rpcClient.calls.size());
    }

    @Test
    public void uploadCurrentStatusRejectsOfflineAndPausedWithoutRpc() {
        FakeRpcClient rpcClient = new FakeRpcClient();
        StatusRepository repository = new StatusRepository(rpcClient);

        ApiResult<CurrentStatusResult> offline = repository.uploadCurrentStatus(
                SESSION,
                "offline",
                Instant.parse("2026-05-25T08:30:00Z"));
        ApiResult<CurrentStatusResult> paused = repository.uploadCurrentStatus(
                SESSION,
                "paused",
                Instant.parse("2026-05-25T08:30:00Z"));

        assertFalse(offline.isSuccess());
        assertFalse(paused.isSuccess());
        assertEquals("invalid_status_code", offline.error().code());
        assertEquals("invalid_status_code", paused.error().code());
        assertEquals(0, rpcClient.calls.size());
    }

    @Test
    public void getPartnerStatusCallsRpcWithEmptyPayloadAndParsesActivePartner() throws Exception {
        FakeRpcClient rpcClient = new FakeRpcClient()
                .respondWith("get_partner_status", activePartnerResponse());
        StatusRepository repository = new StatusRepository(rpcClient);

        ApiResult<PartnerStatus> result = repository.getPartnerStatus(SESSION);

        assertTrue(result.isSuccess());
        assertEquals("access-token", rpcClient.calls.get(0).accessToken);
        assertEquals("get_partner_status", rpcClient.calls.get(0).functionName);
        assertEquals(0, rpcClient.calls.get(0).payload.length());
        assertTrue(result.value().isPaired());
        assertEquals("partner-active", result.value().partner().nickname());
        assertEquals("fox", result.value().partner().avatarId());
        assertEquals("music", result.value().statusCode());
        assertEquals(Instant.parse("2026-05-25T08:30:00Z"), result.value().statusUpdatedAt());
        assertEquals(Instant.parse("2026-05-25T08:45:00Z"), result.value().expiresAt());
        assertFalse(result.value().isPaused());
    }

    @Test
    public void getPartnerStatusParsesOfflinePartner() throws Exception {
        FakeRpcClient rpcClient = new FakeRpcClient()
                .respondWith("get_partner_status", pairedPartnerResponse("partner-offline", "dog", "offline", false));
        StatusRepository repository = new StatusRepository(rpcClient);

        ApiResult<PartnerStatus> result = repository.getPartnerStatus(SESSION);

        assertTrue(result.isSuccess());
        assertTrue(result.value().isPaired());
        assertEquals("partner-offline", result.value().partner().nickname());
        assertEquals("dog", result.value().partner().avatarId());
        assertEquals("offline", result.value().statusCode());
        assertNull(result.value().statusUpdatedAt());
        assertNull(result.value().expiresAt());
        assertFalse(result.value().isPaused());
    }

    @Test
    public void getPartnerStatusParsesPausedPartner() throws Exception {
        FakeRpcClient rpcClient = new FakeRpcClient()
                .respondWith("get_partner_status", pairedPartnerResponse("partner-paused", "rabbit", "paused", true));
        StatusRepository repository = new StatusRepository(rpcClient);

        ApiResult<PartnerStatus> result = repository.getPartnerStatus(SESSION);

        assertTrue(result.isSuccess());
        assertTrue(result.value().isPaired());
        assertEquals("partner-paused", result.value().partner().nickname());
        assertEquals("rabbit", result.value().partner().avatarId());
        assertEquals("paused", result.value().statusCode());
        assertNull(result.value().statusUpdatedAt());
        assertNull(result.value().expiresAt());
        assertTrue(result.value().isPaused());
    }

    @Test
    public void getPartnerStatusParsesUnpairedPartner() throws Exception {
        FakeRpcClient rpcClient = new FakeRpcClient()
                .respondWith("get_partner_status", new JSONObject().put("paired", false));
        StatusRepository repository = new StatusRepository(rpcClient);

        ApiResult<PartnerStatus> result = repository.getPartnerStatus(SESSION);

        assertTrue(result.isSuccess());
        assertFalse(result.value().isPaired());
        assertNull(result.value().partner());
        assertNull(result.value().statusCode());
        assertNull(result.value().statusUpdatedAt());
        assertNull(result.value().expiresAt());
        assertFalse(result.value().isPaused());
        assertEquals(Set.of("paired"), objectKeys(new JSONObject(result.value().rawJson())));
    }

    @Test
    public void activePartnerStatusExcludesPrivateFieldsViaRawJson() throws Exception {
        FakeRpcClient rpcClient = new FakeRpcClient()
                .respondWith("get_partner_status", activePartnerResponse()
                        .put("packageName", "com.spotify.music")
                        .put("package", "com.spotify.music")
                        .put("app", "Spotify")
                        .put("duration", 120)
                        .put("content", "track-title")
                        .put("chat", "message")
                        .put("history", new JSONObject().put("statusCode", "reading"))
                        .put("auth", "secret")
                        .put("id", "private-id")
                        .put("email", "person@example.test")
                        .put("phone", "+10000000000")
                        .put("token", "secret-token"));
        rpcClient.responseFor("get_partner_status")
                .getJSONObject("partner")
                .put("packageName", "com.tencent.mm")
                .put("id", "partner-user-id")
                .put("email", "partner@example.test");
        StatusRepository repository = new StatusRepository(rpcClient);

        ApiResult<PartnerStatus> result = repository.getPartnerStatus(SESSION);

        assertTrue(result.isSuccess());
        assertPublicPartnerStatusJson(result.value().rawJson());
        assertFalse(result.value().rawJson().contains("packageName"));
    }

    @Test
    public void getPartnerStatusRejectsUnknownStatusCodeInResponse() throws Exception {
        FakeRpcClient rpcClient = new FakeRpcClient()
                .respondWith("get_partner_status", pairedPartnerResponse("partner-shopping", "cat", "shopping", false));
        StatusRepository repository = new StatusRepository(rpcClient);

        ApiResult<PartnerStatus> result = repository.getPartnerStatus(SESSION);

        assertFalse(result.isSuccess());
        assertEquals("status_json_error", result.error().code());
    }

    private static JSONObject activePartnerResponse() throws Exception {
        return pairedPartnerResponse("partner-active", "fox", "music", false)
                .put("statusUpdatedAt", "2026-05-25T08:30:00Z")
                .put("expiresAt", "2026-05-25T08:45:00Z");
    }

    private static JSONObject pairedPartnerResponse(
            String nickname,
            String avatarId,
            String statusCode,
            boolean isPaused) throws Exception {
        return new JSONObject()
                .put("paired", true)
                .put("partner", new JSONObject()
                        .put("nickname", nickname)
                        .put("avatarId", avatarId))
                .put("statusCode", statusCode)
                .put("statusUpdatedAt", JSONObject.NULL)
                .put("expiresAt", JSONObject.NULL)
                .put("isPaused", isPaused);
    }

    private static void assertPublicPartnerStatusJson(String rawJson) throws Exception {
        JSONObject json = new JSONObject(rawJson);
        assertEquals(
                Set.of("paired", "partner", "statusCode", "statusUpdatedAt", "expiresAt", "isPaused"),
                objectKeys(json));
        assertEquals(Set.of("nickname", "avatarId"), objectKeys(json.getJSONObject("partner")));
        assertPrivateFieldsExcluded(rawJson);
    }

    private static void assertPrivateFieldsExcluded(String rawJson) {
        List<String> privateFields = List.of(
                "package",
                "packageName",
                "app",
                "duration",
                "content",
                "chat",
                "history",
                "auth",
                "id",
                "email",
                "phone",
                "token");
        for (String privateField : privateFields) {
            assertFalse("rawJson should not expose " + privateField, rawJson.contains(privateField));
        }
    }

    private static Set<String> objectKeys(JSONObject object) {
        Set<String> keys = new HashSet<>();
        Iterator<String> iterator = object.keys();
        while (iterator.hasNext()) {
            keys.add(iterator.next());
        }
        return keys;
    }

    private static final class FakeRpcClient implements SupabaseRpcClient {
        private final List<RpcCall> calls = new ArrayList<>();
        private final List<Response> responses = new ArrayList<>();

        private FakeRpcClient respondWith(String functionName, JSONObject response) {
            responses.add(new Response(functionName, response));
            return this;
        }

        private JSONObject responseFor(String functionName) {
            for (Response response : responses) {
                if (response.functionName.equals(functionName)) {
                    return response.payload;
                }
            }

            throw new AssertionError("missing fake response for " + functionName);
        }

        @Override
        public ApiResult<JSONObject> rpc(String accessToken, String functionName, JSONObject payload) {
            calls.add(new RpcCall(accessToken, functionName, payload));
            return ApiResult.success(responseFor(functionName));
        }
    }

    private static final class Response {
        private final String functionName;
        private final JSONObject payload;

        private Response(String functionName, JSONObject payload) {
            this.functionName = functionName;
            this.payload = payload;
        }
    }

    private static final class RpcCall {
        private final String accessToken;
        private final String functionName;
        private final JSONObject payload;

        private RpcCall(String accessToken, String functionName, JSONObject payload) {
            this.accessToken = accessToken;
            this.functionName = functionName;
            this.payload = payload;
        }
    }
}
