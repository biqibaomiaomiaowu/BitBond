package com.bitbond.app.status;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;

import java.util.List;

@FunctionalInterface
public interface ForegroundAppReader {
    String readMostRecentForegroundPackage(long lookbackMillis);

    static ForegroundAppReader fromContext(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context is required");
        }

        Context applicationContext = context.getApplicationContext();
        Context serviceContext = applicationContext == null ? context : applicationContext;
        UsageStatsManager usageStatsManager =
                (UsageStatsManager) serviceContext.getSystemService(Context.USAGE_STATS_SERVICE);
        return new UsageEventsForegroundAppReader(usageStatsManager, serviceContext.getPackageName());
    }

    final class UsageEventsForegroundAppReader implements ForegroundAppReader {
        private final UsageStatsManager usageStatsManager;
        private final String ownPackageName;

        UsageEventsForegroundAppReader(UsageStatsManager usageStatsManager) {
            this(usageStatsManager, null);
        }

        UsageEventsForegroundAppReader(UsageStatsManager usageStatsManager, String ownPackageName) {
            this.usageStatsManager = usageStatsManager;
            this.ownPackageName = ownPackageName;
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

                ForegroundEventSnapshot selectedEvent = null;
                UsageEvents.Event event = new UsageEvents.Event();

                while (events.hasNextEvent()) {
                    events.getNextEvent(event);
                    if (isForegroundEvent(event)) {
                        selectedEvent = selectMoreRecentForegroundEvent(
                                selectedEvent,
                                new ForegroundEventSnapshot(event.getPackageName(), event.getTimeStamp()),
                                ownPackageName);
                    }
                }

                return selectedEvent == null ? null : selectedEvent.packageName;
            } catch (RuntimeException exception) {
                return null;
            }
        }

        static String selectMostRecentForegroundPackage(
                List<ForegroundEventSnapshot> foregroundEvents,
                String ownPackageName) {
            if (foregroundEvents == null) {
                return null;
            }

            ForegroundEventSnapshot selectedEvent = null;

            for (ForegroundEventSnapshot event : foregroundEvents) {
                selectedEvent = selectMoreRecentForegroundEvent(selectedEvent, event, ownPackageName);
            }

            return selectedEvent == null ? null : selectedEvent.packageName;
        }

        static ForegroundEventSnapshot selectMoreRecentForegroundEvent(
                ForegroundEventSnapshot selectedEvent,
                ForegroundEventSnapshot candidateEvent,
                String ownPackageName) {
            if (candidateEvent == null || !isSelectablePackage(candidateEvent.packageName, ownPackageName)) {
                return selectedEvent;
            }
            if (selectedEvent == null || candidateEvent.timestamp >= selectedEvent.timestamp) {
                return candidateEvent;
            }
            return selectedEvent;
        }

        private static boolean isForegroundEvent(UsageEvents.Event event) {
            int eventType = event.getEventType();
            return eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                    || eventType == UsageEvents.Event.ACTIVITY_RESUMED;
        }

        private static boolean isSelectablePackage(String packageName, String ownPackageName) {
            if (packageName == null || packageName.trim().isEmpty()) {
                return false;
            }
            return ownPackageName == null || !ownPackageName.equals(packageName);
        }

        static final class ForegroundEventSnapshot {
            final String packageName;
            final long timestamp;

            ForegroundEventSnapshot(String packageName, long timestamp) {
                this.packageName = packageName;
                this.timestamp = timestamp;
            }
        }
    }
}
