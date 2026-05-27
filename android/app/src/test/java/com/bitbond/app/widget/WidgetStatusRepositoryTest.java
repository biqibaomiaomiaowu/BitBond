package com.bitbond.app.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.bitbond.app.api.ApiResult;
import com.bitbond.app.api.SupabaseRpcClient;
import com.bitbond.app.auth.AuthSession;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;
import org.junit.Test;

public class WidgetStatusRepositoryTest {
    private static final AuthSession SESSION = new AuthSession("access-token", "refresh-token", Long.MAX_VALUE);

    @Test
    public void fetchesWidgetStatusFromRpcAndKeepsOnlyPublicFields() throws Exception {
        FakeRpcClient rpcClient = new FakeRpcClient(new JSONObject()
                .put("paired", true)
                .put("statusCode", "reading")
                .put("statusUpdatedAt", "2026-05-27T08:00:00Z")
                .put("expiresAt", "2026-05-27T08:15:00Z")
                .put("isPaused", false)
                .put("generatedAt", "2026-05-27T08:01:00Z")
                .put("packageName", "com.example.private")
                .put("token", "secret"));
        WidgetStatusRepository repository = new WidgetStatusRepository(rpcClient);

        ApiResult<WidgetStatusSnapshot> result = repository.getWidgetStatus(SESSION);

        assertTrue(result.isSuccess());
        assertEquals("access-token", rpcClient.calls.get(0).accessToken);
        assertEquals("get_widget_status", rpcClient.calls.get(0).functionName);
        assertEquals(0, rpcClient.calls.get(0).payload.length());
        assertTrue(result.value().paired());
        assertEquals("reading", result.value().statusCode());
        assertEquals("2026-05-27T08:00:00Z", result.value().updatedAt());
        assertTrue(result.value().sharing());
        assertFalse(result.value().toJson().toString().contains("packageName"));
        assertFalse(result.value().toJson().toString().contains("secret"));
    }

    @Test
    public void parsesPausedWidgetStatusAsNotSharing() throws Exception {
        WidgetStatusRepository repository = new WidgetStatusRepository(new FakeRpcClient(new JSONObject()
                .put("paired", true)
                .put("statusCode", "paused")
                .put("statusUpdatedAt", JSONObject.NULL)
                .put("expiresAt", JSONObject.NULL)
                .put("isPaused", true)
                .put("generatedAt", "2026-05-27T08:01:00Z")));

        ApiResult<WidgetStatusSnapshot> result = repository.getWidgetStatus(SESSION);

        assertTrue(result.isSuccess());
        assertEquals("paused", result.value().statusCode());
        assertFalse(result.value().sharing());
    }

    private static final class FakeRpcClient implements SupabaseRpcClient {
        private final JSONObject response;
        private final List<RpcCall> calls = new ArrayList<>();

        private FakeRpcClient(JSONObject response) {
            this.response = response;
        }

        @Override
        public ApiResult<JSONObject> rpc(String accessToken, String functionName, JSONObject payload) {
            calls.add(new RpcCall(accessToken, functionName, payload));
            return ApiResult.success(response);
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
