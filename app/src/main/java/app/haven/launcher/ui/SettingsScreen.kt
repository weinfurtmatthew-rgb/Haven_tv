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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.haven.launcher.BuildConfig
import app.haven.launcher.Haven
import app.haven.launcher.Nav
import app.haven.launcher.Screen
import app.haven.launcher.data.ArtSource
import app.haven.launcher.data.ArtStyle
import app.haven.launcher.data.QuietMode
import app.haven.launcher.data.TvPanel
import app.haven.launcher.sports.ScorePopup
import app.haven.launcher.ha.HaStatus
import app.haven.launcher.setup.SetupServer
import app.haven.launcher.system.HomeButtonService
import app.haven.launcher.update.Updater
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private class TextEdit(val title: String, val initial: String, val hint: String, val onSave: (String) -> Unit)

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
    val first = remember { FocusRequester() }

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
        runCatching { first.requestFocus() }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 96.dp, vertical = 56.dp), verticalArrangement = Arrangement.spacedBy(36.dp)) {
        ScreenHeader("Settings", "Haven ${BuildConfig.VERSION_NAME}", onBack = { nav.go(Screen.Home) })
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            LazyColumn(
                Modifier.width(1060.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 60.dp, start = 8.dp, end = 8.dp),
            ) {
                item { Section("This room") }
                item {
                    SettingRow("Room name", s.roomName, focusRequester = first) {
                        editing = TextEdit("Room name", s.roomName, "Living Room") { v ->
                            Haven.settings.update { it.copy(roomName = v.ifBlank { "Living Room" }) }
                        }
                    }
                }
                item {
                    val current = areas.firstOrNull { it.id == s.haArea }
                    SettingRow(
                        "Home Assistant area",
                        when {
                            current != null -> current.name
                            areas.isEmpty() && s.haConfigured -> "No areas found · showing everything"
                            areas.isEmpty() -> "Connect Home Assistant first"
                            else -> "All areas · select to pick this room"
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
                }
                item {
                    SettingRow("Bedroom features", "Alarm clock and sleep timer on the home screen", toggle = s.bedroomExtras) {
                        Haven.settings.update { it.copy(bedroomExtras = !it.bedroomExtras) }
                    }
                }
                item {
                    SettingRow("Your name", s.profileName.ifBlank { "Not set · used for the greeting and avatar" }) {
                        editing = TextEdit("Your name", s.profileName, "Matt") { v ->
                            Haven.settings.update { it.copy(profileName = v.trim()) }
                        }
                    }
                }

                item { Section("Home Assistant") }
                item {
                    SettingRow(
                        "Connection",
                        when (val st = status) {
                            HaStatus.NotConfigured -> "Not set up · scan the code on the right with your phone"
                            HaStatus.Connecting -> "Connecting…"
                            is HaStatus.Connected -> "Connected · ${st.entityCount} entities"
                            is HaStatus.Failed -> st.message
                        },
                        icon = Icons.House,
                    ) { scope.launch { Haven.ha.refresh() } }
                }
                item {
                    SettingRow("Server address", s.haUrl.ifBlank { "Not set" }) {
                        editing = TextEdit("Home Assistant address", s.haUrl, "http://192.168.1.10:8123") { v ->
                            Haven.settings.update { it.copy(haUrl = v.trim()) }
                            scope.launch { Haven.ha.refresh() }
                        }
                    }
                }
                item {
                    SettingRow("Access token", if (s.haToken.isBlank()) "Not set · easier to paste from your phone" else "Saved") {
                        editing = TextEdit("Long-lived access token", "", "Paste or type the token") { v ->
                            if (v.isNotBlank()) Haven.settings.update { it.copy(haToken = v.trim()) }
                            scope.launch { Haven.ha.refresh() }
                        }
                    }
                }

                item { Section("Art mode") }
                item {
                    SettingRow("Show", s.artSource.label) {
                        val all = ArtSource.values()
                        Haven.settings.update { it.copy(artSource = all[(it.artSource.ordinal + 1) % all.size]) }
                    }
                }
                item {
                    SettingRow("Change the picture every", "${s.artIntervalMin} min") {
                        Haven.settings.update { it.copy(artIntervalMin = cycle(listOf(1, 5, 15, 30, 60), it.artIntervalMin)) }
                    }
                }
                item {
                    SettingRow("Start art mode", if (s.artIdleMin == 0) "Only when I choose it" else "After ${s.artIdleMin} min idle on the home screen") {
                        Haven.settings.update { it.copy(artIdleMin = cycle(listOf(0, 5, 10, 15, 30), it.artIdleMin)) }
                    }
                }
                item { SettingRow("Your photos", "${Haven.art.photos().size} on this TV · add more from your phone") {} }
                item {
                    SettingRow("Use art mode as the screensaver", "Choose \"Haven art\" in Android's screen saver settings") {
                        openSystem(ctx, AndroidSettings.ACTION_DREAM_SETTINGS)
                    }
                }

                item {
                    SettingRow("Art style", s.artCare.style.label) {
                        val all = ArtStyle.values()
                        Haven.settings.update { it.copy(artCare = it.artCare.copy(style = all[(it.artCare.style.ordinal + 1) % all.size])) }
                    }
                }
                item {
                    SettingRow(
                        "TV type", "${s.artCare.panel.label} · " +
                            if (s.artCare.strongProtection) "strong burn-in protection" else "standard burn-in protection",
                    ) {
                        val next = if (s.artCare.panel == TvPanel.Oled) TvPanel.Lcd else TvPanel.Oled
                        Haven.settings.update { it.copy(artCare = it.artCare.copy(panel = next)) }
                    }
                }
                item {
                    SettingRow("Dim the art", if (s.artCare.dimPct == 0) "Off" else "${s.artCare.dimPct}% darker") {
                        Haven.settings.update { it.copy(artCare = it.artCare.copy(dimPct = cycle(listOf(0, 15, 30, 45), it.artCare.dimPct))) }
                    }
                }
                item {
                    val c = s.artCare
                    SettingRow(
                        "Quiet hours",
                        if (!c.quietEnabled) "Off" else "${hourLabel(c.quietStart, s.use24h)} to ${hourLabel(c.quietEnd, s.use24h)} · ${c.quietMode.label.lowercase()}",
                    ) {
                        val presets = listOf(23 to 23, 23 to 6, 22 to 6, 0 to 7, 21 to 7)
                        val i = presets.indexOf(c.quietStart to c.quietEnd)
                        val (a, b) = presets[(i + 1) % presets.size]
                        Haven.settings.update { it.copy(artCare = it.artCare.copy(quietStart = a, quietEnd = b)) }
                    }
                }
                item {
                    SettingRow("During quiet hours", s.artCare.quietMode.label) {
                        val next = if (s.artCare.quietMode == QuietMode.Black) QuietMode.Dim else QuietMode.Black
                        Haven.settings.update { it.copy(artCare = it.artCare.copy(quietMode = next)) }
                    }
                }
                item {
                    SettingRow(
                        "Only when someone is in the room",
                        if (s.artCare.presenceEntity.isBlank()) "Off · pick a motion sensor on your phone"
                        else "${Haven.ha.entities.value[s.artCare.presenceEntity]?.name ?: s.artCare.presenceEntity} · black after ${s.artCare.presenceDelayMin} min",
                    ) {}
                }
                item {
                    SettingRow("Art time limit", if (s.artCare.maxHours == 0) "No limit" else "Go black after ${s.artCare.maxHours} hours") {
                        Haven.settings.update { it.copy(artCare = it.artCare.copy(maxHours = cycle(listOf(0, 2, 4, 8), it.artCare.maxHours))) }
                    }
                }

                item { Section("Morning briefing") }
                item {
                    SettingRow("Show the briefing now", "It also opens when you stop the alarm") { nav.go(Screen.Briefing) }
                }
                item {
                    val b = s.brief
                    SettingRow(
                        "What it shows",
                        listOf(
                            "weather",
                            if (b.calendars.isEmpty()) "all calendars" else "${b.calendars.size} calendar(s)",
                            if (b.commuteEntity.isBlank()) "no commute" else "commute",
                            if (b.statusEntities.isEmpty()) "locks and garage" else "${b.statusEntities.size} home items",
                            if (b.headlinesFeed.isBlank()) "no headlines" else "headlines",
                            "your teams",
                        ).joinToString(" · ") + " — change on your phone",
                    ) {}
                }

                item { Section("Sports") }
                item {
                    SettingRow(
                        "Your teams",
                        if (s.sports.teams.isEmpty()) "None yet · choose them on your phone" else s.sports.teams.joinToString(", ") { it.name },
                        icon = Icons.Trophy,
                    ) { nav.go(Screen.Sports) }
                }
                item {
                    SettingRow("Score alerts", "Game start, scoring and finals for your teams, over any app", toggle = s.sports.alerts) {
                        Haven.settings.update { it.copy(sports = it.sports.copy(alerts = !it.sports.alerts)) }
                    }
                }
                item {
                    val ok = ScorePopup.canOverlay(ctx)
                    SettingRow(
                        "Show alerts on top of other apps",
                        if (ok) "Allowed" else "Not allowed · alerts show as small messages instead. Select to allow",
                        toggle = ok,
                    ) { ScorePopup.requestPermission(ctx) }
                }
                item {
                    SettingRow("Test an alert", "Shows a sample score alert") {
                        ScorePopup.show(ctx, "Sample: HOM 21 – 17 AWY", "Q3 4:12 · Haven score alert")
                    }
                }

                item { Section("Home button") }
                item {
                    SettingRow(
                        "Haven Home button service",
                        if (serviceOn) "On · Home opens Haven" else "Off · select, then turn on \"Haven Home button\"",
                        toggle = serviceOn,
                    ) { openSystem(ctx, AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS) }
                }
                item {
                    SettingRow("Open Haven when Home is pressed", "Press Home twice quickly for the original home screen", toggle = s.homeOverride) {
                        Haven.settings.update { it.copy(homeOverride = !it.homeOverride) }
                    }
                }
                if (!serviceOn) {
                    item {
                        SettingRow(
                            "Switch keeps turning itself off?",
                            "Select to reinstall Haven through Android's installer, then try the switch again",
                            icon = Icons.RefreshCw,
                        ) { scope.launch { Haven.updater.checkAndInstall(reinstall = true) } }
                    }
                }
                item {
                    SettingRow("Open the original home screen", "On the DIRECTV box this is the DIRECTV app") {
                        HomeButtonService.openStockHome(ctx)
                    }
                }
                item {
                    SettingRow("Recent screens", "For troubleshooting: which apps and screens opened lately") { showScreens = true }
                }

                item { Section("Apps and clock") }
                item {
                    SettingRow("Hidden apps", if (s.hiddenApps.isEmpty()) "None" else "${s.hiddenApps.size} hidden · select to show them again") {
                        Haven.settings.update { it.copy(hiddenApps = emptySet()) }
                    }
                }
                item {
                    SettingRow("Clock", if (s.use24h) "24-hour" else "12-hour") {
                        Haven.settings.update { it.copy(use24h = !it.use24h) }
                    }
                }

                item { Section("About") }
                item {
                    SettingRow(
                        "Updates",
                        when (val u = updateState) {
                            Updater.State.Idle -> "Version ${BuildConfig.VERSION_NAME} · select to check"
                            Updater.State.Checking -> "Checking…"
                            Updater.State.UpToDate -> "Up to date (${BuildConfig.VERSION_NAME})"
                            is Updater.State.Downloading -> "Downloading ${u.version}… ${u.percent}%"
                            is Updater.State.Installing -> "Opening the installer for ${u.version}"
                            is Updater.State.Failed -> u.message
                        },
                        icon = Icons.Download,
                    ) { scope.launch { Haven.updater.checkAndInstall() } }
                }
                item { SettingRow("Android settings", "") { openSystem(ctx, AndroidSettings.ACTION_SETTINGS) } }
            }
            PhoneSetupPanel(server, Modifier.weight(1f).fillMaxHeight())
        }
    }

    editing?.let { e -> TextEditDialog(e) { editing = null } }
    if (showScreens) RecentScreensDialog { showScreens = false }
}

/** Lists the package / activity of recent screens, as seen by the Home button service. */
@Composable
private fun RecentScreensDialog(onClose: () -> Unit) {
    val screens by HomeButtonService.recentScreens.collectAsState()
    val close = remember { FocusRequester() }
    HavenDialog(onClose) {
        Column(
            Modifier.width(1300.dp).clip(RoundedCornerShape(24.dp)).background(C.Card).padding(40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Txt("Recent screens", 34, weight = FontWeight.ExtraBold)
            Txt(
                if (screens.isEmpty()) "Nothing yet. Turn on the Home button service, open a few apps, then come back."
                else "Newest first. Home apps on this box: " + HomeButtonService.stockHomePackages(Haven.app).joinToString(", ").ifBlank { "none" },
                20, weight = FontWeight.Medium, color = C.Muted, maxLines = 3,
            )
            for (line in screens) Txt(line, 20, weight = FontWeight.Medium)
            PillButton("Close", onClose, selected = true, focusRequester = close)
        }
    }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { close.requestFocus() }
    }
}

@Composable
private fun PhoneSetupPanel(server: SetupServer, modifier: Modifier) {
    val url by server.url.collectAsState()
    Panel(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Smartphone, 36.dp, C.Accent)
            Txt("Set up from your phone", 30, weight = FontWeight.ExtraBold)
        }
        Txt(
            "Scan with your phone's camera (same Wi-Fi as the TV) to connect Home Assistant, pick this room's lights, upload photos and set the alarm.",
            20, weight = FontWeight.Medium, color = C.Muted, maxLines = 4,
        )
        val u = url
        if (u == null) {
            Txt("This TV isn't on a network yet.", 22, weight = FontWeight.Medium)
        } else {
            Box(
                Modifier.size(300.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(14.dp),
            ) { QrCode(u, Modifier.fillMaxSize()) }
            Txt("Or type this address:", 18, weight = FontWeight.Medium, color = C.Muted)
            Txt(u.removePrefix("http://"), 24, weight = FontWeight.Bold, maxLines = 2)
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
    HavenDialog(onClose) {
        Column(
            Modifier.width(1100.dp).clip(RoundedCornerShape(24.dp)).background(C.Card).padding(40.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Txt(e.title, 34, weight = FontWeight.ExtraBold)
            Box(
                Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(16.dp)).background(C.CardHi).padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) Txt(e.hint, 26, weight = FontWeight.Medium, color = C.Ring)
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(field),
                    singleLine = true,
                    textStyle = TextStyle(color = C.Text, fontSize = 26.sp, fontFamily = Manrope, fontWeight = FontWeight.SemiBold),
                    cursorBrush = SolidColor(C.Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        e.onSave(value)
                        onClose()
                    }),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PillButton("Save", {
                    e.onSave(value)
                    onClose()
                }, selected = true)
                PillButton("Cancel", onClose)
            }
        }
    }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { field.requestFocus() }
    }
}

@Composable
fun SettingRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    toggle: Boolean? = null,
    icon: ImageVector? = null,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    Focusable(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(if (value.isBlank()) 80.dp else 96.dp),
        color = C.CardHi,
        focusedColor = C.Line,
        focusedScale = 1.02f,
        focusRequester = focusRequester,
    ) { focused ->
        Row(
            Modifier.fillMaxSize().padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (icon != null) Icon(icon, 32.dp, if (focused) C.Accent else C.Muted)
            Column(Modifier.weight(1f)) {
                Txt(title, 24, weight = FontWeight.Bold)
                if (value.isNotBlank()) Txt(value, 18, weight = FontWeight.Medium, color = C.Muted)
            }
            if (toggle != null) Toggle(toggle)
        }
    }
}

@Composable
private fun Section(title: String) {
    Txt(title, 22, Modifier.padding(top = 18.dp, bottom = 2.dp), FontWeight.ExtraBold, C.Accent)
}

private fun hourLabel(h: Int, use24h: Boolean): String =
    java.time.LocalTime.of(h, 0).format(java.time.format.DateTimeFormatter.ofPattern(if (use24h) "H:mm" else "h a"))

private fun cycle(options: List<Int>, current: Int): Int = options[(options.indexOf(current) + 1) % options.size]
