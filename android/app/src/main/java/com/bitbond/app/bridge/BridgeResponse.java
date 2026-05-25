package com.bitbond.app.bridge;

import java.util.Objects;

import org.json.JSONObject;

public final class BridgeResponse {
    private final JSONObject data;
    private final String errorCode;
    private final String errorMessage;

    private BridgeResponse(JSONObject data, String errorCode, String errorMessage) {
        this.data = data;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public static BridgeResponse success(JSONObject data) {
        return new BridgeResponse(Objects.requireNonNull(data, "data"), null, null);
    }

    public static BridgeResponse error(String code, String message) {
        return new BridgeResponse(
                null,
                Objects.requireNonNull(code, "code"),
                Objects.requireNonNull(message, "message"));
    }

    public String toJson() {
        if (data != null) {
            return "{\"ok\":true,\"data\":" + data + "}";
        }

        return "{\"ok\":false,\"error\":{\"code\":"
                + JSONObject.quote(errorCode)
                + ",\"message\":"
                + JSONObject.quote(errorMessage)
                + "}}";
    }
}
