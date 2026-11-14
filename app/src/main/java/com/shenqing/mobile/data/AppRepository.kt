package com.shenqing.mobile.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class AppRepository private constructor(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("shenqing_mobile", Context.MODE_PRIVATE)

    private val _spaces = MutableStateFlow(loadSpaces())
    val spaces: StateFlow<List<Space>> = _spaces

    private val _sessions = MutableStateFlow(loadSessions())
    val sessions: StateFlow<List<FocusSession>> = _sessions

    private val _active = MutableStateFlow(loadActive())
    val active: StateFlow<ActiveSession?> = _active

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<Settings> = _settings

    private val _themeId = MutableStateFlow(prefs.getString(KEY_THEME, "solid") ?: "solid")
    val themeId: StateFlow<String> = _themeId

    private val _fontScale = MutableStateFlow(prefs.getFloat(KEY_FONT_SCALE, 1f))
    val fontScale: StateFlow<Float> = _fontScale

    private val _bridge = MutableStateFlow(loadBridge())
    val bridge: StateFlow<BridgeSettings> = _bridge

    private val _cloud = MutableStateFlow(loadCloud())
    val cloud: StateFlow<CloudAccount> = _cloud

    private val _stats = MutableStateFlow(computeStats(_sessions.value))
    val stats: StateFlow<Stats> = _stats

    private val _pomodoro = MutableStateFlow(loadPomodoro())
    val pomodoro: StateFlow<PomodoroState> = _pomodoro

    data class Settings(
        val quickFocusMinutes: Int = 25,
        val quickFocusSpaceId: String = "sp_light",
        val lastWidgetUpdate: Long = 0L,
        val oppoZenEnabled: Boolean = true,
        val profileName: String = "",
        val followSystem: Boolean = false,
        val updateChannel: String = "preview",
        val pomodoroWorkMinutes: Int = 25,
        val pomodoroRounds: Int = 4,
    )

    init {
        val current = _active.value
        if (current != null && !current.paused && current.endsAt <= System.currentTimeMillis()) {
            finishActive(completed = true, now = current.endsAt)
        }
        if (_pomodoro.value.phase != PomodoroPhase.IDLE && _pomodoro.value.running) {
            runCatching { com.shenqing.mobile.service.PomodoroService.start(context) }
        }
    }

    fun spaceById(id: String?): Space = _spaces.value.firstOrNull { it.id == id } ?: _spaces.value.first()

    fun startFocus(space: Space, minutes: Int, startService: Boolean = true) {
        val total = minutes.coerceAtLeast(1) * 60L
        val active = ActiveSession(
            spaceId = space.id,
            name = space.name,
            tier = space.tier,
            totalSeconds = total,
            endsAt = System.currentTimeMillis() + total * 1000L,
        )
        _active.value = active
        persistActive(active)
        prefs.edit().putString(KEY_QUICK_SPACE, space.id).putInt(KEY_QUICK_MINUTES, minutes).apply()
        _settings.value = _settings.value.copy(quickFocusSpaceId = space.id, quickFocusMinutes = minutes)
        if (startService) runCatching { com.shenqing.mobile.service.FocusService.start(context) }
        updateStats()
        notifyWidget()
    }

    fun pause() {
        val active = _active.value ?: return
        if (active.paused || !active.tier.canPause) return
        val remaining = active.remainingSeconds()
        val paused = active.copy(paused = true, pausedRemaining = remaining)
        _active.value = paused
        persistActive(paused)
        runCatching { com.shenqing.mobile.service.FocusService.pause(context) }
        notifyWidget()
    }

    fun resume() {
        val active = _active.value ?: return
        if (!active.paused) return
        val resumed = active.copy(
            paused = false,
            endsAt = System.currentTimeMillis() + active.pausedRemaining * 1000L,
            pausedRemaining = 0L,
        )
        _active.value = resumed
        persistActive(resumed)
        runCatching { com.shenqing.mobile.service.FocusService.start(context) }
        notifyWidget()
    }

    fun finishActive(completed: Boolean, forced: Boolean = false, now: Long = System.currentTimeMillis()) {
        val active = _active.value ?: return
        val minutes = active.elapsedMinutes(now)
        if (minutes > 0) {
            val session = FocusSession(
                id = "s_" + UUID.randomUUID().toString().take(8),
                spaceId = active.spaceId,
                name = active.name,
                tier = active.tier,
                plannedMinutes = (active.totalSeconds / 60).toInt(),
                minutes = minutes,
                completed = completed && !forced,
                startedAt = active.startedAt,
                endedAt = now,
            )
            val updated = (listOf(session) + _sessions.value).take(300)
            _sessions.value = updated
            persistSessions(updated)
        }
        _active.value = null
        persistActive(null)
        runCatching { com.shenqing.mobile.service.OppoZen.clearActive() }
        updateStats()
        notifyWidget()
    }

    fun stopFocus(forced: Boolean = false) {
        finishActive(completed = false, forced = forced)
        runCatching { com.shenqing.mobile.service.FocusService.stop(context) }
    }

    fun clearAllData() {
        prefs.edit().clear().apply()
        _spaces.value = defaultSpaces()
        _sessions.value = emptyList()
        _active.value = null
        _themeId.value = "solid"
        _fontScale.value = 1f
        _bridge.value = BridgeSettings()
        _cloud.value = CloudAccount()
        _settings.value = Settings()
        updateStats()
        notifyWidget()
    }

    fun setUpdateChannel(channel: String) {
        val value = if (channel == "stable") "stable" else "preview"
        _settings.value = _settings.value.copy(updateChannel = value)
        prefs.edit().putString(KEY_UPDATE_CHANNEL, value).apply()
    }

    fun setFollowSystem(enabled: Boolean) {
        _settings.value = _settings.value.copy(followSystem = enabled)
        prefs.edit().putBoolean(KEY_FOLLOW_SYSTEM, enabled).apply()
        runCatching { com.shenqing.mobile.data.ThemeRepository.get(context).refreshSystem() }
        notifyWidget()
    }

    fun setProfileName(name: String) {
        val text = name.trim().take(40)
        _settings.value = _settings.value.copy(profileName = text)
        prefs.edit().putString(KEY_PROFILE_NAME, text).apply()
    }

    fun setOppoZen(enabled: Boolean) {
        _settings.value = _settings.value.copy(oppoZenEnabled = enabled)
        prefs.edit().putBoolean(KEY_OPPO_ZEN, enabled).apply()
    }

    fun startPomodoro(
        workMinutes: Int = 25,
        shortBreakMinutes: Int = 5,
        longBreakMinutes: Int = 15,
        roundsBeforeLong: Int = 4,
        totalRounds: Int = 4,
    ) {
        val work = workMinutes.coerceIn(1, 120)
        val total = work * 60
        val state = PomodoroState(
            phase = PomodoroPhase.FOCUS,
            running = true,
            round = 1,
            remainingSeconds = total,
            totalSeconds = total,
            endsAt = System.currentTimeMillis() + total * 1000L,
            workMinutes = work,
            shortBreakMinutes = shortBreakMinutes.coerceIn(1, 60),
            longBreakMinutes = longBreakMinutes.coerceIn(1, 120),
            roundsBeforeLong = roundsBeforeLong.coerceIn(1, 12),
            totalRounds = totalRounds.coerceIn(1, 12),
        )
        _pomodoro.value = state
        _settings.value = _settings.value.copy(pomodoroWorkMinutes = work, pomodoroRounds = state.totalRounds)
        prefs.edit().putInt(KEY_POMODORO_WORK, work).putInt(KEY_POMODORO_ROUNDS, state.totalRounds).apply()
        persistPomodoro(state)
        runCatching { com.shenqing.mobile.service.PomodoroService.start(context) }
    }

    fun pausePomodoro(notifyService: Boolean = true) {
        val state = _pomodoro.value
        if (state.phase == PomodoroPhase.IDLE || !state.running) return
        val paused = state.copy(running = false, remainingSeconds = state.remaining(), endsAt = 0L)
        _pomodoro.value = paused
        persistPomodoro(paused)
        if (notifyService) runCatching { com.shenqing.mobile.service.PomodoroService.pause(context) }
    }

    fun resumePomodoro(notifyService: Boolean = true) {
        val state = _pomodoro.value
        if (state.phase == PomodoroPhase.IDLE || state.running) return
        val remaining = state.remainingSeconds.coerceAtLeast(1)
        val resumed = state.copy(
            running = true,
            remainingSeconds = remaining,
            endsAt = System.currentTimeMillis() + remaining * 1000L,
        )
        _pomodoro.value = resumed
        persistPomodoro(resumed)
        if (notifyService) runCatching { com.shenqing.mobile.service.PomodoroService.start(context) }
    }

    fun tickPomodoro() {
        var state = _pomodoro.value
        if (state.phase == PomodoroPhase.IDLE || !state.running) return
        val remaining = state.remaining()
        if (remaining > 0) {
            if (state.remainingSeconds != remaining) {
                state = state.copy(remainingSeconds = remaining)
                _pomodoro.value = state
                persistPomodoro(state)
            }
            return
        }
        if (state.phase == PomodoroPhase.FOCUS && state.round >= state.totalRounds) {
            recordPomodoroSession(state.workMinutes)
            _pomodoro.value = PomodoroState()
            persistPomodoro(null)
            notifyWidget()
            return
        }
        state = when (state.phase) {
            PomodoroPhase.FOCUS -> {
                recordPomodoroSession(state.workMinutes)
                val long = state.round % state.roundsBeforeLong == 0
                val minutes = if (long) state.longBreakMinutes else state.shortBreakMinutes
                state.copy(
                    phase = if (long) PomodoroPhase.LONG_BREAK else PomodoroPhase.SHORT_BREAK,
                    running = true,
                    remainingSeconds = minutes * 60,
                    totalSeconds = minutes * 60,
                    endsAt = System.currentTimeMillis() + minutes * 60_000L,
                )
            }
            PomodoroPhase.SHORT_BREAK, PomodoroPhase.LONG_BREAK -> {
                val minutes = state.workMinutes
                state.copy(
                    phase = PomodoroPhase.FOCUS,
                    running = true,
                    round = state.round + 1,
                    remainingSeconds = minutes * 60,
                    totalSeconds = minutes * 60,
                    endsAt = System.currentTimeMillis() + minutes * 60_000L,
                )
            }
            PomodoroPhase.IDLE -> state
        }
        _pomodoro.value = state
        persistPomodoro(state)
        notifyWidget()
    }

    fun skipPomodoro(notifyService: Boolean = true) {
        var state = _pomodoro.value
        if (state.phase == PomodoroPhase.IDLE) return
        if (state.phase == PomodoroPhase.FOCUS && state.round >= state.totalRounds) {
            clearPomodoro()
            if (notifyService) runCatching { com.shenqing.mobile.service.PomodoroService.stop(context) }
            return
        }
        if (state.phase == PomodoroPhase.FOCUS) {
            val long = state.round % state.roundsBeforeLong == 0
            val minutes = if (long) state.longBreakMinutes else state.shortBreakMinutes
            state = state.copy(
                phase = if (long) PomodoroPhase.LONG_BREAK else PomodoroPhase.SHORT_BREAK,
                running = true,
                remainingSeconds = minutes * 60,
                totalSeconds = minutes * 60,
                endsAt = System.currentTimeMillis() + minutes * 60_000L,
            )
        } else {
            state = state.copy(
                phase = PomodoroPhase.FOCUS,
                running = true,
                round = state.round + 1,
                remainingSeconds = state.workMinutes * 60,
                totalSeconds = state.workMinutes * 60,
                endsAt = System.currentTimeMillis() + state.workMinutes * 60_000L,
            )
        }
        _pomodoro.value = state
        persistPomodoro(state)
        if (notifyService) runCatching { com.shenqing.mobile.service.PomodoroService.start(context) }
    }

    fun clearPomodoro() {
        _pomodoro.value = PomodoroState()
        persistPomodoro(null)
    }

    fun stopPomodoro(notifyService: Boolean = true) {
        clearPomodoro()
        if (notifyService) runCatching { com.shenqing.mobile.service.PomodoroService.stop(context) }
    }

    private fun recordPomodoroSession(minutes: Int) {
        if (minutes <= 0) return
        val now = System.currentTimeMillis()
        val session = FocusSession(
            id = "s_" + UUID.randomUUID().toString().take(8),
            spaceId = "pomodoro",
            name = "清番",
            tier = Tier.LIGHT,
            plannedMinutes = minutes,
            minutes = minutes,
            completed = true,
            startedAt = now - minutes * 60_000L,
            endedAt = now,
        )
        val updated = (listOf(session) + _sessions.value).take(300)
        _sessions.value = updated
        persistSessions(updated)
        updateStats()
    }

    fun setTheme(id: String) {
        _themeId.value = id
        prefs.edit().putString(KEY_THEME, id).apply()
        notifyWidget()
    }

    fun setFontScale(value: Float) {
        val clamped = value.coerceIn(0.8f, 1.4f)
        _fontScale.value = clamped
        prefs.edit().putFloat(KEY_FONT_SCALE, clamped).apply()
    }

    fun setBridge(settings: BridgeSettings) {
        _bridge.value = settings
        prefs.edit()
            .putString(KEY_BRIDGE_HOST, settings.host)
            .putInt(KEY_BRIDGE_PORT, settings.port)
            .putString(KEY_BRIDGE_TOKEN, settings.token)
            .apply()
    }

    fun updateCloud(account: CloudAccount) {
        _cloud.value = account
        prefs.edit()
            .putString(KEY_CLOUD_URL, account.url)
            .putString(KEY_CLOUD_EMAIL, account.email)
            .putString(KEY_CLOUD_TOKEN, account.token)
            .putInt(KEY_CLOUD_REVISION, account.revision)
            .putString(KEY_CLOUD_LAST_AT, account.lastAt)
            .putString(KEY_CLOUD_DEVICE, account.device)
            .apply()
    }

    fun cloudBusy(busy: Boolean, status: String = "", error: String = "") {
        _cloud.value = _cloud.value.copy(busy = busy, status = status, error = error)
    }

    fun cloudLoggedOut() {
        _cloud.value = _cloud.value.copy(token = "", revision = 0, busy = false, status = "", error = "")
        prefs.edit().putString(KEY_CLOUD_TOKEN, "").putInt(KEY_CLOUD_REVISION, 0).apply()
    }

    fun setBridgeConnected(connected: Boolean, error: String = "", name: String = "", version: String = "") {
        val next = _bridge.value.copy(
            connected = connected,
            lastError = error,
            pluginName = name,
            pluginVersion = version,
        )
        _bridge.value = next
        setBridge(next)
    }

    fun blockedPackagesFor(space: Space): Set<String> = when (space.tier) {
        Tier.DEEP -> emptySet()
        Tier.COVER -> space.allowedPackages
        Tier.LIGHT -> DEFAULT_BLOCKED
        Tier.SLEEP -> emptySet()
    }

    fun isAllowed(space: Space, packageName: String): Boolean = when (space.tier) {
        Tier.DEEP -> false
        Tier.COVER -> packageName in space.allowedPackages
        Tier.LIGHT -> packageName !in DEFAULT_BLOCKED
        Tier.SLEEP -> true
    }

    fun updateStats() {
        _stats.value = computeStats(_sessions.value)
    }

    private fun computeStats(sessions: List<FocusSession>): Stats {
        val now = System.currentTimeMillis()
        val today = dateKey(now)
        val weekStart = startOfWeek(now)
        val todaySessions = sessions.filter { dateKey(it.endedAt) == today }
        val weekMinutes = sessions.filter { it.endedAt >= weekStart }.sumOf { it.minutes }
        val minutesByDay = sessions.groupBy { dateKey(it.endedAt) }.mapValues { entry -> entry.value.sumOf { it.minutes } }
        val last7 = (6 downTo 0).map { offset ->
            val time = startOfDay(now) - offset * 86_400_000L
            val key = dateKey(time)
            DayStat(key, minutesByDay[key] ?: 0)
        }
        val activeDays = minutesByDay.filterValues { it >= 1 }.keys
        var cursor = startOfDay(now)
        if (!activeDays.contains(dateKey(cursor))) cursor -= 86_400_000L
        var streak = 0
        while (activeDays.contains(dateKey(cursor))) {
            streak += 1
            cursor -= 86_400_000L
        }
        val recentCount = sessions.count { it.endedAt >= startOfDay(now) - 6 * 86_400_000L }
        val categories = sessions.groupBy { it.name }.mapValues { entry -> entry.value.sumOf { it.minutes } }
            .entries.sortedByDescending { it.value }.take(6).map { it.key to it.value }
        return Stats(
            todayMinutes = todaySessions.sumOf { it.minutes },
            weekMinutes = weekMinutes,
            streakDays = streak,
            sessionCount = sessions.size,
            todayCount = todaySessions.size,
            recentCount = recentCount,
            last7Days = last7,
            categories = categories,
        )
    }

    // ---------------- persistence ----------------

    private fun loadSpaces(): List<Space> {
        val text = prefs.getString(KEY_SPACES, null) ?: return defaultSpaces()
        return runCatching {
            val array = JSONArray(text)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                Space(
                    id = obj.optString("id"),
                    name = obj.optString("name"),
                    minutes = obj.optInt("minutes", 45),
                    tier = Tier.of(obj.optString("tier")),
                    allowedPackages = obj.optJSONArray("allowed")?.let { apps ->
                        (0 until apps.length()).map { apps.optString(it) }.filter { it.isNotBlank() }.toSet()
                    } ?: emptySet(),
                    superSpace = obj.optBoolean("super", false),
                )
            }.ifEmpty { defaultSpaces() }
        }.getOrElse { defaultSpaces() }
    }

    private fun defaultSpaces(): List<Space> = listOf(
        Space("sp_light", "工作学习", 45, Tier.LIGHT, setOf("com.tencent.mm")),
        Space("sp_cover", "写点什么", 60, Tier.COVER, setOf("com.tencent.mm")),
        Space("sp_deep", "深度工作", 90, Tier.DEEP, emptySet(), true),
        Space("sp_sleep", "睡前收心", 480, Tier.SLEEP),
    )

    private fun loadSessions(): List<FocusSession> {
        val text = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(text)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                FocusSession(
                    id = obj.optString("id"),
                    spaceId = obj.optString("spaceId"),
                    name = obj.optString("name"),
                    tier = Tier.of(obj.optString("tier")),
                    plannedMinutes = obj.optInt("planned"),
                    minutes = obj.optInt("minutes"),
                    completed = obj.optBoolean("completed"),
                    startedAt = obj.optLong("startedAt"),
                    endedAt = obj.optLong("endedAt"),
                )
            }
        }.getOrElse { emptyList() }
    }

    private fun persistSessions(items: List<FocusSession>) {
        val array = JSONArray()
        items.forEach { s ->
            array.put(JSONObject().apply {
                put("id", s.id); put("spaceId", s.spaceId); put("name", s.name)
                put("tier", s.tier.key); put("planned", s.plannedMinutes); put("minutes", s.minutes)
                put("completed", s.completed); put("startedAt", s.startedAt); put("endedAt", s.endedAt)
            })
        }
        prefs.edit().putString(KEY_SESSIONS, array.toString()).apply()
    }

    private fun loadActive(): ActiveSession? {
        val text = prefs.getString(KEY_ACTIVE, null) ?: return null
        return runCatching {
            val obj = JSONObject(text)
            ActiveSession(
                spaceId = obj.optString("spaceId"),
                name = obj.optString("name"),
                tier = Tier.of(obj.optString("tier")),
                totalSeconds = obj.optLong("totalSeconds"),
                endsAt = obj.optLong("endsAt"),
                paused = obj.optBoolean("paused"),
                pausedRemaining = obj.optLong("pausedRemaining"),
                startedAt = obj.optLong("startedAt"),
            )
        }.getOrNull()
    }

    private fun persistActive(active: ActiveSession?) {
        if (active == null) {
            prefs.edit().remove(KEY_ACTIVE).apply()
            return
        }
        val obj = JSONObject().apply {
            put("spaceId", active.spaceId); put("name", active.name); put("tier", active.tier.key)
            put("totalSeconds", active.totalSeconds); put("endsAt", active.endsAt)
            put("paused", active.paused); put("pausedRemaining", active.pausedRemaining)
            put("startedAt", active.startedAt)
        }
        prefs.edit().putString(KEY_ACTIVE, obj.toString()).apply()
    }

    private fun loadSettings(): Settings = Settings(
        quickFocusMinutes = prefs.getInt(KEY_QUICK_MINUTES, 25),
        quickFocusSpaceId = prefs.getString(KEY_QUICK_SPACE, "sp_light") ?: "sp_light",
        lastWidgetUpdate = prefs.getLong(KEY_WIDGET_UPDATE, 0),
        oppoZenEnabled = prefs.getBoolean(KEY_OPPO_ZEN, true),
        profileName = prefs.getString(KEY_PROFILE_NAME, "") ?: "",
        followSystem = prefs.getBoolean(KEY_FOLLOW_SYSTEM, false),
        updateChannel = prefs.getString(KEY_UPDATE_CHANNEL, "preview") ?: "preview",
        pomodoroWorkMinutes = prefs.getInt(KEY_POMODORO_WORK, 25),
        pomodoroRounds = prefs.getInt(KEY_POMODORO_ROUNDS, 4),
    )

    private fun loadPomodoro(): PomodoroState {
        val raw = prefs.getString(KEY_POMODORO, "") ?: return PomodoroState()
        return runCatching {
            val json = JSONObject(raw)
            PomodoroState(
                phase = runCatching { PomodoroPhase.valueOf(json.optString("phase", "IDLE")) }.getOrDefault(PomodoroPhase.IDLE),
                running = json.optBoolean("running", false),
                round = json.optInt("round", 1),
                remainingSeconds = json.optInt("remaining", 0),
                totalSeconds = json.optInt("total", 0),
                endsAt = json.optLong("endsAt", 0),
                workMinutes = json.optInt("work", 25),
                shortBreakMinutes = json.optInt("short", 5),
                longBreakMinutes = json.optInt("long", 15),
                roundsBeforeLong = json.optInt("rounds", 4),
                totalRounds = json.optInt("totalRounds", 4),
            )
        }.getOrDefault(PomodoroState())
    }

    private fun persistPomodoro(state: PomodoroState?) {
        if (state == null || state.phase == PomodoroPhase.IDLE) {
            prefs.edit().remove(KEY_POMODORO).apply()
            return
        }
        val json = JSONObject().apply {
            put("phase", state.phase.name); put("running", state.running); put("round", state.round)
            put("remaining", state.remainingSeconds); put("total", state.totalSeconds)
            put("endsAt", state.endsAt); put("work", state.workMinutes)
            put("short", state.shortBreakMinutes); put("long", state.longBreakMinutes)
            put("rounds", state.roundsBeforeLong); put("totalRounds", state.totalRounds)
        }
        prefs.edit().putString(KEY_POMODORO, json.toString()).apply()
    }

    private fun loadCloud(): CloudAccount = CloudAccount(
        url = prefs.getString(KEY_CLOUD_URL, "") ?: "",
        email = prefs.getString(KEY_CLOUD_EMAIL, "") ?: "",
        token = prefs.getString(KEY_CLOUD_TOKEN, "") ?: "",
        revision = prefs.getInt(KEY_CLOUD_REVISION, 0),
        lastAt = prefs.getString(KEY_CLOUD_LAST_AT, "") ?: "",
        device = prefs.getString(KEY_CLOUD_DEVICE, "") ?: "",
    )

    private fun loadBridge(): BridgeSettings = BridgeSettings(
        host = prefs.getString(KEY_BRIDGE_HOST, "") ?: "",
        port = prefs.getInt(KEY_BRIDGE_PORT, 17777),
        token = prefs.getString(KEY_BRIDGE_TOKEN, "") ?: "",
    )

    private fun notifyWidget() {
        runCatching { com.shenqing.mobile.widget.FocusWidgetProvider.update(context) }
    }

    companion object {
        private const val KEY_SPACES = "spaces"
        private const val KEY_SESSIONS = "sessions"
        private const val KEY_ACTIVE = "active"
        private const val KEY_THEME = "theme"
        private const val KEY_FONT_SCALE = "font_scale"
        private const val KEY_BRIDGE_HOST = "bridge_host"
        private const val KEY_BRIDGE_PORT = "bridge_port"
        private const val KEY_BRIDGE_TOKEN = "bridge_token"
        private const val KEY_QUICK_SPACE = "quick_space"
        private const val KEY_QUICK_MINUTES = "quick_minutes"
        private const val KEY_WIDGET_UPDATE = "widget_update"
        private const val KEY_CLOUD_URL = "cloud_url"
        private const val KEY_CLOUD_EMAIL = "cloud_email"
        private const val KEY_CLOUD_TOKEN = "cloud_token"
        private const val KEY_CLOUD_REVISION = "cloud_revision"
        private const val KEY_CLOUD_LAST_AT = "cloud_last_at"
        private const val KEY_CLOUD_DEVICE = "cloud_device"
        private const val KEY_OPPO_ZEN = "oppo_zen"
        private const val KEY_PROFILE_NAME = "profile_name"
        private const val KEY_FOLLOW_SYSTEM = "follow_system"
        private const val KEY_UPDATE_CHANNEL = "update_channel"
        private const val KEY_POMODORO_WORK = "pomodoro_work"
        private const val KEY_POMODORO_ROUNDS = "pomodoro_rounds"
        private const val KEY_POMODORO = "pomodoro"

        val DEFAULT_BLOCKED = setOf(
            "com.tencent.mm", "com.ss.android.ugc.aweme", "com.xingin.xhs",
            "com.taobao.taobao", "com.jingdong.app.mall", "tv.danmaku.bili",
            "com.zhihu.android", "com.sina.weibo", "com.tencent.qqlive",
        )

        @Volatile private var instance: AppRepository? = null

        fun get(context: Context): AppRepository = instance ?: synchronized(this) {
            instance ?: AppRepository(context.applicationContext).also { instance = it }
        }
    }
}
