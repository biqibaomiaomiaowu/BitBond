package com.bitbond.app.pairing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.bitbond.app.api.ApiError;
import com.bitbond.app.api.ApiResult;
import com.bitbond.app.api.SupabaseRpcClient;
import com.bitbond.app.auth.AuthSession;
import com.bitbond.app.pairing.PairingModels.CoupleState;
import com.bitbond.app.pairing.PairingModels.PairInvite;

import java.util.ArrayDeque;
import java.util.Queue;

import org.json.JSONObject;
import org.junit.Test;

public class PairingRepositoryTest {
    private static final AuthSession SESSION = new AuthSession("access-token", "refresh-token", Long.MAX_VALUE);

    @Test
    public void implementsPairingGateway() {
        PairingRepository repository = new PairingRepository(new FakeRpcClient());

        assertTrue(repository instanceof PairingGateway);
    }

    @Test
    public void createInviteCallsRpcWithEmptyPayloadAndParsesInvite() throws Exception {
        FakeRpcClient rpcClient = new FakeRpcClient();
        rpcClient.enqueue(ApiResult.success(new JSONObject()
                .put("code", "123456")
                .put("expiresAt", "2026-05-25T15:10:00Z")));
        PairingRepository repository = new PairingRepository(rpcClient);

        ApiResult<PairInvite> result = repository.createInvite(SESSION);

        assertTrue(result.isSuccess());
        assertEquals("access-token", rpcClient.lastToken);
        assertEquals("create_pair_invite", rpcClient.lastFunction);
        assertEquals(0, rpcClient.lastPayload.length());
        assertEquals("123456", result.value().code());
        assertEquals("2026-05-25T15:10:00Z", result.value().expiresAt());
    }

    @Test
    public void acceptInviteCallsRpcWithInviteCodePayloadAndParsesCouple() throws Exception {
        FakeRpcClient rpcClient = new FakeRpcClient();
        rpcClient.enqueue(ApiResult.success(couplePayload("couple-1", "小禾", "avatar_cat")));
        PairingRepository repository = new PairingRepository(rpcClient);

        ApiResult<CoupleState> result = repository.acceptInvite(SESSION, " 123456 ");

        assertTrue(result.isSuccess());
        assertEquals("access-token", rpcClient.lastToken);
        assertEquals("accept_pair_invite", rpcClient.lastFunction);
        assertEquals("123456", rpcClient.lastPayload.getString("invite_code"));
        assertEquals(1, rpcClient.lastPayload.length());
        assertTrue(result.value().paired());
        assertEquals("couple-1", result.value().coupleId());
        assertEquals("小禾", result.value().partner().nickname());
        assertEquals("avatar_cat", result.value().partner().avatarId());
    }

    @Test
    public void blankAcceptInviteSkipsRpcAndReturnsError() {
        FakeRpcClient rpcClient = new FakeRpcClient();
        PairingRepository repository = new PairingRepository(rpcClient);

        ApiResult<CoupleState> result = repository.acceptInvite(SESSION, "   ");

        assertFalse(result.isSuccess());
        assertEquals("invalid_invite_code", result.error().code());
        assertEquals(0, rpcClient.callCount);
    }

    @Test
    public void getCurrentCoupleCallsRpcWithEmptyPayloadAndParsesPairedState() throws Exception {
        FakeRpcClient rpcClient = new FakeRpcClient();
        rpcClient.enqueue(ApiResult.success(new JSONObject()
                .put("paired", true)
                .put("coupleId", "couple-2")
                .put("partner", new JSONObject()
                        .put("nickname", "阿树")
                        .put("avatarId", "avatar_fox"))));
        PairingRepository repository = new PairingRepository(rpcClient);

        ApiResult<CoupleState> result = repository.getCurrentCouple(SESSION);

        assertTrue(result.isSuccess());
        assertEquals("access-token", rpcClient.lastToken);
        assertEquals("get_current_couple", rpcClient.lastFunction);
        assertEquals(0, rpcClient.lastPayload.length());
        assertTrue(result.value().paired());
        assertEquals("couple-2", result.value().coupleId());
        assertEquals("阿树", result.value().partner().nickname());
        assertEquals("avatar_fox", result.value().partner().avatarId());
    }

    @Test
    public void getCurrentCoupleParsesUnpairedState() throws Exception {
        FakeRpcClient rpcClient = new FakeRpcClient();
        rpcClient.enqueue(ApiResult.success(new JSONObject().put("paired", false)));
        PairingRepository repository = new PairingRepository(rpcClient);

        ApiResult<CoupleState> result = repository.getCurrentCouple(SESSION);

        assertTrue(result.isSuccess());
        assertFalse(result.value().paired());
        assertNull(result.value().coupleId());
        assertNull(result.value().partner());
    }

    @Test
    public void unlinkCallsRpcWithEmptyPayloadAndParsesUnlinkedFlag() throws Exception {
        FakeRpcClient rpcClient = new FakeRpcClient();
        rpcClient.enqueue(ApiResult.success(new JSONObject().put("unlinked", true)));
        PairingRepository repository = new PairingRepository(rpcClient);

        ApiResult<Boolean> result = repository.unlink(SESSION);

        assertTrue(result.isSuccess());
        assertEquals("access-token", rpcClient.lastToken);
        assertEquals("unlink_current_couple", rpcClient.lastFunction);
        assertEquals(0, rpcClient.lastPayload.length());
        assertEquals(Boolean.TRUE, result.value());
    }

    @Test
    public void rpcErrorIsReturnedWithoutParsing() {
        FakeRpcClient rpcClient = new FakeRpcClient();
        rpcClient.enqueue(ApiResult.error(new ApiError("already_paired", "Already paired")));
        PairingRepository repository = new PairingRepository(rpcClient);

        ApiResult<PairInvite> result = repository.createInvite(SESSION);

        assertFalse(result.isSuccess());
        assertEquals("already_paired", result.error().code());
    }

    private static JSONObject couplePayload(String coupleId, String nickname, String avatarId) throws Exception {
        return new JSONObject()
                .put("coupleId", coupleId)
                .put("partner", new JSONObject()
                        .put("nickname", nickname)
                        .put("avatarId", avatarId));
    }

    final class FakeRpcClient implements SupabaseRpcClient {
        private final Queue<ApiResult<JSONObject>> results = new ArrayDeque<>();
        String lastToken;
        String lastFunction;
        JSONObject lastPayload;
        int callCount;

        void enqueue(ApiResult<JSONObject> result) {
            results.add(result);
        }

        @Override
        public ApiResult<JSONObject> rpc(String accessToken, String functionName, JSONObject payload) {
            callCount++;
            lastToken = accessToken;
            lastFunction = functionName;
            lastPayload = payload;
            if (results.isEmpty()) {
                return ApiResult.success(new JSONObject());
            }
            return results.remove();
        }
    }
}
