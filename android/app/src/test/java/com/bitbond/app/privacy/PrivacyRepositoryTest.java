package com.bitbond.app.privacy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.bitbond.app.api.ApiResult;
import com.bitbond.app.api.SupabaseRpcClient;
import com.bitbond.app.auth.AuthSession;
import com.bitbond.app.privacy.PrivacyModels.PrivacySettings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class PrivacyRepositoryTest {
    private static final AuthSession SESSION = new AuthSession("access-token", "refresh-token", Long.MAX_VALUE);

    @Test
    public void implementsPrivacyGateway() {
        PrivacyRepository repository = new PrivacyRepository(new FakeRpcClient());

        assertTrue(repository instanceof PrivacyGateway);
    }

    @Test
    public void getSettingsCallsRpcAndExposesOnlyActiveStatusToggles() throws Exception {
        FakeRpcClient rpcClient = new FakeRpcClient()
                .respondWith("get_status_privacy_settings", new JSONObject()
                        .put("allowedStatuses", new JSONArray()
                                .put("music")
                                .put("paused")
                                .put("offline")
                                .put("short_video"))
                        .put("token", "secret-token"));
        PrivacyRepository repository = new PrivacyRepository(rpcClient);

        ApiResult<PrivacySettings> result = repository.getSettings(SESSION);

        assertTrue(result.isSuccess());
        assertEquals("get_status_privacy_settings", rpcClient.calls.get(0).functionName);
        assertEquals(0, rpcClient.calls.get(0).payload.length());
        assertEquals(Arrays.asList("music", "short_video"), result.value().allowedStatuses());
        assertFalse(result.value().availableStatuses().contains("paused"));
        assertFalse(result.value().availableStatuses().contains("offline"));
        assertFalse(result.value().rawJson().contains("paused"));
        assertFalse(result.value().rawJson().contains("offline"));
        assertFalse(result.value().rawJson().contains("secret-token"));
    }

    @Test
    public void updateSettingsFiltersPausedAndOfflineBeforeRpc() throws Exception {
        FakeRpcClient rpcClient = new FakeRpcClient()
                .respondWith("update_status_privacy_settings", new JSONObject()
                        .put("allowedStatuses", new JSONArray()
                                .put("music")
                                .put("short_video")));
        PrivacyRepository repository = new PrivacyRepository(rpcClient);

        ApiResult<PrivacySettings> result = repository.updateSettings(
                SESSION,
                Arrays.asList("music", "paused", "offline", "short_video", "music"));

        assertTrue(result.isSuccess());
        assertEquals("update_status_privacy_settings", rpcClient.calls.get(0).functionName);
        assertEquals(Set.of("next_allowed_statuses"), objectKeys(rpcClient.calls.get(0).payload));
        assertEquals(
                Arrays.asList("music", "short_video"),
                arrayToList(rpcClient.calls.get(0).payload.getJSONArray("next_allowed_statuses")));
        assertEquals(Arrays.asList("music", "short_video"), result.value().allowedStatuses());
    }

    @Test
    public void updateSettingsRejectsUnknownStatusWithoutRpc() {
        FakeRpcClient rpcClient = new FakeRpcClient();
        PrivacyRepository repository = new PrivacyRepository(rpcClient);

        ApiResult<PrivacySettings> result = repository.updateSettings(
                SESSION,
                Arrays.asList("music", "shopping"));

        assertFalse(result.isSuccess());
        assertEquals("invalid_status_code", result.error().code());
        assertEquals(0, rpcClient.calls.size());
    }

    private static List<String> arrayToList(JSONArray array) throws Exception {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            result.add(array.getString(i));
        }
        return result;
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
