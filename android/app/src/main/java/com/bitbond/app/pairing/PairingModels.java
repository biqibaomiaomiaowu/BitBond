package com.bitbond.app.pairing;

import java.util.Objects;

public final class PairingModels {
    private PairingModels() {
    }

    public static final class PairInvite {
        private final String code;
        private final String expiresAt;

        public PairInvite(String code, String expiresAt) {
            this.code = requireText(code, "code");
            this.expiresAt = requireText(expiresAt, "expiresAt");
        }

        public String code() {
            return code;
        }

        public String expiresAt() {
            return expiresAt;
        }
    }

    public static final class CoupleState {
        private final boolean paired;
        private final String coupleId;
        private final PartnerProfile partner;

        private CoupleState(boolean paired, String coupleId, PartnerProfile partner) {
            this.paired = paired;
            this.coupleId = coupleId;
            this.partner = partner;
        }

        public static CoupleState paired(String coupleId, PartnerProfile partner) {
            return new CoupleState(true, requireText(coupleId, "coupleId"), Objects.requireNonNull(partner, "partner"));
        }

        public static CoupleState unpaired() {
            return new CoupleState(false, null, null);
        }

        public boolean paired() {
            return paired;
        }

        public String coupleId() {
            return coupleId;
        }

        public PartnerProfile partner() {
            return partner;
        }
    }

    public static final class PartnerProfile {
        private final String nickname;
        private final String avatarId;

        public PartnerProfile(String nickname, String avatarId) {
            this.nickname = nickname;
            this.avatarId = avatarId;
        }

        public String nickname() {
            return nickname;
        }

        public String avatarId() {
            return avatarId;
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
