package com.bitbond.app.avatar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.bitbond.app.api.ApiError;
import com.bitbond.app.api.ApiResult;
import com.bitbond.app.api.SupabaseRpcClient;
import com.bitbond.app.auth.AuthSession;
import com.bitbond.app.avatar.AvatarModels.AvatarOption;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class AvatarRepositoryTest {
    @Test
    public void implementsAvatarGateway() {
        AvatarRepository repo = new AvatarRepository(new FakeRpcClient());

        assertTrue(repo instanceof AvatarGateway);
    }

    @Test
    public void appendixRequiredRpcContract() throws Exception {
        FakeRpcClient rpc = new FakeRpcClient();
        rpc.enqueue(ApiResult.success(new JSONObject("{\"avatars\":[{\"id\":\"cat\",\"name\":\"小猫\",\"assetKey\":\"avatars/cat\"}]}")));
        AvatarRepository repo = new AvatarRepository(rpc);
        assertEquals(1, repo.listAvatars(session()).data().size());
        rpc.enqueue(ApiResult.success(new JSONObject("{\"avatarId\":\"cat\"}")));
        assertEquals("cat", repo.selectAvatar(session(), "cat").data());
        assertEquals("set_my_avatar", rpc.lastFunction);
        assertEquals("cat", rpc.lastPayload.getString("next_avatar_id"));
    }

    @Test
    public void listAvatarsCallsListAvatarsRpcWithSessionToken() throws Exception {
        FakeRpcClient rpc = new FakeRpcClient();
        rpc.enqueue(ApiResult.success(new JSONObject("{\"avatars\":[]}")));
        AvatarRepository repo = new AvatarRepository(rpc);

        ApiResult<List<AvatarOption>> result = repo.listAvatars(session());

        assertTrue(result.isSuccess());
        assertEquals("access-token", rpc.lastAccessToken);
        assertEquals("list_avatars", rpc.lastFunction);
        assertEquals(0, rpc.lastPayload.length());
    }

    @Test
    public void listAvatarsParsesEightAvatarRows() throws Exception {
        FakeRpcClient rpc = new FakeRpcClient();
        rpc.enqueue(ApiResult.success(new JSONObject().put("avatars", new JSONArray()
                .put(avatar("cat", "小猫", "avatars/cat"))
                .put(avatar("dog", "小狗", "avatars/dog"))
                .put(avatar("rabbit", "小兔", "avatars/rabbit"))
                .put(avatar("bear", "小熊", "avatars/bear"))
                .put(avatar("fox", "小狐", "avatars/fox"))
                .put(avatar("panda", "熊猫", "avatars/panda"))
                .put(avatar("penguin", "企鹅", "avatars/penguin"))
                .put(avatar("duck", "小鸭", "avatars/duck")))));
        AvatarRepository repo = new AvatarRepository(rpc);

        ApiResult<List<AvatarOption>> result = repo.listAvatars(session());

        assertTrue(result.isSuccess());
        assertEquals(8, result.data().size());
        assertEquals("cat", result.data().get(0).id());
        assertEquals("小猫", result.data().get(0).name());
        assertEquals("avatars/cat", result.data().get(0).assetKey());
        assertEquals("duck", result.data().get(7).id());
        assertEquals("小鸭", result.data().get(7).name());
        assertEquals("avatars/duck", result.data().get(7).assetKey());
    }

    @Test
    public void blankAvatarIdReturnsErrorWithoutRpcCall() {
        FakeRpcClient rpc = new FakeRpcClient();
        AvatarRepository repo = new AvatarRepository(rpc);

        ApiResult<String> result = repo.selectAvatar(session(), "   ");

        assertFalse(result.isSuccess());
        assertEquals("invalid_avatar_id", result.error().code());
        assertEquals(0, rpc.callCount);
    }

    private static AuthSession session() {
        return new AuthSession("access-token", "refresh-token", Long.MAX_VALUE);
    }

    private static JSONObject avatar(String id, String name, String assetKey) throws Exception {
        return new JSONObject()
                .put("id", id)
                .put("name", name)
                .put("assetKey", assetKey);
    }

    private static final class FakeRpcClient implements SupabaseRpcClient {
        private final Queue<ApiResult<JSONObject>> responses = new ArrayDeque<>();
        private int callCount;
        private String lastAccessToken;
        private String lastFunction;
        private JSONObject lastPayload;

        private void enqueue(ApiResult<JSONObject> result) {
            responses.add(result);
        }

        @Override
        public ApiResult<JSONObject> rpc(String accessToken, String functionName, JSONObject payload) {
            callCount++;
            lastAccessToken = accessToken;
            lastFunction = functionName;
            lastPayload = payload;
            if (responses.isEmpty()) {
                return ApiResult.error(new ApiError("missing_fake_response", "Fake RPC response was not enqueued"));
            }
            return responses.remove();
        }
    }
}
