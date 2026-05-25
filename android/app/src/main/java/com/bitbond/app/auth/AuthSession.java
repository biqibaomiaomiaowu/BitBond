package com.bitbond.app.auth;

import java.util.Objects;

public final class AuthSession {
    private final String accessToken;
    private final String refreshToken;
    private final long expiresAtEpochSeconds;

    public AuthSession(String accessToken, String refreshToken, long expiresAtEpochSeconds) {
        this.accessToken = requireToken(accessToken, "accessToken");
        this.refreshToken = requireToken(refreshToken, "refreshToken");
        this.expiresAtEpochSeconds = expiresAtEpochSeconds;
    }

    public String accessToken() {
        return accessToken;
    }

    public String refreshToken() {
        return refreshToken;
    }

    public long expiresAtEpochSeconds() {
        return expiresAtEpochSeconds;
    }

    public String authorizationHeader() {
        return "Bearer " + accessToken;
    }

    public boolean isExpired(long nowEpochSeconds) {
        return nowEpochSeconds >= expiresAtEpochSeconds;
    }

    private static String requireToken(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }

        return normalized;
    }
}
