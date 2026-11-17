package com.shenqing.mobile.ui.screens

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.shenqing.mobile.MainActivity
import com.shenqing.mobile.data.ThemeRepository
import com.shenqing.mobile.service.LiveNotification
import com.shenqing.mobile.ui.AppCard
import com.shenqing.mobile.ui.SectionHeader
import com.shenqing.mobile.ui.VendorUi
import com.shenqing.mobile.ui.asCompose
import com.shenqing.mobile.ui.theme.LocalPalette

/**
 * 让用户直接验证 ColorOS 是否能展示系统级计时通知。
 *
 * 系统私有的“流光卡片/流体云模板”没有公开 SDK，第三方无法嵌入；
 * 可公开调用的是 Android 16 Live Updates 通道（ColorOS 流体云会消费 promoted 通知）。
 */
@Composable
fun OppoSystemPage(context: Context, onBack: () -> Unit) {
    val palette = LocalPalette.current
    val manager = context.getSystemService(NotificationManager::class.java)
    val canPromote = remember {
        if (Build.VERSION.SDK_INT >= 36) {
            runCatching {
                manager.javaClass.getMethod("canPostPromotedNotifications").invoke(manager) as Boolean
            }.getOrDefault(false)
        } else false
    }
    var testPosted by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val skin = VendorUi.skin()

    LazyColumn(contentPadding = PaddingValues(18.dp, 12.dp, 18.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SettingsBackHeader("OPPO 系统效果", "ColorOS 流光 / 流体云兼容性实验", onBack) }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("设备识别", color = palette.textPrimary.asCompose(), fontSize = 12.sp)
                    Text(Build.MANUFACTURER + " " + Build.BRAND + " · " + (Build.DISPLAY ?: ""), color = palette.textSecondary.asCompose(), fontSize = 9.sp)
                    Text("视觉适配：" + skin.label, color = palette.accent.asCompose(), fontSize = 10.sp)
                    Text("Android API " + Build.VERSION.SDK_INT + "；" + if (Build.VERSION.SDK_INT >= 36) "支持 Live Updates 构建" else "低于 Android 16，只能显示普通通知", color = palette.textTertiary.asCompose(), fontSize = 9.sp)
                    Text("通知实时活动权限：" + if (canPromote) "已允许" else "未允许/系统未开放", color = if (canPromote) palette.success.asCompose() else palette.textTertiary.asCompose(), fontSize = 9.sp)
                }
            }
        }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SectionHeader("系统测试通知", "用 30/60 秒计时测试 ColorOS 是否上岛/上流体云")
                    Text(
                        "说明：这里调用的是 Android 16 Live Updates（ProgressStyle + promoted ongoing）。ColorOS 的私有流光模板/卡片没有公开第三方 SDK；系统是否展示由 ColorOS 决定。",
                        color = palette.textTertiary.asCompose(), fontSize = 9.sp, modifier = Modifier.padding(bottom = 10.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val open = PendingIntent.getActivity(
                                    context,
                                    901,
                                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                                )
                                val n = LiveNotification.build(
                                    context = context,
                                    channel = LiveNotification.CHANNEL_POMODORO,
                                    title = "深清 · 系统计时光效测试",
                                    text = "00:30",
                                    progress = 30,
                                    max = 60,
                                    shortCriticalText = "深清 00:30",
                                    contentIntent = open,
                                )
                                runCatching {
                                    NotificationManagerCompat.from(context).notify(9001, n)
                                    testPosted = true
                                    message = "已发送；请下拉通知栏/锁屏查看是否出现计时卡片或胶囊"
                                }.onFailure {
                                    message = it.message ?: "发送失败"
                                }
                            },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = palette.accent.asCompose(), contentColor = palette.onAccent.asCompose()),
                        ) { Text("发送测试", fontSize = 10.sp) }
                        OutlinedButton(
                            onClick = {
                                NotificationManagerCompat.from(context).cancel(9001)
                                testPosted = false
                                message = "已取消测试通知"
                            },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                        ) { Text("取消", fontSize = 10.sp) }
                    }
                    if (message.isNotBlank()) Text(message, color = palette.accent.asCompose(), fontSize = 9.sp, modifier = Modifier.padding(top = 8.dp))
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= 36) {
                                runCatching {
                                    context.startActivity(
                                        Intent("android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS")
                                            .setData(Uri.parse("package:" + context.packageName))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }.onFailure { message = "系统没有实时更新设置页，请到通知设置里开启" }
                            } else {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    ) { Text("打开系统通知 / 实时更新设置", fontSize = 10.sp) }
                }
            }
        }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("为什么不能“直接调用流光控件”", color = palette.textPrimary.asCompose(), fontSize = 11.sp)
                    Text(
                        "ColorOS 的流体云卡片、灵动岛样式、系统控件都属于 OPPO 私有实现，普通 APK 没有公开 API，也没有可嵌入的 View。能走通的是：系统通知（普通/进度/Android 16 Live Updates）、系统安装器、禅定空间等公开入口。要获得 OPPO 原生流光组件，需要 OPPO 开放平台 Pantanal/Seedling 准入或系统签名合作。",
                        color = palette.textSecondary.asCompose(), fontSize = 9.sp, modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}
