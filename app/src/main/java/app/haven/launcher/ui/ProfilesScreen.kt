package app.haven.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.haven.launcher.Haven
import app.haven.launcher.Nav
import app.haven.launcher.Screen
import app.haven.launcher.data.ProfileInfo
import kotlinx.coroutines.delay

/** How many profiles one TV keeps: enough for a household, few enough to fit in one row. */
const val MAX_PROFILES = 6

/** "Who's watching?": everyone's picture; picking one switches all of Haven to their settings. */
@Composable
fun ProfilesScreen(nav: Nav) {
    val profiles by Haven.settings.profiles.collectAsState()
    var adding by remember { mutableStateOf(false) }
    val requesters = remember { HashMap<String, FocusRequester>() }
    fun req(id: String) = requesters.getOrPut(id) { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { req(profiles.firstOrNull { it.active }?.id ?: "add").requestFocus() }
    }

    Column(Modifier.fillMaxSize().padding(top = 200.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Txt("Who's watching?", T.Headline, size = 72)
        Txt("Each profile has its own look, apps, alarm, briefing and teams.", T.Body, Modifier.padding(top = 16.dp), C.Stone)
        Row(Modifier.padding(top = 88.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            for (p in profiles) {
                ProfileTile(p, req(p.id)) {
                    Haven.settings.switchTo(p.id)
                    nav.home()
                }
            }
            if (profiles.size < MAX_PROFILES) AddProfileTile(req("add")) { adding = true }
        }
        Box(Modifier.padding(top = 96.dp)) {
            Chip("Manage profiles", {
                nav.settingsStart = "Profiles"
                nav.go(Screen.Settings)
            }, icon = Icons.Settings)
        }
    }
    if (adding) AddProfileFlow(onDone = { id ->
        adding = false
        if (id != null) {
            Haven.settings.switchTo(id)
            nav.home()
        }
    })
}

@Composable
private fun ProfileTile(p: ProfileInfo, focus: FocusRequester, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(260.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Focusable(
            onClick = onClick,
            modifier = Modifier.size(200.dp),
            shape = L.pill(200.dp),
            color = Color.Transparent,
            style = FocusStyle.Picture,
            focusedScale = 1.08f,
            focusRequester = focus,
            onFocus = { focused = it },
        ) {
            Avatar(p.name, ACCENTS.getValue(p.avatar).bright, 200.dp)
        }
        Txt(p.name, T.Title, Modifier.padding(top = 28.dp), if (focused) C.Linen else C.Stone, maxLines = 2, align = TextAlign.Center)
    }
}

@Composable
private fun AddProfileTile(focus: FocusRequester, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(260.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Focusable(
            onClick = onClick,
            modifier = Modifier.size(200.dp),
            shape = L.pill(200.dp),
            color = C.Raised,
            focusedScale = 1.08f,
            focusRequester = focus,
            onFocus = { focused = it },
        ) { f ->
            Icon(Icons.UserPlus, 72.dp, inkIcon(f), Modifier.align(Alignment.Center))
        }
        Txt("Add a profile", T.Title, Modifier.padding(top = 28.dp), if (focused) C.Linen else C.Stone, maxLines = 2, align = TextAlign.Center)
    }
}

/**
 * Name, then where to start from: Haven's own look (keeping this TV's room and Home Assistant) or a copy of the
 * current profile. [onDone] gets the new profile's id, or null when cancelled.
 */
@Composable
fun AddProfileFlow(onDone: (String?) -> Unit) {
    var name by remember { mutableStateOf<String?>(null) }
    val current = Haven.settings.nameOf(Haven.settings.activeId)
    val n = name
    if (n == null) {
        var saved by remember { mutableStateOf(false) }
        TextEditDialog(TextEdit("New profile", "", "Their name") { v ->
            saved = true
            name = v.trim().ifBlank { "Profile ${Haven.settings.profiles.value.size + 1}" }
        }) { if (!saved) onDone(null) }
    } else {
        ChoiceDialog(
            "Start $n's profile with",
            listOf(
                Choice("fresh", "Haven's own look", "Keeps this TV's room and Home Assistant"),
                Choice("copy", "A copy of $current's settings", "Their look, apps, alarm and teams, to change from there"),
            ),
            "fresh",
            onPick = { v -> onDone(Haven.settings.addProfile(n, copy = v == "copy")) },
            onDismiss = { onDone(null) },
        )
    }
}

/** A small profile badge (picture and name) for rows and headers. */
@Composable
fun ProfileBadge(p: ProfileInfo) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(40.dp).clip(L.pill(40.dp)).background(ACCENTS.getValue(p.avatar).bright), contentAlignment = Alignment.Center) {
            Txt(p.name.take(1).uppercase(), T.Label, color = C.OnBright)
        }
        Txt(p.name, T.Label, color = C.Stone)
    }
}
