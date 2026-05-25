package com.bitbond.app.auth;

import java.util.Objects;

public final class SessionStore {
    private AuthSession session;

    public synchronized AuthSession read() {
        return session;
    }

    public synchronized void write(AuthSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    public synchronized void clear() {
        session = null;
    }
}
