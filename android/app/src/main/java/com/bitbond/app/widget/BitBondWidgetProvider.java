package com.bitbond.app.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;

public final class BitBondWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        WidgetUpdateManager.updateWidgets(context, appWidgetManager, appWidgetIds);
        WidgetUpdateManager.refreshAllWidgetsAsync(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (WidgetUpdateManager.actionFor(intent == null ? null : intent.getAction())
                == WidgetUpdateManager.WidgetAction.REFRESH) {
            WidgetUpdateManager.refreshAllWidgetsAsync(context);
        }
    }
}
