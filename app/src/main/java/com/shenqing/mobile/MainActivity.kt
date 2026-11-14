package com.shenqing.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.shenqing.mobile.data.AppRepository
import com.shenqing.mobile.data.Space
import com.shenqing.mobile.data.ThemeRepository
import com.shenqing.mobile.data.Tier
import com.shenqing.mobile.service.FocusAccessibilityService
import com.shenqing.mobile.ui.asCompose
import com.shenqing.mobile.ui.screens.PluginsScreen
import com.shenqing.mobile.ui.screens.SettingsScreen
import com.shenqing.mobile.ui.screens.SpacesScreen
import com.shenqing.mobile.ui.screens.StatsScreen
import com.shenqing.mobile.ui.theme.LocalPalette
import com.shenqing.mobile.ui.theme.ShenQingTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var quickSpaceId by mutableStateOf<String?>(null)
    private var quickMinutes by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = AppRepository.get(this)
        val themeRepo = ThemeRepository.get(this)
        if (savedInstanceState == null) {
            if (repo.active.value != null && repo.active.value?.tier?.locked != true) {
                // 保留进行中的会话，不强制跳转；用户可从首页卡片回到专注。
            }
        }
        quickSpaceId = intent?.getStringExtra("space_id")
        quickMinutes = intent?.getIntExtra("minutes", 0) ?: 0
        if (intent?.action == ACTION_QUICK_FOCUS) {
            quickSpaceId = quickSpaceId ?: repo.settings.value.quickFocusSpaceId
            quickMinutes = quickMinutes.takeIf { it > 0 } ?: repo.settings.value.quickFocusMinutes
        }
        setContent {
            val palette = themeRepo.current.collectAsState().value
            val fontScale = repo.fontScale.collectAsState().value
            ShenQingTheme(palette, fontScale) {
                MainScreen(
                    repo = repo,
                    themeRepo = themeRepo,
                    quickSpaceId = quickSpaceId,
                    quickMinutes = quickMinutes,
                    onQuickConsumed = { quickSpaceId = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_QUICK_FOCUS) {
            quickSpaceId = intent.getStringExtra("space_id") ?: AppRepository.get(this).settings.value.quickFocusSpaceId
            quickMinutes = intent.getIntExtra("minutes", 0).takeIf { it > 0 } ?: 25
        }
    }

    companion object {
        const val ACTION_QUICK_FOCUS = "com.shenqing.mobile.action.QUICK_FOCUS"
    }
}

@Composable
private fun MainScreen(
    repo: AppRepository,
    themeRepo: ThemeRepository,
    quickSpaceId: String?,
    quickMinutes: Int,
    onQuickConsumed: () -> Unit,
) {
    val palette = LocalPalette.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var blockedSpace by remember { mutableStateOf<Space?>(null) }
    val notificationPermission = rememberLauncherForActivityResultContract()

    fun startFocus(space: Space, minutes: Int) {
        if (space.tier.locked && !FocusAccessibilityService.isEnabled(context)) {
            blockedSpace = space
            return
        }
        repo.startFocus(space, minutes)
        val intent = Intent(context, FocusActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        context.startActivity(intent)
    }

    LaunchedEffect(quickSpaceId) {
        val id = quickSpaceId ?: return@LaunchedEffect
        val space = repo.spaceById(id)
        val minutes = quickMinutes.takeIf { it > 0 } ?: space.minutes
        onQuickConsumed()
        if (repo.active.value != null) {
            context.startActivity(Intent(context, FocusActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            startFocus(space, minutes)
        }
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        com.shenqing.mobile.widget.FocusWidgetProvider.update(context)
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(palette.bgTop), Color(palette.color("bgCenter", palette.bgBottom)), Color(palette.bgBottom)))
        )
    ) {
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            when (tab) {
                0 -> SpacesScreen(
                    repo = repo,
                    onStart = { space, minutes -> startFocus(space, minutes) },
                    onOpenFocus = {
                        context.startActivity(Intent(context, FocusActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                    onOpenSettings = { tab = 3 },
                    onOpenStats = { tab = 1 },
                )
                1 -> StatsScreen(repo)
                2 -> PluginsScreen(repo, themeRepo, onOpenSettings = { tab = 3 })
                else -> SettingsScreen(repo, themeRepo)
            }
        }
        NavigationBar(
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),
            containerColor = Color(palette.cardBg).copy(alpha = 0.92f),
            tonalElevation = 0.dp,
        ) {
            val items = listOf(
                Triple("空间", Icons.Outlined.Home, 0),
                Triple("数据", Icons.Outlined.BarChart, 1),
                Triple("插件", Icons.Outlined.Extension, 2),
                Triple("设置", Icons.Outlined.Settings, 3),
            )
            items.forEach { (label, icon, index) ->
                NavigationBarItem(
                    selected = tab == index,
                    onClick = { tab = index },
                    icon = { Icon(icon, contentDescription = label) },
                    label = { Text(label, fontSize = 9.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = palette.accent.asCompose(),
                        selectedTextColor = palette.accent.asCompose(),
                        indicatorColor = Color(palette.accent).copy(alpha = 0.12f),
                        unselectedIconColor = palette.textTertiary.asCompose(),
                        unselectedTextColor = palette.textTertiary.asCompose(),
                    ),
                )
            }
        }
    }

    blockedSpace?.let { space ->
        AlertDialog(
            onDismissRequest = { blockedSpace = null },
            title = { Text("深清/覆清需要无障碍服务") },
            text = { Text("深度锁机通过无障碍服务拦截其他应用与桌面。开启「深清深度锁机」后返回再开始；浅清不需要此权限。") },
            confirmButton = {
                TextButton(onClick = {
                    blockedSpace = null
                    FocusAccessibilityService.openSettings(context)
                }) { Text("去开启") }
            },
            dismissButton = { TextButton(onClick = { blockedSpace = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun rememberLauncherForActivityResultContract(): androidx.activity.result.ActivityResultLauncher<String> {
    return androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }
}
