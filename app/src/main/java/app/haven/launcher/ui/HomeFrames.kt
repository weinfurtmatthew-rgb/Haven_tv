package app.haven.launcher.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.haven.launcher.Haven
import app.haven.launcher.Screen
import app.haven.launcher.data.ClockStyle
import app.haven.launcher.data.HomeSection
import app.haven.launcher.data.Layouts

private const val MENU_WIDTH = 400

/**
 * Big tiles' way around (DESIGN.md, "Layouts"): a menu down the left with the profile on top. Moving down the
 * menu shows each section's page next to it; Right goes into the page, Left from the page comes back to the
 * section it belongs to. Live TV, Search and Settings do their thing on OK instead of showing a page.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MenuFrame(h: HomeCtx, section: HomeSection, onSection: (HomeSection) -> Unit, focusPage: () -> Unit, page: @Composable (HomeCtx) -> Unit) {
    val ctx = LocalContext.current
    val profiles by Haven.settings.profiles.collectAsState()
    val me = profiles.firstOrNull { it.active }
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.width(MENU_WIDTH.dp).fillMaxHeight()
                .then(if (Look.glass) Modifier.glassFill() else Modifier.background(C.Surface))
                .focusProperties { enter = { h.req("menu:${section.name}") } }
                .focusGroup()
                .padding(start = 40.dp, end = 32.dp, top = 56.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (me != null) {
                Row(Modifier.padding(start = 16.dp, bottom = 28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    ProfileButton(me, h.req("menu:profile"), h.track("menu:profile"), size = 72.dp) { h.guard { h.nav.go(Screen.Profiles) } }
                    Txt(me.name, T.Title, Modifier.weight(1f), maxLines = 1)
                    BackgroundButton(h, "menu:background")
                }
            }
            for (sec in h.s.look.sections) {
                MenuEntry(
                    h, sec, selected = sec == section,
                    onFocus = { if (!Layouts.isAction(sec)) onSection(sec) },
                ) {
                    if (Layouts.isAction(sec)) openSection(h, ctx, sec, onSection) else focusPage()
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            page(h.inside(start = 64.dp, end = SIDE.dp, width = (1920 - MENU_WIDTH - 64 - SIDE).dp))
        }
    }
}

@Composable
private fun MenuEntry(h: HomeCtx, sec: HomeSection, selected: Boolean, onFocus: () -> Unit, onClick: () -> Unit) {
    val k = "menu:${sec.name}"
    Focusable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(80.dp),
        color = if (selected) C.Raised else Color.Transparent,
        focusedScale = 1.03f,
        focusRequester = h.req(k),
        onFocus = { f ->
            h.track(k)(f)
            if (f) onFocus()
        },
    ) { focused ->
        Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            if (selected && !lit(focused)) Box(Modifier.size(6.dp, 36.dp).clip(RoundedCornerShape(3.dp)).background(C.Lamp))
            Icon(sectionIcon(sec), 32.dp, if (selected && !lit(focused)) C.Lamp else inkIcon(focused))
            Txt(sec.label, T.CardTitle, color = ink(focused))
        }
    }
}

/**
 * Shows' way around: tabs along the top, with the clock and the profile on the right. Moving along the tabs shows
 * each page below; Up from the page comes back to its tab.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TabsFrame(h: HomeCtx, section: HomeSection, onSection: (HomeSection) -> Unit, page: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val profiles by Haven.settings.profiles.collectAsState()
    val me = profiles.firstOrNull { it.active }
    val now by rememberNow()
    val look = h.s.look
    val use24 = h.s.use24h || DateFormat.is24HourFormat(ctx)
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = h.start, end = h.end, top = 44.dp, bottom = 8.dp)
                .focusProperties { enter = { h.req("tab:${section.name}") } }
                .focusGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (sec in look.sections) {
                    val k = "tab:${sec.name}"
                    Chip(
                        if (sec == HomeSection.Home) "For you" else sec.label,
                        { openSection(h, ctx, sec, onSection) },
                        selected = sec == section,
                        icon = if (Layouts.isAction(sec)) sectionIcon(sec) else null,
                        focusRequester = h.req(k),
                        onFocus = { f ->
                            h.track(k)(f)
                            if (f && !Layouts.isAction(sec)) onSection(sec)
                        },
                        glass = Look.glass,
                    )
                }
            }
            Box(Modifier.weight(1f))
            if (look.clock != ClockStyle.Hidden) Txt(formatTime(now, use24, withAmPm = !use24), T.Title, Modifier.padding(end = 28.dp), C.Linen)
            BackgroundButton(h)
            Spacer(width = 16.dp)
            if (me != null) ProfileButton(me, h.req("top:profile"), h.track("top:profile")) { h.guard { h.nav.go(Screen.Profiles) } }
            Spacer(width = 16.dp)
            IconButton(Icons.Settings, { h.guard { h.nav.go(Screen.Settings) } }, focusRequester = h.req("top:settings"), onFocus = h.track("top:settings"), glass = Look.glass)
        }
        Box(Modifier.weight(1f)) { page() }
    }
}
