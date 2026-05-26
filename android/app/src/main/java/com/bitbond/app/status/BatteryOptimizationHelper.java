package com.bitbond.app.status;

import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.provider.Settings;

public final class BatteryOptimizationHelper implements BatteryOptimizationGateway {
    private final Context context;

    public BatteryOptimizationHelper(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context is required");
        }

        this.context = context.getApplicationContext();
    }

    @Override
    public boolean isIgnoringBatteryOptimizations() {
        try {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return powerManager != null
                    && powerManager.isIgnoringBatteryOptimizations(context.getPackageName());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public void openBatteryOptimizationSettings() {
        Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
