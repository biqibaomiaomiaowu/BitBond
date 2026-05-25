package com.bitbond.app.avatar;

import com.bitbond.app.api.ApiError;
import com.bitbond.app.api.ApiResult;
import com.bitbond.app.api.SupabaseRpcClient;
import com.bitbond.app.auth.AuthSession;
import com.bitbond.app.avatar.AvatarModels.AvatarOption;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class AvatarRepository implements AvatarGateway {
    private static final String LIST_AVATARS_RPC = "list_avatars";
    private static final String SET_MY_AVATAR_RPC = "set_my_avatar";

    private final SupabaseRpcClient rpcClient;

    public AvatarRepository(SupabaseRpcClient rpcClient) {
        this.rpcClient = Objects.requireNonNull(rpcClient, "rpcClient");
    }

    @Override
    public ApiResult<List<AvatarOption>> listAvatars(AuthSession session) {
        Objects.requireNonNull(session, "session");

        ApiResult<JSONObject> result = rpcClient.rpc(
                session.accessToken(),
                LIST_AVATARS_RPC,
                new JSONObject());
        if (!result.isSuccess()) {
            return ApiResult.error(result.error());
        }

        try {
            return ApiResult.success(parseAvatars(result.data()));
        } catch (JSONException | IllegalArgumentException exception) {
            return ApiResult.error(new ApiError("avatar_json_error", "Avatar response could not be parsed"));
        }
    }

    @Override
    public ApiResult<String> selectAvatar(AuthSession session, String avatarId) {
        Objects.requireNonNull(session, "session");

        String normalizedAvatarId = normalize(avatarId);
        if (normalizedAvatarId.isEmpty()) {
            return ApiResult.error(new ApiError("invalid_avatar_id", "Avatar id is required"));
        }

        try {
            ApiResult<JSONObject> result = rpcClient.rpc(
                    session.accessToken(),
                    SET_MY_AVATAR_RPC,
                    new JSONObject().put("next_avatar_id", normalizedAvatarId));
            if (!result.isSuccess()) {
                return ApiResult.error(result.error());
            }

            return ApiResult.success(result.data().getString("avatarId"));
        } catch (JSONException exception) {
            return ApiResult.error(new ApiError("avatar_json_error", "Avatar response could not be parsed"));
        }
    }

    private static List<AvatarOption> parseAvatars(JSONObject payload) throws JSONException {
        JSONArray rows = payload.getJSONArray("avatars");
        List<AvatarOption> avatars = new ArrayList<>(rows.length());
        for (int index = 0; index < rows.length(); index++) {
            JSONObject row = rows.getJSONObject(index);
            avatars.add(new AvatarOption(
                    row.getString("id"),
                    row.getString("name"),
                    row.getString("assetKey")));
        }
        return avatars;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
