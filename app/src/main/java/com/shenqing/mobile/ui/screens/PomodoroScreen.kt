package com.shenqing.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shenqing.mobile.data.AppRepository
import com.shenqing.mobile.data.PomodoroPhase
import com.shenqing.mobile.ui.AppCard
import com.shenqing.mobile.ui.ProgressRing
import com.shenqing.mobile.ui.SectionHeader
import com.shenqing.mobile.ui.asCompose
import com.shenqing.mobile.ui.formatClock
import com.shenqing.mobile.ui.theme.LocalPalette

@Composable
fun PomodoroScreen(repo: AppRepository) {
    val palette = LocalPalette.current
    val state by repo.pomodoro.collectAsState()
    val settings by repo.settings.collectAsState()
    var selectedWork by remember(settings.pomodoroWorkMinutes) { mutableIntStateOf(settings.pomodoroWorkMinutes) }
    var selectedRounds by remember(settings.pomodoroRounds) { mutableIntStateOf(settings.pomodoroRounds) }
    val remaining = state.remaining()
    val total = state.totalSeconds.coerceAtLeast(1)
    val progress = if (state.phase == PomodoroPhase.IDLE) 0f else (total - remaining).toFloat() / total

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp, 12.dp, 20.dp, 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SectionHeader("清番", "置顶番茄钟 · 专注 / 短休息 / 长休息")
        }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (state.phase == PomodoroPhase.IDLE) "准备开始" else state.phaseLabel,
                        color = palette.textSecondary.asCompose(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(12.dp))
                    ProgressRing(
                        progress = progress,
                        size = 210.dp,
                        stroke = 10.dp,
                        label = formatClock(remaining.toLong()),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (state.phase == PomodoroPhase.IDLE) "自定番茄数 · 25 分钟专注 / 5 分钟休息"
                        else "第 " + state.round + " / " + state.totalRounds + " 个番茄 · " + (if (state.running) "运行中" else "已暂停"),
                        color = palette.textTertiary.asCompose(),
                        fontSize = 10.sp,
                    )
                    Spacer(Modifier.height(16.dp))
                    if (state.phase == PomodoroPhase.IDLE) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(15, 25, 45, 60).forEach { minutes ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (selectedWork == minutes) Color(palette.accent).copy(alpha = 0.22f)
                                            else Color.White.copy(alpha = 0.06f)
                                        )
                                        .clickable { selectedWork = minutes }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        "$minutes 分",
                                        color = if (selectedWork == minutes) palette.accent.asCompose() else palette.textSecondary.asCompose(),
                                        fontSize = 10.sp,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1, 2, 3, 4, 6, 8).forEach { count ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (selectedRounds == count) Color(palette.accentSoft).copy(alpha = 0.24f)
                                            else Color.White.copy(alpha = 0.06f)
                                        )
                                        .clickable { selectedRounds = count }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        "$count 个",
                                        color = if (selectedRounds == count) palette.accent.asCompose() else palette.textSecondary.asCompose(),
                                        fontSize = 10.sp,
                                    )
                                }
                            }
                        }
                        Text("本轮共 " + selectedRounds + " 个番茄，完成即结束", color = palette.textTertiary.asCompose(), fontSize = 8.sp, modifier = Modifier.padding(top = 6.dp))
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = { repo.startPomodoro(selectedWork, totalRounds = selectedRounds) },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.accent.asCompose(),
                                contentColor = palette.onAccent.asCompose(),
                            ),
                        ) { Text("开始清番", fontSize = 12.sp) }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { if (state.running) repo.pausePomodoro() else repo.resumePomodoro() },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = palette.accent.asCompose(),
                                    contentColor = palette.onAccent.asCompose(),
                                ),
                            ) { Text(if (state.running) "暂停" else "继续", fontSize = 12.sp) }
                            OutlinedButton(onClick = { repo.skipPomodoro() }, shape = RoundedCornerShape(16.dp)) {
                                Text("跳过", fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { repo.stopPomodoro() }, shape = RoundedCornerShape(16.dp)) {
                            Text("退出清番", fontSize = 11.sp, color = palette.textTertiary.asCompose())
                        }
                    }
                }
            }
        }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("节奏", color = palette.textPrimary.asCompose(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "专注 " + state.workMinutes + " 分钟 · 短休息 " + state.shortBreakMinutes +
                            " 分钟 · 每 " + state.roundsBeforeLong + " 轮长休息 " + state.longBreakMinutes + " 分钟 · 共 " + state.totalRounds + " 个番茄",
                        color = palette.textSecondary.asCompose(),
                        fontSize = 10.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "清番与锁机独立；计时通知会尝试进入 ColorOS 流体云 / 灵动岛（Android 16 Live Updates）。",
                        color = palette.textTertiary.asCompose(),
                        fontSize = 8.sp,
                    )
                }
            }
        }
        item {
            Text(
                "提示：Focus 期间切到其他应用，深清会按锁机规则拦截；清番本身不强制锁机。",
                color = palette.textTertiary.asCompose(),
                fontSize = 8.sp,
                fontFamily = FontFamily.Default,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}
