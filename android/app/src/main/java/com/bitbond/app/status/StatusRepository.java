package com.bitbond.app.status;

import com.bitbond.app.api.ApiError;
import com.bitbond.app.api.ApiResult;
import com.bitbond.app.api.SupabaseRpcClient;
import com.bitbond.app.auth.AuthSession;
import com.bitbond.app.status.StatusModels.CurrentStatusResult;
import com.bitbond.app.status.StatusModels.PartnerProfile;
import com.bitbond.app.status.StatusModels.PartnerStatus;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Objects;

import org.json.JSONException;
import org.json.JSONObject;

public final class StatusRepository implements StatusUploader, StatusGateway {
    private static final String UPSERT_CURRENT_STATUS_RPC = "upsert_current_status";
    private static final String GET_PARTNER_STATUS_RPC = "get_partner_status";
    private static final String STATUS_JSON_ERROR = "status_json_error";

    private final SupabaseRpcClient rpcClient;

    public StatusRepository(SupabaseRpcClient rpcClient) {
        this.rpcClient = Objects.requireNonNull(rpcClient, "rpcClient");
    }

    @Override
    public ApiResult<CurrentStatusResult> uploadCurrentStatus(
            AuthSession session,
            String statusCode,
            Instant statusUpdatedAt) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(statusUpdatedAt, "statusUpdatedAt");

        String normalizedStatusCode;
        try {
            normalizedStatusCode = validateStatusCode(statusCode);
        } catch (IllegalArgumentException exception) {
            return ApiResult.error(new ApiError("invalid_status_code", "Status code is invalid"));
        }

        try {
            JSONObject payload = new JSONObject()
                    .put("next_status_code", normalizedStatusCode)
                    .put("next_status_updated_at", statusUpdatedAt.toString());
            ApiResult<JSONObject> result = rpcClient.rpc(
                    session.accessToken(),
                    UPSERT_CURRENT_STATUS_RPC,
                    payload);
            if (!result.isSuccess()) {
                return ApiResult.error(result.error());
            }

            return ApiResult.success(parseCurrentStatusResult(result.value()));
        } catch (JSONException | IllegalArgumentException | DateTimeParseException exception) {
            return ApiResult.error(new ApiError(STATUS_JSON_ERROR, "Status response could not be parsed"));
        }
    }

    @Override
    public ApiResult<PartnerStatus> getPartnerStatus(AuthSession session) {
        Objects.requireNonNull(session, "session");

        try {
            ApiResult<JSONObject> result = rpcClient.rpc(
                    session.accessToken(),
                    GET_PARTNER_STATUS_RPC,
                    new JSONObject());
            if (!result.isSuccess()) {
                return ApiResult.error(result.error());
            }

            return ApiResult.success(parsePartnerStatus(result.value()));
        } catch (JSONException | IllegalArgumentException | DateTimeParseException exception) {
            return ApiResult.error(new ApiError(STATUS_JSON_ERROR, "Status response could not be parsed"));
        }
    }

    private static CurrentStatusResult parseCurrentStatusResult(JSONObject source) throws JSONException {
        String statusCode = requiredStatusCode(source, "statusCode");
        Instant statusUpdatedAt = requiredInstant(source, "statusUpdatedAt");
        Instant expiresAt = requiredInstant(source, "expiresAt");
        JSONObject publicJson = new JSONObject()
                .put("statusCode", statusCode)
                .put("statusUpdatedAt", statusUpdatedAt.toString())
                .put("expiresAt", expiresAt.toString());

        return new CurrentStatusResult(statusCode, statusUpdatedAt, expiresAt, publicJson.toString());
    }

    private static PartnerStatus parsePartnerStatus(JSONObject source) throws JSONException {
        boolean paired = source.getBoolean("paired");
        if (!paired) {
            JSONObject publicJson = new JSONObject().put("paired", false);
            return new PartnerStatus(false, null, null, null, null, false, publicJson.toString());
        }

        JSONObject partnerSource = source.getJSONObject("partner");
        String nickname = nullableString(partnerSource, "nickname");
        String avatarId = nullableString(partnerSource, "avatarId");
        PartnerProfile partner = new PartnerProfile(nickname, avatarId);
        String statusCode = requiredStatusCode(source, "statusCode");
        Instant statusUpdatedAt = nullableInstant(source, "statusUpdatedAt");
        Instant expiresAt = nullableInstant(source, "expiresAt");
        boolean paused = source.getBoolean("isPaused");

        JSONObject partnerJson = new JSONObject()
                .put("nickname", jsonValue(nickname))
                .put("avatarId", jsonValue(avatarId));
        JSONObject publicJson = new JSONObject()
                .put("paired", true)
                .put("partner", partnerJson)
                .put("statusCode", statusCode)
                .put("statusUpdatedAt", jsonValue(statusUpdatedAt))
                .put("expiresAt", jsonValue(expiresAt))
                .put("isPaused", paused);

        return new PartnerStatus(
                true,
                partner,
                statusCode,
                statusUpdatedAt,
                expiresAt,
                paused,
                publicJson.toString());
    }

    private static String requiredStatusCode(JSONObject source, String key) throws JSONException {
        return validateStatusCode(source.getString(key));
    }

    private static String validateStatusCode(String value) {
        if (value == null) {
            throw new IllegalArgumentException("statusCode is required");
        }

        String normalized = value.trim();
        if (!StatusMapper.allowedStatusCodes().contains(normalized)) {
            throw new IllegalArgumentException("Unsupported statusCode: " + normalized);
        }

        return normalized;
    }

    private static String nullableString(JSONObject source, String key) throws JSONException {
        if (!source.has(key) || source.isNull(key)) {
            return null;
        }

        String value = source.getString(key).trim();
        return value.isEmpty() ? null : value;
    }

    private static Instant requiredInstant(JSONObject source, String key) throws JSONException {
        if (!source.has(key) || source.isNull(key)) {
            throw new JSONException(key + " is required");
        }

        return parseInstant(source.getString(key));
    }

    private static Instant nullableInstant(JSONObject source, String key) throws JSONException {
        if (!source.has(key) || source.isNull(key)) {
            return null;
        }

        return parseInstant(source.getString(key));
    }

    private static Instant parseInstant(String value) {
        String normalized = normalizeTimestamp(value);
        try {
            return Instant.parse(normalized);
        } catch (DateTimeParseException exception) {
            return OffsetDateTime.parse(normalized).toInstant();
        }
    }

    private static String normalizeTimestamp(String value) {
        String normalized = Objects.requireNonNull(value, "timestamp").trim();
        if (normalized.indexOf(' ') > 0 && normalized.indexOf('T') < 0) {
            normalized = normalized.replace(' ', 'T');
        }

        if (normalized.matches(".*[+-]\\d{2}$")) {
            return normalized + ":00";
        }

        if (normalized.matches(".*[+-]\\d{4}$")) {
            return normalized.substring(0, normalized.length() - 2)
                    + ":"
                    + normalized.substring(normalized.length() - 2);
        }

        return normalized;
    }

    private static Object jsonValue(String value) {
        return value == null ? JSONObject.NULL : value;
    }

    private static Object jsonValue(Instant value) {
        return value == null ? JSONObject.NULL : value.toString();
    }
}
