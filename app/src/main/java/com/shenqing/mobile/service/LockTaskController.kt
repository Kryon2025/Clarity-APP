package com.shenqing.mobile.service

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context

object LockTaskController {
    fun isDeviceOwner(context: Context): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
        return dpm.isDeviceOwnerApp(context.packageName)
    }

    fun start(activity: Activity) {
        val dpm = activity.getSystemService(DevicePolicyManager::class.java) ?: return
        if (!dpm.isDeviceOwnerApp(activity.packageName)) return
        val admin = ComponentName(activity, ShenQingDeviceAdminReceiver::class.java)
        runCatching {
            dpm.setLockTaskPackages(admin, arrayOf(activity.packageName))
            activity.startLockTask()
        }
    }

    fun stop(activity: Activity) {
        if (isDeviceOwner(activity)) {
            runCatching { activity.stopLockTask() }
        }
    }
}
