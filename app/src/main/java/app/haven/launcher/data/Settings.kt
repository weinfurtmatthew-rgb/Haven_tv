package app.haven.launcher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek

enum class ArtSource(val label: String) {
    Paintings("Paintings"),
    Photos("Your photos"),
    Mixed("Paintings and photos"),
    Generated("Haven originals"),
}

enum class AlarmSound(val label: String) {
    Chime("Chime"),
    Bells("Bells"),
    Marimba("Marimba"),
    Digital("Digital beeps"),
    Rise("Gentle rise"),
    Silent("Silent, screen and lights only"),
}

enum class StopMethod(val label: String) {
    Press("Press Stop"),
    Hold("Hold OK for 3 seconds"),
    Sum("Solve a sum"),
}

enum class AfterAlarm(val label: String) {
    Briefing("Show the morning briefing"),
    Home("Go to the home screen"),
    LiveTv("Open live TV"),
    Close("Go back to what was on"),
}

enum class IfIgnored(val label: String) {
    Snooze("Snooze"),
    Stop("Stop"),
}

enum class AlarmBackground(val label: String) {
    Sunrise("Sunrise glow"),
    Theme("The theme's colour"),
    Background("Your home screen background"),
    Art("A painting"),
    Black("Black"),
}

data class AlarmConfig(
    val enabled: Boolean = false,
    val hour: Int = 6,
    val minute: Int = 30,
    val days: Set<DayOfWeek> = WEEKDAYS,
    /** Minutes of light fade-in before the alarm; 0 turns sunrise lights off. */
    val sunriseMin: Int = 10,
    val snoozeMin: Int = 9,
    val sound: AlarmSound = AlarmSound.Chime,
    /** Loudest the alarm gets, 10..100 percent of the TV's media volume. */
    val volumePct: Int = 100,
    /** Seconds to rise from quiet to [volumePct]; 0 starts at full volume. */
    val rampSec: Int = 40,
    /** How many snoozes in a row; -1 = no limit, 0 = no snooze button. */
    val maxSnoozes: Int = -1,
    /** Minutes the alarm rings when nobody answers, before [ifIgnored]. */
    val ringMin: Int = 10,
    val ifIgnored: IfIgnored = IfIgnored.Snooze,
    val stopWith: StopMethod = StopMethod.Press,
    val after: AfterAlarm = AfterAlarm.Briefing,
    val background: AlarmBackground = AlarmBackground.Sunrise,
    val showDate: Boolean = true,
    val showWeather: Boolean = true,
    val greeting: GreetingStyle = GreetingStyle.WithName,
    /** The words used when [greeting] is Custom. */
    val message: String = "",
) {
    companion object {
        val WEEKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        val WEEKENDS = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        val EVERY_DAY = DayOfWeek.values().toSet()
    }
}

/** One profile's settings: everything Haven lets a person choose, from the Home Assistant room to the font. */
data class Settings(
    // This room
    val roomName: String = "Living Room",
    /** Home Assistant area id for this TV's room; empty means "not chosen". */
    val haArea: String = "",
    val bedroomExtras: Boolean = false,
    /** The profile's name: the profile picker and the greetings use it. */
    val profileName: String = "",
    // Home Assistant
    val haUrl: String = "",
    val haToken: String = "",
    val weatherEntity: String = "",
    val hiddenEntities: Set<String> = emptySet(),
    // Apps
    val favorites: List<String> = emptyList(),
    val favoritesInitialized: Boolean = false,
    val hiddenApps: Set<String> = emptySet(),
    val use24h: Boolean = false,
    val appLooks: Map<String, AppLook> = emptyMap(),
    // Look
    val look: LookConfig = LookConfig(),
    val background: BackgroundConfig = BackgroundConfig(),
    // Art mode
    val artSource: ArtSource = ArtSource.Paintings,
    val artIntervalMin: Int = 5,
    /** Minutes without input on the home screen before art mode starts; 0 = never. */
    val artIdleMin: Int = 10,
    val artCare: ArtCare = ArtCare(),
    // Morning briefing and sports
    val brief: BriefConfig = BriefConfig(),
    val sports: SportsConfig = SportsConfig(),
    // Layouts
    /** Smart home rows: this room's devices or the whole house's. */
    val homeScope: HomeScope = HomeScope.Room,
    /** Two-letter country for TV schedules and where to watch; empty = the TV's own. */
    val region: String = "",
    val shows: List<FollowedShow> = emptyList(),
    val kids: KidsConfig = KidsConfig(),
    // Home button
    val homeOverride: Boolean = true,
    // Bedroom
    val alarm: AlarmConfig = AlarmConfig(),
    val sleepLightsOff: Boolean = true,
    val sleepBox: Boolean = true,
) {
    val haConfigured: Boolean get() = haUrl.isNotBlank() && haToken.isNotBlank()

    fun appLook(pkg: String): AppLook = appLooks[pkg] ?: AppLook()

    fun withAppLook(pkg: String, look: AppLook): Settings =
        copy(appLooks = if (look.isDefault) appLooks - pkg else appLooks + (pkg to look))

    fun toJson(): JSONObject = JSONObject()
        .put("roomName", roomName)
        .put("haArea", haArea)
        .put("bedroomExtras", bedroomExtras)
        .put("profileName", profileName)
        .put("haUrl", haUrl)
        .put("haToken", haToken)
        .put("weatherEntity", weatherEntity)
        .put("hiddenEntities", JSONArray(hiddenEntities.toList()))
        .put("favorites", JSONArray(favorites))
        .put("favoritesInitialized", favoritesInitialized)
        .put("hiddenApps", JSONArray(hiddenApps.toList()))
        .put("use24h", use24h)
        .put("appLooks", appLooksToJson(appLooks))
        .put("look", look.toJson())
        .put("background", background.toJson())
        .put("artSource", artSource.name)
        .put("artIntervalMin", artIntervalMin)
        .put("artIdleMin", artIdleMin)
        .put("artCare", artCare.toJson())
        .put("brief", brief.toJson())
        .put("sports", sports.toJson())
        .put("homeScope", homeScope.name)
        .put("region", region)
        .put("shows", FollowedShow.listToJson(shows))
        .put("kids", kids.toJson())
        .put("homeOverride", homeOverride)
        .put("alarm", alarm.toJson())
        .put("sleepLightsOff", sleepLightsOff)
        .put("sleepBox", sleepBox)

    companion object {
        fun fromJson(o: JSONObject): Settings {
            val d = Settings()
            return Settings(
                roomName = o.optString("roomName", d.roomName),
                haArea = o.optString("haArea", d.haArea),
                bedroomExtras = o.optBoolean("bedroomExtras", d.bedroomExtras),
                profileName = o.optString("profileName", d.profileName),
                haUrl = o.optString("haUrl", d.haUrl),
                haToken = o.optString("haToken", d.haToken),
                weatherEntity = o.optString("weatherEntity", d.weatherEntity),
                hiddenEntities = o.optJSONArray("hiddenEntities").strings().toSet(),
                favorites = o.optJSONArray("favorites").strings(),
                favoritesInitialized = o.optBoolean("favoritesInitialized", false),
                hiddenApps = o.optJSONArray("hiddenApps").strings().toSet(),
                use24h = o.optBoolean("use24h", d.use24h),
                appLooks = appLooksFromJson(o.optJSONObject("appLooks")),
                look = LookConfig.fromJson(o.optJSONObject("look")),
                background = BackgroundConfig.fromJson(o.optJSONObject("background")),
                artSource = runCatching { ArtSource.valueOf(o.optString("artSource")) }.getOrDefault(d.artSource),
                artIntervalMin = o.optInt("artIntervalMin", d.artIntervalMin),
                artIdleMin = o.optInt("artIdleMin", d.artIdleMin),
                artCare = ArtCare.fromJson(o.optJSONObject("artCare")),
                brief = BriefConfig.fromJson(o.optJSONObject("brief")),
                sports = SportsConfig.fromJson(o.optJSONObject("sports")),
                homeScope = enumOr(o.optString("homeScope"), d.homeScope),
                region = o.optString("region", d.region).trim().uppercase().take(2),
                shows = FollowedShow.listFromJson(o.optJSONArray("shows")),
                kids = KidsConfig.fromJson(o.optJSONObject("kids")),
                homeOverride = o.optBoolean("homeOverride", d.homeOverride),
                alarm = o.optJSONObject("alarm")?.let { alarmFromJson(it) } ?: d.alarm,
                sleepLightsOff = o.optBoolean("sleepLightsOff", d.sleepLightsOff),
                sleepBox = o.optBoolean("sleepBox", d.sleepBox),
            )
        }
    }
}

fun AlarmConfig.toJson(): JSONObject = JSONObject()
    .put("enabled", enabled)
    .put("hour", hour)
    .put("minute", minute)
    .put("days", JSONArray(days.map { it.value }))
    .put("sunriseMin", sunriseMin)
    .put("snoozeMin", snoozeMin)
    .put("sound", sound.name)
    .put("volumePct", volumePct)
    .put("rampSec", rampSec)
    .put("maxSnoozes", maxSnoozes)
    .put("ringMin", ringMin)
    .put("ifIgnored", ifIgnored.name)
    .put("stopWith", stopWith.name)
    .put("after", after.name)
    .put("background", background.name)
    .put("showDate", showDate)
    .put("showWeather", showWeather)
    .put("greeting", greeting.name)
    .put("message", message)

fun alarmFromJson(o: JSONObject): AlarmConfig {
    val d = AlarmConfig()
    val days = o.optJSONArray("days")
    return AlarmConfig(
        enabled = o.optBoolean("enabled", d.enabled),
        hour = o.optInt("hour", d.hour).coerceIn(0, 23),
        minute = o.optInt("minute", d.minute).coerceIn(0, 59),
        days = if (days == null) d.days else (0 until days.length()).mapNotNull { i ->
            days.optInt(i).takeIf { it in 1..7 }?.let { DayOfWeek.of(it) }
        }.toSet(),
        sunriseMin = o.optInt("sunriseMin", d.sunriseMin).coerceIn(0, 60),
        snoozeMin = o.optInt("snoozeMin", d.snoozeMin).coerceIn(1, 60),
        sound = enumOr(o.optString("sound"), d.sound),
        volumePct = o.optInt("volumePct", d.volumePct).coerceIn(10, 100),
        rampSec = o.optInt("rampSec", d.rampSec).coerceIn(0, 300),
        maxSnoozes = o.optInt("maxSnoozes", d.maxSnoozes).coerceIn(-1, 10),
        ringMin = o.optInt("ringMin", d.ringMin).coerceIn(1, 60),
        ifIgnored = enumOr(o.optString("ifIgnored"), d.ifIgnored),
        stopWith = enumOr(o.optString("stopWith"), d.stopWith),
        after = enumOr(o.optString("after"), d.after),
        background = enumOr(o.optString("background"), d.background),
        showDate = o.optBoolean("showDate", d.showDate),
        showWeather = o.optBoolean("showWeather", d.showWeather),
        greeting = enumOr(o.optString("greeting"), d.greeting),
        message = o.optString("message", d.message).trim(),
    )
}

fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf(String::isNotEmpty) }

/** Which profiles exist (in order), whose is showing, and whether to ask when Haven starts. */
data class ProfilesMeta(
    val order: List<String> = listOf(FIRST),
    val active: String = FIRST,
    val askOnStart: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().put("order", JSONArray(order)).put("active", active).put("askOnStart", askOnStart)

    companion object {
        const val FIRST = "p1"

        fun fromJson(o: JSONObject?): ProfilesMeta? {
            if (o == null) return null
            val order = o.optJSONArray("order").strings().distinct()
            if (order.isEmpty()) return null
            val active = o.optString("active").takeIf { it in order } ?: order.first()
            return ProfilesMeta(order, active, o.optBoolean("askOnStart", false))
        }

        /** The next free id: p2, p3, … (ids are never reused while their profile exists). */
        fun newId(taken: Collection<String>): String {
            var n = 2
            while ("p$n" in taken) n++
            return "p$n"
        }
    }
}

/** A profile as the picker shows it. */
data class ProfileInfo(val id: String, val name: String, val avatar: Accent, val active: Boolean)

/**
 * Settings per profile. Each profile's settings are one JSON blob in SharedPreferences ("settings:<id>"); [state]
 * is the active profile, which is what the TV shows. Settings from before profiles become the first profile.
 */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("haven", Context.MODE_PRIVATE)
    private val cache = HashMap<String, Settings>()
    private val _meta = MutableStateFlow(loadMeta())
    val meta: StateFlow<ProfilesMeta> = _meta
    private val _state = MutableStateFlow(read(_meta.value.active))

    /** The active profile's settings. */
    val state: StateFlow<Settings> = _state
    val value: Settings get() = _state.value
    val activeId: String get() = _meta.value.active
    private val _profiles = MutableStateFlow(infos())
    val profiles: StateFlow<List<ProfileInfo>> = _profiles

    private fun key(id: String) = "settings:$id"

    private fun loadMeta(): ProfilesMeta {
        val saved = prefs.getString("profiles", null)?.let { runCatching { JSONObject(it) }.getOrNull() }
        ProfilesMeta.fromJson(saved)?.let { return it }
        val meta = ProfilesMeta()
        val e = prefs.edit()
        prefs.getString("settings", null)?.let { e.putString(key(ProfilesMeta.FIRST), it) }
        e.putString("profiles", meta.toJson().toString()).apply()
        return meta
    }

    private fun read(id: String): Settings = cache.getOrPut(id) {
        prefs.getString(key(id), null)?.let { runCatching { Settings.fromJson(JSONObject(it)) }.getOrNull() } ?: Settings()
    }

    private fun infos(): List<ProfileInfo> {
        val m = _meta.value
        return m.order.mapIndexed { i, id ->
            val s = read(id)
            ProfileInfo(id, s.profileName.ifBlank { "Profile ${i + 1}" }, s.look.avatar, id == m.active)
        }
    }

    private fun saveMeta(m: ProfilesMeta) {
        _meta.value = m
        prefs.edit().putString("profiles", m.toJson().toString()).apply()
        _profiles.value = infos()
    }

    /** Any profile's settings (the alarm of a profile that isn't showing, the phone page editing another one). */
    fun settingsOf(id: String): Settings = synchronized(this) { read(id) }

    fun exists(id: String): Boolean = id in _meta.value.order

    fun all(): List<Pair<String, Settings>> = synchronized(this) { _meta.value.order.map { it to read(it) } }

    fun nameOf(id: String): String = _profiles.value.firstOrNull { it.id == id }?.name ?: ""

    /** Changes the active profile's settings. */
    fun update(block: (Settings) -> Settings) = updateProfile(activeId, block)

    /** Told about every saved change (profile id, before, after), for side effects like rescheduling alarms. */
    @Volatile
    var onChange: ((String, Settings, Settings) -> Unit)? = null

    fun updateProfile(id: String, block: (Settings) -> Settings) {
        val change = synchronized(this) {
            if (id !in _meta.value.order) return
            val cur = read(id)
            val next = block(cur)
            if (next == cur) return
            cache[id] = next
            prefs.edit().putString(key(id), next.toJson().toString()).apply()
            if (id == _meta.value.active) _state.value = next
            _profiles.value = infos()
            cur to next
        }
        onChange?.invoke(id, change.first, change.second)
    }

    fun switchTo(id: String) {
        synchronized(this) {
            val m = _meta.value
            if (id !in m.order || id == m.active) return
            saveMeta(m.copy(active = id))
            _state.value = read(id)
        }
    }

    /**
     * Adds a profile and returns its id. A [copy] starts with everything the active profile has (its alarm off, so
     * one wake-up doesn't ring twice); otherwise it starts fresh, keeping only this TV's room and Home Assistant.
     */
    fun addProfile(name: String, copy: Boolean): String = synchronized(this) {
        val m = _meta.value
        val id = ProfilesMeta.newId(m.order)
        val base = read(m.active)
        val used = m.order.map { read(it).look.avatar }.toSet()
        val avatar = Accent.values().firstOrNull { it !in used && it != Accent.Mono } ?: Accent.Lamp
        val s = if (copy) {
            base.copy(profileName = name.trim(), alarm = base.alarm.copy(enabled = false), look = base.look.copy(avatar = avatar))
        } else {
            Settings(
                profileName = name.trim(),
                roomName = base.roomName,
                haArea = base.haArea,
                bedroomExtras = base.bedroomExtras,
                haUrl = base.haUrl,
                haToken = base.haToken,
                weatherEntity = base.weatherEntity,
                hiddenEntities = base.hiddenEntities,
                use24h = base.use24h,
                homeOverride = base.homeOverride,
                artCare = ArtCare(panel = base.artCare.panel),
                look = LookConfig(avatar = avatar),
                background = BackgroundConfig(tmdbKey = base.background.tmdbKey),
            )
        }
        cache[id] = s
        prefs.edit().putString(key(id), s.toJson().toString()).apply()
        saveMeta(m.copy(order = m.order + id))
        id
    }

    /** Removes a profile (never the last one). Returns false when it couldn't. */
    fun removeProfile(id: String): Boolean = synchronized(this) {
        val m = _meta.value
        if (id !in m.order || m.order.size <= 1) return@synchronized false
        val order = m.order - id
        val active = if (m.active == id) order.first() else m.active
        cache.remove(id)
        prefs.edit().remove(key(id)).apply()
        saveMeta(m.copy(order = order, active = active))
        if (active != m.active) _state.value = read(active)
        true
    }

    fun moveProfile(id: String, by: Int) {
        synchronized(this) {
            val m = _meta.value
            saveMeta(m.copy(order = m.order.moved(id, by)))
        }
    }

    fun setAskOnStart(ask: Boolean) {
        synchronized(this) { saveMeta(_meta.value.copy(askOnStart = ask)) }
    }
}
