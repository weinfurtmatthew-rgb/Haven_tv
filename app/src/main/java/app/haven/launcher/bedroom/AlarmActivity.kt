package app.haven.launcher.bedroom

import android.app.NotificationManager
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.haven.launcher.Haven
import app.haven.launcher.MainActivity
import app.haven.launcher.ui.C
import app.haven.launcher.ui.DesignCanvas
import app.haven.launcher.ui.Focusable
import app.haven.launcher.ui.Icon
import app.haven.launcher.ui.Icons
import app.haven.launcher.ui.Txt
import app.haven.launcher.ui.formatDate
import app.haven.launcher.ui.formatTime
import app.haven.launcher.ui.rememberNow
import app.haven.launcher.ui.weatherIcon
import app.haven.launcher.ui.weatherLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Full-screen alarm from the bedroom mockup: dawn gradient, big clock, Snooze / Stop. */
class AlarmActivity : ComponentActivity() {
    private val chime = Chime()
    private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        getSystemService(NotificationManager::class.java)?.cancel(Bedroom.NOTIFICATION_ID)
        chime.start()
        setContent {
            BackHandler { snooze() }
            DesignCanvas { AlarmRinging(onSnooze = ::snooze, onStop = ::stop) }
        }
        // Nobody answered: snooze once rather than ringing forever.
        lifecycleScope.launch {
            delay(10 * 60_000L)
            snooze()
        }
    }

    override fun onDestroy() {
        chime.stop()
        super.onDestroy()
    }

    private fun snooze() {
        if (done) return
        done = true
        Bedroom.snooze(this)
        finish()
    }

    private fun stop() {
        if (done) return
        done = true
        finish()
        // Straight into the morning briefing.
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SCREEN, "Briefing")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

@Composable
private fun AlarmRinging(onSnooze: () -> Unit, onStop: () -> Unit) {
    val now by rememberNow()
    val s = Haven.settings.value
    val stopFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { stopFocus.requestFocus() }
    }
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF120F2C), Color(0xFF8C3E6E), Color(0xFFFF945C))),
        ),
    ) {
        Box(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 0.dp).size(1400.dp, 500.dp)
                .background(Brush.radialGradient(listOf(Color(0x99FFF0D6), Color(0x00FFD7A0)))),
        )
        Column(
            Modifier.fillMaxSize().padding(top = 230.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Txt(formatDate(now), 36, weight = FontWeight.Medium, color = C.Text.copy(alpha = 0.85f))
            Txt(formatTime(now, s.use24h), 300, Modifier.padding(top = 4.dp), FontWeight.ExtraBold, spacing = -8f)
            val name = s.profileName.trim()
            Txt(if (name.isEmpty()) "Good morning" else "Good morning, $name", 50, weight = FontWeight.SemiBold)
            Row(Modifier.padding(top = 36.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Haven.ha.weather(s)?.takeIf { it.available }?.let { w ->
                    val t = w.attrs.optDouble("temperature", Double.NaN)
                    InfoPill(weatherIcon(w.state), (if (t.isNaN()) "" else "${Math.round(t)}° ") + weatherLabel(w.state))
                }
                if (s.alarm.sunriseMin > 0 && s.haConfigured) InfoPill(Icons.Sunrise, "Sunrise lights on")
            }
            Row(Modifier.padding(top = 48.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                Focusable(
                    onClick = onSnooze,
                    modifier = Modifier.size(240.dp, 80.dp),
                    shape = RoundedCornerShape(40.dp),
                    color = Color(0x55281630),
                    focusedColor = Color(0x88281630),
                    focusedScale = 1.06f,
                ) {
                    Txt("Snooze ${s.alarm.snoozeMin} min", 30, Modifier.align(Alignment.Center))
                }
                Focusable(
                    onClick = onStop,
                    modifier = Modifier.size(240.dp, 80.dp),
                    shape = RoundedCornerShape(40.dp),
                    color = Color.White.copy(alpha = 0.85f),
                    focusedColor = Color.White,
                    focusedScale = 1.08f,
                    focusRequester = stopFocus,
                ) {
                    Txt("Stop", 32, Modifier.align(Alignment.Center), FontWeight.ExtraBold, Color(0xFF1A1420))
                }
            }
        }
    }
}

@Composable
private fun InfoPill(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        Modifier.height(72.dp).clip(RoundedCornerShape(36.dp)).background(Color(0x44281630)).padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, 36.dp, Color(0xFFFFE2B0))
        Txt(text, 28)
    }
}

/** A soft three-note bell, synthesized and looped, getting louder over the first 40 seconds. */
class Chime {
    private var track: AudioTrack? = null
    private val handler = Handler(Looper.getMainLooper())
    private var volume = 0.2f

    private val ramp = object : Runnable {
        override fun run() {
            volume = (volume + 0.02f).coerceAtMost(1f)
            track?.setVolume(volume)
            if (volume < 1f) handler.postDelayed(this, 1000)
        }
    }

    fun start() {
        runCatching {
            val rate = 44_100
            val n = (rate * 3.0).toInt()
            val pcm = FloatArray(n)
            val notes = listOf(0.0 to 1046.5, 0.32 to 1318.5, 0.64 to 1568.0)
            for ((start, f) in notes) {
                val s0 = (start * rate).toInt()
                for (i in s0 until n) {
                    val t = (i - s0).toDouble() / rate
                    val v = 0.6 * sin(2 * PI * f * t) * exp(-t * 2.2) +
                        0.25 * sin(2 * PI * f * 2.76 * t) * exp(-t * 4.5) +
                        0.1 * sin(2 * PI * f * 5.4 * t) * exp(-t * 8.0)
                    val attack = (t / 0.004).coerceAtMost(1.0)
                    pcm[i] += (v * attack).toFloat()
                }
            }
            val out = ShortArray(n) { i -> (pcm[i] * 0.45f * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort() }
            val t = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(n * 2)
                .build()
            t.write(out, 0, n)
            t.setLoopPoints(0, n, -1)
            t.setVolume(volume)
            t.play()
            track = t
            handler.postDelayed(ramp, 1000)
        }
    }

    fun stop() {
        handler.removeCallbacks(ramp)
        runCatching {
            track?.stop()
            track?.release()
        }
        track = null
    }
}
