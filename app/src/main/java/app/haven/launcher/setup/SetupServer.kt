package app.haven.launcher.setup

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import androidx.compose.ui.graphics.asAndroidBitmap
import app.haven.launcher.BuildConfig
import app.haven.launcher.Haven
import app.haven.launcher.R
import app.haven.launcher.art.ArtRepository
import app.haven.launcher.data.AppLook
import app.haven.launcher.data.AppStyle
import app.haven.launcher.data.ArtCare
import app.haven.launcher.data.BriefConfig
import app.haven.launcher.data.LookConfig
import app.haven.launcher.data.Opt
import app.haven.launcher.data.OptSection
import app.haven.launcher.data.Options
import app.haven.launcher.data.Settings
import app.haven.launcher.data.SportsConfig
import app.haven.launcher.data.alarmFromJson
import app.haven.launcher.data.enumOr
import app.haven.launcher.data.strings
import app.haven.launcher.data.toJson
import app.haven.launcher.ha.HaEntity
import app.haven.launcher.ha.HaStatus
import app.haven.launcher.sports.League
import app.haven.launcher.ui.MAX_PROFILES
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
 * A tiny HTTP server for the phone setup page. It runs only while Haven's settings screen is open, and every API
 * call must carry the one-time code shown on the TV. Calls take `p` (a profile id) to change someone else's
 * settings; without it they change the profile showing on the TV.
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

    private class Req(val method: String, val path: String, val query: Map<String, String>, val length: Long, val body: InputStream) {
        /** The profile this call is about: `p` if it names one, otherwise the one on the TV. */
        val profile: String get() = query["p"]?.takeIf { Haven.settings.exists(it) } ?: Haven.settings.activeId
    }

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
        // Haven's fonts for the page. Not secret, so no code needed; the page must work without the internet.
        if (req.method == "GET" && path.startsWith("/font/")) {
            val id = FONTS[path.removePrefix("/font/")] ?: return send(out, 404, "text/plain", "Not found".toByteArray())
            return send(out, 200, "font/ttf", context.resources.openRawResource(id).use { it.readBytes() })
        }
        if (!path.startsWith("/api/")) return send(out, 404, "text/plain", "Not found".toByteArray())
        if (req.query["k"]?.uppercase() != code) return json(out, 403, JSONObject().put("error", "The code doesn't match the one on the TV. Scan the QR code again."))

        try {
            when ("${req.method} $path") {
                "GET /api/state" -> json(out, 200, state(req.profile))
                "POST /api/settings" -> {
                    applySettings(req.profile, JSONObject(readBody(req)))
                    json(out, 200, state(req.profile))
                }
                "POST /api/option" -> {
                    applyOption(req.profile, JSONObject(readBody(req)))
                    json(out, 200, state(req.profile))
                }
                "POST /api/profiles" -> json(out, 200, state(profileAction(req.profile, JSONObject(readBody(req)))))
                "POST /api/test" -> {
                    val o = JSONObject(readBody(req))
                    val token = o.optString("token").ifBlank { Haven.settings.settingsOf(req.profile).haToken }
                    val r = runBlocking { Haven.ha.test(o.optString("url"), token) }
                    json(out, 200, JSONObject().put("ok", r.isSuccess).put("message", r.getOrNull() ?: r.exceptionOrNull()?.message))
                }
                "POST /api/photos" -> json(out, 200, saveImage(req, Haven.art.photosDir, keepName = true).let { JSONObject().put("ok", true).put("name", it.name) })
                "GET /api/sports/teams" -> {
                    val league = League.byId(req.query["league"].orEmpty()) ?: League.NFL
                    val teams = runBlocking { Haven.sports.provider.teams(league) }
                    json(out, 200, JSONObject().put("teams", JSONArray(teams.map { it.toFavorite().toJson() })))
                }
                "POST /api/photos/delete" -> {
                    fileIn(Haven.art.photosDir, req.query["name"].orEmpty())?.delete()
                    json(out, 200, state(req.profile))
                }
                "GET /api/photo" -> sendFile(out, fileIn(Haven.art.photosDir, req.query["name"].orEmpty()))
                "POST /api/backgrounds" -> {
                    val f = saveImage(req, Haven.backgrounds.dir, keepName = true)
                    json(out, 200, JSONObject().put("ok", true).put("name", f.name))
                }
                "POST /api/backgrounds/delete" -> {
                    val name = req.query["name"].orEmpty()
                    fileIn(Haven.backgrounds.dir, name)?.delete()
                    // Profiles that showed just this photo go back to taking turns.
                    for ((id, s) in Haven.settings.all()) {
                        if (s.background.photo == name) Haven.settings.updateProfile(id) { it.copy(background = it.background.copy(photo = "")) }
                    }
                    json(out, 200, state(req.profile))
                }
                "GET /api/background" -> sendFile(out, fileIn(Haven.backgrounds.dir, req.query["name"].orEmpty()))
                "POST /api/app" -> {
                    applyApp(req.profile, JSONObject(readBody(req)))
                    json(out, 200, state(req.profile))
                }
                "POST /api/appimage" -> {
                    val pkg = req.query["pkg"].orEmpty()
                    if (Haven.apps.apps.value.none { it.pkg == pkg }) error("That app isn't on this TV")
                    val f = saveImage(req, Haven.apps.imagesDir, keepName = false)
                    val old = Haven.settings.settingsOf(req.profile).appLook(pkg).image
                    Haven.settings.updateProfile(req.profile) { it.withAppLook(pkg, it.appLook(pkg).copy(image = f.name, style = AppStyle.Picture)) }
                    dropUnusedAppImage(old)
                    json(out, 200, state(req.profile))
                }
                "GET /api/appimage" -> sendFile(out, fileIn(Haven.apps.imagesDir, req.query["name"].orEmpty()))
                "GET /api/appicon" -> {
                    val app = Haven.apps.apps.value.firstOrNull { it.pkg == req.query["pkg"] }
                    val img = app?.banner ?: app?.icon
                    if (img == null) {
                        send(out, 404, "text/plain", ByteArray(0))
                    } else {
                        val bytes = ByteArrayOutputStream().also { img.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                        send(out, 200, "image/png", bytes)
                    }
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

    private fun sendFile(out: OutputStream, f: File?) {
        if (f == null || !f.exists()) send(out, 404, "text/plain", ByteArray(0))
        else send(out, 200, "image/${f.extension.lowercase().replace("jpg", "jpeg")}", f.readBytes())
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

    private fun state(profile: String): JSONObject {
        val s = Haven.settings.settingsOf(profile)
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
            .put("profile", profile)
            .put("maxProfiles", MAX_PROFILES)
            .put(
                "profiles",
                JSONArray(Haven.settings.profiles.value.map { JSONObject().put("id", it.id).put("name", it.name).put("active", it.active).put("avatar", it.avatar.name) }),
            )
            .put("askOnStart", Haven.settings.meta.value.askOnStart)
            .put("options", optionsJson(s))
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
            .put("photos", JSONArray(Haven.art.photos().map { it.name }))
            .put("backgrounds", JSONArray(Haven.backgrounds.files().map { it.name }))
            .put(
                "apps",
                JSONArray(
                    Haven.apps.apps.value.map { a ->
                        val look = s.appLook(a.pkg)
                        JSONObject()
                            .put("pkg", a.pkg).put("label", a.label).put("name", look.name).put("style", look.style.name)
                            .put("color", look.color).put("image", look.image).put("banner", a.banner != null)
                            .put("hidden", a.pkg in s.hiddenApps).put("favorite", a.pkg in s.favorites)
                    },
                ),
            )
            .put("appStyles", JSONArray(AppStyle.values().map { JSONObject().put("id", it.name).put("label", it.label) }))
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

    /** Every registry option for the page, grouped like the TV's Settings, with what's chosen now. */
    private fun optionsJson(s: Settings): JSONArray = JSONArray(
        OptSection.values().map { sec ->
            JSONObject()
                .put("id", sec.name)
                .put("label", sec.label)
                .put(
                    "options",
                    JSONArray(
                        Options.of(sec).map { o ->
                            val j = JSONObject()
                                .put("id", o.id)
                                .put("title", o.title)
                                .put("help", o.help ?: "")
                                .put("visible", o.visible(s))
                                .put("kind", optKind(o))
                                .put("value", o.value(s))
                                .put("choices", JSONArray(o.choices(s).map { c -> JSONObject().put("value", c.value).put("label", c.label) }))
                            val order = o.order
                            if (order != null) {
                                val (keys, shown) = order.get(s)
                                j.put("order", JSONArray(keys)).put("shown", JSONArray(shown.toList()))
                                    .put("items", JSONArray(order.items.map { c -> JSONObject().put("value", c.value).put("label", c.label) }))
                            }
                            j
                        },
                    ),
                )
        },
    )

    private fun optKind(o: Opt): String = when {
        o.order != null -> "order"
        o.toggle -> "toggle"
        o.text -> "text"
        o.free -> "color"
        else -> "choice"
    }

    private fun applyOption(profile: String, o: JSONObject) {
        val opt = Options[o.optString("id")] ?: error("No such option")
        val order = opt.order
        Haven.settings.updateProfile(profile) { s ->
            if (order != null) {
                order.set(s, o.optJSONArray("order").strings(), o.optJSONArray("shown").strings().toSet())
            } else {
                opt.with(s, o.optString("value"))
            }
        }
    }

    /** Add, rename, recolour, remove or switch profiles. Returns the profile the page should show next. */
    private fun profileAction(current: String, o: JSONObject): String {
        val id = o.optString("id").takeIf { Haven.settings.exists(it) } ?: current
        return when (o.optString("action")) {
            "add" -> {
                if (Haven.settings.profiles.value.size >= MAX_PROFILES) error("This TV has room for $MAX_PROFILES profiles")
                val name = o.optString("name").trim().ifBlank { "Profile ${Haven.settings.profiles.value.size + 1}" }
                Haven.settings.addProfile(name, copy = o.optBoolean("copy", false))
            }
            "rename" -> {
                Haven.settings.updateProfile(id) { it.copy(profileName = o.optString("name").trim()) }
                id
            }
            "avatar" -> {
                Haven.settings.updateProfile(id) { it.copy(look = it.look.copy(avatar = enumOr(o.optString("avatar"), it.look.avatar))) }
                id
            }
            "remove" -> {
                if (!Haven.settings.removeProfile(id)) error("The last profile can't be removed")
                Haven.settings.activeId
            }
            "switch" -> {
                Haven.settings.switchTo(id)
                id
            }
            "ask" -> {
                Haven.settings.setAskOnStart(o.optBoolean("on"))
                id
            }
            else -> error("Unknown profile action")
        }
    }

    private fun applyApp(profile: String, o: JSONObject) {
        val pkg = o.optString("pkg")
        Haven.settings.updateProfile(profile) { s ->
            var look: AppLook = s.appLook(pkg)
            if (o.has("name")) look = look.copy(name = o.optString("name").trim().takeIf { n -> Haven.apps.apps.value.none { it.pkg == pkg && it.label == n } }.orEmpty())
            if (o.has("style")) look = look.copy(style = enumOr(o.optString("style"), look.style))
            if (o.has("color")) look = look.copy(color = o.optLong("color", 0L))
            if (o.optBoolean("reset")) look = AppLook()
            var next = s.withAppLook(pkg, look)
            if (o.has("hidden")) next = next.copy(hiddenApps = if (o.optBoolean("hidden")) next.hiddenApps + pkg else next.hiddenApps - pkg)
            if (o.has("favorite")) next = next.copy(favorites = if (o.optBoolean("favorite")) (next.favorites - pkg) + pkg else next.favorites - pkg)
            next
        }
    }

    /** Deletes an app picture once no profile uses it any more. */
    private fun dropUnusedAppImage(name: String) {
        if (name.isBlank()) return
        if (Haven.settings.all().none { (_, s) -> s.appLooks.values.any { it.image == name } }) fileIn(Haven.apps.imagesDir, name)?.delete()
    }

    private fun applySettings(profile: String, o: JSONObject) {
        val before = Haven.settings.settingsOf(profile)
        Haven.settings.updateProfile(profile) { cur ->
            var s: Settings = cur
            if (o.has("roomName")) s = s.copy(roomName = o.getString("roomName").trim().ifBlank { cur.roomName })
            if (o.has("haArea")) s = s.copy(haArea = o.getString("haArea"))
            if (o.has("bedroomExtras")) s = s.copy(bedroomExtras = o.getBoolean("bedroomExtras"))
            if (o.has("profileName")) s = s.copy(profileName = o.getString("profileName").trim())
            if (o.has("haUrl")) s = s.copy(haUrl = o.getString("haUrl").trim())
            if (o.optString("haToken").isNotBlank()) s = s.copy(haToken = o.getString("haToken").trim())
            if (o.has("weatherEntity")) s = s.copy(weatherEntity = o.getString("weatherEntity"))
            if (o.has("hiddenEntities")) s = s.copy(hiddenEntities = o.optJSONArray("hiddenEntities").strings().toSet())
            if (o.has("alarm")) {
                // The page sends the time, days and switches; everything else about the alarm stays as chosen.
                val a = alarmFromJson(o.getJSONObject("alarm"))
                s = s.copy(alarm = s.alarm.copy(enabled = a.enabled, hour = a.hour, minute = a.minute, days = a.days))
            }
            if (o.has("presenceEntity")) s = s.copy(artCare = s.artCare.copy(presenceEntity = o.getString("presenceEntity")))
            if (o.optBoolean("resetLook")) s = s.copy(look = LookConfig(avatar = s.look.avatar))
            if (o.has("brief")) {
                val b = BriefConfig.fromJson(o.getJSONObject("brief"))
                s = s.copy(
                    brief = s.brief.copy(
                        calendars = b.calendars, commuteEntity = b.commuteEntity, commuteLabel = b.commuteLabel,
                        statusEntities = b.statusEntities, headlinesFeed = b.headlinesFeed,
                    ),
                )
            }
            if (o.has("teams")) {
                val t = SportsConfig.fromJson(JSONObject().put("teams", o.getJSONArray("teams"))).teams
                s = s.copy(sports = s.sports.copy(teams = t))
            }
            s
        }
        val after = Haven.settings.settingsOf(profile)
        // Return Home Assistant's real status with the answer, not a stale one.
        if (profile == Haven.settings.activeId && (after.haUrl != before.haUrl || after.haToken != before.haToken || after.haArea != before.haArea)) {
            runBlocking { Haven.ha.refresh() }
        }
    }

    private fun entityList(pick: (HaEntity) -> Boolean): JSONArray = JSONArray(
        Haven.ha.entities.value.values.filter(pick).sortedBy { it.name.lowercase() }
            .map { JSONObject().put("id", it.id).put("name", it.name) },
    )

    /** Saves an uploaded image into [dir]: under its own name (photos) or a fresh one (app pictures). */
    private fun saveImage(req: Req, dir: File, keepName: Boolean): File {
        if (req.length <= 0 || req.length > 40_000_000) error("Pictures must be under 40 MB")
        val raw = req.query["name"].orEmpty().substringAfterLast('/')
        var ext = raw.substringAfterLast('.', "jpg").lowercase()
        if (ext !in ArtRepository.IMAGE_EXT) ext = "jpg"
        val base = if (keepName) {
            raw.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9 _.-]"), "").trim().take(60).ifBlank { "Photo" }
        } else {
            "app-" + System.currentTimeMillis()
        }
        var f = File(dir, "$base.$ext")
        var n = 2
        while (f.exists()) f = File(dir, "$base ${n++}.$ext")
        f.outputStream().use { req.body.copyTo(it) }
        if (f.length() != req.length) {
            f.delete()
            error("Upload was cut off")
        }
        req.query["taken"]?.toLongOrNull()?.let { f.setLastModified(it) }
        return f
    }

    private fun fileIn(dir: File, name: String): File? {
        val clean = name.substringAfterLast('/')
        if (clean.isBlank() || clean.startsWith(".")) return null
        return File(dir, clean)
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
        private val FONTS = mapOf(
            "fraunces.ttf" to R.font.fraunces_title,
            "atkinson-500.ttf" to R.font.atkinson_medium,
            "atkinson-600.ttf" to R.font.atkinson_semibold,
            "atkinson-700.ttf" to R.font.atkinson_bold,
        )
        private val PORTS = listOf(8765, 8766, 8767, 0)
        private val PRESENCE_CLASSES = setOf("motion", "occupancy", "presence")
        private val DOOR_CLASSES = setOf("door", "garage_door", "window", "opening", "lock")
    }
}
