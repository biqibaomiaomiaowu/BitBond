package com.bitbond.app.auth;

import com.bitbond.app.api.ApiError;
import com.bitbond.app.api.ApiResult;
import com.bitbond.app.api.SupabaseRpcClient;
import com.bitbond.app.api.Transport;
import com.bitbond.app.api.TransportResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.json.JSONException;
import org.json.JSONObject;

public final class AuthRepository implements AuthGateway {
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String ENSURE_USER_PROFILE_RPC = "ensure_user_profile";

    private final String baseUrl;
    private final String anonKey;
    private final Transport transport;
    private final SupabaseRpcClient rpcClient;
    private final SessionStore sessionStore;

    public AuthRepository(
            String baseUrl,
            String anonKey,
            Transport transport,
            SupabaseRpcClient rpcClient,
            SessionStore sessionStore) {
        this.baseUrl = stripTrailingSlash(Objects.requireNonNull(baseUrl, "baseUrl"));
        this.anonKey = Objects.requireNonNull(anonKey, "anonKey").trim();
        this.transport = Objects.requireNonNull(transport, "transport");
        this.rpcClient = Objects.requireNonNull(rpcClient, "rpcClient");
        this.sessionStore = Objects.requireNonNull(sessionStore, "sessionStore");
    }

    @Override
    public ApiResult<AuthSession> ensureSession() {
        return ensureSession(System.currentTimeMillis() / 1000L);
    }

    public ApiResult<AuthSession> ensureSession(long nowEpochSeconds) {
        AuthSession cachedSession = sessionStore.read();
        if (cachedSession != null && !cachedSession.isExpired(nowEpochSeconds)) {
            return ApiResult.success(cachedSession);
        }

        ApiResult<AuthSession> sessionResult = null;
        if (cachedSession != null && hasRefreshToken(cachedSession)) {
            sessionResult = refreshSession(cachedSession.refreshToken(), nowEpochSeconds);
            if (!sessionResult.isSuccess() && !isRefreshTokenRejected(sessionResult)) {
                return sessionResult;
            }
        }

        if (sessionResult == null || !sessionResult.isSuccess()) {
            sessionResult = signUpAnonymously(nowEpochSeconds);
            if (!sessionResult.isSuccess()) {
                return sessionResult;
            }
        }

        AuthSession session = sessionResult.value();
        ApiResult<JSONObject> profileResult = rpcClient.rpc(
                session.accessToken(),
                ENSURE_USER_PROFILE_RPC,
                new JSONObject());
        if (!profileResult.isSuccess()) {
            return ApiResult.error(profileResult.error());
        }

        sessionStore.write(session);
        return ApiResult.success(session);
    }

    private ApiResult<AuthSession> refreshSession(String refreshToken, long nowEpochSeconds) {
        String url = baseUrl + "/auth/v1/token?grant_type=refresh_token";

        try {
            TransportResponse response = transport.post(url, authHeaders(), refreshTokenBody(refreshToken));
            if (response.statusCode < 200 || response.statusCode >= 300) {
                return ApiResult.error(new ApiError("supabase_auth_refresh_http_" + response.statusCode, "Auth refresh failed"));
            }

            return ApiResult.success(parseSession(response.body, nowEpochSeconds));
        } catch (IOException exception) {
            return ApiResult.error(new ApiError("supabase_auth_refresh_network_error", "Auth refresh failed"));
        } catch (JSONException | IllegalArgumentException exception) {
            return ApiResult.error(new ApiError("supabase_auth_refresh_json_error", "Auth refresh response could not be parsed"));
        }
    }

    private ApiResult<AuthSession> signUpAnonymously(long nowEpochSeconds) {
        String url = baseUrl + "/auth/v1/signup";

        try {
            TransportResponse response = transport.post(url, authHeaders(), anonymousSignupBody());
            if (response.statusCode < 200 || response.statusCode >= 300) {
                return ApiResult.error(new ApiError("supabase_auth_http_" + response.statusCode, "Anonymous auth failed"));
            }

            return ApiResult.success(parseSession(response.body, nowEpochSeconds));
        } catch (IOException exception) {
            return ApiResult.error(new ApiError("supabase_auth_network_error", "Anonymous auth failed"));
        } catch (JSONException | IllegalArgumentException exception) {
            return ApiResult.error(new ApiError("supabase_auth_json_error", "Auth response could not be parsed"));
        }
    }

    private Map<String, String> authHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("apikey", anonKey);
        headers.put("Authorization", "Bearer " + anonKey);
        headers.put("Content-Type", CONTENT_TYPE_JSON);
        headers.put("Accept", CONTENT_TYPE_JSON);
        return headers;
    }

    private static AuthSession parseSession(String body, long nowEpochSeconds) throws JSONException {
        JSONObject json = new JSONObject(normalize(body));
        String accessToken = json.getString("access_token");
        String refreshToken = json.getString("refresh_token");
        long expiresAtEpochSeconds = expiresAtEpochSeconds(json, nowEpochSeconds);
        return new AuthSession(accessToken, refreshToken, expiresAtEpochSeconds);
    }

    private static long expiresAtEpochSeconds(JSONObject json, long nowEpochSeconds) throws JSONException {
        if (json.has("expires_at") && !json.isNull("expires_at")) {
            return json.getLong("expires_at");
        }

        return nowEpochSeconds + json.getLong("expires_in");
    }

    private static String anonymousSignupBody() throws JSONException {
        return new JSONObject()
                .put("data", new JSONObject())
                .toString();
    }

    private static String refreshTokenBody(String refreshToken) throws JSONException {
        return new JSONObject()
                .put("refresh_token", refreshToken)
                .toString();
    }

    private static boolean hasRefreshToken(AuthSession session) {
        return !normalize(session.refreshToken()).isEmpty();
    }

    private static boolean isRefreshTokenRejected(ApiResult<AuthSession> result) {
        String code = result.error().code();
        return "supabase_auth_refresh_http_400".equals(code)
                || "supabase_auth_refresh_http_401".equals(code);
    }

    private static String stripTrailingSlash(String value) {
        String normalized = normalize(value);
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
