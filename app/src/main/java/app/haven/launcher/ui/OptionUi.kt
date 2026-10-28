package app.haven.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
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

/**
 * One registry option as a settings row. A pick from a list changes with left and right, previewed and saved at
 * once; OK steps on to the next value, or opens the whole list when it's long (layouts, fonts, colours, photos).
 * Switches flip with OK, left or right. Orders and text open their editors; phone-only options point at the phone.
 */
@Composable
fun OptionRow(
    opt: Opt,
    s: Settings,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onPhone: (String) -> Unit,
    onOpen: (Opt) -> Unit,
) {
    val stepped = !opt.text && !opt.phoneOnly && opt.order == null
    if (!stepped) {
        val value = when {
            opt.secret -> opt.label(s)
            opt.text && opt.phoneOnly -> if (opt.value(s).isBlank()) "Not set. Add it on your phone" else "Saved"
            opt.text -> opt.value(s).ifBlank { "Not set" }
            else -> listOfNotNull(opt.label(s), opt.help?.takeIf { opt.order != null }).joinToString(". ")
        }
        SettingRow(opt.title, value, modifier, phoneOnly = opt.phoneOnly, focusRequester = focusRequester) {
            if (opt.phoneOnly) onPhone(opt.title) else onOpen(opt)
        }
        return
    }
    val choices = opt.choices(s)
    val at = choices.indexOfFirst { it.value == opt.value(s) }
    val long = choices.size > 6 || choices.any { it.note != null }
    // Read the newest settings when a key comes in: a held key can arrive before the row has redrawn.
    fun step(dir: Int, wrap: Boolean = false) {
        val cur = Haven.settings.value
        val list = opt.choices(cur)
        if (list.isEmpty()) return
        val i = list.indexOfFirst { it.value == opt.value(cur) }
        val j = when {
            i < 0 -> 0
            wrap -> Math.floorMod(i + dir, list.size)
            else -> (i + dir).coerceIn(0, list.lastIndex)
        }
        if (j == i) return
        Haven.settings.update { opt.with(it, list[j].value) }
        if (opt.preview == Preview.Sound) AlarmTone.preview(enumOr(list[j].value, AlarmSound.Chime))
    }
    val chosen = choices.getOrNull(at)
    ValueRow(
        title = opt.title,
        help = chosen?.note ?: opt.help,
        value = if (opt.toggle) null else opt.label(s),
        toggle = if (opt.toggle) opt.on(s) else null,
        canLess = at > 0,
        canMore = at in 0 until choices.lastIndex,
        modifier = modifier,
        focusRequester = focusRequester,
        // A switch: left is off, right is on.
        onStep = { dir -> if (opt.toggle) Haven.settings.update { opt.with(it, if (dir > 0) Opt.ON else Opt.OFF) } else step(dir) },
        onLeave = { if (opt.preview == Preview.Sound) AlarmTone.stopPreview() },
        onClick = { if (long && !opt.toggle) onOpen(opt) else step(1, wrap = true) },
    )
}

/**
 * A row whose value changes in place: the title and a line of help on the left, the value (or a switch) on the
 * right with arrows while it's focused. Left and right call [onStep]; they never move focus off the row.
 */
@Composable
fun ValueRow(
    title: String,
    help: String?,
    value: String?,
    toggle: Boolean?,
    canLess: Boolean,
    canMore: Boolean,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onStep: (Int) -> Unit,
    onLeave: () -> Unit = {},
    onClick: () -> Unit,
) {
    Focusable(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 104.dp),
        color = C.Surface,
        focusedScale = 1.03f,
        focusRequester = focusRequester,
        onFocus = { if (!it) onLeave() },
        onKey = { ev ->
            val dir = when (ev.key) {
                Key.DirectionLeft -> -1
                Key.DirectionRight -> 1
                else -> 0
            }
            if (dir != 0 && ev.type == KeyEventType.KeyDown) onStep(dir)
            dir != 0
        },
    ) { focused ->
        Row(
            Modifier.align(Alignment.CenterStart).fillMaxWidth().padding(start = 28.dp, end = 20.dp, top = 20.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Txt(title, T.CardTitle, color = ink(focused), maxLines = 2)
                if (!help.isNullOrBlank()) Txt(help, T.Label, color = inkMuted(focused), maxLines = 2)
            }
            if (toggle != null) {
                Toggle(toggle, focused)
            } else if (value != null) {
                Row(Modifier.widthIn(max = 400.dp), verticalAlignment = Alignment.CenterVertically) {
                    val arrow = if (lit(focused)) C.Ink else C.Linen
                    if (focused) Icon(Icons.ChevronLeft, 32.dp, arrow.copy(alpha = if (canLess) 1f else 0.25f))
                    Txt(
                        value, T.Body, Modifier.weight(1f, fill = false).padding(horizontal = 8.dp),
                        color = if (focused) ink(true) else C.Stone, maxLines = 2, align = TextAlign.End,
                    )
                    if (focused) Icon(Icons.ChevronRight, 32.dp, arrow.copy(alpha = if (canMore) 1f else 0.25f))
                }
            }
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
) = optionList(Options.of(section).filter { it.visible(s) }, s, rowModifier, firstRow, onPhone, onOpen)

/** Adds [opts] to a settings list, the first one taking [firstRow]. */
fun LazyListScope.optionList(
    opts: List<Opt>,
    s: Settings,
    rowModifier: Modifier,
    firstRow: FocusRequester?,
    onPhone: (String) -> Unit,
    onOpen: (Opt) -> Unit,
) {
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
                order.items.associateBy { it.value }.let { byKey -> keys.mapNotNull { byKey[it] } }.map { OrderItem(it.value, it.label, it.note) },
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
        opt.text -> TextEditDialog(
            TextEdit(opt.title, if (opt.secret) "" else opt.value(s), opt.help ?: "") { v -> Haven.settings.update { opt.with(it, v) } },
            onClose,
        )
        else -> {
            val saved = remember(opt.id) { Haven.settings.value }
            fun revert() {
                when (opt.preview) {
                    Preview.Look -> Look.apply(Haven.settings.value.look)
                    Preview.Background -> BgPreview.value = null
                    Preview.Layout -> {
                        Look.apply(Haven.settings.value.look)
                        BgPreview.value = null
                    }
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
                    Preview.Layout -> { v ->
                        val next = opt.with(saved, v)
                        Look.apply(next.look)
                        BgPreview.value = next.background
                    }
                    Preview.Sound -> { v -> AlarmTone.preview(enumOr(v, AlarmSound.Chime)) }
                    Preview.None -> null
                },
                sample = opt.preview == Preview.Look || opt.preview == Preview.Layout,
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
    else -> Choice(c.value, c.label, c.note)
}
