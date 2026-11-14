package com.shenqing.mobile.data

import java.util.Calendar

enum class Tier(
    val key: String,
    val label: String,
    val english: String,
    val tagline: String,
    val detail: String,
    val defaultMinutes: Int,
    val minMinutes: Int,
    val maxMinutes: Int,
    val step: Int,
    val locked: Boolean,
    val canPause: Boolean,
) {
    LIGHT("light", "浅清", "LIGHT", "只挡黑名单，留一扇窗",
        "挡掉最容易分心的应用与声音，其余照常。可暂停，也可提前结束。", 25, 15, 240, 5, false, true),
    COVER("cover", "覆清", "COVER", "白名单之外，暂时安静",
        "只留白名单里的应用，遮罩覆盖整个屏幕。开始后不能暂停或提前结束。", 45, 15, 240, 5, true, false),
    DEEP("deep", "深清", "DEEP", "时间没到，门不开",
        "白名单外全部挡住，只剩倒计时。开始后不可暂停，也不可提前结束。", 90, 15, 240, 5, true, false),
    SLEEP("sleep", "还清", "SLEEP", "睡前，把今天还给自己",
        "睡前锁定与提醒。到点前只留一个安静的倒计时，可暂停。", 480, 60, 600, 30, false, true);

    companion object {
        fun of(value: String?): Tier = entries.firstOrNull { it.key == value } ?: LIGHT
    }
}

data class Space(
    val id: String,
    val name: String,
    val minutes: Int,
    val tier: Tier,
    val allowedPackages: Set<String> = emptySet(),
    val superSpace: Boolean = false,
)

data class ActiveSession(
    val spaceId: String,
    val name: String,
    val tier: Tier,
    val totalSeconds: Long,
    val endsAt: Long,
    val paused: Boolean = false,
    val pausedRemaining: Long = 0L,
    val startedAt: Long = System.currentTimeMillis(),
) {
    fun remainingSeconds(now: Long = System.currentTimeMillis()): Long {
        if (paused) return pausedRemaining.coerceAtLeast(0)
        return ((endsAt - now) / 1000L).coerceAtLeast(0)
    }

    fun elapsedMinutes(now: Long = System.currentTimeMillis()): Int =
        (((totalSeconds - remainingSeconds(now)) / 60L).toInt()).coerceAtLeast(0)
}

data class FocusSession(
    val id: String,
    val spaceId: String,
    val name: String,
    val tier: Tier,
    val plannedMinutes: Int,
    val minutes: Int,
    val completed: Boolean,
    val startedAt: Long,
    val endedAt: Long,
)

data class DayStat(val date: String, val minutes: Int)

data class Stats(
    val todayMinutes: Int,
    val weekMinutes: Int,
    val streakDays: Int,
    val sessionCount: Int,
    val todayCount: Int,
    val recentCount: Int,
    val last7Days: List<DayStat>,
    val categories: List<Pair<String, Int>>,
)

data class CloudAccount(
    val url: String = "",
    val email: String = "",
    val token: String = "",
    val revision: Int = 0,
    val lastAt: String = "",
    val device: String = "",
    val busy: Boolean = false,
    val status: String = "",
    val error: String = "",
) {
    val loggedIn: Boolean get() = token.isNotBlank()
}

data class BridgeSettings(
    val host: String = "",
    val port: Int = 17777,
    val token: String = "",
    val connected: Boolean = false,
    val lastError: String = "",
    val pluginName: String = "",
    val pluginVersion: String = "",
)

data class Metric(val key: String, val label: String, val value: String, val unit: String = "")

data class RemotePlugin(
    val id: String,
    val name: String,
    val version: String,
    val enabled: Boolean,
    val error: String = "",
    val summary: String = "",
    val metrics: List<Metric> = emptyList(),
)

data class ThemeRef(
    val id: String,
    val name: String,
    val author: String = "",
    val version: String = "",
    val builtIn: Boolean = false,
    val source: String = "local",
)

fun dateKey(time: Long = System.currentTimeMillis()): String {
    val c = Calendar.getInstance().apply { timeInMillis = time }
    return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
}

fun startOfDay(time: Long = System.currentTimeMillis()): Long {
    val c = Calendar.getInstance().apply {
        timeInMillis = time
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    return c.timeInMillis
}

fun startOfWeek(time: Long = System.currentTimeMillis()): Long {
    val c = Calendar.getInstance().apply {
        timeInMillis = startOfDay(time)
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    }
    return c.timeInMillis
}
