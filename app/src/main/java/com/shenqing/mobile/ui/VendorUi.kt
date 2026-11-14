package com.shenqing.mobile.ui

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shenqing.mobile.data.ThemePalette

/**
 * 厂商视觉适配。
 *
 * 重要：Android 没有公开 API 让第三方 App 直接调用 ColorOS/HyperOS 等系统“液态玻璃/流光”控件；
 * 这里用 Compose 做同风格的半透明玻璃卡片、双色描边和 OPPO 流光强调色，属于自适应仿真，
 * 不会加载厂商私有控件。
 */
object VendorUi {
    enum class Skin(val label: String) {
        OPPO("ColorOS 流光"),
        XIAOMI("HyperOS 玻璃"),
        HONOR("MagicOS 玻璃"),
        SAMSUNG("One UI 玻璃"),
        GENERIC("原生玻璃"),
    }

    fun skin(): Skin {
        val text = (Build.MANUFACTURER + " " + Build.BRAND + " " + Build.PRODUCT).lowercase()
        return when {
            text.contains("oppo") || text.contains("oplus") || text.contains("oneplus") || text.contains("realme") -> Skin.OPPO
            text.contains("xiaomi") || text.contains("redmi") || text.contains("poco") -> Skin.XIAOMI
            text.contains("honor") -> Skin.HONOR
            text.contains("samsung") -> Skin.SAMSUNG
            else -> Skin.GENERIC
        }
    }

    fun isOppo(): Boolean = skin() == Skin.OPPO

    fun cornerRadius() = when (skin()) {
        Skin.OPPO -> 26.dp
        Skin.XIAOMI -> 24.dp
        else -> 22.dp
    }

    fun glassColors(palette: ThemePalette): List<Color> = when (skin()) {
        Skin.OPPO -> listOf(
            Color.White.copy(alpha = 0.34f),
            Color(palette.gradA).copy(alpha = 0.42f),
            Color(palette.accent).copy(alpha = 0.34f),
            Color(palette.accentSoft).copy(alpha = 0.28f),
            Color.White.copy(alpha = 0.12f),
        )
        Skin.XIAOMI -> listOf(
            Color.White.copy(alpha = 0.30f),
            Color(palette.accent).copy(alpha = 0.24f),
            Color.White.copy(alpha = 0.08f),
        )
        else -> listOf(
            Color.White.copy(alpha = 0.28f),
            Color(palette.accent).copy(alpha = 0.20f),
            Color.White.copy(alpha = 0.06f),
        )
    }

    fun surfaceAlpha(): Float = if (isOppo()) 0.70f else 0.80f
}
