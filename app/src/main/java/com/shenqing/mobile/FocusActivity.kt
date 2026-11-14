package com.shenqing.mobile

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.shenqing.mobile.data.AppRepository
import com.shenqing.mobile.data.ThemeRepository
import com.shenqing.mobile.service.FocusService
import com.shenqing.mobile.service.LockTaskController
import com.shenqing.mobile.ui.screens.FocusScreen
import com.shenqing.mobile.ui.theme.ShenQingTheme

class FocusActivity : ComponentActivity() {
    private lateinit var repo: AppRepository
    private lateinit var themeRepo: ThemeRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = AppRepository.get(this)
        themeRepo = ThemeRepository.get(this)
        if (repo.active.value == null) {
            finish()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (repo.active.value?.tier?.locked == true) LockTaskController.start(this)
        FocusService.start(this)
        setContent {
            val palette = themeRepo.current.collectAsState().value
            val fontScale = repo.fontScale.collectAsState().value
            ShenQingTheme(palette, fontScale) {
                FocusScreen(
                    repo = repo,
                    onMinimize = { finish() },
                    onFinish = { finishAndRemoveTask() },
                )
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (repo.active.value?.tier?.locked != true) finish()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (::repo.isInitialized && repo.active.value == null) finish()
    }

    override fun onDestroy() {
        if (::repo.isInitialized && repo.active.value == null) {
            LockTaskController.stop(this)
        }
        super.onDestroy()
    }
}
