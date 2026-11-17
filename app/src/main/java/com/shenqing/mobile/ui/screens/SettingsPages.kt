package com.shenqing.mobile.ui.screens

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.shenqing.mobile.BuildConfig
import com.shenqing.mobile.R
import com.shenqing.mobile.data.AppRepository
import com.shenqing.mobile.data.BridgeClient
import com.shenqing.mobile.data.CloudAccount
import com.shenqing.mobile.data.CloudSyncManager
import com.shenqing.mobile.data.RemotePlugin
import com.shenqing.mobile.data.StoreCatalogClient
import com.shenqing.mobile.data.StoreItem
import com.shenqing.mobile.data.ThemeRepository
import com.shenqing.mobile.data.UpdateChecker
import com.shenqing.mobile.data.UpdateDownloader
import com.shenqing.mobile.data.UpdateChannel
import com.shenqing.mobile.data.UpdateInfo
import com.shenqing.mobile.ui.AppCard
import com.shenqing.mobile.ui.SectionHeader
import com.shenqing.mobile.ui.asCompose
import com.shenqing.mobile.ui.theme.LocalPalette
import kotlinx.coroutines.launch
import org.json.JSONObject

enum class SettingsPage {
    HOME, ACCOUNT, PROFILE, APPEARANCE_STORE, PLUGIN_STORE, UPDATE, ABOUT, OPPO_SYSTEM
}

@Composable
fun SettingsBackHeader(title: String, subtitle: String, onBack: () -> Unit) {
    val palette = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp)) {
        TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
            Text("← 返回", color = palette.textSecondary.asCompose(), fontSize = 11.sp)
        }
        Text(title, color = palette.textPrimary.asCompose(), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = palette.textTertiary.asCompose(), fontSize = 10.sp)
    }
}

@Composable
fun SettingsProfileHeader(
    settings: AppRepository.Settings,
    cloud: CloudAccount,
    onAccount: () -> Unit,
    onProfile: () -> Unit,
) {
    val palette = LocalPalette.current
    val name = settings.profileName.ifBlank { cloud.email.ifBlank { "未登录" } }
    val initial = name.trim().take(1).uppercase().ifBlank { "深" }
    AppCard(Modifier.fillMaxWidth().clickable { if (cloud.loggedIn) onProfile() else onAccount() }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(CircleShape)
                    .background(Color(palette.accent).copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(initial, color = palette.accent.asCompose(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(name, color = palette.textPrimary.asCompose(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (cloud.loggedIn) cloud.email else "登录深清商店账号，同步主题与设置",
                    color = palette.textTertiary.asCompose(),
                    fontSize = 9.sp,
                )
            }
            Text(
                if (cloud.loggedIn) "个人资料" else "登录",
                color = palette.accent.asCompose(),
                fontSize = 10.sp,
            )
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = palette.textTertiary.asCompose(), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun SettingsMenuRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(11.dp))
                .background(Color(palette.accent).copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = palette.accent.asCompose(), modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(title, color = palette.textPrimary.asCompose(), fontSize = 11.sp)
            Text(subtitle, color = palette.textTertiary.asCompose(), fontSize = 8.sp)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = palette.textTertiary.asCompose(), modifier = Modifier.size(16.dp))
    }
}

@Composable
fun AccountSettingsPage(repo: AppRepository, bridge: com.shenqing.mobile.data.BridgeSettings, themeRepo: ThemeRepository, onBack: () -> Unit) {
    val palette = LocalPalette.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val cloud by repo.cloud.collectAsState()
    val manager = remember { CloudSyncManager(context) }
    var url by remember(cloud.url) { mutableStateOf(cloud.url) }
    var email by remember(cloud.email) { mutableStateOf(cloud.email) }
    var password by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }

    LazyColumn(contentPadding = PaddingValues(18.dp, 12.dp, 18.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SettingsBackHeader("账户与同步", "使用桌面 v0.20 的商店账号同步主题与设置", onBack) }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(if (cloud.loggedIn) "已登录 · " + cloud.email else "未登录", color = if (cloud.loggedIn) palette.success.asCompose() else palette.textSecondary.asCompose(), fontSize = 11.sp)
                    if (cloud.lastAt.isNotBlank()) Text("上次同步：" + cloud.lastAt, color = palette.textTertiary.asCompose(), fontSize = 8.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(url, { url = it }, label = { Text("商店服务器地址", fontSize = 9.sp) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    if (!cloud.loggedIn) {
                        OutlinedTextField(email, { email = it }, label = { Text("登录邮箱", fontSize = 9.sp) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(password, { password = it }, label = { Text("密码（不保存）", fontSize = 9.sp) }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!cloud.loggedIn) {
                            Button(
                                onClick = {
                                    repo.updateCloud(repo.cloud.value.copy(url = url.trim(), email = email.trim()))
                                    scope.launch { manager.login(url, email, password); password = "" }
                                },
                                enabled = !cloud.busy,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = palette.accent.asCompose(), contentColor = palette.onAccent.asCompose()),
                            ) { Text(if (cloud.busy) "登录中…" else "登录", fontSize = 11.sp) }
                        } else {
                            Button(
                                onClick = { scope.launch { manager.syncNow() } },
                                enabled = !cloud.busy,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = palette.accent.asCompose(), contentColor = palette.onAccent.asCompose()),
                            ) { Text(if (cloud.busy) "同步中…" else "立即同步", fontSize = 11.sp) }
                        }
                        OutlinedButton(
                            onClick = {
                                if (bridge.host.isBlank()) notice = "先连接电脑桥接，才能读取桌面配置"
                                else scope.launch {
                                    try {
                                        val data = BridgeClient(bridge).settings()
                                        val remote = data.optJSONObject("cloud")
                                        url = remote?.optString("url", url)?.ifBlank { url } ?: url
                                        email = remote?.optString("email", email)?.ifBlank { email } ?: email
                                        repo.updateCloud(repo.cloud.value.copy(url = url, email = email))
                                        val custom = data.optJSONArray("customThemes")
                                        if (custom != null) for (i in 0 until custom.length()) {
                                            val item = custom.optJSONObject(i) ?: continue
                                            val p = item.optJSONObject("palette") ?: continue
                                            themeRepo.importPaletteTheme(item.optString("id"), item.optString("name", item.optString("id")), item.optString("description", ""), p)
                                        }
                                        val mode = data.optString("themeMode", "solid")
                                        if (mode.startsWith("custom:")) themeRepo.apply(mode.removePrefix("custom:"))
                                        else if (mode in listOf("solid", "light", "glass", "ocean")) themeRepo.apply(mode)
                                        repo.setFontScale(data.optDouble("fontScale", 1.0).toFloat())
                                        notice = "已读取电脑端主题 / 字号 / 同步地址"
                                    } catch (e: Exception) { notice = e.message ?: "读取桌面配置失败" }
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                        ) { Text("从电脑读取", fontSize = 10.sp) }
                    }
                    if (cloud.loggedIn) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { scope.launch { manager.pull() } }, enabled = !cloud.busy, shape = RoundedCornerShape(14.dp)) { Text("拉取", fontSize = 10.sp) }
                            OutlinedButton(onClick = { scope.launch { manager.push() } }, enabled = !cloud.busy, shape = RoundedCornerShape(14.dp)) { Text("上传", fontSize = 10.sp) }
                            TextButton(onClick = { manager.logout() }) { Text("退出", color = palette.textTertiary.asCompose(), fontSize = 10.sp) }
                        }
                    }
                    if (cloud.status.isNotBlank()) Text(cloud.status, color = palette.accent.asCompose(), fontSize = 8.sp, modifier = Modifier.padding(top = 6.dp))
                    if (cloud.error.isNotBlank()) Text("错误：" + cloud.error, color = palette.danger.asCompose(), fontSize = 8.sp)
                    if (notice.isNotBlank()) Text(notice, color = palette.textTertiary.asCompose(), fontSize = 8.sp)
                    Text("只同步主题 / 字号 / custom_themes；不会覆盖电脑空间与统计。", color = palette.textTertiary.asCompose(), fontSize = 8.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

@Composable
fun ProfileSettingsPage(repo: AppRepository, onBack: () -> Unit, onAccount: () -> Unit) {
    val palette = LocalPalette.current
    val settings by repo.settings.collectAsState()
    val cloud by repo.cloud.collectAsState()
    var name by remember(settings.profileName) { mutableStateOf(settings.profileName) }

    LazyColumn(contentPadding = PaddingValues(18.dp, 12.dp, 18.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SettingsBackHeader("个人资料", "本机昵称只保存在手机；邮箱来自商店账号", onBack) }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(72.dp).clip(CircleShape).background(Color(palette.accent).copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                        Text((name.ifBlank { cloud.email }).take(1).uppercase().ifBlank { "深" }, color = palette.accent.asCompose(), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(name, { name = it.take(40) }, label = { Text("本机昵称", fontSize = 9.sp) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(cloud.email.ifBlank { "未登录" }, {}, label = { Text("商店邮箱", fontSize = 9.sp) }, modifier = Modifier.fillMaxWidth(), singleLine = true, readOnly = true)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { repo.setProfileName(name) }, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = palette.accent.asCompose(), contentColor = palette.onAccent.asCompose())) { Text("保存资料", fontSize = 11.sp) }
                    TextButton(onClick = onAccount) { Text(if (cloud.loggedIn) "管理账户与同步" else "登录商店账号", fontSize = 10.sp) }
                }
            }
        }
    }
}

@Composable
fun AppearanceStorePage(themeRepo: ThemeRepository, onBack: () -> Unit) {
    val palette = LocalPalette.current
    val scope = rememberCoroutineScope()
    val entries by themeRepo.entries.collectAsState()
    val current by themeRepo.current.collectAsState()
    var online by remember { mutableStateOf<List<StoreItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var applying by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try { online = StoreCatalogClient.fetch().filter { it.kind == "theme" } }
        catch (e: Exception) { error = e.message ?: "外观商店加载失败" }
        finally { loading = false }
    }

    LazyColumn(contentPadding = PaddingValues(18.dp, 12.dp, 18.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SettingsBackHeader("外观商店", "在线主题与本地主题包 · 与桌面 palette 共用", onBack) }
        item {
            SectionHeader("在线主题", if (loading) "加载中…" else if (error.isNotBlank()) "离线 · " + error else online.size.toString() + " 套")
            if (error.isNotBlank()) Text("可继续使用下面的本地主题。", color = palette.textTertiary.asCompose(), fontSize = 9.sp)
        }
        items(online.size) { index ->
            val item = online[index]
            StoreThemeCard(
                item = item,
                active = current.id == item.id,
                enabled = item.supportsMobile && item.palette != null && applying.isBlank(),
                onApply = {
                    val p = item.palette ?: return@StoreThemeCard
                    scope.launch {
                        applying = item.id
                        themeRepo.importPaletteTheme(item.id, item.name, item.description, p, item.platforms.ifEmpty { listOf("desktop", "mobile") })
                        themeRepo.apply(item.id)
                        applying = ""
                    }
                },
            )
        }
        item { SectionHeader("本地主题", entries.size.toString() + " 套") }
        items(entries.size) { index ->
            val entry = entries[index]
            AppCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.name, color = palette.textPrimary.asCompose(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text((if (entry.builtIn) "内置 · 深清" else "本地主题包") + " · " + entry.platformLabel, color = palette.textTertiary.asCompose(), fontSize = 8.sp)
                    }
                    if (current.id == entry.id) Text("使用中", color = palette.accent.asCompose(), fontSize = 9.sp)
                    else TextButton(onClick = { themeRepo.apply(entry.id) }, enabled = entry.supportsMobile) { Text(if (entry.supportsMobile) "应用" else "仅桌面", fontSize = 10.sp) }
                }
            }
        }
    }
}

@Composable
private fun StoreThemeCard(item: StoreItem, active: Boolean, enabled: Boolean, onApply: () -> Unit) {
    val palette = LocalPalette.current
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.name, color = palette.textPrimary.asCompose(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(item.platformLabel, color = if (item.supportsMobile) palette.success.asCompose() else palette.textTertiary.asCompose(), fontSize = 8.sp, modifier = Modifier.padding(start = 6.dp))
                    }
                    Text(item.author + " · v" + item.version, color = palette.textTertiary.asCompose(), fontSize = 8.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("bgTop", "bgMid", "accent", "cardBg").forEach { key ->
                        Box(Modifier.size(18.dp).clip(CircleShape).background(colorOf(item.palette?.optString(key))))
                    }
                }
            }
            Text(item.summary, color = palette.textSecondary.asCompose(), fontSize = 9.sp, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("★ " + item.rating + " · " + item.downloads + " 次", color = palette.textTertiary.asCompose(), fontSize = 8.sp, modifier = Modifier.weight(1f))
                if (active) Text("使用中", color = palette.accent.asCompose(), fontSize = 9.sp)
                else Button(onClick = onApply, enabled = enabled, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) { Text(if (item.supportsMobile) "应用" else "仅桌面", fontSize = 10.sp) }
            }
        }
    }
}

@Composable
fun PluginStorePage(repo: AppRepository, bridge: com.shenqing.mobile.data.BridgeSettings, onBack: () -> Unit) {
    val palette = LocalPalette.current
    val scope = rememberCoroutineScope()
    var online by remember { mutableStateOf<List<StoreItem>>(emptyList()) }
    var installed by remember { mutableStateOf<List<RemotePlugin>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var showInfo by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try { online = StoreCatalogClient.fetch().filter { it.kind == "plugin" } }
        catch (e: Exception) { error = e.message ?: "插件商店加载失败" }
        if (bridge.host.isNotBlank()) {
            runCatching { installed = BridgeClient(bridge).plugins() }
        }
        if (online.isEmpty() && error.isNotBlank()) {
            online = fallbackPlugins()
        }
        loading = false
    }

    LazyColumn(contentPadding = PaddingValues(18.dp, 12.dp, 18.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SettingsBackHeader("插件商店", "桌面插件 + 手机端 mobile 桥接", onBack) }
        item {
            Text(
                "手机端不执行桌面 Python/QML；插件在电脑端安装后，手机读取 mobile 摘要与指标。",
                color = palette.textTertiary.asCompose(), fontSize = 9.sp,
            )
        }
        item { SectionHeader("商店插件", if (loading) "加载中…" else online.size.toString() + " 个") }
        items(online.size) { index ->
            val item = online[index]
            val plugin = installed.firstOrNull { it.id == item.id }
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, color = palette.textPrimary.asCompose(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(item.author + " · v" + item.version + " · " + item.platformLabel, color = palette.textTertiary.asCompose(), fontSize = 8.sp)
                        }
                        Text(if (plugin != null) "桌面已安装" else if (item.supportsMobile) "手机可读" else "桌面插件", color = if (plugin != null) palette.success.asCompose() else palette.textTertiary.asCompose(), fontSize = 8.sp)
                    }
                    Text(item.summary, color = palette.textSecondary.asCompose(), fontSize = 9.sp, modifier = Modifier.padding(top = 5.dp))
                    plugin?.metrics?.take(3)?.forEach { metric ->
                        Text(metric.label + "：" + metric.value + " " + metric.unit, color = palette.accent.asCompose(), fontSize = 9.sp)
                    }
                    if (plugin == null) {
                        TextButton(onClick = { showInfo = true }) { Text("如何安装到电脑", fontSize = 9.sp) }
                    }
                }
            }
        }
        if (installed.isNotEmpty()) {
            item { SectionHeader("桌面已安装", installed.size.toString() + " 个") }
            items(installed.size) { index ->
                val plugin = installed[index]
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(plugin.name, color = palette.textPrimary.asCompose(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(plugin.summary.ifBlank { plugin.id }, color = palette.textTertiary.asCompose(), fontSize = 8.sp)
                        plugin.metrics.take(3).forEach { Text(it.label + "：" + it.value + " " + it.unit, color = palette.accent.asCompose(), fontSize = 9.sp) }
                    }
                }
            }
        }
    }
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text("桌面插件安装") },
            text = { Text("电脑端打开 深清 → 插件/商店，安装桌面包；安装后手机通过 AI 沟通桥接或 raw mobile 元数据读取，不执行插件代码。") },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("知道了") } },
        )
    }
}

@Composable
fun CheckUpdatePage(context: Context, repo: AppRepository, onBack: () -> Unit) {
    val palette = LocalPalette.current
    val settings by repo.settings.collectAsState()
    val current = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0.0" }
    var channel by remember(settings.updateChannel) { mutableStateOf(UpdateChannel.of(settings.updateChannel)) }
    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    var loading by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var downloadId by remember { mutableLongStateOf(-1L) }
    var downloadMessage by remember { mutableStateOf("") }
    val latestDownloadId by rememberUpdatedState(downloadId)

    LaunchedEffect(channel, refresh) {
        loading = true
        info = UpdateChecker.check(current, channel, BuildConfig.BUILD_ID)
        loading = false
    }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: android.content.Intent) {
                if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                    if (id > 0 && id == latestDownloadId) {
                        downloadMessage = UpdateDownloader.installDownloaded(context, id)
                    }
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED,
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    LazyColumn(contentPadding = PaddingValues(18.dp, 12.dp, 18.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SettingsBackHeader("检查更新", "当前 v" + current + " · build." + BuildConfig.BUILD_ID + " · " + channel.label, onBack) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                UpdateChannel.entries.forEach { item ->
                    if (channel == item) {
                        Button(
                            onClick = { channel = item; repo.setUpdateChannel(item.key) },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = palette.accent.asCompose(), contentColor = palette.onAccent.asCompose()),
                        ) { Text(item.label, fontSize = 10.sp) }
                    } else {
                        OutlinedButton(
                            onClick = { channel = item; repo.setUpdateChannel(item.key) },
                            shape = RoundedCornerShape(14.dp),
                        ) { Text(item.label, fontSize = 10.sp) }
                    }
                }
            }
        }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        when {
                            loading -> "正在检查…"
                            info?.hasUpdate == true -> "发现新版本 v" + info?.latest
                            info?.error?.isNotBlank() == true -> if (channel == UpdateChannel.STABLE) "暂无正式版" else "未找到预览版"
                            else -> "已是最新版本"
                        },
                        color = palette.textPrimary.asCompose(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    )
                    info?.releaseType?.takeIf { it.isNotBlank() }?.let {
                        Text("渠道：" + it + (if (info?.publishedAt?.isNotBlank() == true) " · " + info?.publishedAt else ""), color = palette.textTertiary.asCompose(), fontSize = 8.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    info?.error?.takeIf { it.isNotBlank() }?.let { Text(it, color = palette.textTertiary.asCompose(), fontSize = 9.sp, modifier = Modifier.padding(top = 6.dp)) }
                    info?.notes?.takeIf { it.isNotBlank() }?.let { Text(it, color = palette.textSecondary.asCompose(), fontSize = 9.sp, modifier = Modifier.padding(top = 6.dp)) }
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { refresh += 1 }, enabled = !loading, shape = RoundedCornerShape(14.dp)) { Text("重新检查", fontSize = 10.sp) }
                        if (info?.hasUpdate == true && info?.assetUrl?.isNotBlank() == true) {
                            Button(
                                onClick = {
                                    val target = info
                                    if (target != null) {
                                        if (target.assetUrl.isBlank()) {
                                            downloadMessage = "该 Release 没有 APK 资产"
                                        } else if (!UpdateDownloader.canInstall(context)) {
                                            UpdateDownloader.openInstallSettings(context)
                                            downloadMessage = "请允许深清安装未知应用后，再点一次下载"
                                        } else {
                                            runCatching { UpdateDownloader.enqueue(context, target) }
                                                .onSuccess { id -> downloadId = id; downloadMessage = "开始下载，完成后自动打开系统安装器" }
                                                .onFailure { downloadMessage = it.message ?: "无法开始下载" }
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = palette.accent.asCompose(), contentColor = palette.onAccent.asCompose()),
                            ) { Text("下载并安装", fontSize = 10.sp) }
                        }
                        if (info?.url?.isNotBlank() == true) {
                            OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info?.url))) }, shape = RoundedCornerShape(14.dp)) { Text("打开下载页", fontSize = 10.sp) }
                        }
                    }
                    if (downloadMessage.isNotBlank()) Text(downloadMessage, color = palette.accent.asCompose(), fontSize = 9.sp, modifier = Modifier.padding(top = 6.dp))
                    Text("预览渠道取 GitHub 预发布构建；正式版渠道只取非 prerelease 的 Release。", color = palette.textTertiary.asCompose(), fontSize = 8.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
fun AboutPage(context: Context, onBack: () -> Unit) {
    val palette = LocalPalette.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0.0" }
    LazyColumn(contentPadding = PaddingValues(18.dp, 12.dp, 18.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SettingsBackHeader("关于", "深清 · 手机端", onBack) }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painterResource(R.mipmap.ic_launcher), contentDescription = "深清", modifier = Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)))
                    Spacer(Modifier.height(10.dp))
                    Text("深清手机端", color = palette.textPrimary.asCompose(), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("v" + version + " · Android 原生", color = palette.textTertiary.asCompose(), fontSize = 9.sp)
                    Text("深度锁机、清番、主题与桌面插件互通。", color = palette.textSecondary.asCompose(), fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LinkRow("GitHub · Clarity-APP", "https://github.com/Kryon2025/Clarity-APP") { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                    LinkRow("桌面深清", "https://github.com/Kryon2025/Clarity") { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                    LinkRow("主题包规范", "docs/主题包规范.md") { }
                    Text("Android 没有 root / Device Owner 时无法做到绝对不可退出；OPPO 超禅需系统禅定空间支持。", color = palette.textTertiary.asCompose(), fontSize = 8.sp)
                }
            }
        }
    }
}

@Composable
private fun LinkRow(title: String, url: String, onClick: (String) -> Unit) {
    val palette = LocalPalette.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onClick(url) }.padding(vertical = 4.dp)) {
        Text(title, color = palette.accent.asCompose(), fontSize = 10.sp, modifier = Modifier.weight(1f))
        Text("↗", color = palette.textTertiary.asCompose(), fontSize = 10.sp)
    }
}

private fun colorOf(value: String?): Color = runCatching {
    Color(android.graphics.Color.parseColor(value))
}.getOrDefault(Color.Gray)

private fun fallbackPlugins(): List<StoreItem> = listOf(
    StoreItem("com.shenqing.aichat", "plugin", "AI 沟通", "Clarity Studio", "把桌面 AI 对话与插件工作区带到手机", description = "通过局域网桥接读取空间、统计、插件 mobile 指标与主题包。", version = "1.0.0", platforms = listOf("desktop", "mobile")),
    StoreItem("com.shenqing.tomatodo", "plugin", "番茄同步", "Clarity Studio", "番茄ToDo 专注历史与今日统计", description = "桌面插件安装后，手机读取 mobile 指标。", version = "1.0.0", platforms = listOf("desktop", "mobile")),
    StoreItem("com.shenqing.muyu", "plugin", "暮语", "Clarity Studio", "桌面专注伴侣插件", description = "手机端只读 mobile 摘要。", version = "1.0.0", platforms = listOf("desktop")),
)
