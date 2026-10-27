package app.haven.launcher.ui

import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.haven.launcher.BuildConfig
import app.haven.launcher.Haven
import app.haven.launcher.Nav
import app.haven.launcher.Screen
import app.haven.launcher.bedroom.Bedroom
import app.haven.launcher.data.Accent
import app.haven.launcher.data.BgKind
import app.haven.launcher.data.LookConfig
import app.haven.launcher.data.Opt
import app.haven.launcher.data.OptSection
import app.haven.launcher.data.ProfileInfo
import app.haven.launcher.data.ProfilesMeta
import app.haven.launcher.data.Settings
import app.haven.launcher.ha.HaArea
import app.haven.launcher.ha.HaStatus
import app.haven.launcher.setup.SetupServer
import app.haven.launcher.sports.ScorePopup
import app.haven.launcher.system.HomeButtonService
import app.haven.launcher.update.Updater
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Settings are grouped like Android TV's own: pick a group on the left, its settings appear next to it. */
private enum class Group(val label: String) {
    Profiles("Profiles"),
    Look("Look"),
    Home("Home screen"),
    Background("Background"),
    Room("This room"),
    HomeAssistant("Home Assistant"),
    Art("Art mode"),
    Alarm("Alarm"),
    Briefing("Morning briefing"),
    Sports("Sports"),
    HomeButton("Home button"),
    About("About"),
}

@Composable
fun SettingsScreen(nav: Nav) {
    val ctx = LocalContext.current
    val s by Haven.settings.state.collectAsState()
    val meta by Haven.settings.meta.collectAsState()
    val profiles by Haven.settings.profiles.collectAsState()
    val status by Haven.ha.status.collectAsState()
    val areas by Haven.ha.areas.collectAsState()
    val serviceOn by HomeButtonService.running.collectAsState()
    val updateState by Haven.updater.state.collectAsState()
    val bgStatus by Haven.backgrounds.status.collectAsState()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<TextEdit?>(null) }
    var showScreens by remember { mutableStateOf(false) }
    var openOption by remember { mutableStateOf<Opt?>(null) }
    var dialog by remember { mutableStateOf<String?>(null) }
    val start = remember { Group.values().firstOrNull { it.name == nav.settingsStart } ?: Group.Profiles }
    var group by remember { mutableStateOf(start) }
    var phoneHint by remember { mutableStateOf<String?>(null) }
    val rail = remember { Group.values().associateWith { FocusRequester() } }
    val firstRow = remember { FocusRequester() }
    val groups = Group.values().filter { it != Group.Alarm || s.bedroomExtras }

    // The phone setup page is served only while this screen is open, and only with the code shown here.
    val server = remember { SetupServer(ctx.applicationContext) }
    DisposableEffect(Unit) {
        server.start()
        onDispose { server.stop() }
    }
    LaunchedEffect(Unit) {
        nav.settingsStart = null
        Haven.ha.refresh()
    }
    LaunchedEffect(Unit) {
        delay(60)
        runCatching { rail.getValue(group).requestFocus() }
    }
    LaunchedEffect(phoneHint) {
        if (phoneHint != null) {
            delay(8_000)
            phoneHint = null
        }
    }

    val ui = SettingsUi(
        s = s,
        meta = meta,
        profiles = profiles,
        status = status,
        areas = areas,
        serviceOn = serviceOn,
        updateState = updateState,
        bgStatus = bgStatus,
        scope = scope,
        nav = nav,
        edit = { editing = it },
        onPhone = { phoneHint = it },
        openOpt = { openOption = it },
        openDialog = { dialog = it },
        showScreens = { showScreens = true },
        backToRail = rail.getValue(group),
        firstRow = firstRow,
    )

    Column(Modifier.fillMaxSize().padding(start = 116.dp, end = 116.dp, top = 48.dp), verticalArrangement = Arrangement.spacedBy(40.dp)) {
        ScreenHeader("Settings", profiles.firstOrNull { it.active }?.name, onBack = { nav.go(Screen.Home) })
        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (g in groups) {
                    RailItem(
                        g.label,
                        selected = g == group,
                        focusRequester = rail.getValue(g),
                        onFocus = {
                            if (group != g) phoneHint = null
                            group = g
                        },
                        onClick = { runCatching { firstRow.requestFocus() } },
                    )
                }
            }
            Spacer(width = 48.dp)
            Box(Modifier.width(840.dp).fillMaxHeight()) {
                key(group) {
                    LazyColumn(
                        Modifier.fillMaxSize().bleed(16.dp).fadeBottom(64.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    ) {
                        with(ui) {
                            when (group) {
                                Group.Profiles -> profileRows()
                                Group.Look -> lookRows()
                                Group.Home -> homeRows()
                                Group.Background -> backgroundRows()
                                Group.Room -> roomRows()
                                Group.HomeAssistant -> homeAssistantRows()
                                Group.Art -> artRows()
                                Group.Alarm -> alarmRows()
                                Group.Briefing -> briefingRows()
                                Group.Sports -> sportsRows()
                                Group.HomeButton -> homeButtonRows()
                                Group.About -> aboutRows()
                            }
                        }
                    }
                }
            }
            Spacer(width = 48.dp)
            PhoneSetupPanel(server, phoneHint, Modifier.weight(1f))
        }
    }

    editing?.let { e -> TextEditDialog(e) { editing = null } }
    openOption?.let { o -> OptionDialog(o) { openOption = null } }
    if (showScreens) RecentScreensDialog { showScreens = false }
    when (dialog) {
        "add" -> AddProfileFlow { id ->
            dialog = null
            if (id != null) Haven.settings.switchTo(id)
        }
        "avatar" -> ChoiceDialog(
            "Profile colour",
            Accent.values().filter { it != Accent.Mono }.map { Choice(it.name, it.label, swatch = ACCENTS.getValue(it).bright) },
            s.look.avatar.name,
            onPick = { v ->
                dialog = null
                Haven.settings.update { it.copy(look = it.look.copy(avatar = Accent.values().firstOrNull { a -> a.name == v } ?: it.look.avatar)) }
            },
            onDismiss = { dialog = null },
        )
        "resetLook" -> ConfirmDialog(
            "Back to Haven's own look?",
            "Colours, fonts, sizes, focus and the home screen's rows go back to how Haven came. Apps, background, alarm and everything else stay as they are.",
            "Reset the look",
            onYes = { Haven.settings.update { it.copy(look = LookConfig(avatar = it.look.avatar)) } },
            onDismiss = { dialog = null },
        )
        "remove" -> ConfirmDialog(
            "Remove ${profiles.firstOrNull { it.active }?.name}?",
            "Their look, apps, alarm, briefing and teams go with them. Photos and pictures uploaded for everyone stay.",
            "Remove",
            onYes = { Haven.settings.removeProfile(Haven.settings.activeId) },
            onDismiss = { dialog = null },
        )
    }
}

/** What every group's rows need. Rows that can only be changed on the phone point at the QR code instead. */
private class SettingsUi(
    val s: Settings,
    val meta: ProfilesMeta,
    val profiles: List<ProfileInfo>,
    val status: HaStatus,
    val areas: List<HaArea>,
    val serviceOn: Boolean,
    val updateState: Updater.State,
    val bgStatus: String?,
    val scope: CoroutineScope,
    val nav: Nav,
    val edit: (TextEdit) -> Unit,
    val onPhone: (String) -> Unit,
    val openOpt: (Opt) -> Unit,
    val openDialog: (String) -> Unit,
    val showScreens: () -> Unit,
    val backToRail: FocusRequester,
    val firstRow: FocusRequester,
) {
    /** Left from any row goes back to this group in the rail, not to whichever group happens to be level with it. */
    val rowModifier: Modifier get() = Modifier.focusProperties { left = backToRail }

    fun LazyListScope.row(
        title: String,
        value: String,
        first: Boolean = false,
        toggle: Boolean? = null,
        toggleEnabled: Boolean = true,
        phoneOnly: Boolean = false,
        onClick: () -> Unit = { onPhone(title) },
    ) {
        item(key = title) {
            SettingRow(
                title, value, rowModifier, toggle = toggle, toggleEnabled = toggleEnabled, phoneOnly = phoneOnly,
                focusRequester = if (first) firstRow else null, onClick = onClick,
            )
        }
    }

    fun LazyListScope.options(section: OptSection, first: Boolean = false) =
        optionRows(section, s, rowModifier, if (first) firstRow else null, onPhone, openOpt)

    fun LazyListScope.heading(text: String) {
        item(key = "h:$text") { SectionTitle(text, Modifier.padding(top = 24.dp, bottom = 4.dp)) }
    }

    fun LazyListScope.profileRows() {
        val me = profiles.firstOrNull { it.active }
        row("Name", s.profileName.ifBlank { "Not set. Used in greetings and the profile picker" }, first = true) {
            edit(TextEdit("Profile name", s.profileName, "Your name") { v -> Haven.settings.update { it.copy(profileName = v.trim()) } })
        }
        row("Colour", s.look.avatar.label) { openDialog("avatar") }
        row("Switch profile", "Opens “Who's watching?”") { nav.go(Screen.Profiles) }
        if (profiles.size < MAX_PROFILES) row("Add a profile", "Everyone can have their own look, apps, alarm and teams") { openDialog("add") }
        row(
            "Ask who's watching",
            if (profiles.size < 2) "Once there's more than one profile, ask each time the TV turns on" else "Each time the TV turns on",
            toggle = meta.askOnStart,
        ) { Haven.settings.setAskOnStart(!meta.askOnStart) }
        if (profiles.size > 1 && me != null) {
            heading("Everyone")
            for (p in profiles) {
                item(key = "p:${p.id}") {
                    SettingRow(p.name, if (p.active) "Showing now" else "Select to switch to ${p.name}", rowModifier) {
                        Haven.settings.switchTo(p.id)
                    }
                }
            }
            row("Remove ${me.name}", "Removes this profile from the TV") { openDialog("remove") }
        }
    }

    fun LazyListScope.lookRows() {
        options(OptSection.Look, first = true)
        if (s.look != LookConfig(avatar = s.look.avatar)) {
            row("Back to Haven's own look", "Colours, fonts, sizes, focus and home rows as they came") { openDialog("resetLook") }
        }
    }

    fun LazyListScope.homeRows() {
        options(OptSection.Home, first = true)
        row("Hidden apps", if (s.hiddenApps.isEmpty()) "None" else "${s.hiddenApps.size} hidden. Select to show them again") {
            Haven.settings.update { it.copy(hiddenApps = emptySet()) }
        }
        row("App names and pictures", "Hold OK on an app on the home screen. Upload pictures for apps from your phone", phoneOnly = true)
    }

    fun LazyListScope.backgroundRows() {
        options(OptSection.Background, first = true)
        val kind = s.background.kind
        if (kind == BgKind.Photo) {
            row("Your background photos", "${Haven.backgrounds.files().size} on this TV. Add more from your phone", phoneOnly = true)
        }
        if (kind == BgKind.Daily || kind == BgKind.Posters) {
            val note = bgStatus ?: if (kind == BgKind.Daily) "Bing's photo of the day" else "Trending this week on TMDB"
            row("Now showing", note) { }
        }
        if (kind == BgKind.Posters) {
            row("About posters", "This product uses the TMDB API but is not endorsed or certified by TMDB.") { }
        }
    }

    fun LazyListScope.roomRows() {
        row("Room name", s.roomName, first = true) {
            edit(TextEdit("Room name", s.roomName, "Living Room") { v -> Haven.settings.update { it.copy(roomName = v.ifBlank { "Living Room" }) } })
        }
        val current = areas.firstOrNull { it.id == s.haArea }
        row(
            "Home Assistant area",
            when {
                current != null -> current.name
                areas.isEmpty() && s.haConfigured -> "No areas found, so Haven shows everything"
                areas.isEmpty() -> "Connect Home Assistant first"
                else -> "All areas. Select to pick this room's"
            },
        ) {
            if (areas.isNotEmpty()) {
                val ids = listOf("") + areas.map { it.id }
                val next = ids[(ids.indexOf(s.haArea) + 1) % ids.size]
                val nextName = areas.firstOrNull { it.id == next }?.name
                Haven.settings.update { cur ->
                    val renamed = current == null && cur.roomName == "Living Room" || cur.roomName == current?.name
                    cur.copy(haArea = next, roomName = if (renamed && nextName != null) nextName else cur.roomName)
                }
            }
        }
        row("Bedroom features", "Alarm clock and sleep timer on the home screen", toggle = s.bedroomExtras) {
            Haven.settings.update { it.copy(bedroomExtras = !it.bedroomExtras) }
        }
    }

    fun LazyListScope.homeAssistantRows() {
        row(
            "Connection",
            when (val st = status) {
                HaStatus.NotConfigured -> "Not set up. Scan the code with your phone"
                HaStatus.Connecting -> "Connecting…"
                is HaStatus.Connected -> "Connected · ${st.entityCount} entities"
                is HaStatus.Failed -> st.message
            },
            first = true,
        ) { scope.launch { Haven.ha.refresh() } }
        row("Server address", s.haUrl.ifBlank { "Not set" }) {
            edit(TextEdit("Home Assistant address", s.haUrl, "http://192.168.1.10:8123") { v -> Haven.settings.update { it.copy(haUrl = v.trim()) } })
        }
        row("Access token", if (s.haToken.isBlank()) "Not set. Easier to paste from your phone" else "Saved") {
            edit(
                TextEdit("Long-lived access token", "", "Paste or type the token") { v ->
                    if (v.isNotBlank()) Haven.settings.update { it.copy(haToken = v.trim()) }
                },
            )
        }
    }

    fun LazyListScope.artRows() {
        options(OptSection.Art, first = true)
        row("Your photos", "${Haven.art.photos().size} on this TV. Add more from your phone", phoneOnly = true)
        val c = s.artCare
        row(
            "Only when someone is in the room",
            if (c.presenceEntity.isBlank()) "Off. Pick a motion sensor on your phone"
            else Haven.ha.entities.value[c.presenceEntity]?.name ?: c.presenceEntity,
            phoneOnly = true,
        )
        row("Use art mode as the screensaver", "Choose \"Haven art\" in Android's screen saver settings") {
            openSystem(Haven.app, AndroidSettings.ACTION_DREAM_SETTINGS)
        }
    }

    fun LazyListScope.alarmRows() {
        val a = s.alarm
        row(
            "Alarm time",
            if (a.enabled) "${Bedroom.timeLabel(a.hour, a.minute, s.use24h)} · ${Bedroom.daysLabel(a.days)}" else "Off. Select to set it",
            first = true,
        ) { nav.go(Screen.Alarm) }
        options(OptSection.Alarm)
        heading(OptSection.AlarmScreen.label)
        options(OptSection.AlarmScreen)
    }

    fun LazyListScope.briefingRows() {
        val b = s.brief
        row("Show the briefing now", "Weather, your day and the news, the way you set it up below", first = true) { nav.go(Screen.Briefing) }
        options(OptSection.Briefing)
        row(
            "Calendars, commute and home status",
            listOf(
                if (b.calendars.isEmpty()) "all calendars" else "${b.calendars.size} calendars",
                if (b.commuteEntity.isBlank()) null else "commute to ${b.commuteLabel}",
                if (b.statusEntities.isEmpty()) "locks and garage" else "${b.statusEntities.size} home items",
                if (b.headlinesFeed.isBlank()) null else "headlines feed",
            ).filterNotNull().joinToString(", ").replaceFirstChar { it.uppercase() },
            phoneOnly = true,
        )
    }

    fun LazyListScope.sportsRows() {
        row(
            "Your teams",
            if (s.sports.teams.isEmpty()) "None yet. Choose them on your phone" else s.sports.teams.joinToString(", ") { it.name },
            first = true,
            phoneOnly = s.sports.teams.isEmpty(),
        ) { if (s.sports.teams.isEmpty()) onPhone("Your teams") else nav.go(Screen.Sports) }
        options(OptSection.Sports)
        heading(OptSection.Alerts.label)
        options(OptSection.Alerts)
        val ok = ScorePopup.canOverlay(Haven.app)
        row(
            "Show alerts on top of other apps",
            if (ok) "Allowed" else "Not allowed, so alerts show as small messages. Select to allow",
            toggle = ok,
        ) { ScorePopup.requestPermission(Haven.app) }
        row("Test an alert", "Shows a sample score alert the way you set it up") {
            ScorePopup.show(Haven.app, "AWY 17 – 21 HOM", "Q3 4:12 · Sample alert from Haven")
        }
    }

    fun LazyListScope.homeButtonRows() {
        row(
            "Haven Home button service",
            if (serviceOn) "On. Home opens Haven" else "Off. Select, then turn on \"Haven Home button\"",
            first = true,
            toggle = serviceOn,
        ) { openSystem(Haven.app, AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS) }
        row("Open Haven when Home is pressed", "Press Home twice quickly for the original home screen", toggle = s.homeOverride) {
            Haven.settings.update { it.copy(homeOverride = !it.homeOverride) }
        }
        if (!serviceOn) {
            row("Switch keeps turning itself off?", "Select to reinstall Haven through Android's installer, then try the switch again") {
                scope.launch { Haven.updater.checkAndInstall(reinstall = true) }
            }
        }
        row("Open the original home screen", "On the DIRECTV box this is the DIRECTV app") { HomeButtonService.openStockHome(Haven.app) }
        row("Recent screens", "For troubleshooting: which apps and screens opened lately") { showScreens() }
    }

    fun LazyListScope.aboutRows() {
        row(
            "Updates",
            when (val u = updateState) {
                Updater.State.Idle -> "Version ${BuildConfig.VERSION_NAME}. Select to check"
                Updater.State.Checking -> "Checking…"
                Updater.State.UpToDate -> "Up to date (${BuildConfig.VERSION_NAME})"
                is Updater.State.Downloading -> "Downloading ${u.version}… ${u.percent}%"
                is Updater.State.Installing -> "Opening the installer for ${u.version}"
                is Updater.State.Failed -> u.message
            },
            first = true,
        ) { scope.launch { Haven.updater.checkAndInstall() } }
        row("Android settings", "Network, display, sound and accessibility") { openSystem(Haven.app, AndroidSettings.ACTION_SETTINGS) }
        row(
            "Credits",
            "Paintings: Art Institute of Chicago, public domain. Photo of the day: Bing. Posters: TMDB, which doesn't " +
                "endorse or certify Haven. Scores: ESPN. Fonts: SIL Open Font License. Icons: Lucide.",
        ) { }
    }
}

@Composable
private fun RailItem(label: String, selected: Boolean, focusRequester: FocusRequester, onFocus: () -> Unit, onClick: () -> Unit) {
    Focusable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(64.dp),
        color = Color.Transparent,
        focusedScale = 1.03f,
        focusRequester = focusRequester,
        onFocus = { if (it) onFocus() },
    ) { focused ->
        val l = lit(focused)
        Row(
            Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier.size(6.dp, 32.dp).clip(RoundedCornerShape(3.dp))
                    .background(if (selected) (if (l) C.Ember else C.Lamp) else Color.Transparent),
            )
            Txt(label, T.Label, size = 26, color = if (l) C.Ink else if (selected || focused) C.Linen else C.Stone)
        }
    }
}

/** Lists the package / activity of recent screens, as seen by the Home button service. */
@Composable
private fun RecentScreensDialog(onClose: () -> Unit) {
    val screens by HomeButtonService.recentScreens.collectAsState()
    val close = remember { FocusRequester() }
    HavenDialog(onClose) {
        Panel(Modifier.width(1300.dp)) {
            Txt("Recent screens", T.Title)
            Txt(
                if (screens.isEmpty()) "Nothing yet. Turn on the Home button service, open a few apps, then come back."
                else "Newest first. Home apps on this box: " + HomeButtonService.stockHomePackages(Haven.app).joinToString(", ").ifBlank { "none" },
                T.Body, color = C.Stone, maxLines = 3,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (line in screens) Txt(line, T.Label)
            }
            Chip("Close", onClose, focusRequester = close)
        }
    }
    FocusOnOpen(close)
}

@Composable
private fun PhoneSetupPanel(server: SetupServer, hint: String?, modifier: Modifier) {
    val url by server.url.collectAsState()
    Panel(modifier, padding = 36.dp) {
        Txt("Set up on your phone", T.Title, size = 32, maxLines = 2)
        Txt(
            "Home Assistant, teams, photos, backgrounds, app pictures and every option. Use the same Wi-Fi as the TV.",
            T.Body, color = C.Stone, maxLines = 5,
        )
        val u = url
        if (u == null) {
            Txt("This TV isn't on a network yet.", T.Body)
        } else {
            Box(Modifier.size(264.dp).clip(L.shape).background(Color.White).padding(16.dp)) {
                QrCode(u, Modifier.fillMaxSize())
            }
            Column {
                Txt("Or open", T.Label, color = C.Dust)
                Txt(u.removePrefix("http://"), T.Numeric, maxLines = 2)
            }
        }
        if (hint != null) {
            Row(
                Modifier.fillMaxWidth().clip(L.shape).background(C.LampSoft).padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Smartphone, 28.dp, C.Lamp)
                Txt("Change “$hint” on your phone: scan the code above.", T.Label, color = C.Lamp, maxLines = 3)
            }
        }
    }
}

@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) {
        runCatching { QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0)) }.getOrNull()
    }
    Canvas(modifier) {
        val m = matrix ?: return@Canvas
        val cell = size.minDimension / m.width
        for (y in 0 until m.height) {
            for (x in 0 until m.width) {
                if (m.get(x, y)) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.6f, cell + 0.6f))
            }
        }
    }
}
