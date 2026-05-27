package com.bitbond.app.sharing;

import com.bitbond.app.status.StatusMapper;

import java.util.Objects;

public final class SharingModels {
    private SharingModels() {
    }

    public static final class SharingState {
        private final boolean sharing;
        private final String statusCode;
        private final String rawJson;

        public SharingState(boolean sharing, String statusCode, String rawJson) {
            this.sharing = sharing;
            this.statusCode = requireStatusCode(statusCode);
            this.rawJson = Objects.requireNonNull(rawJson, "rawJson");
        }

        public boolean sharing() {
            return sharing;
        }

        public String statusCode() {
            return statusCode;
        }

        public String rawJson() {
            return rawJson;
        }

        private static String requireStatusCode(String value) {
            String normalized = Objects.requireNonNull(value, "statusCode").trim();
            if (!StatusMapper.allowedStatusCodes().contains(normalized)) {
                throw new IllegalArgumentException("Unsupported sharing status: " + normalized);
            }
            return normalized;
        }
    }
}
