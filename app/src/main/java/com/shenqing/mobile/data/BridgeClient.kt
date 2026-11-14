package com.shenqing.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

class BridgeException(message: String) : Exception(message)

class BridgeClient(private val settings: BridgeSettings) {
    private val base: String = buildBase(settings.host, settings.port)
    private val authToken: String = settings.token.ifBlank { pairingToken(settings.host) }

    suspend fun ping(): Boolean = withContext(Dispatchers.IO) {
        val root = base
        val json = requestAbsolute(root + "/ping", authenticated = false) ?: return@withContext false
        json.optJSONObject("protocol")?.optInt("major") == 1
    }

    suspend fun manifest(): JSONObject = withContext(Dispatchers.IO) {
        request("/shenqing/v1/manifest") ?: throw BridgeException("桥接没有返回清单")
    }

    suspend fun settings(): JSONObject = withContext(Dispatchers.IO) {
        request("/shenqing/v1/settings") ?: throw BridgeException("桥接没有返回设置")
    }

    suspend fun plugins(): List<RemotePlugin> = withContext(Dispatchers.IO) {
        val json = request("/shenqing/v1/plugins") ?: return@withContext emptyList()
        val array = json.optJSONArray("plugins") ?: return@withContext emptyList()
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val mobile = item.optJSONObject("mobile")
            val metrics = mobile?.optJSONArray("metrics")?.let { list ->
                (0 until list.length()).mapNotNull { i ->
                    val metric = list.optJSONObject(i) ?: return@mapNotNull null
                    Metric(
                        key = metric.optString("key"),
                        label = metric.optString("label", metric.optString("key")),
                        value = metric.optString("value", ""),
                        unit = metric.optString("unit", ""),
                    )
                }
            } ?: emptyList()
            RemotePlugin(
                id = item.optString("id"),
                name = item.optString("name", item.optString("id")),
                version = item.optString("version"),
                enabled = item.optBoolean("enabled", true),
                error = item.optString("error", ""),
                summary = mobile?.optString("summary", "") ?: "",
                metrics = metrics,
            )
        }
    }

    suspend fun themes(): List<ThemeRef> = withContext(Dispatchers.IO) {
        val json = request("/shenqing/v1/themes") ?: return@withContext emptyList()
        val array = json.optJSONArray("themes") ?: return@withContext emptyList()
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            ThemeRef(
                id = item.optString("id"),
                name = item.optString("name", item.optString("id")),
                author = item.optString("author", ""),
                version = item.optString("version", ""),
                builtIn = item.optBoolean("builtIn", false),
                source = "desktop",
            )
        }
    }

    suspend fun themePack(id: String): String? = withContext(Dispatchers.IO) {
        val encoded = java.net.URLEncoder.encode(id, "UTF-8")
        rawRequest("/shenqing/v1/themes/" + encoded)
    }

    private suspend fun request(path: String): JSONObject? = withContext(Dispatchers.IO) {
        requestAbsolute(base + path, authenticated = true)
    }

    private suspend fun rawRequest(path: String): String? = withContext(Dispatchers.IO) {
        val connection = openConnection(base + path, authenticated = true) ?: return@withContext null
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
            if (code !in 200..299) throw BridgeException("HTTP " + code)
            text
        } finally {
            connection.disconnect()
        }
    }

    private fun requestAbsolute(url: String, authenticated: Boolean): JSONObject? {
        val connection = openConnection(url, authenticated) ?: return null
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
            if (code !in 200..299) throw BridgeException("HTTP " + code)
            JSONObject(text)
        } catch (error: Exception) {
            if (error is BridgeException) throw error
            throw BridgeException(error.message ?: "连接失败")
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String, authenticated: Boolean): HttpURLConnection? {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 8000
            connection.setRequestProperty("Accept", "application/json")
            if (authenticated && authToken.isNotBlank()) {
                connection.setRequestProperty("Authorization", "Bearer " + authToken)
            }
            connection
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        fun buildBase(host: String, port: Int): String {
            var value = host.trim()
            if (value.isEmpty()) return "http://127.0.0.1:" + port
            if (value.startsWith("shenqing://pair?", ignoreCase = true)) {
                val query = value.substringAfter("?", "")
                val params = query.split("&").mapNotNull { part ->
                    val index = part.indexOf('=')
                    if (index <= 0) null else URLDecoder.decode(part.substring(0, index), "UTF-8") to
                        URLDecoder.decode(part.substring(index + 1), "UTF-8")
                }.toMap()
                val parsedHost = params["host"] ?: "127.0.0.1"
                val parsedPort = params["port"]?.toIntOrNull() ?: port
                return "http://" + parsedHost + ":" + parsedPort
            }
            value = value.removePrefix("http://").removePrefix("https://")
            value = value.substringBefore("/")
            value = value.substringBefore(":")
            if (value.isEmpty()) value = "127.0.0.1"
            return "http://" + value + ":" + port
        }

        fun pairingToken(raw: String): String {
            if (!raw.startsWith("shenqing://pair?", ignoreCase = true)) return ""
            val query = raw.substringAfter("?", "")
            return query.split("&").firstOrNull { it.startsWith("token=") }?.substringAfter("token=")?.let {
                URLDecoder.decode(it, "UTF-8")
            } ?: ""
        }
    }
}
