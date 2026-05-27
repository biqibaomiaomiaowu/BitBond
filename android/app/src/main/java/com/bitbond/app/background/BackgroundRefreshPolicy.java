package com.bitbond.app.background;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class BackgroundRefreshPolicy {
    private final Duration minimumInterval;

    public BackgroundRefreshPolicy(Duration minimumInterval) {
        this.minimumInterval = Objects.requireNonNull(minimumInterval, "minimumInterval");
    }

    public boolean shouldRefresh(
            boolean supabaseConfigured,
            boolean usageAccessGranted,
            Instant lastRefreshAt,
            Instant now) {
        Objects.requireNonNull(now, "now");
        if (!supabaseConfigured || !usageAccessGranted || minimumInterval.isZero() || minimumInterval.isNegative()) {
            return false;
        }
        if (lastRefreshAt == null) {
            return true;
        }
        return !Duration.between(lastRefreshAt, now).minus(minimumInterval).isNegative();
    }
}
