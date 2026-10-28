package app.haven.launcher.ha

import app.haven.launcher.data.HomeScope
import app.haven.launcher.data.Settings
import app.haven.launcher.demo.Demo
import app.haven.launcher.data.SettingsStore
import app.haven.launcher.data.strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

data class HaEntity(val id: String, val state: String, val attrs: JSONObject) {
    val domain: String get() = id.substringBefore('.')
    val name: String
        get() = attrs.optString("friendly_name").ifBlank { id.substringAfter('.').replace('_', ' ') }
    val available: Boolean get() = state != "unavailable" && state != "unknown"
    val isOn: Boolean
        get() = when (domain) {
            "media_player" -> state !in setOf("off", "standby", "unavailable", "unknown")
            else -> state == "on"
        }
    private val features: Int get() = attrs.optInt("supported_features", 0)
    val dimmable: Boolean
        get() = domain == "light" &&
            (attrs.optJSONArray("supported_color_modes").strings().any { it != "onoff" } || features and 1 != 0)

    /** 0..100, or null when off / not reported. */
    val brightnessPct: Int?
        get() = if (attrs.has("brightness") && !attrs.isNull("brightness")) {
            (attrs.optDouble("brightness", 0.0) / 255.0 * 100).roundToInt().coerceIn(0, 100)
        } else null

    val canSetVolume: Boolean get() = domain == "media_player" && features and 4 != 0
    val volume: Float?
        get() = if (attrs.has("volume_level") && !attrs.isNull("volume_level")) attrs.optDouble("volume_level").toFloat() else null

    fun copyWith(state: String = this.state, attrs: Map<String, Any?> = emptyMap()): HaEntity {
        val a = JSONObject(this.attrs.toString())
        for ((k, v) in attrs) a.put(k, v ?: JSONObject.NULL)
        return HaEntity(id, state, a)
    }
}

data class HaArea(val id: String, val name: String, val entities: Set<String>)

sealed interface HaStatus {
    data object NotConfigured : HaStatus
    data object Connecting : HaStatus
    data class Connected(val entityCount: Int) : HaStatus
    data class Failed(val message: String) : HaStatus
}

/** Talks to Home Assistant's REST API with a long-lived access token. */
class HomeAssistant(private val store: SettingsStore) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val json = "application/json".toMediaType()

    private val _status = MutableStateFlow<HaStatus>(HaStatus.NotConfigured)
    val status: StateFlow<HaStatus> = _status
    private val _entities = MutableStateFlow<Map<String, HaEntity>>(emptyMap())
    val entities: StateFlow<Map<String, HaEntity>> = _entities
    private val _areas = MutableStateFlow<List<HaArea>>(emptyList())
    val areas: StateFlow<List<HaArea>> = _areas
    private var areasLoadedFor = ""
    private var areasLoadedAt = 0L

    private fun request(s: Settings, path: String) =
        Request.Builder().url(normalizeUrl(s.haUrl) + path).header("Authorization", "Bearer ${s.haToken.trim()}")

    /** Fetches every entity state. Also loads the area list the first time and every 10 minutes. */
    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        val s = store.value
        if (Demo.active) {
            // Keep local changes (toggles, levels) between polls; only seed once.
            if (_entities.value["light.floor_lamp"] == null) _entities.value = Demo.entities()
            _entities.value = Demo.playing(_entities.value)
            _areas.value = Demo.areas()
            _status.value = HaStatus.Connected(_entities.value.size)
            return@withContext true
        }
        if (!s.haConfigured) {
            _status.value = HaStatus.NotConfigured
            _entities.value = emptyMap()
            return@withContext false
        }
        if (_status.value !is HaStatus.Connected) _status.value = HaStatus.Connecting
        try {
            http.newCall(request(s, "/api/states").get().build()).execute().use { r ->
                if (r.code == 401) error("Home Assistant rejected the access token")
                if (!r.isSuccessful) error("Home Assistant answered ${r.code}")
                val arr = JSONArray(r.body!!.string())
                val map = HashMap<String, HaEntity>(arr.length())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val id = o.optString("entity_id")
                    map[id] = HaEntity(id, o.optString("state"), o.optJSONObject("attributes") ?: JSONObject())
                }
                _entities.value = map
                _status.value = HaStatus.Connected(map.size)
            }
            val key = normalizeUrl(s.haUrl) + s.haToken.hashCode()
            if (key != areasLoadedFor || System.currentTimeMillis() - areasLoadedAt > 10 * 60_000) {
                runCatching { _areas.value = fetchAreas(s) }
                areasLoadedFor = key
                areasLoadedAt = System.currentTimeMillis()
            }
            true
        } catch (e: Exception) {
            _status.value = HaStatus.Failed(e.friendly())
            false
        }
    }

    /** Areas come from the template API (needs an admin user's token); without it Haven shows everything. */
    private fun fetchAreas(s: Settings): List<HaArea> {
        val template = """
            {% set ns = namespace(items=[]) %}
            {% for a in areas() %}{% set ns.items = ns.items + [{"id": a, "name": area_name(a), "entities": area_entities(a)}] %}{% endfor %}
            {{ ns.items | tojson }}
        """.trimIndent()
        val body = JSONObject().put("template", template).toString().toRequestBody(json)
        http.newCall(request(s, "/api/template").post(body).build()).execute().use { r ->
            if (!r.isSuccessful) return emptyList()
            val arr = JSONArray(r.body!!.string().trim())
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                HaArea(o.optString("id"), o.optString("name"), o.optJSONArray("entities").strings().toSet())
            }.sortedBy { it.name.lowercase() }
        }
    }

    suspend fun call(domain: String, service: String, data: JSONObject): Boolean = withContext(Dispatchers.IO) {
        val s = store.value
        if (Demo.active) return@withContext true
        if (!s.haConfigured) return@withContext false
        runCatching {
            http.newCall(request(s, "/api/services/$domain/$service").post(data.toString().toRequestBody(json)).build())
                .execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    // ---- briefing data ------------------------------------------------------------------------

    /** Hourly or daily forecast through `weather.get_forecasts` (HA 2023.9+), falling back to the old attribute. */
    suspend fun forecast(entityId: String, type: String): List<Forecast> = withContext(Dispatchers.IO) {
        if (Demo.active) return@withContext Demo.forecast(type)
        val s = store.value
        if (!s.haConfigured || entityId.isBlank()) return@withContext emptyList()
        val fromService = runCatching {
            val body = JSONObject().put("entity_id", entityId).put("type", type).toString().toRequestBody(json)
            http.newCall(request(s, "/api/services/weather/get_forecasts?return_response").post(body).build()).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val root = JSONObject(r.body!!.string())
                val resp = root.optJSONObject("service_response") ?: root
                resp.optJSONObject(entityId)?.optJSONArray("forecast")
            }
        }.getOrNull() ?: _entities.value[entityId]?.attrs?.optJSONArray("forecast")
        Forecast.parse(fromService)
    }

    /** Calendar entities: id to name. */
    suspend fun calendars(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        if (Demo.active) return@withContext Demo.calendars()
        val s = store.value
        if (!s.haConfigured) return@withContext emptyList()
        runCatching {
            http.newCall(request(s, "/api/calendars").get().build()).execute().use { r ->
                if (!r.isSuccessful) return@use emptyList()
                val arr = JSONArray(r.body!!.string())
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    o.optString("entity_id") to o.optString("name").ifBlank { o.optString("entity_id") }
                }
            }
        }.getOrDefault(emptyList())
    }

    /** Events between [startIso] and [endIso] (ISO-8601 with offset) for one calendar. */
    suspend fun events(calendarId: String, startIso: String, endIso: String): List<CalEvent> = withContext(Dispatchers.IO) {
        if (Demo.active) return@withContext Demo.events(calendarId)
        val s = store.value
        if (!s.haConfigured) return@withContext emptyList()
        runCatching {
            val q = "?start=" + java.net.URLEncoder.encode(startIso, "UTF-8") + "&end=" + java.net.URLEncoder.encode(endIso, "UTF-8")
            http.newCall(request(s, "/api/calendars/$calendarId$q").get().build()).execute().use { r ->
                if (!r.isSuccessful) return@use emptyList()
                CalEvent.parse(JSONArray(r.body!!.string()))
            }
        }.getOrDefault(emptyList())
    }

    /** Applies a guessed state locally so the UI reacts before the next poll. */
    fun assume(entity: HaEntity) {
        _entities.value = _entities.value + (entity.id to entity)
    }

    /** Checks a URL + token pair without saving it. Returns a short human message. */
    suspend fun test(url: String, token: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(normalizeUrl(url) + "/api/states")
                .header("Authorization", "Bearer ${token.trim()}").get().build()
            http.newCall(req).execute().use { r ->
                if (r.code == 401) error("The access token was rejected")
                if (!r.isSuccessful) error("Home Assistant answered ${r.code}")
                val n = JSONArray(r.body!!.string()).length()
                "Connected: $n entities"
            }
        }.recoverCatching { throw IllegalStateException(it.friendly()) }
    }

    // ---- room views ---------------------------------------------------------------------------

    private fun inRoom(s: Settings): (String) -> Boolean {
        val area = _areas.value.firstOrNull { it.id == s.haArea }
        return { id -> (area == null || id in area.entities) && id !in s.hiddenEntities }
    }

    fun roomLights(s: Settings = store.value): List<HaEntity> {
        val ok = inRoom(s)
        return _entities.value.values.filter { e ->
            ok(e.id) && (e.domain == "light" || (e.domain == "switch" && LIGHTISH.containsMatchIn(e.name)))
        }.sortedBy { it.name.lowercase() }
    }

    fun roomSpeakers(s: Settings = store.value): List<HaEntity> {
        val ok = inRoom(s)
        return _entities.value.values.filter { it.domain == "media_player" && ok(it.id) && it.available }
            .sortedBy { it.name.lowercase() }
    }

    fun roomScenes(s: Settings = store.value): List<HaEntity> {
        val all = _entities.value.values.filter { it.domain == "scene" && it.id !in s.hiddenEntities }
        val area = _areas.value.firstOrNull { it.id == s.haArea } ?: return all.sortedBy { it.name }.take(4)
        val inArea = all.filter { it.id in area.entities }
        val chosen = inArea.ifEmpty { all.filter { it.name.contains(area.name, ignoreCase = true) } }
        return chosen.sortedBy { it.name.lowercase() }.take(4)
    }

    // ---- smart home rows ----------------------------------------------------------------------

    /**
     * Entities of [domains] for the smart home rows: this room's (or the whole house's, as the profile chose); when
     * the room has none of a kind, the house's, so a room without a thermostat still shows the one in the hall.
     */
    fun devices(s: Settings, vararg domains: String, filter: (HaEntity) -> Boolean = { true }): List<HaEntity> {
        val all = _entities.value.values.filter { it.domain in domains && it.id !in s.hiddenEntities && it.available && filter(it) }
            .sortedBy { it.name.lowercase() }
        if (s.homeScope == HomeScope.House) return all
        val ok = inRoom(s)
        return all.filter { ok(it.id) }.ifEmpty { all }
    }

    fun lightsFor(s: Settings): List<HaEntity> =
        if (s.homeScope == HomeScope.House) {
            _entities.value.values.filter { e ->
                e.id !in s.hiddenEntities && (e.domain == "light" || (e.domain == "switch" && LIGHTISH.containsMatchIn(e.name)))
            }.sortedBy { it.name.lowercase() }
        } else {
            roomLights(s)
        }

    fun scenesFor(s: Settings): List<HaEntity> =
        if (s.homeScope == HomeScope.House) devices(s, "scene", "script") else roomScenes(s).ifEmpty { devices(s, "scene", "script") }

    private val SENSOR_CLASSES = setOf("temperature", "humidity")
    private val OPENING_CLASSES = setOf("door", "window", "opening", "garage_door", "motion", "occupancy", "moisture", "smoke")

    fun sensorsFor(s: Settings): List<HaEntity> = devices(s, "sensor", "binary_sensor") { e ->
        val cls = e.attrs.optString("device_class")
        if (e.domain == "sensor") cls in SENSOR_CLASSES else cls in OPENING_CLASSES
    }

    /** People Home Assistant tracks (home or away). */
    fun people(): List<HaEntity> = _entities.value.values.filter { it.domain == "person" }.sortedBy { it.name.lowercase() }

    /** The full address of an entity's picture: a camera's snapshot or an album cover (signed by Home Assistant). */
    fun picture(e: HaEntity, s: Settings = store.value): String? {
        val pic = e.attrs.optString("entity_picture").takeIf { it.isNotBlank() && it != "null" } ?: return null
        return if (pic.startsWith("http") || pic.startsWith("/data") || pic.startsWith("file:")) pic else normalizeUrl(s.haUrl) + pic
    }

    suspend fun setTemperature(e: HaEntity, target: Double): Boolean {
        assume(e.copyWith(attrs = mapOf("temperature" to target)))
        return call("climate", "set_temperature", JSONObject().put("entity_id", e.id).put("temperature", target))
    }

    suspend fun setHvacMode(e: HaEntity, mode: String): Boolean {
        assume(e.copyWith(mode))
        return call("climate", "set_hvac_mode", JSONObject().put("entity_id", e.id).put("hvac_mode", mode))
    }

    suspend fun stopCover(e: HaEntity): Boolean = call("cover", "stop_cover", JSONObject().put("entity_id", e.id))

    suspend fun setLocked(e: HaEntity, locked: Boolean): Boolean {
        assume(e.copyWith(if (locked) "locked" else "unlocked"))
        return call("lock", if (locked) "lock" else "unlock", JSONObject().put("entity_id", e.id))
    }

    suspend fun setOpen(e: HaEntity, open: Boolean): Boolean {
        assume(e.copyWith(if (open) "open" else "closed"))
        return call("cover", if (open) "open_cover" else "close_cover", JSONObject().put("entity_id", e.id))
    }

    suspend fun setAutomation(e: HaEntity, on: Boolean): Boolean {
        assume(e.copyWith(if (on) "on" else "off"))
        return call("automation", if (on) "turn_on" else "turn_off", JSONObject().put("entity_id", e.id))
    }

    suspend fun runAutomation(e: HaEntity): Boolean =
        call("automation", "trigger", JSONObject().put("entity_id", e.id))

    /** Play or pause, next or previous on a speaker ("media_play_pause", "media_next_track", "media_previous_track"). */
    suspend fun media(e: HaEntity, service: String): Boolean {
        if (service == "media_play_pause") assume(e.copyWith(if (e.state == "playing") "paused" else "playing"))
        return call("media_player", service, JSONObject().put("entity_id", e.id))
    }

    fun weather(s: Settings = store.value): HaEntity? =
        _entities.value[s.weatherEntity] ?: _entities.value.values.filter { it.domain == "weather" }.minByOrNull { it.id }

    // ---- actions ------------------------------------------------------------------------------

    suspend fun setLight(e: HaEntity, on: Boolean, brightnessPct: Int? = null): Boolean {
        val data = JSONObject().put("entity_id", e.id)
        if (on && brightnessPct != null && e.dimmable) data.put("brightness_pct", brightnessPct.coerceIn(1, 100))
        assume(e.copyWith(if (on) "on" else "off", if (brightnessPct != null && on) mapOf("brightness" to brightnessPct * 255 / 100) else emptyMap()))
        return call(e.domain, if (on) "turn_on" else "turn_off", data)
    }

    suspend fun setLights(ids: List<HaEntity>, on: Boolean, brightnessPct: Int? = null, transitionSec: Int? = null): Boolean {
        var ok = true
        for ((domain, group) in ids.groupBy { it.domain }) {
            val data = JSONObject().put("entity_id", JSONArray(group.map { it.id }))
            if (domain == "light" && on && brightnessPct != null) data.put("brightness_pct", brightnessPct)
            if (domain == "light" && transitionSec != null) data.put("transition", transitionSec)
            ok = call(domain, if (on) "turn_on" else "turn_off", data) && ok
        }
        return ok
    }

    suspend fun setPower(e: HaEntity, on: Boolean): Boolean {
        assume(e.copyWith(if (on) "on" else "off"))
        return call(e.domain, if (on) "turn_on" else "turn_off", JSONObject().put("entity_id", e.id))
    }

    suspend fun setVolume(e: HaEntity, level: Float): Boolean {
        val v = level.coerceIn(0f, 1f)
        assume(e.copyWith(attrs = mapOf("volume_level" to v.toDouble())))
        return call("media_player", "volume_set", JSONObject().put("entity_id", e.id).put("volume_level", v.toDouble()))
    }

    suspend fun activate(scene: HaEntity): Boolean =
        call(scene.domain, "turn_on", JSONObject().put("entity_id", scene.id))

    companion object {
        private val LIGHTISH = Regex("light|lamp|sconce|chandelier|lantern", RegexOption.IGNORE_CASE)

        fun normalizeUrl(raw: String): String {
            var u = raw.trim().trimEnd('/')
            if (u.isEmpty()) return u
            if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://$u"
            val hostPart = u.substringAfter("://")
            if (!hostPart.contains(':') && !hostPart.contains('/') && u.startsWith("http://")) u += ":8123"
            return u
        }

        private fun Throwable.friendly(): String = when (this) {
            is java.net.UnknownHostException -> "Can't find that address on your network"
            is java.net.ConnectException -> "Nothing answered at that address"
            is java.net.SocketTimeoutException -> "Home Assistant didn't answer in time"
            is javax.net.ssl.SSLException -> "Secure connection failed (check http vs https)"
            else -> message ?: javaClass.simpleName
        }
    }
}

data class Forecast(val time: java.time.OffsetDateTime?, val temperature: Double?, val low: Double?, val condition: String) {
    companion object {
        fun parse(arr: JSONArray?): List<Forecast> {
            if (arr == null) return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                Forecast(
                    runCatching { java.time.OffsetDateTime.parse(o.optString("datetime")) }.getOrNull(),
                    o.optDouble("temperature").takeIf { !it.isNaN() },
                    o.optDouble("templow").takeIf { !it.isNaN() },
                    o.optString("condition"),
                )
            }
        }
    }
}

data class CalEvent(val summary: String, val start: java.time.OffsetDateTime?, val allDay: Boolean) {
    companion object {
        fun parse(arr: JSONArray): List<CalEvent> = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val start = o.optJSONObject("start")
            val dt = start?.optString("dateTime").orEmpty()
            val date = start?.optString("date").orEmpty()
            CalEvent(
                o.optString("summary").ifBlank { "Busy" },
                when {
                    dt.isNotBlank() -> runCatching { java.time.OffsetDateTime.parse(dt) }.getOrNull()
                    date.isNotBlank() -> runCatching {
                        java.time.LocalDate.parse(date).atStartOfDay(java.time.ZoneId.systemDefault()).toOffsetDateTime()
                    }.getOrNull()
                    else -> null
                },
                allDay = dt.isBlank() && date.isNotBlank(),
            )
        }
    }
}
