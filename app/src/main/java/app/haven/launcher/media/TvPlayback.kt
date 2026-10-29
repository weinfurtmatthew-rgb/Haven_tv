package app.haven.launcher.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import app.haven.launcher.Haven
import app.haven.launcher.system.MediaListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Something playing in an app on this TV itself. [title] is blank when Haven only knows that something plays. */
data class TvTrack(
    val app: String,
    val pkg: String,
    val title: String,
    val artist: String,
    val album: String,
    val art: ImageBitmap?,
    val playing: Boolean,
    /** Where the song was, in ms, at [positionAt] (uptime); [durationMs] is 0 when unknown. */
    val positionMs: Long,
    val positionAt: Long,
    val durationMs: Long,
) {
    /** How far into the song it is now, in ms. */
    fun elapsedMs(now: Long = SystemClock.elapsedRealtime()): Long =
        (if (playing) positionMs + (now - positionAt) else positionMs).coerceIn(0L, if (durationMs > 0) durationMs else Long.MAX_VALUE)
}

/**
 * What's playing on this TV (Amazon Music, Spotify, YouTube Music…), next to Home Assistant's speakers. With
 * notification access ([MediaListener]) Android tells Haven the app, song, artist, cover and position; without it
 * Haven only knows that music is playing and guesses the app from the last music app opened.
 */
object TvPlayback {
    private val _track = MutableStateFlow<TvTrack?>(null)
    val track: StateFlow<TvTrack?> = _track

    /** Whether Haven has been allowed to see what's playing (notification access). */
    val access = MutableStateFlow(false)

    /** The music app last opened, from Haven or seen by the Home button service. */
    @Volatile
    var lastMusicApp: String? = null

    @Volatile
    private var controller: MediaController? = null
    private var lastActive = 0L
    private var artKey: String? = null
    private var art: ImageBitmap? = null

    fun start(ctx: Context) {
        access.value = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
        Haven.scope.launch {
            while (true) {
                runCatching { _track.value = read(ctx) }
                delay(2000)
            }
        }
    }

    private fun read(ctx: Context): TvTrack? {
        val now = SystemClock.elapsedRealtime()
        val sessions = if (!access.value) emptyList() else runCatching {
            ctx.getSystemService(MediaSessionManager::class.java)
                .getActiveSessions(ComponentName(ctx, MediaListener::class.java))
        }.getOrDefault(emptyList())
        val c = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PAUSED && it.metadata != null }
        controller = c
        if (c != null) {
            val m = c.metadata
            val p = c.playbackState
            val title = m?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
            val artist = (m?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: m?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)).orEmpty()
            val key = "${c.packageName}|$title|$artist"
            if (key != artKey) {
                artKey = key
                val bmp: Bitmap? = m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                art = bmp?.asImageBitmap()
            }
            val playing = p?.state == PlaybackState.STATE_PLAYING
            return TvTrack(
                appLabel(c.packageName), c.packageName, title, artist, m?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(), art, playing,
                p?.position ?: 0L, p?.lastPositionUpdateTime?.takeIf { it > 0 } ?: now, m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            )
        }
        // No details: Android still says whether music is playing. Keep a paused card for a while after it stops.
        val audio = ctx.getSystemService(AudioManager::class.java)
        val pkg = lastMusicApp.orEmpty()
        if (audio.isMusicActive) {
            lastActive = now
            return TvTrack(appLabel(pkg).ifBlank { "this TV" }, pkg, "", "", "", null, true, 0L, 0L, 0L)
        }
        val last = _track.value
        return if (last != null && now - lastActive < 10 * 60_000L) last.copy(playing = false) else null
    }

    private fun appLabel(pkg: String): String =
        if (pkg.isBlank()) "" else Haven.apps.apps.value.firstOrNull { it.pkg == pkg }?.label ?: pkg

    fun playPause() = control(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
        if (it.playbackState?.state == PlaybackState.STATE_PLAYING) it.transportControls.pause() else it.transportControls.play()
    }

    fun next() = control(KeyEvent.KEYCODE_MEDIA_NEXT) { it.transportControls.skipToNext() }

    fun previous() = control(KeyEvent.KEYCODE_MEDIA_PREVIOUS) { it.transportControls.skipToPrevious() }

    /** Louder or quieter: this TV's own music volume, shown by Android as it changes. */
    fun volume(up: Boolean) {
        val audio = Haven.app.getSystemService(AudioManager::class.java)
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
    }

    /** Through the app's own controls when Haven can see them, otherwise the remote's media keys. */
    private fun control(key: Int, direct: (MediaController) -> Unit) {
        val c = controller
        if (c != null && runCatching { direct(c) }.isSuccess) return
        val audio = Haven.app.getSystemService(AudioManager::class.java)
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
    }
}
