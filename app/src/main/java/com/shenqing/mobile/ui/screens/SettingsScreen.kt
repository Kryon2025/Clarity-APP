package com.shenqing.mobile.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.shenqing.mobile.data.AppRepository
import com.shenqing.mobile.data.BridgeClient
import com.shenqing.mobile.data.CloudSyncManager
import com.shenqing.mobile.data.BridgeSettings
import com.shenqing.mobile.data.ThemeRepository
import org.json.JSONObject
import com.shenqing.mobile.service.FocusAccessibilityService
import com.shenqing.mobile.service.LockTaskController
import com.shenqing.mobile.ui.AppCard
import com.shenqing.mobile.ui.SectionHeader
import com.shenqing.mobile.ui.asCompose
import com.shenqing.mobile.ui.theme.LocalPalette
import com.shenqing.mobile.widget.FocusWidgetProvider
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    repo: AppRepository,
    themeRepo: ThemeRepository,
) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val entries by themeRepo.entries.collectAsState()
    val fontScale by repo.fontScale.collectAsState()
    val bridge by repo.bridge.collectAsState()
    val cloud by repo.cloud.collectAsState()
    val cloudManager = remember { CloudSyncManager(context) }
    var cloudUrl by remember(cloud.url) { mutableStateOf(cloud.url) }
    var cloudEmail by remember(cloud.email) { mutableStateOf(cloud.email) }
    var cloudPassword by remember { mutableStateOf("") }
    var cloudNotice by remember { mutableStateOf("") }
    var slider by remember(fontScale) { mutableFloatStateOf(fontScale * 100f) }
    var host by remember(bridge.host) { mutableStateOf(bridge.host) }
    var port by remember(bridge.port) { mutableStateOf(bridge.port.toString()) }
    var token by remember(bridge.token) { mutableStateOf(bridge.token) }
    var bridgeError by remember { mutableStateOf("") }
    var connecting by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }

    val accessibility = FocusAccessibilityService.isEnabled(context)
    val notifications = NotificationManagerCompat.from(context).areNotificationsEnabled()
    val battery = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
        .isIgnoringBatteryOptimizations(context.packageName)
    val deviceOwner = LockTaskController.isDeviceOwner(context)

    LazyColumn(
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Text("设置", color = palette.textTertiary.asCompose(), fontSize = 9.sp, letterSpacing = 2.sp)
                Text("把深清调成你的样子", color = palette.textPrimary.asCompose(), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("主题、锁机权限与桌面连接，都只在本机生效。", color = palette.textTertiary.asCompose(), fontSize = 10.sp)
            }
        }
        item {
            SectionHeader("外观", "与桌面 AppTheme 同源 · " + entries.size + " 套")
            ThemeGrid(entries, themeRepo)
        }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row {
                        Text("字号", color = palette.textSecondary.asCompose(), fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Text(slider.toInt().toString() + "%", color = palette.accent.asCompose(), fontSize = 11.sp)
                    }
                    Slider(
                        value = slider,
                        onValueChange = { slider = it; repo.setFontScale(it / 100f) },
                        valueRange = 80f..140f, steps = 11,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("小", "标准", "大", "特大").forEach { Text(it, color = palette.textTertiary.asCompose(), fontSize = 8.sp) }
                    }
                }
            }
        }
        item {
            SectionHeader("桌面连接", "AI 沟通插件 · 局域网")
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        if (bridge.connected) "已连接 · " + bridge.pluginName + " v" + bridge.pluginVersion else "未连接 · 本地模式",
                        color = if (bridge.connected) palette.success.asCompose() else palette.textSecondary.asCompose(),
                        fontSize = 10.sp,
                    )
                    if (bridgeError.isNotBlank()) Text(bridgeError, color = palette.danger.asCompose(), fontSize = 9.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = host, onValueChange = { host = it },
                        label = { Text("地址 / shenqing://pair?…", fontSize = 9.sp) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = port, onValueChange = { port = it },
                            label = { Text("端口", fontSize = 9.sp) },
                            modifier = Modifier.weight(1f), singleLine = true,
                        )
                        OutlinedTextField(
                            value = token, onValueChange = { token = it },
                            label = { Text("配对令牌", fontSize = 9.sp) },
                            modifier = Modifier.weight(1.6f), singleLine = true,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    connecting = true; bridgeError = ""
                                    val settings = BridgeSettings(host.trim(), port.toIntOrNull() ?: 17777, token.trim())
                                    repo.setBridge(settings)
                                    try {
                                        val client = BridgeClient(settings)
                                        if (!client.ping()) throw IllegalStateException("协议版本不兼容")
                                        val manifest = client.manifest()
                                        repo.setBridgeConnected(
                                            true, "",
                                            manifest.optJSONObject("plugin")?.optString("name", "") ?: "",
                                            manifest.optJSONObject("plugin")?.optString("version", "") ?: "",
                                        )
                                    } catch (e: Exception) {
                                        repo.setBridgeConnected(false, e.message ?: "连接失败")
                                        bridgeError = e.message ?: "连接失败"
                                    } finally { connecting = false }
                                }
                            },
                            enabled = !connecting,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = palette.accent.asCompose(), contentColor = palette.onAccent.asCompose()),
                        ) { Text(if (connecting) "连接中…" else "连接", fontSize = 11.sp) }
                        OutlinedButton(onClick = { repo.setBridgeConnected(false) }, shape = RoundedCornerShape(14.dp)) {
                            Text("断开", fontSize = 11.sp)
                        }
                    }
                    Text("电脑端启用「AI 沟通」后，把状态摘要里的 shenqing://pair?… 整串粘到地址栏。", color = palette.textTertiary.asCompose(), fontSize = 8.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        item {
            SectionHeader("账户同步", "与电脑共用深清商店账号 · v0.20 快照")
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        if (cloud.loggedIn) "已登录 · " + cloud.email else "未登录 · 可同步主题与字号",
                        color = if (cloud.loggedIn) palette.success.asCompose() else palette.textSecondary.asCompose(),
                        fontSize = 10.sp,
                    )
                    if (cloud.lastAt.isNotBlank()) {
                        Text("上次同步：" + cloud.lastAt, color = palette.textTertiary.asCompose(), fontSize = 8.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = cloudUrl, onValueChange = { cloudUrl = it },
                        label = { Text("商店服务器地址", fontSize = 9.sp) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                    )
                    if (!cloud.loggedIn) {
                        OutlinedTextField(
                            value = cloudEmail, onValueChange = { cloudEmail = it },
                            label = { Text("登录邮箱", fontSize = 9.sp) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                        )
                        OutlinedTextField(
                            value = cloudPassword, onValueChange = { cloudPassword = it },
                            label = { Text("密码（只用于登录，不保存）", fontSize = 9.sp) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        if (!cloud.loggedIn) {
                            Button(
                                onClick = {
                                    repo.updateCloud(repo.cloud.value.copy(url = cloudUrl.trim(), email = cloudEmail.trim()))
                                    scope.launch { cloudManager.login(cloudUrl, cloudEmail, cloudPassword); cloudPassword = "" }
                                },
                                enabled = !cloud.busy,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = palette.accent.asCompose(), contentColor = palette.onAccent.asCompose()),
                            ) { Text(if (cloud.busy) "登录中…" else "登录", fontSize = 11.sp) }
                        } else {
                            Button(
                                onClick = { scope.launch { cloudManager.syncNow() } },
                                enabled = !cloud.busy,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = palette.accent.asCompose(), contentColor = palette.onAccent.asCompose()),
                            ) { Text(if (cloud.busy) "同步中…" else "立即同步", fontSize = 11.sp) }
                        }
                        OutlinedButton(
                            onClick = {
                                if (bridge.host.isBlank()) {
                                    cloudNotice = "先连接电脑桥接，才能读取桌面配置"
                                } else {
                                    scope.launch {
                                        try {
                                            val data = BridgeClient(bridge).settings()
                                            val remoteCloud = data.optJSONObject("cloud")
                                            cloudUrl = remoteCloud?.optString("url", cloudUrl)?.ifBlank { cloudUrl } ?: cloudUrl
                                            cloudEmail = remoteCloud?.optString("email", cloudEmail)?.ifBlank { cloudEmail } ?: cloudEmail
                                            repo.updateCloud(repo.cloud.value.copy(url = cloudUrl, email = cloudEmail))
                                            val custom = data.optJSONArray("customThemes")
                                            if (custom != null) {
                                                for (i in 0 until custom.length()) {
                                                    val item = custom.optJSONObject(i) ?: continue
                                                    val paletteObj = item.optJSONObject("palette") ?: continue
                                                    themeRepo.importPaletteTheme(
                                                        item.optString("id"),
                                                        item.optString("name", item.optString("id")),
                                                        item.optString("description", ""),
                                                        paletteObj,
                                                    )
                                                }
                                            }
                                            val mode = data.optString("themeMode", "solid")
                                            if (mode.startsWith("custom:")) themeRepo.apply(mode.removePrefix("custom:"))
                                            else if (mode in listOf("solid", "light", "glass", "ocean")) themeRepo.apply(mode)
                                            repo.setFontScale(data.optDouble("fontScale", 1.0).toFloat())
                                            cloudNotice = "已读取电脑端主题 / 字号 / 同步地址"
                                        } catch (e: Exception) {
                                            cloudNotice = e.message ?: "读取桌面配置失败"
                                        }
                                    }
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                        ) { Text("从电脑读取配置", fontSize = 10.sp) }
                    }
                    if (cloud.loggedIn) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { scope.launch { cloudManager.pull() } }, enabled = !cloud.busy, shape = RoundedCornerShape(14.dp)) { Text("从云端拉取", fontSize = 10.sp) }
                            OutlinedButton(onClick = { scope.launch { cloudManager.push() } }, enabled = !cloud.busy, shape = RoundedCornerShape(14.dp)) { Text("上传手机设置", fontSize = 10.sp) }
                            TextButton(onClick = { cloudManager.logout() }) { Text("退出", fontSize = 10.sp, color = palette.textTertiary.asCompose()) }
                        }
                    }
                    if (cloud.status.isNotBlank()) Text(cloud.status, color = palette.accent.asCompose(), fontSize = 8.sp, modifier = Modifier.padding(top = 6.dp))
                    if (cloud.error.isNotBlank()) Text("错误：" + cloud.error, color = palette.danger.asCompose(), fontSize = 8.sp)
                    if (cloudNotice.isNotBlank()) Text(cloudNotice, color = palette.textTertiary.asCompose(), fontSize = 8.sp)
                    Text("只同步主题 / 字号 / custom_themes；不会覆盖电脑的空间与统计。密码不会保存。", color = palette.textTertiary.asCompose(), fontSize = 8.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        item {
            SectionHeader("锁机权限", "深清/覆清需要无障碍；设备所有者可开启真 kiosk")
            PermissionRow("无障碍服务", accessibility, if (accessibility) "已开启" else "必需") {
                FocusAccessibilityService.openSettings(context)
            }
            PermissionRow("通知", notifications, if (notifications) "已开启" else "建议开启") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            PermissionRow("电池优化", battery, if (battery) "已忽略" else "建议忽略") {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
            PermissionRow("设备所有者", deviceOwner, if (deviceOwner) "真 kiosk 可用" else "可选 · adb 设置") { }
            Text(
                "设备所有者启用（电脑执行一次）：adb shell dpm set-device-owner com.shenqing.mobile/.service.ShenQingDeviceAdminReceiver\n未设置时使用无障碍 + 悬浮层，属 Android 允许范围内的深度锁机。",
                color = palette.textTertiary.asCompose(), fontSize = 8.sp, lineHeight = 13.sp, modifier = Modifier.padding(vertical = 6.dp),
            )
        }
        item {
            SectionHeader("桌面组件", "快速专注 / 今日次数 / 近 7 天次数")
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("在桌面空白处长按 → 小组件 → 深清，添加 4×2 组件。", color = palette.textSecondary.asCompose(), fontSize = 10.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val manager = AppWidgetManager.getInstance(context)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.isRequestPinAppWidgetSupported) {
                                manager.requestPinAppWidget(ComponentName(context, FocusWidgetProvider::class.java), null, null)
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("添加到桌面", fontSize = 11.sp) }
                }
            }
        }
        item {
            SectionHeader("数据", "只在本机")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showClear = true }, shape = RoundedCornerShape(14.dp)) {
                    Text("清除本地数据", fontSize = 11.sp, color = palette.danger.asCompose())
                }
            }
        }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("深清 · 手机端 1.0.0", color = palette.textPrimary.asCompose(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Android 原生 · Kotlin + Jetpack Compose", color = palette.textTertiary.asCompose(), fontSize = 9.sp)
                    Text("主题令牌 v1 · 协议 shenqing.bridge/v1", color = palette.textTertiary.asCompose(), fontSize = 9.sp)
                }
            }
        }
    }

    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            title = { Text("清除本地数据？") },
            text = { Text("设置、统计、主题包与专注记录都会被删除。桌面数据不受影响。") },
            confirmButton = { TextButton(onClick = { repo.clearAllData(); showClear = false }) { Text("清除") } },
            dismissButton = { TextButton(onClick = { showClear = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun ThemeGrid(entries: List<com.shenqing.mobile.data.ThemeEntry>, themeRepo: ThemeRepository) {
    val palette = LocalPalette.current
    val current by themeRepo.current.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { entry ->
                    val p = themeRepo.paletteFor(entry.id)
                    AppCard(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable { themeRepo.apply(entry.id) },
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(40.dp).clip(RoundedCornerShape(11.dp))
                                        .background(Color(p.bgTop)).padding(4.dp)
                                ) {
                                    Box(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(4.dp)).background(Color(p.cardBg)))
                                }
                                Column(Modifier.padding(start = 8.dp)) {
                                    Text(entry.name, color = palette.textPrimary.asCompose(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    Text(if (entry.builtIn) "内置" else "主题包", color = palette.textTertiary.asCompose(), fontSize = 8.sp)
                                }
                            }
                            Text(if (current.id == entry.id) "使用中" else "轻点应用", color = if (current.id == entry.id) palette.accent.asCompose() else palette.textTertiary.asCompose(), fontSize = 8.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PermissionRow(title: String, ok: Boolean, hint: String, onClick: () -> Unit) {
    val palette = LocalPalette.current
    AppCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(99.dp)).background(if (ok) palette.success.asCompose() else palette.danger.asCompose()))
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(title, color = palette.textPrimary.asCompose(), fontSize = 11.sp)
                Text(hint, color = if (ok) palette.success.asCompose() else palette.textTertiary.asCompose(), fontSize = 8.sp)
            }
            if (!ok) TextButton(onClick = onClick) { Text("设置", color = palette.accent.asCompose(), fontSize = 10.sp) }
        }
    }
}
