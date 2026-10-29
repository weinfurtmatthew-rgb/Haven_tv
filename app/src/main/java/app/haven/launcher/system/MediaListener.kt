package app.haven.launcher.system

import android.service.notification.NotificationListenerService
import app.haven.launcher.media.TvPlayback

/**
 * Only here so Haven may ask Android which app on this TV is playing what (the song, artist and cover). Android
 * ties that to notification access; Haven reads no notifications.
 */
class MediaListener : NotificationListenerService() {
    override fun onListenerConnected() {
        TvPlayback.access.value = true
    }

    override fun onListenerDisconnected() {
        TvPlayback.access.value = false
    }
}
