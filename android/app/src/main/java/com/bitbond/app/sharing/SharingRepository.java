package com.bitbond.app.sharing;

import com.bitbond.app.api.ApiError;
import com.bitbond.app.api.ApiResult;
import com.bitbond.app.api.SupabaseRpcClient;
import com.bitbond.app.auth.AuthSession;
import com.bitbond.app.sharing.SharingModels.SharingState;
import com.bitbond.app.status.StatusMapper;

import java.util.Objects;

import org.json.JSONException;
import org.json.JSONObject;

public final class SharingRepository implements SharingGateway {
    private static final String GET_SHARING_STATE_RPC = "get_sharing_state";
    private static final String SET_SHARING_PAUSED_RPC = "set_sharing_paused";

    private final SupabaseRpcClient rpcClient;

    public SharingRepository(SupabaseRpcClient rpcClient) {
        this.rpcClient = Objects.requireNonNull(rpcClient, "rpcClient");
    }

    @Override
    public ApiResult<SharingState> getSharingState(AuthSession session) {
        Objects.requireNonNull(session, "session");

        try {
            ApiResult<JSONObject> result = rpcClient.rpc(
                    session.accessToken(),
                    GET_SHARING_STATE_RPC,
                    new JSONObject());
            if (!result.isSuccess()) {
                return ApiResult.error(result.error());
            }

            return ApiResult.success(parseSharingState(result.value()));
        } catch (JSONException | IllegalArgumentException exception) {
            return ApiResult.error(new ApiError("sharing_json_error", "Sharing response could not be parsed"));
        }
    }

    @Override
    public ApiResult<SharingState> setSharingPaused(AuthSession session, boolean paused) {
        Objects.requireNonNull(session, "session");

        try {
            ApiResult<JSONObject> result = rpcClient.rpc(
                    session.accessToken(),
                    SET_SHARING_PAUSED_RPC,
                    new JSONObject().put("next_is_paused", paused));
            if (!result.isSuccess()) {
                return ApiResult.error(result.error());
            }

            return ApiResult.success(parseSharingState(result.value()));
        } catch (JSONException | IllegalArgumentException exception) {
            return ApiResult.error(new ApiError("sharing_json_error", "Sharing response could not be parsed"));
        }
    }

    private static SharingState parseSharingState(JSONObject source) throws JSONException {
        String statusCode = source.getString("statusCode").trim();
        if (!StatusMapper.allowedStatusCodes().contains(statusCode)) {
            throw new IllegalArgumentException("Unsupported sharing status: " + statusCode);
        }

        Object statusUpdatedAt = source.has("statusUpdatedAt") && !source.isNull("statusUpdatedAt")
                ? source.getString("statusUpdatedAt").trim()
                : JSONObject.NULL;
        Object expiresAt = source.has("expiresAt") && !source.isNull("expiresAt")
                ? source.getString("expiresAt").trim()
                : JSONObject.NULL;
        boolean isPaused = source.getBoolean("isPaused");
        boolean sharing = source.has("sharing") ? source.getBoolean("sharing") : !isPaused;
        JSONObject publicJson = new JSONObject()
                .put("sharing", sharing)
                .put("statusCode", statusCode)
                .put("statusUpdatedAt", statusUpdatedAt)
                .put("expiresAt", expiresAt)
                .put("isPaused", isPaused);

        return new SharingState(sharing, statusCode, publicJson.toString());
    }
}
