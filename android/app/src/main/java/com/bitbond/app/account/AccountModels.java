package com.bitbond.app.account;

import java.util.Objects;

public final class AccountModels {
    private AccountModels() {
    }

    public static final class DeleteAccountResult {
        private final boolean deleted;
        private final String rawJson;

        public DeleteAccountResult(boolean deleted, String rawJson) {
            this.deleted = deleted;
            this.rawJson = Objects.requireNonNull(rawJson, "rawJson");
        }

        public boolean deleted() {
            return deleted;
        }

        public String rawJson() {
            return rawJson;
        }
    }
}
