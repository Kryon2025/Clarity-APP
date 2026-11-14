package com.shenqing.mobile.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import com.shenqing.mobile.FocusActivity
import com.shenqing.mobile.data.AppRepository
import com.shenqing.mobile.data.Tier
import com.shenqing.mobile.data.ThemeRepository
import kotlin.math.min

@SuppressLint("ViewConstructor")
class LockOverlayView(private val host: Context) : View(host) {
    private val repo = AppRepository.get(host)
    private val theme = ThemeRepository.get(host).current.value
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val handler = Handler(Looper.getMainLooper())
    private var taps = 0
    private var tapStarted = 0L
    private var showExit = false
    private val reopenRect = RectF()
    private val continueRect = RectF()
    private val stopRect = RectF()

    private val ticker = object : Runnable {
        override fun run() {
            invalidate()
            handler.postDelayed(this, 500)
        }
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = true
        isFocusable = true
        handler.post(ticker)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val active = repo.active.value
        if (active == null) {
            handler.post { (context as? LockOverlayHost)?.hideOverlay() }
            return
        }
        val w = width.toFloat()
        val h = height.toFloat()
        paint.shader = LinearGradient(0f, 0f, w, h, theme.bgTop, theme.bgBottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        paint.color = theme.textTertiary
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = w * 0.035f
        canvas.drawText(active.tier.english + " · " + active.tier.label, w / 2, h * 0.16f, paint)

        paint.color = theme.textPrimary
        paint.textSize = w * 0.075f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(active.name, w / 2, h * 0.25f, paint)

        val centerY = h * 0.47f
        val radius = min(w * 0.34f, h * 0.22f)
        val oval = RectF(w / 2 - radius, centerY - radius, w / 2 + radius, centerY + radius)
        ringPaint.strokeWidth = w * 0.018f
        ringPaint.color = withAlpha(theme.textPrimary, 40)
        canvas.drawArc(oval, 0f, 360f, false, ringPaint)
        val progress = ((active.totalSeconds - active.remainingSeconds()).toFloat() / active.totalSeconds.coerceAtLeast(1)).coerceIn(0f, 1f)
        ringPaint.color = theme.accent
        canvas.drawArc(oval, -90f, 360f * progress, false, ringPaint)

        paint.color = theme.textPrimary
        paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        paint.textSize = w * 0.18f
        canvas.drawText(formatTime(active.remainingSeconds()), w / 2, centerY + paint.textSize * 0.28f, paint)

        paint.typeface = Typeface.DEFAULT
        paint.color = theme.textSecondary
        paint.textSize = w * 0.04f
        canvas.drawText("时间没到，门不开", w / 2, h * 0.69f, paint)

        if (showExit) {
            paint.color = withAlpha(theme.textPrimary, 35)
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.color = theme.textPrimary
            paint.textSize = w * 0.06f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("真的要提前结束？", w / 2, h * 0.42f, paint)
            paint.typeface = Typeface.DEFAULT
            paint.textSize = w * 0.035f
            paint.color = theme.textSecondary
            canvas.drawText("深清与覆清本不提供提前结束，结束后记为中断。", w / 2, h * 0.47f, paint)
            continueRect.set(w * 0.08f, h * 0.52f, w * 0.48f, h * 0.58f)
            stopRect.set(w * 0.52f, h * 0.52f, w * 0.92f, h * 0.58f)
            drawButton(canvas, continueRect, "继续专注", theme.cardBg, theme.textPrimary)
            drawButton(canvas, stopRect, "仍然结束", withAlpha(theme.danger, 40), theme.danger)
        } else {
            paint.typeface = Typeface.DEFAULT
            paint.color = theme.textTertiary
            paint.textSize = w * 0.032f
            canvas.drawText("连点时间 10 次可退出 · 当前 " + taps + "/10", w / 2, h * 0.77f, paint)
            reopenRect.set(w * 0.25f, h * 0.82f, w * 0.75f, h * 0.88f)
            drawButton(canvas, reopenRect, "回到深清", withAlpha(theme.accent, 30), theme.accent)
        }
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawButton(canvas: Canvas, rect: RectF, text: String, bg: Int, fg: Int) {
        paint.color = bg
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, paint)
        paint.color = fg
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = width * 0.038f
        val cy = rect.centerY() - (paint.descent() + paint.ascent()) / 2
        canvas.drawText(text, rect.centerX(), cy, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return true
        val x = event.x
        val y = event.y
        if (showExit) {
            if (continueRect.contains(x, y)) {
                showExit = false
                taps = 0
                invalidate()
            } else if (stopRect.contains(x, y)) {
                repo.stopFocus(forced = true)
                (context as? LockOverlayHost)?.hideOverlay()
            }
            return true
        }
        if (reopenRect.contains(x, y)) {
            openFocus()
            return true
        }
        // 连点时间区域：以屏幕中部为界
        if (y > height * 0.2f && y < height * 0.7f) {
            val now = System.currentTimeMillis()
            if (now - tapStarted > 6000) {
                tapStarted = now
                taps = 0
            }
            taps += 1
            if (taps >= 10) {
                showExit = true
                taps = 0
            }
            invalidate()
        }
        return true
    }

    private fun openFocus() {
        runCatching {
            context.startActivity(
                Intent(context, FocusActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }
    }

    private fun formatTime(seconds: Long): String {
        val total = seconds.coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
    }
}

interface LockOverlayHost {
    fun hideOverlay()
}
