package com.bitbond.app.status;

import java.time.Instant;
import java.util.Objects;

public final class StatusModels {
    private StatusModels() {
    }

    public static final class CurrentStatusResult {
        private final String statusCode;
        private final Instant statusUpdatedAt;
        private final Instant expiresAt;
        private final String rawJson;

        public CurrentStatusResult(String statusCode, Instant statusUpdatedAt, Instant expiresAt, String rawJson) {
            this.statusCode = requireStatusCode(statusCode);
            this.statusUpdatedAt = Objects.requireNonNull(statusUpdatedAt, "statusUpdatedAt");
            this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
            this.rawJson = Objects.requireNonNull(rawJson, "rawJson");
        }

        public String statusCode() {
            return statusCode;
        }

        public Instant statusUpdatedAt() {
            return statusUpdatedAt;
        }

        public Instant expiresAt() {
            return expiresAt;
        }

        public String rawJson() {
            return rawJson;
        }
    }

    public static final class PartnerStatus {
        private final boolean paired;
        private final PartnerProfile partner;
        private final String statusCode;
        private final Instant statusUpdatedAt;
        private final Instant expiresAt;
        private final boolean paused;
        private final String rawJson;

        public PartnerStatus(
                boolean paired,
                PartnerProfile partner,
                String statusCode,
                Instant statusUpdatedAt,
                Instant expiresAt,
                boolean paused,
                String rawJson) {
            this.paired = paired;
            this.partner = partner;
            this.statusCode = normalizeOptional(statusCode);
            this.statusUpdatedAt = statusUpdatedAt;
            this.expiresAt = expiresAt;
            this.paused = paused;
            this.rawJson = Objects.requireNonNull(rawJson, "rawJson");
        }

        public boolean isPaired() {
            return paired;
        }

        public PartnerProfile partner() {
            return partner;
        }

        public String statusCode() {
            return statusCode;
        }

        public Instant statusUpdatedAt() {
            return statusUpdatedAt;
        }

        public Instant expiresAt() {
            return expiresAt;
        }

        public boolean isPaused() {
            return paused;
        }

        public String rawJson() {
            return rawJson;
        }
    }

    public static final class PartnerProfile {
        private final String nickname;
        private final String avatarId;

        public PartnerProfile(String nickname, String avatarId) {
            this.nickname = normalizeOptional(nickname);
            this.avatarId = normalizeOptional(avatarId);
        }

        public String nickname() {
            return nickname;
        }

        public String avatarId() {
            return avatarId;
        }
    }

    private static String requireStatusCode(String value) {
        String normalized = Objects.requireNonNull(value, "statusCode").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("statusCode must not be empty");
        }

        return normalized;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
