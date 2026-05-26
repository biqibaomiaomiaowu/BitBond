package com.bitbond.app.status;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.text.TextUtils;

public final class AccessibilityAccessHelper implements AccessibilityAccessGateway {
    private final Context context;
    private final ComponentName serviceComponent;

    public AccessibilityAccessHelper(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context is required");
        }

        this.context = context.getApplicationContext();
        serviceComponent = new ComponentName(this.context, StatusAccessibilityService.class);
    }

    @Override
    public boolean hasAccessibilityAccess() {
        try {
            if (Settings.Secure.getInt(
                    context.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED,
                    0) != 1) {
                return false;
            }

            String enabledServices = Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabledServices == null || enabledServices.trim().isEmpty()) {
                return false;
            }

            TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
            splitter.setString(enabledServices);
            while (splitter.hasNext()) {
                ComponentName enabledComponent = ComponentName.unflattenFromString(splitter.next());
                if (serviceComponent.equals(enabledComponent)) {
                    return true;
                }
            }
        } catch (RuntimeException exception) {
            return false;
        }

        return false;
    }

    @Override
    public void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
