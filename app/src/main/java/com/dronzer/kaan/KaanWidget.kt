package com.dronzer.kaan

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlin.math.roundToInt

/** Home screen widget. Shows today's budget for Pro users. */
class KaanWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context, manager, ids)
    }

    companion object {
        fun refresh(ctx: Context) {
            val manager = AppWidgetManager.getInstance(ctx) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(ctx, KaanWidget::class.java))
            if (ids.isNotEmpty()) render(ctx, manager, ids)
        }

        private fun render(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
            val store = Store(ctx)
            val views = RemoteViews(ctx.packageName, R.layout.widget)
            if (store.isPro) {
                views.setTextViewText(R.id.widget_value, "${(store.dose() * 100).roundToInt()}%")
                views.setTextViewText(R.id.widget_caption, "of today's hearing budget")
            } else {
                views.setTextViewText(R.id.widget_value, "Kaan Pro")
                views.setTextViewText(R.id.widget_caption, "Open Kaan to unlock the widget")
            }
            val open = PendingIntent.getActivity(
                ctx, 3,
                Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            ids.forEach { manager.updateAppWidget(it, views) }
        }
    }
}
