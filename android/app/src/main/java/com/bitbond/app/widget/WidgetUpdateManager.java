package com.bitbond.app.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;

import com.bitbond.app.MainActivity;
import com.bitbond.app.R;
import com.bitbond.app.status.StatusMapper;

public final class WidgetUpdateManager {
    public static final String ACTION_OPEN_APP = "com.bitbond.app.widget.OPEN_APP";
    public static final String ACTION_REFRESH = "com.bitbond.app.widget.REFRESH";

    private WidgetUpdateManager() {
    }

    public static WidgetRenderState renderState(WidgetStatusSnapshot snapshot) {
        if (snapshot == null) {
            return new WidgetRenderState(false, "offline", "offline", "", false);
        }

        return new WidgetRenderState(
                snapshot.paired(),
                snapshot.statusCode(),
                snapshot.statusCode(),
                snapshot.updatedAt() == null ? "" : snapshot.updatedAt(),
                snapshot.sharing());
    }

    public static WidgetAction actionFor(String action) {
        if (ACTION_OPEN_APP.equals(action)) {
            return WidgetAction.OPEN_APP;
        }
        if (ACTION_REFRESH.equals(action)) {
            return WidgetAction.REFRESH;
        }
        return WidgetAction.UPDATE;
    }

    public static void updateAllWidgets(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        int[] ids = appWidgetManager.getAppWidgetIds(new ComponentName(context, BitBondWidgetProvider.class));
        updateWidgets(context, appWidgetManager, ids);
    }

    public static void refreshAllWidgetsAsync(Context context) {
        Context appContext = context.getApplicationContext();
        new Thread(() -> {
            WidgetStatusRefreshCoordinator.refreshFromNetwork(appContext);
            updateAllWidgets(appContext);
        }, "BitBondWidgetRefresh").start();
    }

    public static void updateWidgets(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        WidgetRenderState renderState = renderState(new WidgetStatusCache(context).read());
        for (int appWidgetId : appWidgetIds) {
            appWidgetManager.updateAppWidget(appWidgetId, remoteViews(context, renderState));
        }
    }

    private static RemoteViews remoteViews(Context context, WidgetRenderState state) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.bitbond_status_widget);
        views.setTextViewText(R.id.widget_status_code, state.statusCode());
        views.setTextViewText(R.id.widget_status_updated_at, state.updatedAt().isEmpty() ? "waiting" : state.updatedAt());
        views.setTextViewText(R.id.widget_status_sharing, state.sharing() ? "sharing" : "paused");
        views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(context));
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshPendingIntent(context));
        return views;
    }

    private static PendingIntent openAppPendingIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class)
                .setAction(ACTION_OPEN_APP);
        return PendingIntent.getActivity(context, 0, intent, pendingIntentFlags());
    }

    private static PendingIntent refreshPendingIntent(Context context) {
        Intent intent = new Intent(context, BitBondWidgetProvider.class)
                .setAction(ACTION_REFRESH);
        return PendingIntent.getBroadcast(context, 1, intent, pendingIntentFlags());
    }

    private static int pendingIntentFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }

    public enum WidgetAction {
        OPEN_APP,
        REFRESH,
        UPDATE
    }
}
