package com.bitbond.app.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.bitbond.app.api.ApiResult;
import com.bitbond.app.api.SupabaseRpcClient;
import com.bitbond.app.api.Transport;
import com.bitbond.app.api.TransportResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

public class AuthRepositoryTest {
    private static final long NOW_EPOCH_SECONDS = 1_800_000_000L;

    @Test
    public void implementsAuthGateway() {
        AuthRepository repository = newRepository(new RecordingTransport(200, authResponse("access", "refresh", NOW_EPOCH_SECONDS + 60L)));

        assertTrue(repository instanceof AuthGateway);
    }

    @Test
    public void reusesUnexpiredSessionAndSkipsNetwork() {
        SessionStore store = new SessionStore();
        AuthSession cachedSession = new AuthSession("cached-access", "cached-refresh", NOW_EPOCH_SECONDS + 60L);
        store.write(cachedSession);
        RecordingTransport transport = new RecordingTransport(200, authResponse("new-access", "new-refresh", NOW_EPOCH_SECONDS + 600L));
        FakeRpcClient rpcClient = new FakeRpcClient();
        AuthRepository repository = new AuthRepository(
                "https://api.example.test/",
                "anon-key",
                transport,
                rpcClient,
                store);

        ApiResult<AuthSession> result = repository.ensureSession(NOW_EPOCH_SECONDS);

        assertTrue(result.isSuccess());
        assertSame(cachedSession, result.value());
        assertEquals(0, transport.callCount);
        assertEquals(0, rpcClient.calls.size());
    }

    @Test
    public void authGatewayEnsureSessionReusesFarFutureCachedSession() {
        SessionStore store = new SessionStore();
        AuthSession cachedSession = new AuthSession("cached-access", "cached-refresh", Long.MAX_VALUE);
        store.write(cachedSession);
        RecordingTransport transport = new RecordingTransport(500, "{}");
        AuthGateway gateway = new AuthRepository(
                "https://api.example.test",
                "anon-key",
                transport,
                new FakeRpcClient(),
                store);

        ApiResult<AuthSession> result = gateway.ensureSession();

        assertTrue(result.isSuccess());
        assertSame(cachedSession, result.data());
        assertEquals(0, transport.callCount);
    }

    @Test
    public void missingSessionSignsUpAnonymouslySavesSessionAndEnsuresUserProfile() throws Exception {
        SessionStore store = new SessionStore();
        RecordingTransport transport = new RecordingTransport(200, authResponse("access-1", "refresh-1", NOW_EPOCH_SECONDS + 3600L));
        FakeRpcClient rpcClient = new FakeRpcClient();
        AuthRepository repository = new AuthRepository(
                "https://api.example.test/",
                "anon-key",
                transport,
                rpcClient,
                store);

        ApiResult<AuthSession> result = repository.ensureSession(NOW_EPOCH_SECONDS);

        assertTrue(result.isSuccess());
        assertEquals("https://api.example.test/auth/v1/signup", transport.url);
        assertEquals("anon-key", transport.headers.get("apikey"));
        assertEquals("Bearer anon-key", transport.headers.get("Authorization"));
        assertEquals("application/json", transport.headers.get("Content-Type"));
        assertEquals("application/json", transport.headers.get("Accept"));
        assertEquals(0, new JSONObject(transport.body).getJSONObject("data").length());
        assertEquals("access-1", result.value().accessToken());
        assertEquals("refresh-1", result.value().refreshToken());
        assertEquals(NOW_EPOCH_SECONDS + 3600L, result.value().expiresAtEpochSeconds());
        assertEquals("access-1", store.read().accessToken());
        assertEquals(1, rpcClient.calls.size());
        assertEquals("access-1", rpcClient.calls.get(0).accessToken);
        assertEquals("ensure_user_profile", rpcClient.calls.get(0).functionName);
        assertEquals(0, rpcClient.calls.get(0).payload.length());
    }

    @Test
    public void expiredSessionSignsUpAnonymouslyInsteadOfReusingCachedToken() {
        SessionStore store = new SessionStore();
        store.write(new AuthSession("expired-access", "expired-refresh", NOW_EPOCH_SECONDS));
        RecordingTransport transport = new RecordingTransport(200, authResponse("fresh-access", "fresh-refresh", NOW_EPOCH_SECONDS + 3600L));
        FakeRpcClient rpcClient = new FakeRpcClient();
        AuthRepository repository = new AuthRepository(
                "https://api.example.test",
                "anon-key",
                transport,
                rpcClient,
                store);

        ApiResult<AuthSession> result = repository.ensureSession(NOW_EPOCH_SECONDS);

        assertTrue(result.isSuccess());
        assertEquals(1, transport.callCount);
        assertEquals("fresh-access", result.value().accessToken());
        assertEquals("fresh-access", store.read().accessToken());
        assertEquals(1, rpcClient.calls.size());
    }

    private static AuthRepository newRepository(RecordingTransport transport) {
        return new AuthRepository(
                "https://api.example.test",
                "anon-key",
                transport,
                new FakeRpcClient(),
                new SessionStore());
    }

    private static String authResponse(String accessToken, String refreshToken, long expiresAtEpochSeconds) {
        try {
            return new JSONObject()
                    .put("access_token", accessToken)
                    .put("refresh_token", refreshToken)
                    .put("expires_at", expiresAtEpochSeconds)
                    .toString();
        } catch (JSONException exception) {
            throw new AssertionError("failed to build auth response fixture", exception);
        }
    }

    private static final class RecordingTransport implements Transport {
        private final int statusCode;
        private final String responseBody;
        private int callCount;
        private String url;
        private Map<String, String> headers;
        private String body;

        private RecordingTransport(int statusCode, String responseBody) {
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }

        @Override
        public TransportResponse post(String url, Map<String, String> headers, String body) throws IOException {
            callCount++;
            this.url = url;
            this.headers = headers;
            this.body = body;
            return new TransportResponse(statusCode, responseBody);
        }
    }

    private static final class FakeRpcClient implements SupabaseRpcClient {
        private final List<RpcCall> calls = new ArrayList<>();

        @Override
        public ApiResult<JSONObject> rpc(String accessToken, String functionName, JSONObject payload) {
            calls.add(new RpcCall(accessToken, functionName, payload));
            return ApiResult.success(new JSONObject());
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
