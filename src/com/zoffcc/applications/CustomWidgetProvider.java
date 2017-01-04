package com.zoffcc.applications;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import org.smssecure.smssecure.R;

/**
 * Created on 01.09.2016.
 */
public class CustomWidgetProvider extends AppWidgetProvider
{

    // ---- CONFIG ----
    // ---- CONFIG ----
    // ---- CONFIG ----
    public static final int MAX_COUNT = 20;
    public static Class ToOpenActivity = org.smssecure.smssecure.ConversationListActivity.class;
    public static String baseClass = "org.smssecure.smssecure";
    // ---- CONFIG ----
    // ---- CONFIG ----
    // ---- CONFIG ----

    @Override
    public void onUpdate(final Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds)
    {
        for (int widgetId : appWidgetIds)
        {
            Intent intent = new Intent(context, ToOpenActivity);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.badge_widget);

            int unreadCount = BadgeWidgetIntentReceiver.unreadCount;
            if (unreadCount <= 0)
            {
                // Hide TextView for unread count
                remoteViews.setViewVisibility(R.id.widget_number, View.GONE);
            }
            else
            {
                remoteViews.setViewVisibility(R.id.widget_number, View.VISIBLE);

                String displayCount = (unreadCount <= MAX_COUNT) ? String.valueOf(unreadCount) : String.valueOf(MAX_COUNT) + "+";
                remoteViews.setTextViewText(R.id.widget_number, displayCount);
            }

            remoteViews.setOnClickPendingIntent(R.id.widget_icon, pendingIntent);
            appWidgetManager.updateAppWidget(widgetId, remoteViews);
        }
    }
}
