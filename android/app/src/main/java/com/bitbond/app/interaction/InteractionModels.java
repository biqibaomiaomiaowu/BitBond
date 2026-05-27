package com.bitbond.app.interaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class InteractionModels {
    private InteractionModels() {
    }

    public static final class HeartInteraction {
        private final String interactionId;
        private final String type;
        private final String createdAt;
        private final boolean seen;
        private final String rawJson;

        public HeartInteraction(String interactionId, String type, String createdAt, boolean seen, String rawJson) {
            this.interactionId = requireText(interactionId, "interactionId");
            this.type = requireText(type, "type");
            this.createdAt = requireText(createdAt, "createdAt");
            this.seen = seen;
            this.rawJson = Objects.requireNonNull(rawJson, "rawJson");
        }

        public String interactionId() {
            return interactionId;
        }

        public String type() {
            return type;
        }

        public String createdAt() {
            return createdAt;
        }

        public boolean seen() {
            return seen;
        }

        public String rawJson() {
            return rawJson;
        }
    }

    public static final class InteractionList {
        private final List<HeartInteraction> interactions;
        private final String rawJson;

        public InteractionList(List<HeartInteraction> interactions, String rawJson) {
            this.interactions = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(
                    interactions,
                    "interactions")));
            this.rawJson = Objects.requireNonNull(rawJson, "rawJson");
        }

        public List<HeartInteraction> interactions() {
            return interactions;
        }

        public String rawJson() {
            return rawJson;
        }
    }

    public static final class MarkSeenResult {
        private final int markedCount;
        private final String rawJson;

        public MarkSeenResult(int markedCount, String rawJson) {
            if (markedCount < 0) {
                throw new IllegalArgumentException("markedCount must not be negative");
            }
            this.markedCount = markedCount;
            this.rawJson = Objects.requireNonNull(rawJson, "rawJson");
        }

        public int markedCount() {
            return markedCount;
        }

        public boolean markedSeen() {
            return markedCount > 0;
        }

        public String rawJson() {
            return rawJson;
        }
    }

    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return normalized;
    }
}
