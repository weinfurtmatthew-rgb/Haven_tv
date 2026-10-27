package app.haven.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.haven.launcher.Haven
import app.haven.launcher.bedroom.AlarmTone
import app.haven.launcher.data.Accent
import app.haven.launcher.data.AlarmSound
import app.haven.launcher.data.GradientPreset
import app.haven.launcher.data.HeadingFont
import app.haven.launcher.data.Opt
import app.haven.launcher.data.OptChoice
import app.haven.launcher.data.OptSection
import app.haven.launcher.data.Options
import app.haven.launcher.data.Preview
import app.haven.launcher.data.Settings
import app.haven.launcher.data.TextFont
import app.haven.launcher.data.ThemePreset
import app.haven.launcher.data.enumOr

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
            if (icon != null) Icon(icon, 32.dp, inkIcon(focused))
            Column(Modifier.weight(1f)) {
                Txt(title, T.CardTitle, color = ink(focused), maxLines = 2)
                if (value.isNotBlank()) Txt(value, T.Label, color = inkMuted(focused), maxLines = 3)
            }
            if (phoneOnly) Icon(Icons.Smartphone, 32.dp, if (lit(focused)) C.Ink else C.Dust)
            if (toggle != null) Toggle(toggle, focused, enabled = toggleEnabled)
        }
    }
}

/** One registry option as a settings row. Switches flip on OK; everything else opens [onOpen]. */
@Composable
fun OptionRow(
    opt: Opt,
    s: Settings,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onPhone: (String) -> Unit,
    onOpen: (Opt) -> Unit,
) {
    val value = when {
        opt.toggle -> opt.help.orEmpty()
        opt.text && opt.phoneOnly -> if (opt.value(s).isBlank()) "Not set. Add it on your phone" else "Saved"
        opt.text -> opt.value(s).ifBlank { "Not set" }
        else -> listOfNotNull(opt.label(s), opt.help?.takeIf { opt.order != null }).joinToString(". ")
    }
    SettingRow(
        opt.title, value, modifier,
        toggle = if (opt.toggle) opt.on(s) else null,
        phoneOnly = opt.phoneOnly,
        focusRequester = focusRequester,
    ) {
        when {
            opt.toggle -> Haven.settings.update { opt.with(it, if (opt.on(it)) Opt.OFF else Opt.ON) }
            opt.phoneOnly -> onPhone(opt.title)
            else -> onOpen(opt)
        }
    }
}

/** Adds a section's visible options to a settings list. */
fun LazyListScope.optionRows(
    section: OptSection,
    s: Settings,
    rowModifier: Modifier,
    firstRow: FocusRequester?,
    onPhone: (String) -> Unit,
    onOpen: (Opt) -> Unit,
) {
    val opts = Options.of(section).filter { it.visible(s) }
    for ((i, opt) in opts.withIndex()) {
        item(key = opt.id) {
            OptionRow(opt, s, rowModifier, if (i == 0) firstRow else null, onPhone, onOpen)
        }
    }
}

/**
 * The dialog for an open option: a list to pick from (previewing looks and backgrounds live), the order list for
 * rows and sections, or text entry. Back puts back whatever was showing before.
 */
@Composable
fun OptionDialog(opt: Opt, onClose: () -> Unit) {
    val s by Haven.settings.state.collectAsState()
    val order = opt.order
    when {
        order != null -> {
            val (keys, shown) = order.get(s)
            OrderDialog(
                opt.title, opt.help,
                keys.mapNotNull { k -> order.items.firstOrNull { it.value == k } }.map { OrderItem(it.value, it.label) },
                shown,
                onToggle = { k ->
                    Haven.settings.update { cur ->
                        val (o, sh) = order.get(cur)
                        order.set(cur, o, if (k in sh) sh - k else sh + k)
                    }
                },
                onMove = { k, by ->
                    Haven.settings.update { cur ->
                        val (o, sh) = order.get(cur)
                        val i = o.indexOf(k)
                        val j = (i + by).coerceIn(0, o.size - 1)
                        if (i < 0 || i == j) cur else order.set(cur, o.toMutableList().apply { add(j, removeAt(i)) }, sh)
                    }
                },
                onDismiss = onClose,
            )
        }
        opt.text -> TextEditDialog(TextEdit(opt.title, opt.value(s), opt.help ?: "") { v -> Haven.settings.update { opt.with(it, v) } }, onClose)
        else -> {
            val saved = remember(opt.id) { Haven.settings.value }
            fun revert() {
                when (opt.preview) {
                    Preview.Look -> Look.apply(Haven.settings.value.look)
                    Preview.Background -> BgPreview.value = null
                    Preview.Sound -> AlarmTone.stopPreview()
                    Preview.None -> Unit
                }
            }
            // Leaving the dialog any way at all must never leave a preview behind.
            DisposableEffect(opt.id) { onDispose { revert() } }
            ChoiceDialog(
                title = opt.title,
                choices = opt.choices(s).map { decorate(opt, it) },
                selected = opt.value(s),
                note = opt.help,
                onPreview = when (opt.preview) {
                    Preview.Look -> { v -> Look.apply(opt.with(saved, v).look) }
                    Preview.Background -> { v -> BgPreview.value = opt.with(saved, v).background }
                    Preview.Sound -> { v -> AlarmTone.preview(enumOr(v, AlarmSound.Chime)) }
                    Preview.None -> null
                },
                sample = opt.preview == Preview.Look,
                onPick = { v ->
                    Haven.settings.update { opt.with(it, v) }
                    revert()
                    onClose()
                },
                onDismiss = {
                    revert()
                    onClose()
                },
            )
        }
    }
}

/** Shows choices the way they look: colours as swatches, fonts in their own font. */
fun decorate(opt: Opt, c: OptChoice): Choice = when (opt.id) {
    "look.theme" -> Choice(c.value, c.label, swatch = SCHEME_SWATCHES[enumOr(c.value, ThemePreset.Hearth)])
    "look.accent" -> Choice(c.value, c.label, swatch = ACCENTS[enumOr(c.value, Accent.Lamp)]?.bright)
    "look.headingFont" -> Choice(c.value, c.label, family = headingFamilyOf(enumOr(c.value, HeadingFont.Fraunces)))
    "look.textFont" -> Choice(c.value, c.label, family = textFamilyOf(enumOr(c.value, TextFont.Atkinson)))
    "bg.color" -> Choice(c.value, c.label, swatch = c.value.toLongOrNull()?.let { Color(it) })
    "bg.gradient" -> Choice(c.value, c.label, swatch = Color(enumOr(c.value, GradientPreset.Dusk).colors[1]))
    else -> Choice(c.value, c.label)
}
