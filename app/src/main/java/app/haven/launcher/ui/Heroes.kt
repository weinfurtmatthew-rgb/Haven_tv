package app.haven.launcher.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.haven.launcher.Haven
import app.haven.launcher.Screen
import app.haven.launcher.bg.DepthMask
import app.haven.launcher.data.GreetingStyle
import app.haven.launcher.data.HeroKind
import app.haven.launcher.data.SpotClock
import app.haven.launcher.data.TvApp
import app.haven.launcher.ha.HaEntity
import app.haven.launcher.media.MediaItem
import app.haven.launcher.media.MediaKind
import app.haven.launcher.media.MediaList
import app.haven.launcher.sports.Game
import app.haven.launcher.sports.GameState
import app.haven.launcher.sports.Watch
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt

/** The top of a page below the clock bar (Spotlight is drawn by the page itself, around its first row). */
@Composable
fun Hero(h: HomeCtx, hero: HeroKind) {
    when (hero) {
        HeroKind.ClockBar, HeroKind.Spotlight -> Unit
        HeroKind.Scoreboard -> ScoreboardHero(h)
        HeroKind.Movie -> FeatureHero(h, MediaKind.Movie)
        HeroKind.Show -> FeatureHero(h, MediaKind.Show)
        HeroKind.NowPlaying -> NowPlayingHero(h)
        HeroKind.HomeStatus -> HomeStatusHero(h)
        HeroKind.Welcome -> WelcomeHero(h)
    }
}

/** A hero's main button, for where focus starts when no row has anything. */
fun heroKey(hero: HeroKind): String? = when (hero) {
    HeroKind.Scoreboard, HeroKind.Movie, HeroKind.Show -> "hero:main"
    HeroKind.NowPlaying -> "hero:play"
    HeroKind.HomeStatus -> "hero:status:0"
    else -> null
}

/**
 * Where a page starts when its hero is the point of it: the game on now, the song playing, the featured movie or
 * show (which moves on while nobody's pressing anything).
 */
fun heroStart(hero: HeroKind): String? = when (hero) {
    HeroKind.Scoreboard, HeroKind.NowPlaying, HeroKind.Movie, HeroKind.Show -> heroKey(hero)
    else -> null
}

/** Small capitals over a hero's title ("LIVE · NFL", "TRENDING THIS WEEK"). */
@Composable
private fun Eyebrow(text: String, color: androidx.compose.ui.graphics.Color = C.Lamp) {
    Txt(text.uppercase(), T.Label.copy(letterSpacing = 2.sp), color = color)
}

// ---- Game day ----------------------------------------------------------------------------------------------

@Composable
private fun ScoreboardHero(h: HomeCtx) {
    val ctx = LocalContext.current
    val games by Haven.sports.games.collectAsState()
    val cfg = h.s.sports
    val g = games.firstOrNull { it.state == GameState.Live } ?: games.firstOrNull { it.state == GameState.Scheduled } ?: games.firstOrNull()
    var reveal by remember(g?.id) { mutableStateOf(false) }
    val show = !cfg.noSpoilers || reveal
    Box(Modifier.fillMaxWidth().height(540.dp)) {
        if (g != null && cfg.teamColors) TeamGlow(g)
        Column(Modifier.fillMaxSize().padding(start = h.start, end = h.end, top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (cfg.teams.isEmpty() || g == null) {
                Spacer(height = 80.dp)
                Txt(if (cfg.teams.isEmpty()) "Follow your teams" else "No games coming up", T.Headline, size = 72)
                Txt(
                    if (cfg.teams.isEmpty()) "Pick them on your phone and their games, scores and channels show up here."
                    else "Your teams' next games show here as soon as they're scheduled.",
                    T.Body, Modifier.padding(top = 16.dp), C.Stone, maxLines = 2, align = TextAlign.Center,
                )
                Box(Modifier.padding(top = 40.dp)) {
                    Chip(
                        if (cfg.teams.isEmpty()) "Pick teams" else "All scores",
                        { if (cfg.teams.isEmpty()) openSettings(h, "Sports") else h.nav.go(Screen.Sports) },
                        icon = Icons.Trophy, focusRequester = h.req("hero:main"), onFocus = h.track("hero:main"), glass = Look.glass,
                    )
                }
                return@Column
            }
            val live = g.state == GameState.Live
            val stateWord = when (g.state) {
                GameState.Live -> "Live"
                GameState.Final -> "Final"
                GameState.Postponed -> "Postponed"
                GameState.Scheduled -> "Next game"
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (live) Box(Modifier.size(14.dp).clip(CircleShape).background(C.Clay))
                Eyebrow("$stateWord · ${g.league.label}", if (live) C.Clay else C.Lamp)
            }
            Row(Modifier.padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(64.dp)) {
                TeamBlock(g.first.team, cfg.logos)
                Box(Modifier.width(620.dp), contentAlignment = Alignment.Center) {
                    if (g.state != GameState.Scheduled && show) {
                        Txt("${g.first.score ?: ""} – ${g.second.score ?: ""}", T.Display, size = 200, align = TextAlign.Center)
                    } else if (g.state == GameState.Scheduled) {
                        Txt(gameStatus(g, h.s.use24h), T.Headline, size = 72, align = TextAlign.Center, maxLines = 2)
                    } else {
                        Txt("Score hidden", T.Headline, size = 64, color = C.Stone, align = TextAlign.Center)
                    }
                }
                TeamBlock(g.second.team, cfg.logos)
            }
            val detail = buildList {
                if (live && show) add(g.detail)
                if (g.state == GameState.Scheduled) countdown(g.start)?.let { add(it) }
                g.broadcasts.firstOrNull()?.let { add("on $it") }
            }.filter { it.isNotBlank() }.joinToString(" · ")
            if (detail.isNotBlank()) Txt(detail, T.Body, Modifier.padding(top = 8.dp), C.Stone)
            Row(Modifier.padding(top = 32.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Chip(
                    "Watch", { Watch.open(ctx, g)?.let { ways -> h.show(HomeDialog.WatchGame(g, ways)) } },
                    icon = Icons.Play, focusRequester = h.req("hero:main"), onFocus = h.track("hero:main"), glass = Look.glass,
                )
                Chip("All scores", { h.nav.go(Screen.Sports) }, icon = Icons.Trophy, focusRequester = h.req("hero:scores"), onFocus = h.track("hero:scores"), glass = Look.glass)
                if (cfg.noSpoilers && g.state != GameState.Scheduled) {
                    Chip(if (reveal) "Hide the score" else "Show the score", { reveal = !reveal }, icon = Icons.Eye, glass = Look.glass)
                }
            }
        }
    }
}

/** "in 2 h 10 min", "in 25 min", or null once it's started. */
fun countdown(start: Instant): String? {
    val m = Duration.between(Instant.now(), start).toMinutes()
    return when {
        m <= 0 -> null
        m < 60 -> "in $m min"
        m < 24 * 60 -> "in ${m / 60} h ${m % 60} min"
        else -> null
    }
}

@Composable
private fun TeamBlock(team: app.haven.launcher.sports.Team, logos: Boolean) {
    Column(Modifier.width(300.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (logos && team.logo.isNotBlank()) {
            TeamLogo(team, 176.dp)
        } else {
            Box(Modifier.size(176.dp).clip(CircleShape).background(teamColor(team) ?: C.Raised), contentAlignment = Alignment.Center) {
                Txt(team.abbr, T.Headline, color = Color.White)
            }
        }
        Txt(team.name, T.CardTitle, color = C.Linen, maxLines = 2, align = TextAlign.Center)
    }
}

/** The two teams' colours glowing behind the scoreboard. */
@Composable
private fun TeamGlow(g: Game) {
    val left = teamColor(g.first.team) ?: C.Lamp
    val right = teamColor(g.second.team) ?: C.Clay
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.radialGradient(listOf(left.copy(alpha = 0.35f), Color.Transparent), center = Offset(size.width * 0.2f, size.height * 0.45f), radius = size.width * 0.35f))
        drawRect(Brush.radialGradient(listOf(right.copy(alpha = 0.35f), Color.Transparent), center = Offset(size.width * 0.8f, size.height * 0.45f), radius = size.width * 0.35f))
    }
}

// ---- Cinema and Shows -------------------------------------------------------------------------------------

/** The featured movie (trending) or show (new tonight from the ones followed, else on tonight), changing every few seconds. */
@Composable
private fun FeatureHero(h: HomeCtx, kind: MediaKind) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val lists by Haven.media.lists.collectAsState()
    val items = if (kind == MediaKind.Movie) {
        lists[MediaList.TrendingMovies].orEmpty().filter { it.backdrop != null }.take(8)
    } else {
        val mine = lists[MediaList.YourShows].orEmpty().filter { it.badge.isNotBlank() }
        (mine + lists[MediaList.Tonight].orEmpty().take(6)).distinctBy { it.key }.take(8)
    }
    var index by remember { mutableIntStateOf(0) }
    var holding by remember { mutableStateOf(false) }
    val heldAt = remember { LongArray(1) }
    // A new one every 12 seconds. With focus on it, only after half a minute with no key pressed and no move onto
    // it, so what you're about to pick doesn't change under you.
    LaunchedEffect(items.size) {
        while (items.size > 1 && !Look.reduced) {
            delay(12_000)
            val quiet = SystemClock.uptimeMillis() - maxOf(HomeFocus.lastKeyAt, heldAt[0])
            if (!holding || quiet > 30_000) index = (index + 1) % items.size
        }
    }
    val item = items.getOrNull(index % maxOf(1, items.size))
    val hold: (Boolean) -> Unit = {
        holding = it
        if (it) heldAt[0] = SystemClock.uptimeMillis()
    }
    Box(Modifier.fillMaxWidth().height(640.dp)) {
        if (item == null) {
            Column(Modifier.padding(start = h.start, top = 80.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (kind == MediaKind.Movie && h.s.background.tmdbKey.isBlank()) {
                    Txt("Movies, as they come out", T.Headline, size = 72)
                    Txt("Add your free TMDB key on the phone page to see what's trending, in theaters and coming soon.", T.Body, color = C.Stone, maxLines = 2)
                    Box(Modifier.padding(top = 24.dp)) {
                        Chip("How to add it", { openSettings(h, "HomeScreen") }, icon = Icons.Smartphone, focusRequester = h.req("hero:main"), onFocus = h.track("hero:main"))
                    }
                } else {
                    Txt(if (kind == MediaKind.Movie) "Movies" else "Tonight", T.Headline, size = 72)
                    Txt(if (kind == MediaKind.Movie) "Loading what's trending…" else "Loading tonight's schedule…", T.Body, color = C.Stone)
                }
            }
            return@Box
        }
        val wide by produceState<String?>(item.backdrop, item.key) {
            if (item.backdrop == null && item.showId != null) value = Haven.media.showBackground(item.showId)
        }
        Crossfade(targetState = wide ?: item.poster, animationSpec = motion(900), label = "feature") { url ->
            Box(Modifier.fillMaxSize()) {
                if (url != null) {
                    AsyncImage(
                        url, null,
                        if (wide != null) Modifier.fillMaxSize() else Modifier.align(Alignment.CenterEnd).padding(end = h.end).width(400.dp).fillMaxHeight(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
        // Darken towards the text and the rows, so both read over any picture.
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to C.Hearth, 0.45f to C.Hearth.copy(alpha = 0.75f), 0.8f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to C.Hearth)))
        Column(Modifier.padding(start = h.start, top = 64.dp).width(1000.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Eyebrow(
                when {
                    kind == MediaKind.Movie -> "Trending this week"
                    item.badge.isNotBlank() -> item.badge
                    else -> "On tonight"
                },
            )
            Txt(item.title, T.Headline, size = 96, maxLines = 2)
            val meta = if (kind == MediaKind.Movie) {
                listOfNotNull(item.year.ifBlank { null }, item.genres.take(2).joinToString(", ").ifBlank { null }, item.rating?.let { "★ %.1f".format(it) })
            } else {
                listOfNotNull(item.note.ifBlank { null }, item.genres.take(2).joinToString(", ").ifBlank { null })
            }
            if (meta.isNotEmpty()) Txt(meta.joinToString(" · "), T.Body, color = C.Linen)
            if (item.overview.isNotBlank()) Txt(item.overview, T.Body, color = C.Stone, maxLines = 2)
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (kind == MediaKind.Movie) {
                    Chip("Where to watch", { h.show(HomeDialog.Title(item)) }, icon = Icons.Tv, focusRequester = h.req("hero:main"), onFocus = { h.track("hero:main")(it); hold(it) }, glass = Look.glass)
                    Chip("Trailer", {
                        scope.launch {
                            val key = Haven.media.details(item)?.trailer
                            if (key == null) Toast.makeText(ctx, "No trailer for ${item.title}", Toast.LENGTH_SHORT).show() else openTrailer(ctx, key)
                        }
                    }, icon = Icons.Play, focusRequester = h.req("hero:trailer"), onFocus = { h.track("hero:trailer")(it); hold(it) }, glass = Look.glass)
                    movieScene(h)?.let { scene ->
                        Chip("Lights down", { scope.launch { Haven.ha.activate(scene) } }, icon = Icons.Lightbulb, onFocus = hold, glass = Look.glass)
                    }
                } else {
                    Chip("Watch", { watchShow(ctx, item) }, icon = Icons.Play, focusRequester = h.req("hero:main"), onFocus = { h.track("hero:main")(it); hold(it) }, glass = Look.glass)
                    Chip("Details", { h.show(HomeDialog.Title(item)) }, icon = Icons.Info, onFocus = hold, glass = Look.glass)
                }
            }
        }
    }
}

/** A scene that sounds like movie night ("Movie night", "Cinema", "TV time"), for the Lights down button. */
private fun movieScene(h: HomeCtx): HaEntity? =
    Haven.ha.scenesFor(h.s).firstOrNull { Regex("movie|cinema|film|tv|theater|theatre", RegexOption.IGNORE_CASE).containsMatchIn(it.name) }

/** Opens a trailer in YouTube (the app on the TV, or the web as a fallback). */
fun openTrailer(ctx: android.content.Context, key: String) {
    val app = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$key")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$key")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { ctx.startActivity(app) }.isFailure) runCatching { ctx.startActivity(web) }
}

/** A show's channel or service: its app when the TV has one, otherwise live TV. */
fun watchShow(ctx: android.content.Context, item: MediaItem) {
    val apps = Haven.apps.apps.value
    val app = apps.firstOrNull { item.network.isNotBlank() && app.haven.launcher.media.MediaParse.sameService(item.network, it.label) }
    val target = app ?: Haven.apps.provider()
    if (target == null) {
        Toast.makeText(ctx, "${item.title} is on ${item.network.ifBlank { "TV" }}", Toast.LENGTH_LONG).show()
        return
    }
    Haven.apps.launch(target)
    if (app == null && item.network.isNotBlank()) Toast.makeText(ctx, "${item.title} is on ${item.network}. Find it in the guide.", Toast.LENGTH_LONG).show()
}

// ---- Music --------------------------------------------------------------------------------------------------

@Composable
private fun NowPlayingHero(h: HomeCtx) {
    Haven.ha.entities.collectAsState().value
    val scope = rememberCoroutineScope()
    val e = leadSpeaker(h)
    Box(Modifier.fillMaxWidth().height(600.dp)) {
        if (e == null) {
            Column(Modifier.padding(start = h.start, top = 96.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Txt("Nothing's playing", T.Headline, size = 80)
                Txt("Start something in a music app, or turn on a speaker below.", T.Body, color = C.Stone)
            }
            return@Box
        }
        Row(Modifier.padding(start = h.start, end = h.end, top = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(480.dp).clip(L.panelShape).background(C.Raised), contentAlignment = Alignment.Center) {
                val pic = Haven.ha.picture(e, h.s)
                if (pic != null) AsyncImage(pic, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Music, 160.dp, C.Stone)
            }
            Column(Modifier.padding(start = 72.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Eyebrow((if (e.state == "playing") "Playing on " else "Paused on ") + e.name)
                Txt(e.attrs.optString("media_title").ifBlank { e.attrs.optString("app_name").ifBlank { "Playing" } }, T.Headline, size = 88, maxLines = 2)
                val artist = e.attrs.optString("media_artist")
                if (artist.isNotBlank()) Txt(artist, T.Title, color = C.Stone)
                val album = e.attrs.optString("media_album_name")
                if (album.isNotBlank()) Txt(album, T.Body, color = C.Dust)
                Progress(e)
                Row(Modifier.padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    IconButton(Icons.SkipBack, { scope.launch { Haven.ha.media(e, "media_previous_track") } }, size = 88.dp, glass = Look.glass)
                    IconButton(
                        if (e.state == "playing") Icons.Pause else Icons.Play, { scope.launch { Haven.ha.media(e, "media_play_pause") } },
                        size = 120.dp, focusRequester = h.req("hero:play"), onFocus = h.track("hero:play"), glass = Look.glass,
                    )
                    IconButton(Icons.SkipForward, { scope.launch { Haven.ha.media(e, "media_next_track") } }, size = 88.dp, glass = Look.glass)
                    if (e.canSetVolume) {
                        Spacer(width = 40.dp)
                        IconButton(Icons.Minus, { scope.launch { Haven.ha.setVolume(e, (e.volume ?: 0f) - 0.05f) } }, size = 72.dp, glass = Look.glass)
                        LevelBar(e.volume ?: 0f, Modifier.width(220.dp))
                        IconButton(Icons.Plus, { scope.launch { Haven.ha.setVolume(e, (e.volume ?: 0f) + 0.05f) } }, size = 72.dp, glass = Look.glass)
                    }
                }
            }
        }
    }
}

/** How far into the song, from Home Assistant's position and when it was reported, ticking while it plays. */
@Composable
private fun Progress(e: HaEntity) {
    val duration = e.attrs.optDouble("media_duration", Double.NaN)
    if (duration.isNaN() || duration <= 0) return
    val pos = e.attrs.optDouble("media_position", 0.0)
    val at = runCatching { Instant.parse(e.attrs.optString("media_position_updated_at").replace("+00:00", "Z")) }.getOrNull()
    val now by produceState(Instant.now(), e.state) {
        while (e.state == "playing") {
            value = Instant.now()
            delay(1000)
        }
    }
    val elapsed = if (e.state == "playing" && at != null) pos + Duration.between(at, now).toMillis() / 1000.0 else pos
    val t = elapsed.coerceIn(0.0, duration)
    fun mmss(s: Double) = "%d:%02d".format((s / 60).toInt(), (s % 60).toInt())
    Column(Modifier.padding(top = 16.dp).width(1000.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LevelBar((t / duration).toFloat(), Modifier.fillMaxWidth())
        Row {
            Txt(mmss(t), T.Label, color = C.Stone)
            Box(Modifier.weight(1f))
            Txt(mmss(duration), T.Label, color = C.Stone)
        }
    }
}

// ---- Smart home ----------------------------------------------------------------------------------------------

private class Status(val key: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String, val selected: Boolean, val onClick: () -> Unit)

@Composable
private fun HomeStatusHero(h: HomeCtx) {
    Haven.ha.entities.collectAsState().value
    val scope = rememberCoroutineScope()
    val now by rememberNow()
    val ha = Haven.ha
    val s = h.s
    val items = buildList {
        ha.weather(s)?.takeIf { it.available }?.let { w ->
            val t = w.attrs.optDouble("temperature")
            add(Status("weather", weatherIcon(w.state), "${degrees(t)} ${weatherLabel(w.state)}", false) { h.nav.go(Screen.Briefing) })
        }
        val lights = ha.lightsFor(s)
        if (lights.isNotEmpty()) {
            val on = lights.count { it.isOn }
            add(Status("lights", Icons.Lightbulb, if (on == 0) "Lights off" else "$on ${if (on == 1) "light" else "lights"} on", on > 0) { h.nav.go(Screen.Lights) })
        }
        for (e in ha.devices(s, "lock", "cover").take(4)) {
            val (icon, words, _) = lockLook(e)
            add(Status(e.id, icon, "${e.name} ${words.lowercase()}", false) { lockAction(h, e, scope) })
        }
        ha.devices(s, "climate").firstOrNull()?.let { c ->
            add(Status(c.id, Icons.Thermometer, "Inside ${degrees(c.attrs.optDouble("current_temperature"))}", false) { h.show(HomeDialog.Device(c.id)) })
        }
        val people = ha.people()
        if (people.isNotEmpty()) {
            val home = people.filter { it.state == "home" }.map { it.name }
            val who = when {
                home.isEmpty() -> "Nobody home"
                home.size == people.size && people.size > 2 -> "Everyone home"
                home.size <= 2 -> home.joinToString(" and ") + " home"
                else -> home.dropLast(1).joinToString(", ") + " and " + home.last() + " home"
            }
            add(Status("people", Icons.UserRound, who, home.isNotEmpty()) {})
        }
    }
    Column(Modifier.fillMaxWidth().padding(start = h.start, end = h.end, top = 24.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Txt(greetingLine(GreetingStyle.WithName, "", s.profileName, now.hour) ?: greeting(now.hour), T.Headline, size = 80)
        if (items.isEmpty()) {
            Txt("Connect Home Assistant to see how the house is.", T.Body, color = C.Stone)
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                for ((i, st) in items.withIndex()) {
                    Chip(st.label, st.onClick, selected = st.selected, icon = st.icon, focusRequester = h.req("hero:status:$i"), onFocus = h.track("hero:status:$i"), glass = Look.glass)
                }
            }
        }
    }
}

// ---- Kids -------------------------------------------------------------------------------------------------------

@Composable
private fun WelcomeHero(h: HomeCtx) {
    val profiles by Haven.settings.profiles.collectAsState()
    val me = profiles.firstOrNull { it.active }
    val now by rememberNow()
    val name = h.s.profileName.ifBlank { me?.name.orEmpty() }
    val bed = h.s.kids.bedtime
    Box(Modifier.fillMaxWidth().height(380.dp)) {
        SkyPicture(now.hour, Modifier.align(Alignment.TopEnd).padding(end = h.end, top = 24.dp).size(420.dp, 300.dp))
        Row(Modifier.padding(start = h.start, top = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            if (me != null) Avatar(name.ifBlank { me.name }, ACCENTS.getValue(me.avatar).bright, 200.dp)
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Txt(if (name.isBlank()) "Hi there!" else "Hi $name!", T.Display, size = 150)
                if (bed >= 0) {
                    val t = LocalTime.of(bed / 60, bed % 60)
                    val words = app.haven.launcher.bedroom.Bedroom.timeLabel(t.hour, t.minute, h.s.use24h)
                    Box(Modifier.clip(L.pill(64.dp)).background(C.Lamp).padding(horizontal = 32.dp, vertical = 12.dp)) {
                        Txt("TV time until $words", T.Title, color = C.Hearth)
                    }
                }
            }
        }
    }
}

/** A sun with its rays by day, a moon and stars by night: drawn, so it suits any colour scheme. */
@Composable
private fun SkyPicture(hour: Int, modifier: Modifier) {
    val day = hour in 6..18
    val sun = C.Sunrise
    val moon = C.Linen
    Canvas(modifier) {
        val c = Offset(size.width * 0.62f, size.height * 0.45f)
        if (day) {
            val r = size.height * 0.22f
            for (i in 0 until 12) {
                val a = Math.toRadians(i * 30.0)
                val from = Offset(c.x + (r * 1.35f * Math.cos(a)).toFloat(), c.y + (r * 1.35f * Math.sin(a)).toFloat())
                val to = Offset(c.x + (r * 1.75f * Math.cos(a)).toFloat(), c.y + (r * 1.75f * Math.sin(a)).toFloat())
                drawLine(sun, from, to, strokeWidth = r * 0.14f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
            drawCircle(sun, r, c)
        } else {
            val r = size.height * 0.26f
            drawCircle(moon, r, c)
            drawCircle(C.Hearth, r * 0.92f, Offset(c.x + r * 0.45f, c.y - r * 0.25f))
            val stars = listOf(0.15f to 0.2f, 0.3f to 0.75f, 0.85f to 0.15f, 0.92f to 0.7f, 0.08f to 0.55f)
            for ((x, y) in stars) drawCircle(moon, size.height * 0.025f, Offset(size.width * x, size.height * y))
        }
    }
}

// ---- Spotlight ----------------------------------------------------------------------------------------------------

/** The app focused in Spotlight's first row, so the photo can follow it. */
object SpotFocus {
    var app: TvApp? by mutableStateOf(null)
}

/**
 * Spotlight's photo, ready to draw: the clock's place, the scenery to draw over the clock, and [stage]: the photo
 * blurred and carried down the whole page, for behind the rows.
 */
private class SpotPhoto(
    val photo: ImageBitmap,
    val frost: ImageBitmap,
    val stage: ImageBitmap,
    val place: DepthMask.Placement,
    val cut: ImageBitmap?,
    val cutX: Int,
    val cutY: Int,
)

private const val SPOT_W = 1920
private const val SPOT_H = 860
private const val CLOCK_SIZE = 260

/**
 * Spotlight (DESIGN.md, "Layouts"): a big photo across the top with the date and a large clock in its sky. When the
 * photo has scenery rising into the sky, the clock sits a little behind it. The first row of apps sits on glass
 * along the photo's bottom; on a streaming app, the photo shows what's popular there instead (with a TMDB key).
 */
@Composable
fun SpotlightHero(h: HomeCtx, chrome: PageChrome, firstRow: (@Composable () -> Unit)?, stage: MutableState<ImageBitmap?>) {
    val ctx = LocalContext.current
    val look = h.s.look
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val now by rememberNow()
    val use24 = h.s.use24h || DateFormat.is24HourFormat(ctx)
    val time = formatTime(now, use24)
    val clockStyle = T.Display.copy(fontSize = (CLOCK_SIZE * Look.r.type.factorOf(T.Display)).sp)
    val measured = measurer.measure(time, clockStyle)
    val clockW = (measured.size.width / density.density).roundToInt()
    val capH = (CLOCK_SIZE * Look.r.type.factorOf(T.Display) * 0.72f).roundToInt()
    val firstBaseline = measured.firstBaseline / density.density
    val behind = look.spotClock == SpotClock.Behind
    val turn = remember { (0..999).random() }

    var spotW by remember { mutableIntStateOf(SPOT_W) }
    val spot by produceState<SpotPhoto?>(null, look.spotSource, behind, time.length, capH, h.s.background, spotW) {
        while (true) {
            val src = runCatching { Haven.backgrounds.spotlight(look.spotSource, h.s, turn) }.getOrNull()
            val bmp = src?.let { runCatching { Haven.backgrounds.loadCrop(it, spotW, SPOT_H) }.getOrNull() }
            if (bmp != null) value = withContext(Dispatchers.Default) { analyse(bmp, clockW, capH, behind) }
            delay(if (bmp == null) 60_000L else 3 * 3600_000L)
        }
    }
    // On a streaming app in the first row: what's popular there, after a moment so passing over apps doesn't load them all.
    val app = SpotFocus.app
    val service by produceState<MediaItem?>(null, app?.pkg, look.spotFollow) {
        value = null
        if (app == null || !look.spotFollow || h.s.background.tmdbKey.isBlank()) return@produceState
        delay(600)
        value = runCatching { Haven.media.onService(app) }.getOrNull()?.firstOrNull()
    }

    // A streaming app's picture melts into the page the same way, on its own blurred continuation.
    val serviceStage by produceState<ImageBitmap?>(null, service?.backdrop, spotW) {
        value = null
        val url = service?.backdrop ?: return@produceState
        val bmp = runCatching { Haven.backgrounds.loadCrop(url, spotW, SPOT_H) }.getOrNull() ?: return@produceState
        value = withContext(Dispatchers.Default) { stageOf(Glass.frost(bmp.asImageBitmap()), bmp.width) }
    }

    // The page draws the photo's blurred continuation behind everything, so the photo and the rows are one piece.
    LaunchedEffect(spot, serviceStage) { stage.value = serviceStage ?: spot?.stage }
    // Beside the side menu the page is narrower than the screen: the photo is cut to the page's own width.
    Box(
        Modifier.fillMaxWidth().height(SPOT_H.dp).onGloballyPositioned {
            spotW = (it.size.width / density.density).roundToInt().coerceAtLeast(800)
        },
    ) {
        val shown = spot
        Crossfade(targetState = service, animationSpec = motion(700), label = "spot") { svc ->
            Box(Modifier.fillMaxSize()) {
                if (svc?.backdrop != null) {
                    AsyncImage(svc.backdrop, null, Modifier.fillMaxSize().fadeDown(), contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))).fadeDown())
                    Column(Modifier.padding(start = h.start, top = 360.dp).width(1100.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Eyebrow("Popular on ${app?.label.orEmpty()}", Color.White)
                        Txt(svc.title, T.Headline, size = 80, color = Color.White, maxLines = 2)
                    }
                } else if (shown != null) {
                    // The photo melts into its own blurred continuation below it (the page's stage).
                    Image(shown.photo, null, Modifier.fillMaxSize().fadeDown(), contentScale = ContentScale.FillBounds)
                    if (look.spotClock != SpotClock.Hidden) {
                        val baseline = shown.place.baseline
                        val textTop = baseline - firstBaseline
                        Txt(
                            formatDate(now), T.Title, Modifier.fillMaxWidth().offset { IntOffset(0, ((baseline - capH - 76) * density.density).roundToInt()) },
                            color = Color.White.copy(alpha = 0.92f), align = TextAlign.Center, size = 40,
                        )
                        Txt(
                            time, T.Display, Modifier.fillMaxWidth().offset { IntOffset(0, (textTop * density.density).roundToInt()) },
                            color = Color.White.copy(alpha = 0.95f), align = TextAlign.Center, size = CLOCK_SIZE,
                        )
                        if (shown.place.depth && shown.cut != null) {
                            Image(
                                shown.cut, null,
                                Modifier.offset { IntOffset((shown.cutX * density.density).roundToInt(), (shown.cutY * density.density).roundToInt()) }
                                    .size(shown.cut.width.dp, shown.cut.height.dp),
                                contentScale = ContentScale.FillBounds,
                            )
                        }
                    }
                }
            }
        }
        if (chrome == PageChrome.TopBar) Box(Modifier.padding(top = 40.dp)) { TopBar(h, overPhoto = true) }
        if (firstRow != null) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(bottom = 16.dp)) { firstRow() }
        }
    }
}

/**
 * How a Spotlight page scrolls, like Apple TV's: the photo stays whole while focus is on it (its top bar and the dock
 * along its bottom), and moving below it slides it away, so the rows under it have the screen. Further down, rows
 * scroll just enough to show the focused one.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun spotlightScroll(scroll: ScrollState): BringIntoViewSpec {
    val density = LocalDensity.current
    val photo = with(density) { SPOT_H.dp.toPx() }
    val title = with(density) { 100.dp.toPx() }
    return remember(scroll, photo, title) { PastThePhoto(scroll, photo, title) }
}

@OptIn(ExperimentalFoundationApi::class)
private class PastThePhoto(private val scroll: ScrollState, private val photo: Float, private val title: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        spotlightScrollBy(scroll.value.toFloat(), offset, size, containerSize, photo, title, scroll.maxValue.toFloat())
}

/** Fades what this draws away to nothing over its lowest third, so what's behind shows through there. */
private fun Modifier.fadeDown(): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(Brush.verticalGradient(0.68f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
    }

/** The screen's height in design pixels: the stage behind a Spotlight page fills it. */
private const val SCREEN_H = 1080

/**
 * The photo blurred and carried down the whole page: [frost] (the photo, blurred) where the photo is, its bottom edge
 * stretched on below it, darkening towards the bottom so the rows on it read. [photoW] is the photo's width.
 */
private fun stageOf(frost: ImageBitmap, photoW: Int): ImageBitmap {
    val f = frost.asAndroidBitmap()
    val w = f.width
    val photoH = f.height
    val h = maxOf(photoH + 1, (w.toFloat() * SCREEN_H / photoW).roundToInt())
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(out)
    val p = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
    c.drawBitmap(f, null, android.graphics.Rect(0, 0, w, photoH), p)
    c.drawBitmap(f, android.graphics.Rect(0, photoH - 1, w, photoH), android.graphics.Rect(0, photoH, w, h), p)
    val shade = android.graphics.Paint().apply {
        shader = android.graphics.LinearGradient(
            0f, photoH * 0.6f, 0f, h.toFloat(), 0x00000000, 0xA6000000.toInt(), android.graphics.Shader.TileMode.CLAMP,
        )
    }
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), shade)
    return out.asImageBitmap()
}

/** Finds the photo's scenery and places the clock; a no-depth placement when the photo has no sky to use. */
private fun analyse(bmp: Bitmap, clockW: Int, capH: Int, behind: Boolean): SpotPhoto {
    // The small copy keeps the photo's shape: 480 across.
    val fw = bmp.width
    val fh = bmp.height
    val minBase = 190 + capH
    val maxBase = SPOT_H - 330
    var place = DepthMask.Placement(minBase, false)
    var cut: ImageBitmap? = null
    var cx = 0
    var cy = 0
    if (behind) {
        val full = IntArray(fw * fh)
        bmp.getPixels(full, 0, fw, 0, 0, fw, fh)
        val sw = 480
        val sh = maxOf(16, sw * fh / fw)
        val small = Bitmap.createScaledBitmap(bmp, sw, sh, true)
        val spx = IntArray(sw * sh)
        small.getPixels(spx, 0, sw, 0, 0, sw, sh)
        val scene = DepthMask.scene(spx, sw, sh)
        if (scene != null) {
            val edge = DepthMask.edge(scene, full, fw, fh)
            place = DepthMask.place(edge, fw / 2, clockW, capH, minBase, maxBase)
            if (place.depth) {
                val w = clockW + 60
                val ht = capH + 60
                cx = fw / 2 - w / 2
                cy = place.baseline - capH - 30
                val px = DepthMask.cutout(full, fw, fh, scene, edge, cx, cy, w, ht)
                cut = Bitmap.createBitmap(px, w, ht, Bitmap.Config.ARGB_8888).asImageBitmap()
            }
        }
    }
    val photo = bmp.asImageBitmap()
    val frost = Glass.frost(photo)
    return SpotPhoto(photo, frost, stageOf(frost, fw), place, cut, cx, cy)
}
