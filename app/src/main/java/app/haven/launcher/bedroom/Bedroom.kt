package app.haven.launcher.bedroom

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.widget.Toast
import app.haven.launcher.Haven
import app.haven.launcher.MainActivity
import app.haven.launcher.R
import app.haven.launcher.data.AlarmConfig
import app.haven.launcher.system.HomeButtonService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Alarm clock, sunrise lights and sleep timer for bedroom TVs. All timing goes through AlarmManager. */
object Bedroom {
    const val ACTION_ALARM = "app.haven.launcher.action.ALARM"
    const val ACTION_SUNRISE = "app.haven.launcher.action.SUNRISE"
    const val ACTION_SLEEP_WARN = "app.haven.launcher.action.SLEEP_WARN"
    const val ACTION_SLEEP = "app.haven.launcher.action.SLEEP"
    const val NOTIFICATION_ID = 6030

    private val _sleepEndsAt = MutableStateFlow(0L)

    /** Wall-clock millis when the sleep timer ends, or 0 when it is off. */
    val sleepEndsAt: StateFlow<Long> = _sleepEndsAt

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("haven_bedroom", Context.MODE_PRIVATE)

    /** Called at app start and after boot: alarms do not survive a reboot or an app update. */
    fun restore(ctx: Context) {
        val end = prefs(ctx).getLong("sleepEndsAt", 0L)
        if (end > System.currentTimeMillis()) {
            _sleepEndsAt.value = end
            scheduleSleep(ctx, end)
        } else {
            _sleepEndsAt.value = 0L
        }
        scheduleAlarm(ctx)
    }

    // ---- alarm ---------------------------------------------------------------------------------

    /** The next time [cfg] rings strictly after [now], or null when it is off. */
    fun nextAlarm(cfg: AlarmConfig, now: ZonedDateTime): ZonedDateTime? {
        if (!cfg.enabled || cfg.days.isEmpty()) return null
        for (d in 0..7) {
            val day = now.toLocalDate().plusDays(d.toLong())
            if (day.dayOfWeek !in cfg.days) continue
            val t = ZonedDateTime.of(day, LocalTime.of(cfg.hour, cfg.minute), now.zone)
            if (t.isAfter(now)) return t
        }
        return null
    }

    fun scheduleAlarm(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(broadcast(ctx, ACTION_ALARM))
        am.cancel(broadcast(ctx, ACTION_SUNRISE))
        val s = Haven.settings.value
        val now = System.currentTimeMillis()
        val snooze = prefs(ctx).getLong("snoozeUntil", 0L).takeIf { it > now }
        val next = nextAlarm(s.alarm, ZonedDateTime.now())?.toInstant()?.toEpochMilli()
        val fireAt = listOfNotNull(snooze, next).minOrNull() ?: return
        val show = PendingIntent.getActivity(
            ctx, 10,
            Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_SCREEN, "Alarm"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(fireAt, show), broadcast(ctx, ACTION_ALARM))
        } catch (e: SecurityException) {
            exact(am, fireAt, broadcast(ctx, ACTION_ALARM))
        }
        val lead = s.alarm.sunriseMin * 60_000L
        if (next != null && fireAt == next && lead > 0 && s.haConfigured && next - lead > now) {
            exact(am, next - lead, broadcast(ctx, ACTION_SUNRISE))
        }
    }

    fun snooze(ctx: Context) {
        val min = Haven.settings.value.alarm.snoozeMin
        prefs(ctx).edit().putLong("snoozeUntil", System.currentTimeMillis() + min * 60_000L).apply()
        scheduleAlarm(ctx)
    }

    internal fun onAlarmFired(ctx: Context) {
        prefs(ctx).edit().remove("snoozeUntil").apply()
        val ring = Intent(ctx, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(ring) }
        // Fallback for when Android blocks a background start: a full-screen notification opens the same screen.
        runCatching {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel("alarm", "Alarm", NotificationManager.IMPORTANCE_HIGH))
            val pi = PendingIntent.getActivity(ctx, 11, ring, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = Notification.Builder(ctx, "alarm")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Alarm")
                .setContentText("Good morning")
                .setCategory(Notification.CATEGORY_ALARM)
                .setFullScreenIntent(pi, true)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            nm.notify(NOTIFICATION_ID, n)
        }
        scheduleAlarm(ctx)
    }

    /** Fades the room's lights up to full over the sunrise lead time (Home Assistant `transition`). */
    internal suspend fun sunrise() {
        val s = Haven.settings.value
        if (!s.haConfigured) return
        Haven.ha.refresh()
        val lights = Haven.ha.roomLights(s).filter { it.domain == "light" }
        if (lights.isNotEmpty()) Haven.ha.setLights(lights, on = true, brightnessPct = 100, transitionSec = s.alarm.sunriseMin * 60)
    }

    // ---- sleep timer ---------------------------------------------------------------------------

    fun startSleep(ctx: Context, minutes: Int) {
        val end = System.currentTimeMillis() + minutes * 60_000L
        prefs(ctx).edit().putLong("sleepEndsAt", end).apply()
        _sleepEndsAt.value = end
        scheduleSleep(ctx, end)
    }

    fun cancelSleep(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am?.cancel(broadcast(ctx, ACTION_SLEEP_WARN))
        am?.cancel(broadcast(ctx, ACTION_SLEEP))
        prefs(ctx).edit().remove("sleepEndsAt").apply()
        _sleepEndsAt.value = 0L
    }

    private fun scheduleSleep(ctx: Context, end: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        if (end - 60_000L > System.currentTimeMillis()) exact(am, end - 60_000L, broadcast(ctx, ACTION_SLEEP_WARN))
        exact(am, end, broadcast(ctx, ACTION_SLEEP))
    }

    /** Sleep timer ran out: pause playback, turn the room's lights off, then put the box to sleep. */
    internal suspend fun sleepNow(ctx: Context) {
        val s = Haven.settings.value
        cancelSleep(ctx)
        val audio = ctx.getSystemService(AudioManager::class.java)
        val t = SystemClock.uptimeMillis()
        audio?.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE, 0))
        audio?.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE, 0))
        if (s.sleepLightsOff && s.haConfigured) {
            Haven.ha.refresh()
            val on = Haven.ha.roomLights(s).filter { it.isOn }
            if (on.isNotEmpty()) Haven.ha.setLights(on, on = false)
        }
        if (s.sleepBox) {
            delay(1500)
            if (!HomeButtonService.sleepDevice()) {
                Toast.makeText(ctx, "Sleep timer: turn on the Haven Home button service so Haven can put the box to sleep", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ---- helpers -------------------------------------------------------------------------------

    private fun broadcast(ctx: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
        ctx, action.hashCode(),
        Intent(ctx, BedroomReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun exact(am: AlarmManager, at: Long, pi: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun timeLabel(hour: Int, minute: Int, use24h: Boolean): String =
        LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern(if (use24h) "H:mm" else "h:mm a", Locale.getDefault()))

    fun daysLabel(days: Set<DayOfWeek>): String = when (days) {
        AlarmConfig.EVERY_DAY -> "Every day"
        AlarmConfig.WEEKDAYS -> "Weekdays"
        AlarmConfig.WEEKENDS -> "Weekends"
        emptySet<DayOfWeek>() -> "No days"
        else -> days.sorted().joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    }
}

class BedroomReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            Bedroom.ACTION_ALARM -> Bedroom.onAlarmFired(ctx)
            Bedroom.ACTION_SUNRISE -> async { Bedroom.sunrise() }
            Bedroom.ACTION_SLEEP_WARN -> Toast.makeText(ctx, "Sleep timer: 1 minute left", Toast.LENGTH_LONG).show()
            Bedroom.ACTION_SLEEP -> async { Bedroom.sleepNow(ctx.applicationContext) }
        }
    }

    private fun async(block: suspend () -> Unit) {
        val pending = goAsync()
        Haven.scope.launch {
            try {
                withTimeoutOrNull(25_000) { block() }
            } finally {
                pending.finish()
            }
        }
    }
}
