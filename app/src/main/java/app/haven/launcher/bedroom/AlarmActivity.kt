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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.haven.launcher.Haven
import app.haven.launcher.MainActivity
import app.haven.launcher.ui.C
import app.haven.launcher.ui.DesignCanvas
import app.haven.launcher.ui.Focusable
import app.haven.launcher.ui.Icon
import app.haven.launcher.ui.Icons
import app.haven.launcher.ui.T
import app.haven.launcher.ui.Txt
import app.haven.launcher.ui.formatDate
import app.haven.launcher.ui.formatTime
import app.haven.launcher.ui.greeting
import app.haven.launcher.ui.ink
import app.haven.launcher.ui.rememberNow
import app.haven.launcher.ui.weatherIcon
import app.haven.launcher.ui.weatherLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Full-screen alarm from the bedroom mockup: a sunrise in a dark room, big clock, Snooze / Stop. */
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
    // The light comes up over the first minute, like the sunrise lights in the room.
    val rise = remember { Animatable(0.15f) }
    LaunchedEffect(Unit) { rise.animateTo(1f, tween(60_000, easing = LinearEasing)) }
    Box(Modifier.fillMaxSize().background(C.Hearth)) {
        SunriseGlow(rise.value, Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().padding(top = 136.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Txt(formatDate(now), T.Body, color = C.Stone, size = 34)
            Txt(formatTime(now, s.use24h), T.Display, Modifier.padding(top = 8.dp))
            val name = s.profileName.trim()
            Txt(greeting(now.hour) + if (name.isEmpty()) "" else ", $name", T.Headline, Modifier.padding(top = 8.dp))
            Row(Modifier.padding(top = 40.dp), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                Haven.ha.weather(s)?.takeIf { it.available }?.let { w ->
                    val t = w.attrs.optDouble("temperature", Double.NaN)
                    Info(weatherIcon(w.state), (if (t.isNaN()) "" else "${Math.round(t)}° ") + weatherLabel(w.state))
                }
                if (s.alarm.sunriseMin > 0 && s.haConfigured) Info(Icons.Sunrise, "Sunrise lights are on")
            }
            Row(Modifier.padding(top = 56.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                BigButton("Snooze ${s.alarm.snoozeMin} min", onSnooze)
                BigButton("Stop", onStop, stopFocus)
            }
        }
    }
}

/** A low sun under the bottom edge: lamp light fading out over the dark room. Dithered so it doesn't band. */
@Composable
private fun SunriseGlow(rise: Float, modifier: Modifier) {
    val paint = remember { android.graphics.Paint().apply { isDither = true } }
    Canvas(modifier) {
        val r = size.height * (0.6f + 0.3f * rise)
        val cx = size.width / 2
        val cy = size.height + r * 0.3f
        val lamp = 0xF2B35E
        fun a(alpha: Float) = ((alpha * 255).toInt().coerceIn(0, 255) shl 24) or lamp
        paint.shader = android.graphics.RadialGradient(
            cx, cy, r,
            intArrayOf(a(0.62f * rise), a(0.22f * rise), a(0f)),
            floatArrayOf(0f, 0.5f, 1f),
            android.graphics.Shader.TileMode.CLAMP,
        )
        drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint) }
    }
}

@Composable
private fun BigButton(label: String, onClick: () -> Unit, focus: FocusRequester? = null) {
    Focusable(
        onClick = onClick,
        modifier = Modifier.size(300.dp, 96.dp),
        shape = RoundedCornerShape(48.dp),
        color = C.Raised,
        focusedScale = 1.06f,
        focusRequester = focus,
    ) { focused ->
        Txt(label, T.CardTitle, Modifier.align(Alignment.Center), ink(focused), size = 32)
    }
}

@Composable
private fun Info(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, 36.dp, C.Stone)
        Txt(text, T.Body, color = C.Stone, size = 30)
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
