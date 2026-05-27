package com.bitbond.app.status;

import android.content.SharedPreferences;

import java.time.DateTimeException;
import java.time.Instant;
import java.util.Objects;

public interface LastForegroundStore {
    void save(String packageName, String statusCode, Instant detectedAt);

    Entry readFresh(long maxAgeMillis, Instant now);

    static LastForegroundStore inMemory() {
        return new InMemoryLastForegroundStore();
    }

    static LastForegroundStore noOp() {
        return NoOpLastForegroundStore.INSTANCE;
    }

    final class Entry {
        private final String packageName;
        private final String statusCode;
        private final Instant detectedAt;

        public Entry(String packageName, String statusCode, Instant detectedAt) {
            this.packageName = requireNonBlank(packageName, "packageName");
            this.statusCode = requireNonBlank(statusCode, "statusCode");
            this.detectedAt = Objects.requireNonNull(detectedAt, "detectedAt");
        }

        public String packageName() {
            return packageName;
        }

        public String statusCode() {
            return statusCode;
        }

        public Instant detectedAt() {
            return detectedAt;
        }
    }

    final class SharedPreferencesLastForegroundStore implements LastForegroundStore {
        private static final String KEY_PACKAGE_NAME = "package_name";
        private static final String KEY_STATUS_CODE = "status_code";
        private static final String KEY_DETECTED_AT_EPOCH_MILLIS = "detected_at_epoch_millis";

        private final SharedPreferences preferences;

        public SharedPreferencesLastForegroundStore(SharedPreferences preferences) {
            this.preferences = Objects.requireNonNull(preferences, "preferences");
        }

        @Override
        public void save(String packageName, String statusCode, Instant detectedAt) {
            Entry entry = new Entry(packageName, statusCode, detectedAt);
            preferences.edit()
                    .putString(KEY_PACKAGE_NAME, entry.packageName())
                    .putString(KEY_STATUS_CODE, entry.statusCode())
                    .putLong(KEY_DETECTED_AT_EPOCH_MILLIS, entry.detectedAt().toEpochMilli())
                    .apply();
        }

        @Override
        public Entry readFresh(long maxAgeMillis, Instant now) {
            String packageName = preferences.getString(KEY_PACKAGE_NAME, "");
            String statusCode = preferences.getString(KEY_STATUS_CODE, "");
            long detectedAtEpochMillis = preferences.getLong(
                    KEY_DETECTED_AT_EPOCH_MILLIS,
                    Long.MIN_VALUE);
            if (detectedAtEpochMillis == Long.MIN_VALUE) {
                return null;
            }

            try {
                return freshEntryOrNull(
                        packageName,
                        statusCode,
                        Instant.ofEpochMilli(detectedAtEpochMillis),
                        maxAgeMillis,
                        now);
            } catch (DateTimeException | IllegalArgumentException exception) {
                return null;
            }
        }
    }

    final class InMemoryLastForegroundStore implements LastForegroundStore {
        private Entry entry;

        @Override
        public synchronized void save(String packageName, String statusCode, Instant detectedAt) {
            entry = new Entry(packageName, statusCode, detectedAt);
        }

        @Override
        public synchronized Entry readFresh(long maxAgeMillis, Instant now) {
            if (entry == null) {
                return null;
            }
            return freshEntryOrNull(
                    entry.packageName(),
                    entry.statusCode(),
                    entry.detectedAt(),
                    maxAgeMillis,
                    now);
        }
    }

    final class NoOpLastForegroundStore implements LastForegroundStore {
        private static final NoOpLastForegroundStore INSTANCE = new NoOpLastForegroundStore();

        private NoOpLastForegroundStore() {
        }

        @Override
        public void save(String packageName, String statusCode, Instant detectedAt) {
        }

        @Override
        public Entry readFresh(long maxAgeMillis, Instant now) {
            return null;
        }
    }

    private static Entry freshEntryOrNull(
            String packageName,
            String statusCode,
            Instant detectedAt,
            long maxAgeMillis,
            Instant now) {
        Objects.requireNonNull(now, "now");
        Entry entry = new Entry(packageName, statusCode, detectedAt);
        long ageMillis = now.toEpochMilli() - entry.detectedAt().toEpochMilli();
        if (ageMillis < 0L) {
            return null;
        }
        if (ageMillis > Math.max(0L, maxAgeMillis)) {
            return null;
        }
        return entry;
    }

    private static String requireNonBlank(String value, String fieldName) {
        String trimmed = Objects.requireNonNull(value, fieldName).trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return trimmed;
    }
}
