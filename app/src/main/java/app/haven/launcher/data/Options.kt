package app.haven.launcher.data

/**
 * Every choice a profile can make, defined once. The TV's Settings screen shows each as a row that opens a list
 * (with a live preview for looks and backgrounds), and the phone page draws the same options from /api/options.
 * Adding an option here adds it to both.
 */
enum class OptSection(val label: String) {
    Look("Look"),
    Home("Home screen"),
    Background("Background"),
    Art("Art mode"),
    Alarm("Alarm"),
    AlarmScreen("Alarm screen"),
    Briefing("Morning briefing"),
    Sports("Sports"),
    Alerts("Score alerts"),
}

/** What changing an option previews before it's saved. */
enum class Preview { None, Look, Background, Sound }

class OptChoice(val value: String, val label: String)

/** A list with each item on or off, in an order (home rows, briefing sections). */
class OrderSpec(
    val items: List<OptChoice>,
    val get: (Settings) -> Pair<List<String>, Set<String>>,
    val set: (Settings, List<String>, Set<String>) -> Settings,
)

class Opt(
    val id: String,
    val section: OptSection,
    val title: String,
    val choices: (Settings) -> List<OptChoice> = { emptyList() },
    val get: (Settings) -> String,
    val set: (Settings, String) -> Settings,
    val help: String? = null,
    /** On or off: the TV shows a switch and OK flips it. */
    val toggle: Boolean = false,
    /** Free text (a greeting, a key) rather than a list. */
    val text: Boolean = false,
    /** Set on the phone only (long keys): the TV row points at the QR code. */
    val phoneOnly: Boolean = false,
    val order: OrderSpec? = null,
    val preview: Preview = Preview.None,
    val visible: (Settings) -> Boolean = { true },
    /** Takes values beyond [choices] (any colour from the phone page's picker). */
    val free: Boolean = false,
) {
    fun value(s: Settings): String = get(s)

    /** The current choice in words, for the row under the title. */
    fun label(s: Settings): String {
        if (order != null) {
            val (o, shown) = order.get(s)
            val names = o.filter { it in shown }.mapNotNull { k -> order.items.firstOrNull { it.value == k }?.label }
            return if (names.isEmpty()) "None" else names.joinToString(", ")
        }
        val v = get(s)
        if (text) return v
        return choices(s).firstOrNull { it.value == v }?.label ?: v
    }

    fun on(s: Settings): Boolean = get(s) == ON

    /** The setting with [value] chosen; unknown values leave it as it was. */
    fun with(s: Settings, value: String): Settings =
        if (text || free || choices(s).any { it.value == value }) set(s, value) else s

    companion object {
        const val ON = "on"
        const val OFF = "off"
    }
}

/** Things outside the settings that some options list (uploaded backgrounds). Filled in by Haven at start. */
object OptionSources {
    @Volatile
    var backgrounds: () -> List<String> = { emptyList() }
}

private fun <E : Enum<E>> enumOpt(
    id: String,
    section: OptSection,
    title: String,
    values: Array<E>,
    label: (E) -> String,
    get: (Settings) -> E,
    set: (Settings, E) -> Settings,
    help: String? = null,
    preview: Preview = Preview.None,
    visible: (Settings) -> Boolean = { true },
) = Opt(
    id, section, title,
    choices = { values.map { OptChoice(it.name, label(it)) } },
    get = { get(it).name },
    set = { s, v -> values.firstOrNull { it.name == v }?.let { set(s, it) } ?: s },
    help = help, preview = preview, visible = visible,
)

private fun boolOpt(
    id: String,
    section: OptSection,
    title: String,
    get: (Settings) -> Boolean,
    set: (Settings, Boolean) -> Settings,
    help: String? = null,
    preview: Preview = Preview.None,
    visible: (Settings) -> Boolean = { true },
) = Opt(
    id, section, title,
    choices = { listOf(OptChoice(Opt.ON, "On"), OptChoice(Opt.OFF, "Off")) },
    get = { if (get(it)) Opt.ON else Opt.OFF },
    set = { s, v -> set(s, v == Opt.ON) },
    help = help, toggle = true, preview = preview, visible = visible,
)

private fun intOpt(
    id: String,
    section: OptSection,
    title: String,
    values: List<Int>,
    label: (Int) -> String,
    get: (Settings) -> Int,
    set: (Settings, Int) -> Settings,
    help: String? = null,
    preview: Preview = Preview.None,
    visible: (Settings) -> Boolean = { true },
) = Opt(
    id, section, title,
    // The saved value is always listed, even when the phone page set one that isn't in the usual steps.
    choices = { s -> (values + get(s)).distinct().sorted().map { OptChoice(it.toString(), label(it)) } },
    get = { get(it).toString() },
    set = { s, v -> v.toIntOrNull()?.let { set(s, it) } ?: s },
    help = help, preview = preview, visible = visible,
)

private fun textOpt(
    id: String,
    section: OptSection,
    title: String,
    get: (Settings) -> String,
    set: (Settings, String) -> Settings,
    help: String? = null,
    phoneOnly: Boolean = false,
    visible: (Settings) -> Boolean = { true },
) = Opt(id, section, title, get = get, set = { s, v -> set(s, v.trim()) }, help = help, text = true, phoneOnly = phoneOnly, visible = visible)

private fun minutes(m: Int): String = when {
    m == 0 -> "Never"
    m < 60 -> "$m min"
    m % 1440 == 0 -> if (m == 1440) "Once a day" else "${m / 1440} days"
    m % 60 == 0 -> if (m == 60) "1 hour" else "${m / 60} hours"
    else -> "${m / 60} hr ${m % 60} min"
}

fun hourName(h: Int): String = when (h) {
    0 -> "12 AM"
    12 -> "12 PM"
    in 1..11 -> "$h AM"
    else -> "${h - 12} PM"
}

private inline fun Settings.withLook(f: (LookConfig) -> LookConfig) = copy(look = f(look))
private inline fun Settings.withBg(f: (BackgroundConfig) -> BackgroundConfig) = copy(background = f(background))
private inline fun Settings.alarmCfg(f: (AlarmConfig) -> AlarmConfig) = copy(alarm = f(alarm))
private inline fun Settings.briefCfg(f: (BriefConfig) -> BriefConfig) = copy(brief = f(brief))
private inline fun Settings.sportsCfg(f: (SportsConfig) -> SportsConfig) = copy(sports = f(sports))
private inline fun Settings.care(f: (ArtCare) -> ArtCare) = copy(artCare = f(artCare))

object Options {
    private val L = OptSection.Look
    private val H = OptSection.Home
    private val B = OptSection.Background
    private val A = OptSection.Art
    private val AL = OptSection.Alarm
    private val AS = OptSection.AlarmScreen
    private val BR = OptSection.Briefing
    private val SP = OptSection.Sports
    private val SA = OptSection.Alerts

    private val pictureKinds = setOf(BgKind.Photo, BgKind.Daily, BgKind.Art, BgKind.Posters, BgKind.NowPlaying)
    private val rotatingKinds = setOf(BgKind.Photo, BgKind.Art, BgKind.Posters)

    val all: List<Opt> = listOf(
        // ---- look
        enumOpt("look.theme", L, "Colour scheme", ThemePreset.values(), { it.label }, { it.look.theme }, { s, v -> s.withLook { l -> l.copy(theme = v) } }, preview = Preview.Look),
        enumOpt(
            "look.accent", L, "Accent colour", Accent.values(), { it.label }, { it.look.accent }, { s, v -> s.withLook { l -> l.copy(accent = v) } },
            help = "For things that are on or chosen: lights, the alarm, the selected scene", preview = Preview.Look,
        ),
        enumOpt(
            "look.headingFont", L, "Heading font", HeadingFont.values(), { it.label }, { it.look.headingFont },
            { s, v -> s.withLook { l -> l.copy(headingFont = v) } }, help = "Clocks, greetings and titles", preview = Preview.Look,
        ),
        enumOpt(
            "look.textFont", L, "Text font", TextFont.values(), { it.label }, { it.look.textFont },
            { s, v -> s.withLook { l -> l.copy(textFont = v) } }, help = "Everything you read or select", preview = Preview.Look,
        ),
        enumOpt("look.textScale", L, "Text size", TextScale.values(), { it.label }, { it.look.textScale }, { s, v -> s.withLook { l -> l.copy(textScale = v) } }, preview = Preview.Look),
        enumOpt("look.corners", L, "Corners", Corners.values(), { it.label }, { it.look.corners }, { s, v -> s.withLook { l -> l.copy(corners = v) } }, preview = Preview.Look),
        enumOpt(
            "look.focus", L, "Focus", FocusLook.values(), { it.label }, { it.look.focus }, { s, v -> s.withLook { l -> l.copy(focus = v) } },
            help = "How the thing you're on stands out", preview = Preview.Look,
        ),
        enumOpt("look.zoom", L, "Zoom on focus", Zoom.values(), { it.label }, { it.look.zoom }, { s, v -> s.withLook { l -> l.copy(zoom = v) } }, preview = Preview.Look),
        enumOpt(
            "look.motion", L, "Motion", Motion.values(), { it.label }, { it.look.motion }, { s, v -> s.withLook { l -> l.copy(motion = v) } },
            help = "Reduced turns off fades and zooms, which also helps slow TV boxes",
        ),

        // ---- home screen
        Opt(
            "home.rows", H, "Rows",
            get = { "" }, set = { s, _ -> s },
            help = "Which rows show, in which order",
            order = OrderSpec(
                HomeRow.values().map { OptChoice(it.name, it.label) },
                get = { s -> s.look.rowOrder.map { it.name } to s.look.rowsShown.map { it.name }.toSet() },
                set = { s, order, shown ->
                    s.withLook { l ->
                        l.copy(
                            rowOrder = completeOrder(order.mapNotNull { n -> HomeRow.values().firstOrNull { it.name == n } }),
                            rowsShown = shown.mapNotNull { n -> HomeRow.values().firstOrNull { it.name == n } }.toSet(),
                        )
                    }
                },
            ),
        ),
        enumOpt("look.favSize", H, "Favorite tiles", TileSize.values(), { it.label }, { it.look.favSize }, { s, v -> s.withLook { l -> l.copy(favSize = v) } }, preview = Preview.Look),
        enumOpt("look.appSize", H, "App tiles", TileSize.values(), { it.label }, { it.look.appSize }, { s, v -> s.withLook { l -> l.copy(appSize = v) } }, preview = Preview.Look),
        enumOpt("look.spacing", H, "Space between things", Spacing.values(), { it.label }, { it.look.spacing }, { s, v -> s.withLook { l -> l.copy(spacing = v) } }, preview = Preview.Look),
        enumOpt("look.appNames", H, "App names", AppNames.values(), { it.label }, { it.look.appNames }, { s, v -> s.withLook { l -> l.copy(appNames = v) } }),
        enumOpt(
            "look.appArt", H, "App pictures", AppArt.values(), { it.label }, { it.look.appArt }, { s, v -> s.withLook { l -> l.copy(appArt = v) } },
            help = "Hold OK on an app to rename it or give it your own picture",
        ),
        enumOpt("look.clock", H, "Clock", ClockStyle.values(), { it.label }, { it.look.clock }, { s, v -> s.withLook { l -> l.copy(clock = v) } }),
        boolOpt("look.showDate", H, "Date next to the clock", { it.look.showDate }, { s, v -> s.withLook { l -> l.copy(showDate = v) } }),
        boolOpt("use24h", H, "24-hour clock", { it.use24h }, { s, v -> s.copy(use24h = v) }),
        boolOpt("look.showWeather", H, "Weather in the top bar", { it.look.showWeather }, { s, v -> s.withLook { l -> l.copy(showWeather = v) } }),
        boolOpt("look.showRoomChip", H, "Room button in the top bar", { it.look.showRoomChip }, { s, v -> s.withLook { l -> l.copy(showRoomChip = v) } }),
        intOpt(
            "artIdleMin", H, "Start art mode", listOf(0, 5, 10, 15, 30, 60),
            { if (it == 0) "Only when I choose it" else "After $it min on the home screen" }, { it.artIdleMin }, { s, v -> s.copy(artIdleMin = v) },
        ),

        // ---- background
        enumOpt("bg.kind", B, "Background", BgKind.values(), { it.label }, { it.background.kind }, { s, v -> s.withBg { b -> b.copy(kind = v) } }, preview = Preview.Background),
        Opt(
            "bg.color", B, "Colour",
            choices = { s ->
                val presets = BgColor.values().map { OptChoice(it.argb.toString(), it.label) }
                val custom = s.background.color
                if (presets.any { it.value == custom.toString() }) presets
                else presets + OptChoice(custom.toString(), "Your colour, " + hex(custom))
            },
            get = { it.background.color.toString() },
            set = { s, v -> v.toLongOrNull()?.let { c -> s.withBg { b -> b.copy(color = c or 0xFF000000) } } ?: s },
            preview = Preview.Background,
            visible = { it.background.kind == BgKind.Color },
            free = true,
        ),
        enumOpt(
            "bg.gradient", B, "Gradient", GradientPreset.values(), { it.label }, { it.background.gradient },
            { s, v -> s.withBg { b -> b.copy(gradient = v) } }, preview = Preview.Background, visible = { it.background.kind == BgKind.Gradient },
        ),
        Opt(
            "bg.photo", B, "Which photo",
            choices = { listOf(OptChoice("", "Take turns")) + OptionSources.backgrounds().map { OptChoice(it, it.substringBeforeLast('.')) } },
            get = { it.background.photo },
            set = { s, v -> s.withBg { b -> b.copy(photo = v) } },
            help = "Add photos from your phone",
            preview = Preview.Background,
            visible = { it.background.kind == BgKind.Photo },
        ),
        enumOpt(
            "bg.posters", B, "Posters of", PosterKind.values(), { it.label }, { it.background.posters },
            { s, v -> s.withBg { b -> b.copy(posters = v) } }, preview = Preview.Background, visible = { it.background.kind == BgKind.Posters },
        ),
        enumOpt(
            "bg.posterStyle", B, "Poster style", PosterStyle.values(), { it.label }, { it.background.posterStyle },
            { s, v -> s.withBg { b -> b.copy(posterStyle = v) } }, preview = Preview.Background, visible = { it.background.kind == BgKind.Posters },
        ),
        textOpt(
            "bg.tmdbKey", B, "TMDB key", { it.background.tmdbKey }, { s, v -> s.withBg { b -> b.copy(tmdbKey = v) } },
            help = "Posters come from The Movie Database. Get a free API key at themoviedb.org (Settings, API) and paste it on your phone.",
            phoneOnly = true, visible = { it.background.kind == BgKind.Posters },
        ),
        intOpt(
            "bg.dim", B, "Darken the picture", listOf(0, 15, 30, 45, 60, 75), { if (it == 0) "Off" else "$it%" },
            { it.background.dimPct }, { s, v -> s.withBg { b -> b.copy(dimPct = v) } },
            help = "So text stays easy to read on top", preview = Preview.Background, visible = { it.background.kind in pictureKinds },
        ),
        enumOpt(
            "bg.blur", B, "Soften the picture", BgBlur.values(), { it.label }, { it.background.blur },
            { s, v -> s.withBg { b -> b.copy(blur = v) } }, preview = Preview.Background, visible = { it.background.kind in pictureKinds },
        ),
        intOpt(
            "bg.every", B, "Change the picture every", listOf(5, 15, 30, 60, 180, 1440), ::minutes,
            { it.background.everyMin }, { s, v -> s.withBg { b -> b.copy(everyMin = v) } },
            visible = { it.background.kind in rotatingKinds && !(it.background.kind == BgKind.Photo && it.background.photo.isNotBlank()) },
        ),

        // ---- art mode
        enumOpt("artSource", A, "Show", ArtSource.values(), { it.label }, { it.artSource }, { s, v -> s.copy(artSource = v) }),
        enumOpt("art.style", A, "Style", ArtStyle.values(), { it.label }, { it.artCare.style }, { s, v -> s.care { c -> c.copy(style = v) } }),
        intOpt("artIntervalMin", A, "Change the picture every", listOf(1, 5, 15, 30, 60), ::minutes, { it.artIntervalMin }, { s, v -> s.copy(artIntervalMin = v) }),
        enumOpt(
            "art.panel", A, "TV type", TvPanel.values(), { it.label }, { it.artCare.panel }, { s, v -> s.care { c -> c.copy(panel = v) } },
            help = "OLED screens get stronger burn-in protection",
        ),
        intOpt("art.dim", A, "Dim the art", listOf(0, 15, 30, 45), { if (it == 0) "Off" else "$it% darker" }, { it.artCare.dimPct }, { s, v -> s.care { c -> c.copy(dimPct = v) } }),
        intOpt(
            "art.quietStart", A, "Quiet hours start", (0..23).toList(), ::hourName, { it.artCare.quietStart },
            { s, v -> s.care { c -> c.copy(quietStart = v) } }, help = "The same start and end turns quiet hours off",
        ),
        intOpt("art.quietEnd", A, "Quiet hours end", (0..23).toList(), ::hourName, { it.artCare.quietEnd }, { s, v -> s.care { c -> c.copy(quietEnd = v) } }),
        enumOpt(
            "art.quietMode", A, "During quiet hours", QuietMode.values(), { it.label }, { it.artCare.quietMode },
            { s, v -> s.care { c -> c.copy(quietMode = v) } }, visible = { it.artCare.quietEnabled },
        ),
        intOpt(
            "art.presenceDelay", A, "Go black after nobody's there for", listOf(5, 10, 20, 30), { "$it min" }, { it.artCare.presenceDelayMin },
            { s, v -> s.care { c -> c.copy(presenceDelayMin = v) } }, visible = { it.artCare.presenceEntity.isNotBlank() },
        ),
        intOpt(
            "art.maxHours", A, "Time limit", listOf(0, 2, 4, 8), { if (it == 0) "No limit" else "Black after $it hours" },
            { it.artCare.maxHours }, { s, v -> s.care { c -> c.copy(maxHours = v) } },
        ),

        // ---- alarm
        enumOpt(
            "alarm.sound", AL, "Sound", AlarmSound.values(), { it.label }, { it.alarm.sound }, { s, v -> s.alarmCfg { a -> a.copy(sound = v) } },
            preview = Preview.Sound,
        ),
        intOpt("alarm.volume", AL, "Loudest volume", listOf(25, 50, 75, 100), { "$it%" }, { it.alarm.volumePct }, { s, v -> s.alarmCfg { a -> a.copy(volumePct = v) } }),
        intOpt(
            "alarm.ramp", AL, "Gentle start", listOf(0, 15, 30, 60, 120),
            { if (it == 0) "Off, full volume at once" else if (it < 60) "Gets louder over $it seconds" else "Gets louder over ${it / 60} min" },
            { it.alarm.rampSec }, { s, v -> s.alarmCfg { a -> a.copy(rampSec = v) } },
        ),
        intOpt("alarm.snooze", AL, "Snooze length", listOf(5, 9, 10, 15, 20), { "$it min" }, { it.alarm.snoozeMin }, { s, v -> s.alarmCfg { a -> a.copy(snoozeMin = v) } }),
        intOpt(
            "alarm.maxSnoozes", AL, "Snoozes", listOf(-1, 0, 1, 2, 3, 5),
            { when (it) { -1 -> "As many as you like"; 0 -> "No snooze"; 1 -> "Once"; else -> "Up to $it times" } },
            { it.alarm.maxSnoozes }, { s, v -> s.alarmCfg { a -> a.copy(maxSnoozes = v) } },
        ),
        enumOpt(
            "alarm.stopWith", AL, "To stop it", StopMethod.values(), { it.label }, { it.alarm.stopWith },
            { s, v -> s.alarmCfg { a -> a.copy(stopWith = v) } }, help = "Holding or solving makes it harder to switch off half asleep",
        ),
        enumOpt("alarm.after", AL, "After stopping", AfterAlarm.values(), { it.label }, { it.alarm.after }, { s, v -> s.alarmCfg { a -> a.copy(after = v) } }),
        intOpt("alarm.ringMin", AL, "Ring for", listOf(1, 3, 5, 10, 15, 30), { "$it min" }, { it.alarm.ringMin }, { s, v -> s.alarmCfg { a -> a.copy(ringMin = v) } }),
        enumOpt("alarm.ifIgnored", AL, "If nobody answers", IfIgnored.values(), { it.label }, { it.alarm.ifIgnored }, { s, v -> s.alarmCfg { a -> a.copy(ifIgnored = v) } }),
        intOpt(
            "alarm.sunrise", AL, "Sunrise lights", listOf(0, 5, 10, 15, 20, 30),
            { if (it == 0) "Off" else "The room's lights fade in over $it min" }, { it.alarm.sunriseMin }, { s, v -> s.alarmCfg { a -> a.copy(sunriseMin = v) } },
        ),
        enumOpt("alarm.background", AS, "Background", AlarmBackground.values(), { it.label }, { it.alarm.background }, { s, v -> s.alarmCfg { a -> a.copy(background = v) } }),
        enumOpt("alarm.greeting", AS, "Greeting", GreetingStyle.values(), { it.label }, { it.alarm.greeting }, { s, v -> s.alarmCfg { a -> a.copy(greeting = v) } }),
        textOpt(
            "alarm.message", AS, "Your words", { it.alarm.message }, { s, v -> s.alarmCfg { a -> a.copy(message = v) } },
            visible = { it.alarm.greeting == GreetingStyle.Custom },
        ),
        boolOpt("alarm.showDate", AS, "Show the date", { it.alarm.showDate }, { s, v -> s.alarmCfg { a -> a.copy(showDate = v) } }),
        boolOpt("alarm.showWeather", AS, "Show the weather", { it.alarm.showWeather }, { s, v -> s.alarmCfg { a -> a.copy(showWeather = v) } }),

        // ---- morning briefing
        enumOpt("brief.when", BR, "Show it", BriefWhen.values(), { it.label }, { it.brief.showWhen }, { s, v -> s.briefCfg { b -> b.copy(showWhen = v) } }),
        Opt(
            "brief.sections", BR, "What it shows",
            get = { "" }, set = { s, _ -> s },
            help = "Turn parts on or off and put them in order",
            order = OrderSpec(
                BriefSection.values().map { OptChoice(it.name, it.label) },
                get = { s -> s.brief.order.map { it.name } to s.brief.shown.map { it.name }.toSet() },
                set = { s, order, shown ->
                    s.briefCfg { b ->
                        b.copy(
                            order = completeOrder(order.mapNotNull { n -> BriefSection.values().firstOrNull { it.name == n } }),
                            shown = shown.mapNotNull { n -> BriefSection.values().firstOrNull { it.name == n } }.toSet(),
                        )
                    }
                },
            ),
        ),
        enumOpt("brief.layout", BR, "Layout", BriefLayout.values(), { it.label }, { it.brief.layout }, { s, v -> s.briefCfg { b -> b.copy(layout = v) } }),
        enumOpt("brief.greeting", BR, "Greeting", GreetingStyle.values(), { it.label }, { it.brief.greeting }, { s, v -> s.briefCfg { b -> b.copy(greeting = v) } }),
        textOpt(
            "brief.message", BR, "Your words", { it.brief.message }, { s, v -> s.briefCfg { b -> b.copy(message = v) } },
            visible = { it.brief.greeting == GreetingStyle.Custom },
        ),
        enumOpt("brief.background", BR, "Background", BriefBackground.values(), { it.label }, { it.brief.background }, { s, v -> s.briefCfg { b -> b.copy(background = v) } }),
        intOpt(
            "brief.headlines", BR, "Headlines", listOf(0, 1, 2, 3, 4, 5), { if (it == 0) "None" else "$it" },
            { it.brief.headlines }, { s, v -> s.briefCfg { b -> b.copy(headlines = v) } },
        ),
        boolOpt(
            "brief.readAloud", BR, "Read it aloud", { it.brief.readAloud }, { s, v -> s.briefCfg { b -> b.copy(readAloud = v) } },
            help = "Uses the TV's text-to-speech voice",
        ),
        enumOpt("brief.close", BR, "Closing it", BriefClose.values(), { it.label }, { it.brief.closeWith }, { s, v -> s.briefCfg { b -> b.copy(closeWith = v) } }),
        intOpt(
            "brief.autoClose", BR, "Close by itself after", listOf(0, 5, 10, 20, 30, 60), { if (it == 0) "Never" else "$it min" },
            { it.brief.autoCloseMin }, { s, v -> s.briefCfg { b -> b.copy(autoCloseMin = v) } },
        ),

        // ---- sports
        enumOpt("sports.layout", SP, "Your teams' games", SportsLayout.values(), { it.label }, { it.sports.layout }, { s, v -> s.sportsCfg { c -> c.copy(layout = v) } }),
        boolOpt(
            "sports.noSpoilers", SP, "Hide scores", { it.sports.noSpoilers }, { s, v -> s.sportsCfg { c -> c.copy(noSpoilers = v) } },
            help = "No spoilers on the home screen, the briefing or alerts. Hold OK on a game to see its score",
        ),
        boolOpt("sports.logos", SP, "Team logos", { it.sports.logos }, { s, v -> s.sportsCfg { c -> c.copy(logos = v) } }),
        boolOpt("sports.teamColors", SP, "Team colours on cards", { it.sports.teamColors }, { s, v -> s.sportsCfg { c -> c.copy(teamColors = v) } }),
        boolOpt("sports.board", SP, "Today's games in every league", { it.sports.board }, { s, v -> s.sportsCfg { c -> c.copy(board = v) } }),
        enumOpt("sports.onSelect", SP, "Selecting a game", GameAction.values(), { it.label }, { it.sports.onSelect }, { s, v -> s.sportsCfg { c -> c.copy(onSelect = v) } }),
        boolOpt("sports.homeTile", SP, "Next game on the home screen", { it.sports.homeTile }, { s, v -> s.sportsCfg { c -> c.copy(homeTile = v) } }),
        boolOpt(
            "sports.alerts", SA, "Score alerts", { it.sports.alerts }, { s, v -> s.sportsCfg { c -> c.copy(alerts = v) } },
            help = "Your teams' scores over any app",
        ),
        boolOpt("sports.alertStart", SA, "When a game starts", { it.sports.alertStart }, { s, v -> s.sportsCfg { c -> c.copy(alertStart = v) } }, visible = { it.sports.alerts }),
        boolOpt("sports.alertScore", SA, "When someone scores", { it.sports.alertScore }, { s, v -> s.sportsCfg { c -> c.copy(alertScore = v) } }, visible = { it.sports.alerts }),
        boolOpt("sports.alertFinal", SA, "Final scores", { it.sports.alertFinal }, { s, v -> s.sportsCfg { c -> c.copy(alertFinal = v) } }, visible = { it.sports.alerts }),
        enumOpt(
            "sports.alertCorner", SA, "Where alerts appear", AlertCorner.values(), { it.label }, { it.sports.alertCorner },
            { s, v -> s.sportsCfg { c -> c.copy(alertCorner = v) } }, visible = { it.sports.alerts },
        ),
        intOpt(
            "sports.alertSeconds", SA, "Alerts stay for", listOf(5, 8, 15, 30), { "$it seconds" }, { it.sports.alertSeconds },
            { s, v -> s.sportsCfg { c -> c.copy(alertSeconds = v) } }, visible = { it.sports.alerts },
        ),
        enumOpt(
            "sports.alertSize", SA, "Alert size", AlertSize.values(), { it.label }, { it.sports.alertSize },
            { s, v -> s.sportsCfg { c -> c.copy(alertSize = v) } }, visible = { it.sports.alerts },
        ),
        boolOpt(
            "sports.alertSound", SA, "Alert sound", { it.sports.alertSound }, { s, v -> s.sportsCfg { c -> c.copy(alertSound = v) } },
            visible = { it.sports.alerts },
        ),
    )

    private val byId = all.associateBy { it.id }

    operator fun get(id: String): Opt? = byId[id]

    fun of(section: OptSection): List<Opt> = all.filter { it.section == section }

    fun hex(argb: Long): String = "#" + (argb and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()
}
