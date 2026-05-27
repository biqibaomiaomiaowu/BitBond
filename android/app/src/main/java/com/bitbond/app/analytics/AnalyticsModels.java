package com.bitbond.app.analytics;

import java.util.Objects;

public final class AnalyticsModels {
    private AnalyticsModels() {
    }

    public static final class AnalyticsEventResult {
        private final boolean recorded;
        private final String rawJson;

        public AnalyticsEventResult(boolean recorded, String rawJson) {
            this.recorded = recorded;
            this.rawJson = Objects.requireNonNull(rawJson, "rawJson");
        }

        public boolean recorded() {
            return recorded;
        }

        public String rawJson() {
            return rawJson;
        }
    }
}
