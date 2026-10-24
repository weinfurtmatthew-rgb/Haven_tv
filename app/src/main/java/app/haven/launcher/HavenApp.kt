package app.haven.launcher

import android.app.Application
import android.content.Context
import app.haven.launcher.art.ArtRepository
import app.haven.launcher.bedroom.Bedroom
import app.haven.launcher.data.AppsRepository
import app.haven.launcher.data.SettingsStore
import app.haven.launcher.ha.HomeAssistant
import app.haven.launcher.update.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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

    /** Uptime (ms) of the last remote key press anywhere in Haven; drives art mode's idle start. */
    @Volatile
    var lastInput: Long = 0L

    internal fun init(context: Context) {
        app = context
        settings = SettingsStore(context)
        apps = AppsRepository(context, scope)
        ha = HomeAssistant(settings)
        art = ArtRepository(context)
        updater = Updater(context)
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
            previous?.uncaughtException(t, e)
        }
        Haven.init(this)
        Bedroom.restore(this)
    }
}
