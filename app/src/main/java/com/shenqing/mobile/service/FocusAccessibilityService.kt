package com.shenqing.mobile.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.shenqing.mobile.FocusActivity
import com.shenqing.mobile.data.AppRepository
import com.shenqing.mobile.data.Tier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FocusAccessibilityService : AccessibilityService(), LockOverlayHost {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repo: AppRepository
    private var overlayView: LockOverlayView? = null
    private var observeJob: Job? = null
    private var lastLaunchAt = 0L
    private var launcherPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        repo = AppRepository.get(this)
        launcherPackage = resolveLauncherPackage()
        observeJob = scope.launch {
            repo.active.collectLatest { active ->
                if (active == null || active.tier == Tier.LIGHT) hideOverlay()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val active = repo.active.value ?: run { hideOverlay(); return }
        if (active.tier == Tier.LIGHT) return
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == packageName) {
            hideOverlay()
            return
        }
        if (pkg == "com.android.systemui") {
            showOverlay()
            return
        }
        val space = repo.spaceById(active.spaceId)
        if (repo.isAllowed(space, pkg)) {
            hideOverlay()
            return
        }
        if (pkg == launcherPackage) {
            // 桌面上盖住，不主动拉起 Activity，避免 home 键来回闪。
            showOverlay()
            return
        }
        showOverlay()
        launchFocusThrottled()
    }

    override fun onInterrupt() {
        hideOverlay()
    }

    override fun onDestroy() {
        hideOverlay()
        observeJob?.cancel()
        scope.cancel()
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun hideOverlay() {
        val view = overlayView ?: return
        overlayView = null
        runCatching {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(view)
        }
    }

    private fun showOverlay() {
        val active = repo.active.value ?: return
        if (active.tier == Tier.LIGHT) return
        if (overlayView != null) return
        val view = LockOverlayView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
            },
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        runCatching {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).addView(view, params)
            overlayView = view
        }
    }

    private fun launchFocusThrottled() {
        val now = System.currentTimeMillis()
        if (now - lastLaunchAt < 1200L) return
        lastLaunchAt = now
        runCatching {
            startActivity(
                Intent(this, FocusActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }
    }

    private fun resolveLauncherPackage(): String? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }

    companion object {
        @Volatile var instance: FocusAccessibilityService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, FocusAccessibilityService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
        }

        fun openSettings(context: Context) {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
