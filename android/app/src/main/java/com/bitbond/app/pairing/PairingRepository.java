package com.bitbond.app.pairing;

import com.bitbond.app.api.ApiError;
import com.bitbond.app.api.ApiResult;
import com.bitbond.app.api.SupabaseRpcClient;
import com.bitbond.app.auth.AuthSession;
import com.bitbond.app.pairing.PairingModels.CoupleState;
import com.bitbond.app.pairing.PairingModels.PairInvite;
import com.bitbond.app.pairing.PairingModels.PartnerProfile;

import java.util.Objects;

import org.json.JSONException;
import org.json.JSONObject;

public final class PairingRepository implements PairingGateway {
    private static final String CREATE_PAIR_INVITE_RPC = "create_pair_invite";
    private static final String ACCEPT_PAIR_INVITE_RPC = "accept_pair_invite";
    private static final String GET_CURRENT_COUPLE_RPC = "get_current_couple";
    private static final String UNLINK_CURRENT_COUPLE_RPC = "unlink_current_couple";

    private final SupabaseRpcClient rpcClient;

    public PairingRepository(SupabaseRpcClient rpcClient) {
        this.rpcClient = Objects.requireNonNull(rpcClient, "rpcClient");
    }

    @Override
    public ApiResult<PairInvite> createInvite(AuthSession session) {
        ApiResult<JSONObject> result = rpcClient.rpc(
                accessToken(session),
                CREATE_PAIR_INVITE_RPC,
                new JSONObject());
        if (!result.isSuccess()) {
            return ApiResult.error(result.error());
        }

        try {
            return ApiResult.success(new PairInvite(
                    result.value().getString("code"),
                    result.value().getString("expiresAt")));
        } catch (JSONException | IllegalArgumentException exception) {
            return parseError();
        }
    }

    @Override
    public ApiResult<CoupleState> acceptInvite(AuthSession session, String code) {
        String normalizedCode = normalize(code);
        if (normalizedCode.isEmpty()) {
            return ApiResult.error(new ApiError("invalid_invite_code", "Invite code is required"));
        }

        try {
            ApiResult<JSONObject> result = rpcClient.rpc(
                    accessToken(session),
                    ACCEPT_PAIR_INVITE_RPC,
                    new JSONObject().put("invite_code", normalizedCode));
            if (!result.isSuccess()) {
                return ApiResult.error(result.error());
            }

            return ApiResult.success(parseAcceptedCouple(result.value()));
        } catch (JSONException | IllegalArgumentException exception) {
            return parseError();
        }
    }

    @Override
    public ApiResult<CoupleState> getCurrentCouple(AuthSession session) {
        ApiResult<JSONObject> result = rpcClient.rpc(
                accessToken(session),
                GET_CURRENT_COUPLE_RPC,
                new JSONObject());
        if (!result.isSuccess()) {
            return ApiResult.error(result.error());
        }

        try {
            return ApiResult.success(parseCurrentCouple(result.value()));
        } catch (JSONException | IllegalArgumentException exception) {
            return parseError();
        }
    }

    @Override
    public ApiResult<Boolean> unlink(AuthSession session) {
        ApiResult<JSONObject> result = rpcClient.rpc(
                accessToken(session),
                UNLINK_CURRENT_COUPLE_RPC,
                new JSONObject());
        if (!result.isSuccess()) {
            return ApiResult.error(result.error());
        }

        try {
            return ApiResult.success(result.value().getBoolean("unlinked"));
        } catch (JSONException exception) {
            return parseError();
        }
    }

    private static CoupleState parseAcceptedCouple(JSONObject json) throws JSONException {
        return CoupleState.paired(
                json.getString("coupleId"),
                parsePartner(json.getJSONObject("partner")));
    }

    private static CoupleState parseCurrentCouple(JSONObject json) throws JSONException {
        if (!json.getBoolean("paired")) {
            return CoupleState.unpaired();
        }

        return CoupleState.paired(
                json.getString("coupleId"),
                parsePartner(json.getJSONObject("partner")));
    }

    private static PartnerProfile parsePartner(JSONObject json) {
        return new PartnerProfile(
                nullableString(json, "nickname"),
                nullableString(json, "avatarId"));
    }

    private static String nullableString(JSONObject json, String key) {
        if (!json.has(key) || json.isNull(key)) {
            return null;
        }
        return json.optString(key, null);
    }

    private static String accessToken(AuthSession session) {
        return Objects.requireNonNull(session, "session").accessToken();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static <T> ApiResult<T> parseError() {
        return ApiResult.error(new ApiError("pairing_json_error", "Pairing response could not be parsed"));
    }
}
