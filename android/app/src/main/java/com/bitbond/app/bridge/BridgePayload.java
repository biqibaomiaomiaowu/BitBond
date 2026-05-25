package com.bitbond.app.bridge;

import java.util.Iterator;
import java.util.Locale;
import java.util.Objects;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class BridgePayload {
    private final JSONObject rpcPayload;

    private BridgePayload(JSONObject rpcPayload) {
        this.rpcPayload = rpcPayload;
    }

    public static BridgePayload from(JSONObject rawPayload) {
        Objects.requireNonNull(rawPayload, "rawPayload");

        String inviteCode = trimmedString(rawPayload, "code");
        if (inviteCode == null || inviteCode.isEmpty()) {
            throw new IllegalArgumentException("invite code is required");
        }

        try {
            JSONObject rpcPayload = new JSONObject()
                    .put("invite_code", inviteCode);

            String avatarId = trimmedString(rawPayload, "avatarId");
            if (avatarId != null && !avatarId.isEmpty()) {
                rpcPayload.put("next_avatar_id", avatarId);
            }

            return new BridgePayload(rpcPayload);
        } catch (JSONException exception) {
            throw new IllegalArgumentException("payload could not be mapped");
        }
    }

    public JSONObject toRpcPayload() {
        try {
            return new JSONObject(rpcPayload.toString());
        } catch (JSONException exception) {
            throw new IllegalStateException("payload copy failed");
        }
    }

    public static JSONObject safeObject(JSONObject rawPayload) {
        Objects.requireNonNull(rawPayload, "rawPayload");

        JSONObject safePayload = new JSONObject();
        Iterator<String> keys = rawPayload.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (isSecretLikeKey(key)) {
                continue;
            }
            try {
                safePayload.put(key, safeValue(rawPayload.opt(key)));
            } catch (JSONException exception) {
                throw new IllegalArgumentException("payload could not be sanitized");
            }
        }
        return safePayload;
    }

    private static String trimmedString(JSONObject object, String key) {
        if (!object.has(key) || object.isNull(key)) {
            return null;
        }

        Object value = object.opt(key);
        if (!(value instanceof String)) {
            throw new IllegalArgumentException(key + " must be a string");
        }
        return ((String) value).trim();
    }

    private static Object safeValue(Object value) {
        if (value instanceof JSONObject) {
            return safeObject((JSONObject) value);
        }

        if (value instanceof JSONArray) {
            JSONArray source = (JSONArray) value;
            JSONArray safeArray = new JSONArray();
            for (int index = 0; index < source.length(); index++) {
                safeArray.put(safeValue(source.opt(index)));
            }
            return safeArray;
        }

        return value;
    }

    private static boolean isSecretLikeKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.contains("token")
                || normalized.contains("secret")
                || normalized.contains("password")
                || normalized.contains("authorization")
                || normalized.contains("api_key")
                || normalized.contains("apikey");
    }
}
