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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.haven.launcher.BuildConfig
import app.haven.launcher.Haven
import app.haven.launcher.Nav
import app.haven.launcher.Screen
import app.haven.launcher.data.ArtSource
import app.haven.launcher.data.ArtStyle
import app.haven.launcher.data.QuietMode
import app.haven.launcher.data.Settings
import app.haven.launcher.data.TvPanel
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

private class TextEdit(val title: String, val initial: String, val hint: String, val onSave: (String) -> Unit)

/** Settings are grouped like Android TV's own: pick a group on the left, its settings appear next to it. */
private enum class Group(val label: String) {
    Room("This room"),
    HomeAssistant("Home Assistant"),
    Art("Art mode"),
    Briefing("Morning briefing"),
    Sports("Sports"),
    HomeButton("Home button"),
    Apps("Apps and clock"),
    About("About"),
}

@Composable
fun SettingsScreen(nav: Nav) {
    val ctx = LocalContext.current
    val s by Haven.settings.state.collectAsState()
    val status by Haven.ha.status.collectAsState()
    val areas by Haven.ha.areas.collectAsState()
    val serviceOn by HomeButtonService.running.collectAsState()
    val updateState by Haven.updater.state.collectAsState()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<TextEdit?>(null) }
    var showScreens by remember { mutableStateOf(false) }
    var group by remember { mutableStateOf(Group.Room) }
    var phoneHint by remember { mutableStateOf<String?>(null) }
    val rail = remember { Group.values().associateWith { FocusRequester() } }
    val firstRow = remember { FocusRequester() }

    // The phone setup page is served only while this screen is open, and only with the code shown here.
    val server = remember { SetupServer(ctx.applicationContext) }
    DisposableEffect(Unit) {
        server.start()
        onDispose { server.stop() }
    }
    LaunchedEffect(Unit) {
        Haven.ha.refresh()
    }
    LaunchedEffect(Unit) {
        delay(60)
        runCatching { rail.getValue(Group.Room).requestFocus() }
    }
    LaunchedEffect(phoneHint) {
        if (phoneHint != null) {
            delay(8_000)
            phoneHint = null
        }
    }

    val ui = SettingsUi(
        s = s,
        status = status,
        areas = areas,
        serviceOn = serviceOn,
        updateState = updateState,
        scope = scope,
        nav = nav,
        edit = { editing = it },
        onPhone = { phoneHint = it },
        showScreens = { showScreens = true },
        backToRail = rail.getValue(group),
        firstRow = firstRow,
    )

    Column(Modifier.fillMaxSize().padding(start = 116.dp, end = 116.dp, top = 48.dp), verticalArrangement = Arrangement.spacedBy(40.dp)) {
        ScreenHeader("Settings", "Haven ${BuildConfig.VERSION_NAME}", onBack = { nav.go(Screen.Home) })
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(320.dp).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (g in Group.values()) {
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
                        Modifier.fillMaxSize().bleed(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    ) {
                        with(ui) {
                            when (group) {
                                Group.Room -> roomRows()
                                Group.HomeAssistant -> homeAssistantRows()
                                Group.Art -> artRows()
                                Group.Briefing -> briefingRows()
                                Group.Sports -> sportsRows()
                                Group.HomeButton -> homeButtonRows()
                                Group.Apps -> appsRows()
                                Group.About -> aboutRows()
                            }
                        }
                    }
                }
                BottomFade(64.dp)
            }
            Spacer(width = 48.dp)
            PhoneSetupPanel(server, phoneHint, Modifier.weight(1f))
        }
    }

    editing?.let { e -> TextEditDialog(e) { editing = null } }
    if (showScreens) RecentScreensDialog { showScreens = false }
}

/** What every group's rows need. Rows that can only be changed on the phone point at the QR code instead. */
private class SettingsUi(
    val s: Settings,
    val status: HaStatus,
    val areas: List<HaArea>,
    val serviceOn: Boolean,
    val updateState: Updater.State,
    val scope: CoroutineScope,
    val nav: Nav,
    val edit: (TextEdit) -> Unit,
    val onPhone: (String) -> Unit,
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
        row("Your name", s.profileName.ifBlank { "Not set. Used for the morning greeting" }) {
            edit(TextEdit("Your name", s.profileName, "Matt") { v -> Haven.settings.update { it.copy(profileName = v.trim()) } })
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
            edit(
                TextEdit("Home Assistant address", s.haUrl, "http://192.168.1.10:8123") { v ->
                    Haven.settings.update { it.copy(haUrl = v.trim()) }
                    scope.launch { Haven.ha.refresh() }
                },
            )
        }
        row("Access token", if (s.haToken.isBlank()) "Not set. Easier to paste from your phone" else "Saved") {
            edit(
                TextEdit("Long-lived access token", "", "Paste or type the token") { v ->
                    if (v.isNotBlank()) Haven.settings.update { it.copy(haToken = v.trim()) }
                    scope.launch { Haven.ha.refresh() }
                },
            )
        }
    }

    fun LazyListScope.artRows() {
        val c = s.artCare
        row("Show", s.artSource.label, first = true) {
            val all = ArtSource.values()
            Haven.settings.update { it.copy(artSource = all[(it.artSource.ordinal + 1) % all.size]) }
        }
        row("Style", c.style.label) {
            val all = ArtStyle.values()
            Haven.settings.update { it.copy(artCare = it.artCare.copy(style = all[(it.artCare.style.ordinal + 1) % all.size])) }
        }
        row("Change the picture every", "${s.artIntervalMin} min") {
            Haven.settings.update { it.copy(artIntervalMin = cycle(listOf(1, 5, 15, 30, 60), it.artIntervalMin)) }
        }
        row("Start art mode", if (s.artIdleMin == 0) "Only when I choose it" else "After ${s.artIdleMin} min on the home screen") {
            Haven.settings.update { it.copy(artIdleMin = cycle(listOf(0, 5, 10, 15, 30), it.artIdleMin)) }
        }
        row("Your photos", "${Haven.art.photos().size} on this TV. Add more from your phone", phoneOnly = true)
        row("Use art mode as the screensaver", "Choose \"Haven art\" in Android's screen saver settings") {
            openSystem(Haven.app, AndroidSettings.ACTION_DREAM_SETTINGS)
        }
        row("TV type", "${c.panel.label} · " + if (c.strongProtection) "strong burn-in protection" else "standard burn-in protection") {
            val next = if (c.panel == TvPanel.Oled) TvPanel.Lcd else TvPanel.Oled
            Haven.settings.update { it.copy(artCare = it.artCare.copy(panel = next)) }
        }
        row("Dim the art", if (c.dimPct == 0) "Off" else "${c.dimPct}% darker") {
            Haven.settings.update { it.copy(artCare = it.artCare.copy(dimPct = cycle(listOf(0, 15, 30, 45), it.artCare.dimPct))) }
        }
        row(
            "Quiet hours",
            if (!c.quietEnabled) "Off" else "${hourLabel(c.quietStart, s.use24h)} to ${hourLabel(c.quietEnd, s.use24h)} · ${c.quietMode.label.lowercase()}",
        ) {
            val presets = listOf(23 to 23, 23 to 6, 22 to 6, 0 to 7, 21 to 7)
            val i = presets.indexOf(c.quietStart to c.quietEnd)
            val (a, b) = presets[(i + 1) % presets.size]
            Haven.settings.update { it.copy(artCare = it.artCare.copy(quietStart = a, quietEnd = b)) }
        }
        row("During quiet hours", c.quietMode.label) {
            val next = if (c.quietMode == QuietMode.Black) QuietMode.Dim else QuietMode.Black
            Haven.settings.update { it.copy(artCare = it.artCare.copy(quietMode = next)) }
        }
        row(
            "Only when someone is in the room",
            if (c.presenceEntity.isBlank()) "Off. Pick a motion sensor on your phone"
            else "${Haven.ha.entities.value[c.presenceEntity]?.name ?: c.presenceEntity} · black after ${c.presenceDelayMin} min",
            phoneOnly = true,
        )
        row("Time limit", if (c.maxHours == 0) "No limit" else "Black after ${c.maxHours} hours") {
            Haven.settings.update { it.copy(artCare = it.artCare.copy(maxHours = cycle(listOf(0, 2, 4, 8), it.artCare.maxHours))) }
        }
    }

    fun LazyListScope.briefingRows() {
        val b = s.brief
        row("Show the briefing now", "It also opens when you stop the alarm", first = true) { nav.go(Screen.Briefing) }
        row(
            "What it shows",
            listOf(
                "weather",
                if (b.calendars.isEmpty()) "all calendars" else "${b.calendars.size} calendars",
                if (b.commuteEntity.isBlank()) null else "commute",
                if (b.statusEntities.isEmpty()) "locks and garage" else "${b.statusEntities.size} home items",
                if (b.headlinesFeed.isBlank()) null else "headlines",
                "your teams",
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
        row("Score alerts", "Game start, scores and finals for your teams, over any app", toggle = s.sports.alerts) {
            Haven.settings.update { it.copy(sports = it.sports.copy(alerts = !it.sports.alerts)) }
        }
        val ok = ScorePopup.canOverlay(Haven.app)
        row(
            "Show alerts on top of other apps",
            if (ok) "Allowed" else "Not allowed, so alerts show as small messages. Select to allow",
            toggle = ok,
        ) { ScorePopup.requestPermission(Haven.app) }
        row("Test an alert", "Shows a sample score alert") {
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

    fun LazyListScope.appsRows() {
        row("Hidden apps", if (s.hiddenApps.isEmpty()) "None" else "${s.hiddenApps.size} hidden. Select to show them again", first = true) {
            Haven.settings.update { it.copy(hiddenApps = emptySet()) }
        }
        row("Clock", if (s.use24h) "24-hour" else "12-hour") {
            Haven.settings.update { it.copy(use24h = !it.use24h) }
        }
        row("Android settings", "Network, display, sound and accessibility") { openSystem(Haven.app, AndroidSettings.ACTION_SETTINGS) }
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
    }
}

@Composable
private fun RailItem(label: String, selected: Boolean, focusRequester: FocusRequester, onFocus: () -> Unit, onClick: () -> Unit) {
    Focusable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(72.dp),
        color = C.Hearth,
        focusedScale = 1.03f,
        focusRequester = focusRequester,
        onFocus = { if (it) onFocus() },
    ) { focused ->
        Row(
            Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier.size(6.dp, 32.dp).clip(RoundedCornerShape(3.dp))
                    .background(if (selected) (if (focused) C.Ember else C.Lamp) else Color.Transparent),
            )
            Txt(label, T.Label, size = 26, color = if (focused) C.Ink else if (selected) C.Linen else C.Stone)
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
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { close.requestFocus() }
    }
}

@Composable
private fun PhoneSetupPanel(server: SetupServer, hint: String?, modifier: Modifier) {
    val url by server.url.collectAsState()
    Panel(modifier, padding = 36.dp) {
        Txt("Set up on your phone", T.Title, size = 32, maxLines = 2)
        Txt(
            "Home Assistant, lights, teams, photos and the alarm. Use the same Wi-Fi as the TV.",
            T.Body, color = C.Stone, maxLines = 4,
        )
        val u = url
        if (u == null) {
            Txt("This TV isn't on a network yet.", T.Body)
        } else {
            Box(Modifier.size(264.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(16.dp)) {
                QrCode(u, Modifier.fillMaxSize())
            }
            Column {
                Txt("Or open", T.Label, color = C.Dust)
                Txt(u.removePrefix("http://"), T.Numeric, maxLines = 2)
            }
        }
        if (hint != null) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.LampSoft).padding(20.dp),
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

@Composable
private fun TextEditDialog(e: TextEdit, onClose: () -> Unit) {
    var value by remember { mutableStateOf(e.initial) }
    val field = remember { FocusRequester() }
    // At the top of the screen, so the on-screen keyboard doesn't cover the buttons.
    HavenDialog(onClose, alignment = Alignment.TopCenter) {
        Panel(Modifier.padding(top = 96.dp).width(1100.dp)) {
            Txt(e.title, T.Title)
            Box(
                Modifier.fillMaxWidth().height(88.dp).clip(RoundedCornerShape(12.dp)).background(C.Raised).padding(horizontal = 28.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) Txt(e.hint, T.Body, color = C.Dust)
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(field),
                    singleLine = true,
                    textStyle = T.Body.copy(color = C.Linen, fontSize = T.CardTitle.fontSize),
                    cursorBrush = SolidColor(C.Lamp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        e.onSave(value)
                        onClose()
                    }),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Chip("Save", {
                    e.onSave(value)
                    onClose()
                })
                Chip("Cancel", onClose)
            }
        }
    }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { field.requestFocus() }
    }
}

/**
 * A row in a settings list: title, current value, and a switch when it's on/off. [phoneOnly] rows show a phone
 * icon; selecting one points at the QR code instead of doing nothing.
 */
@Composable
fun SettingRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    toggle: Boolean? = null,
    toggleEnabled: Boolean = true,
    phoneOnly: Boolean = false,
    icon: ImageVector? = null,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    Focusable(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 104.dp),
        color = C.Surface,
        focusedScale = 1.03f,
        focusRequester = focusRequester,
    ) { focused ->
        Row(
            Modifier.align(Alignment.CenterStart).fillMaxWidth().padding(horizontal = 28.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (icon != null) Icon(icon, 32.dp, if (focused) C.Ink else C.Stone)
            Column(Modifier.weight(1f)) {
                Txt(title, T.CardTitle, color = ink(focused), maxLines = 2)
                if (value.isNotBlank()) Txt(value, T.Label, color = inkMuted(focused), maxLines = 3)
            }
            if (phoneOnly) Icon(Icons.Smartphone, 32.dp, if (focused) C.Ink else C.Dust)
            if (toggle != null) Toggle(toggle, focused, enabled = toggleEnabled)
        }
    }
}

private fun hourLabel(h: Int, use24h: Boolean): String =
    java.time.LocalTime.of(h, 0).format(java.time.format.DateTimeFormatter.ofPattern(if (use24h) "H:mm" else "h a"))

private fun cycle(options: List<Int>, current: Int): Int = options[(options.indexOf(current) + 1) % options.size]
