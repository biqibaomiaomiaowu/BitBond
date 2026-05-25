package com.bitbond.app.status;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class StatusMapper {
    public static final String DEFAULT_STATUS_CODE = "online";

    private static final Set<String> ALLOWED_STATUS_CODES = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(
                    "short_video",
                    "watching_show",
                    "reading",
                    "music",
                    "gaming",
                    "social",
                    "online",
                    "resting",
                    "offline",
                    "paused")));

    private final Map<String, String> statusByPackage;

    private StatusMapper(Map<String, String> statusByPackage) {
        this.statusByPackage = Collections.unmodifiableMap(new HashMap<>(statusByPackage));
    }

    public static StatusMapper fromJson(String rawJson) {
        if (rawJson == null || rawJson.trim().isEmpty()) {
            throw new IllegalArgumentException("Status map JSON is required");
        }

        try {
            JSONArray entries = new JSONArray(rawJson);
            Map<String, String> statusByPackage = new HashMap<>();

            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.getJSONObject(i);
                validateEntryShape(entry);

                String packageName = entry.getString("package").trim();
                String statusCode = entry.getString("statusCode").trim();
                if (packageName.isEmpty()) {
                    throw new IllegalArgumentException("Status map package is required");
                }
                validateStatusCode(statusCode);

                statusByPackage.put(packageName, statusCode);
            }

            return new StatusMapper(statusByPackage);
        } catch (JSONException exception) {
            throw new IllegalArgumentException("Invalid status map JSON", exception);
        }
    }

    public String mapPackageName(String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) {
            return DEFAULT_STATUS_CODE;
        }

        return statusByPackage.getOrDefault(packageName.trim(), DEFAULT_STATUS_CODE);
    }

    public static Set<String> allowedStatusCodes() {
        return ALLOWED_STATUS_CODES;
    }

    private static void validateEntryShape(JSONObject entry) {
        if (entry.length() != 2 || !entry.has("package") || !entry.has("statusCode")) {
            throw new IllegalArgumentException("Status map entries must only contain package and statusCode");
        }
    }

    private static void validateStatusCode(String statusCode) {
        if (!ALLOWED_STATUS_CODES.contains(statusCode)) {
            throw new IllegalArgumentException("Unsupported statusCode: " + statusCode);
        }
    }
}
