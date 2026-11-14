package com.shenqing.mobile.data

import android.content.Context
import android.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File

data class ThemePalette(
    val id: String,
    val name: String,
    val mode: String,
    val colors: Map<String, Int>,
    val lists: Map<String, List<Int>>,
) {
    fun color(key: String, fallback: Int = Color.WHITE): Int = colors[key] ?: fallback
    val bgTop get() = color("bgTop", 0xFF04121C.toInt())
    val bgBottom get() = color("bgBottom", 0xFF0A2A36.toInt())
    val textPrimary get() = color("textPrimary", 0xFFEFF9FF.toInt())
    val textSecondary get() = color("textSecondary", 0xFFA9C6D2.toInt())
    val textTertiary get() = color("textTertiary", 0xFF6E8C99.toInt())
    val accent get() = color("accent", 0xFF2ED3C6.toInt())
    val accentSoft get() = color("accentSoft", 0xFF2B86FF.toInt())
    val cardBg get() = color("cardBg", 0xFF0E2A38.toInt())
    val cardBgAlt get() = color("cardBgAlt", 0xFF123243.toInt())
    val cardBorder get() = color("cardBorder", 0x2BFFFFFF)
    val danger get() = color("danger", 0xFFE2555F.toInt())
    val success get() = color("success", 0xFF6FE3B0.toInt())
    val onAccent get() = color("onAccent", 0xFF04222B.toInt())
    val gradA get() = color("gradA", 0xFF7FE7DA.toInt())
    val gradB get() = color("gradB", 0xFF2ED3C6.toInt())
    val gradC get() = color("gradC", 0xFF2B86FF.toInt())
    fun list(key: String): List<Int> = lists[key] ?: emptyList()
}

data class ThemeEntry(
    val id: String,
    val name: String,
    val author: String,
    val version: String,
    val builtIn: Boolean,
    val description: String = "",
)

class ThemeRepository private constructor(private val context: Context) {
    private val repo = AppRepository.get(context)
    private val builtInJson: JSONObject by lazy {
        JSONObject(context.assets.open("theme_tokens.json").bufferedReader().use { it.readText() })
    }
    private val builtInModes: List<String> by lazy {
        val modes = builtInJson.optJSONObject("modes") ?: JSONObject()
        modes.keys().asSequence().toList()
    }

    private val _current = MutableStateFlow(loadCurrent())
    val current: StateFlow<ThemePalette> = _current

    private val _entries = MutableStateFlow(scanEntries())
    val entries: StateFlow<List<ThemeEntry>> = _entries

    init {
        // 把随包示例主题复制到 filesDir/themes，之后与用户导入的包一视同仁。
        val themeDir = File(context.filesDir, "themes").apply { mkdirs() }
        runCatching {
            context.assets.list("themes")?.forEach { name ->
                val target = File(themeDir, name)
                if (!target.exists()) {
                    context.assets.open("themes/" + name).use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }
        if (repo.themeId.value !in builtInModes && findPackFile(repo.themeId.value) == null) {
            repo.setTheme("solid")
        }
        _current.value = paletteFor(repo.themeId.value)
        _entries.value = scanEntries()
    }

    fun builtInNames(): Map<String, String> = mapOf(
        "solid" to "纯色",
        "light" to "清空间浅色",
        "glass" to "液态玻璃",
        "ocean" to "海洋",
    )

    fun refreshEntries() {
        _entries.value = scanEntries()
    }

    fun apply(id: String) {
        repo.setTheme(id)
        _current.value = paletteFor(id)
        refreshEntries()
        runCatching { com.shenqing.mobile.widget.FocusWidgetProvider.update(context) }
    }

    fun paletteFor(id: String): ThemePalette {
        if (id in builtInModes) return builtIn(id)
        val file = findPackFile(id) ?: return builtIn("solid")
        return runCatching {
            val pack = JSONObject(file.readText())
            val base = pack.optString("base", "solid").ifBlank { "solid" }
            val basePalette = builtIn(if (base in builtInModes) base else "solid")
            val modeObj = pack.optJSONObject("modes")?.optJSONObject(base)
                ?: pack.optJSONObject("modes")?.optJSONObject("solid")
            val colorsObj = modeObj?.optJSONObject("colors") ?: pack.optJSONObject("colors")
            val paletteObj = pack.optJSONObject("palette")
            val colors = basePalette.colors.toMutableMap()
            if (paletteObj != null) {
                paletteObj.keys().forEach { key ->
                    val parsed = parseQtColor(paletteObj.optString(key))
                    if (parsed != null) colors[key] = parsed
                }
            }
            if (colorsObj != null) {
                colorsObj.keys().forEach { key ->
                    val parsed = parseQtColor(colorsObj.optString(key))
                    if (parsed != null) colors[key] = parsed
                }
            }
            if (paletteObj != null) {
                // 与桌面 AppTheme 的 custom 分支一致：bgStart = bgTop，bgCenter = bgMid
                colors["bgStart"] = colors["bgTop"] ?: basePalette.bgTop
                colors["bgCenter"] = colors["bgMid"] ?: basePalette.color("bgMid", basePalette.bgTop)
            }
            val lists = basePalette.lists.toMutableMap()
            val listsObj = modeObj?.optJSONObject("lists")
            if (listsObj != null) {
                listsObj.keys().forEach { key ->
                    val arr = listsObj.optJSONArray(key) ?: return@forEach
                    lists[key] = (0 until arr.length()).mapNotNull { parseQtColor(arr.optString(it)) }
                }
            }
            ThemePalette(
                id = id,
                name = pack.optString("name", id),
                mode = base,
                colors = colors,
                lists = lists,
            )
        }.getOrElse { builtIn("solid") }
    }

    fun importPack(text: String): Result<ThemeEntry> = runCatching {
        val pack = JSONObject(text)
        val id = pack.optString("id").trim().ifBlank { throw IllegalArgumentException("主题包缺少 id") }
        if (!id.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$"))) {
            throw IllegalArgumentException("主题包 id 不合法")
        }
        val dir = File(context.filesDir, "themes").apply { mkdirs() }
        File(dir, id + ".sqtheme").writeText(text)
        val entry = ThemeEntry(
            id = id,
            name = pack.optString("name", id),
            author = pack.optString("author", ""),
            version = pack.optString("version", ""),
            builtIn = false,
            description = pack.optString("description", ""),
        )
        refreshEntries()
        entry
    }

    fun deletePack(id: String): Boolean {
        if (id in builtInModes) return false
        val file = findPackFile(id) ?: return false
        val ok = file.delete()
        if (ok) {
            if (repo.themeId.value == id) apply("solid")
            refreshEntries()
        }
        return ok
    }

    fun packJson(id: String): String? = findPackFile(id)?.takeIf { it.isFile }?.readText()

    private fun loadCurrent(): ThemePalette = paletteFor(repo.themeId.value)

    private fun builtIn(mode: String): ThemePalette {
        val modes = builtInJson.optJSONObject("modes") ?: JSONObject()
        val item = modes.optJSONObject(mode) ?: modes.optJSONObject("solid") ?: JSONObject()
        val colorsObj = item.optJSONObject("colors") ?: JSONObject()
        val colors = colorsObj.keys().asSequence().mapNotNull { key ->
            parseQtColor(colorsObj.optString(key))?.let { key to it }
        }.toMap()
        val listsObj = item.optJSONObject("lists") ?: JSONObject()
        val lists = listsObj.keys().asSequence().associateWith { key ->
            val arr = listsObj.optJSONArray(key)
            if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { parseQtColor(arr.optString(it)) }
        }
        return ThemePalette(
            id = mode,
            name = builtInNames()[mode] ?: mode,
            mode = mode,
            colors = colors,
            lists = lists,
        )
    }

    private fun scanEntries(): List<ThemeEntry> {
        val result = builtInModes.map { mode ->
            ThemeEntry(mode, builtInNames()[mode] ?: mode, "深清", "内置", true)
        }.toMutableList()
        val dir = File(context.filesDir, "themes")
        val files = dir.listFiles { file -> file.isFile && file.name.endsWith(".sqtheme") } ?: emptyArray()
        files.sortedBy { it.name }.forEach { file ->
            runCatching {
                val pack = JSONObject(file.readText())
                val id = pack.optString("id", file.nameWithoutExtension)
                if (id in builtInModes) return@runCatching
                result += ThemeEntry(
                    id = id,
                    name = pack.optString("name", id),
                    author = pack.optString("author", ""),
                    version = pack.optString("version", ""),
                    builtIn = false,
                    description = pack.optString("description", ""),
                )
            }
        }
        // 去重，pack 优先
        return result.distinctBy { it.id }
    }

    private fun findPackFile(id: String): File? {
        if (id.isBlank() || id.contains("/") || id.contains("..")) return null
        val file = File(File(context.filesDir, "themes"), id + ".sqtheme")
        return if (file.isFile) file else null
    }

    companion object {
        @Volatile private var instance: ThemeRepository? = null
        fun get(context: Context): ThemeRepository = instance ?: synchronized(this) {
            instance ?: ThemeRepository(context.applicationContext).also { instance = it }
        }

        /** Qt 的 #AARRGGBB / #RRGGBB → Android ARGB int。 */
        fun parseQtColor(value: String?): Int? {
            val text = value?.trim()?.removePrefix("#") ?: return null
            return try {
                when (text.length) {
                    6 -> (0xFF000000.toInt()) or text.toLong(16).toInt()
                    8 -> {
                        val long = text.toLong(16)
                        // Qt: AARRGGBB，Android 内部也是 ARGB，直接转 Int 即可
                        long.toInt()
                    }
                    else -> null
                }
            } catch (_: NumberFormatException) {
                null
            }
        }

        fun toCssArgb(color: Int): String = String.format("#%08X", color)
    }
}
