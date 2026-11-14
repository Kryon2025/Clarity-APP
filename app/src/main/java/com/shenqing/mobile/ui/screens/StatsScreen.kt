package com.shenqing.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shenqing.mobile.data.AppRepository
import com.shenqing.mobile.ui.AppCard
import com.shenqing.mobile.ui.SectionHeader
import com.shenqing.mobile.ui.asCompose
import com.shenqing.mobile.ui.formatMinutes
import com.shenqing.mobile.ui.theme.LocalPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatsScreen(repo: AppRepository) {
    val palette = LocalPalette.current
    val stats by repo.stats.collectAsState()
    val sessions by repo.sessions.collectAsState()
    LazyColumn(
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text("数据", color = palette.textTertiary.asCompose(), fontSize = 9.sp, letterSpacing = 2.sp)
                Text("空间的心事", color = palette.textPrimary.asCompose(), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("专注不是打卡，是今天真的推进了哪一步。", color = palette.textTertiary.asCompose(), fontSize = 10.sp)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Kpi("今日", formatMinutes(stats.todayMinutes), "分钟", Modifier.weight(1f))
                Kpi("本周", formatMinutes(stats.weekMinutes), "分钟", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Kpi("连续", stats.streakDays.toString() + " 天", "没有断", Modifier.weight(1f))
                Kpi("累计", stats.sessionCount.toString() + " 段", "专注", Modifier.weight(1f))
            }
        }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text("7 天分布", color = palette.textPrimary.asCompose(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("合计 " + formatMinutes(stats.last7Days.sumOf { it.minutes }) + " 分钟", color = palette.textTertiary.asCompose(), fontSize = 10.sp)
                        }
                        Text("峰值 " + formatMinutes(stats.last7Days.maxOfOrNull { it.minutes } ?: 0), color = palette.textTertiary.asCompose(), fontSize = 9.sp)
                    }
                    Spacer(Modifier.height(14.dp))
                    Bars(stats.last7Days.map { it.minutes }, palette.accent, palette.gradB)
                }
            }
        }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SectionHeader("分类", "时间去了哪里")
                    stats.categories.forEachIndexed { index, pair ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).clip(RoundedCornerShape(99.dp)).background(Color(palette.list("appUsageColors").getOrNull(index) ?: palette.accent)))
                            Text(pair.first, color = palette.textSecondary.asCompose(), fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp).weight(1f))
                            Text(formatMinutes(pair.second), color = palette.textTertiary.asCompose(), fontSize = 10.sp)
                        }
                    }
                    if (stats.categories.isEmpty()) {
                        Text("开始一段专注，这里就会长出来。", color = palette.textTertiary.asCompose(), fontSize = 10.sp, modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SectionHeader("最近专注", "本机记录")
                    if (sessions.isEmpty()) {
                        Text("还没有记录。", color = palette.textTertiary.asCompose(), fontSize = 11.sp, modifier = Modifier.padding(vertical = 10.dp))
                    } else {
                        sessions.take(12).forEach { session ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(32.dp).clip(RoundedCornerShape(11.dp)).background(Color(palette.gradB)),
                                    contentAlignment = Alignment.Center,
                                ) { Text(session.name.take(1), color = palette.onAccent.asCompose(), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                    Text(session.name, color = palette.textPrimary.asCompose(), fontSize = 12.sp)
                                    Text(
                                        SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(session.endedAt)),
                                        color = palette.textTertiary.asCompose(), fontSize = 9.sp,
                                    )
                                }
                                Text(session.minutes.toString() + " 分钟", color = palette.textSecondary.asCompose(), fontSize = 10.sp)
                                Text(
                                    if (session.completed) "完成" else "中断",
                                    color = if (session.completed) palette.success.asCompose() else palette.textTertiary.asCompose(),
                                    fontSize = 8.sp, modifier = Modifier.padding(start = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            Text(
                "数据只保存在本机 · 主题与桌面 AppTheme 同源",
                color = palette.textTertiary.asCompose(), fontSize = 9.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Kpi(label: String, value: String, hint: String, modifier: Modifier) {
    val palette = LocalPalette.current
    AppCard(modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(label, color = palette.textTertiary.asCompose(), fontSize = 9.sp, letterSpacing = 1.sp)
            Text(value, color = palette.textPrimary.asCompose(), fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(hint, color = palette.textTertiary.asCompose(), fontSize = 8.sp)
        }
    }
}

@Composable
private fun Bars(days: List<Int>, accent: Int, accent2: Int) {
    val palette = LocalPalette.current
    val max = (days.maxOrNull() ?: 1).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        days.forEach { minutes ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.05f)),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight((minutes.toFloat() / max).coerceIn(0.02f, 1f))
                            .clip(RoundedCornerShape(6.dp))
                            .background(Brush.verticalGradient(listOf(Color(accent2), Color(accent)))),
                    )
                }
                Text("", fontSize = 1.sp)
            }
        }
    }
}
