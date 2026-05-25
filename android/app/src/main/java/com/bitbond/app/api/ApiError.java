package com.bitbond.app.api;

public final class ApiError {
    private final String code;
    private final String message;

    public ApiError(String code, String message) {
        this.code = normalize(code);
        this.message = safeMessage(message);
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String safeMessage(String message) {
        String normalized = normalize(message);
        if (normalized.startsWith("{") || normalized.startsWith("[")) {
            return "Request failed";
        }

        return normalized;
    }
}
