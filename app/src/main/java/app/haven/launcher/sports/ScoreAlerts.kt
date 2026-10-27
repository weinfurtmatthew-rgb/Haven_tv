package app.haven.launcher.sports

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.ServiceCompat
import androidx.core.content.res.ResourcesCompat
import app.haven.launcher.Haven
import app.haven.launcher.R
import app.haven.launcher.bedroom.AlarmTone
import app.haven.launcher.data.AlertCorner
import app.haven.launcher.data.GameAction
import app.haven.launcher.data.TvApp
import app.haven.launcher.ui.Look
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/** Opens whatever shows a game: a streaming app that carries it, otherwise the DIRECTV app. */
object Watch {
    /** Channels you'd find in the DIRECTV guide rather than in a separate app. */
    private val TV_NETWORKS = setOf(
        "abc", "cbs", "nbc", "fox", "espn", "espn2", "espnu", "fs1", "fs2", "tnt", "tbs", "trutv", "usa", "nfl network",
        "mlb network", "nhl network", "nba tv", "golf", "cbs sports network", "cbssn", "big ten network", "btn", "sec network",
        "acc network", "telemundo", "univision", "unimas", "tudn", "fox deportes", "espn deportes", "bally sports", "fanduel sports",
        "msg", "nesn", "yes", "sportsnet", "marquee", "root sports", "nbc sports", "spectrum sportsnet",
    )

    private fun norm(s: String) = s.lowercase().replace("+", "").replace(".", "").replace(" tv", "").replace("tv ", "").trim()

    /** One way to watch a game: an app to open and what to tell the viewer. */
    class Way(val label: String, val app: TvApp, val message: String)

    /** The ways to watch [game] on this TV: streaming apps that carry it first, then DIRECTV. */
    fun ways(game: Game): List<Way> {
        val apps = Haven.apps.apps.value
        val directv = Haven.apps.provider()
        val out = mutableListOf<Way>()
        for (b in game.broadcasts) {
            val n = norm(b)
            if (n.isBlank() || TV_NETWORKS.any { n.startsWith(it) }) continue
            apps.firstOrNull { a -> a != directv && (norm(a.label).contains(n) || n.contains(norm(a.label))) }
                ?.takeIf { a -> out.none { it.app == a } }
                ?.let { out += Way(it.label, it, "Opening ${it.label} for ${game.matchup}") }
        }
        if (directv != null) {
            val channel = game.broadcasts.firstOrNull { b -> TV_NETWORKS.any { norm(b).startsWith(it) } } ?: game.broadcasts.firstOrNull()
            out += Way(
                directv.label + (channel?.let { " ($it)" } ?: ""), directv,
                if (channel != null) "${game.matchup} is on $channel. Find it in the guide." else "${game.matchup}: no channel is listed yet",
            )
        }
        return out
    }

    fun go(ctx: Context, way: Way) {
        Haven.apps.launch(way.app)
        Toast.makeText(ctx, way.message, Toast.LENGTH_LONG).show()
    }

    /** Opens the game the profile's way; returns the choices when it should ask instead. */
    fun open(ctx: Context, game: Game, action: GameAction = Haven.settings.value.sports.onSelect): List<Way>? {
        val ways = ways(game)
        if (ways.isEmpty()) {
            Toast.makeText(ctx, "${game.matchup}: no app on this TV shows it", Toast.LENGTH_LONG).show()
            return null
        }
        return when (action) {
            GameAction.Ask -> ways
            GameAction.Directv -> {
                go(ctx, ways.last())
                null
            }
            GameAction.Auto -> {
                // A game on a TV channel opens DIRECTV; one only on a streaming service opens that app.
                val onTv = game.broadcasts.any { b -> TV_NETWORKS.any { norm(b).startsWith(it) } }
                go(ctx, if (onTv) ways.last() else ways.first())
                null
            }
        }
    }
}

/** Shows a small score card in the top-right corner over any app, or a toast when that isn't allowed. */
object ScorePopup {
    private val main = Handler(Looper.getMainLooper())

    private fun font(ctx: Context, id: Int, fallback: Typeface): Typeface =
        runCatching { ResourcesCompat.getFont(ctx, id) }.getOrNull() ?: fallback

    fun canOverlay(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    fun requestPermission(ctx: Context) {
        val intents = listOf(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")),
        )
        for (i in intents) {
            if (runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
        }
    }

    fun show(ctx: Context, title: String, line: String) = main.post {
        val app = ctx.applicationContext
        val cfg = Haven.settings.value.sports
        if (cfg.alertSound) runCatching { AlarmTone.ping() }
        if (!canOverlay(app)) {
            Toast.makeText(app, if (line.isBlank()) title else "$title\n$line", Toast.LENGTH_LONG).show()
            return@post
        }
        runCatching {
            val wm = app.getSystemService(WindowManager::class.java)
            val k = cfg.alertSize.factor
            val dp = { v: Int -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v * k, app.resources.displayMetrics).toInt() }
            val p = Look.r.palette
            // The profile's colours and fonts, fully opaque so the picture behind never shows through the text.
            val card = LinearLayout(app).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(22), dp(14), dp(22), dp(16))
                background = GradientDrawable().apply {
                    cornerRadius = dp(Look.cfg.corners.px + 4).toFloat()
                    setColor(p.surface.toArgb())
                    setStroke(dp(1), p.line.toArgb())
                }
                addView(TextView(app).apply {
                    text = title
                    setTextColor(p.linen.toArgb())
                    textSize = 20f * k
                    typeface = font(app, R.font.atkinson_bold, Typeface.DEFAULT_BOLD)
                    fontFeatureSettings = "tnum"
                })
                if (line.isNotBlank()) addView(TextView(app).apply {
                    text = line
                    setTextColor(p.stone.toArgb())
                    textSize = 16f * k
                    typeface = font(app, R.font.atkinson_medium, Typeface.DEFAULT)
                    setPadding(0, dp(2), 0, 0)
                })
            }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // Not focusable, so the remote keeps working in the app underneath. Deliberately not
                // FLAG_NOT_TOUCHABLE: Android caps untouchable overlays at 80% opacity, which let the picture
                // show through the score. Nothing touches a TV, so being touchable costs nothing.
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = when (cfg.alertCorner) {
                    AlertCorner.TopRight -> Gravity.TOP or Gravity.END
                    AlertCorner.TopLeft -> Gravity.TOP or Gravity.START
                    AlertCorner.BottomRight -> Gravity.BOTTOM or Gravity.END
                    AlertCorner.BottomLeft -> Gravity.BOTTOM or Gravity.START
                }
                x = dp(32)
                y = dp(28)
            }
            wm.addView(card, lp)
            main.postDelayed({ runCatching { wm.removeView(card) } }, cfg.alertSeconds * 1000L)
        }.onFailure {
            Toast.makeText(app, if (line.isBlank()) title else "$title\n$line", Toast.LENGTH_LONG).show()
        }
    }
}

/**
 * Runs while a followed team is playing (or about to): polls scores every 30 seconds and pops up changes,
 * whatever app is on screen. Stops itself when nothing is live and schedules the next start with an alarm.
 */
class ScoreAlertService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Live scores", NotificationManager.IMPORTANCE_MIN))
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Following your teams")
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, n,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        if (job?.isActive != true) job = scope.launch { loop() }
        return START_NOT_STICKY
    }

    private suspend fun loop() {
        val repo = Haven.sports
        repo.changes(repo.refresh()) // first pass only records the current scores
        while (true) {
            delay(30_000)
            val games = repo.refresh()
            if (Haven.settings.value.sports.alerts) {
                for ((title, line) in repo.changes(games)) ScorePopup.show(this, title, line)
            }
            val soon = Instant.now().plus(Duration.ofMinutes(20))
            if (games.none { it.state == GameState.Live || (it.state == GameState.Scheduled && it.start.isBefore(soon)) }) break
        }
        ScoreAlerts.scheduleNext(this, repo.games.value)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "scores"
        private const val NOTIFICATION_ID = 7041
    }
}

object ScoreAlerts {
    private const val ACTION = "app.haven.launcher.action.SCORE_WATCH"

    /** Starts watching now if a followed game is live or near; otherwise sets an alarm for the next one. */
    fun sync(ctx: Context, games: List<Game>) {
        val cfg = Haven.settings.value.sports
        if (!cfg.alerts || cfg.teams.isEmpty()) return cancel(ctx)
        val soon = Instant.now().plus(Duration.ofMinutes(20))
        if (games.any { it.state == GameState.Live || (it.state == GameState.Scheduled && it.start.isBefore(soon)) }) {
            runCatching { ctx.startForegroundService(Intent(ctx, ScoreAlertService::class.java)) }
        } else {
            scheduleNext(ctx, games)
        }
    }

    fun scheduleNext(ctx: Context, games: List<Game>) {
        val next = games.filter { it.state == GameState.Scheduled && it.start.isAfter(Instant.now()) }.minByOrNull { it.start } ?: return
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val at = next.start.minus(Duration.ofMinutes(10)).toEpochMilli().coerceAtLeast(System.currentTimeMillis() + 60_000)
        runCatching { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(ctx)) }
            .onFailure { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(ctx)) }
    }

    fun cancel(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java)?.cancel(pending(ctx))
        ctx.stopService(Intent(ctx, ScoreAlertService::class.java))
    }

    private fun pending(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 7042, Intent(ctx, ScoreAlarmReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** Fires shortly before a followed game; exact alarms may start a foreground service from the background. */
class ScoreAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (Haven.settings.value.sports.alerts) {
            runCatching { ctx.startForegroundService(Intent(ctx, ScoreAlertService::class.java)) }
        }
    }
}
