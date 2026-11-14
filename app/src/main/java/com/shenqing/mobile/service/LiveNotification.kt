package com.shenqing.mobile.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * 持续计时通知：普通系统通知 + Android 16 Live Updates（ColorOS 流体云/灵动岛）尽力适配。
 *
 * 说明：OPPO/ColorOS 的“灵动岛/流体云”没有公开第三方本地 API。Android 16 (API 36)
 * 的 promoted ongoing notification 会被 Fluid Cloud 识别；这里用反射构建
 * Notification.ProgressStyle，避免把 minSdk/compileSdk 绑死在 36。旧系统回退普通进度通知。
 */
object LiveNotification {
    const val CHANNEL_FOCUS = "focus_live"
    const val CHANNEL_POMODORO = "pomodoro_live"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val focus = NotificationChannel(CHANNEL_FOCUS, "专注计时", NotificationManager.IMPORTANCE_LOW).apply {
            description = "覆清/深清/清番的持续倒计时"
            setShowBadge(false)
        }
        val pomodoro = NotificationChannel(CHANNEL_POMODORO, "清番计时", NotificationManager.IMPORTANCE_LOW).apply {
            description = "清番番茄钟的专注/休息倒计时"
            setShowBadge(false)
        }
        manager.createNotificationChannel(focus)
        manager.createNotificationChannel(pomodoro)
    }

    fun build(
        context: Context,
        channel: String,
        title: String,
        text: String,
        progress: Int,
        max: Int,
        shortCriticalText: String,
        contentIntent: PendingIntent?,
        actions: List<Pair<String, PendingIntent>> = emptyList(),
        category: String = Notification.CATEGORY_STOPWATCH,
    ): Notification {
        ensureChannels(context)
        return if (Build.VERSION.SDK_INT >= 36) buildLive(
            context, channel, title, text, progress, max, shortCriticalText, contentIntent, actions, category,
        ) else buildCompat(
            context, channel, title, text, progress, max, contentIntent, actions, category,
        )
    }

    private fun buildCompat(
        context: Context,
        channel: String,
        title: String,
        text: String,
        progress: Int,
        max: Int,
        contentIntent: PendingIntent?,
        actions: List<Pair<String, PendingIntent>>,
        category: String,
    ): Notification {
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(category)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setProgress(max.coerceAtLeast(1), progress.coerceIn(0, max.coerceAtLeast(1)), false)
        contentIntent?.let { builder.setContentIntent(it) }
        actions.forEach { (label, intent) ->
            builder.addAction(android.R.drawable.ic_media_pause, label, intent)
        }
        return builder.build()
    }

    private fun buildLive(
        context: Context,
        channel: String,
        title: String,
        text: String,
        progress: Int,
        max: Int,
        shortCriticalText: String,
        contentIntent: PendingIntent?,
        actions: List<Pair<String, PendingIntent>>,
        category: String,
    ): Notification {
        val builder = Notification.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(category)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setProgress(max.coerceAtLeast(1), progress.coerceIn(0, max.coerceAtLeast(1)), false)
        contentIntent?.let { builder.setContentIntent(it) }
        actions.forEach { (label, intent) ->
            val icon = Icon.createWithResource(context, android.R.drawable.ic_media_pause)
            builder.addAction(Notification.Action.Builder(icon, label, intent).build())
        }
        try {
            val styleClass = Class.forName("android.app.Notification\$ProgressStyle")
            val style = styleClass.getDeclaredConstructor().newInstance()
            runCatching {
                styleClass.getMethod("setProgress", Int::class.javaPrimitiveType).invoke(style, progress)
            }
            runCatching {
                styleClass.getMethod("setStyledByProgress", Boolean::class.javaPrimitiveType).invoke(style, false)
            }
            runCatching {
                val styleBase = Class.forName("android.app.Notification\$Style")
                builder.javaClass.getMethod("setStyle", styleBase).invoke(builder, style)
            }
            runCatching {
                builder.javaClass.getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
                    .invoke(builder, true)
            }
            runCatching {
                builder.javaClass.getMethod("setShortCriticalText", CharSequence::class.java)
                    .invoke(builder, shortCriticalText.take(16))
            }
        } catch (_: Throwable) {
            // 旧 ColorOS / 非 API 36 回退普通通知
        }
        return builder.build()
    }
}
