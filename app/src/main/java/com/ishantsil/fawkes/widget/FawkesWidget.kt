package com.ishantsil.fawkes.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.ishantsil.fawkes.MainActivity
import com.ishantsil.fawkes.R

/** Home-screen widget: tap to talk, shows the last thing Fawkes heard. */
class FawkesWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (widgetId in appWidgetIds) {
            appWidgetManager.updateAppWidget(widgetId, buildViews(context))
        }
    }

    private fun buildViews(context: Context): RemoteViews {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_LISTEN, true)
        }
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val lastHeard = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_HEARD, null)
        return RemoteViews(context.packageName, R.layout.widget_layout).apply {
            setTextViewText(R.id.widgetLastHeard, lastHeard ?: "Tap to talk to Fawkes")
            setOnClickPendingIntent(R.id.widgetMicButton, pending)
            setOnClickPendingIntent(R.id.widgetRoot, pending)
        }
    }

    companion object {
        private const val PREFS = "fawkes_widget"
        private const val KEY_LAST_HEARD = "last_heard"

        fun updateLastHeard(context: Context, text: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_LAST_HEARD, "\"$text\"").apply()
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, FawkesWidget::class.java),
            )
            if (ids.isNotEmpty()) {
                val views = FawkesWidget().buildViews(context)
                for (id in ids) manager.updateAppWidget(id, views)
            }
        }
    }
}
