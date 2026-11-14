package com.shenqing.mobile.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/**
 * OPPO / 一加 / realme 的「禅定空间」超禅接入。
 *
 * 公开资料里 Zen Space 的包名是 com.oneplus.brickmode；没有公开的本地 API。
 * 因此这里做两件事：
 * 1. 用 launch intent 打开禅定空间，并尽量带上时长/超禅 extras（官方若忽略则不影响打开）；
 * 2. 无障碍服务尝试按文本点「超禅」→ 对应时长 → 「开始」，属于尽力而为，无法保证所有 ColorOS 版本。
 *
 * 在没有真机/官方 API 前，调用失败会自动回退深清自己的全屏覆盖层。
 */
object OppoZen {
    @Volatile var activePackage: String? = null
        private set

    private val candidates = listOf(
        "com.oneplus.brickmode",
        "com.oplus.brickmode",
        "com.coloros.brickmode",
        "com.oppo.brickmode",
        "com.oplus.zenmode",
        "com.coloros.zenmode",
    )

    /** 超禅可选时长（分钟）。 */
    val supportedMinutes = listOf(1, 20, 30, 40, 60, 90, 120)

    data class Result(
        val launched: Boolean,
        val packageName: String? = null,
        val minutes: Int = 0,
        val message: String = "",
    )

    fun isOppoFamily(): Boolean {
        val text = (Build.MANUFACTURER + " " + Build.BRAND + " " + Build.PRODUCT).lowercase()
        return text.contains("oppo") || text.contains("oplus") ||
            text.contains("oneplus") || text.contains("realme")
    }

    fun findPackage(context: Context): String? {
        val pm = context.packageManager
        return candidates.firstOrNull { pkg ->
            runCatching {
                pm.getLaunchIntentForPackage(pkg) != null ||
                    pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES) != null
            }.getOrDefault(false)
        }
    }

    fun available(context: Context): Boolean = isOppoFamily() && findPackage(context) != null

    fun isZenPackage(packageName: String?): Boolean = packageName != null && packageName in candidates

    /** 25 -> 30；40 -> 40；45 -> 60；90 -> 90；超过 120 用 120。 */
    fun mapMinutes(minutes: Int): Int =
        supportedMinutes.firstOrNull { it >= minutes } ?: supportedMinutes.last()

    fun start(context: Context, minutes: Int): Result {
        val pkg = findPackage(context)
            ?: return Result(false, message = "未找到禅定空间（com.oneplus.brickmode），回退深清锁机")
        val mapped = mapMinutes(minutes)
        val pm = context.packageManager
        val intent = runCatching { pm.getLaunchIntentForPackage(pkg) }.getOrNull()
            ?: Intent(Intent.ACTION_MAIN).setPackage(pkg)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        // 尽力传递；官方未公开 extras，禅定空间不支持时会忽略。
        intent.putExtra("minutes", mapped)
        intent.putExtra("duration", mapped)
        intent.putExtra("deep_zen", true)
        intent.putExtra("super_zen", true)
        intent.putExtra("mode", "deep")
        val launched = runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
        if (!launched) {
            return Result(false, pkg, mapped, "无法启动禅定空间，回退深清锁机")
        }
        activePackage = pkg
        FocusAccessibilityService.instance?.startZenAutomation(pkg, mapped)
        val note = if (mapped != minutes) "OPPO 超禅仅支持 $supportedMinutes 分钟，已将 $minutes 映射为 $mapped 分钟" else "已请求 OPPO 超禅 $mapped 分钟"
        return Result(true, pkg, mapped, note)
    }

    fun clearActive() {
        activePackage = null
    }
}
