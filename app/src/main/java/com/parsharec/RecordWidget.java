package com.parsharec;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/** Home-screen widget: shows the current Parsha and starts recording into it with one tap. */
public class RecordWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context c, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) mgr.updateAppWidget(id, build(c));
    }

    static void refreshAll(Context c) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(c);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(c, RecordWidget.class));
        if (ids.length > 0) for (int id : ids) mgr.updateAppWidget(id, build(c));
    }

    private static RemoteViews build(Context c) {
        boolean israel = c.getSharedPreferences("prefs", Context.MODE_PRIVATE).getBoolean("israel", false);
        Catalog.Folder f = Catalog.getOrGeneral(Catalog.currentKey(israel));
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_record);
        v.setTextViewText(R.id.widget_name, f.en);
        v.setTextViewText(R.id.widget_hebrew, f.he);
        Intent i = new Intent("com.parsharec.RECORD_NOW").setClass(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        v.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(c, 0, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        return v;
    }
}
