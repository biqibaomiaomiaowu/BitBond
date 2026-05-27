package com.bitbond.app.interaction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.bitbond.app.api.ApiResult;
import com.bitbond.app.api.SupabaseRpcClient;
import com.bitbond.app.auth.AuthSession;
import com.bitbond.app.interaction.InteractionModels.HeartInteraction;
import com.bitbond.app.interaction.InteractionModels.InteractionList;
import com.bitbond.app.interaction.InteractionModels.MarkSeenResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class InteractionRepositoryTest {
    private static final AuthSession SESSION = new AuthSession("access-token", "refresh-token", Long.MAX_VALUE);

    @Test
    public void implementsInteractionGateway() {
        InteractionRepository repository = new InteractionRepository(new FakeRpcClient());

        assertTrue(repository instanceof InteractionGateway);
    }

    @Test
    public void sendHeartCallsRpcWithEmptyPayloadAndSanitizesResult() throws Exception {
        FakeRpcClient rpcClient = new FakeRpcClient()
                .respondWith("send_heart_interaction", new JSONObject()
                        .put("interaction", backendInteractionJson("heart-1")
                                .put("token", "secret-token")
                                .put("email", "person@example.test")));
        InteractionRepository repository = new InteractionRepository(rpcClient);

        ApiResult<HeartInteraction> result = repository.sendHeart(SESSION);

        assertTrue(result.isSuccess());
        assertEquals("send_heart_interaction", rpcClient.calls.get(0).functionName);
        assertEquals(0, rpcClient.calls.get(0).payload.length());
        assertEquals("heart-1", result.value().interactionId());
        assertEquals("heart", result.value().type());
        assertEquals(Set.of("interactionId", "type", "createdAt", "seen"), objectKeys(new JSONObject(result.value().rawJson())));
        assertFalse(result.value().rawJson().contains("secret-token"));
    }

    @Test
    public void getLatestInteractionsReturnsPublicInteractionArray() throws Exception {
        FakeRpcClient rpcClient = new FakeRpcClient()
                .respondWith("get_latest_interactions", new JSONObject()
                        .put("interactions", new JSONArray()
                                .put(backendInteractionJson("heart-1").put("token", "secret"))
                                .put(backendInteractionJson("heart-2").put("phone", "+10000000000"))));
        InteractionRepository repository = new InteractionRepository(rpcClient);

        ApiResult<InteractionList> result = repository.getLatestInteractions(SESSION);

        assertTrue(result.isSuccess());
        assertEquals("get_latest_interactions", rpcClient.calls.get(0).functionName);
        assertEquals(0, rpcClient.calls.get(0).payload.length());
        assertEquals(2, result.value().interactions().size());
        assertEquals("heart-1", result.value().interactions().get(0).interactionId());
        assertFalse(result.value().rawJson().contains("secret"));
        assertFalse(result.value().rawJson().contains("phone"));
    }

    @Test
    public void markInteractionsSeenSendsUuidArrayAndParsesResult() throws Exception {
        FakeRpcClient rpcClient = new FakeRpcClient()
                .respondWith("mark_interactions_seen", new JSONObject()
                        .put("markedCount", 2));
        InteractionRepository repository = new InteractionRepository(rpcClient);

        ApiResult<MarkSeenResult> result = repository.markInteractionsSeen(
                SESSION,
                Arrays.asList(" 00000000-0000-0000-0000-000000000001 ", "00000000-0000-0000-0000-000000000002"));

        assertTrue(result.isSuccess());
        assertEquals("mark_interactions_seen", rpcClient.calls.get(0).functionName);
        assertEquals(Set.of("next_interaction_ids"), objectKeys(rpcClient.calls.get(0).payload));
        assertEquals(
                Arrays.asList("00000000-0000-0000-0000-000000000001", "00000000-0000-0000-0000-000000000002"),
                arrayToList(rpcClient.calls.get(0).payload.getJSONArray("next_interaction_ids")));
        assertEquals(2, result.value().markedCount());
        assertEquals(Set.of("markedCount"), objectKeys(new JSONObject(result.value().rawJson())));
    }

    private static JSONObject backendInteractionJson(String id) throws Exception {
        return new JSONObject()
                .put("id", id)
                .put("type", "heart")
                .put("createdAt", "2026-05-27T08:00:00Z");
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
