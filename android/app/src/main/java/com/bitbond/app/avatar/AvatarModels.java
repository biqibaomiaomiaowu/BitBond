package com.bitbond.app.avatar;

import java.util.Objects;

public final class AvatarModels {
    private AvatarModels() {
    }

    public static final class AvatarOption {
        private final String id;
        private final String name;
        private final String assetKey;

        public AvatarOption(String id, String name, String assetKey) {
            this.id = requireText(id, "id");
            this.name = requireText(name, "name");
            this.assetKey = requireText(assetKey, "assetKey");
        }

        public String id() {
            return id;
        }

        public String name() {
            return name;
        }

        public String assetKey() {
            return assetKey;
        }
    }

    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return normalized;
    }
}
