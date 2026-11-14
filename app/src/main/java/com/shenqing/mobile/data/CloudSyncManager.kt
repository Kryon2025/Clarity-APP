package com.shenqing.mobile.data

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 手机端参与桌面 v0.20 账户同步：同一商店账号、同一 /api/sync 快照。
 *
 * 关键安全/兼容约束：
 * - 只往 payload.settings 合并手机主题/字号；保留桌面端 spaces/stats 等其他字段不动；
 * - 不上传密码、AI Key、桥接令牌；
 * - 拉取时安装桌面 custom_themes 并应用 theme_mode / ui_font_scale；
 * - 上传前先拉取最新 revision，409 时再拉一次重试。
 */
class CloudSyncManager(
    private val context: Context,
    private val repo: AppRepository = AppRepository.get(context),
    private val themeRepo: ThemeRepository = ThemeRepository.get(context),
) {
    val deviceName: String by lazy {
        val model = Build.MODEL?.trim().orEmpty()
        (if (model.isBlank()) "Android" else model).take(64)
    }

    suspend fun login(url: String, email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            repo.cloudBusy(false, error = "请填写邮箱和密码")
            return
        }
        repo.cloudBusy(true, status = "正在登录商店账号…")
        try {
            val client = CloudSyncClient(url)
            val token = client.login(email, password)
            val current = repo.cloud.value
            repo.updateCloud(
                current.copy(
                    url = CloudSyncClient.normalize(url),
                    email = email.trim(),
                    token = token,
                    device = deviceName,
                    busy = false,
                    status = "已登录：" + email.trim(),
                    error = "",
                )
            )
        } catch (error: Exception) {
            repo.cloudBusy(false, error = error.message ?: "登录失败")
        }
    }

    fun logout() {
        repo.cloudLoggedOut()
        repo.cloudBusy(false, status = "已退出账户")
    }

    suspend fun pull() = syncBlock("正在拉取云端设置…") {
        val account = requireAccount()
        val client = CloudSyncClient(account.url, account.token)
        val snapshot = client.pull()
        applySettings(snapshot.payload)
        repo.updateCloud(
            account.copy(
                revision = snapshot.revision,
                lastAt = nowText(),
                busy = false,
                status = "已拉取云端设置（revision " + snapshot.revision + "）",
                error = "",
            )
        )
    }

    suspend fun push() = syncBlock("正在上传手机设置…") {
        val account = requireAccount()
        var client = CloudSyncClient(account.url, account.token)
        var snapshot = client.pull()
        var payload = mergeLocalSettings(snapshot.payload)
        var result = client.push(payload, snapshot.revision, account.device.ifBlank { deviceName })
        if (!result.ok) {
            // 409：远端已被桌面推进；重新拉取最新 payload 再合并一次。
            snapshot = client.pull()
            payload = mergeLocalSettings(snapshot.payload)
            result = client.push(payload, snapshot.revision, account.device.ifBlank { deviceName })
        }
        if (!result.ok) throw CloudSyncException("云端版本冲突，请重新同步")
        repo.updateCloud(
            account.copy(
                revision = result.revision,
                lastAt = nowText(),
                busy = false,
                status = "已上传手机主题与字号（revision " + result.revision + "）",
                error = "",
            )
        )
    }

    suspend fun syncNow() = syncBlock("正在与云端同步…") {
        val account = requireAccount()
        val client = CloudSyncClient(account.url, account.token)
        var snapshot = client.pull()
        applySettings(snapshot.payload)
        var payload = mergeLocalSettings(snapshot.payload)
        var result = client.push(payload, snapshot.revision, account.device.ifBlank { deviceName })
        if (!result.ok) {
            snapshot = client.pull()
            applySettings(snapshot.payload)
            payload = mergeLocalSettings(snapshot.payload)
            result = client.push(payload, snapshot.revision, account.device.ifBlank { deviceName })
        }
        if (!result.ok) throw CloudSyncException("云端版本冲突，请重试")
        repo.updateCloud(
            account.copy(
                revision = result.revision,
                lastAt = nowText(),
                busy = false,
                status = "同步完成（revision " + result.revision + "）",
                error = "",
            )
        )
    }

    private suspend fun syncBlock(status: String, block: suspend () -> Unit) {
        repo.cloudBusy(true, status = status)
        try {
            block()
        } catch (error: Exception) {
            repo.cloudBusy(false, error = error.message ?: "同步失败")
        }
    }

    private fun requireAccount(): CloudAccount {
        val account = repo.cloud.value
        if (account.url.isBlank()) throw CloudSyncException("请先填写商店服务器地址")
        if (account.token.isBlank()) throw CloudSyncException("请先登录商店账号")
        return account
    }

    private fun applySettings(payload: JSONObject) {
        val settings = payload.optJSONObject("settings") ?: return
        val custom = settings.optJSONObject("custom_themes")
        if (custom != null) {
            custom.keys().forEach { id ->
                val item = custom.optJSONObject(id) ?: return@forEach
                val palette = item.optJSONObject("palette") ?: return@forEach
                if (palette.length() >= 6) {
                    themeRepo.importPaletteTheme(
                        id,
                        item.optString("name", id),
                        item.optString("description", ""),
                        palette,
                    )
                }
            }
        }
        val mode = settings.optString("theme_mode", "").trim()
        if (mode.startsWith("custom:")) {
            val id = mode.removePrefix("custom:")
            if (!themeRepo.isBuiltIn(id) && themeRepo.paletteFor(id).id == id) themeRepo.apply(id)
        } else if (mode in listOf("solid", "light", "glass", "ocean")) {
            themeRepo.apply(mode)
        }
        val scale = settings.optDouble("ui_font_scale", repo.fontScale.value.toDouble()).toFloat()
        repo.setFontScale(scale)
    }

    private fun mergeLocalSettings(remote: JSONObject): JSONObject {
        val payload = JSONObject(remote.toString())
        val settings = payload.optJSONObject("settings") ?: JSONObject().also { payload.put("settings", it) }
        settings.put("theme_mode", themeRepo.currentThemeMode())
        settings.put("ui_font_scale", repo.fontScale.value.toDouble())
        val themes = themeRepo.mobileThemesForCloud()
        if (themes.length() > 0) {
            val existing = settings.optJSONObject("custom_themes") ?: JSONObject()
            themes.keys().forEach { key -> existing.put(key, themes.get(key)) }
            settings.put("custom_themes", existing)
        }
        return payload
    }

    private fun nowText(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())
}
