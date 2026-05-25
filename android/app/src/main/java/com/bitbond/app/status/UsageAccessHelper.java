package com.bitbond.app.status;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

public final class UsageAccessHelper implements UsageAccessGateway {
    private final Context context;

    public UsageAccessHelper(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context is required");
        }

        this.context = context.getApplicationContext();
    }

    @Override
    public boolean hasUsageAccess() {
        AppOpsManager appOpsManager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (appOpsManager == null) {
            return false;
        }

        int mode;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mode = appOpsManager.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    context.getApplicationInfo().uid,
                    context.getPackageName());
        } else {
            mode = appOpsManager.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    context.getApplicationInfo().uid,
                    context.getPackageName());
        }

        return mode == AppOpsManager.MODE_ALLOWED;
    }

    @Override
    public void openUsageAccessSettings() {
        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
