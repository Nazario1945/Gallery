package org.fossify.gallery.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import org.fossify.gallery.R
import org.fossify.gallery.helpers.PlayerHolder

@OptIn(UnstableApi::class)
class AudioPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    private val pauseTimeoutRunnable = Runnable {
        if (player?.playWhenReady != true) stopSelf()
    }

    override fun onCreate() {
        super.onCreate()
        player = PlayerHolder.player
        // Forzar notificación en foreground SIEMPRE
        startForeground(NOTIF_ID, minimalNotification(), FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)

        if (player == null) {
            stopSelf()
            return
        }

        player?.apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_LOCAL)
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true).build()
            clearVideoSurface()
        }

        mediaSession = MediaSession.Builder(this, player!!).build()

        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) stopSelf()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) handler.removeCallbacks(pauseTimeoutRunnable)
                else handler.postDelayed(pauseTimeoutRunnable, PAUSE_TIMEOUT_MS)
            }
        })
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = player
        val shouldStop = p == null || !p.playWhenReady || p.mediaItemCount == 0 || p.playbackState == Player.STATE_ENDED
        if (shouldStop) stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(pauseTimeoutRunnable)
        stopForeground(true)
        mediaSession?.release()
        mediaSession = null
        if (PlayerHolder.backgroundMode) {
            player?.release()
            PlayerHolder.clear()
        } else {
            player?.clearVideoSurface()
        }
        player = null
        super.onDestroy()
    }

    private fun minimalNotification(): Notification {
        val chId = "audio_bg_playback"
        if (Build.VERSION.SDK_INT >= 26) {
            val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(chId) == null) {
                mgr.createNotificationChannel(NotificationChannel(chId, "Reproducción", NotificationManager.IMPORTANCE_LOW))
            }
        }
        return NotificationCompat.Builder(this, chId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val PAUSE_TIMEOUT_MS = 5 * 60 * 1000L
        private const val NOTIF_ID = 1001
        private const val FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK =
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
    }
}
