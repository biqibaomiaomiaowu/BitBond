package com.bitbond.app.widget;

import android.content.Context;
import android.content.SharedPreferences;

import com.bitbond.app.status.StatusModels.PartnerStatus;

import java.util.Objects;

import org.json.JSONException;

public final class WidgetStatusCache implements WidgetStatusSink {
    private static final String PREFS_NAME = "bitbond_widget_status";
    private static final String KEY_SNAPSHOT = "snapshot";

    private final SharedPreferences preferences;

    public WidgetStatusCache(Context context) {
        this(Objects.requireNonNull(context, "context")
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE));
    }

    WidgetStatusCache(SharedPreferences preferences) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
    }

    @Override
    public void cachePartnerStatus(PartnerStatus partnerStatus) {
        write(WidgetStatusSnapshot.fromPartnerStatus(partnerStatus));
    }

    public WidgetStatusSnapshot read() {
        String rawJson = preferences.getString(KEY_SNAPSHOT, "");
        if (rawJson == null || rawJson.trim().isEmpty()) {
            return null;
        }
        try {
            return WidgetStatusSnapshot.fromJson(rawJson);
        } catch (JSONException exception) {
            return null;
        }
    }

    public void write(WidgetStatusSnapshot snapshot) {
        try {
            preferences.edit()
                    .putString(KEY_SNAPSHOT, Objects.requireNonNull(snapshot, "snapshot").toJson().toString())
                    .apply();
        } catch (JSONException exception) {
            // Widget cache is best-effort and should never break bridge flows.
        }
    }
}
