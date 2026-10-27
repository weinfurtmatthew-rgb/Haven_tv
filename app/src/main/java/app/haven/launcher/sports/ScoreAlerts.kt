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
import android.graphics.Color
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
import androidx.core.app.ServiceCompat
import androidx.core.content.res.ResourcesCompat
import app.haven.launcher.Haven
import app.haven.launcher.R
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

    fun open(ctx: Context, game: Game) {
        val apps = Haven.apps.apps.value
        val directv = apps.firstOrNull { it.pkg.contains("directv", true) || it.label.contains("directv", true) }
        val onTv = game.broadcasts.firstOrNull { b -> TV_NETWORKS.any { norm(b).startsWith(it) } }
        val streaming = if (onTv == null) {
            game.broadcasts.firstNotNullOfOrNull { b ->
                val n = norm(b)
                apps.firstOrNull { a -> a != directv && n.isNotBlank() && (norm(a.label).contains(n) || n.contains(norm(a.label))) }
            }
        } else null
        val target = streaming ?: directv
        if (target != null) Haven.apps.launch(target)
        val channel = onTv ?: game.broadcasts.firstOrNull()
        val msg = when {
            streaming != null -> "Opening ${streaming.label} for ${game.matchup}"
            channel != null -> "${game.matchup} is on $channel. Find it in the guide."
            else -> "${game.matchup}: no channel is listed yet"
        }
        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
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
        if (!canOverlay(app)) {
            Toast.makeText(app, if (line.isBlank()) title else "$title\n$line", Toast.LENGTH_LONG).show()
            return@post
        }
        runCatching {
            val wm = app.getSystemService(WindowManager::class.java)
            val dp = { v: Int -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), app.resources.displayMetrics).toInt() }
            // Haven's colours and type (DESIGN.md), fully opaque so the picture behind never shows through the text.
            val card = LinearLayout(app).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(22), dp(14), dp(22), dp(16))
                background = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(Color.rgb(0x28, 0x24, 0x1F))
                    setStroke(dp(1), Color.rgb(0x4A, 0x43, 0x3B))
                }
                addView(TextView(app).apply {
                    text = title
                    setTextColor(Color.rgb(0xF2, 0xED, 0xE4))
                    textSize = 20f
                    typeface = font(app, R.font.atkinson_bold, Typeface.DEFAULT_BOLD)
                    fontFeatureSettings = "tnum"
                })
                if (line.isNotBlank()) addView(TextView(app).apply {
                    text = line
                    setTextColor(Color.rgb(0xB9, 0xB0, 0xA3))
                    textSize = 16f
                    typeface = font(app, R.font.atkinson_medium, Typeface.DEFAULT)
                    setPadding(0, dp(2), 0, 0)
                })
            }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = dp(32)
                y = dp(28)
            }
            wm.addView(card, lp)
            main.postDelayed({ runCatching { wm.removeView(card) } }, 8_000)
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
