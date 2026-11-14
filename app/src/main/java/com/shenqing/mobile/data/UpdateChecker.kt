package com.shenqing.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val current: String,
    val latest: String = "",
    val url: String = "",
    val notes: String = "",
    val hasUpdate: Boolean = false,
    val error: String = "",
)

object UpdateChecker {
    private const val API = "https://api.github.com/repos/Kryon2025/Clarity-APP/releases?per_page=10"

    suspend fun check(current: String): UpdateInfo = withContext(Dispatchers.IO) {
        val connection = try {
            (URL(API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "ShenQing-Android/1.0")
            }
        } catch (error: Exception) {
            return@withContext UpdateInfo(current, error = "检查更新失败：" + (error.message ?: ""))
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                return@withContext UpdateInfo(current, error = "GitHub 返回 HTTP " + code)
            }
            val text = connection.inputStream.bufferedReader().use(BufferedReader::readText)
            val releases = org.json.JSONArray(text)
            if (releases.length() == 0) {
                return@withContext UpdateInfo(current, error = "暂无 GitHub Release；源码已同步 main 分支")
            }
            val data = releases.optJSONObject(0) ?: return@withContext UpdateInfo(current, error = "Release 数据为空")
            val latest = data.optString("tag_name", "").removePrefix("v").removePrefix("V")
            val url = data.optString("html_url", "")
            val notes = data.optString("body", "").take(4000)
            UpdateInfo(
                current = current,
                latest = latest,
                url = url,
                notes = notes,
                hasUpdate = latest.isNotBlank() && compare(latest, current) > 0,
            )
        } catch (error: Exception) {
            UpdateInfo(current, error = "检查更新失败：" + (error.message ?: ""))
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
