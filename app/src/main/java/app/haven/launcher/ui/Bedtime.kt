package app.haven.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.haven.launcher.Haven
import app.haven.launcher.Nav
import app.haven.launcher.bedroom.Bedroom

/**
 * Bedtime for a profile that has one (the Kids layout): goodnight, and the sleep timer already running. A grown-up
 * can give half an hour more with the PIN.
 */
@Composable
fun BedtimeScreen(nav: Nav) {
    val ctx = LocalContext.current
    val s by Haven.settings.state.collectAsState()
    val ends by Bedroom.sleepEndsAt.collectAsState()
    val ok = remember { FocusRequester() }
    var asking by remember { mutableStateOf(false) }
    val left = if (ends > 0) maxOf(1, ((ends - System.currentTimeMillis()) / 60_000L).toInt()) else 0
    Column(Modifier.fillMaxSize().padding(top = 120.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Moon(Modifier.size(260.dp))
        Txt(if (s.profileName.isBlank()) "Time for bed" else "Time for bed, ${s.profileName}", T.Display, size = 120, align = TextAlign.Center)
        Txt(
            if (left > 0) "The TV turns off in $left ${if (left == 1) "minute" else "minutes"}. Sweet dreams!" else "Sweet dreams!",
            T.Body, color = C.Stone, size = 36,
        )
        Row(Modifier.padding(top = 32.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Chip("Goodnight", { nav.home() }, icon = Icons.Moon, focusRequester = ok)
            Chip("Grown-ups: 30 more minutes", {
                if (s.kids.locked && !GrownUp.isOpen(s)) asking = true else {
                    Bedroom.startSleep(ctx, 30)
                    nav.home()
                }
            }, icon = Icons.Clock)
        }
    }
    if (asking) {
        PinDialog(s.kids.pin, onOk = {
            GrownUp.unlock()
            asking = false
            Bedroom.startSleep(ctx, 30)
            nav.home()
        }, onDismiss = { asking = false })
    } else {
        FocusOnOpen(ok, 200)
    }
}

/** A crescent moon and a few stars, in the scheme's own light. */
@Composable
private fun Moon(modifier: Modifier) {
    val light = C.Linen
    val dark = C.Hearth
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension * 0.32f
            val c = Offset(size.width * 0.5f, size.height * 0.5f)
            drawCircle(light, r, c)
            drawCircle(dark, r * 0.9f, Offset(c.x + r * 0.45f, c.y - r * 0.3f))
            for ((x, y) in listOf(0.12f to 0.2f, 0.85f to 0.15f, 0.9f to 0.75f, 0.18f to 0.8f)) {
                drawCircle(light, size.minDimension * 0.02f, Offset(size.width * x, size.height * y))
            }
        }
    }
}
