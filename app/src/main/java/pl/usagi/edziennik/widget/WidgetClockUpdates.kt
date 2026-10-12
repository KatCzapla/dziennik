package pl.usagi.edziennik.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetReceiver

internal fun isWidgetClockBroadcast(action: String?): Boolean = action in setOf(
    Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_BOOT_COMPLETED,
)

internal fun handleWidgetClockBroadcast(
    context: Context,
    intent: Intent,
    receiver: Class<out GlanceAppWidgetReceiver>,
): Boolean {
    if (!isWidgetClockBroadcast(intent.action)) return false
    val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, receiver))
    if (ids.isNotEmpty()) enqueueLocalTimetableWidgetUpdate(context)
    return true
}
