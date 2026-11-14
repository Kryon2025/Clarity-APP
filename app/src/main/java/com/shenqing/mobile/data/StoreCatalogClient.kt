package com.shenqing.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

data class StoreItem(
    val id: String,
    val kind: String,
    val name: String,
    val author: String = "",
    val summary: String = "",
    val description: String = "",
    val version: String = "",
    val rating: Double = 0.0,
    val downloads: Int = 0,
    val tags: List<String> = emptyList(),
    val palette: JSONObject? = null,
    val downloadUrl: String = "",
)

class StoreCatalogException(message: String) : Exception(message)

object StoreCatalogClient {
    const val DEFAULT_URL = "https://raw.githubusercontent.com/Kryon2025/Clarity/store/catalog.json"

    suspend fun fetch(url: String = DEFAULT_URL, timeoutMs: Int = 8000): List<StoreItem> = withContext(Dispatchers.IO) {
        val target = url.trim().ifBlank { DEFAULT_URL }
        val connection = try {
            (URL(target).openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "ShenQing-Android/1.0")
            }
        } catch (error: Exception) {
            throw StoreCatalogException("商店目录地址不正确：" + (error.message ?: ""))
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw StoreCatalogException("商店目录 HTTP " + code)
            val text = connection.inputStream.bufferedReader().use(BufferedReader::readText)
            val root = JSONObject(text)
            val items = root.optJSONArray("items") ?: return@withContext emptyList()
            (0 until items.length()).mapNotNull { index ->
                val item = items.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("id", "")
                val name = item.optString("name", "")
                if (id.isBlank() || name.isBlank()) return@mapNotNull null
                val tags = item.optJSONArray("tags")?.let { array ->
                    (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
                } ?: emptyList()
                StoreItem(
                    id = id,
                    kind = item.optString("kind", "theme"),
                    name = name,
                    author = item.optString("author", ""),
                    summary = item.optString("summary", ""),
                    description = item.optString("description", ""),
                    version = item.optString("version", ""),
                    rating = item.optDouble("rating", 0.0),
                    downloads = item.optInt("downloads", 0),
                    tags = tags,
                    palette = item.optJSONObject("palette"),
                    downloadUrl = item.optString("download_url", ""),
                )
            }
        } catch (error: StoreCatalogException) {
            throw error
        } catch (error: Exception) {
            throw StoreCatalogException(error.message ?: "读取商店目录失败")
        } finally {
            connection.disconnect()
        }
    }
}
