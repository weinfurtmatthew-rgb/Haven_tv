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

data class AlarmConfig(
    val enabled: Boolean = false,
    val hour: Int = 6,
    val minute: Int = 30,
    val days: Set<DayOfWeek> = WEEKDAYS,
    /** Minutes of light fade-in before the alarm; 0 turns sunrise lights off. */
    val sunriseMin: Int = 10,
    val snoozeMin: Int = 9,
) {
    companion object {
        val WEEKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        val WEEKENDS = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        val EVERY_DAY = DayOfWeek.values().toSet()
    }
}

data class Settings(
    // This room
    val roomName: String = "Living Room",
    /** Home Assistant area id for this TV's room; empty means "not chosen". */
    val haArea: String = "",
    val bedroomExtras: Boolean = false,
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
    // Art mode
    val artSource: ArtSource = ArtSource.Paintings,
    val artIntervalMin: Int = 5,
    /** Minutes without input on the home screen before art mode starts; 0 = never. */
    val artIdleMin: Int = 10,
    val artCare: ArtCare = ArtCare(),
    // Morning briefing and sports
    val brief: BriefConfig = BriefConfig(),
    val sports: SportsConfig = SportsConfig(),
    // Home button
    val homeOverride: Boolean = true,
    // Bedroom
    val alarm: AlarmConfig = AlarmConfig(),
    val sleepLightsOff: Boolean = true,
    val sleepBox: Boolean = true,
) {
    val haConfigured: Boolean get() = haUrl.isNotBlank() && haToken.isNotBlank()

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
        .put("artSource", artSource.name)
        .put("artIntervalMin", artIntervalMin)
        .put("artIdleMin", artIdleMin)
        .put("artCare", artCare.toJson())
        .put("brief", brief.toJson())
        .put("sports", sports.toJson())
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
                artSource = runCatching { ArtSource.valueOf(o.optString("artSource")) }.getOrDefault(d.artSource),
                artIntervalMin = o.optInt("artIntervalMin", d.artIntervalMin),
                artIdleMin = o.optInt("artIdleMin", d.artIdleMin),
                artCare = ArtCare.fromJson(o.optJSONObject("artCare")),
                brief = BriefConfig.fromJson(o.optJSONObject("brief")),
                sports = SportsConfig.fromJson(o.optJSONObject("sports")),
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
    )
}

fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf(String::isNotEmpty) }

/** Settings live in one JSON blob in SharedPreferences and are exposed as a StateFlow. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("haven", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Settings> = _state
    val value: Settings get() = _state.value

    private fun load(): Settings =
        prefs.getString("settings", null)
            ?.let { runCatching { Settings.fromJson(JSONObject(it)) }.getOrNull() }
            ?: Settings()

    fun update(block: (Settings) -> Settings) {
        synchronized(this) {
            val next = block(_state.value)
            if (next == _state.value) return
            _state.value = next
            prefs.edit().putString("settings", next.toJson().toString()).apply()
        }
    }
}
