package app.haven.launcher

import android.app.Application
import android.content.Context
import app.haven.launcher.art.ArtRepository
import app.haven.launcher.bedroom.Bedroom
import app.haven.launcher.bg.Backgrounds
import app.haven.launcher.data.AppsRepository
import app.haven.launcher.data.OptionSources
import app.haven.launcher.data.SettingsStore
import app.haven.launcher.ha.HomeAssistant
import app.haven.launcher.media.Media
import app.haven.launcher.sports.ScoreAlerts
import app.haven.launcher.sports.SportsRepository
import app.haven.launcher.system.CrashReport
import app.haven.launcher.ui.Look
import app.haven.launcher.update.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Process-wide singletons. Created once in [HavenApp.onCreate]. */
object Haven {
    lateinit var app: Context
        private set
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var settings: SettingsStore
        private set
    lateinit var apps: AppsRepository
        private set
    lateinit var ha: HomeAssistant
        private set
    lateinit var art: ArtRepository
        private set
    lateinit var updater: Updater
        private set
    lateinit var sports: SportsRepository
        private set
    lateinit var backgrounds: Backgrounds
        private set
    lateinit var media: Media
        private set

    /** Uptime (ms) of the last remote key press anywhere in Haven; drives art mode's idle start. */
    @Volatile
    var lastInput: Long = 0L

    internal fun init(context: Context) {
        app = context
        settings = SettingsStore(context)
        apps = AppsRepository(context, scope) { settings.activeId }
        ha = HomeAssistant(settings)
        art = ArtRepository(context)
        updater = Updater(context)
        sports = SportsRepository(settings)
        backgrounds = Backgrounds(context, art)
        media = Media(settings)
        OptionSources.backgrounds = { backgrounds.files().map { it.name } }
        OptionSources.apps = { apps.apps.value.map { it.pkg to it.label } }

        // The active profile's look, before anything is drawn, then whenever it changes.
        Look.apply(settings.value.look)
        scope.launch { settings.state.collect { Look.apply(it.look) } }
        settings.onChange = { id, old, new ->
            // Any profile's alarm may ring, so a change to any of them reschedules.
            if (old.alarm != new.alarm || old.kids.bedtime != new.kids.bedtime) Bedroom.scheduleAlarm(context)
            if (id == settings.activeId) {
                if (old.sports != new.sports) scope.launch { ScoreAlerts.sync(context, sports.refresh()) }
                if (old.haUrl != new.haUrl || old.haToken != new.haToken || old.haArea != new.haArea) scope.launch { ha.refresh() }
            }
        }
        // Someone else is watching: their Home Assistant, their teams.
        scope.launch {
            settings.meta.map { it.active }.distinctUntilChanged().drop(1).collect {
                ha.refresh()
                ScoreAlerts.sync(context, sports.refresh())
            }
        }
        // A profile came or went: its alarm with it.
        scope.launch {
            settings.meta.map { it.order }.distinctUntilChanged().drop(1).collect { Bedroom.scheduleAlarm(context) }
        }
    }

    /** Wall-clock time of the last crash, so the Home-button service stops redirecting to a broken app. */
    fun lastCrashAt(context: Context): Long =
        context.getSharedPreferences("haven_crash", Context.MODE_PRIVATE).getLong("at", 0L)
}

class HavenApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            getSharedPreferences("haven_crash", MODE_PRIVATE).edit().putLong("at", System.currentTimeMillis()).commit()
            CrashReport.record(this, e)
            previous?.uncaughtException(t, e)
        }
        try {
            Haven.init(this)
            Bedroom.restore(this)
        } catch (e: Exception) {
            // Keep the process alive so MainActivity can show what went wrong instead of closing.
            CrashReport.record(this, e)
        }
    }
}
