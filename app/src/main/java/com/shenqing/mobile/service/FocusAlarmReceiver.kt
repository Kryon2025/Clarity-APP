package com.shenqing.mobile.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class FocusAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        FocusService.check(context)
    }

    companion object {
        private const val REQUEST = 4201

        fun schedule(context: Context, endsAt: Long) {
            if (endsAt <= 0L) return
            val alarm = context.getSystemService(AlarmManager::class.java) ?: return
            val pending = pendingIntent(context)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarm.canScheduleExactAlarms()) {
                    alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, pending)
                } else {
                    alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, pending)
                }
            } catch (_: SecurityException) {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, pending)
            }
        }

        fun cancel(context: Context) {
            val alarm = context.getSystemService(AlarmManager::class.java) ?: return
            alarm.cancel(pendingIntent(context))
        }

        private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, REQUEST,
            Intent(context, FocusAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
