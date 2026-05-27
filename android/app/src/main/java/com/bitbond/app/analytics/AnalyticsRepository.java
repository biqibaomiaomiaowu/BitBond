package com.bitbond.app.analytics;

import com.bitbond.app.analytics.AnalyticsModels.AnalyticsEventResult;
import com.bitbond.app.api.ApiError;
import com.bitbond.app.api.ApiResult;
import com.bitbond.app.api.SupabaseRpcClient;
import com.bitbond.app.auth.AuthSession;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Locale;
import java.util.Objects;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class AnalyticsRepository implements AnalyticsGateway {
    private static final String RECORD_ANALYTICS_EVENT_RPC = "record_analytics_event";
    private static final int MAX_PROPERTIES_DEPTH = 4;
    private static final int MAX_PROPERTIES_BYTES = 4096;
    private static final Object DROPPED_VALUE = new Object();

    private final SupabaseRpcClient rpcClient;

    public AnalyticsRepository(SupabaseRpcClient rpcClient) {
        this.rpcClient = Objects.requireNonNull(rpcClient, "rpcClient");
    }

    @Override
    public ApiResult<AnalyticsEventResult> recordEvent(AuthSession session, String eventName, JSONObject properties) {
        Objects.requireNonNull(session, "session");

        String normalizedEventName = normalizeEventName(eventName);
        if (normalizedEventName.isEmpty()) {
            return ApiResult.error(new ApiError("invalid_event_name", "Event name is required"));
        }

        try {
            JSONObject nextProperties = safeProperties(properties);
            if (utf8Size(nextProperties) > MAX_PROPERTIES_BYTES) {
                return ApiResult.error(new ApiError(
                        "analytics_properties_too_large",
                        "Analytics properties exceed the size limit"));
            }

            ApiResult<JSONObject> result = rpcClient.rpc(
                    session.accessToken(),
                    RECORD_ANALYTICS_EVENT_RPC,
                    new JSONObject()
                            .put("next_event_name", normalizedEventName)
                            .put("next_properties", nextProperties));
            if (!result.isSuccess()) {
                return ApiResult.error(result.error());
            }

            boolean recorded = result.value().getBoolean("recorded");
            return ApiResult.success(new AnalyticsEventResult(
                    recorded,
                    new JSONObject().put("recorded", recorded).toString()));
        } catch (AnalyticsPropertiesException exception) {
            return ApiResult.error(new ApiError(exception.code, exception.getMessage()));
        } catch (JSONException | IllegalArgumentException exception) {
            return ApiResult.error(new ApiError("analytics_json_error", "Analytics response could not be parsed"));
        }
    }

    public static JSONObject safeProperties(JSONObject properties) throws JSONException {
        JSONObject source = properties == null ? new JSONObject() : properties;
        if (exceedsMaxDepth(source, 1)) {
            throw new AnalyticsPropertiesException(
                    "analytics_properties_too_deep",
                    "Analytics properties exceed the depth limit");
        }
        SanitizationState state = new SanitizationState();
        JSONObject safe = safeProperties(source, 1, state);
        if (state.depthExceeded) {
            throw new AnalyticsPropertiesException(
                    "analytics_properties_too_deep",
                    "Analytics properties exceed the depth limit");
        }
        return safe;
    }

    private static JSONObject safeProperties(
            JSONObject source,
            int depth,
            SanitizationState state) throws JSONException {
        JSONObject safe = new JSONObject();
        Iterator<String> keys = source.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (isPrivateProperty(key)) {
                continue;
            }
            Object value = safeValue(source.opt(key), depth, state);
            if (value != DROPPED_VALUE) {
                safe.put(key, value);
            }
        }
        return safe;
    }

    private static Object safeValue(
            Object value,
            int containerDepth,
            SanitizationState state) throws JSONException {
        if (value instanceof JSONObject) {
            if (containerDepth >= MAX_PROPERTIES_DEPTH) {
                state.depthExceeded = true;
                return DROPPED_VALUE;
            }
            return safeProperties((JSONObject) value, containerDepth + 1, state);
        }
        if (value instanceof JSONArray) {
            if (containerDepth >= MAX_PROPERTIES_DEPTH) {
                state.depthExceeded = true;
                return DROPPED_VALUE;
            }
            JSONArray source = (JSONArray) value;
            JSONArray safe = new JSONArray();
            for (int i = 0; i < source.length(); i++) {
                Object item = safeValue(source.opt(i), containerDepth + 1, state);
                if (item != DROPPED_VALUE) {
                    safe.put(item);
                }
            }
            return safe;
        }
        return value;
    }

    private static boolean exceedsMaxDepth(Object value, int depth) throws JSONException {
        if (depth > MAX_PROPERTIES_DEPTH) {
            return true;
        }
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                if (exceedsMaxDepth(object.opt(keys.next()), depth + 1)) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                if (exceedsMaxDepth(array.opt(i), depth + 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isPrivateProperty(String key) {
        String normalized = key == null
                ? ""
                : key.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return "package".equals(normalized)
                || "packagename".equals(normalized)
                || "packageid".equals(normalized)
                || "apppackage".equals(normalized)
                || "app".equals(normalized)
                || "appid".equals(normalized)
                || "appname".equals(normalized)
                || "usageduration".equals(normalized)
                || "duration".equals(normalized)
                || "content".equals(normalized)
                || "chat".equals(normalized)
                || "chattarget".equals(normalized)
                || "statuscode".equals(normalized)
                || "partnerstatus".equals(normalized)
                || "email".equals(normalized)
                || "emailaddress".equals(normalized)
                || "phone".equals(normalized)
                || "phonenumber".equals(normalized)
                || "history".equals(normalized)
                || normalized.contains("token")
                || normalized.contains("secret")
                || normalized.contains("password")
                || normalized.contains("authorization")
                || normalized.contains("apikey");
    }

    private static int utf8Size(JSONObject properties) {
        return properties.toString().getBytes(StandardCharsets.UTF_8).length;
    }

    private static String normalizeEventName(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class SanitizationState {
        private boolean depthExceeded;
    }

    private static final class AnalyticsPropertiesException extends IllegalArgumentException {
        private final String code;

        private AnalyticsPropertiesException(String code, String message) {
            super(message);
            this.code = code;
        }
    }
}
