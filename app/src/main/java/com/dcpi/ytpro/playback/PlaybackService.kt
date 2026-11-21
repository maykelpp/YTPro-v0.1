package com.dcpi.ytpro.playback

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSession.Builder(this, PlaybackPlayerHolder.get(this)).build()
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession
    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        PlaybackPlayerHolder.release()
        super.onDestroy()
    }
}
