package com.bitbond.app.widget;

import com.bitbond.app.status.StatusMapper;
import com.bitbond.app.status.StatusModels.PartnerStatus;

import java.time.Instant;
import java.util.Objects;

import org.json.JSONException;
import org.json.JSONObject;

public final class WidgetStatusSnapshot {
    private final boolean paired;
    private final String statusCode;
    private final String updatedAt;
    private final boolean sharing;

    public WidgetStatusSnapshot(boolean paired, String statusCode, String updatedAt, boolean sharing) {
        this.paired = paired;
        this.statusCode = normalizeStatusCode(statusCode);
        this.updatedAt = normalizeOptional(updatedAt);
        this.sharing = sharing;
    }

    public static WidgetStatusSnapshot fromPartnerStatus(PartnerStatus partnerStatus) {
        Objects.requireNonNull(partnerStatus, "partnerStatus");
        String statusCode = partnerStatus.statusCode();
        Instant statusUpdatedAt = partnerStatus.statusUpdatedAt();
        return new WidgetStatusSnapshot(
                partnerStatus.isPaired(),
                statusCode == null ? StatusMapper.DEFAULT_STATUS_CODE : statusCode,
                statusUpdatedAt == null ? null : statusUpdatedAt.toString(),
                !partnerStatus.isPaused());
    }

    public static WidgetStatusSnapshot fromJson(String rawJson) throws JSONException {
        JSONObject source = new JSONObject(rawJson);
        return new WidgetStatusSnapshot(
                source.optBoolean("paired", false),
                source.optString("statusCode", StatusMapper.DEFAULT_STATUS_CODE),
                source.isNull("updatedAt") ? null : source.optString("updatedAt", null),
                source.optBoolean("sharing", true));
    }

    public static WidgetStatusSnapshot fromWidgetStatusPayload(JSONObject source) {
        Objects.requireNonNull(source, "source");
        boolean paired = source.optBoolean("paired", false);
        boolean isPaused = source.optBoolean("isPaused", false);
        String statusCode = source.optString("statusCode", paired ? StatusMapper.DEFAULT_STATUS_CODE : "offline");
        String updatedAt = source.isNull("statusUpdatedAt")
                ? null
                : source.optString("statusUpdatedAt", null);
        return new WidgetStatusSnapshot(paired, statusCode, updatedAt, !isPaused);
    }

    public JSONObject toJson() throws JSONException {
        return new JSONObject()
                .put("paired", paired)
                .put("statusCode", statusCode)
                .put("updatedAt", updatedAt == null ? JSONObject.NULL : updatedAt)
                .put("sharing", sharing);
    }

    public boolean paired() {
        return paired;
    }

    public String statusCode() {
        return statusCode;
    }

    public String updatedAt() {
        return updatedAt;
    }

    public boolean sharing() {
        return sharing;
    }

    private static String normalizeStatusCode(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || !StatusMapper.allowedStatusCodes().contains(normalized)) {
            return StatusMapper.DEFAULT_STATUS_CODE;
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
