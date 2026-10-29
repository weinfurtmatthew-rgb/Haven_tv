package app.haven.launcher.media

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import app.haven.launcher.Haven
import app.haven.launcher.data.AppKind
import app.haven.launcher.system.MediaListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
 *
 * With access it also keeps Continue watching: what video apps say they're playing (title, picture, how far in),
 * remembered for the profile watching ([WatchList]).
 */
object TvPlayback {
    private val _track = MutableStateFlow<TvTrack?>(null)
    val track: StateFlow<TvTrack?> = _track

    /** Whether Haven has been allowed to see what's playing (notification access). */
    val access = MutableStateFlow(false)

    /** The music app last opened, from Haven or seen by the Home button service. */
    @Volatile
    var lastMusicApp: String? = null

    private val _watched = MutableStateFlow<List<Watched>>(emptyList())

    /** Every profile's Continue watching, newest first; [WatchList.shown] picks a profile's row. */
    val watched: StateFlow<List<Watched>> = _watched

    @Volatile
    private var controller: MediaController? = null

    /** The sessions seen last time, by app, to open a title right where it plays. */
    @Volatile
    private var live: Map<String, MediaController> = emptyMap()
    private val savedAt = HashMap<String, Long>()
    private lateinit var dir: File
    private var lastActive = 0L
    private var artKey: String? = null
    private var art: ImageBitmap? = null

    fun start(ctx: Context) {
        access.value = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
        dir = File(ctx.filesDir, "watching").apply { mkdirs() }
        Haven.scope.launch {
            _watched.value = withContext(Dispatchers.IO) { runCatching { WatchList.fromJson(File(dir, "list.json").readText()) }.getOrDefault(emptyList()) }
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
        live = sessions.associateBy { it.packageName }
        sessions.filter { isVideo(it.packageName) }.forEach { watch(it, now) }
        // Now playing is for music: a paused film left in Netflix isn't "playing" on the Music page.
        val music = sessions.filter { !isVideo(it.packageName) }
        val c = music.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: music.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PAUSED && it.metadata != null }
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

    /** Video, not music: anything that isn't a music app Haven knows (Netflix, Prime Video, YouTube, Plex…). */
    private fun isVideo(pkg: String): Boolean {
        val app = Haven.apps.apps.value.firstOrNull { it.pkg == pkg } ?: return false
        return AppKind.Music !in app.kinds && pkg != Haven.app.packageName
    }

    /** Remembers what [c] is playing for Continue watching, at most every 15 seconds per app. */
    private fun watch(c: MediaController, now: Long) {
        val state = c.playbackState?.state
        if (state != PlaybackState.STATE_PLAYING && state != PlaybackState.STATE_PAUSED) return
        val m = c.metadata ?: return
        val title = (m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: m.getString(MediaMetadata.METADATA_KEY_TITLE)).orEmpty().trim()
        if (title.isBlank()) return
        val pkg = c.packageName
        val profile = Haven.settings.activeId
        val known = _watched.value.firstOrNull { it.pkg == pkg && it.title == title && it.profile == profile }
        if (known != null && now - (savedAt[pkg] ?: 0L) < 15_000L) return
        savedAt[pkg] = now
        val p = c.playbackState
        val pos = (p?.position ?: 0L) + if (state == PlaybackState.STATE_PLAYING && (p?.lastPositionUpdateTime ?: 0L) > 0) now - p!!.lastPositionUpdateTime else 0L
        val subtitle = (m.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: m.getString(MediaMetadata.METADATA_KEY_ARTIST)).orEmpty()
        val bmp = if (known?.art.isNullOrBlank()) {
            m.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON) ?: m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        } else null
        val uri = listOf(MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI, MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            .firstNotNullOfOrNull { k -> m.getString(k)?.takeIf { it.startsWith("http") } }.orEmpty()
        val w = Watched(pkg, title, subtitle, uri, pos, m.getLong(MediaMetadata.METADATA_KEY_DURATION), System.currentTimeMillis(), profile)
        _watched.update { WatchList.record(it, w) }
        Haven.scope.launch(Dispatchers.IO) {
            if (bmp != null) {
                val name = "${w.key.hashCode().toUInt()}.jpg"
                runCatching {
                    val scale = minOf(1f, 640f / bmp.width)
                    val small = Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true)
                    File(dir, name).outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                    _watched.update { l -> l.map { if (it.key == w.key && it.profile == w.profile) it.copy(art = name) else it } }
                }
            }
            save()
        }
    }

    private fun save() = runCatching {
        val list = _watched.value
        File(dir, "list.json").writeText(WatchList.toJson(list))
        val keep = list.map { it.art }.toSet()
        dir.listFiles { f -> f.name.endsWith(".jpg") && f.name !in keep }?.forEach { it.delete() }
    }

    /** A Continue watching picture: a file Haven saved, or the app's own web address. */
    fun picture(w: Watched): Any? = when {
        w.art.isBlank() -> null
        w.art.startsWith("http") -> w.art
        else -> File(dir, w.art)
    }

    /** Opens [w]: where it plays when the app still has it open, otherwise the app (most pick up where you were). */
    fun open(ctx: Context, w: Watched) {
        val pi = live[w.pkg]?.takeIf { it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty().isNotBlank() }?.sessionActivity
        if (pi != null && runCatching {
                if (Build.VERSION.SDK_INT >= 34) {
                    val o = ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    pi.send(ctx, 0, null, null, null, null, o.toBundle())
                } else {
                    pi.send()
                }
            }.isSuccess
        ) return
        val app = Haven.apps.apps.value.firstOrNull { it.pkg == w.pkg }
        if (app != null) {
            Haven.apps.launch(app)
            return
        }
        val pm = ctx.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(w.pkg) ?: pm.getLaunchIntentForPackage(w.pkg) ?: return
        runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Takes [w] off Continue watching. */
    fun forget(w: Watched) {
        _watched.update { WatchList.remove(it, w) }
        Haven.scope.launch(Dispatchers.IO) { save() }
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
