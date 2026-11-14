package com.shenqing.mobile.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.shenqing.mobile.MainActivity
import com.shenqing.mobile.data.AppRepository
import com.shenqing.mobile.data.PomodoroPhase

class PomodoroService : Service() {
    private lateinit var repo: AppRepository
    private var lastKey: String = ""
    @Volatile private var tickerStarted = false
    private var tickerThread: Thread? = null

    override fun onCreate() {
        super.onCreate()
        repo = AppRepository.get(this)
        LiveNotification.ensureChannels(this)
        startForeground(NOTIFICATION_ID, buildNotification(repo.pomodoro.value))
        startTicker()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> repo.pausePomodoro(false)
            ACTION_RESUME -> repo.resumePomodoro(false)
            ACTION_SKIP -> repo.skipPomodoro(false)
            ACTION_STOP -> {
                repo.clearPomodoro()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        val state = repo.pomodoro.value
        if (state.phase == PomodoroPhase.IDLE) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification(state))
        startTicker()
        return START_STICKY
    }

    private fun startTicker() {
        if (tickerStarted) return
        tickerStarted = true
        tickerThread = Thread {
            while (!Thread.currentThread().isInterrupted) {
                try {
                    Thread.sleep(1000L)
                    val before = repo.pomodoro.value
                    repo.tickPomodoro()
                    val after = repo.pomodoro.value
                    if (after.phase == PomodoroPhase.IDLE) {
                        stopSelf()
                        break
                    }
                    val key = after.phase.name + ":" + after.remaining() / 5 + ":" + after.running
                    if (key != lastKey || before.phase != after.phase) {
                        lastKey = key
                        notifyNotification(after)
                    }
                } catch (_: InterruptedException) {
                    break
                } catch (_: Throwable) {
                    // 服务不能因单次通知异常被杀
                }
            }
        }.apply { isDaemon = true; name = "pomodoro-ticker" }.also { it.start() }
    }

    private fun notifyNotification(state: com.shenqing.mobile.data.PomodoroState) {
        runCatching {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(state))
        }
    }

    private fun buildNotification(state: com.shenqing.mobile.data.PomodoroState): Notification {
        val remaining = state.remaining()
        val total = state.totalSeconds.coerceAtLeast(1)
        val title = "清番 · " + state.phaseLabel
        val text = if (state.phase == PomodoroPhase.IDLE) "番茄钟" else clock(remaining)
        val open = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("open_pomodoro", true)
        }
        val content = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val actions = if (state.phase == PomodoroPhase.IDLE) emptyList() else listOf(
            (if (state.running) "暂停" else "继续") to servicePending(if (state.running) ACTION_PAUSE else ACTION_RESUME, 1),
            "跳过" to servicePending(ACTION_SKIP, 2),
            "退出" to servicePending(ACTION_STOP, 3),
        )
        return LiveNotification.build(
            context = this,
            channel = LiveNotification.CHANNEL_POMODORO,
            title = title,
            text = text,
            progress = (total - remaining).coerceIn(0, total),
            max = total,
            shortCriticalText = "清番 " + clock(remaining),
            contentIntent = content,
            actions = actions,
        )
    }

    private fun servicePending(action: String, request: Int): PendingIntent {
        val intent = Intent(this, PomodoroService::class.java).setAction(action)
        return PendingIntent.getService(this, request, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun clock(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        return "%02d:%02d".format(safe / 60, safe % 60)
    }

    override fun onDestroy() {
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        tickerStarted = false
        tickerThread?.interrupt()
        tickerThread = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 4302
        const val ACTION_PAUSE = "com.shenqing.mobile.pomodoro.PAUSE"
        const val ACTION_RESUME = "com.shenqing.mobile.pomodoro.RESUME"
        const val ACTION_SKIP = "com.shenqing.mobile.pomodoro.SKIP"
        const val ACTION_STOP = "com.shenqing.mobile.pomodoro.STOP"

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, PomodoroService::class.java).setAction("com.shenqing.mobile.pomodoro.START"),
                )
            }
        }

        fun pause(context: Context) {
            runCatching { context.startService(Intent(context, PomodoroService::class.java).setAction(ACTION_PAUSE)) }
        }

        fun stop(context: Context) {
            runCatching { context.startService(Intent(context, PomodoroService::class.java).setAction(ACTION_STOP)) }
        }
    }
}
