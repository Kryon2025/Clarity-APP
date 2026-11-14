package com.shenqing.mobile.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shenqing.mobile.data.AppRepository
import com.shenqing.mobile.data.BridgeClient
import com.shenqing.mobile.data.RemotePlugin
import com.shenqing.mobile.data.ThemeEntry
import com.shenqing.mobile.data.ThemeRef
import com.shenqing.mobile.data.ThemeRepository
import com.shenqing.mobile.ui.AppCard
import com.shenqing.mobile.ui.SectionHeader
import com.shenqing.mobile.ui.asCompose
import com.shenqing.mobile.ui.theme.LocalPalette
import kotlinx.coroutines.launch

@Composable
fun PluginsScreen(
    repo: AppRepository,
    themeRepo: ThemeRepository,
    onOpenSettings: () -> Unit,
) {
    val palette = LocalPalette.current
    val bridge by repo.bridge.collectAsState()
    val localEntries by themeRepo.entries.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var plugins by remember { mutableStateOf<List<RemotePlugin>>(emptyList()) }
    var remoteThemes by remember { mutableStateOf<List<ThemeRef>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    suspend fun refresh() {
        if (bridge.host.isBlank()) {
            error = "还没有连接桌面。去设置里填电脑地址与配对令牌。"
            return
        }
        loading = true
        error = ""
        try {
            val client = BridgeClient(bridge)
            if (!client.ping()) throw IllegalStateException("桌面桥接协议不兼容")
            val manifest = client.manifest()
            repo.setBridgeConnected(
                true, "",
                manifest.optJSONObject("plugin")?.optString("name", "") ?: "",
                manifest.optJSONObject("plugin")?.optString("version", "") ?: "",
            )
            plugins = client.plugins()
            remoteThemes = client.themes()
        } catch (e: Exception) {
            error = e.message ?: "连接失败"
            repo.setBridgeConnected(false, error)
        } finally {
            loading = false
        }
    }

    LaunchedEffect(bridge.host, bridge.port, bridge.token) {
        if (bridge.host.isNotBlank() && bridge.token.isNotBlank()) refresh()
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.onSuccess { text ->
                if (text != null) {
                    themeRepo.importPack(text).onSuccess { entry ->
                        if (entry.supportsMobile) themeRepo.apply(entry.id)
                        else error = "该主题包声明为仅桌面（platforms 不含 mobile），已保留但不自动应用"
                    }.onFailure { error = it.message ?: "主题包无效" }
                }
            }.onFailure { error = it.message ?: "无法读取主题包" }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text("插件", color = palette.textTertiary.asCompose(), fontSize = 9.sp, letterSpacing = 2.sp)
                Text("桌面能力，手机上也能用", color = palette.textPrimary.asCompose(), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("只读取插件声明的 mobile 摘要 / 指标与主题包；不加载桌面 Python / QML。", color = palette.textTertiary.asCompose(), fontSize = 10.sp)
            }
        }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(9.dp).clip(RoundedCornerShape(99.dp)).background(
                            if (bridge.connected) palette.success.asCompose() else palette.textTertiary.asCompose()
                        )
                    )
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(
                            if (bridge.connected) "桌面在线 · " + bridge.pluginName + " v" + bridge.pluginVersion else "未连接桌面桥接",
                            color = palette.textPrimary.asCompose(), fontSize = 11.sp,
                        )
                        Text(
                            if (bridge.host.isBlank()) "去设置填地址与配对令牌" else bridge.host + ":" + bridge.port,
                            color = palette.textTertiary.asCompose(), fontSize = 9.sp,
                        )
                    }
                    TextButton(onClick = { if (bridge.host.isBlank()) onOpenSettings() else scope.launch { refresh() } }) {
                        Text(if (bridge.host.isBlank()) "去连接" else "刷新", color = palette.accent.asCompose(), fontSize = 10.sp)
                    }
                }
            }
        }
        if (loading) item { Text("正在读取桌面插件…", color = palette.textTertiary.asCompose(), fontSize = 10.sp) }
        if (error.isNotBlank()) {
            item {
                Text(error, color = palette.danger.asCompose(), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 2.dp))
            }
        }
        item { SectionHeader("桌面插件", "来自 AI 沟通插件的 /plugins") }
        if (plugins.isEmpty()) {
            item { Text("没有插件数据。连接桌面后刷新；插件需要在 plugin.json 里声明 mobile 摘要。", color = palette.textTertiary.asCompose(), fontSize = 10.sp) }
        } else {
            items(plugins) { plugin -> PluginCard(plugin) }
        }
        item {
            SectionHeader(
                "主题包",
                "手机 / 桌面共用的 .sqtheme",
                trailing = {
                    Icon(
                        Icons.Outlined.UploadFile, contentDescription = "导入",
                        tint = palette.accent.asCompose(),
                        modifier = Modifier.clickable { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    )
                },
            )
        }
        items(localEntries) { entry -> ThemeRow(entry) { if (entry.supportsMobile) themeRepo.apply(entry.id) else error = "该主题包仅桌面，手机不能应用" } }
        items(remoteThemes) { ref ->
            ThemeRow(
                ThemeEntry(ref.id, ref.name, ref.author, ref.version, false, "来自桌面", platforms = ref.platforms),
            ) {
                if (!ref.supportsMobile) { error = "该主题包声明为仅桌面，手机不能应用"; return@ThemeRow }
                scope.launch {
                    runCatching {
                        val json = BridgeClient(bridge).themePack(ref.id)
                        if (json != null) themeRepo.importPack(json).getOrThrow()
                        themeRepo.apply(ref.id)
                    }.onFailure { error = it.message ?: "主题包下载失败" }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                Text("  导入 .sqtheme 主题包", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun PluginCard(plugin: RemotePlugin) {
    val palette = LocalPalette.current
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Extension, contentDescription = null, tint = palette.accent.asCompose(), modifier = Modifier.size(18.dp))
                Text(plugin.name, color = palette.textPrimary.asCompose(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp).weight(1f))
                Text("v" + plugin.version, color = palette.textTertiary.asCompose(), fontSize = 9.sp)
            }
            if (plugin.summary.isNotBlank()) {
                Text(plugin.summary, color = palette.textSecondary.asCompose(), fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
            }
            if (plugin.metrics.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 10.dp)) {
                    plugin.metrics.forEach { metric ->
                        Column {
                            Text(metric.label, color = palette.textTertiary.asCompose(), fontSize = 8.sp)
                            Text(metric.value + metric.unit, color = palette.accent.asCompose(), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Text(
                if (plugin.enabled) "已启用" else "已停用" + (if (plugin.error.isNotBlank()) " · " + plugin.error else ""),
                color = if (plugin.enabled) palette.success.asCompose() else palette.textTertiary.asCompose(),
                fontSize = 8.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun ThemeRow(entry: ThemeEntry, onApply: () -> Unit) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    val applied = ThemeRepository.get(context).current.collectAsState().value.id == entry.id
    AppCard(Modifier.fillMaxWidth().clickable(onClick = onApply)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                    .background(Color(ThemeRepository.get(context).paletteFor(entry.id).accent))
            )
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(entry.name, color = palette.textPrimary.asCompose(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    (if (entry.builtIn) "内置" else "主题包") + " · " + entry.author + (if (entry.version.isNotBlank()) " · " + entry.version else ""),
                    color = palette.textTertiary.asCompose(), fontSize = 9.sp,
                )
            }
            Text(if (applied) "使用中" else "应用", color = if (applied) palette.accent.asCompose() else palette.textSecondary.asCompose(), fontSize = 10.sp)
        }
    }
}
