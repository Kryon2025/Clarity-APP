package com.shenqing.mobile.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shenqing.mobile.data.AppRepository

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED && intent?.action != "android.intent.action.QUICKBOOT_POWERON") return
        val repo = AppRepository.get(context)
        val active = repo.active.value
        if (active != null && !active.paused) {
            runCatching { FocusService.start(context) }
            FocusAlarmReceiver.schedule(context, active.endsAt)
        }
    }
}
