package com.bitbond.app.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.bitbond.app.account.AccountModels.DeleteAccountResult;
import com.bitbond.app.api.ApiResult;
import com.bitbond.app.api.SupabaseRpcClient;
import com.bitbond.app.auth.AuthSession;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;
import org.junit.Test;

public class AccountRepositoryTest {
    private static final AuthSession SESSION = new AuthSession("access-token", "refresh-token", Long.MAX_VALUE);

    @Test
    public void implementsAccountGateway() {
        AccountRepository repository = new AccountRepository(new FakeRpcClient());

        assertTrue(repository instanceof AccountGateway);
    }

    @Test
    public void deleteAccountCallsRpcWithEmptyPayloadAndParsesPublicResult() throws Exception {
        FakeRpcClient rpcClient = new FakeRpcClient()
                .respondWith("delete_account_data", new JSONObject()
                        .put("deleted", true)
                        .put("token", "secret"));
        AccountRepository repository = new AccountRepository(rpcClient);

        ApiResult<DeleteAccountResult> result = repository.deleteAccount(SESSION);

        assertTrue(result.isSuccess());
        assertEquals("access-token", rpcClient.calls.get(0).accessToken);
        assertEquals("delete_account_data", rpcClient.calls.get(0).functionName);
        assertEquals(0, rpcClient.calls.get(0).payload.length());
        assertTrue(result.value().deleted());
        assertFalse(result.value().rawJson().contains("secret"));
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
