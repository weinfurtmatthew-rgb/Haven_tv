package app.haven.launcher.setup

import android.content.Context
import android.net.ConnectivityManager
import app.haven.launcher.BuildConfig
import app.haven.launcher.Haven
import app.haven.launcher.art.ArtRepository
import app.haven.launcher.bedroom.Bedroom
import app.haven.launcher.data.ArtCare
import app.haven.launcher.data.ArtSource
import app.haven.launcher.data.BriefConfig
import app.haven.launcher.data.SportsConfig
import app.haven.launcher.sports.League
import app.haven.launcher.data.Settings
import app.haven.launcher.data.alarmFromJson
import app.haven.launcher.data.strings
import app.haven.launcher.data.toJson
import app.haven.launcher.ha.HaEntity
import app.haven.launcher.ha.HaStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.concurrent.Executors

/**
 * A tiny HTTP server for the phone setup page. It runs only while Haven's settings screen is open,
 * and every API call must carry the one-time code shown on the TV.
 */
class SetupServer(private val context: Context) {
    private val code: String = buildString {
        val rnd = SecureRandom()
        repeat(6) { append(ALPHABET[rnd.nextInt(ALPHABET.length)]) }
    }
    private val _url = MutableStateFlow<String?>(null)
    val url: StateFlow<String?> = _url

    @Volatile
    private var socket: ServerSocket? = null
    private val pool = Executors.newFixedThreadPool(4)

    fun start() {
        if (socket != null) return
        Thread({
            val ss = (PORTS.firstNotNullOfOrNull { p -> runCatching { ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(p)) } }.getOrNull() })
                ?: return@Thread
            socket = ss
            _url.value = localIp()?.let { "http://$it:${ss.localPort}/?k=$code" }
            while (!ss.isClosed) {
                val client = runCatching { ss.accept() }.getOrNull() ?: break
                pool.execute { runCatching { client.use { handle(it) } } }
            }
        }, "haven-setup").start()
    }

    fun stop() {
        runCatching { socket?.close() }
        socket = null
        pool.shutdown()
    }

    // ---- HTTP ---------------------------------------------------------------------------------

    private class Req(val method: String, val path: String, val query: Map<String, String>, val length: Long, val body: InputStream)

    private fun handle(sock: Socket) {
        sock.soTimeout = 30_000
        val input = sock.getInputStream().buffered()
        val out = sock.getOutputStream()
        val requestLine = readLine(input) ?: return
        val parts = requestLine.split(' ')
        if (parts.size < 2) return
        var length = 0L
        while (true) {
            val h = readLine(input) ?: return
            if (h.isEmpty()) break
            val i = h.indexOf(':')
            if (i > 0 && h.substring(0, i).trim().equals("content-length", true)) length = h.substring(i + 1).trim().toLongOrNull() ?: 0L
        }
        val target = parts[1]
        val path = target.substringBefore('?')
        val query = target.substringAfter('?', "").split('&').filter { it.contains('=') }.associate {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }
        val req = Req(parts[0], path, query, length, LimitedStream(input, length))

        if (req.method == "GET" && (path == "/" || path == "/index.html")) {
            val html = context.assets.open("setup.html").use { it.readBytes() }
            return send(out, 200, "text/html; charset=utf-8", html)
        }
        if (!path.startsWith("/api/")) return send(out, 404, "text/plain", "Not found".toByteArray())
        if (req.query["k"]?.uppercase() != code) return json(out, 403, JSONObject().put("error", "The code doesn't match the one on the TV. Scan the QR code again."))

        try {
            when ("${req.method} $path") {
                "GET /api/state" -> json(out, 200, state())
                "POST /api/settings" -> {
                    applySettings(JSONObject(readBody(req)))
                    json(out, 200, state())
                }
                "POST /api/test" -> {
                    val o = JSONObject(readBody(req))
                    val token = o.optString("token").ifBlank { Haven.settings.value.haToken }
                    val r = runBlocking { Haven.ha.test(o.optString("url"), token) }
                    json(out, 200, JSONObject().put("ok", r.isSuccess).put("message", r.getOrNull() ?: r.exceptionOrNull()?.message))
                }
                "POST /api/photos" -> json(out, 200, savePhoto(req))
                "GET /api/sports/teams" -> {
                    val league = League.byId(req.query["league"].orEmpty()) ?: League.NFL
                    val teams = runBlocking { Haven.sports.provider.teams(league) }
                    json(out, 200, JSONObject().put("teams", JSONArray(teams.map { it.toFavorite().toJson() })))
                }
                "POST /api/photos/delete" -> {
                    val f = photoFile(req.query["name"].orEmpty())
                    if (f != null) f.delete()
                    json(out, 200, state())
                }
                "GET /api/photo" -> {
                    val f = photoFile(req.query["name"].orEmpty())
                    if (f == null || !f.exists()) send(out, 404, "text/plain", ByteArray(0))
                    else send(out, 200, "image/${f.extension.lowercase().replace("jpg", "jpeg")}", f.readBytes())
                }
                else -> send(out, 404, "text/plain", "Not found".toByteArray())
            }
        } catch (e: Exception) {
            json(out, 400, JSONObject().put("error", e.message ?: "Bad request"))
        }
    }

    private fun readBody(req: Req): String {
        if (req.length > 1_000_000) error("Too large")
        return req.body.readBytes().toString(Charsets.UTF_8)
    }

    private fun send(out: OutputStream, status: Int, type: String, body: ByteArray) {
        val reason = when (status) { 200 -> "OK"; 403 -> "Forbidden"; 404 -> "Not Found"; else -> "Bad Request" }
        val head = "HTTP/1.1 $status $reason\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\nConnection: close\r\n\r\n"
        out.write(head.toByteArray())
        out.write(body)
        out.flush()
    }

    private fun json(out: OutputStream, status: Int, o: JSONObject) = send(out, status, "application/json", o.toString().toByteArray())

    private fun readLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) return if (buf.size() == 0) null else buf.toString("ISO-8859-1")
            if (b == '\n'.code) break
            if (b != '\r'.code) buf.write(b)
            if (buf.size() > 16_384) return null
        }
        return buf.toString("ISO-8859-1")
    }

    /** Reads at most [limit] bytes of the request body. */
    private class LimitedStream(private val inner: InputStream, private var limit: Long) : InputStream() {
        override fun read(): Int {
            if (limit <= 0) return -1
            val b = inner.read()
            if (b >= 0) limit--
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (limit <= 0) return -1
            val n = inner.read(b, off, minOf(len.toLong(), limit).toInt())
            if (n > 0) limit -= n
            return n
        }
    }

    // ---- API ----------------------------------------------------------------------------------

    private fun state(): JSONObject {
        val s = Haven.settings.value
        val ha = Haven.ha
        val all = s.copy(hiddenEntities = emptySet())
        fun list(items: List<HaEntity>) = JSONArray(items.map {
            JSONObject().put("id", it.id).put("name", it.name).put("hidden", it.id in s.hiddenEntities)
        })
        val status = when (val st = ha.status.value) {
            HaStatus.NotConfigured -> "Not set up"
            HaStatus.Connecting -> "Connecting…"
            is HaStatus.Connected -> "Connected · ${st.entityCount} entities"
            is HaStatus.Failed -> st.message
        }
        return JSONObject()
            .put("version", BuildConfig.VERSION_NAME)
            .put(
                "room",
                JSONObject().put("name", s.roomName).put("area", s.haArea).put("bedroom", s.bedroomExtras).put("profile", s.profileName),
            )
            .put(
                "ha",
                JSONObject()
                    .put("url", s.haUrl)
                    .put("tokenSet", s.haToken.isNotBlank())
                    .put("status", status)
                    .put("connected", ha.status.value is HaStatus.Connected)
                    .put("areas", JSONArray(ha.areas.value.map { JSONObject().put("id", it.id).put("name", it.name) }))
                    .put("weatherEntity", s.weatherEntity)
                    .put("weathers", JSONArray(ha.entities.value.values.filter { it.domain == "weather" }.map { JSONObject().put("id", it.id).put("name", it.name) })),
            )
            .put(
                "entities",
                JSONObject()
                    .put("lights", list(ha.roomLights(all)))
                    .put("speakers", list(ha.roomSpeakers(all)))
                    .put("scenes", list(ha.roomScenes(all))),
            )
            .put(
                "art",
                JSONObject()
                    .put("source", s.artSource.name)
                    .put("sources", JSONArray(ArtSource.values().map { JSONObject().put("id", it.name).put("label", it.label) }))
                    .put("interval", s.artIntervalMin)
                    .put("idle", s.artIdleMin),
            )
            .put("photos", JSONArray(Haven.art.photos().map { it.name }))
            .put("alarm", s.alarm.toJson())
            .put("use24h", s.use24h)
            .put("artCare", s.artCare.toJson())
            .put("brief", s.brief.toJson())
            .put("sports", s.sports.toJson())
            .put("leagues", JSONArray(League.values().map { JSONObject().put("id", it.id).put("label", it.label) }))
            .put(
                "choices",
                JSONObject()
                    .put("calendars", JSONArray(runBlocking { ha.calendars() }.map { JSONObject().put("id", it.first).put("name", it.second) }))
                    .put("presence", entityList { it.domain == "binary_sensor" && it.attrs.optString("device_class") in PRESENCE_CLASSES || it.domain == "person" })
                    .put("commute", entityList { it.domain == "sensor" && it.attrs.optString("unit_of_measurement") in setOf("min", "minutes") })
                    .put(
                        "status",
                        entityList {
                            it.domain == "lock" || it.domain == "cover" || it.domain == "alarm_control_panel" ||
                                (it.domain == "binary_sensor" && it.attrs.optString("device_class") in DOOR_CLASSES)
                        },
                    ),
            )
    }

    private fun applySettings(o: JSONObject) {
        val before = Haven.settings.value
        Haven.settings.update { cur ->
            var s: Settings = cur
            if (o.has("roomName")) s = s.copy(roomName = o.getString("roomName").trim().ifBlank { cur.roomName })
            if (o.has("haArea")) s = s.copy(haArea = o.getString("haArea"))
            if (o.has("bedroomExtras")) s = s.copy(bedroomExtras = o.getBoolean("bedroomExtras"))
            if (o.has("profileName")) s = s.copy(profileName = o.getString("profileName").trim())
            if (o.has("haUrl")) s = s.copy(haUrl = o.getString("haUrl").trim())
            if (o.optString("haToken").isNotBlank()) s = s.copy(haToken = o.getString("haToken").trim())
            if (o.has("weatherEntity")) s = s.copy(weatherEntity = o.getString("weatherEntity"))
            if (o.has("hiddenEntities")) s = s.copy(hiddenEntities = o.optJSONArray("hiddenEntities").strings().toSet())
            if (o.has("artSource")) s = s.copy(artSource = runCatching { ArtSource.valueOf(o.getString("artSource")) }.getOrDefault(cur.artSource))
            if (o.has("artIntervalMin")) s = s.copy(artIntervalMin = o.getInt("artIntervalMin").coerceIn(1, 240))
            if (o.has("artIdleMin")) s = s.copy(artIdleMin = o.getInt("artIdleMin").coerceIn(0, 240))
            if (o.has("use24h")) s = s.copy(use24h = o.getBoolean("use24h"))
            if (o.has("alarm")) s = s.copy(alarm = alarmFromJson(o.getJSONObject("alarm")))
            if (o.has("artCare")) s = s.copy(artCare = ArtCare.fromJson(o.getJSONObject("artCare")))
            if (o.has("brief")) s = s.copy(brief = BriefConfig.fromJson(o.getJSONObject("brief")))
            if (o.has("sports")) s = s.copy(sports = SportsConfig.fromJson(o.getJSONObject("sports")))
            s
        }
        val after = Haven.settings.value
        if (after.haUrl != before.haUrl || after.haToken != before.haToken || after.haArea != before.haArea) {
            runBlocking { Haven.ha.refresh() }
        }
        if (after.alarm != before.alarm) Haven.scope.launch { Bedroom.scheduleAlarm(context) }
        if (after.sports != before.sports) {
            Haven.scope.launch { app.haven.launcher.sports.ScoreAlerts.sync(context, Haven.sports.refresh()) }
        }
    }

    private fun entityList(pick: (HaEntity) -> Boolean): JSONArray = JSONArray(
        Haven.ha.entities.value.values.filter(pick).sortedBy { it.name.lowercase() }
            .map { JSONObject().put("id", it.id).put("name", it.name) },
    )

    private fun savePhoto(req: Req): JSONObject {
        if (req.length <= 0 || req.length > 40_000_000) error("Photos must be under 40 MB")
        val raw = req.query["name"].orEmpty().substringAfterLast('/')
        var ext = raw.substringAfterLast('.', "jpg").lowercase()
        if (ext !in ArtRepository.IMAGE_EXT) ext = "jpg"
        val base = raw.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9 _.-]"), "").trim().take(60).ifBlank { "Photo" }
        val dir = Haven.art.photosDir
        var f = File(dir, "$base.$ext")
        var n = 2
        while (f.exists()) f = File(dir, "$base ${n++}.$ext")
        f.outputStream().use { req.body.copyTo(it) }
        if (f.length() != req.length) {
            f.delete()
            error("Upload was cut off")
        }
        req.query["taken"]?.toLongOrNull()?.let { f.setLastModified(it) }
        return JSONObject().put("ok", true).put("name", f.name)
    }

    private fun photoFile(name: String): File? {
        val clean = name.substringAfterLast('/')
        if (clean.isBlank() || clean.startsWith(".")) return null
        return File(Haven.art.photosDir, clean)
    }

    private fun localIp(): String? {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val fromActive = runCatching {
            cm.getLinkProperties(cm.activeNetwork)?.linkAddresses
                ?.map { it.address }
                ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                ?.hostAddress
        }.getOrNull()
        if (fromActive != null) return fromActive
        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
        }.getOrNull()
    }

    companion object {
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private val PORTS = listOf(8765, 8766, 8767, 0)
        private val PRESENCE_CLASSES = setOf("motion", "occupancy", "presence")
        private val DOOR_CLASSES = setOf("door", "garage_door", "window", "opening", "lock")
    }
}
