package com.bitbond.app.analytics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.bitbond.app.analytics.AnalyticsModels.AnalyticsEventResult;
import com.bitbond.app.api.ApiResult;
import com.bitbond.app.api.SupabaseRpcClient;
import com.bitbond.app.auth.AuthSession;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.json.JSONObject;
import org.junit.Test;

public class AnalyticsRepositoryTest {
    private static final AuthSession SESSION = new AuthSession("access-token", "refresh-token", Long.MAX_VALUE);

    @Test
    public void implementsAnalyticsGateway() {
        AnalyticsRepository repository = new AnalyticsRepository(new FakeRpcClient());

        assertTrue(repository instanceof AnalyticsGateway);
    }

    @Test
    public void recordEventFiltersPrivatePropertiesBeforeRpc() throws Exception {
        FakeRpcClient rpcClient = new FakeRpcClient()
                .respondWith("record_analytics_event", new JSONObject().put("recorded", true));
        AnalyticsRepository repository = new AnalyticsRepository(rpcClient);
        JSONObject properties = new JSONObject()
                .put("surface", "widget")
                .put("packageName", "com.tencent.mm")
                .put("package_name", "com.tencent.mm")
                .put("packageId", "pkg-private")
                .put("package_id", "pkg-private")
                .put("app_package", "com.tencent.mm")
                .put("appId", "app-private")
                .put("app_id", "app-private")
                .put("appName", "WeChat")
                .put("usageDuration", 120)
                .put("usage_duration", 120)
                .put("content", "private-title")
                .put("chatTarget", "friend")
                .put("chat_target", "friend")
                .put("statusCode", "music")
                .put("status_code", "music")
                .put("partner_status", "music")
                .put("history", "private-history")
                .put("token", "secret")
                .put("email", "person@example.test")
                .put("emailAddress", "person@example.test")
                .put("phone", "+10000000000")
                .put("phoneNumber", "+10000000000")
                .put("nested", new JSONObject()
                        .put("button", "heart")
                        .put("token", "nested-secret")
                        .put("status_code", "music")
                        .put("packageId", "nested-pkg-private")
                        .put("app_id", "nested-app-private")
                        .put("emailAddress", "person@example.test"));

        ApiResult<AnalyticsEventResult> result = repository.recordEvent(
                SESSION,
                " heart_sent ",
                properties);

        assertTrue(result.isSuccess());
        assertEquals("record_analytics_event", rpcClient.calls.get(0).functionName);
        JSONObject payload = rpcClient.calls.get(0).payload;
        assertEquals(Set.of("next_event_name", "next_properties"), objectKeys(payload));
        assertEquals("heart_sent", payload.getString("next_event_name"));
        JSONObject safeProperties = payload.getJSONObject("next_properties");
        assertEquals(Set.of("surface", "nested"), objectKeys(safeProperties));
        assertEquals("widget", safeProperties.getString("surface"));
        assertEquals(Set.of("button"), objectKeys(safeProperties.getJSONObject("nested")));
        assertFalse(payload.toString().contains("secret"));
        assertFalse(payload.toString().contains("person@example.test"));
        assertFalse(payload.toString().contains("+10000000000"));
        assertTrue(result.value().recorded());
    }

    @Test
    public void recordEventRejectsBlankNameWithoutRpc() {
        FakeRpcClient rpcClient = new FakeRpcClient();
        AnalyticsRepository repository = new AnalyticsRepository(rpcClient);

        ApiResult<AnalyticsEventResult> result = repository.recordEvent(SESSION, " ", new JSONObject());

        assertFalse(result.isSuccess());
        assertEquals("invalid_event_name", result.error().code());
        assertEquals(0, rpcClient.calls.size());
    }

    @Test
    public void recordEventRejectsTooDeepPropertiesWithoutRpc() throws Exception {
        FakeRpcClient rpcClient = new FakeRpcClient()
                .respondWith("record_analytics_event", new JSONObject().put("recorded", true));
        AnalyticsRepository repository = new AnalyticsRepository(rpcClient);
        JSONObject properties = new JSONObject()
                .put("level1", new JSONObject()
                        .put("level2", new JSONObject()
                                .put("level3", new JSONObject()
                                        .put("level4", new JSONObject()
                                                .put("value", "too-deep")))));

        ApiResult<AnalyticsEventResult> result = repository.recordEvent(SESSION, "heart_sent", properties);

        assertFalse(result.isSuccess());
        assertEquals("analytics_properties_too_deep", result.error().code());
        assertEquals(0, rpcClient.calls.size());
    }

    @Test
    public void recordEventRejectsBackendDepthFiveBoundaryWithoutRpc() throws Exception {
        FakeRpcClient rpcClient = new FakeRpcClient()
                .respondWith("record_analytics_event", new JSONObject().put("recorded", true));
        AnalyticsRepository repository = new AnalyticsRepository(rpcClient);
        JSONObject properties = new JSONObject()
                .put("level1", new JSONObject()
                        .put("level2", new JSONObject()
                                .put("level3", new JSONObject()
                                        .put("value", "too-deep"))));

        ApiResult<AnalyticsEventResult> result = repository.recordEvent(SESSION, "heart_sent", properties);

        assertFalse(result.isSuccess());
        assertEquals("analytics_properties_too_deep", result.error().code());
        assertEquals(0, rpcClient.calls.size());
    }

    @Test
    public void recordEventRejectsOversizedPropertiesWithoutRpc() throws Exception {
        FakeRpcClient rpcClient = new FakeRpcClient()
                .respondWith("record_analytics_event", new JSONObject().put("recorded", true));
        AnalyticsRepository repository = new AnalyticsRepository(rpcClient);
        JSONObject properties = new JSONObject()
                .put("surface", "widget")
                .put("label", repeated('a', 5000));

        ApiResult<AnalyticsEventResult> result = repository.recordEvent(SESSION, "heart_sent", properties);

        assertFalse(result.isSuccess());
        assertEquals("analytics_properties_too_large", result.error().code());
        assertEquals(0, rpcClient.calls.size());
    }

    private static Set<String> objectKeys(JSONObject object) {
        Set<String> keys = new HashSet<>();
        Iterator<String> iterator = object.keys();
        while (iterator.hasNext()) {
            keys.add(iterator.next());
        }
        return keys;
    }

    private static String repeated(char value, int count) {
        char[] characters = new char[count];
        Arrays.fill(characters, value);
        return new String(characters);
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
