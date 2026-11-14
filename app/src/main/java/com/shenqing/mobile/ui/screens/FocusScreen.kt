package com.shenqing.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shenqing.mobile.data.AppRepository
import com.shenqing.mobile.ui.ProgressRing
import com.shenqing.mobile.ui.asCompose
import com.shenqing.mobile.ui.formatClock
import com.shenqing.mobile.ui.theme.LocalPalette
import kotlinx.coroutines.delay

@Composable
fun FocusScreen(
    repo: AppRepository,
    onMinimize: () -> Unit,
    onFinish: () -> Unit,
) {
    val palette = LocalPalette.current
    val active by repo.active.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showExit by remember { mutableStateOf(false) }
    var taps by remember { mutableIntStateOf(0) }
    var tapStarted by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(400)
        }
    }
    LaunchedEffect(active) {
        if (active == null) onFinish()
    }
    val session = active ?: return
    val remaining = session.remainingSeconds(now)
    val progress = ((session.totalSeconds - remaining).toFloat() / session.totalSeconds.coerceAtLeast(1)).coerceIn(0f, 1f)
    val doneMinutes = ((session.totalSeconds - remaining) / 60).toInt().coerceAtLeast(0)
    val remainMinutes = ((remaining + 59) / 60).toInt().coerceAtLeast(0)

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(
                    Color(palette.color("overlayTop", palette.bgTop)),
                    Color(palette.color("overlayBottom", palette.bgBottom)),
                )
            )
        )
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (!session.tier.locked) {
                    IconButton(onClick = onMinimize) {
                        Icon(Icons.Outlined.Close, contentDescription = "收起", tint = palette.textSecondary.asCompose())
                    }
                } else {
                    Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Lock, contentDescription = null, tint = palette.textTertiary.asCompose(), modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(session.tier.english + "  " + session.tier.label, color = palette.accent.asCompose(), fontSize = 10.sp, letterSpacing = 3.sp)
                Spacer(Modifier.weight(1f))
                Text(formatClock(remaining), color = palette.textTertiary.asCompose(), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(session.name, color = palette.textPrimary.asCompose(), fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                Text(if (session.paused) "已暂停 · 时间停在这里" else session.tier.tagline, color = palette.textTertiary.asCompose(), fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
                Box(
                    Modifier.padding(top = 18.dp).pointerInput(session, session.tier.locked) {
                        detectTapGestures {
                            if (!session.tier.locked) return@detectTapGestures
                            val current = System.currentTimeMillis()
                            if (current - tapStarted > 6000) { tapStarted = current; taps = 0 }
                            taps += 1
                            if (taps >= 10) { taps = 0; showExit = true }
                        }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    ProgressRing(progress, size = 250.dp, stroke = 10.dp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(formatClock(remaining), color = palette.textPrimary.asCompose(), fontSize = 54.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Text((progress * 100).toInt().toString() + "%", color = palette.textTertiary.asCompose(), fontSize = 10.sp, letterSpacing = 2.sp)
                    }
                }
                Text("只做眼前这一件事，其余的等会儿再说。", color = palette.textSecondary.asCompose(), fontSize = 11.sp, modifier = Modifier.padding(top = 20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(top = 14.dp)) {
                    Text(doneMinutes.toString(), color = palette.textPrimary.asCompose(), fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text("分钟已专注", color = palette.textTertiary.asCompose(), fontSize = 9.sp, modifier = Modifier.align(Alignment.CenterVertically))
                    Text(remainMinutes.toString(), color = palette.textPrimary.asCompose(), fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text("分钟剩余", color = palette.textTertiary.asCompose(), fontSize = 9.sp, modifier = Modifier.align(Alignment.CenterVertically))
                }
                if (session.tier.locked) {
                    Text("深清/覆清期间不可提前结束 · 连点时间 10 次", color = palette.textTertiary.asCompose(), fontSize = 9.sp, modifier = Modifier.padding(top = 14.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                if (session.tier.canPause) {
                    Button(
                        onClick = { if (session.paused) repo.resume() else repo.pause() },
                        modifier = Modifier.weight(1f).height(54.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f), contentColor = palette.textPrimary.asCompose()),
                    ) {
                        Icon(if (session.paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(if (session.paused) "继续" else "暂停", modifier = Modifier.padding(start = 6.dp), fontSize = 12.sp)
                    }
                }
                if (!session.tier.locked) {
                    Button(
                        onClick = { repo.stopFocus(false); onFinish() },
                        modifier = Modifier.weight(1f).height(54.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(palette.danger).copy(alpha = 0.14f), contentColor = palette.danger.asCompose()),
                    ) {
                        Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("结束专注", modifier = Modifier.padding(start = 6.dp), fontSize = 12.sp)
                    }
                }
            }
        }
    }

    if (showExit) {
        AlertDialog(
            onDismissRequest = { showExit = false },
            title = { Text("真的要提前结束？") },
            text = { Text("深清与覆清本不提供提前结束，结束后会记为「中断」，这段仍然算数。") },
            confirmButton = {
                TextButton(onClick = {
                    showExit = false
                    repo.stopFocus(forced = true)
                    onFinish()
                }) { Text("仍然结束") }
            },
            dismissButton = { TextButton(onClick = { showExit = false }) { Text("继续专注") } },
        )
    }
}
