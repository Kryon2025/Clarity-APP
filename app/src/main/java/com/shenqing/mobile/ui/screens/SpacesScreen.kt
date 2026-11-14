package com.shenqing.mobile.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Anchor
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shenqing.mobile.data.ActiveSession
import com.shenqing.mobile.data.AppRepository
import com.shenqing.mobile.data.Space
import com.shenqing.mobile.data.Tier
import com.shenqing.mobile.ui.AppCard
import com.shenqing.mobile.ui.ProgressRing
import com.shenqing.mobile.ui.SectionHeader
import com.shenqing.mobile.ui.asCompose
import com.shenqing.mobile.ui.formatClock
import com.shenqing.mobile.ui.formatMinutes
import com.shenqing.mobile.ui.theme.LocalPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpacesScreen(
    repo: AppRepository,
    onStart: (Space, Int) -> Unit,
    onOpenFocus: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenPomodoro: () -> Unit,
) {
    val palette = LocalPalette.current
    val stats by repo.stats.collectAsState()
    val spaces by repo.spaces.collectAsState()
    val active by repo.active.collectAsState()
    val pomodoro by repo.pomodoro.collectAsState()
    val settings by repo.settings.collectAsState()
    var sheetSpace by remember { mutableStateOf<Space?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("深清", color = palette.textPrimary.asCompose(), fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text(
                        SimpleDateFormat("M月d日 · EEE", Locale.CHINA).format(Date()),
                        color = palette.textTertiary.asCompose(), fontSize = 11.sp,
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "设置", tint = palette.textSecondary.asCompose())
                }
            }
        }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("今日专注", color = palette.textTertiary.asCompose(), fontSize = 11.sp, letterSpacing = 2.sp)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                stats.todayMinutes.toString(),
                                color = palette.textPrimary.asCompose(),
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                            Text("分钟", color = palette.textSecondary.asCompose(), fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp, start = 4.dp))
                        }
                        Text(
                            "连续 " + stats.streakDays + " 天 · " + stats.sessionCount + " 段",
                            color = palette.textSecondary.asCompose(), fontSize = 10.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier.clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = 0.06f))
                                .border(1.dp, palette.cardBorder.asCompose(), RoundedCornerShape(99.dp))
                                .padding(horizontal = 9.dp, vertical = 4.dp),
                        ) {
                            Text("目标 120 分钟 · " + minOf(100, stats.todayMinutes * 100 / 120) + "%", color = palette.textTertiary.asCompose(), fontSize = 9.sp)
                        }
                    }
                    ProgressRing(
                        progress = (stats.todayMinutes / 120f).coerceIn(0f, 1f),
                        size = 100.dp, stroke = 7.dp,
                        label = minOf(100, stats.todayMinutes * 100 / 120).toString() + "%",
                    )
                }
            }
        }
        active?.let { session ->
            item { ActiveCard(session, palette, onOpenFocus) }
        }
        item {
            SectionHeader("清番", "番茄钟 · 自定本轮番茄数")
            AppCard(Modifier.fillMaxWidth().clickable(onClick = onOpenPomodoro)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (pomodoro.phase == com.shenqing.mobile.data.PomodoroPhase.IDLE) "清番 · 番茄钟"
                            else "清番 · " + pomodoro.phaseLabel + " · " + pomodoro.round + "/" + pomodoro.totalRounds,
                            color = palette.textPrimary.asCompose(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (pomodoro.phase == com.shenqing.mobile.data.PomodoroPhase.IDLE)
                                settings.pomodoroWorkMinutes.toString() + " 分钟专注 · 本轮 " + settings.pomodoroRounds + " 个番茄"
                            else formatClock(pomodoro.remaining().toLong()) + " · " + pomodoro.totalRounds + " 个番茄",
                            color = palette.textTertiary.asCompose(), fontSize = 9.sp,
                        )
                    }
                    if (pomodoro.phase == com.shenqing.mobile.data.PomodoroPhase.IDLE) {
                        Button(
                            onClick = { repo.startPomodoro(settings.pomodoroWorkMinutes, totalRounds = settings.pomodoroRounds); onOpenPomodoro() },
                            shape = RoundedCornerShape(14.dp),
                        ) { Text("开始", fontSize = 10.sp) }
                    } else {
                        TextButton(onClick = onOpenPomodoro) { Text("查看", color = palette.accent.asCompose(), fontSize = 10.sp) }
                    }
                }
            }
        }
        item {
            SectionHeader(
                title = "清空间",
                subtitle = "选一档强度，开始一段不被打断的时间",
                trailing = {
                    TextButton(onClick = onOpenStats) {
                        Text("查看数据", color = palette.accent.asCompose(), fontSize = 10.sp)
                    }
                },
            )
        }
        items(spaces.chunked(2)) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { space ->
                    TierCard(space, Modifier.weight(1f), palette) { sheetSpace = space }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text("近 7 天", color = palette.textPrimary.asCompose(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("每天专注的分钟数", color = palette.textTertiary.asCompose(), fontSize = 10.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(formatMinutes(stats.weekMinutes), color = palette.textPrimary.asCompose(), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("本周", color = palette.textTertiary.asCompose(), fontSize = 9.sp)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    SevenDayBars(stats.last7Days.map { it.minutes }, palette.accent, palette.gradB)
                }
            }
        }
        item {
            Text(
                "把这件事做完，再抬头。",
                color = palette.textTertiary.asCompose(), fontSize = 10.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }

    val space = sheetSpace
    if (space != null) {
        var minutes by remember(space) { mutableIntStateOf(space.minutes.coerceIn(space.tier.minMinutes, space.tier.maxMinutes)) }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { sheetSpace = null },
            sheetState = sheetState,
            containerColor = palette.cardBg.asCompose(),
        ) {
            Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 30.dp)) {
                Text(space.tier.english + " · " + space.tier.label, color = palette.accent.asCompose(), fontSize = 10.sp, letterSpacing = 2.sp)
                Text(space.name, color = palette.textPrimary.asCompose(), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(space.tier.detail, color = palette.textSecondary.asCompose(), fontSize = 11.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    Text(minutes.toString(), color = palette.textPrimary.asCompose(), fontSize = 52.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text("分钟", color = palette.textTertiary.asCompose(), fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp, start = 4.dp))
                }
                Slider(
                    value = minutes.toFloat(),
                    onValueChange = { minutes = it.toInt() },
                    valueRange = space.tier.minMinutes.toFloat()..space.tier.maxMinutes.toFloat(),
                    steps = ((space.tier.maxMinutes - space.tier.minMinutes) / space.tier.step) - 1,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    val presets = if (space.tier == Tier.SLEEP) listOf(60, 120, 240, 480, 600) else listOf(15, 25, 45, 60, 90, 120)
                    presets.forEach { preset ->
                        TextButton(
                            onClick = { minutes = preset },
                            modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(
                                if (minutes == preset) palette.accent.asCompose() else Color.White.copy(alpha = 0.05f)
                            ),
                        ) {
                            Text(preset.toString() + " 分", color = if (minutes == preset) palette.onAccent.asCompose() else palette.textSecondary.asCompose(), fontSize = 10.sp)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (space.tier.locked) Icons.Outlined.Anchor else Icons.Outlined.Spa,
                        contentDescription = null, tint = palette.accent.asCompose(), modifier = Modifier.size(14.dp),
                    )
                    Text(
                        if (space.tier.locked) "开始后不可暂停或提前结束；连点 10 次才会出现出口。" else "可以暂停，也可以提前结束；结束后照常记入今天。",
                        color = palette.textTertiary.asCompose(), fontSize = 10.sp, modifier = Modifier.padding(start = 6.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { onStart(space, minutes); sheetSpace = null },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent.asCompose(), contentColor = palette.onAccent.asCompose()),
                ) {
                    Text("开始" + space.tier.label + " · " + minutes + " 分钟", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ActiveCard(active: ActiveSession, palette: com.shenqing.mobile.data.ThemePalette, onOpen: () -> Unit) {
    val remaining = active.remainingSeconds()
    val progress = ((active.totalSeconds - remaining).toFloat() / active.totalSeconds.coerceAtLeast(1)).coerceIn(0f, 1f)
    AppCard(
        Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(progress, size = 64.dp, stroke = 5.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("正在进行", color = palette.accent.asCompose(), fontSize = 9.sp, letterSpacing = 2.sp)
                Text(active.name, color = palette.textPrimary.asCompose(), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(formatClock(remaining), color = palette.textPrimary.asCompose(), fontSize = 20.sp, fontFamily = FontFamily.Monospace)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("回到专注", color = palette.accent.asCompose(), fontSize = 10.sp)
                Icon(Icons.Outlined.ArrowForward, contentDescription = null, tint = palette.accent.asCompose(), modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun TierCard(space: Space, modifier: Modifier, palette: com.shenqing.mobile.data.ThemePalette, onClick: () -> Unit) {
    val icon: ImageVector = when (space.tier) {
        Tier.LIGHT -> Icons.Outlined.Spa
        Tier.COVER -> Icons.Outlined.Layers
        Tier.DEEP -> Icons.Outlined.Anchor
        Tier.SLEEP -> Icons.Outlined.NightsStay
    }
    val tierColor = when (space.tier) {
        Tier.LIGHT -> palette.accent
        Tier.COVER -> palette.gradB
        Tier.DEEP -> palette.gradC
        Tier.SLEEP -> palette.gradA
    }
    AppCard(modifier.clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp).fillMaxHeight()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(Color.White.copy(alpha = 0.06f))
                        .border(1.dp, palette.cardBorder.asCompose(), RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, contentDescription = null, tint = tierColor.asCompose(), modifier = Modifier.size(19.dp)) }
                Spacer(Modifier.weight(1f))
                Text(space.tier.english, color = palette.textTertiary.asCompose(), fontSize = 8.sp, letterSpacing = 2.sp)
            }
            Spacer(Modifier.height(12.dp))
            Text(space.tier.label, color = palette.textPrimary.asCompose(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(space.tier.tagline, color = palette.textSecondary.asCompose(), fontSize = 10.sp, lineHeight = 15.sp)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(space.name + " · " + space.minutes + " 分钟", color = palette.textTertiary.asCompose(), fontSize = 9.sp, modifier = Modifier.weight(1f))
                Text("开始", color = tierColor.asCompose(), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun SevenDayBars(minutes: List<Int>, accent: Int, accent2: Int) {
    val palette = LocalPalette.current
    val max = max(1, minutes.maxOrNull() ?: 1)
    Row(Modifier.fillMaxWidth().height(100.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        minutes.forEachIndexed { index, value ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.05f)),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight((value.toFloat() / max).coerceIn(0.03f, 1f))
                            .clip(RoundedCornerShape(6.dp))
                            .background(Brush.verticalGradient(listOf(Color(accent2), Color(accent)))),
                    )
                }
                Text(
                    listOf("日", "一", "二", "三", "四", "五", "六")[((index + 1) % 7)],
                    color = palette.textTertiary.asCompose(), fontSize = 8.sp, modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
