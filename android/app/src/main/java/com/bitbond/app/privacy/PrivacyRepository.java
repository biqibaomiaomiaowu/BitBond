package com.bitbond.app.privacy;

import com.bitbond.app.api.ApiError;
import com.bitbond.app.api.ApiResult;
import com.bitbond.app.api.SupabaseRpcClient;
import com.bitbond.app.auth.AuthSession;
import com.bitbond.app.privacy.PrivacyModels.PrivacySettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class PrivacyRepository implements PrivacyGateway {
    private static final String GET_PRIVACY_SETTINGS_RPC = "get_status_privacy_settings";
    private static final String UPDATE_PRIVACY_SETTINGS_RPC = "update_status_privacy_settings";

    private final SupabaseRpcClient rpcClient;

    public PrivacyRepository(SupabaseRpcClient rpcClient) {
        this.rpcClient = Objects.requireNonNull(rpcClient, "rpcClient");
    }

    @Override
    public ApiResult<PrivacySettings> getSettings(AuthSession session) {
        Objects.requireNonNull(session, "session");

        try {
            ApiResult<JSONObject> result = rpcClient.rpc(
                    session.accessToken(),
                    GET_PRIVACY_SETTINGS_RPC,
                    new JSONObject());
            if (!result.isSuccess()) {
                return ApiResult.error(result.error());
            }

            return ApiResult.success(parseSettings(result.value()));
        } catch (JSONException | IllegalArgumentException exception) {
            return ApiResult.error(new ApiError("privacy_json_error", "Privacy response could not be parsed"));
        }
    }

    @Override
    public ApiResult<PrivacySettings> updateSettings(AuthSession session, List<String> allowedStatuses) {
        Objects.requireNonNull(session, "session");

        List<String> safeAllowedStatuses;
        try {
            safeAllowedStatuses = PrivacyModels.sanitizeAllowedStatuses(allowedStatuses);
        } catch (IllegalArgumentException exception) {
            return ApiResult.error(new ApiError("invalid_status_code", "Status code is invalid"));
        }

        try {
            ApiResult<JSONObject> result = rpcClient.rpc(
                    session.accessToken(),
                    UPDATE_PRIVACY_SETTINGS_RPC,
                    new JSONObject().put("next_allowed_statuses", stringArray(safeAllowedStatuses)));
            if (!result.isSuccess()) {
                return ApiResult.error(result.error());
            }

            return ApiResult.success(parseSettings(result.value()));
        } catch (JSONException | IllegalArgumentException exception) {
            return ApiResult.error(new ApiError("privacy_json_error", "Privacy response could not be parsed"));
        }
    }

    private static PrivacySettings parseSettings(JSONObject source) throws JSONException {
        List<String> allowedStatuses = PrivacyModels.sanitizeAllowedStatuses(stringList(source.getJSONArray("allowedStatuses")));
        List<String> availableStatuses = PrivacyModels.activeStatusCodes();
        JSONObject publicJson = new JSONObject()
                .put("allowedStatuses", stringArray(allowedStatuses))
                .put("availableStatuses", stringArray(availableStatuses));
        return new PrivacySettings(allowedStatuses, availableStatuses, publicJson.toString());
    }

    private static List<String> stringList(JSONArray array) throws JSONException {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            result.add(array.getString(i));
        }
        return result;
    }

    private static JSONArray stringArray(List<String> values) {
        JSONArray result = new JSONArray();
        for (String value : values) {
            result.put(value);
        }
        return result;
    }
}
