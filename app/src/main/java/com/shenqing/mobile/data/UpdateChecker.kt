package com.shenqing.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

enum class UpdateChannel(val key: String, val label: String) {
    PREVIEW("preview", "预览渠道"),
    STABLE("stable", "正式版渠道");

    companion object {
        fun of(value: String?): UpdateChannel = if (value == "stable") STABLE else PREVIEW
    }
}

data class UpdateInfo(
    val current: String,
    val channel: UpdateChannel,
    val latest: String = "",
    val url: String = "",
    val notes: String = "",
    val hasUpdate: Boolean = false,
    val error: String = "",
    val releaseType: String = "",
    val publishedAt: String = "",
    val assetUrl: String = "",
    val assetName: String = "",
)

object UpdateChecker {
    private const val API = "https://api.github.com/repos/Kryon2025/Clarity-APP/releases?per_page=20"

    suspend fun check(current: String, channel: UpdateChannel, buildId: String = "local"): UpdateInfo = withContext(Dispatchers.IO) {
        val connection = try {
            (URL(API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "ShenQing-Android/1.0")
            }
        } catch (error: Exception) {
            return@withContext UpdateInfo(current, channel, error = "检查更新失败：" + (error.message ?: ""))
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                return@withContext UpdateInfo(current, channel, error = "GitHub 返回 HTTP " + code)
            }
            val text = connection.inputStream.bufferedReader().use(BufferedReader::readText)
            val releases = JSONArray(text)
            val matched = (0 until releases.length()).mapNotNull { index ->
                releases.optJSONObject(index)?.takeIf { item ->
                    !item.optBoolean("draft", false) &&
                        if (channel == UpdateChannel.STABLE) !item.optBoolean("prerelease", false)
                        else item.optBoolean("prerelease", false)
                }
            }.firstOrNull()
                ?: return@withContext UpdateInfo(
                    current,
                    channel,
                    error = if (channel == UpdateChannel.STABLE) {
                        "暂无正式版发布；当前只有预览渠道构建"
                    } else {
                        "暂无预览版发布"
                    },
                )

            val latest = matched.optString("tag_name", "").removePrefix("v").removePrefix("V")
            val url = matched.optString("html_url", "")
            val notes = matched.optString("body", "").take(4000)
            val isPre = matched.optBoolean("prerelease", false)
            val assets = matched.optJSONArray("assets")
            var assetUrl = ""
            var assetName = ""
            if (assets != null) {
                for (index in 0 until assets.length()) {
                    val asset = assets.optJSONObject(index) ?: continue
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        assetUrl = asset.optString("browser_download_url", "")
                        assetName = name
                        break
                    }
                }
            }
            val latestBuild = latest.substringAfter("-build.", "").toIntOrNull()
            val installedBuild = buildId.toIntOrNull()
            val hasUpdate = if (channel == UpdateChannel.PREVIEW && latestBuild != null && installedBuild != null) {
                latestBuild > installedBuild
            } else {
                latest.isNotBlank() && compare(latest, current) > 0
            }
            UpdateInfo(
                current = current,
                channel = channel,
                latest = latest,
                url = url,
                notes = notes,
                hasUpdate = hasUpdate,
                releaseType = if (isPre) "预览版" else "正式版",
                publishedAt = matched.optString("published_at", ""),
                assetUrl = assetUrl,
                assetName = assetName,
            )
        } catch (error: Exception) {
            UpdateInfo(current, channel, error = "检查更新失败：" + (error.message ?: ""))
        } finally {
            connection.disconnect()
        }
    }

    private fun compare(left: String, right: String): Int {
        val a = left.split('.', '-', '+').mapNotNull { it.toIntOrNull() }
        val b = right.split('.', '-', '+').mapNotNull { it.toIntOrNull() }
        for (index in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(index) { 0 }
            val y = b.getOrElse(index) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }
}
