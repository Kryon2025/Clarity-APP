package com.shenqing.mobile.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.shenqing.mobile.FocusActivity
import com.shenqing.mobile.MainActivity
import com.shenqing.mobile.R
import com.shenqing.mobile.data.AppRepository
import com.shenqing.mobile.data.Tier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FocusService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ticker: Job? = null
    private lateinit var repo: AppRepository

    override fun onCreate() {
        super.onCreate()
        repo = AppRepository.get(this)
        createChannel()
        ticker = scope.launch {
            while (isActive) {
                val active = repo.active.value
                if (active == null) {
                    stopSelf()
                    break
                }
                if (!active.paused && active.remainingSeconds() <= 0) {
                    repo.finishActive(completed = true, now = active.endsAt)
                    stopSelf()
                    break
                }
                updateNotification(active)
                delay(1000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService 后必须尽快 startForeground，即使随后结束。
        startForeground(NOTIF_ID, buildNotification())
        when (intent?.action) {
            ACTION_START -> {
                if (repo.active.value == null) {
                    val space = repo.spaceById(intent.getStringExtra(EXTRA_SPACE_ID))
                    val minutes = intent.getIntExtra(EXTRA_MINUTES, space.minutes)
                    repo.startFocus(space, minutes, startService = false)
                }
                FocusAlarmReceiver.schedule(this, repo.active.value?.endsAt ?: 0L)
            }
            ACTION_STOP -> {
                repo.finishActive(completed = false)
                stopSelf()
            }
            ACTION_PAUSE -> repo.pause()
            ACTION_RESUME -> repo.resume()
            ACTION_CHECK -> {
                val active = repo.active.value
                if (active != null && !active.paused && active.remainingSeconds() <= 0) {
                    repo.finishActive(completed = true, now = active.endsAt)
                }
                if (repo.active.value == null) stopSelf()
            }
            else -> {
                if (repo.active.value == null) stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // 保持前台服务；若系统回收，START_STICKY 会重建。
    }

    override fun onDestroy() {
        ticker?.cancel()
        scope.cancel()
        FocusAlarmReceiver.cancel(this)
        super.onDestroy()
    }

    private fun createChannel() {
        LiveNotification.ensureChannels(this)
    }

    private fun buildNotification(): Notification {
        val active = repo.active.value
        val remaining = active?.remainingSeconds() ?: 0L
        val total = active?.totalSeconds?.coerceAtLeast(1L) ?: 1L
        val title = if (active == null) "深清" else active.tier.label + " · " + active.name
        val text = if (active == null) "准备开始" else formatTime(remaining)
        val openIntent = PendingIntent.getActivity(
            this, 1,
            Intent(this, FocusActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val actions = mutableListOf<Pair<String, PendingIntent>>()
        if (active != null && active.tier.canPause) {
            val pauseLabel = if (active.paused) "继续" else "暂停"
            val action = if (active.paused) ACTION_RESUME else ACTION_PAUSE
            actions += pauseLabel to PendingIntent.getService(
                this, 2, Intent(this, FocusService::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        if (active != null && !active.tier.locked) {
            actions += "结束" to PendingIntent.getService(
                this, 3, Intent(this, FocusService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        return LiveNotification.build(
            context = this,
            channel = LiveNotification.CHANNEL_FOCUS,
            title = title,
            text = text,
            progress = (total - remaining).toInt().coerceIn(0, total.toInt()),
            max = total.toInt(),
            shortCriticalText = title + " " + formatTime(remaining),
            contentIntent = openIntent,
            actions = actions,
        )
    }

    private fun updateNotification(active: com.shenqing.mobile.data.ActiveSession) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID, buildNotification())
    }

    private fun formatTime(seconds: Long): String {
        val total = seconds.coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    companion object {
        private const val CHANNEL_ID = "shenqing_focus"
        private const val NOTIF_ID = 4101
        const val ACTION_START = "com.shenqing.mobile.action.START"
        const val ACTION_STOP = "com.shenqing.mobile.action.STOP"
        const val ACTION_PAUSE = "com.shenqing.mobile.action.PAUSE"
        const val ACTION_RESUME = "com.shenqing.mobile.action.RESUME"
        const val ACTION_CHECK = "com.shenqing.mobile.action.CHECK"
        const val EXTRA_SPACE_ID = "space_id"
        const val EXTRA_MINUTES = "minutes"

        fun start(context: Context) {
            val intent = Intent(context, FocusService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, FocusService::class.java).setAction(ACTION_STOP)
            ContextCompat.startForegroundService(context, intent)
        }

        fun pause(context: Context) {
            // 暂停只更新状态；若服务未启动则启动一次以刷新通知。
            val intent = Intent(context, FocusService::class.java).setAction(ACTION_PAUSE)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun check(context: Context) {
            val intent = Intent(context, FocusService::class.java).setAction(ACTION_CHECK)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }
    }
}
