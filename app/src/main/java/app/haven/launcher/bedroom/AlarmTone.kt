package app.haven.launcher.bedroom

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import app.haven.launcher.data.AlarmSound
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** The alarm sounds, synthesized on the TV (no sound files, nothing to license). Pure, so tests can check them. */
object Tones {
    const val RATE = 22_050

    /** One loop of [sound] as 16-bit mono PCM; empty for Silent. */
    fun pcm(sound: AlarmSound, rate: Int = RATE): ShortArray {
        val f = when (sound) {
            AlarmSound.Chime -> chime(rate)
            AlarmSound.Bells -> bells(rate)
            AlarmSound.Marimba -> marimba(rate)
            AlarmSound.Digital -> digital(rate)
            AlarmSound.Rise -> rise(rate)
            AlarmSound.Silent -> FloatArray(0)
        }
        return ShortArray(f.size) { i -> (f[i] * 0.45f * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort() }
    }

    /** A struck bell: a fundamental and inharmonic partials, each dying away at its own speed. */
    private fun strike(out: FloatArray, rate: Int, at: Double, freq: Double, gain: Double, partials: List<Triple<Double, Double, Double>>) {
        val s0 = (at * rate).toInt()
        for (i in s0 until out.size) {
            val t = (i - s0).toDouble() / rate
            var v = 0.0
            for ((ratio, amp, decay) in partials) v += amp * sin(2 * PI * freq * ratio * t) * exp(-t * decay)
            val attack = (t / 0.004).coerceAtMost(1.0)
            out[i] += (v * attack * gain).toFloat()
        }
    }

    private val BELL = listOf(Triple(1.0, 0.6, 2.2), Triple(2.76, 0.25, 4.5), Triple(5.4, 0.1, 8.0))

    /** Three rising notes, the original Haven chime. */
    private fun chime(rate: Int): FloatArray {
        val out = FloatArray(rate * 3)
        for ((at, f) in listOf(0.0 to 1046.5, 0.32 to 1318.5, 0.64 to 1568.0)) strike(out, rate, at, f, 1.0, BELL)
        return out
    }

    /** Two low church bells, slow and round. */
    private fun bells(rate: Int): FloatArray {
        val out = FloatArray(rate * 4)
        val partials = listOf(Triple(1.0, 0.55, 0.9), Triple(2.0, 0.25, 1.4), Triple(2.4, 0.18, 1.8), Triple(3.0, 0.1, 2.5), Triple(4.2, 0.06, 3.5))
        strike(out, rate, 0.0, 523.25, 1.0, partials)
        strike(out, rate, 1.4, 392.0, 1.0, partials)
        return out
    }

    /** A quick wooden arpeggio up and back down. */
    private fun marimba(rate: Int): FloatArray {
        val out = FloatArray((rate * 2.4).toInt())
        val partials = listOf(Triple(1.0, 0.7, 6.0), Triple(4.0, 0.2, 14.0), Triple(9.2, 0.05, 24.0))
        val notes = listOf(523.25, 659.25, 783.99, 1046.5, 783.99, 659.25)
        for ((i, f) in notes.withIndex()) strike(out, rate, i * 0.2, f, 0.9, partials)
        return out
    }

    /** The classic bedside beep: four short beeps, then a pause. Softened square waves, not a harsh buzz. */
    private fun digital(rate: Int): FloatArray {
        val out = FloatArray((rate * 1.4).toInt())
        for (b in 0 until 4) {
            val s0 = (b * 0.2 * rate).toInt()
            val n = (0.1 * rate).toInt()
            for (k in 0 until n) {
                val t = k.toDouble() / rate
                val edge = minOf(k, n - k).toDouble() / (0.004 * rate)
                val env = edge.coerceAtMost(1.0)
                val v = sin(2 * PI * 2000 * t) + sin(2 * PI * 6000 * t) / 3 + sin(2 * PI * 10000 * t) / 5
                out[s0 + k] += (v * 0.55 * env).toFloat()
            }
        }
        return out
    }

    /**
     * A warm major chord that swells and fades. Every frequency completes whole cycles in the 4 second loop, so it
     * repeats without a click.
     */
    private fun rise(rate: Int): FloatArray {
        val n = rate * 4
        val out = FloatArray(n)
        val chord = listOf(261.5, 329.75, 392.0, 523.25)
        for (i in 0 until n) {
            val t = i.toDouble() / rate
            var v = 0.0
            for ((k, f) in chord.withIndex()) v += sin(2 * PI * f * t) / (k + 1.5)
            val swell = 0.55 + 0.45 * sin(2 * PI * 0.5 * t - PI / 2)
            out[i] = (v * swell * 0.7).toFloat()
        }
        return out
    }
}

/**
 * Plays an alarm sound on a loop, rising from quiet to [maxVolume] over [rampSec] seconds. Also plays short
 * samples while someone picks a sound in Settings.
 */
class AlarmTone(private val sound: AlarmSound, private val maxVolume: Float = 1f, private val rampSec: Int = 40) {
    private var track: AudioTrack? = null
    private val handler = Handler(Looper.getMainLooper())
    private var volume = 0f
    private val step: Float get() = if (rampSec <= 0) maxVolume else (maxVolume * 0.8f / rampSec)

    private val ramp = object : Runnable {
        override fun run() {
            volume = (volume + step).coerceAtMost(maxVolume)
            track?.setVolume(volume)
            if (volume < maxVolume) handler.postDelayed(this, 1000)
        }
    }

    fun start() {
        if (sound == AlarmSound.Silent) return
        runCatching {
            val pcm = Tones.pcm(sound)
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
                        .setSampleRate(Tones.RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
            t.write(pcm, 0, pcm.size)
            t.setLoopPoints(0, pcm.size, -1)
            volume = if (rampSec <= 0) maxVolume else maxVolume * 0.2f
            t.setVolume(volume)
            t.play()
            track = t
            if (volume < maxVolume) handler.postDelayed(ramp, 1000)
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

    companion object {
        private var sample: AlarmTone? = null
        private val main = Handler(Looper.getMainLooper())
        private val stopSample = Runnable { sample?.stop(); sample = null }

        /** A few seconds of [sound] at a gentle volume, while choosing one. */
        fun preview(sound: AlarmSound) {
            main.removeCallbacks(stopSample)
            sample?.stop()
            sample = AlarmTone(sound, maxVolume = 0.6f, rampSec = 0).also { it.start() }
            main.postDelayed(stopSample, 4000)
        }

        fun stopPreview() {
            main.removeCallbacks(stopSample)
            stopSample.run()
        }

        /** A short chime for a score alert. */
        fun ping() {
            main.post {
                main.removeCallbacks(stopSample)
                sample?.stop()
                sample = AlarmTone(AlarmSound.Chime, maxVolume = 0.5f, rampSec = 0).also { it.start() }
                main.postDelayed(stopSample, 1300)
            }
        }
    }
}
