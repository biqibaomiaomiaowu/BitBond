package com.bitbond.app.background;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.Test;

public class BackgroundRefreshPolicyTest {
    @Test
    public void shouldRefreshOnlyWhenConfiguredAllowedAndPastLowFrequencyInterval() {
        BackgroundRefreshPolicy policy = new BackgroundRefreshPolicy(Duration.ofHours(6));
        Instant now = Instant.parse("2026-05-27T08:00:00Z");

        assertFalse(policy.shouldRefresh(false, true, null, now));
        assertFalse(policy.shouldRefresh(true, false, null, now));
        assertTrue(policy.shouldRefresh(true, true, null, now));
        assertFalse(policy.shouldRefresh(true, true, now.minus(Duration.ofHours(5)), now));
        assertTrue(policy.shouldRefresh(true, true, now.minus(Duration.ofHours(6)), now));
    }

    @Test
    public void invalidIntervalsDisableRefreshInsteadOfLooping() {
        BackgroundRefreshPolicy policy = new BackgroundRefreshPolicy(Duration.ZERO);

        assertFalse(policy.shouldRefresh(true, true, null, Instant.parse("2026-05-27T08:00:00Z")));
    }
}
