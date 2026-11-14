package com.shenqing.mobile.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.shenqing.mobile.MainActivity
import com.shenqing.mobile.R
import com.shenqing.mobile.data.AppRepository
import com.shenqing.mobile.data.ThemeRepository

class FocusWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> updateOne(context, appWidgetManager, id) }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        super.onReceive(context, intent)
        if (intent?.action == ACTION_UPDATE) update(context)
    }

    companion object {
        const val ACTION_UPDATE = "com.shenqing.mobile.action.WIDGET_UPDATE"

        fun update(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, FocusWidgetProvider::class.java))
            ids.forEach { id -> updateOne(context, manager, id) }
        }

        private fun updateOne(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val repo = AppRepository.get(context)
            val themeRepo = ThemeRepository.get(context)
            val palette = themeRepo.effectivePalette()
            val stats = repo.stats.value
            val settings = repo.settings.value
            val space = repo.spaceById(settings.quickFocusSpaceId)
            val minutes = settings.quickFocusMinutes.coerceIn(5, 240)

            val views = RemoteViews(context.packageName, R.layout.widget_focus)
            views.setTextViewText(
                R.id.widget_counts,
                "今日 " + stats.todayCount + " 次 · 近 7 天 " + stats.recentCount + " 次",
            )
            views.setTextViewText(R.id.widget_tier, space.tier.english)
            views.setTextViewText(R.id.widget_quick, "快速专注 " + minutes + " 分钟")

            val isLight = palette.mode == "light"
            val isOcean = palette.mode == "ocean"
            views.setTextColor(R.id.widget_title, palette.textPrimary)
            views.setTextColor(R.id.widget_counts, palette.textSecondary)
            views.setTextColor(R.id.widget_tier, palette.textTertiary)
            views.setTextColor(R.id.widget_quick, palette.onAccent)
            views.setTextColor(R.id.widget_open, palette.textSecondary)
            views.setInt(
                R.id.widget_root, "setBackgroundResource",
                when {
                    isLight -> R.drawable.widget_bg_light
                    isOcean -> R.drawable.widget_bg_ocean
                    else -> R.drawable.widget_bg_solid
                },
            )
            views.setInt(
                R.id.widget_quick, "setBackgroundResource",
                when {
                    isLight -> R.drawable.widget_pill_light
                    isOcean -> R.drawable.widget_pill_ocean
                    else -> R.drawable.widget_pill_solid
                },
            )

            val quickIntent = Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_QUICK_FOCUS
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("space_id", space.id)
                putExtra("minutes", minutes)
            }
            val quickPending = PendingIntent.getActivity(
                context, 1001, quickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_quick, quickPending)

            val openIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            val openPending = PendingIntent.getActivity(
                context, 1002, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_open, openPending)
            views.setOnClickPendingIntent(R.id.widget_root, openPending)

            runCatching { manager.updateAppWidget(widgetId, views) }
        }
    }
}
