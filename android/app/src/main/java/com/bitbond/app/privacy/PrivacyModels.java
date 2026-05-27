package com.bitbond.app.privacy;

import com.bitbond.app.status.StatusMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class PrivacyModels {
    private PrivacyModels() {
    }

    public static List<String> activeStatusCodes() {
        List<String> result = new ArrayList<>();
        for (String statusCode : StatusMapper.allowedStatusCodes()) {
            if (isActiveStatusCode(statusCode)) {
                result.add(statusCode);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public static List<String> sanitizeAllowedStatuses(List<String> statuses) {
        Objects.requireNonNull(statuses, "statuses");

        Set<String> result = new LinkedHashSet<>();
        for (String status : statuses) {
            String normalized = normalize(status);
            if (normalized.isEmpty()) {
                continue;
            }
            if (!StatusMapper.allowedStatusCodes().contains(normalized)) {
                throw new IllegalArgumentException("Unsupported statusCode: " + normalized);
            }
            if (isActiveStatusCode(normalized)) {
                result.add(normalized);
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(result));
    }

    private static boolean isActiveStatusCode(String statusCode) {
        return !"paused".equals(statusCode) && !"offline".equals(statusCode);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class PrivacySettings {
        private final List<String> allowedStatuses;
        private final List<String> availableStatuses;
        private final String rawJson;

        public PrivacySettings(List<String> allowedStatuses, List<String> availableStatuses, String rawJson) {
            this.allowedStatuses = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(
                    allowedStatuses,
                    "allowedStatuses")));
            this.availableStatuses = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(
                    availableStatuses,
                    "availableStatuses")));
            this.rawJson = Objects.requireNonNull(rawJson, "rawJson");
        }

        public List<String> allowedStatuses() {
            return allowedStatuses;
        }

        public List<String> availableStatuses() {
            return availableStatuses;
        }

        public String rawJson() {
            return rawJson;
        }
    }
}
