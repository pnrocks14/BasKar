package app.pratyahara.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import app.pratyahara.MainActivity
import app.pratyahara.R
import app.pratyahara.core.lock.FocusHours
import app.pratyahara.core.lock.LockState
import app.pratyahara.engine

/** Home-screen widget with today's Reels/Shorts minutes left. Tapping it opens Pratyahara. */
class MinutesWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)

    companion object {
        /** What the widget shows right now: the big value and the line under it. */
        fun text(context: Context): Pair<String, String> {
            val engine = context.engine
            return when (val s = engine.lockState()) {
                is LockState.Allowed -> "${(s.remainingSeconds + 59) / 60} min" to "of Reels left today"
                is LockState.BudgetLocked -> "0 min" to "Reels are done for today"
                is LockState.TaskLocked -> "Paused" to "Write today's task to open Reels"
                is LockState.Cooldown -> "Breathe" to "Reels open in a few seconds"
                is LockState.FocusLocked -> "Focus" to "Reels closed until ${FocusHours.format(s.endMinute)}"
                LockState.Disabled -> "Off" to "Blocking is switched off"
            }
        }

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, MinutesWidget::class.java))
            if (ids.isEmpty()) return
            val (value, label) = text(context)
            val open = PendingIntent.getActivity(
                context, 10, MainActivity.intent(context, "home"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val views = RemoteViews(context.packageName, R.layout.widget_minutes).apply {
                setTextViewText(R.id.widget_value, value)
                setTextViewText(R.id.widget_label, label)
                setOnClickPendingIntent(R.id.widget_root, open)
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
