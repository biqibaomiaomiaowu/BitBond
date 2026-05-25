package com.bitbond.app.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AuthSessionTest {
    @Test
    public void authorizationHeaderAndExpiryUseAccessTokenAndExpiryEpoch() {
        AuthSession session = new AuthSession("access-token", "refresh-token", 1000L);

        assertEquals("Bearer access-token", session.authorizationHeader());
        assertFalse(session.isExpired(999L));
        assertTrue(session.isExpired(1000L));
        assertTrue(session.isExpired(1001L));
    }

    @Test
    public void sessionStoreReadsWritesAndClearsSession() {
        SessionStore store = new SessionStore();
        AuthSession session = new AuthSession("access-token", "refresh-token", 1000L);

        assertNull(store.read());

        store.write(session);

        assertSame(session, store.read());

        store.clear();

        assertNull(store.read());
    }
}
