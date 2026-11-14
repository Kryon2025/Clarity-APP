package com.shenqing.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

data class CloudSnapshot(
    val revision: Int,
    val payload: JSONObject,
    val updatedAt: Long,
    val device: String,
)

data class CloudPushResult(val ok: Boolean, val revision: Int)

class CloudSyncException(message: String) : Exception(message)

/**
 * 与桌面 v0.20 相同的商店服务端账户同步协议：
 *   POST /api/auth/login {email,password} -> {token}
 *   GET  /api/sync -> {revision, updated_at, payload, device_name}
 *   PUT  /api/sync {payload, revision, device} -> {revision} / 409 {revision}
 */
class CloudSyncClient(
    baseUrl: String,
    private val token: String = "",
) {
    private val base: String = normalize(baseUrl)

    suspend fun login(email: String, password: String): String = withContext(Dispatchers.IO) {
        val data = call("POST", "/api/auth/login", JSONObject().apply {
            put("email", email.trim())
            put("password", password)
        }, authenticated = false)
        data.optString("token").takeIf { it.isNotBlank() }
            ?: throw CloudSyncException("登录响应没有 token")
    }

    suspend fun pull(): CloudSnapshot = withContext(Dispatchers.IO) {
        val data = call("GET", "/api/sync", null, authenticated = true)
        CloudSnapshot(
            revision = data.optInt("revision", 0),
            payload = data.optJSONObject("payload") ?: JSONObject(),
            updatedAt = data.optLong("updated_at", 0L),
            device = data.optString("device_name", ""),
        )
    }

    suspend fun push(payload: JSONObject, revision: Int, device: String): CloudPushResult = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("payload", payload)
            put("revision", revision.coerceAtLeast(0))
            put("device", device.take(64))
        }
        val connection = open("/api/sync", "PUT", authenticated = true)
        try {
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            val data = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            when {
                code in 200..299 -> CloudPushResult(true, data.optInt("revision", revision + 1))
                code == 409 -> CloudPushResult(false, data.optInt("revision", revision))
                code == 401 || code == 403 -> throw CloudSyncException("登录已失效，请重新登录")
                else -> throw CloudSyncException(detail(data, "上传失败（HTTP " + code + "）"))
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun call(method: String, path: String, body: JSONObject?, authenticated: Boolean): JSONObject {
        if (base.isBlank()) throw CloudSyncException("请先填写商店服务器地址")
        val connection = open(path, method, authenticated)
        return try {
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            val data = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            when {
                code in 200..299 -> data
                code == 401 || code == 403 -> throw CloudSyncException(
                    if (path.contains("login")) "邮箱或密码不正确" else "登录已失效，请重新登录"
                )
                code == 404 -> throw CloudSyncException("服务器没有账户同步接口，请升级商店服务端")
                code == 429 -> throw CloudSyncException("尝试太频繁，请稍后再试")
                else -> throw CloudSyncException(detail(data, "请求失败（HTTP " + code + "）"))
            }
        } catch (error: CloudSyncException) {
            throw error
        } catch (error: Exception) {
            throw CloudSyncException(error.message ?: "网络请求失败")
        } finally {
            connection.disconnect()
        }
    }

    private fun open(path: String, method: String, authenticated: Boolean): HttpURLConnection {
        val url = base + path
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = 8000
            connection.readTimeout = 15000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("User-Agent", "ShenQing-Android/1.0")
            if (authenticated && token.isNotBlank()) {
                connection.setRequestProperty("Authorization", "Bearer " + token)
            }
            connection
        } catch (error: Exception) {
            throw CloudSyncException("服务器地址不正确：" + (error.message ?: ""))
        }
    }

    private fun detail(data: JSONObject, fallback: String): String {
        val value = data.opt("detail") ?: data.opt("message") ?: data.opt("error")
        return value?.toString()?.take(200)?.takeIf { it.isNotBlank() } ?: fallback
    }

    companion object {
        fun normalize(url: String): String {
            var text = url.trim()
            if (text.isBlank()) return ""
            if (!text.contains("://")) text = "https://" + text
            return text.trimEnd('/')
        }
    }
}
