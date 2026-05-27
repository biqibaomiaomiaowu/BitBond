package com.bitbond.app.sharing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.bitbond.app.api.ApiResult;
import com.bitbond.app.api.SupabaseRpcClient;
import com.bitbond.app.auth.AuthSession;
import com.bitbond.app.sharing.SharingModels.SharingState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.json.JSONObject;
import org.junit.Test;

public class SharingRepositoryTest {
    private static final AuthSession SESSION = new AuthSession("access-token", "refresh-token", Long.MAX_VALUE);

    @Test
    public void implementsSharingGateway() {
        SharingRepository repository = new SharingRepository(new FakeRpcClient());

        assertTrue(repository instanceof SharingGateway);
    }

    @Test
    public void pauseSharingCallsRpcWithPausedFlagAndParsesPublicPayload() throws Exception {
        FakeRpcClient rpcClient = new FakeRpcClient()
                .respondWith("set_sharing_paused", new JSONObject()
                        .put("statusCode", "paused")
                        .put("statusUpdatedAt", "2026-05-27T08:00:00Z")
                        .put("expiresAt", "2026-05-27T08:15:00Z")
                        .put("isPaused", true)
                        .put("token", "secret")
                        .put("email", "person@example.test"));
        SharingRepository repository = new SharingRepository(rpcClient);

        ApiResult<SharingState> result = repository.setSharingPaused(SESSION, true);

        assertTrue(result.isSuccess());
        assertEquals("access-token", rpcClient.calls.get(0).accessToken);
        assertEquals("set_sharing_paused", rpcClient.calls.get(0).functionName);
        assertEquals(Set.of("next_is_paused"), objectKeys(rpcClient.calls.get(0).payload));
        assertTrue(rpcClient.calls.get(0).payload.getBoolean("next_is_paused"));
        assertFalse(result.value().sharing());
        assertEquals("paused", result.value().statusCode());
        assertEquals(
                Set.of("sharing", "statusCode", "statusUpdatedAt", "expiresAt", "isPaused"),
                objectKeys(new JSONObject(result.value().rawJson())));
        assertFalse(result.value().rawJson().contains("secret"));
    }

    @Test
    public void getSharingStateCallsRpcWithEmptyPayloadAndParsesPausedState() throws Exception {
        FakeRpcClient rpcClient = new FakeRpcClient()
                .respondWith("get_sharing_state", new JSONObject()
                        .put("sharing", false)
                        .put("statusCode", "paused")
                        .put("statusUpdatedAt", "2026-05-27T08:00:00Z")
                        .put("expiresAt", "2026-05-27T08:15:00Z")
                        .put("isPaused", true)
                        .put("token", "secret"));
        SharingRepository repository = new SharingRepository(rpcClient);

        ApiResult<SharingState> result = repository.getSharingState(SESSION);

        assertTrue(result.isSuccess());
        assertEquals("access-token", rpcClient.calls.get(0).accessToken);
        assertEquals("get_sharing_state", rpcClient.calls.get(0).functionName);
        assertEquals(0, rpcClient.calls.get(0).payload.length());
        assertFalse(result.value().sharing());
        assertEquals("paused", result.value().statusCode());
        assertFalse(result.value().rawJson().contains("secret"));
    }

    @Test
    public void resumeSharingParsesAnyAllowedStatusAndComputesSharingFromIsPaused() throws Exception {
        FakeRpcClient rpcClient = new FakeRpcClient()
                .respondWith("set_sharing_paused", new JSONObject()
                        .put("statusCode", "music")
                        .put("statusUpdatedAt", "2026-05-27T08:00:00Z")
                        .put("expiresAt", "2026-05-27T08:15:00Z")
                        .put("isPaused", false));
        SharingRepository repository = new SharingRepository(rpcClient);

        ApiResult<SharingState> result = repository.setSharingPaused(SESSION, false);

        assertTrue(result.isSuccess());
        assertFalse(rpcClient.calls.get(0).payload.getBoolean("next_is_paused"));
        assertTrue(result.value().sharing());
        assertEquals("music", result.value().statusCode());
    }

    @Test
    public void rejectsUnexpectedSharingStatusFromRpc() throws Exception {
        FakeRpcClient rpcClient = new FakeRpcClient()
                .respondWith("set_sharing_paused", new JSONObject()
                        .put("statusCode", "custom_status")
                        .put("statusUpdatedAt", "2026-05-27T08:00:00Z")
                        .put("expiresAt", "2026-05-27T08:15:00Z")
                        .put("isPaused", false));
        SharingRepository repository = new SharingRepository(rpcClient);

        ApiResult<SharingState> result = repository.setSharingPaused(SESSION, false);

        assertFalse(result.isSuccess());
        assertEquals("sharing_json_error", result.error().code());
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

        @Override
        public ApiResult<JSONObject> rpc(String accessToken, String functionName, JSONObject payload) {
            calls.add(new RpcCall(accessToken, functionName, payload));
            for (Response response : responses) {
                if (response.functionName.equals(functionName)) {
                    return ApiResult.success(response.payload);
                }
            }
            throw new AssertionError("missing fake response for " + functionName);
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
