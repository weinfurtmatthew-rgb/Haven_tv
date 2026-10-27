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
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.widget.Toast
import app.haven.launcher.Haven
import app.haven.launcher.MainActivity
import app.haven.launcher.R
import app.haven.launcher.data.AlarmConfig
import app.haven.launcher.data.Settings
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

/**
 * Alarm clock, sunrise lights and sleep timer for bedroom TVs. All timing goes through AlarmManager. Every profile
 * has its own alarm; each is scheduled separately and rings with that profile's sound, look and name.
 */
object Bedroom {
    const val ACTION_ALARM = "app.haven.launcher.action.ALARM"
    const val ACTION_SUNRISE = "app.haven.launcher.action.SUNRISE"
    const val ACTION_SLEEP_WARN = "app.haven.launcher.action.SLEEP_WARN"
    const val ACTION_SLEEP = "app.haven.launcher.action.SLEEP"
    const val EXTRA_PROFILE = "app.haven.launcher.PROFILE"
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

    /** Schedules every profile's next alarm (and its sunrise lights), cancelling ones that are off or gone. */
    fun scheduleAlarm(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        // Alarms set before profiles existed carried no profile; never let one of those ring as well.
        am.cancel(legacy(ctx, ACTION_ALARM))
        am.cancel(legacy(ctx, ACTION_SUNRISE))
        val all = Haven.settings.all()
        val ids = all.map { it.first }.toSet()
        val p = prefs(ctx)
        for (gone in p.getStringSet("alarmProfiles", emptySet()).orEmpty() - ids) {
            am.cancel(broadcast(ctx, ACTION_ALARM, gone))
            am.cancel(broadcast(ctx, ACTION_SUNRISE, gone))
        }
        p.edit().putStringSet("alarmProfiles", ids).apply()
        for ((id, s) in all) scheduleFor(ctx, am, id, s)
    }

    private fun scheduleFor(ctx: Context, am: AlarmManager, id: String, s: Settings) {
        val ring = broadcast(ctx, ACTION_ALARM, id)
        val sunrise = broadcast(ctx, ACTION_SUNRISE, id)
        am.cancel(ring)
        am.cancel(sunrise)
        val now = System.currentTimeMillis()
        val snooze = prefs(ctx).getLong("snoozeUntil:$id", 0L).takeIf { it > now }
        val next = nextAlarm(s.alarm, ZonedDateTime.now())?.toInstant()?.toEpochMilli()
        val fireAt = listOfNotNull(snooze, next).minOrNull() ?: return
        val show = PendingIntent.getActivity(
            ctx, 10 + code(id),
            Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_SCREEN, "Alarm").setData(profileUri(id)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(fireAt, show), ring)
        } catch (e: SecurityException) {
            exact(am, fireAt, ring)
        }
        val lead = s.alarm.sunriseMin * 60_000L
        if (next != null && fireAt == next && lead > 0 && s.haConfigured && next - lead > now) {
            exact(am, next - lead, sunrise)
        }
    }

    /** Snoozes in a row for a profile's alarm, so it can stop offering snooze after the limit. */
    fun snoozesUsed(ctx: Context, id: String): Int = prefs(ctx).getInt("snoozes:$id", 0)

    fun snooze(ctx: Context, id: String) {
        val min = Haven.settings.settingsOf(id).alarm.snoozeMin
        prefs(ctx).edit()
            .putLong("snoozeUntil:$id", System.currentTimeMillis() + min * 60_000L)
            .putInt("snoozes:$id", snoozesUsed(ctx, id) + 1)
            .apply()
        scheduleAlarm(ctx)
    }

    /** The alarm was stopped: the next one starts with a fresh snooze count. */
    fun stopped(ctx: Context, id: String) {
        prefs(ctx).edit().remove("snoozeUntil:$id").remove("snoozes:$id").apply()
        scheduleAlarm(ctx)
    }

    internal fun onAlarmFired(ctx: Context, id: String) {
        val p = prefs(ctx)
        val snoozed = p.getLong("snoozeUntil:$id", 0L)
        // Rang on time (not from a snooze): a new morning, a new snooze count.
        if (snoozed == 0L || kotlin.math.abs(System.currentTimeMillis() - snoozed) > 5 * 60_000L) p.edit().remove("snoozes:$id").apply()
        p.edit().remove("snoozeUntil:$id").apply()
        val ring = Intent(ctx, AlarmActivity::class.java)
            .putExtra(EXTRA_PROFILE, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(ring) }
        // Fallback for when Android blocks a background start: a full-screen notification opens the same screen.
        runCatching {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel("alarm", "Alarm", NotificationManager.IMPORTANCE_HIGH))
            val pi = PendingIntent.getActivity(ctx, 11 + code(id), ring.setData(profileUri(id)), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
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

    /** Fades a profile's room lights up to full over its sunrise lead time (Home Assistant `transition`). */
    internal suspend fun sunrise(id: String) {
        val s = Haven.settings.settingsOf(id)
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

    private fun code(id: String): Int = (id.hashCode() and 0x3FFF) * 4

    private fun profileUri(id: String): Uri = Uri.parse("haven://profile/$id")

    /** A profile's alarm or sunrise: the profile in the data makes each one a separate alarm. */
    private fun broadcast(ctx: Context, action: String, id: String): PendingIntent = PendingIntent.getBroadcast(
        ctx, code(id) + action.length,
        Intent(ctx, BedroomReceiver::class.java).setAction(action).setData(profileUri(id)).putExtra(EXTRA_PROFILE, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun legacy(ctx: Context, action: String): PendingIntent = broadcast(ctx, action)

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
            Bedroom.ACTION_ALARM -> profileOf(intent)?.let { Bedroom.onAlarmFired(ctx, it) }
            Bedroom.ACTION_SUNRISE -> {
                val id = profileOf(intent) ?: return
                async { Bedroom.sunrise(id) }
            }
            Bedroom.ACTION_SLEEP_WARN -> Toast.makeText(ctx, "Sleep timer: 1 minute left", Toast.LENGTH_LONG).show()
            Bedroom.ACTION_SLEEP -> async { Bedroom.sleepNow(ctx.applicationContext) }
        }
    }

    /**
     * The profile an alarm belongs to, or null when that profile has been removed since. One set before profiles
     * existed belongs to whoever is active.
     */
    private fun profileOf(intent: Intent): String? {
        val id = intent.getStringExtra(Bedroom.EXTRA_PROFILE) ?: return Haven.settings.activeId
        return id.takeIf { Haven.settings.exists(it) }
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
