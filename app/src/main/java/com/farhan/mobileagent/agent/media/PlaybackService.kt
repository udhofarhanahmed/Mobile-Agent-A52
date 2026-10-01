package com.farhan.mobileagent.agent.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val exoPlayer = ExoPlayer.Builder(this).build()
        player = exoPlayer
        mediaSession = MediaSession.Builder(this, exoPlayer).build()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_URI -> {
                val uri = intent.getParcelableExtra<Uri>(EXTRA_URI)
                if (uri != null) {
                    player?.apply {
                        setMediaItem(MediaItem.fromUri(uri))
                        prepare()
                        play()
                    }
                }
            }
            ACTION_STOP -> {
                player?.stop()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        private const val ACTION_PLAY_URI = "com.farhan.mobileagent.action.PLAY_URI"
        private const val ACTION_STOP = "com.farhan.mobileagent.action.STOP"
        private const val EXTRA_URI = "media_uri"

        fun play(context: Context, uri: Uri) {
            val intent = Intent(context, PlaybackService::class.java).apply {
                action = ACTION_PLAY_URI
                putExtra(EXTRA_URI, uri)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, PlaybackService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
