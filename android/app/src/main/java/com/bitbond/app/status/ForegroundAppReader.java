package com.bitbond.app.status;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;

@FunctionalInterface
public interface ForegroundAppReader {
    String readMostRecentForegroundPackage(long lookbackMillis);

    static ForegroundAppReader fromContext(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context is required");
        }

        UsageStatsManager usageStatsManager =
                (UsageStatsManager) context.getApplicationContext().getSystemService(Context.USAGE_STATS_SERVICE);
        return new UsageEventsForegroundAppReader(usageStatsManager);
    }

    final class UsageEventsForegroundAppReader implements ForegroundAppReader {
        private final UsageStatsManager usageStatsManager;

        UsageEventsForegroundAppReader(UsageStatsManager usageStatsManager) {
            this.usageStatsManager = usageStatsManager;
        }

        @Override
        public String readMostRecentForegroundPackage(long lookbackMillis) {
            if (usageStatsManager == null) {
                return null;
            }

            try {
                long now = System.currentTimeMillis();
                long startTime = Math.max(0L, now - Math.max(0L, lookbackMillis));
                UsageEvents events = usageStatsManager.queryEvents(startTime, now);
                if (events == null) {
                    return null;
                }

                String packageName = null;
                long latestTimestamp = Long.MIN_VALUE;
                UsageEvents.Event event = new UsageEvents.Event();

                while (events.hasNextEvent()) {
                    events.getNextEvent(event);
                    if (isForegroundEvent(event) && event.getTimeStamp() >= latestTimestamp) {
                        packageName = event.getPackageName();
                        latestTimestamp = event.getTimeStamp();
                    }
                }

                return packageName;
            } catch (RuntimeException exception) {
                return null;
            }
        }

        private static boolean isForegroundEvent(UsageEvents.Event event) {
            int eventType = event.getEventType();
            return eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                    || eventType == UsageEvents.Event.ACTIVITY_RESUMED;
        }
    }
}
